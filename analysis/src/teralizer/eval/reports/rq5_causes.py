"""RQ5 controlled-dataset exclusion causes report."""

from __future__ import annotations

from dataclasses import replace

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required, read_sql
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import Prose, RQReport, Section
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
)

REQUIRES: tuple[Required, ...] = (
    Required("project", "table", ("id", "use_test_generalization")),
    Required("test", "table", ("id", "project_id", "is_included", "exclusion_info")),
    Required("assertion", "table", ("id", "test_id", "is_included", "exclusion_info")),
    Required(
        "generalization",
        "table",
        ("id", "variant", "is_included", "exclusion_info", "project_id"),
    ),
    Required(
        "filter_result",
        "table",
        (
            "project_id",
            "test_id",
            "assertion_id",
            "generalization_id",
            "filter_name",
            "decision",
        ),
    ),
    Required(
        "mv_exclusions_all",
        "view",
        ("level", "is_included", "excluded_by", "count"),
    ),
)

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
        LEFT JOIN generalization g ON fr.generalization_id = g.id
        WHERE p.use_test_generalization
        GROUP BY
            g.variant,
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
                WHEN t.exclusion_info LIKE '%%TestFilteringTask%%'
                    AND EXISTS (
                        SELECT 1
                        FROM filter_result fr
                        WHERE fr.test_id = t.id
                          AND fr.decision = 'REJECT'
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
                WHEN a.exclusion_info LIKE '%%TestFilteringTask%%'
                    AND EXISTS (
                        SELECT 1
                        FROM filter_result fr
                        WHERE fr.assertion_id = a.id
                          AND fr.decision = 'REJECT'
                    ) THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM assertion a
        JOIN test t ON a.test_id = t.id
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY bucket
    ),
    generalization_counts AS (
        SELECT
            g.variant AS strategy,
            'Generalization' AS level,
            CASE
                WHEN g.is_included THEN 'included'
                WHEN g.exclusion_info LIKE '%%TestFilteringTask%%' THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM generalization g
        JOIN project p ON g.project_id = p.id
        WHERE p.use_test_generalization
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
        SELECT DISTINCT g.variant AS strategy, 'Generalization' AS level
        FROM generalization g
        JOIN project p ON g.project_id = p.id
        WHERE p.use_test_generalization
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
    """Return filter rejection counts normalized for the shared table builder."""
    df = read_sql(conn, FILTERING_SQL)
    for column in ("total", "accept", "defer", "reject"):
        df.isetitem(df.columns.get_loc(column), df[column].astype(int))
    return pd.DataFrame(
        df, columns=["level", "filter", "total", "accept", "defer", "reject"]
    )


def _fetch_breakdown(conn: Connection) -> pd.DataFrame:
    """Return inclusion, filtering, and failure counts by level and strategy."""
    df = read_sql(conn, BREAKDOWN_SQL)
    for column in ("total", "included", "filtering", "failures"):
        df.isetitem(df.columns.get_loc(column), df[column].astype(int))
    return pd.DataFrame(
        df, columns=["strategy", "level", "total", "included", "filtering", "failures"]
    )


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("controlled")
    filtering = build_filtering_table(
        _fetch_filtering(conn),
        key="tab-exclusions-filtering",
        label="tab:exclusions-filtering",
        caption=(
            "Filtering results for tests and assertions in the "
            "{entity.dataset.commons} and {entity.dataset.eqbench_es} projects."
        ),
        short_caption="Filtering decisions by level and filter",
        body_style="\\tabstyle",
        full_width=True,
    )
    filtering = replace(
        filtering,
        df=filtering.df.assign(
            row_key=filtering.df["level"] + ":" + filtering.df["filter"]
        ),
        row_key="row_key",
        provenance=capture(_fetch_filtering, query=FILTERING_SQL),
    )

    breakdown = build_breakdown_table(
        _fetch_breakdown(conn),
        key="tab-exclusions-breakdown",
        label="tab:exclusions-breakdown",
        caption=(
            "Exclusion results for tests, assertions, and generalizations in the "
            "{entity.dataset.commons} and {entity.dataset.eqbench_es} projects."
        ),
        short_caption="Inclusion, filtering, and failure counts by level",
        body_style="\\tabstyle\\setlength{\\tabcolsep}{3pt}",
        full_width=True,
        group_header_align="r",
        include_strategy=True,
    )
    breakdown = replace(
        breakdown, provenance=capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    )

    section = Section(
        title="Exclusion breakdown",
        blocks=[
            Prose(
                "Controlled-dataset exclusions separate successful inclusions from "
                "proactive filter rejections and task failures."
            ),
            breakdown,
            filtering,
        ],
    )
    return RQReport(
        rq="rq5",
        title="RQ5 - Causes of Unsuccessful Generalization (Controlled)",
        sections=[section],
    )


register(
    "rq5",
    ReportSpec(build, (CorpusInputSpec("controlled", "controlled", REQUIRES),)),
)
