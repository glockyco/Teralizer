"""RQ6 real-world exclusion causes report."""

from __future__ import annotations
from dataclasses import replace

from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import (
    Metric,
    MetricPopulation,
    Prose,
    RQReport,
    Section,
    ValueKind,
)
from teralizer.eval.provenance import Provenance, capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel
from teralizer.eval.reports import _generalization_funnel as generation_funnel
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
    build_mechanism_table,
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
    WIDENING_REFUSALS,
    WIDENING_REFUSAL_SQL,
    fetch_widening_refusals,
    widening_refusal_metrics,
    widening_refusal_table,
)


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
    df = read_sql(conn, UNRESOLVED_TELEMETRY_SQL, exclusion.query_params(variant))
    return int(df["assertions_without_resolution"].iloc[0])


def _population(key: str, entity_level: str) -> MetricPopulation:
    return MetricPopulation(key, entity_level, "real-world")


def _count_metric(
    key: str,
    value: int,
    entity_level: str,
    provenance: Provenance,
) -> Metric:
    return Metric(
        key,
        value,
        fmt="count",
        provenance=provenance,
        kind=ValueKind.COUNT,
        population=_population(key, entity_level),
    )


def _share_metric(
    key: str,
    value: float,
    numerator_key: str,
    denominator_key: str,
    entity_level: str,
    provenance: Provenance,
) -> Metric:
    return Metric(
        key,
        value,
        fmt="pct1",
        provenance=provenance,
        kind=ValueKind.SHARE,
        population=_population(numerator_key, entity_level),
        numerator_key=numerator_key,
        denominator_key=denominator_key,
    )


def _validate_final_usable_projects(
    final_usable: frozenset[int], applicable: frozenset[int]
) -> None:
    if final_usable == applicable:
        return
    raise ValueError(
        "RQ6 final-usable and applicable project populations differ: "
        f"only_final_usable={sorted(final_usable - applicable)}, "
        f"only_applicable={sorted(applicable - final_usable)}"
    )


def _stage_slug(stage: str) -> str:
    """Stage label to metric-key segment ("1 + 2" -> "1_2")."""
    return "_".join(part for part in stage.replace("+", " ").split() if part)


