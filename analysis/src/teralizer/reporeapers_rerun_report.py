"""RepoReapers rerun barrier snapshot report.

Run from the repository root, for example:

    uv run --directory analysis python -m teralizer.reporeapers_rerun_report \
        --db postgres_reporeapers_rerun2

The report is intentionally read-only. It summarizes the pipeline evidence we
use for planning: telemetry integrity, the filter funnel by stable reason
code, SPF/spec extraction losses, build failure causes, true end-to-end
yield, assertion semantics, and baseline deltas. Telemetry-backed sections
skip with an explicit note against snapshots that predate the tables.
"""

from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path
from typing import Any

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.config import db_config
from teralizer.exports import save_csv_data

_ASSERTION_SCOPE = "assertion"
_TEST_SCOPE = "test"

_FILTER_ALIASES = {
    "AssertionInLoopFilter": "AssertionInLoop",
    "AssertionInMethodFilter": "AssertionInMethod",
    "ExcludedAssertionFilter": "ExcludedAssertion",
    "ExcludedTestFilter": "ExcludedTest",
    "MissingValueFilter": "MissingValue",
    "NestedClassesFilter": "NestedClasses",
    "NoAssertionsFilter": "NoAssertions",
    "NonPassingTestFilter": "NonPassingTest",
    "ParameterTypeFilter": "ParameterType",
    "ReturnTypeFilter": "ReturnType",
    "StaticInitializersFilter": "StaticInitializers",
    "TestedMethodInLoopFilter": "TestedMethodInLoop",
    "TestTypeFilter": "TestType",
    "UnnamedPackageFilter": "UnnamedPackage",
    "UnsupportedAssertionFilter": "UnsupportedAssertion",
}

_OUTPUT_PATH = re.compile(r"Output:\s*(?P<path>[^\s]+)")


def _short_filter(fq_name: str) -> str:
    """Return a readable filter name from a fully qualified class name."""
    simple = fq_name.rsplit(".", 1)[-1]
    return _FILTER_ALIASES.get(simple, simple)


def _read_sql(conn: Connection, sql: str, **params: Any) -> pd.DataFrame:
    """Run one read-only SQL query into a DataFrame."""
    return pd.read_sql(text(sql), conn, params=params)


def _table_exists(conn: Connection, table: str) -> bool:
    """True when the snapshot carries the given telemetry table."""
    df = _read_sql(
        conn,
        """
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = :table
        """,
        table=table,
    )
    return not df.empty


_SKIP_NOTE = "(section skipped: snapshot predates table '{table}')"


def get_telemetry_integrity(conn: Connection) -> pd.DataFrame:
    """Totality invariants over the telemetry writers.

    A violated invariant means a writer silently broke and every downstream
    section is suspect, so this table prints first.
    """
    if not _table_exists(conn, "mut_resolution_observation"):
        return pd.DataFrame(
            [{"invariant": _SKIP_NOTE.format(table="mut_resolution_observation")}]
        )
    df = _read_sql(
        conn,
        """
        WITH counts AS (
            SELECT
                (SELECT count(*) FROM assertion) AS assertions,
                (SELECT count(*) FROM mut_resolution_observation) AS mut_obs,
                (SELECT count(*) FROM assertion_semantics) AS semantics,
                (SELECT count(*) FROM generalization) AS generalizations,
                (SELECT count(*) FROM generalization_lifecycle) AS lifecycle,
                (SELECT count(*) FROM generalization_lifecycle WHERE NOT generated_source_created) AS lifecycle_unsourced,
                (SELECT count(*) FROM generalization_lifecycle WHERE final_usable) AS final_usable,
                (SELECT count(*) FROM jqwik_property_execution) AS jqwik_outcomes
        )
        SELECT
            'mut_resolution_observation total (== assertions)' AS invariant,
            mut_obs AS actual, assertions AS expected, mut_obs = assertions AS holds
        FROM counts
        UNION ALL
        SELECT 'assertion_semantics total (== assertions)',
               semantics, assertions, semantics = assertions
        FROM counts
        UNION ALL
        SELECT 'lifecycle rows only for generated sources (0 unsourced)',
               lifecycle_unsourced, 0, lifecycle_unsourced = 0
        FROM counts
        UNION ALL
        SELECT 'lifecycle rows within generalizations (<=)',
               lifecycle, generalizations, lifecycle <= generalizations
        FROM counts
        UNION ALL
        SELECT 'jqwik outcome rows cover final_usable (>=)',
               jqwik_outcomes, final_usable, jqwik_outcomes >= final_usable
        FROM counts
        """,
    )
    return df


