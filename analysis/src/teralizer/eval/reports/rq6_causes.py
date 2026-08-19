"""RQ6 real-world exclusion causes report."""

from __future__ import annotations
from dataclasses import replace

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import Metric, Prose, RQReport, Section
from teralizer.eval.provenance import Provenance, capture
from teralizer.eval.registry import Corpus, ReportSpec, register
from teralizer.eval.reports import _funnel
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
    collapse_mechanisms,
)
from teralizer.eval.reports._diagnostics import (
    JPF_EXCEPTION_DETAIL_SQL,
    MUT_CHOICE_SENSITIVITY_SQL,
    fetch_jpf_exception_causes,
    fetch_mut_choice_sensitivity,
    jpf_exception_table,
    mut_choice_table,
)
from teralizer.eval.reports._widening import (
    WIDENING_REFUSAL_SQL,
    fetch_widening_refusals,
    widening_refusal_metrics,
    widening_refusal_table,
)

DEFAULT_DB = "postgres_reporeapers_rq6_v7"

# How each exclusion mechanism records itself. See docs/exclusion-model.md.
# The name test matters: the javac quarantine writes REJECT rows to
# `filter_result` without being a filter, so `decision = 'REJECT'` alone
# overcounts filtering.
FILTER_CLASS_PATTERN = r"filter\.\w+Filter$"
GATE_CODES = ("ORACLE_NOT_WIDENABLE", "INPUT_SPEC_NOT_SATISFIED_BY_SEED")
QUARANTINE_CODES = (
    "UNCOMPILABLE_GENERALIZED_TEST",
    "UNCOMPILABLE_INSTRUMENTED_WRAPPER",
)
CAPABILITY_PATTERN = "INHERITED_METHOD_NOT_FLATTENABLE%"


def _query_params(variant: str) -> dict[str, object]:
    return {
        **_funnel.base_query_params(variant),
        "filter_class_pattern": FILTER_CLASS_PATTERN,
        "gate_codes": list(GATE_CODES),
        "quarantine_codes": list(QUARANTINE_CODES),
        "capability_pattern": CAPABILITY_PATTERN,
    }


