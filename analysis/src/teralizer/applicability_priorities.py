"""Applicability-gap prioritization for real-world test generalization.

Re-runnable analysis of which Teralizer filter blockers are the highest-leverage
fixes, accounting for filter short-circuiting (a fix only reaches assertions
where it is the *first* reject and later filters also pass). Produces a ranked
prioritization of generalizable fixes and the projects closest to completing
the generalization pipeline.

All queries are read-only. Use ``db_config.get_test_engine()`` (repo-reapers /
real-world dataset) or ``get_dev_engine()`` (controlled dataset).
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass
from typing import Any, cast

import pandas as pd
from sqlalchemy import Connection, text

# Filter names are stored fully-qualified in filter_result.filter_name.
# Short aliases for readable output.
_FILTER_ALIASES = {
    "MissingValueFilter": "MissingValue",
    "ReturnTypeFilter": "ReturnType",
    "ParameterTypeFilter": "ParameterType",
    "NoAssertionsFilter": "NoAssertions",
    "UnsupportedAssertionFilter": "UnsupportedAssertion",
    "ExcludedTestFilter": "ExcludedTest",
    "TestTypeFilter": "TestType",
    "NonPassingTestFilter": "NonPassingTest",
    "AssertionInMethodFilter": "AssertionInMethod",
    "AssertionInLoopFilter": "AssertionInLoop",
    "TestedMethodInLoopFilter": "TestedMethodInLoop",
    "NestedClassesFilter": "NestedClasses",
    "StaticInitializersFilter": "StaticInitializers",
    "UnnamedPackageFilter": "UnnamedPackage",
    "ExcludedAssertionFilter": "ExcludedAssertion",
}

# Regex patterns for classifying MissingValue call-extraction state.
_INSTANCE_CALL = re.compile(r"^[a-z][a-zA-Z0-9_]*\.")
_STATIC_CALL = re.compile(r"^[A-Z][a-zA-Z0-9_]*\.")
_CASTED_CALL = re.compile(r"^\(")
_INSTANCE_CALL_IN_SRC = re.compile(r"[a-z_]\.[a-zA-Z_][a-zA-Z0-9_]*\s*\(")
_STATIC_CALL_IN_SRC = re.compile(r"[A-Z][a-zA-Z0-9_]*\.[a-zA-Z_][a-zA-Z0-9_]*\s*\(")


def _short_filter(fq_name: str) -> str:
    """Strip the package prefix from a fully-qualified filter class name."""
    simple = fq_name.rsplit(".", 1)[-1]
    return _FILTER_ALIASES.get(simple, simple)


def _classify_missingvalue_call(row: pd.Series) -> str:
    """Classify one MissingValue-rejected assertion by call-extraction state."""
    call_src = row.get("tested_method_call_source_code")
    src = row.get("assertion_source_code", "")

    if call_src:
        if _INSTANCE_CALL.match(call_src):
            return "instance_call_extracted"
        if _STATIC_CALL.match(call_src):
            return "static_call_extracted"
        if _CASTED_CALL.match(call_src):
            return "casted_call_extracted"
        return "other_call_extracted"

    if src and _INSTANCE_CALL_IN_SRC.search(src):
        return "instance_call_in_source_not_extracted"
    if src and _STATIC_CALL_IN_SRC.search(src):
        return "static_call_in_source_not_extracted"
    return "no_call_visible"


# =============================================================================
# Data retrieval
# =============================================================================


def get_assertion_filter_chain(conn: Connection) -> pd.DataFrame:
    """Return one row per assertion with its ordered reject chain.

    Columns: assertion_id, project_id, filter_name (short), reason, position
    (1-based index within that assertion's REJECT chain). Only REJECT decisions
    are included; DEFER and ACCEPT are filtered out, matching the shadowing rule
    that a fix's true reach is assertions where it is the first REJECT.
    """
    sql = text(
        """
        WITH rejects AS (
            SELECT
                fr.assertion_id,
                a.project_id,
                fr.filter_name,
                fr.reason,
                ROW_NUMBER() OVER (PARTITION BY fr.assertion_id ORDER BY fr.id) AS position
            FROM filter_result fr
            JOIN assertion a ON a.id = fr.assertion_id
            WHERE fr.decision = 'REJECT'
              AND fr.assertion_id IS NOT NULL
        )
        SELECT assertion_id, project_id, filter_name, reason, position
        FROM rejects
        ORDER BY assertion_id, position
        """
    )
    df = pd.read_sql(sql, conn)
    df["filter_name"] = df["filter_name"].map(_short_filter)
    return df


def get_missingvalue_assertions(conn: Connection) -> pd.DataFrame:
    """Return the MissingValue first-reject assertions with source code.

    Columns: assertion_id, assertion_source_code,
    tested_method_call_source_code. These are the assertions where
    MissingValue is the first REJECT and the reason is the 'tested_class_path is
    null' variant (the MUT-identification failure, not other MissingValue
    reasons).
    """
    sql = text(
        """
        WITH first_rejects AS (
            SELECT
                fr.assertion_id,
                a.assertion_source_code,
                a.tested_method_call_source_code
            FROM filter_result fr
            JOIN assertion a ON a.id = fr.assertion_id
            WHERE fr.decision = 'REJECT'
              AND fr.filter_name LIKE '%MissingValueFilter'
              AND fr.reason LIKE '%tested_class_path column is null%'
              AND fr.id = (
                  SELECT min(fr2.id) FROM filter_result fr2
                  WHERE fr2.assertion_id = a.id AND fr2.decision = 'REJECT'
              )
        )
        SELECT assertion_id, assertion_source_code, tested_method_call_source_code
        FROM first_rejects
        ORDER BY assertion_id
        """
    )
    return pd.read_sql(sql, conn)


# =============================================================================
# Computation
# =============================================================================


@dataclass(frozen=True)
class BlockerStat:
    """One filter as a first-reject blocker."""

    filter_name: str
    first_reject_count: int
    total_reject_count: int
    shadowed_count: int  # rejected by this filter AND an earlier one
    net_reach: int  # first_reject_count (assertions this fix would unblock)


def compute_first_reject_blockers(chain_df: pd.DataFrame) -> pd.DataFrame:
    """Shadowing-aware blocker counts: only first-rejects count as true reach.

    A fix to filter X only unblocks assertions where X is the *first* reject in
    the chain (position 1) and the assertion would then survive all later
    filters. We cannot know the latter without re-running, so net_reach is an
    upper bound — but it correctly de-prioritizes filters that are shadowed
    behind earlier rejects.
    """
    first = chain_df[chain_df["position"] == 1]
    total = chain_df.groupby("filter_name").size().rename("total_reject_count")
    first_counts = first.groupby("filter_name").size().rename("first_reject_count")

    df = (
        pd.concat([first_counts, total], axis=1)
        .fillna(0)
        .astype(int)
        .reset_index()
        .rename(columns={"index": "filter_name"})
    )
    df["shadowed_count"] = df["total_reject_count"] - df["first_reject_count"]
    df["net_reach"] = df["first_reject_count"]
    df = df.sort_values("net_reach", ascending=False).reset_index(drop=True)
    return df


def compute_blocker_cooccurrence(chain_df: pd.DataFrame) -> pd.DataFrame:
    """Co-occurring blocker pairs: (first_blocker, second_blocker, count).

    Shows which two filters most often reject the same assertion, revealing
    where a single fix is insufficient and coordinated fixes are needed.
    """
    pivoted: Counter[tuple[str, str | None]] = Counter()
    for _, grp in chain_df.groupby("assertion_id"):
        ordered = grp.sort_values("position")["filter_name"].tolist()
        first = ordered[0] if ordered else None
        second = ordered[1] if len(ordered) > 1 else None
        if first is not None:
            pivoted[(first, second)] += 1

    rows = [
        {"first_blocker": f, "second_blocker": s, "count": c}
        for (f, s), c in pivoted.items()
    ]
    df = pd.DataFrame(rows).sort_values("count", ascending=False).reset_index(drop=True)
    return df


def compute_multi_blocker_rate(chain_df: pd.DataFrame) -> float:
    """Fraction of blocked assertions rejected by >=2 filters.

    High values (>0.5) mean most assertions need coordinated fixes; any single
    fix is marginal.
    """
    per_assertion = chain_df.groupby("assertion_id").size()
    if per_assertion.empty:
        return 0.0
    multi = int((per_assertion >= 2).sum())
    return round(multi / len(per_assertion), 3)


def compute_projects_closest_to_completion(
    project_df: pd.DataFrame, min_included: int = 1
) -> pd.DataFrame:
    """Projects with at least one included assertion, ranked by inclusion count.

    These are the projects closest to making it through the pipeline — the
    candidates for targeted investigation of what blocks the remaining
    assertions.
    """
    mask = project_df["included_assertions"] >= min_included
    df = project_df.loc[mask, :].reset_index(drop=True)
    return cast(pd.DataFrame, df)


def compute_missingvalue_taxonomy(mv_df: pd.DataFrame) -> pd.DataFrame:
    """Classify the MissingValue first-reject bucket by call-extraction state.

    The dominant blocker (58k net reach) is MissingValue with reason
    'tested_class_path is null' — the MUT-identification layer could not
    identify the tested file/class at all. This classifier breaks it into:

    - ``instance_call_extracted``: var.method call found, MUT not resolved.
    - ``static_call_extracted``: Class.method call found, MUT not resolved.
    - ``casted_call_extracted``: casted/chained call found, MUT not resolved.
    - ``other_call_extracted``: call found but unclassified pattern.
    - ``instance_call_in_source_not_extracted``: obj.method() visible in the
      assertion source code but never extracted — the biggest fixable bucket.
    - ``static_call_in_source_not_extracted``: Class.method() visible in
      source but not extracted.
    - ``no_call_visible``: field access, instanceof, bare variable, or fail()
      — no method call to identify a MUT from.

    Columns: category, count, pct.
    """
    categories = mv_df.apply(_classify_missingvalue_call, axis=1)
    counts = categories.value_counts().reset_index()
    counts.columns = ["category", "count"]
    counts["pct"] = (counts["count"] / counts["count"].sum() * 100).round(1)
    counts = counts.sort_values("count", ascending=False).reset_index(drop=True)
    return counts


# =============================================================================
# Reporting
# =============================================================================


def format_blocker_table(blockers: pd.DataFrame) -> str:
    """Human-readable blocker table."""
    lines = [
        f"{'Filter':<22} {'FirstReject':>11} {'TotalReject':>11} "
        f"{'Shadowed':>9} {'NetReach':>9}",
        "-" * 66,
    ]
    for _, r in blockers.iterrows():
        lines.append(
            f"{r['filter_name']:<22} {r['first_reject_count']:>11} "
            f"{r['total_reject_count']:>11} {r['shadowed_count']:>9} "
            f"{r['net_reach']:>9}"
        )
    return "\n".join(lines)


def generate_report(conn: Connection) -> dict[str, Any]:
    """Run the full prioritization analysis and return a structured report.

    Returns a dict with keys: blockers, cooccurrence, multi_blocker_rate,
    missingvalue_taxonomy, projects_closest, project_summary. Each value is a
    DataFrame or scalar.
    """
    chain = get_assertion_filter_chain(conn)
    projects = get_project_summary(conn)
    mv_df = get_missingvalue_assertions(conn)
    return {
        "blockers": compute_first_reject_blockers(chain),
        "cooccurrence": compute_blocker_cooccurrence(chain),
        "multi_blocker_rate": compute_multi_blocker_rate(chain),
        "missingvalue_taxonomy": compute_missingvalue_taxonomy(mv_df),
        "projects_closest": compute_projects_closest_to_completion(projects),
        "project_summary": projects,
    }


def print_report(report: dict[str, Any]) -> None:
    """Print a human-readable summary of the prioritization report."""
    print("=" * 70)
    print("APPLICABILITY GAP PRIORITIZATION")
    print("=" * 70)
    print()
    print(
        f"Multi-blocker rate: {report['multi_blocker_rate']:.1%} of blocked "
        f"assertions hit >=2 filters"
    )
    print()
    print("--- First-reject blockers (shadowing-aware) ---")
    print(format_blocker_table(report["blockers"]))
    print()
    print("--- Top blocker co-occurrences ---")
    cooc = report["cooccurrence"].head(10)
    for _, r in cooc.iterrows():
        second = r["second_blocker"] or "(none)"
        print(f"  {r['first_blocker']:<22} + {second:<22} {r['count']:>6}")
    print()
    print("--- MissingValue taxonomy (MUT-identification failure modes) ---")
    tax = report["missingvalue_taxonomy"]
    for _, r in tax.iterrows():
        print(f"  {r['category']:<45} {r['count']:>6} ({r['pct']:>5}%)")
    print()
    print("--- Projects closest to completion (top 15) ---")
    proj = report["projects_closest"].head(15)
    for _, r in proj.iterrows():
        print(
            f"  {r['project_name']:<45} {r['included_assertions']:>4}/"
            f"{r['total_assertions']:<4} ({r['pct_included']:>5}%)"
        )
    print()


# =============================================================================
# Project summary (kept at the end; uses a SQL query)
# =============================================================================


def get_project_summary(conn: Connection) -> pd.DataFrame:
    """Per-project assertion inclusion summary.

    Columns: project_id, project_name, total_assertions, included_assertions,
    pct_included.
    """
    sql = text(
        """
        SELECT
            p.id AS project_id,
            p.root_path,
            COUNT(DISTINCT a.id) AS total_assertions,
            COUNT(DISTINCT a.id) FILTER (WHERE a.is_included) AS included_assertions
        FROM project p
        JOIN assertion a ON a.project_id = p.id
        GROUP BY p.id, p.root_path
        ORDER BY included_assertions DESC, total_assertions DESC
        """
    )
    df = pd.read_sql(sql, conn)
    df["project_name"] = df["root_path"].str.rsplit("/", n=1).str[-1]
    df["pct_included"] = (
        df["included_assertions"] / df["total_assertions"].replace(0, pd.NA) * 100
    ).round(1)
    df = df.drop(columns=["root_path"])
    return df


# =============================================================================
# Pipeline failure funnel
# =============================================================================


def get_pipeline_failure_funnel(conn: Connection) -> pd.DataFrame:
    """Per-project first-failure stage: which stage kills each project.

    Columns: project_id, first_failed_stage, first_failed_step.
    Projects with no failures are excluded.
    """
    sql = text(
        """
        WITH first_failure AS (
            SELECT project_id, stage, step,
                ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY step) AS rn
            FROM task
            WHERE status = 'FAILED'
        )
        SELECT project_id, stage AS first_failed_stage, step AS first_failed_step
        FROM first_failure
        WHERE rn = 1
        ORDER BY step
        """
    )
    return pd.read_sql(sql, conn)


def compute_stage_failure_summary(funnel_df: pd.DataFrame) -> pd.DataFrame:
    """Count how many projects fail at each pipeline stage (first failure).

    Columns: first_failed_stage, first_failed_step, n_projects, pct.
    """
    counter = Counter(
        (row["first_failed_stage"], row["first_failed_step"])
        for _, row in funnel_df.iterrows()
    )
    rows = [
        {
            "first_failed_stage": stage,
            "first_failed_step": step,
            "n_projects": count,
        }
        for (stage, step), count in counter.items()
    ]
    counts = pd.DataFrame(
        rows, columns=["first_failed_stage", "first_failed_step", "n_projects"]
    ).sort_values("first_failed_step", ignore_index=True)
    total = counts["n_projects"].sum()
    counts["pct"] = (counts["n_projects"] / total * 100).round(1)
    return counts


def get_stage_failure_causes(conn: Connection, stage: str) -> pd.DataFrame:
    """Root-cause breakdown for one pipeline stage's failures.

    Columns: cause_snippet, n.
    """
    sql = text(
        """
        SELECT left(info, 80) AS cause_snippet, count(*) AS n
        FROM task
        WHERE stage = :stage AND status = 'FAILED'
        GROUP BY cause_snippet
        ORDER BY n DESC
        """
    )
    return pd.read_sql(sql, conn, params={"stage": stage})
