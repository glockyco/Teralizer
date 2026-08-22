"""RQ6 real-world exclusion causes report."""

from __future__ import annotations
from dataclasses import replace

from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import Metric, Prose, RQReport, Section
from teralizer.eval.provenance import Provenance, capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel
from teralizer.eval.reports import _generalization_funnel as generation_funnel
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


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("real-world")
    variant = _funnel.resolve_variant(conn)
    funnel = _funnel.build_funnel(conn, variant=variant)
    generation_funnel_provenance = capture(
        generation_funnel.build_generalization_funnel,
        query=generation_funnel.GENERALIZATION_LIFECYCLE_SQL,
    )
    generalizations_funnel = generation_funnel.build_generalization_funnel(
        conn, variant, generation_funnel_provenance
    )
    breakdown_data = exclusion.fetch_mechanism_counts(conn, variant)
    jpf_exception_data = fetch_jpf_exception_causes(conn, variant)
    jpf_table = jpf_exception_table(jpf_exception_data)
    mut_choice_data = fetch_mut_choice_sensitivity(conn, variant)
    mut_table = mut_choice_table(mut_choice_data)

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
        provenance=capture(
            exclusion.fetch_mechanism_counts, query=exclusion.MECHANISM_COUNTS_SQL
        ),
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
        latex_resize_to_width=True,
        provenance=capture(
            exclusion.fetch_filter_decisions, query=exclusion.FILTER_DECISION_SQL
        ),
    )

    funnel_provenance = capture(
        _funnel.build_funnel, query=_funnel._PROJECT_SIGNALS_SQL
    )
    breakdown_provenance = capture(
        exclusion.fetch_mechanism_counts, query=exclusion.MECHANISM_COUNTS_SQL
    )
    levels = breakdown_data.set_index("level")
    assertions = levels.loc["Assertion"]
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
            generalizations_funnel.counts[generation_funnel.PopulationKey.ATTEMPTED],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalizations_emitted",
            generalizations_funnel.counts[generation_funnel.PopulationKey.EMITTED],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalizations_filter_adjudicated",
            generalizations_funnel.counts[
                generation_funnel.PopulationKey.FILTER_ADJUDICATED
            ],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalizations_filter_passed",
            generalizations_funnel.counts[
                generation_funnel.PopulationKey.FILTER_PASSED
            ],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalizations_validated",
            generalizations_funnel.counts[generation_funnel.PopulationKey.VALIDATED],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalization_validated_pct",
            generalizations_funnel.counts[generation_funnel.PopulationKey.VALIDATED]
            / generalizations_funnel.counts[generation_funnel.PopulationKey.ATTEMPTED],
            fmt="pct1",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalizations_reduced",
            generalizations_funnel.counts[generation_funnel.PopulationKey.REDUCED],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalizations_final_usable",
            generalizations_funnel.counts[generation_funnel.PopulationKey.FINAL_USABLE],
            fmt="count",
            provenance=generation_funnel_provenance,
        ),
        Metric(
            "realworld.generalization_unknown_attempt_state",
            generalizations_funnel.unknown_attempt_state,
            fmt="count",
            provenance=generation_funnel_provenance,
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
        sections=[section],
        metrics=metrics,
    )


register(
    "rq6",
    ReportSpec(build, (CorpusInputSpec("real-world", "real-world"),)),
)