# ---------------------------------------------------------------------------
# Query helpers
# ---------------------------------------------------------------------------


def get_run_progress(conn: Connection) -> dict[str, pd.DataFrame]:
    """Return high-level run-progress tables."""
    projects = _read_sql(
        conn,
        """
        SELECT
            count(*) AS projects_total,
            count(*) FILTER (WHERE runtime IS NOT NULL) AS projects_with_runtime
        FROM project
        """,
    )
    active_tasks = _read_sql(
        conn,
        """
        SELECT id, stage, project_id, status
        FROM task
        WHERE status = 'IN_PROGRESS'
        ORDER BY id
        """,
    )
    tool_versions = _read_sql(
        conn,
        """
        SELECT coalesce(tool_git_version, '<null>') AS tool_git_version, count(*) AS count
        FROM project
        GROUP BY tool_git_version
        ORDER BY count DESC
        """,
    )
    return {
        "projects": projects,
        "active_tasks": active_tasks,
        "tool_versions": tool_versions,
    }


def get_entity_counts(conn: Connection) -> pd.DataFrame:
    """Count test/assertion/generalization inclusion flags."""
    return _read_sql(
        conn,
        """
        SELECT 'test' AS entity,
               count(*) AS total,
               count(*) FILTER (WHERE is_included) AS included,
               count(*) FILTER (WHERE NOT is_included) AS excluded
        FROM test
        UNION ALL
        SELECT 'assertion', count(*),
               count(*) FILTER (WHERE is_included),
               count(*) FILTER (WHERE NOT is_included)
        FROM assertion
        UNION ALL
        SELECT 'generalization', count(*),
               count(*) FILTER (WHERE is_included),
               count(*) FILTER (WHERE NOT is_included)
        FROM generalization
        ORDER BY entity
        """,
    )


def get_project_stage_summary(conn: Connection) -> pd.DataFrame:
    """Project-level pipeline status counts by stage."""
    return _read_sql(
        conn,
        """
        SELECT stage, status, count(*) AS count
        FROM task
        WHERE test_id IS NULL
          AND assertion_id IS NULL
          AND generalization_id IS NULL
        GROUP BY stage, status
        ORDER BY min(step), status
        """,
    )


def get_filter_summary(conn: Connection, scope: str) -> pd.DataFrame:
    """Filter decision counts for test or assertion rows."""
    if scope == _TEST_SCOPE:
        where = (
            "test_id IS NOT NULL AND assertion_id IS NULL AND generalization_id IS NULL"
        )
        distinct_id = "test_id"
        label = "tests"
    elif scope == _ASSERTION_SCOPE:
        where = "assertion_id IS NOT NULL AND generalization_id IS NULL"
        distinct_id = "assertion_id"
        label = "assertions"
    else:
        raise ValueError(f"Unknown filter scope: {scope}")

    df = _read_sql(
        conn,
        f"""
        SELECT
            coalesce(reason_code, '<none>') AS reason_code,
            filter_name,
            decision,
            count(*) AS rows,
            count(DISTINCT {distinct_id}) AS {label}
        FROM filter_result
        WHERE {where}
          AND decision <> 'ACCEPT'
        GROUP BY reason_code, filter_name, decision
        ORDER BY decision DESC, {label} DESC, reason_code
        """,
    )
    if not df.empty:
        df["filter_name"] = df["filter_name"].map(_short_filter)
    return df


