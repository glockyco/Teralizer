"""Invariants of the exclusion model documented in `docs/exclusion-model.md`.

The RQ6 breakdown sorts every excluded entity into `filtering` or `failures`, but the
pipeline has five ways to exclude one. A failure here means a mechanism grew, moved, or
started writing somewhere new, and a bucket is now silently absorbing it. Read
`docs/exclusion-model.md` before relaxing an assertion.
"""

import re

import pytest
import sqlalchemy.exc
from sqlalchemy import text

from teralizer.eval.data import connect
from teralizer.eval.reports import _funnel, rq6_causes
from teralizer.eval.reports._causes_common import MECHANISM_OUTCOMES
from teralizer.eval.reports.rq6_causes import DEFAULT_DB

# `FILTERING_SQL` recognises a filter by its class name. Anything else that writes to
# `filter_result` is a different mechanism wearing a filter's clothes.
FILTER_CLASS_NAME = re.compile(r"filter\.\w+Filter$")

# Non-filters allowed to write `filter_result`. Adding a name here is a decision, not a
# formality: `BREAKDOWN_SQL` must also stop counting it as a filter rejection.
DECLARED_NON_FILTERS = frozenset({"GeneratedTestValidator"})

# Typed exclusion codes, grouped by the mechanism that writes them.
GENERATION_GATE_CODES = frozenset(
    {"ORACLE_NOT_WIDENABLE", "INPUT_SPEC_NOT_SATISFIED_BY_SEED"}
)
QUARANTINE_CODES = frozenset(
    {"UNCOMPILABLE_GENERALIZED_TEST", "UNCOMPILABLE_INSTRUMENTED_WRAPPER"}
)
CAPABILITY_CODES = frozenset({"INHERITED_METHOD_NOT_FLATTENABLE"})
KNOWN_EXCLUSION_CODES = GENERATION_GATE_CODES | QUARANTINE_CODES | CAPABILITY_CODES

ENTITY_TABLES = ("test", "assertion", "generalization")


def _rows(sql: str, **params) -> list[tuple]:
    """Run `sql` against the RQ6 database, skipping when it is not reachable."""
    try:
        with connect(DEFAULT_DB) as conn:
            return [tuple(row) for row in conn.execute(text(sql), params)]
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def _variant() -> str:
    try:
        with connect(DEFAULT_DB) as conn:
            return _funnel.resolve_variant(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def _breakdown():
    try:
        with connect(DEFAULT_DB) as conn:
            return rq6_causes._fetch_breakdown(conn, _funnel.resolve_variant(conn))
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_generalizations_partition_into_gated_and_emitted():
    """A generalization is either refused before emission or has a lifecycle row."""
    attempts, gated, emitted = _rows(
        """
        SELECT count(*),
               count(*) FILTER (WHERE g.exclusion_info = ANY(:codes)),
               count(l.id)
        FROM generalization g
        LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant
        """,
        codes=sorted(GENERATION_GATE_CODES),
        variant=_variant(),
    )[0]
    assert gated + emitted == attempts, (
        f"{attempts} attempts != {gated} gated + {emitted} emitted; a third outcome "
        "exists that docs/exclusion-model.md does not describe"
    )


def test_gated_generalizations_leave_no_downstream_rows():
    """The generation gates return before emission, so nothing downstream may exist."""
    lifecycle, filtered = _rows(
        """
        SELECT count(l.id),
               count(*) FILTER (
                 WHERE EXISTS (SELECT 1 FROM filter_result fr
                                WHERE fr.generalization_id = g.id))
        FROM generalization g
        LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant AND g.exclusion_info = ANY(:codes)
        """,
        codes=sorted(GENERATION_GATE_CODES),
        variant=_variant(),
    )[0]
    assert (lifecycle, filtered) == (0, 0), (
        f"gated generalizations have {lifecycle} lifecycle and {filtered} filter rows; "
        "they were refused before a generalized test existed"
    )


def test_every_filter_result_writer_is_a_filter_or_declared_otherwise():
    """`filter_result` is shared with the javac quarantine. Nothing else may join."""
    names = {
        name for (name,) in _rows("SELECT DISTINCT filter_name FROM filter_result")
    }
    unknown = {
        n for n in names if not FILTER_CLASS_NAME.search(n)
    } - DECLARED_NON_FILTERS
    assert not unknown, (
        f"{sorted(unknown)} write filter_result but are not filters; BREAKDOWN_SQL "
        "counts every REJECT as filtering, so these are being misreported"
    )


@pytest.mark.parametrize("table", ENTITY_TABLES)
def test_every_typed_exclusion_code_is_a_known_mechanism(table):
    """A new typed code means a new mechanism the breakdown does not model."""
    codes = {
        code
        for (code,) in _rows(
            f"""
            SELECT DISTINCT split_part(exclusion_info, ':', 1)
            FROM {table}
            WHERE exclusion_info IS NOT NULL
              AND exclusion_info NOT LIKE 'Excluded by%'
            """
        )
    }
    unknown = codes - KNOWN_EXCLUSION_CODES
    assert not unknown, (
        f"{table} carries unmodelled exclusion codes {sorted(unknown)}; the breakdown "
        "will absorb them into whichever bucket its predicates happen to match"
    )


def test_breakdown_buckets_sum_to_level_total():
    """Every entity lands in exactly one mechanism.

    `_fetch_breakdown` already refuses to return an unclassified entity, so this
    also proves the classification is total rather than merely non-overlapping.
    """
    df = _breakdown()
    counted = sum(df[outcome.column] for outcome in MECHANISM_OUTCOMES)
    mismatched = df[df["total"] != counted]
    assert mismatched.empty, f"mechanisms do not partition the level:\n{mismatched}"


def test_filter_passed_generalizations_carry_no_rejection():
    """`generated_filter_passed` and a REJECT row are contradictory verdicts."""
    (contradictory,) = _rows(
        """
        SELECT count(*)
        FROM generalization_lifecycle l
        JOIN generalization g ON g.id = l.generalization_id
        WHERE g.variant = :variant
          AND l.generated_filter_passed
          AND EXISTS (SELECT 1 FROM filter_result fr
                       WHERE fr.generalization_id = g.id AND fr.decision = 'REJECT')
        """,
        variant=_variant(),
    )[0]
    assert contradictory == 0, (
        f"{contradictory} generalizations both passed filtering and were rejected"
    )


@pytest.mark.xfail(
    strict=True,
    reason=(
        "EM-7: deriveRollup reports the first unset flag as the failure stage, so a "
        "stage that never ran is indistinguishable from one that failed. Remove this "
        "marker once the lifecycle gains a not-attempted outcome."
    ),
)
def test_lifecycle_failure_stage_was_actually_attempted():
    """A generalization cannot fail at a stage its project never reached."""
    (never_ran,) = _rows(
        """
        SELECT count(*)
        FROM generalization_lifecycle l
        JOIN generalization g ON g.id = l.generalization_id
        WHERE l.final_failure_stage IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM task t
                           WHERE t.project_id = g.project_id
                             AND t.stage::text = l.final_failure_stage)
        """
    )[0]
    assert never_ran == 0, (
        f"{never_ran} generalizations are blamed on a stage with no task row"
    )
