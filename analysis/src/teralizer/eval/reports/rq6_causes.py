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

DEFAULT_DB = "postgres_reporeapers_rq6_v5"

_ELIGIBILITY_CTE = """
WITH eligible_projects AS (
    SELECT p.id
    FROM project p
    WHERE p.use_test_generalization
      AND NOT EXISTS (
          SELECT 1
          FROM task t
          WHERE t.project_id = p.id
            AND t.test_id IS NULL
            AND t.assertion_id IS NULL
            AND t.generalization_id IS NULL
            AND t.status <> 'SUCCEEDED'
            AND t.stage = ANY(:ineligible_stages)
      )
)
"""


def _query_params(variant: str) -> dict[str, object]:
    return {
        "variant": variant,
        "ineligible_stages": list(_funnel.INELIGIBLE_STAGES),
    }


FILTERING_SQL = rf"""
{_ELIGIBILITY_CTE},
base_data AS (
    SELECT
        CASE
            WHEN fr.test_id IS NOT NULL THEN 'Test'
            WHEN fr.assertion_id IS NOT NULL THEN 'Assertion'
            WHEN fr.generalization_id IS NOT NULL THEN 'Generalization'
        END AS level,
        substring(fr.filter_name from 'filter\.(\w+)Filter$') AS filter_name,
        fr.decision,
        coalesce(fr.test_id, fr.assertion_id, fr.generalization_id) AS entity_id
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    LEFT JOIN generalization g ON g.id = fr.generalization_id
    WHERE fr.test_id IS NOT NULL
       OR fr.assertion_id IS NOT NULL
       OR (fr.generalization_id IS NOT NULL AND g.variant = :variant)
),
pivoted AS (
    SELECT
        level,
        filter_name,
        count(DISTINCT entity_id)::bigint AS total,
        count(DISTINCT entity_id) FILTER (WHERE decision = 'ACCEPT')::bigint AS accept,
        count(DISTINCT entity_id) FILTER (WHERE decision = 'DEFER')::bigint AS defer,
        count(DISTINCT entity_id) FILTER (WHERE decision = 'REJECT')::bigint AS reject
    FROM base_data
    WHERE filter_name IS NOT NULL
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
ORDER BY
    CASE level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    filter
"""