def _stage_metrics(
    funnel: _funnel.FunnelResult, provenance: Provenance
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
        entering_key = f"realworld.stage_{slug}.entering"
        included_key = f"realworld.stage_{slug}.included"
        metrics.extend(
            [
                _count_metric(entering_key, band.entering, "Project", provenance),
                _count_metric(included_key, band.passing, "Project", provenance),
                _count_metric(
                    f"realworld.stage_{slug}.excluded",
                    band.exclusions,
                    "Project",
                    provenance,
                ),
                _share_metric(
                    f"realworld.stage_{slug}.included_pct",
                    rate,
                    included_key,
                    entering_key,
                    "Project",
                    provenance,
                ),
            ]
        )
    return metrics


RETAINED_METRIC_KEYS = frozenset(
    {
        "realworld.selected_projects",
        "realworld.initial_gate_excluded_projects",
        "realworld.no_executed_test_excluded_projects",
        "realworld.eligible_projects",
        "realworld.applicability_projects",
        "realworld.applicability_pct",
        "realworld.assertions_total",
        "realworld.assertions_without_resolution",
        "realworld.assertions_included",
        "realworld.assertions_included_pct",
        "realworld.generalization_attempts",
        "realworld.generalizations_emitted",
        "realworld.generalizations_filter_adjudicated",
        "realworld.generalizations_filter_passed",
        "realworld.generalizations_validated",
        "realworld.generalization_validated_pct",
        "realworld.generalizations_reduced",
        "realworld.generalizations_final_usable",
        "realworld.final_usable_projects",
        "realworld.generalization_unknown_attempt_state",
        "realworld.stage4_projects",
        "realworld.reduction_excluded_projects",
        "realworld.reduction_excluded_baseline_side",
        "realworld.jpf_uncaught_exception_diagnostics",
        "realworld.jpf_uncaught_exception_reclassified",
        "realworld.jpf_uncaught_exception_reclassified_pct",
        "realworld.parameter_type_choice_observations",
        "realworld.parameter_type_choice_dependent_lower_bound",
        "realworld.parameter_type_choice_dependent_lower_bound_pct",
        "realworld.widening_refusals",
        "realworld.widening_refusals_pct",
    }
    | {
        f"realworld.stage_{stage}.{suffix}"
        for stage in ("1_2", "3", "4", "5")
        for suffix in ("entering", "included", "excluded", "included_pct")
    }
    | {
        f"realworld.widening_refusal_{slug}{suffix}"
        for slug, _ in WIDENING_REFUSALS.values()
        for suffix in ("", "_pct")
    }
)

RETAINED_TABLE_KEYS = frozenset(
    {
        "tab-processing-failures",
        "rq6_generalization_funnel",
        "rq6_jpf_exception_causes",
        "rq6_mut_choice_sensitivity",
        "rq6_exclusion_mechanisms",
        "tab-exclusions-breakdown-extended",
        "tab-exclusions-filtering-extended",
        "rq6_widening_refusals",
    }
)


def validate_retained_consumers(report: RQReport) -> None:
    """Require the reviewed RQ6 metric and table identities exactly once."""
    metric_keys = [metric.key for metric in report.metrics]
    missing_metrics = sorted(RETAINED_METRIC_KEYS - set(metric_keys))
    if missing_metrics:
        raise ValueError(f"RQ6 retained metrics are missing: {missing_metrics}")
    report.validate_metric_relations(require_metadata=True)
    table_keys = [table.key for table in report.tables()]
    if len(table_keys) != len(set(table_keys)):
        duplicates = sorted(key for key in set(table_keys) if table_keys.count(key) > 1)
        raise ValueError(f"RQ6 table keys are duplicated: {duplicates}")
    missing_tables = sorted(RETAINED_TABLE_KEYS - set(table_keys))
    if missing_tables:
        raise ValueError(f"RQ6 retained tables are missing: {missing_tables}")
    for table in report.tables():
        if table.key not in RETAINED_TABLE_KEYS:
            continue
        if table.provenance is None:
            raise ValueError(f"RQ6 retained table lacks provenance: {table.key}")
        if table.row_key is None:
            raise ValueError(f"RQ6 retained table lacks row identities: {table.key}")


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("real-world")
    variant = _funnel.resolve_variant(conn)
    exclusion.validate_evidence(conn, variant)
    funnel = _funnel.build_funnel(conn, variant=variant)
    generation_funnel_provenance = capture(
        generation_funnel.build_generalization_funnel,
        query=generation_funnel.GENERALIZATION_LIFECYCLE_SQL,
    )
    generalizations_funnel = generation_funnel.build_generalization_funnel(
        conn, variant, generation_funnel_provenance
    )
    final_usable_project_ids = generalizations_funnel.project_ids[
        generation_funnel.PopulationKey.FINAL_USABLE
    ]
    applicable_project_ids = funnel.survivor_project_ids[-1]
    _validate_final_usable_projects(final_usable_project_ids, applicable_project_ids)
    mechanism_partition = exclusion.fetch_mechanism_partition(conn, variant)
    breakdown_data = exclusion.pivot_mechanism_partition(mechanism_partition)
    jpf_exception_data = fetch_jpf_exception_causes(conn, variant)
    jpf_table = jpf_exception_table(jpf_exception_data)
    mut_choice_data = fetch_mut_choice_sensitivity(conn, variant)
    mut_table = mut_choice_table(mut_choice_data)
    mechanism_provenance = capture(
        exclusion.fetch_mechanism_partition, query=exclusion.MECHANISM_COUNTS_SQL
    )
    mechanism_table = build_mechanism_table(
        mechanism_partition, provenance=mechanism_provenance
    )

    breakdown = build_breakdown_table(
        collapse_mechanisms(breakdown_data),
        key="tab-exclusions-breakdown-extended",
        label="tab:exclusions-breakdown-extended",
        caption="Exclusion results for {entity.variant.improved_c} in the RepoReapers projects.",
        short_caption=(
            "{entity.variant.improved_c} inclusion, filtering, and failure counts in RepoReapers"
        ),
        body_style="\\tabstyle",
        full_width=True,
        include_strategy=False,
    )
    breakdown = replace(
        breakdown,
        row_key="level",
        provenance=mechanism_provenance,
    )

    filtering_data = exclusion.fetch_filter_decisions(conn, variant)
    filtering = build_filtering_table(
        filtering_data,
        key="tab-exclusions-filtering-extended",
        label="tab:exclusions-filtering-extended",
        caption="Filtering results for {entity.variant.improved_c} in the RepoReapers projects.",
        short_caption=(
            "RepoReapers filtering decisions for {entity.variant.improved_c} by level and filter"
        ),
        body_style="\\tabstyle",
        full_width=True,
    )
    filtering = replace(
        filtering,
        df=filtering.df.assign(
            row_key=filtering.df["level"] + ":" + filtering.df["filter"]
        ),
        row_key="row_key",
        latex_resize_to_width=True,
        provenance=capture(
            exclusion.fetch_filter_decisions, query=exclusion.FILTER_DECISION_SQL
        ),
    )

    funnel_provenance = capture(
        _funnel.build_funnel, query=_funnel._PROJECT_SIGNALS_SQL
    )
    breakdown_provenance = mechanism_provenance
    levels = breakdown_data.set_index("level")
    assertions = levels.loc["Assertion"]
    applicability_key = "realworld.applicability_projects"
    eligible_key = "realworld.eligible_projects"
    assertion_total_key = "realworld.assertions_total"
    assertion_included_key = "realworld.assertions_included"
    generation_attempt_key = "realworld.generalization_attempts"
    generation_validated_key = "realworld.generalizations_validated"
    metrics = [
        _count_metric(
            "realworld.selected_projects", funnel.selected, "Project", funnel_provenance
        ),
        _count_metric(
            "realworld.initial_gate_excluded_projects",
            funnel.initial_gate_excluded,
            "Project",
            funnel_provenance,
        ),
        _count_metric(
            "realworld.no_executed_test_excluded_projects",
            funnel.no_executed_test_excluded,
            "Project",
            funnel_provenance,
        ),
        _count_metric(eligible_key, funnel.eligible, "Project", funnel_provenance),
        _count_metric(
            applicability_key, funnel.success_count, "Project", funnel_provenance
        ),
        _share_metric(
            "realworld.applicability_pct",
            funnel.success_count / funnel.eligible,
            applicability_key,
            eligible_key,
            "Project",
            funnel_provenance,
        ),
        _count_metric(
            assertion_total_key,
            int(assertions["total"]),
            "Assertion",
            breakdown_provenance,
        ),
        # Telemetry invariant: every stored assertion carries a resolver
        # observation. Non-zero is a persistence defect, never a category.
        _count_metric(
            "realworld.assertions_without_resolution",
            _fetch_assertions_without_resolution(conn, variant),
            "Assertion",
            capture(
                _fetch_assertions_without_resolution, query=UNRESOLVED_TELEMETRY_SQL
            ),
        ),
        _count_metric(
            assertion_included_key,
            int(assertions["included"]),
            "Assertion",
            breakdown_provenance,
        ),
        _share_metric(
            "realworld.assertions_included_pct",
            int(assertions["included"]) / int(assertions["total"]),
            assertion_included_key,
            assertion_total_key,
            "Assertion",
            breakdown_provenance,
        ),
        _count_metric(
            generation_attempt_key,
            generalizations_funnel.counts[generation_funnel.PopulationKey.ATTEMPTED],
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.generalizations_emitted",
            generalizations_funnel.counts[generation_funnel.PopulationKey.EMITTED],
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.generalizations_filter_adjudicated",
            generalizations_funnel.counts[
                generation_funnel.PopulationKey.FILTER_ADJUDICATED
            ],
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.generalizations_filter_passed",
            generalizations_funnel.counts[
                generation_funnel.PopulationKey.FILTER_PASSED
            ],
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            generation_validated_key,
            generalizations_funnel.counts[generation_funnel.PopulationKey.VALIDATED],
            "Generalization",
            generation_funnel_provenance,
        ),
        _share_metric(
            "realworld.generalization_validated_pct",
            generalizations_funnel.counts[generation_funnel.PopulationKey.VALIDATED]
            / generalizations_funnel.counts[generation_funnel.PopulationKey.ATTEMPTED],
            generation_validated_key,
            generation_attempt_key,
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.generalizations_reduced",
            generalizations_funnel.counts[generation_funnel.PopulationKey.REDUCED],
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.generalizations_final_usable",
            generalizations_funnel.counts[generation_funnel.PopulationKey.FINAL_USABLE],
            "Generalization",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.final_usable_projects",
            len(final_usable_project_ids),
            "Project",
            generation_funnel_provenance,
        ),
        _count_metric(
            "realworld.generalization_unknown_attempt_state",
            generalizations_funnel.unknown_attempt_state,
            "Generalization",
            generation_funnel_provenance,
        ),
        # Projects holding a validated generalized test before reduction. Reported
        # beside the headline so the prose can state what reduction itself costs.
        _count_metric(
            "realworld.stage4_projects",
            funnel.reduction.entering,
            "Project",
            funnel_provenance,
        ),
        _count_metric(
            "realworld.reduction_excluded_projects",
            funnel.reduction.exclusions,
            "Project",
            funnel_provenance,
        ),
        _count_metric(
            "realworld.reduction_excluded_baseline_side",
            funnel.reduction_excluded_baseline_side,
            "Project",
            funnel_provenance,
        ),
    ]
    metrics.extend(_stage_metrics(funnel, funnel_provenance))
    jpf_rows = int(jpf_exception_data["count"].sum())
    jpf_unparsed = int(
        jpf_exception_data.loc[
            jpf_exception_data["category"].eq("Unparsed"), "count"
        ].sum()
    )
    jpf_provenance = capture(fetch_jpf_exception_causes, query=JPF_EXCEPTION_DETAIL_SQL)
    jpf_total_key = "realworld.jpf_uncaught_exception_diagnostics"
    jpf_reclassified_key = "realworld.jpf_uncaught_exception_reclassified"
    metrics.extend(
        [
            _count_metric(jpf_total_key, jpf_rows, "Diagnostic", jpf_provenance),
            _count_metric(
                jpf_reclassified_key,
                jpf_rows - jpf_unparsed,
                "Diagnostic",
                jpf_provenance,
            ),
            _share_metric(
                "realworld.jpf_uncaught_exception_reclassified_pct",
                (jpf_rows - jpf_unparsed) / jpf_rows if jpf_rows else 0.0,
                jpf_reclassified_key,
                jpf_total_key,
                "Diagnostic",
                jpf_provenance,
            ),
        ]
    )
    mut_choice_total = int(mut_choice_data["count"].sum())
    mut_choice_dependent = int(
        mut_choice_data.loc[
            mut_choice_data["category"].eq("Choice-dependent"), "count"
        ].sum()
    )
    mut_choice_provenance = capture(
        fetch_mut_choice_sensitivity, query=MUT_CHOICE_SENSITIVITY_SQL
    )
    mut_choice_total_key = "realworld.parameter_type_choice_observations"
    mut_choice_dependent_key = "realworld.parameter_type_choice_dependent_lower_bound"
    metrics.extend(
        [
            _count_metric(
                mut_choice_total_key,
                mut_choice_total,
                "Assertion",
                mut_choice_provenance,
            ),
            _count_metric(
                mut_choice_dependent_key,
                mut_choice_dependent,
                "Assertion",
                mut_choice_provenance,
            ),
            _share_metric(
                "realworld.parameter_type_choice_dependent_lower_bound_pct",
                mut_choice_dependent / mut_choice_total if mut_choice_total else 0.0,
                mut_choice_dependent_key,
                mut_choice_total_key,
                "Assertion",
                mut_choice_provenance,
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
                "Generalization attempts are reported separately from emitted, "
                "filter-adjudicated, validated, reduced, and final-usable tests. "
                "A missing independent task record remains unknown."
            ),
            generalizations_funnel.table,
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
            mechanism_table,
            breakdown,
            filtering,
            Prose(
                "The reader-facing filtering column combines filter decisions, "
                "generation-gate refusals, and unsupported-capability declinations. "
                "The preceding table preserves the exact mechanisms."
            ),
            widening_table,
        ],
    )
    report = RQReport(
        rq="rq6",
        title="RQ6 - Causes of Unsuccessful Generalization (Real-World)",
        sections=[section],
        metrics=metrics,
    )
    validate_retained_consumers(report)
    return report


register(
    "rq6",
    ReportSpec(build, (CorpusInputSpec("real-world", "real-world"),)),
)
