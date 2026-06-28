"""MUT-identification project targeting for the RepoReapers corpus.

Ranks projects by the realistic upside of improving method-under-test (MUT)
identification. Three factors decide whether a project is a good target:

1. **Upside** -- how many assertions are blocked *solely* by MUT-id
   (``MissingValueFilter`` rejects and no other filter rejects the same
   assertion). This is the addressable surface. It is an *upper bound* on the
   realized gain: ``ParameterTypeFilter``/``ReturnTypeFilter`` *defer* when
   MUT-id rejects, so once MUT-id resolves a focal method those filters become
   the next gate and may still reject if the method's parameter/return types
   are unsupported.

2. **Viability** -- whether the project already produces generalizations. A
   project that already generalizes proves the full pipeline works for it, so
   unblocking more of its assertions is near-certain to let more flow further.

3. **Oracle eligibility** -- whether the project reaches
   ``FILTER_TESTS_ORIGINAL``. That is the stage after which ``PIT_ORIGINAL``
   (the proposed killed-mutant oracle source) would run, so reaching it means
   the project can supply oracle data. ``has_pit_data`` reports whether *any*
   mutation data exists today (``PIT_INITIAL``, post-filter, narrower).

Run:  uv run --directory analysis python -m teralizer.mut_id_targets
"""

from __future__ import annotations

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.config import db_config

_MISSING_VALUE = "teralizer.processing.filter.MissingValueFilter"

# Stage a project must reach for PIT_ORIGINAL (pipeline step 12) to collect
# oracle data: original-test filtering. Reaching it implies the project built
# and its original tests ran.
_ORACLE_GATE_STAGE = "FILTER_TESTS_ORIGINAL"

_TIER_GENERALIZES = "1: already generalizes (highest-confidence expansion)"
_TIER_PARTIAL = "2: partial progress (downstream blocker too)"
_TIER_NONE = "3: no progress yet (MUT-id alone may not suffice)"


def get_project_potential(conn: Connection) -> pd.DataFrame:
    """Per-project MUT-id targeting factors, ranked by addressable surface.

    Returns one row per project that has at least one MUT-id-sole-blocked
    assertion, ordered by ``mut_id_blocked`` descending.
    """
    sql = text(
        """
        WITH sole AS (
            SELECT DISTINCT mv.assertion_id, mv.project_id
            FROM filter_result mv
            WHERE mv.filter_name = :mv AND mv.decision = 'REJECT'
              AND NOT EXISTS (
                  SELECT 1 FROM filter_result other
                  WHERE other.assertion_id = mv.assertion_id
                    AND other.decision = 'REJECT'
                    AND other.filter_name <> mv.filter_name
              )
        ),
        upside AS (
            SELECT project_id, count(*) AS mut_id_blocked
            FROM sole GROUP BY project_id
        ),
        oracle_gate AS (
            SELECT DISTINCT project_id FROM task
            WHERE stage = :gate AND status = 'SUCCEEDED'
        ),
        pit_now AS (
            SELECT DISTINCT project_id FROM pit_mutation_report
        )
        SELECT
            p.id AS project_id,
            regexp_replace(p.root_path, '^.*/', '') AS project,
            u.mut_id_blocked,
            (SELECT count(*) FROM generalization g
                 WHERE g.project_id = p.id) AS generalizations,
            (SELECT count(*) FROM assertion a
                 WHERE a.project_id = p.id AND a.is_included) AS included_assertions,
            (og.project_id IS NOT NULL) AS oracle_eligible,
            (pn.project_id IS NOT NULL) AS has_pit_data
        FROM project p
        JOIN upside u ON u.project_id = p.id
        LEFT JOIN oracle_gate og ON og.project_id = p.id
        LEFT JOIN pit_now pn ON pn.project_id = p.id
        ORDER BY u.mut_id_blocked DESC
        """
    )
    return pd.read_sql(
        sql, conn, params={"mv": _MISSING_VALUE, "gate": _ORACLE_GATE_STAGE}
    )


def assign_tier(row: pd.Series) -> str:
    """Classify a project by pipeline progress (viability)."""
    if row["generalizations"] > 0:
        return _TIER_GENERALIZES
    if row["included_assertions"] > 0:
        return _TIER_PARTIAL
    return _TIER_NONE


def rank_targets(df: pd.DataFrame) -> pd.DataFrame:
    """Add the viability tier column to the project-potential frame."""
    ranked = df.copy()
    ranked["tier"] = ranked.apply(assign_tier, axis=1)
    return ranked


def print_targets(ranked: pd.DataFrame, top: int = 15) -> None:
    """Print the top MUT-id targets per viability tier."""
    total_blocked = int(ranked["mut_id_blocked"].sum())
    print(
        f"{len(ranked)} projects have MUT-id-sole-blocked assertions "
        f"({total_blocked} assertions total)."
    )
    print(
        "mut_id_blocked is the addressable surface (upper bound; deferred "
        "ParameterType/ReturnType become the next gate).\n"
    )
    for tier in (_TIER_GENERALIZES, _TIER_PARTIAL, _TIER_NONE):
        subset = ranked[ranked["tier"] == tier].head(top)
        if subset.empty:
            continue
        print(f"Tier {tier}")
        for _, row in subset.iterrows():
            flags = []
            if row["oracle_eligible"]:
                flags.append("oracle-eligible")
            if row["has_pit_data"]:
                flags.append("has-pit")
            suffix = f" [{', '.join(flags)}]" if flags else ""
            print(
                f"  {row['mut_id_blocked']:>5} blocked  "
                f"gen={row['generalizations']:<4} "
                f"incl={row['included_assertions']:<4} "
                f"{row['project']}{suffix}"
            )
        print()


def main() -> None:
    with db_config.get_test_engine(validate=False).connect() as conn:
        ranked = rank_targets(get_project_potential(conn))
    print_targets(ranked)


if __name__ == "__main__":
    main()