FILTERING_SQL = rf"""
{_funnel.ELIGIBILITY_CTE},
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
{_funnel.ELIGIBILITY_CTE},
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
            WHEN t.exclusion_info LIKE :capability_pattern THEN 'unsupported'
            WHEN EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.test_id = t.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name ~ :filter_class_pattern
            ) THEN 'filtered'
            WHEN t.exclusion_info = ANY(:quarantine_codes)
              OR t.exclusion_info LIKE 'Excluded by%'
              OR EXISTS (
                 SELECT 1
                 FROM task ft
                 WHERE ft.test_id = t.id
                   AND ft.assertion_id IS NULL
                   AND ft.generalization_id IS NULL
                   AND ft.status <> 'SUCCEEDED'
             ) THEN 'failed'
            ELSE 'uncoded'
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
                   AND fa.generalization_id IS NULL
                   AND fa.status <> 'SUCCEEDED'
             ) THEN 'included'
            WHEN EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.assertion_id = a.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name ~ :filter_class_pattern
            ) THEN 'filtered'
            WHEN a.exclusion_info = ANY(:quarantine_codes)
              OR a.exclusion_info LIKE 'Excluded by%'
              OR EXISTS (
                 SELECT 1
                 FROM task fa
                 WHERE fa.assertion_id = a.id
                   AND fa.generalization_id IS NULL
                   AND fa.status <> 'SUCCEEDED'
             ) THEN 'failed'
            ELSE 'uncoded'
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
            WHEN g.exclusion_info = ANY(:gate_codes) THEN 'refused'
            WHEN EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.generalization_id = g.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name ~ :filter_class_pattern
            ) THEN 'filtered'
            WHEN g.exclusion_info = ANY(:quarantine_codes)
              OR g.exclusion_info LIKE 'Excluded by%'
              OR l.final_failure_stage IS NOT NULL
              OR EXISTS (
                 SELECT 1
                 FROM task fg
                 WHERE fg.generalization_id = g.id
                   AND fg.status <> 'SUCCEEDED'
             ) THEN 'failed'
            ELSE 'uncoded'
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
    coalesce(sum(item_count) FILTER (WHERE bucket = 'filtered'), 0)::bigint AS filtered,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'refused'), 0)::bigint AS refused,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'unsupported'), 0)::bigint
        AS unsupported,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'failed'), 0)::bigint AS failed,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'uncoded'), 0)::bigint AS uncoded
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
{_funnel.ELIGIBILITY_CTE}
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


_BREAKDOWN_COLUMNS = ("included", "filtered", "refused", "unsupported", "failed")


def _fetch_breakdown(conn: Connection, variant: str) -> pd.DataFrame:
    """Return eligible-entity outcomes, one column per exclusion mechanism.

    Raises when an entity matches no mechanism, which means the pipeline can
    exclude something this SQL does not model. Add the mechanism rather than a
    fallback branch, or the count lands in whichever column is written last.
    """
    df = read_sql(conn, BREAKDOWN_SQL, _query_params(variant))
    for column in (*_BREAKDOWN_COLUMNS, "total", "uncoded"):
        df[column] = df[column].astype(int)
    drifted = df[df["uncoded"] > 0]
    if not drifted.empty:
        levels = ", ".join(
            f"{row['level']}: {row['uncoded']}" for row in drifted.to_dict("records")
        )
        raise RuntimeError(
            "unclassified exclusions, see docs/exclusion-model.md: " + levels
        )
    return pd.DataFrame(df, columns=["strategy", "level", "total", *_BREAKDOWN_COLUMNS])


def _stage_slug(stage: str) -> str:
    """Stage label to metric-key segment ("1 + 2" -> "1_2")."""
    return "_".join(part for part in stage.replace("+", " ").split() if part)


def _stage_metrics(
    funnel: _funnel.FunnelResult, provenance: Provenance | None
) -> list[Metric]:
    """One entering/included/excluded/rate quartet per pipeline stage.

    The funnel note states these figures in the markdown report only. Citing a
    stage in prose needs a macro, otherwise the chapter hardcodes a number that
    no regeneration can correct.
    """
    metrics: list[Metric] = []
    for band in funnel.stages:
        slug = _stage_slug(band.stage)
        rate = band.passing / band.entering if band.entering else 0.0
        metrics.extend(
            [
                Metric(
                    f"realworld.stage_{slug}.entering",
                    band.entering,
                    fmt="int",
                    provenance=provenance,
                ),
                Metric(
                    f"realworld.stage_{slug}.included",
                    band.passing,
                    fmt="int",
                    provenance=provenance,
                ),
                Metric(
                    f"realworld.stage_{slug}.excluded",
                    band.exclusions,
                    fmt="int",
                    provenance=provenance,
                ),
                Metric(
                    f"realworld.stage_{slug}.included_pct",
                    rate,
                    fmt="pct1",
                    provenance=provenance,
                ),
            ]
        )
    return metrics


def build(conn: Connection) -> RQReport:
    variant = _funnel.resolve_variant(conn)
    funnel = _funnel.build_funnel(conn, variant=variant)
    breakdown_data = _fetch_breakdown(conn, variant)
    jpf_exception_data = fetch_jpf_exception_causes(conn, variant)
    jpf_table = jpf_exception_table(jpf_exception_data)
    mut_choice_data = fetch_mut_choice_sensitivity(conn, variant)
    mut_table = mut_choice_table(mut_choice_data)

    breakdown = build_breakdown_table(
        collapse_mechanisms(breakdown_data),
        key="tab-exclusions-breakdown-extended",
        label="tab:exclusions-breakdown-extended",
        caption="Exclusion results for \\VariantImprovedC{} in the RepoReapers projects.",
        short_caption=(
            "\\VariantImprovedC{} inclusion, filtering, and failure counts in RepoReapers"
        ),
        body_style="\\tabstyle",
        full_width=True,
        include_strategy=False,
    )
    breakdown = replace(
        breakdown, provenance=capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    )

    filtering_data = _fetch_filtering(conn, variant)
    filtering = build_filtering_table(
        filtering_data,
        key="tab-exclusions-filtering-extended",
        label="tab:exclusions-filtering-extended",
        caption="Filtering results for \\VariantImprovedC{} in the RepoReapers projects.",
        short_caption=(
            "RepoReapers filtering decisions for \\VariantImprovedC{} by level and filter"
        ),
        body_style="\\tabstyle",
        full_width=True,
    )
    filtering = replace(
        filtering,
        latex_resize_to_width=True,
        provenance=capture(_fetch_filtering, query=FILTERING_SQL),
    )

    funnel_provenance = capture(
        _funnel.build_funnel, query=_funnel._PROJECT_SIGNALS_SQL
    )
    breakdown_provenance = capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    levels = breakdown_data.set_index("level")
    assertions = levels.loc["Assertion"]
    generalizations = levels.loc["Generalization"]
    metrics = [
        Metric(
            "realworld.selected_projects",
            funnel.selected,
            fmt="int",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.initial_gate_excluded_projects",
            funnel.initial_gate_excluded,
            fmt="int",
            provenance=funnel_provenance,
        ),
        Metric(
            "realworld.no_executed_test_excluded_projects",
            funnel.no_executed_test_excluded,
            fmt="int",
            provenance=funnel_provenance,
        ),
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
    metrics.extend(_stage_metrics(funnel, funnel_provenance))
    jpf_rows = int(jpf_exception_data["count"].sum())
    jpf_unparsed = int(
        jpf_exception_data.loc[
            jpf_exception_data["category"].eq("Unparsed"), "count"
        ].sum()
    )
    metrics.extend(
        [
            Metric(
                "realworld.jpf_uncaught_exception_diagnostics",
                jpf_rows,
                fmt="count",
                provenance=capture(
                    fetch_jpf_exception_causes,
                    query=JPF_EXCEPTION_DETAIL_SQL,
                ),
            ),
            Metric(
                "realworld.jpf_uncaught_exception_reclassified_pct",
                (jpf_rows - jpf_unparsed) / jpf_rows if jpf_rows else 0.0,
                fmt="pct1",
                provenance=capture(
                    fetch_jpf_exception_causes,
                    query=JPF_EXCEPTION_DETAIL_SQL,
                ),
            ),
        ]
    )
    mut_choice_total = int(mut_choice_data["count"].sum())
    mut_choice_dependent = int(
        mut_choice_data.loc[
            mut_choice_data["category"].eq("Choice-dependent"), "count"
        ].sum()
    )
    metrics.extend(
        [
            Metric(
                "realworld.parameter_type_choice_dependent_lower_bound",
                mut_choice_dependent,
                fmt="count",
                provenance=capture(
                    fetch_mut_choice_sensitivity,
                    query=MUT_CHOICE_SENSITIVITY_SQL,
                ),
            ),
            Metric(
                "realworld.parameter_type_choice_dependent_lower_bound_pct",
                mut_choice_dependent / mut_choice_total if mut_choice_total else 0.0,
                fmt="pct1",
                provenance=capture(
                    fetch_mut_choice_sensitivity,
                    query=MUT_CHOICE_SENSITIVITY_SQL,
                ),
            ),
        ]
    )
    widening_data = fetch_widening_refusals(conn, variant)
    widening_provenance = capture(fetch_widening_refusals, query=WIDENING_REFUSAL_SQL)
    widening_table = widening_refusal_table(widening_data, widening_provenance)
    metrics.extend(widening_refusal_metrics(widening_data, widening_provenance))
    section = Section(
        title="Project-level exclusions",
        blocks=[
            Prose(
                "Real-world exclusions separate project-level failures from "
                "filtering and downstream test, assertion, and generalization failures."
            ),
            funnel.table,
            Prose(
                "Generic JPF uncaught-exception diagnostics are reclassified "
                "from their retained detail into application exceptions and "
                "JPF environment gaps."
            ),
            jpf_table,
            Prose(
                "ParameterType choice sensitivity is reported conservatively: "
                "only a rejection with an observed argument-taking alternative "
                "is choice-dependent."
            ),
            mut_table,
            breakdown,
            filtering,
            Prose(
                "Most of the generalization row's filtering column contains "
                "pre-emission soundness rejections rather than filter decisions."
            ),
            widening_table,
        ],
    )
    return RQReport(
        rq="rq6",
        title="RQ6 - Causes of Unsuccessful Generalization (Real-World)",
        db=str(conn.engine.url.database),
        sections=[section],
        metrics=metrics,
    )


register(
    "rq6",
    ReportSpec(
        build,
        DEFAULT_DB,
        "new",
        corpus=Corpus(
            data_dir="data/reporeapers-rerun-v7",
            config_dir="project-configs/replication/extended",
        ),
    ),
)
