"""RepoReapers rerun barrier snapshot report.

Run from the repository root, for example:

    uv run --directory analysis python -m teralizer.reporeapers_rerun_report \
        --corpus real-world

The report is intentionally read-only. It summarizes the pipeline evidence we
use for planning: telemetry integrity, the filter funnel by stable reason
code, SPF/spec extraction losses, build failure causes, true end-to-end
yield, and assertion semantics. Telemetry-backed sections
skip with an explicit note against snapshots that predate the tables.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.corpora import open_corpus
from teralizer.exports import save_csv_data
from teralizer.report_basis import print_basis_header

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


def _column_exists(conn: Connection, table: str, column: str) -> bool:
    """True when the snapshot's table carries the given column."""
    df = _read_sql(
        conn,
        """
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = :table
          AND column_name = :column
        """,
        table=table,
        column=column,
    )
    return not df.empty


_SKIP_NOTE = "(section skipped: snapshot predates table '{table}')"


_GENERIC_BUDGET = 0.30


def _budget_row(
    invariant: str, actual: float, expected: float = _GENERIC_BUDGET
) -> dict[str, Any]:
    """Build one generic-code budget invariant row."""
    return {
        "invariant": invariant,
        "actual": actual,
        "expected": expected,
        "holds": actual <= expected,
    }


def _skip_row(table: str) -> dict[str, Any]:
    """Build one predates-snapshot note row for the integrity section."""
    return {"invariant": _SKIP_NOTE.format(table=table)}


def _generic_budget_rows(conn: Connection) -> list[dict[str, Any]]:
    """Return generic-code share invariants for lossy telemetry buckets."""
    rows: list[dict[str, Any]] = []

    if _table_exists(conn, "task_diagnostic"):
        task = _read_sql(
            conn,
            """
            SELECT
                count(*) AS total,
                sum(CASE WHEN reason_code = 'OTHER_COMPILE_FAILURE' THEN 1 ELSE 0 END) AS generic
            FROM task_diagnostic
            WHERE stage LIKE 'BUILD_%'
            """,
        )
        total = int(task.iloc[0]["total"] or 0)
        generic = int(task.iloc[0]["generic"] or 0)
        rows.append(
            _budget_row(
                "generic budget: task_diagnostic BUILD_% OTHER_COMPILE_FAILURE share",
                generic / total if total else 0.0,
            )
        )
    else:
        rows.append(_skip_row("task_diagnostic"))

    if _table_exists(conn, "jpf_extraction_summary"):
        failure_counts = _read_sql(
            conn,
            "SELECT failure_counts FROM jpf_extraction_summary",
        )
        total = 0
        generic = 0
        for raw_counts in failure_counts["failure_counts"]:
            counts = raw_counts
            if isinstance(raw_counts, str):
                counts = json.loads(raw_counts)
            if not isinstance(counts, dict):
                continue
            for cause, count in counts.items():
                failures = int(count or 0)
                total += failures
                if cause == "UNCAUGHT_EXCEPTION_PATH":
                    generic += failures
        rows.append(
            _budget_row(
                "generic budget: jpf_extraction_summary UNCAUGHT_EXCEPTION_PATH failure_counts share",
                generic / total if total else 0.0,
            )
        )
    else:
        rows.append(_skip_row("jpf_extraction_summary"))

    if _table_exists(conn, "assertion_semantics"):
        semantics = _read_sql(
            conn,
            """
            SELECT
                count(*) AS total,
                sum(CASE WHEN semantic_kind = 'UNKNOWN' THEN 1 ELSE 0 END) AS generic
            FROM assertion_semantics
            """,
        )
        total = int(semantics.iloc[0]["total"] or 0)
        generic = int(semantics.iloc[0]["generic"] or 0)
        rows.append(
            _budget_row(
                "generic budget: assertion_semantics UNKNOWN semantic_kind share",
                generic / total if total else 0.0,
            )
        )
    else:
        rows.append(_skip_row("assertion_semantics"))

    if _table_exists(conn, "filter_result") and _column_exists(
        conn, "filter_result", "reason_code"
    ):
        filters = _read_sql(
            conn,
            """
            SELECT
                count(*) AS total,
                sum(CASE WHEN reason_code IS NULL OR reason_code = '<none>' THEN 1 ELSE 0 END) AS generic
            FROM filter_result
            WHERE decision = 'REJECT'
            """,
        )
        total = int(filters.iloc[0]["total"] or 0)
        generic = int(filters.iloc[0]["generic"] or 0)
        rows.append(
            _budget_row(
                "generic budget: filter_result REJECT NULL/<none> reason_code share",
                generic / total if total else 0.0,
            )
        )
    elif _table_exists(conn, "filter_result"):
        rows.append(_skip_row("filter_result.reason_code"))
    else:
        rows.append(_skip_row("filter_result"))

    return rows


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
    budget = pd.DataFrame(_generic_budget_rows(conn))
    if not budget.empty:
        df = pd.concat([df, budget], ignore_index=True, sort=False)
    return df


