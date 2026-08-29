"""Corpus invariants for the canonical RQ6 exclusion mechanisms."""

import pandas as pd
import pytest
from sqlalchemy import text

from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel
from teralizer.eval.reports._causes_common import MECHANISM_OUTCOMES


def _rows(conn, sql: str, **params) -> list[tuple]:
    return [tuple(row) for row in conn.execute(text(sql), params)]


def _variant(conn) -> str:
    return _funnel.resolve_variant(conn)


def _breakdown(conn):
    partition = exclusion.fetch_mechanism_partition(conn, _funnel.resolve_variant(conn))
    return exclusion.pivot_mechanism_partition(partition)


def test_mechanism_registry_covers_reader_outcomes():
    assert set(exclusion.MECHANISM_BY_KEY) == set(exclusion.MechanismKey)
    assert exclusion.READER_COLLAPSE == {
        "included": ("included",),
        "filtering": (
            "filter_rejection",
            "generation_gate",
            "inherited_test_inlining_limit",
        ),
        "failures": ("build_quarantine", "task_exception"),
    }


def test_filter_result_storage_does_not_choose_the_mechanism():
    assert (
        exclusion.producer_mechanism(
            "teralizer.processing.filter.ReturnTypeFilter",
            "REJECT",
            "UNSUPPORTED_RETURN_TYPE",
        )
        is exclusion.MechanismKey.FILTER_REJECTION
    )
    assert (
        exclusion.producer_mechanism(
            "GeneratedTestValidator",
            "REJECT",
            "UNCOMPILABLE_GENERALIZED_TEST",
        )
        is exclusion.MechanismKey.BUILD_QUARANTINE
    )


def test_unknown_filter_result_producer_fails_loudly():
    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="unknown filter-result producer: MysteryWriter",
    ):
        exclusion.producer_mechanism("MysteryWriter", "REJECT", "MYSTERY")


@pytest.mark.parametrize(
    ("decision", "reason_code", "message"),
    (
        ("MAYBE", "SOME_REASON", "unknown filter-result decision"),
        ("REJECT", None, "missing rejection reason"),
    ),
)
def test_invalid_filter_result_shape_fails_loudly(decision, reason_code, message):
    with pytest.raises(exclusion.ExclusionEvidenceError, match=message):
        exclusion.producer_mechanism(
            "teralizer.processing.filter.ReturnTypeFilter",
            decision,
            reason_code,
        )


@pytest.mark.parametrize(
    ("level", "code", "mechanism"),
    (
        (
            "Generalization",
            "ORACLE_NOT_WIDENABLE",
            exclusion.MechanismKey.GENERATION_GATE,
        ),
        (
            "Generalization",
            "INPUT_SPEC_NOT_SATISFIED_BY_SEED",
            exclusion.MechanismKey.GENERATION_GATE,
        ),
        (
            "Assertion",
            "UNCOMPILABLE_GENERALIZED_TEST",
            exclusion.MechanismKey.BUILD_QUARANTINE,
        ),
        (
            "Generalization",
            "UNCOMPILABLE_INSTRUMENTED_WRAPPER",
            exclusion.MechanismKey.BUILD_QUARANTINE,
        ),
        (
            "Test",
            "INHERITED_METHOD_NOT_FLATTENABLE",
            exclusion.MechanismKey.INHERITED_TEST_INLINING_LIMIT,
        ),
    ),
)
def test_typed_code_is_bound_to_its_entity_level(level, code, mechanism):
    assert exclusion.typed_code_mechanism(level, code) is mechanism


def test_typed_code_at_wrong_level_fails_loudly():
    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="ORACLE_NOT_WIDENABLE is invalid at Test level",
    ):
        exclusion.typed_code_mechanism("Test", "ORACLE_NOT_WIDENABLE")


def test_unknown_typed_code_fails_loudly():
    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="unknown Assertion exclusion code: MYSTERY_CODE",
    ):
        exclusion.typed_code_mechanism("Assertion", "MYSTERY_CODE")


def test_duplicate_mechanism_attribution_fails_loudly():
    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="multiply classified entity: Assertion:7:filter_rejection,task_exception",
    ):
        exclusion.resolve_mechanism_candidates(
            "Assertion",
            7,
            (
                exclusion.MechanismKey.FILTER_REJECTION,
                exclusion.MechanismKey.TASK_EXCEPTION,
            ),
        )


@pytest.mark.parametrize(
    "mechanism",
    (exclusion.MechanismKey.INCLUDED, exclusion.MechanismKey.TASK_EXCEPTION),
)
def test_single_mechanism_candidate_is_retained(mechanism):
    assert exclusion.resolve_mechanism_candidates("Test", 3, (mechanism,)) is mechanism


def test_absent_mechanism_evidence_stays_absent():
    assert (
        exclusion.producer_mechanism("GeneratedTestValidator", "ACCEPT", None) is None
    )
    assert (
        exclusion.producer_mechanism(
            "teralizer.processing.filter.NestedClassesFilter",
            "DEFER",
            "NESTED_CLASSES",
        )
        is None
    )
    assert exclusion.resolve_mechanism_candidates("Test", 3, ()) is None