BREAKDOWN_SQL = f"""
{_ELIGIBILITY_CTE},
test_counts AS (
    SELECT
        'All' AS strategy,
        'Test' AS level,
        CASE
            WHEN t.is_included
             AND NOT EXISTS (
                 SELECT 1
                 FROM task ft
                 WHERE ft.test_id = t.id
                   AND ft.assertion_id IS NULL
                   AND ft.generalization_id IS NULL
                   AND ft.status <> 'SUCCEEDED'
             ) THEN 'included'
            WHEN EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.test_id = t.id
                  AND fr.decision = 'REJECT'
            ) THEN 'filtering'
            ELSE 'failures'
        END AS bucket,
        count(*) AS item_count
    FROM test t
    JOIN eligible_projects ep ON ep.id = t.project_id
    GROUP BY bucket
),
assertion_counts AS (
    SELECT
        'All' AS strategy,
        'Assertion' AS level,
        CASE
            WHEN a.is_included
             AND NOT EXISTS (
                 SELECT 1
                 FROM task fa
                 WHERE fa.assertion_id = a.id
                   AND fa.status <> 'SUCCEEDED'
             ) THEN 'included'
            WHEN EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.assertion_id = a.id
                  AND fr.decision = 'REJECT'
            ) THEN 'filtering'
            ELSE 'failures'
        END AS bucket,
        count(*) AS item_count
    FROM assertion a
    JOIN eligible_projects ep ON ep.id = a.project_id
    GROUP BY bucket
),
generalization_counts AS (
    SELECT
        g.variant AS strategy,
        'Generalization' AS level,
        CASE
            WHEN l.generated_filter_passed THEN 'included'
            WHEN NOT g.is_included
             AND (
                 g.exclusion_info IN (
                     'ORACLE_NOT_WIDENABLE',
                     'INPUT_SPEC_NOT_SATISFIED_BY_SEED'
                 )
                 OR EXISTS (
                     SELECT 1
                     FROM filter_result fr
                     WHERE fr.generalization_id = g.id
                       AND fr.decision = 'REJECT'
                 )
             ) THEN 'filtering'
            ELSE 'failures'
        END AS bucket,
        count(*) AS item_count
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
    WHERE g.variant = :variant
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
    SELECT :variant AS strategy, 'Generalization' AS level
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


UNRESOLVED_TELEMETRY_SQL = f"""
{_ELIGIBILITY_CTE}
SELECT count(*) AS assertions_without_resolution
FROM assertion a
JOIN eligible_projects ep ON ep.id = a.project_id
WHERE NOT EXISTS (
    SELECT 1
    FROM mut_resolution_observation o
    WHERE o.assertion_id = a.id
)
"""


def _fetch_assertions_without_resolution(conn: Connection, variant: str) -> int:
    """Assertions whose resolver telemetry was never persisted. Must be zero."""
    df = read_sql(conn, UNRESOLVED_TELEMETRY_SQL, _query_params(variant))
    return int(df["assertions_without_resolution"].iloc[0])


def _fetch_filtering(conn: Connection, variant: str) -> pd.DataFrame:
    """Return distinct-entity filter decision counts for the eligible corpus."""
    df = read_sql(conn, FILTERING_SQL, _query_params(variant))
    for column in ("total", "accept", "defer", "reject"):
        df[column] = df[column].astype(int)
    return pd.DataFrame(
        df, columns=["level", "filter", "total", "accept", "defer", "reject"]
    )


def _fetch_breakdown(conn: Connection, variant: str) -> pd.DataFrame:
    """Return eligible-entity outcomes split by filtering and failures."""
    df = read_sql(conn, BREAKDOWN_SQL, _query_params(variant))
    for column in ("total", "included", "filtering", "failures"):
        df[column] = df[column].astype(int)
    return pd.DataFrame(
        df, columns=["strategy", "level", "total", "included", "filtering", "failures"]
    )


def build(conn: Connection) -> RQReport:
    variant = _funnel.resolve_variant(conn)
    funnel = _funnel.build_funnel(conn, variant=variant)
    breakdown_data = _fetch_breakdown(conn, variant)

    breakdown = build_breakdown_table(
        breakdown_data,
        key="rq6_breakdown",
        label="tab:exclusions-breakdown-extended",
        caption=(
            "Eligible test, assertion, and generalization outcomes by filtering "
            "versus failures for the real-world dataset."
        ),
        include_strategy=False,
    )
    breakdown = replace(
        breakdown, provenance=capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    )

    filtering_data = _fetch_filtering(conn, variant)
    filtering = build_filtering_table(
        filtering_data,
        key="rq6_filtering",
        label="tab:exclusions-filtering-extended",
        caption=(
            "Distinct eligible entities receiving each filter decision, by level "
            "and filter."
        ),
    )
    filtering = replace(
        filtering, provenance=capture(_fetch_filtering, query=FILTERING_SQL)
    )

    funnel_provenance = capture(_funnel.build_funnel)
    breakdown_provenance = capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    levels = breakdown_data.set_index("level")
    assertions = levels.loc["Assertion"]
    generalizations = levels.loc["Generalization"]
    metrics = [
        Metric(
            "realworld.eligible_projects",
            funnel.eligible,
            fmt="int",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.applicability_projects",
            funnel.success_count,
            fmt="int",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.applicability_pct",
            funnel.success_count / funnel.eligible,
            fmt="pct1",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.assertions_total",
            int(assertions["total"]),
            fmt="count",
            provenance=breakdown_provenance,
        ),
        # Telemetry invariant: every stored assertion carries a resolver
        # observation. Non-zero is a persistence defect, never a category.
        Metric(
            "realworld.assertions_without_resolution",
            _fetch_assertions_without_resolution(conn, variant),
            fmt="count",
            provenance=capture(
                _fetch_assertions_without_resolution, query=UNRESOLVED_TELEMETRY_SQL
            ),
        ),
        Metric(
            "realworld.assertions_included",
            int(assertions["included"]),
            fmt="count",
            provenance=breakdown_provenance,
        ),
        Metric(
            "realworld.assertions_included_pct",
            int(assertions["included"]) / int(assertions["total"]),
            fmt="pct1",
            provenance=breakdown_provenance,
        ),
        Metric(
            "realworld.generalization_attempts",
            int(generalizations["total"]),
            fmt="count",
            provenance=breakdown_provenance,
        ),
        Metric(
            "realworld.generalizations_validated",
            int(generalizations["included"]),
            fmt="count",
            provenance=breakdown_provenance,
        ),
        Metric(
            "realworld.generalization_validated_pct",
            int(generalizations["included"]) / int(generalizations["total"]),
            fmt="pct1",
            provenance=breakdown_provenance,
        ),
        # Projects holding a validated generalized test before reduction. Reported
        # beside the headline so the prose can state what reduction itself costs.
        Metric(
            "realworld.stage4_projects",
            funnel.reduction.entering,
            fmt="int",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.reduction_excluded_projects",
            funnel.reduction.exclusions,
            fmt="int",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.reduction_excluded_baseline_side",
            funnel.reduction_excluded_baseline_side,
            fmt="int",
            provenance=funnel_provenance,
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
        db=str(conn.engine.url.database),
        sections=[section],
        metrics=metrics,
    )


register("rq6", ReportSpec(build, DEFAULT_DB, "new"))