def _strings(values: pd.Series) -> list[str]:
    """Return a pandas series as a plain string list for type-checking."""
    return [str(value) for value in values]


def _join_samples(snippets: list[str]) -> str:
    """Join sampled snippets in the compact report format."""
    return " | ".join(
        " ".join(snippet.replace("\\n", " ").split()) for snippet in snippets
    )


def _sample_assertion_sources(
    conn: Connection, bucket_column: str, bucket_values: list[str], where_sql: str
) -> dict[str, str]:
    """Sample short assertion-source snippets for each report bucket."""
    samples: dict[str, str] = {}
    if not bucket_values or not _column_exists(
        conn, "assertion", "assertion_source_code"
    ):
        return samples
    for bucket in bucket_values:
        rows = _read_sql(
            conn,
            f"""
            SELECT substr(a.assertion_source_code, 1, 60) AS snippet
            FROM filter_result fr
            JOIN assertion a ON a.id = fr.assertion_id
            WHERE {where_sql}
              AND {bucket_column} = :bucket
              AND a.assertion_source_code IS NOT NULL
            ORDER BY random()
            LIMIT 3
            """,
            bucket=bucket,
        )
        samples[bucket] = _join_samples(_strings(rows["snippet"]))
    return samples


def _sample_task_messages(conn: Connection, causes: list[str]) -> dict[str, str]:
    """Sample short task-diagnostic messages for each stable SPF cause."""
    samples: dict[str, str] = {}
    if not causes or not _table_exists(conn, "task_diagnostic"):
        return samples
    message_expr = (
        "first_error_message"
        if _column_exists(conn, "task_diagnostic", "first_error_message")
        else "NULL"
    )
    if _column_exists(conn, "task_diagnostic", "detail_json"):
        message_expr = (
            f"coalesce({message_expr}, detail_json ->> 'message', "
            "detail_json ->> 'detail', detail_json::text)"
        )
    for cause in causes:
        rows = _read_sql(
            conn,
            f"""
            SELECT substr({message_expr}, 1, 60) AS snippet
            FROM task_diagnostic
            WHERE reason_code = :cause
              AND {message_expr} IS NOT NULL
            ORDER BY random()
            LIMIT 3
            """,
            cause=cause,
        )
        samples[cause] = _join_samples(_strings(rows["snippet"]))
    return samples


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

    has_reason_code = _column_exists(conn, "filter_result", "reason_code")
    reason_expr = (
        "coalesce(reason_code, '<none>')"
        if has_reason_code
        else "'<predates reason codes>'"
    )
    df = _read_sql(
        conn,
        f"""
        SELECT
            {reason_expr} AS reason_code,
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
        if scope == _ASSERTION_SCOPE and has_reason_code:
            samples = _sample_assertion_sources(
                conn,
                "coalesce(reason_code, '<none>')",
                _strings(df["reason_code"]),
                f"{where} AND fr.decision <> 'ACCEPT'",
            )
            df["sample"] = df["reason_code"].map(samples).fillna("")
        elif scope == _ASSERTION_SCOPE:
            df["sample"] = ""
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


def get_first_cause_attribution(conn: Connection) -> pd.DataFrame:
    """Attribute every excluded assertion to its upstream-most known cause."""
    required_tables = [
        "assertion",
        "assertion_semantics",
        "mut_resolution_observation",
        "filter_result",
        "task_diagnostic",
        "generalization",
        "generalization_lifecycle",
    ]
    for table in required_tables:
        if not _table_exists(conn, table):
            return pd.DataFrame([{"first_cause": _SKIP_NOTE.format(table=table)}])
    if not _column_exists(conn, "filter_result", "reason_code"):
        return pd.DataFrame(
            [{"first_cause": _SKIP_NOTE.format(table="filter_result.reason_code")}]
        )
    if not _column_exists(conn, "generalization_lifecycle", "final_failure_code"):
        return pd.DataFrame(
            [
                {
                    "first_cause": _SKIP_NOTE.format(
                        table="generalization_lifecycle.final_failure_code"
                    )
                }
            ]
        )

    df = _read_sql(
        conn,
        """
        WITH excluded AS (
            SELECT id
            FROM assertion
            WHERE NOT is_included
        ),
        unsupported AS (
            SELECT DISTINCT e.id AS assertion_id
            FROM excluded e
            LEFT JOIN assertion_semantics s ON s.assertion_id = e.id
            LEFT JOIN mut_resolution_observation m ON m.assertion_id = e.id
            WHERE s.semantic_kind NOT IN (
                'BOOLEAN_FALSE', 'BOOLEAN_TRUE', 'EQUALITY', 'VALUE_EQUALITY'
            )
               OR m.no_pick_reason = 'UNSUPPORTED_ASSERTION_SHAPE'
        ),
        mut_abstention AS (
            SELECT e.id AS assertion_id, min(m.no_pick_reason) AS no_pick_reason
            FROM excluded e
            JOIN mut_resolution_observation m ON m.assertion_id = e.id
            WHERE m.no_pick_reason IS NOT NULL
              AND m.no_pick_reason <> 'UNSUPPORTED_ASSERTION_SHAPE'
            GROUP BY e.id
        ),
        first_filter AS (
            SELECT assertion_id, reason_code
            FROM (
                SELECT fr.assertion_id,
                       coalesce(fr.reason_code, '<none>') AS reason_code,
                       row_number() OVER (
                           PARTITION BY fr.assertion_id ORDER BY fr.id
                       ) AS rank
                FROM filter_result fr
                WHERE fr.assertion_id IS NOT NULL
                  AND fr.generalization_id IS NULL
                  AND fr.decision = 'REJECT'
            ) ranked
            WHERE rank = 1
        ),
        spf_loss AS (
            SELECT assertion_id, min(reason_code) AS reason_code
            FROM task_diagnostic
            WHERE assertion_id IS NOT NULL
              AND reason_code IS NOT NULL
            GROUP BY assertion_id
        ),
        license_refusal AS (
            SELECT DISTINCT assertion_id
            FROM generalization
            WHERE exclusion_info IS NOT NULL
              AND upper(exclusion_info) LIKE '%%LICENSE%%'
        ),
        lifecycle_failure AS (
            SELECT g.assertion_id, min(gl.final_failure_code) AS final_failure_code
            FROM generalization g
            JOIN generalization_lifecycle gl ON gl.generalization_id = g.id
            WHERE gl.final_failure_code IS NOT NULL
              AND gl.final_failure_code <> '<none>'
            GROUP BY g.assertion_id
        ),
        attributed AS (
            SELECT
                e.id,
                CASE
                    WHEN u.assertion_id IS NOT NULL THEN 'assertion-kind-unsupported'
                    WHEN ma.no_pick_reason IS NOT NULL THEN 'MUT-resolution abstention: ' || ma.no_pick_reason
                    WHEN ff.reason_code IS NOT NULL THEN 'filter: ' || ff.reason_code
                    WHEN spf.reason_code IS NOT NULL THEN 'SPF loss: ' || spf.reason_code
                    WHEN lr.assertion_id IS NOT NULL THEN 'license refusal'
                    WHEN lf.final_failure_code IS NOT NULL THEN 'lifecycle failure: ' || lf.final_failure_code
                    ELSE 'unattributed'
                END AS first_cause
            FROM excluded e
            LEFT JOIN unsupported u ON u.assertion_id = e.id
            LEFT JOIN mut_abstention ma ON ma.assertion_id = e.id
            LEFT JOIN first_filter ff ON ff.assertion_id = e.id
            LEFT JOIN spf_loss spf ON spf.assertion_id = e.id
            LEFT JOIN license_refusal lr ON lr.assertion_id = e.id
            LEFT JOIN lifecycle_failure lf ON lf.assertion_id = e.id
        ),
        totals AS (
            SELECT count(*) AS excluded_assertions
            FROM excluded
        ),
        counts AS (
            SELECT first_cause, count(*) AS count
            FROM attributed
            GROUP BY first_cause
        )
        SELECT
            first_cause,
            count,
            CASE
                WHEN totals.excluded_assertions = 0 THEN 0.0
                ELSE count * 1.0 / totals.excluded_assertions
            END AS share
        FROM counts, totals
        UNION ALL
        SELECT
            'invariant: attributed excluded assertions',
            (SELECT count(*) FROM attributed),
            CASE
                WHEN excluded_assertions = 0 THEN 1.0
                ELSE (SELECT count(*) FROM attributed) * 1.0 / excluded_assertions
            END
        FROM totals
        ORDER BY first_cause
        """,
    )
    return df


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
    df = _read_sql(
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
    if not df.empty:
        samples = _sample_assertion_sources(
            conn,
            "a.assertion_name",
            _strings(df["assertion_name"]),
            "fr.assertion_id IS NOT NULL "
            "AND fr.generalization_id IS NULL "
            "AND fr.filter_name LIKE '%UnsupportedAssertionFilter' "
            "AND fr.decision = 'REJECT'",
        )
        df["sample"] = df["assertion_name"].map(samples).fillna("")
    return df


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
    df = _read_sql(
        conn,
        """
        SELECT key AS cause, sum(value::int) AS assertions
        FROM jpf_extraction_summary, jsonb_each_text(failure_counts)
        GROUP BY key
        ORDER BY assertions DESC
        """,
    )
    if not df.empty:
        samples = _sample_task_messages(conn, _strings(df["cause"]))
        df["sample"] = df["cause"].map(samples).fillna("")
    return df


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


def get_assertion_semantics_profile(conn: Connection) -> pd.DataFrame:
    """Semantic kind by argument shape, corpus-wide."""
    if not _table_exists(conn, "assertion_semantics"):
        return pd.DataFrame([{"note": _SKIP_NOTE.format(table="assertion_semantics")}])
    return _read_sql(
        conn,
        """
        SELECT semantic_kind, argument_shape, count(*) AS assertions
        FROM assertion_semantics
        GROUP BY semantic_kind, argument_shape
        ORDER BY assertions DESC
        """,
    )


def get_fail_and_matcher_breakdown(conn: Connection) -> pd.DataFrame:
    """fail() contexts and matcher families for the support-work sizing."""
    if not _table_exists(conn, "assertion_semantics"):
        return pd.DataFrame([{"note": _SKIP_NOTE.format(table="assertion_semantics")}])
    return _read_sql(
        conn,
        """
        SELECT
            coalesce(fail_context, '-') AS fail_context,
            coalesce(matcher_family, '-') AS matcher_family,
            coalesce(matcher_name, '-') AS matcher_name,
            count(*) AS assertions
        FROM assertion_semantics
        WHERE fail_context IS NOT NULL OR matcher_family IS NOT NULL
        GROUP BY fail_context, matcher_family, matcher_name
        ORDER BY assertions DESC
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
        "first_cause_attribution": get_first_cause_attribution(conn),
        "parameter_signatures": get_parameter_signature_counts(conn, top),
        "return_types": get_return_type_counts(conn, top),
        "unsupported_assertions": get_unsupported_assertion_counts(conn, top),
        "missing_value_shapes": get_missing_value_shapes(conn),
        "spf_extraction_rollup": get_spf_extraction_rollup(conn),
        "spf_failure_causes": get_spf_failure_causes(conn),
        "task_diagnostics": get_task_diagnostics(conn),
        "true_yield": get_true_yield(conn),
        "yield_gap_causes": get_yield_gap_causes(conn),
        "assertion_semantics_profile": get_assertion_semantics_profile(conn),
        "fail_matcher_breakdown": get_fail_and_matcher_breakdown(conn),
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
        ("First-cause attribution", "first_cause_attribution", None),
        ("ParameterType reject parameter metadata", "parameter_signatures", top),
        ("ReturnType reject return types", "return_types", top),
        ("Unsupported assertion names", "unsupported_assertions", top),
        ("MissingValue reject metadata shapes", "missing_value_shapes", None),
        ("SPF extraction funnel (per-test rollup)", "spf_extraction_rollup", None),
        ("SPF extraction losses by stable cause", "spf_failure_causes", None),
        ("Failed-task reason codes (all stages)", "task_diagnostics", None),
        ("True end-to-end yield (lifecycle)", "true_yield", None),
        ("Yield gap causes (stage, code, jqwik outcome)", "yield_gap_causes", None),
        ("Assertion semantics (kind by shape)", "assertion_semantics_profile", None),
        ("fail() contexts and matcher families", "fail_matcher_breakdown", top),
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
        "--corpus",
        default="real-world",
        help="registered corpus to inspect (default: real-world)",
    )
    parser.add_argument(
        "--ledger",
        type=Path,
        default=None,
        help="optional attempt ledger for the basis header done-marker progress",
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
    with open_corpus(args.corpus) as (entry, conn):
        print_basis_header(conn, entry.database, ledger=args.ledger)
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