def test_generalizations_partition_into_gated_and_emitted(rq6_conn):
    """A generalization is either refused before emission or has a lifecycle row."""
    attempts, gated, emitted = _rows(
        rq6_conn,
        """
        SELECT count(*),
               count(*) FILTER (WHERE g.exclusion_info = ANY(:codes)),
               count(l.id)
        FROM generalization g
        LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant
        """,
        codes=sorted(exclusion.GATE_CODES),
        variant=_variant(rq6_conn),
    )[0]
    assert gated + emitted == attempts, (
        f"{attempts} attempts != {gated} gated + {emitted} emitted; "
        "the exclusion-accounting contract has an unclassified third outcome"
    )


def test_proactive_filter_rows_use_persisted_populations(rq6_conn):
    variant = _variant(rq6_conn)
    exclusion.validate_evidence(rq6_conn, variant)
    decisions = exclusion.fetch_filter_decisions(rq6_conn, variant)
    partition = exclusion.fetch_mechanism_partition(rq6_conn, variant)

    rows = decisions.set_index(["level", "filter"])
    expected = {
        ("Test", "InheritedTestMethod"): (6_259, 3_424, 0, 2_835),
        ("Generalization", "SeedSpecConsistency"): (5_356, 5_355, 0, 1),
        ("Generalization", "WideningLicense"): (5_355, 2_057, 0, 3_298),
        ("Generalization", "NonPassingTest"): (2_035, 1_615, 0, 420),
    }
    for key, counts in expected.items():
        row = rows.loc[key]
        assert (
            tuple(int(row[column]) for column in ("total", "accept", "defer", "reject"))
            == counts
        )

    exclusion.validate_filtering_reconciliation(decisions, partition)
    generalization = decisions.loc[decisions["level"] == "Generalization"]
    assert int(generalization["reject"].sum()) == 3_719


def test_filtering_reconciliation_rejects_missing_semantic_filter():
    decisions = pd.DataFrame(
        [
            ("Generalization", "SeedSpecConsistency", 2, 1, 0, 1),
            ("Generalization", "NonPassingTest", 1, 0, 0, 1),
        ],
        columns=["level", "filter", "total", "accept", "defer", "reject"],
    )
    partition = pd.DataFrame(
        [("Generalization", "filtering", 2)],
        columns=["level", "reader_outcome", "entity_count"],
    )

    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="generalization filter set disagrees with the semantic registry",
    ):
        exclusion.validate_filtering_reconciliation(decisions, partition)


def test_gated_generalizations_leave_no_downstream_rows(rq6_conn):
    """The generation gates return before emission, so nothing downstream may exist."""
    lifecycle, filtered = _rows(
        rq6_conn,
        """
        SELECT count(l.id),
               count(*) FILTER (
                 WHERE EXISTS (SELECT 1 FROM filter_result fr
                                WHERE fr.generalization_id = g.id))
        FROM generalization g
        LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant AND g.exclusion_info = ANY(:codes)
        """,
        codes=sorted(exclusion.GATE_CODES),
        variant=_variant(rq6_conn),
    )[0]
    assert (lifecycle, filtered) == (0, 0), (
        f"gated generalizations have {lifecycle} lifecycle and {filtered} filter rows; "
        "they were refused before a generalized test existed"
    )


def test_every_filter_result_writer_has_one_producer_semantics(rq6_conn):
    """A shared storage row is classified by its producer and verdict."""
    rows = _rows(
        rq6_conn,
        """
        SELECT DISTINCT filter_name, decision, reason_code
        FROM filter_result
        """,
    )
    for producer, decision, reason_code in rows:
        exclusion.producer_mechanism(producer, decision, reason_code)


@pytest.mark.parametrize(
    ("table", "level"),
    (
        ("test", "Test"),
        ("assertion", "Assertion"),
        ("generalization", "Generalization"),
    ),
)
def test_every_typed_exclusion_code_is_a_known_mechanism(table, level, rq6_conn):
    """A new typed code must have one valid level-specific mechanism."""
    codes = {
        code
        for (code,) in _rows(
            rq6_conn,
            f"""
            SELECT DISTINCT split_part(exclusion_info, ':', 1)
            FROM {table}
            WHERE exclusion_info IS NOT NULL
              AND exclusion_info NOT LIKE 'Excluded by%'
            """,
        )
    }
    for code in codes:
        exclusion.typed_code_mechanism(level, code)


def test_breakdown_buckets_sum_to_level_total(rq6_conn):
    """Every entity lands in exactly one mechanism.

    `fetch_mechanism_partition` refuses unclassified or multiply classified entities, so this
    also proves the classification is total rather than merely non-overlapping.
    """
    df = _breakdown(rq6_conn)
    counted = sum(df[outcome.column] for outcome in MECHANISM_OUTCOMES)
    mismatched = df[df["total"] != counted]
    assert mismatched.empty, f"mechanisms do not partition the level:\n{mismatched}"


def test_filter_passed_generalizations_carry_no_rejection(rq6_conn):
    """`generated_filter_passed` and a REJECT row are contradictory verdicts."""
    (contradictory,) = _rows(
        rq6_conn,
        """
        SELECT count(*)
        FROM generalization_lifecycle l
        JOIN generalization g ON g.id = l.generalization_id
        WHERE g.variant = :variant
          AND l.generated_filter_passed
          AND EXISTS (SELECT 1 FROM filter_result fr
                       WHERE fr.generalization_id = g.id AND fr.decision = 'REJECT')
        """,
        variant=_variant(rq6_conn),
    )[0]
    assert contradictory == 0, (
        f"{contradictory} generalizations both passed filtering and were rejected"
    )
