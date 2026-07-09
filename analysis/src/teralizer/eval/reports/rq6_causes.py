"""RQ6 real-world exclusion causes report."""

from __future__ import annotations

from dataclasses import replace

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import Metric, Prose, RQReport, Section
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.eval.reports import _funnel
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
)

VARIANT = "IMPROVED_100_TRIES"

FILTERING_SQL = r"""
WITH
    base_data AS (
        SELECT
            CASE
                WHEN fr.test_id IS NOT NULL THEN 'Test'
                WHEN fr.assertion_id IS NOT NULL THEN 'Assertion'
                WHEN fr.generalization_id IS NOT NULL THEN 'Generalization'
            END AS level,
            substring(fr.filter_name from 'filter\.(\w+)Filter$') AS filter_name,
            fr.decision,
            count(*) AS count
        FROM filter_result fr
        JOIN project p ON fr.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY
            fr.filter_name,
            CASE
                WHEN fr.test_id IS NOT NULL THEN 'Test'
                WHEN fr.assertion_id IS NOT NULL THEN 'Assertion'
                WHEN fr.generalization_id IS NOT NULL THEN 'Generalization'
            END,
            fr.decision
    ),
    pivoted AS (
        SELECT
            level,
            filter_name,
            sum(count)::bigint AS total,
            sum(CASE WHEN decision = 'ACCEPT' THEN count ELSE 0 END)::bigint AS accept,
            sum(CASE WHEN decision = 'DEFER' THEN count ELSE 0 END)::bigint AS defer,
            sum(CASE WHEN decision = 'REJECT' THEN count ELSE 0 END)::bigint AS reject
        FROM base_data
        GROUP BY level, filter_name
    )
SELECT
    level,
    CASE
        WHEN filter_name = 'UnsupportedAssertion' THEN 'AssertionType'
        ELSE filter_name
    END AS filter,
    total,
    accept,
    defer,
    reject
FROM pivoted
WHERE reject > 0
  AND level IN ('Test', 'Assertion')
  AND filter_name IS NOT NULL
ORDER BY
    CASE level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    filter
"""

BREAKDOWN_SQL = """
WITH
    test_counts AS (
        SELECT
            'All' AS strategy,
            'Test' AS level,
            CASE
                WHEN t.is_included THEN 'included'
                WHEN EXISTS (
                    SELECT 1
                    FROM filter_result fr
                    WHERE fr.test_id = t.id
                      AND fr.decision = 'REJECT'
                ) OR starts_with(
                    coalesce(t.exclusion_info, ''),
                    'Excluded by TestFilteringTask{'
                ) THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM test t
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY bucket
    ),
    assertion_counts AS (
        SELECT
            'All' AS strategy,
            'Assertion' AS level,
            CASE
                WHEN a.is_included THEN 'included'
                WHEN EXISTS (
                    SELECT 1
                    FROM filter_result fr
                    WHERE fr.assertion_id = a.id
                      AND fr.decision = 'REJECT'
                ) OR starts_with(
                    coalesce(a.exclusion_info, ''),
                    'Excluded by TestFilteringTask{'
                ) THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM assertion a
        JOIN project p ON a.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY bucket
    ),
    generalization_counts AS (
        SELECT
            g.variant AS strategy,
            'Generalization' AS level,
            CASE
                WHEN g.is_included THEN 'included'
                WHEN EXISTS (
                    SELECT 1
                    FROM filter_result fr
                    WHERE fr.generalization_id = g.id
                      AND fr.decision = 'REJECT'
                ) OR starts_with(
                    coalesce(g.exclusion_info, ''),
                    'Excluded by TestFilteringTask{'
                ) THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM generalization g
        JOIN project p ON g.project_id = p.id
        WHERE p.use_test_generalization
          AND g.variant = 'IMPROVED_100_TRIES'
        GROUP BY g.variant, bucket
    ),
    combined AS (
        SELECT strategy, level, bucket, item_count FROM test_counts
        UNION ALL
        SELECT strategy, level, bucket, item_count FROM assertion_counts
        UNION ALL
        SELECT strategy, level, bucket, item_count FROM generalization_counts
    ),
    report_rows AS (
        SELECT 'All' AS strategy, 'Test' AS level
        UNION ALL
        SELECT 'All' AS strategy, 'Assertion' AS level
        UNION ALL
        SELECT 'IMPROVED_100_TRIES' AS strategy, 'Generalization' AS level
    )
SELECT
    report_rows.strategy,
    report_rows.level,
    coalesce(sum(item_count), 0)::bigint AS total,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'included'), 0)::bigint AS included,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'filtering'), 0)::bigint AS filtering,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'failures'), 0)::bigint AS failures
FROM report_rows
LEFT JOIN combined
    ON combined.strategy = report_rows.strategy
   AND combined.level = report_rows.level
GROUP BY report_rows.strategy, report_rows.level
ORDER BY
    CASE report_rows.level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    report_rows.strategy
"""


def _fetch_filtering(conn: Connection) -> pd.DataFrame:
    """Return real-world filter rejection counts for the shared table builder."""
    df = read_sql(conn, FILTERING_SQL)
    for column in ("total", "accept", "defer", "reject"):
        df[column] = df[column].astype(int)
    return pd.DataFrame(
        df, columns=["level", "filter", "total", "accept", "defer", "reject"]
    )


def _fetch_breakdown(conn: Connection) -> pd.DataFrame:
    """Return real-world inclusion, filtering, and failure counts by level."""
    df = read_sql(conn, BREAKDOWN_SQL)
    for column in ("total", "included", "filtering", "failures"):
        df[column] = df[column].astype(int)
    return pd.DataFrame(
        df, columns=["strategy", "level", "total", "included", "filtering", "failures"]
    )


def build(conn: Connection) -> RQReport:
    funnel = _funnel.build_funnel(conn)

    breakdown = build_breakdown_table(
        _fetch_breakdown(conn),
        key="rq6_breakdown",
        label="tab:exclusions-breakdown-extended",
        caption=(
            "Test, assertion, and generalization exclusions by filtering versus "
            "failures for the real-world dataset."
        ),
        include_strategy=False,
    )
    breakdown = replace(
        breakdown, provenance=capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    )

    filtering = build_filtering_table(
        _fetch_filtering(conn),
        key="rq6_filtering",
        label="tab:exclusions-filtering-extended",
        caption="Filter rejection rates by level and filter for the real-world dataset.",
    )
    filtering = replace(
        filtering, provenance=capture(_fetch_filtering, query=FILTERING_SQL)
    )

    metrics = [
        Metric(
            "realworld.eligible_projects",
            funnel.eligible,
            fmt="int",
            provenance=capture(_funnel.build_funnel),
        ),
        Metric(
            "realworld.overall_inclusion_pct",
            funnel.success_count / funnel.eligible,
            fmt="pct1",
            provenance=capture(_funnel.build_funnel),
        ),
    ]
    section = Section(
        title="Project-level exclusions",
        blocks=[
            Prose(
                "Real-world exclusions separate project-level failures from "
                "filtering and downstream test, assertion, and generalization failures."
            ),
            funnel.table,
            breakdown,
            filtering,
        ],
    )
    return RQReport(
        rq="rq6",
        title="RQ6 - Causes of Unsuccessful Generalization (Real-World)",
        db="postgres_reporeapers",
        sections=[section],
        metrics=metrics,
    )


register("rq6", ReportSpec(build, "postgres_reporeapers", "new"))
