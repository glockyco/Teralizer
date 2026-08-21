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

import argparse
from dataclasses import dataclass
import json

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.corpora import open_corpus
from teralizer.report_basis import print_basis_header

_MISSING_VALUE = "teralizer.processing.filter.MissingValueFilter"

_DEFAULT_CORPUS = "real-world"
_LOCAL_PRODUCER_PROVENANCE = {"LOCAL_OTHER"}


@dataclass(frozen=True)
class LibraryAccessorEstimate:
    """One no-pick row classified for Lever-4 sizing."""

    accessor: str
    estimated_recoverable: bool
    evidence: str


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


def get_library_declaration_observations(conn: Connection) -> pd.DataFrame:
    """Rows whose selected call was library-declared rather than source-declared."""
    sql = text(
        """
        SELECT resolved_call_source, resolved_method_name, resolved_declaring_type,
               receiver_provenance, candidate_details
        FROM mut_resolution_observation
        WHERE no_pick_reason = 'LIBRARY_DECLARATION'
        """
    )
    return pd.read_sql(sql, conn)


def _accessor_family(method_name: str | None, declaring_type: str | None) -> str:
    method = method_name or ""
    declaring = declaring_type or ""
    if method == "get":
        if declaring == "java.util.Optional":
            return "Optional.get"
        if declaring.endswith("Map") or declaring in {
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.Hashtable",
            "java.util.Properties",
            "java.util.concurrent.ConcurrentHashMap",
        }:
            return "Map.get"
        if declaring.endswith("List") or declaring in {
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.Vector",
        }:
            return "List.get"
    if method == "next" and declaring.endswith("Iterator"):
        return "Iterator.next"
    return "other"


def _selected_receiver(source: str | None, method_name: str | None) -> str | None:
    if not source or not method_name:
        return None
    needle = f".{method_name}("
    start = source.rfind(needle)
    if start < 0:
        return None
    return source[:start].strip()


def _receiver_contains_call(receiver: str | None) -> bool:
    if not receiver:
        return False
    return "(" in receiver and ")" in receiver


def _candidate_call_sources(candidate_details: object) -> set[str]:
    if candidate_details is None or pd.isna(candidate_details):
        return set()
    if isinstance(candidate_details, str):
        if not candidate_details.strip():
            return set()
        try:
            parsed = json.loads(candidate_details)
        except json.JSONDecodeError:
            return set()
    else:
        parsed = candidate_details
    if not isinstance(parsed, list):
        return set()
    sources = set()
    for item in parsed:
        if isinstance(item, dict) and isinstance(item.get("callSource"), str):
            sources.add(item["callSource"].strip())
    return sources


def estimate_library_accessor_unwrap(
    *,
    resolved_method_name: str | None,
    resolved_declaring_type: str | None,
    resolved_call_source: str | None,
    receiver_provenance: str | None,
    candidate_details: object,
) -> LibraryAccessorEstimate:
    """Estimate whether a library-accessor pick has a same-method producer.

    Operationalization: only the fixed JDK accessor allowlist counts. A row is
    recoverable when the accessor receiver is an inline call, the topology says
    the root receiver is a non-constructor local, or the recorded candidate
    details contain the receiver as a same-method candidate source.
    """
    accessor = _accessor_family(resolved_method_name, resolved_declaring_type)
    if accessor == "other":
        return LibraryAccessorEstimate(accessor, False, "not_allowlisted")

    receiver = _selected_receiver(resolved_call_source, resolved_method_name)
    if _receiver_contains_call(receiver):
        return LibraryAccessorEstimate(accessor, True, "inline_receiver_call")
    if receiver_provenance in _LOCAL_PRODUCER_PROVENANCE:
        return LibraryAccessorEstimate(accessor, True, "local_receiver")
    if receiver is not None and receiver in _candidate_call_sources(candidate_details):
        return LibraryAccessorEstimate(accessor, True, "candidate_details_receiver")
    return LibraryAccessorEstimate(accessor, False, "no_same_method_producer_evidence")


def summarize_library_accessor_unwrap(observations: pd.DataFrame) -> pd.DataFrame:
    """Return Lever-4 totals and estimated recoverable rows by accessor family."""
    estimates = [
        estimate_library_accessor_unwrap(
            resolved_method_name=row.get("resolved_method_name"),
            resolved_declaring_type=row.get("resolved_declaring_type"),
            resolved_call_source=row.get("resolved_call_source"),
            receiver_provenance=row.get("receiver_provenance"),
            candidate_details=row.get("candidate_details"),
        )
        for _, row in observations.iterrows()
    ]
    rows = []
    for accessor in ("List.get", "Map.get", "Iterator.next", "Optional.get", "other"):
        subset = [estimate for estimate in estimates if estimate.accessor == accessor]
        rows.append(
            {
                "accessor": accessor,
                "total": len(subset),
                "estimated_recoverable": sum(
                    1 for estimate in subset if estimate.estimated_recoverable
                ),
            }
        )
    rows.append(
        {
            "accessor": "TOTAL",
            "total": len(estimates),
            "estimated_recoverable": sum(
                1 for estimate in estimates if estimate.estimated_recoverable
            ),
        }
    )
    return pd.DataFrame(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--corpus",
        default=_DEFAULT_CORPUS,
        help=f"registered corpus to inspect (default: {_DEFAULT_CORPUS})",
    )
    args = parser.parse_args()
    with open_corpus(args.corpus) as (entry, conn):
        print_basis_header(conn, entry.database)
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

        print("\n== Lever 4 library-accessor unwrap sizing ==")
        print(
            summarize_library_accessor_unwrap(
                get_library_declaration_observations(conn)
            ).to_string(index=False)
        )


if __name__ == "__main__":
    main()
