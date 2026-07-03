"""MUT-resolution confidence-tier funnel.

Reports how method-under-test identification resolved across the corpus:
per-tier/status/signal counts, the MissingValue cross-tab (which resolution
outcomes still hit ``MissingValueFilter``), and ranked-guess provenance
(how many T4 guesses exist and what their alternatives were).

Tier-slicing keeps high-confidence evidence separate from best-effort picks:
headline claims should cite T1/T2 only; T3/T4 are reported separately.

Run:  uv run --directory analysis python -m teralizer.mut_resolution_funnel
"""

from __future__ import annotations

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.config import db_config

_MISSING_VALUE = "teralizer.processing.filter.MissingValueFilter"


def get_tier_funnel(conn: Connection) -> pd.DataFrame:
    """Aggregate by resolver outcome so confidence tiers are never conflated."""
    sql = text(
        """
        SELECT status, confidence_tier, deciding_signal,
               COUNT(*) AS assertions,
               SUM(CASE WHEN shallow_inspector_pick THEN 1 ELSE 0 END) AS shallow_picks,
               SUM(CASE WHEN inspector_unwrapped THEN 1 ELSE 0 END) AS inspector_unwraps
        FROM mut_resolution_observation
        GROUP BY status, confidence_tier, deciding_signal
        ORDER BY confidence_tier, status, assertions DESC
        """
    )
    return pd.read_sql(sql, conn)


def get_missing_value_cross_tab(conn: Connection) -> pd.DataFrame:
    """Expose which resolver outcomes still leave declaration columns empty."""
    sql = text(
        """
        SELECT o.status, o.confidence_tier, o.no_pick_reason, COUNT(*) AS mv_rejects
        FROM mut_resolution_observation o
        JOIN filter_result fr ON fr.assertion_id = o.assertion_id
        WHERE fr.filter_name = :mv AND fr.decision = 'REJECT'
        GROUP BY o.status, o.confidence_tier, o.no_pick_reason
        ORDER BY mv_rejects DESC
        """
    )
    return pd.read_sql(sql, conn, params={"mv": _MISSING_VALUE})


def get_guess_provenance(conn: Connection) -> pd.DataFrame:
    """Surface low-confidence picks for manual review before using them as evidence."""
    sql = text(
        """
        SELECT o.project_id, o.assertion_id, o.resolved_method_name, o.candidate_count,
               o.candidate_param_supported, o.candidate_return_supported,
               o.focal_agreement, o.candidate_details
        FROM mut_resolution_observation o
        WHERE o.confidence_tier = 'T4_GUESS'
        ORDER BY o.candidate_count DESC
        """
    )
    return pd.read_sql(sql, conn)


def get_topology_cross_tab(conn: Connection) -> pd.DataFrame:
    """Size recipe work by actual-value shape and receiver-origin combinations."""
    sql = text(
        """
        SELECT actual_shape, receiver_provenance, COUNT(*) AS assertions,
               SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved
        FROM mut_resolution_observation
        GROUP BY actual_shape, receiver_provenance
        ORDER BY assertions DESC
        """
    )
    return pd.read_sql(sql, conn)


def main() -> None:
    engine = db_config.get_test_engine(validate=False)
    with engine.connect() as conn:
        funnel = get_tier_funnel(conn)
        print("== Tier funnel ==")
        print(funnel.to_string(index=False))

        total = int(funnel["assertions"].sum())
        if total:
            by_tier = funnel.groupby("confidence_tier")["assertions"].sum()
            print("\n== Tier shares ==")
            for tier, count in by_tier.items():
                print(f"{tier}: {count} ({count / total:.1%})")

        print("\n== MissingValue cross-tab ==")
        print(get_missing_value_cross_tab(conn).to_string(index=False))

        guesses = get_guess_provenance(conn)
        print(f"\n== T4 guesses: {len(guesses)} ==")
        print(guesses.head(20).to_string(index=False))

        print("\n== Input topology (shape x provenance) ==")
        print(get_topology_cross_tab(conn).to_string(index=False))


if __name__ == "__main__":
    main()
