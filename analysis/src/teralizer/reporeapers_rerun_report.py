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


def get_spf_extraction_rollup(conn: Connection) -> pd.DataFrame:
    """Corpus-wide SPF extraction funnel from the per-test rollup telemetry."""
    if not _table_exists(conn, "jpf_extraction_summary"):
        return pd.DataFrame(
            [{"note": _SKIP_NOTE.format(table="jpf_extraction_summary")}]
        )
    return _read_sql(
        conn,
        """
        SELECT
            sum(assertions_scheduled) AS scheduled,
            sum(assertions_instrumented) AS instrumented,
            sum(assertions_jpf_succeeded) AS jpf_succeeded,
            sum(assertions_jpf_failed) AS jpf_failed,
            sum(assertions_with_input_spec) AS with_input_spec,
            sum(assertions_with_output_spec) AS with_output_spec,
            sum(assertions_with_complete_spec) AS with_complete_spec
        FROM jpf_extraction_summary
        """,
    )


def get_spf_failure_causes(conn: Connection) -> pd.DataFrame:
    """Ranked stable-cause table for SPF extraction losses."""
    if not _table_exists(conn, "jpf_extraction_summary"):
        return pd.DataFrame(
            [{"note": _SKIP_NOTE.format(table="jpf_extraction_summary")}]
        )
    return _read_sql(
        conn,
        """
        SELECT key AS cause, sum(value::int) AS assertions
        FROM jpf_extraction_summary, jsonb_each_text(failure_counts)
        GROUP BY key
        ORDER BY assertions DESC
        """,
    )


def get_task_diagnostics(conn: Connection) -> pd.DataFrame:
    """Stable reason codes for failed tasks, all stages."""
    if not _table_exists(conn, "task_diagnostic"):
        return pd.DataFrame([{"note": _SKIP_NOTE.format(table="task_diagnostic")}])
    return _read_sql(
        conn,
        """
        SELECT stage, reason_code, count(*) AS tasks
        FROM task_diagnostic
        GROUP BY stage, reason_code
        ORDER BY tasks DESC
        """,
    )


def get_true_yield(conn: Connection) -> pd.DataFrame:
    """End-to-end yield: included vs final_usable, with the gap explained.

    generalization.is_included is not final success. The lifecycle flags say
    where each included generalization actually ended, and the jqwik outcome
    kinds explain execution-stage losses.
    """
    if not _table_exists(conn, "generalization_lifecycle"):
        return pd.DataFrame(
            [{"note": _SKIP_NOTE.format(table="generalization_lifecycle")}]
        )
    return _read_sql(
        conn,
        """
        SELECT
            (SELECT count(*) FROM generalization) AS generalizations,
            (SELECT count(*) FROM generalization WHERE is_included) AS included,
            count(*) FILTER (WHERE gl.generated_project_compiled) AS compiled,
            count(*) FILTER (WHERE gl.generated_tests_executed) AS executed,
            count(*) FILTER (WHERE gl.generated_report_collected) AS report_collected,
            count(*) FILTER (WHERE gl.generated_filter_passed) AS filter_passed,
            count(*) FILTER (WHERE gl.final_usable) AS final_usable
        FROM generalization_lifecycle gl
        """,
    )


def get_yield_gap_causes(conn: Connection) -> pd.DataFrame:
    """Failure stage/code breakdown for non-usable lifecycle rows, plus jqwik outcomes."""
    if not _table_exists(conn, "generalization_lifecycle"):
        return pd.DataFrame(
            [{"note": _SKIP_NOTE.format(table="generalization_lifecycle")}]
        )
    return _read_sql(
        conn,
        """
        SELECT
            coalesce(gl.final_failure_stage, '<none>') AS failure_stage,
            coalesce(gl.final_failure_code, '<none>') AS failure_code,
            coalesce(jpe.diagnostic_kind, '<no jqwik outcome>') AS jqwik_outcome,
            count(*) AS generalizations
        FROM generalization_lifecycle gl
        LEFT JOIN jqwik_property_execution jpe
               ON jpe.generalization_id = gl.generalization_id
        WHERE NOT gl.final_usable
        GROUP BY failure_stage, failure_code, jqwik_outcome
        ORDER BY generalizations DESC
        """,
    )


def get_build_failure_causes(conn: Connection) -> pd.DataFrame:
    """Build-stage failures joined with the build-environment telemetry.

    The level-mismatch column derives the Java-8 generated-source blocker
    directly: a failed generalized build whose project compiles below the
    level the generated source requires.
    """
    if not _table_exists(conn, "build_environment_observation"):
        return pd.DataFrame(
            [{"note": _SKIP_NOTE.format(table="build_environment_observation")}]
        )
    return _read_sql(
        conn,
        """
        SELECT
            td.stage,
            td.reason_code,
            coalesce(beo.compiler_source, '<unknown>') AS compiler_source,
            coalesce(beo.generated_source_required_level, '-') AS generated_level,
            CASE
                WHEN beo.generated_source_required_level IS NOT NULL
                     AND beo.compiler_source ~ '^[0-9.]+$'
                     AND string_to_array(beo.compiler_source, '.')::int[]
                         < string_to_array(beo.generated_source_required_level, '.')::int[]
                    THEN 'generated level above project source'
                WHEN beo.generated_source_required_level IS NOT NULL
                     AND beo.compiler_source !~ '^[0-9.]+$'
                    THEN 'source level unresolved (build property)'
                ELSE '-'
            END AS level_mismatch,
            count(DISTINCT td.project_id) AS projects
        FROM task_diagnostic td
        LEFT JOIN build_environment_observation beo
               ON beo.project_id = td.project_id AND beo.stage = td.stage
        WHERE td.stage LIKE 'BUILD_%'
        GROUP BY td.stage, td.reason_code, compiler_source, generated_level,
                 level_mismatch
        ORDER BY projects DESC
        """,
    )


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------


def generate_report(conn: Connection, top: int) -> dict[str, pd.DataFrame]:
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
        "spf_extraction_rollup": get_spf_extraction_rollup(conn),
        "spf_failure_causes": get_spf_failure_causes(conn),
        "task_diagnostics": get_task_diagnostics(conn),
        "true_yield": get_true_yield(conn),
        "yield_gap_causes": get_yield_gap_causes(conn),
        "build_failure_causes": get_build_failure_causes(conn),
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
        ("SPF extraction funnel (per-test rollup)", "spf_extraction_rollup", None),
        ("SPF extraction losses by stable cause", "spf_failure_causes", None),
        ("Failed-task reason codes (all stages)", "task_diagnostics", None),
        ("True end-to-end yield (lifecycle)", "true_yield", None),
        ("Yield gap causes (stage, code, jqwik outcome)", "yield_gap_causes", None),
        (
            "Build failure causes (telemetry)",
            "build_failure_causes",
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
        "--csv-prefix",
        help="optional prefix for saving every report table under analysis/output/<variant>/data",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    with db_config.get_engine(args.db, validate=False).connect() as conn:
        report = generate_report(conn, args.top)
    print_report(report, args.top)
    if args.csv_prefix:
        paths = export_report(report, args.csv_prefix)
        print()
        print("## CSV exports")
        for path in paths:
            print(path)


if __name__ == "__main__":
    main()