def get_test_blocker_combos(conn: Connection, top: int) -> pd.DataFrame:
    """Top test-level REJECT combinations."""
    df = _read_sql(
        conn,
        """
        WITH blockers AS (
            SELECT test_id, string_agg(filter_name, '+' ORDER BY filter_name) AS combo
            FROM filter_result
            WHERE test_id IS NOT NULL
              AND assertion_id IS NULL
              AND generalization_id IS NULL
              AND decision = 'REJECT'
            GROUP BY test_id
        )
        SELECT combo, count(*) AS tests
        FROM blockers
        GROUP BY combo
        ORDER BY tests DESC
        LIMIT :top
        """,
        top=top,
    )
    if not df.empty:
        df["combo"] = df["combo"].map(_short_combo)
    return df


def get_assertion_blocker_combos(conn: Connection, top: int) -> pd.DataFrame:
    """Top assertion-level REJECT combinations."""
    df = _read_sql(
        conn,
        """
        WITH blockers AS (
            SELECT
                assertion_id,
                string_agg(filter_name, '+' ORDER BY filter_name) AS combo
            FROM filter_result
            WHERE assertion_id IS NOT NULL
              AND generalization_id IS NULL
              AND decision = 'REJECT'
            GROUP BY assertion_id
        )
        SELECT combo, count(*) AS assertions
        FROM blockers
        GROUP BY combo
        ORDER BY assertions DESC
        LIMIT :top
        """,
        top=top,
    )
    if not df.empty:
        df["combo"] = df["combo"].map(_short_combo)
    return df


def _short_combo(combo: str) -> str:
    """Shorten a '+ '-separated fully qualified filter combo."""
    return " + ".join(_short_filter(part) for part in combo.split("+"))


def get_assertion_exclusion_sources(conn: Connection) -> pd.DataFrame:
    """Explain excluded assertions as filter vs SPF/instrumentation exclusions."""
    return _read_sql(
        conn,
        """
        WITH rejected AS (
            SELECT DISTINCT assertion_id
            FROM filter_result
            WHERE assertion_id IS NOT NULL
              AND generalization_id IS NULL
              AND decision = 'REJECT'
        )
        SELECT
            CASE
                WHEN r.assertion_id IS NOT NULL THEN 'filter reject'
                WHEN a.exclusion_info LIKE '%JpfExecutionTask%' THEN 'JPF execution failure'
                WHEN a.exclusion_info LIKE '%JpfInstrumentationTask%' THEN 'JPF instrumentation failure'
                ELSE 'other non-filter exclusion'
            END AS source,
            count(*) AS count
        FROM assertion a
        LEFT JOIN rejected r ON r.assertion_id = a.id
        WHERE NOT a.is_included
        GROUP BY source
        ORDER BY count DESC
        """,
    )


def get_parameter_signature_counts(conn: Connection, top: int) -> pd.DataFrame:
    """Parameter metadata among ParameterType rejects."""
    return _read_sql(
        conn,
        """
        SELECT coalesce(a.tested_method_parameters, '<null>') AS params, count(*) AS count
        FROM filter_result fr
        JOIN assertion a ON a.id = fr.assertion_id
        WHERE fr.assertion_id IS NOT NULL
          AND fr.generalization_id IS NULL
          AND fr.filter_name LIKE '%ParameterTypeFilter'
          AND fr.decision = 'REJECT'
        GROUP BY params
        ORDER BY count DESC
        LIMIT :top
        """,
        top=top,
    )


def get_return_type_counts(conn: Connection, top: int) -> pd.DataFrame:
    """Return types among ReturnType rejects."""
    return _read_sql(
        conn,
        """
        SELECT coalesce(a.tested_method_return_type, '<null>') AS return_type, count(*) AS count
        FROM filter_result fr
        JOIN assertion a ON a.id = fr.assertion_id
        WHERE fr.assertion_id IS NOT NULL
          AND fr.generalization_id IS NULL
          AND fr.filter_name LIKE '%ReturnTypeFilter'
          AND fr.decision = 'REJECT'
        GROUP BY return_type
        ORDER BY count DESC
        LIMIT :top
        """,
        top=top,
    )


def get_unsupported_assertion_counts(conn: Connection, top: int) -> pd.DataFrame:
    """Assertion names among UnsupportedAssertion rejects."""
    return _read_sql(
        conn,
        """
        SELECT a.assertion_name, count(*) AS count
        FROM filter_result fr
        JOIN assertion a ON a.id = fr.assertion_id
        WHERE fr.assertion_id IS NOT NULL
          AND fr.generalization_id IS NULL
          AND fr.filter_name LIKE '%UnsupportedAssertionFilter'
          AND fr.decision = 'REJECT'
        GROUP BY a.assertion_name
        ORDER BY count DESC
        LIMIT :top
        """,
        top=top,
    )


def get_missing_value_shapes(conn: Connection) -> pd.DataFrame:
    """Metadata shape among MissingValue rejects."""
    return _read_sql(
        conn,
        """
        SELECT
            CASE WHEN a.tested_method_name IS NULL THEN 'no method name' ELSE 'has method name' END AS method_name,
            CASE WHEN a.tested_class_name IS NULL THEN 'no class name' ELSE 'has class name' END AS class_name,
            CASE WHEN a.tested_method_parameters IS NULL THEN 'no params metadata' ELSE 'has params metadata' END AS params,
            count(*) AS count
        FROM filter_result fr
        JOIN assertion a ON a.id = fr.assertion_id
        WHERE fr.filter_name LIKE '%MissingValueFilter'
          AND fr.decision = 'REJECT'
        GROUP BY method_name, class_name, params
        ORDER BY count DESC
        """,
    )


def classify_assertion_failure_info(stage: str, info: str | None) -> str:
    """Coarse root-cause bucket for assertion-level failed tasks."""
    text_value = info or ""
    if stage == "ADD_JPF_INSTRUMENTATION":
        if "Failed to identify valid type for parameter _target_" in text_value:
            return "unsupported receiver/_target_ type"
        if "TestAnalysis.isJUnit4Assertion" in text_value:
            return "instrumentation NPE in assertion cleanup"
        return "other instrumentation failure"

    if "Class.getProtectionDomain" in text_value:
        return "missing JPF model: Class.getProtectionDomain"
    if "Failed JPF execution due to exception in native peers" in text_value:
        return "native peer exception"
    if "UnsatisfiedLinkError" in text_value:
        return "missing native peer / native method"
    if "Unexpected initialization failure" in text_value:
        return "library static init failure"
    if (
        "ClassNotFoundException: class not found: java.lang.NoSuchMethodException"
        in text_value
    ):
        return "missing JPF model: NoSuchMethodException class"
    if "NoSuchMethodError" in text_value:
        return "JPF runtime method gap"
    if "SEARCH_DEPTH_LIMIT" in text_value:
        return "search depth limit"
    if "ArithmeticException" in text_value:
        return "uncaught arithmetic exception path"
    if "NullPointerException" in text_value:
        return "NPE during JPF/extraction"
    return "other"


def get_assertion_failure_causes(conn: Connection) -> pd.DataFrame:
    """Categorize assertion-level failed instrumentation/SPF tasks."""
    failures = _read_sql(
        conn,
        """
        SELECT stage, info
        FROM task
        WHERE assertion_id IS NOT NULL
          AND generalization_id IS NULL
          AND status = 'FAILED'
        """,
    )
    if failures.empty:
        return pd.DataFrame(columns=["stage", "cause", "count"])

    failures["cause"] = failures.apply(
        lambda row: classify_assertion_failure_info(row["stage"], row["info"]), axis=1
    )
    counts = Counter(
        (str(row["stage"]), str(row["cause"])) for _, row in failures.iterrows()
    )
    rows = [
        {"stage": stage, "cause": cause, "count": count}
        for (stage, cause), count in counts.items()
    ]
    return pd.DataFrame(rows).sort_values(
        ["stage", "count"], ascending=[True, False], ignore_index=True
    )


def get_generated_property_categories(conn: Connection) -> pd.DataFrame:
    """Generated properties by downstream outcome category."""
    return _read_sql(
        conn,
        """
        WITH build_failed_projects AS (
            SELECT project_id
            FROM task
            WHERE stage = 'BUILD_PROJECT_GENERALIZED'
              AND status = 'FAILED'
              AND test_id IS NULL
              AND assertion_id IS NULL
              AND generalization_id IS NULL
        ),
        gen_task_failed AS (
            SELECT generalization_id, min(stage) AS stage
            FROM task
            WHERE generalization_id IS NOT NULL
              AND status = 'FAILED'
            GROUP BY generalization_id
        ),
        gen_filter_reject AS (
            SELECT DISTINCT generalization_id
            FROM filter_result
            WHERE generalization_id IS NOT NULL
              AND decision = 'REJECT'
        )
        SELECT
            CASE
                WHEN b.project_id IS NOT NULL THEN 'project build generalized failed before execution'
                WHEN tf.stage = 'COLLECT_JUNIT_REPORTS_GENERALIZED' THEN 'generalized junit report collection failed'
                WHEN r.generalization_id IS NOT NULL THEN 'generalized test executed but failed'
                ELSE 'generalized test passed filters'
            END AS category,
            count(*) AS count
        FROM generalization g
        LEFT JOIN build_failed_projects b ON b.project_id = g.project_id
        LEFT JOIN gen_task_failed tf ON tf.generalization_id = g.id
        LEFT JOIN gen_filter_reject r ON r.generalization_id = g.id
        GROUP BY category
        ORDER BY count DESC
        """,
    )


def classify_build_log(log: str) -> str:
    """Coarse build-log bucket for generalized build failures."""
    if not log:
        return "missing build log"
    if "not supported in -source 1." in log and (
        "lambda expressions" in log or "method references" in log
    ):
        return "generated Java 8 syntax under pre-Java-8 source level"
    if "COMPILATION ERROR" in log or "Compilation failure" in log:
        return "compilation failure"
    if "Command execution timeout exceeded" in log:
        return "command timeout"
    return "other build failure"


def _extract_output_path(info: str | None) -> Path | None:
    """Extract the command-output path from a task info message."""
    if not info:
        return None
    match = _OUTPUT_PATH.search(info)
    if not match:
        return None
    return Path(match.group("path"))


def get_generalized_build_failure_causes(
    conn: Connection, log_root: Path
) -> pd.DataFrame:
    """Classify generalized build failures using command output logs when present."""
    failures = _read_sql(
        conn,
        """
        SELECT p.root_path, t.info
        FROM task t
        JOIN project p ON p.id = t.project_id
        WHERE t.stage = 'BUILD_PROJECT_GENERALIZED'
          AND t.status = 'FAILED'
          AND t.test_id IS NULL
          AND t.assertion_id IS NULL
          AND t.generalization_id IS NULL
        ORDER BY p.root_path
        """,
    )
    if failures.empty:
        return pd.DataFrame(columns=["cause", "projects"])

    causes: list[str] = []
    for _, row in failures.iterrows():
        output_path = _extract_output_path(row["info"])
        log_text = ""
        if output_path is not None:
            path = output_path if output_path.is_absolute() else log_root / output_path
            if path.exists():
                log_text = path.read_text(errors="replace")
        causes.append(classify_build_log(log_text))

    cause_counts = Counter(causes)
    rows = [
        {"cause": cause, "projects": count} for cause, count in cause_counts.items()
    ]
    return pd.DataFrame(rows).sort_values(
        "projects", ascending=False, ignore_index=True
    )


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------


def generate_report(
    conn: Connection, top: int, log_root: Path
) -> dict[str, pd.DataFrame]:
    """Run all snapshot queries."""
    progress = get_run_progress(conn)
    return {
        "telemetry_integrity": get_telemetry_integrity(conn),
        "run_projects": progress["projects"],
        "active_tasks": progress["active_tasks"],
        "tool_versions": progress["tool_versions"],
        "entity_counts": get_entity_counts(conn),
        "project_stages": get_project_stage_summary(conn),
        "test_filters": get_filter_summary(conn, _TEST_SCOPE),
        "test_blocker_combos": get_test_blocker_combos(conn, top),
        "assertion_filters": get_filter_summary(conn, _ASSERTION_SCOPE),
        "assertion_blocker_combos": get_assertion_blocker_combos(conn, top),
        "assertion_exclusion_sources": get_assertion_exclusion_sources(conn),
        "parameter_signatures": get_parameter_signature_counts(conn, top),
        "return_types": get_return_type_counts(conn, top),
        "unsupported_assertions": get_unsupported_assertion_counts(conn, top),
        "missing_value_shapes": get_missing_value_shapes(conn),
        "assertion_failure_causes": get_assertion_failure_causes(conn),
        "generated_property_categories": get_generated_property_categories(conn),
        "generalized_build_failure_causes": get_generalized_build_failure_causes(
            conn, log_root
        ),
    }


def _format_frame(df: pd.DataFrame, *, limit: int | None = None) -> str:
    """Render a compact DataFrame table for terminal output."""
    if df.empty:
        return "(none)"
    shown = df.head(limit) if limit is not None else df
    return shown.to_string(index=False)


def print_report(report: dict[str, pd.DataFrame], top: int) -> None:
    """Print a human-readable rerun snapshot report."""
    sections = [
        ("Telemetry integrity invariants", "telemetry_integrity", None),
        ("Run progress", "run_projects", None),
        ("Active tasks", "active_tasks", None),
        ("Tool git versions", "tool_versions", None),
        ("Corpus row counts", "entity_counts", None),
        ("Project-level stage outcomes", "project_stages", None),
        ("Test-level filter decisions", "test_filters", None),
        ("Top test blocker combinations", "test_blocker_combos", top),
        ("Assertion-level filter decisions", "assertion_filters", None),
        ("Top assertion blocker combinations", "assertion_blocker_combos", top),
        ("Excluded assertion sources", "assertion_exclusion_sources", None),
        ("ParameterType reject parameter metadata", "parameter_signatures", top),
        ("ReturnType reject return types", "return_types", top),
        ("Unsupported assertion names", "unsupported_assertions", top),
        ("MissingValue reject metadata shapes", "missing_value_shapes", None),
        (
            "Assertion-level SPF/instrumentation failure causes",
            "assertion_failure_causes",
            None,
        ),
        (
            "Generated property downstream outcomes",
            "generated_property_categories",
            None,
        ),
        (
            "Generalized build failure log causes",
            "generalized_build_failure_causes",
            None,
        ),
    ]

    print("# RepoReapers rerun barrier snapshot")
    for title, key, limit in sections:
        print()
        print(f"## {title}")
        print(_format_frame(report[key], limit=limit))


def export_report(report: dict[str, pd.DataFrame], prefix: str) -> list[Path]:
    """Save report tables as CSV via the shared export helper."""
    paths = []
    for name, frame in report.items():
        paths.append(save_csv_data(frame, f"{prefix}-{name}"))
    return paths


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--db",
        default="postgres_reporeapers_rerun",
        help="database to inspect (default: postgres_reporeapers_rerun)",
    )
    parser.add_argument(
        "--top",
        type=int,
        default=15,
        help="number of rows for top-N detail tables",
    )
    parser.add_argument(
        "--log-root",
        type=Path,
        default=Path("."),
        help="repository root used to resolve command-data paths in task.info",
    )
    parser.add_argument(
        "--csv-prefix",
        help="optional prefix for saving every report table under analysis/output/<variant>/data",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    with db_config.get_engine(args.db, validate=False).connect() as conn:
        report = generate_report(conn, args.top, args.log_root.resolve())
    print_report(report, args.top)
    if args.csv_prefix:
        paths = export_report(report, args.csv_prefix)
        print()
        print("## CSV exports")
        for path in paths:
            print(path)


if __name__ == "__main__":
    main()
