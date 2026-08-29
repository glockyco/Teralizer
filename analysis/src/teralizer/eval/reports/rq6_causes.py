"""RQ6 real-world exclusion causes report."""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import replace
from pathlib import Path
from typing import cast

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.evidence import reporeapers_reconstruction as reconstruction
from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec, ReportContext
from teralizer.eval.model import (
    ColumnSpec,
    Metric,
    MetricPopulation,
    Prose,
    RQReport,
    Section,
    Table,
    ValueKind,
)
from teralizer.eval.provenance import Provenance, capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _filtering_comparison as filtering_comparison
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

TEST_TYPE_CATEGORY_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT CASE
           WHEN t.test_annotations_source_code = '@Theory' THEN 'junit_theory'
           WHEN t.test_annotations_source_code = '@Override' THEN 'overridden_declaration'
           WHEN t.test_annotation_name = 'TestNG' THEN 'testng'
           ELSE 'unknown'
       END AS category,
       count(DISTINCT t.id) AS count
FROM filter_result fr
JOIN eligible_projects ep ON ep.id = fr.project_id
JOIN test t ON t.id = fr.test_id
WHERE fr.decision = 'REJECT'
  AND fr.filter_name LIKE '%%TestTypeFilter'
GROUP BY category
ORDER BY category
"""
TEST_TYPE_CATEGORIES = (
    "junit_theory",
    "overridden_declaration",
    "testng",
)
TEST_TYPE_CATEGORY_METRIC_KEYS = frozenset(
    f"realworld.test_type.{category}" for category in TEST_TYPE_CATEGORIES
)


def _fetch_assertions_without_resolution(conn: Connection, variant: str) -> int:
    """Assertions whose resolver telemetry was never persisted. Must be zero."""
    df = read_sql(conn, UNRESOLVED_TELEMETRY_SQL, exclusion.query_params(variant))
    return int(df["assertions_without_resolution"].iloc[0])


def _fetch_test_type_categories(conn: Connection, variant: str) -> dict[str, int]:
    """Return the recorded declaration categories rejected by TestType."""
    df = read_sql(conn, TEST_TYPE_CATEGORY_SQL, exclusion.query_params(variant))
    categories = {str(row["category"]): int(row["count"]) for _, row in df.iterrows()}
    if set(categories) != set(TEST_TYPE_CATEGORIES):
        raise exclusion.ExclusionEvidenceError(
            "TestType declaration categories differ: "
            f"expected={sorted(TEST_TYPE_CATEGORIES)}, "
            f"actual={sorted(categories)}"
        )
    return categories


def _validate_test_type_categories(
    categories: dict[str, int], filtering: pd.DataFrame
) -> None:
    row = filtering.loc[
        filtering["level"].eq("Test") & filtering["filter"].eq("TestType")
    ]
    if len(row) != 1:
        raise exclusion.ExclusionEvidenceError(
            f"expected one TestType filtering row, found {len(row)}"
        )
    rejected = int(row["reject"].iloc[0])
    if sum(categories.values()) != rejected:
        raise exclusion.ExclusionEvidenceError(
            "TestType declaration categories do not partition its rejections: "
            f"categories={sum(categories.values())}, rejected={rejected}"
        )


def _population(
    key: str, entity_level: str, input_role: str = "real-world"
) -> MetricPopulation:
    return MetricPopulation(key, entity_level, input_role)


def _count_metric(
    key: str,
    value: int,
    entity_level: str,
    provenance: Provenance,
    input_role: str = "real-world",
) -> Metric:
    return Metric(
        key,
        value,
        fmt="count",
        provenance=provenance,
        kind=ValueKind.COUNT,
        population=_population(key, entity_level, input_role),
    )


def _share_metric(
    key: str,
    value: float,
    numerator_key: str,
    denominator_key: str,
    entity_level: str,
    provenance: Provenance,
    input_role: str = "real-world",
) -> Metric:
    return Metric(
        key,
        value,
        fmt="pct1",
        provenance=provenance,
        kind=ValueKind.SHARE,
        population=_population(numerator_key, entity_level, input_role),
        numerator_key=numerator_key,
        denominator_key=denominator_key,
    )


def _filtering_metrics(
    dataset: str,
    input_role: str,
    summary: filtering_comparison.FilteringSummary,
    provenance: Provenance,
) -> list[Metric]:
    prefix = f"rq6.filtering.{dataset}"
    total_key = f"{prefix}.total"
    included_key = f"{prefix}.included"
    return [
        _count_metric(
            total_key,
            summary.total,
            "Generalization",
            provenance,
            input_role,
        ),
        _count_metric(
            included_key,
            summary.included,
            "Generalization",
            provenance,
            input_role,
        ),
        _count_metric(
            f"{prefix}.excluded",
            summary.excluded,
            "Generalization",
            provenance,
            input_role,
        ),
        _share_metric(
            f"{prefix}.included_pct",
            float(summary.included_share),
            included_key,
            total_key,
            "Generalization",
            provenance,
            input_role,
        ),
    ]


TEST_FILTERING_FLOW_FIELDS = (
    ("identified", "identified"),
    ("inherited_method_evaluated", "inherited_method_evaluated"),
    ("inherited_method_rejected", "inherited_method_rejected"),
    ("pre_filter_failures", "pre_filter_failures"),
    ("round_one_evaluated", "round_one_evaluated"),
    ("round_one_rejected", "round_one_rejected"),
    ("round_one_overlap", "round_one_overlap"),
    ("inter_round_failures", "inter_round_failures"),
    ("round_two_evaluated", "round_two_evaluated"),
)
TEST_FILTERING_FLOW_METRIC_KEYS = frozenset(
    f"realworld.test_filtering.{suffix}" for suffix, _ in TEST_FILTERING_FLOW_FIELDS
)


def _test_filtering_flow_metrics(
    flow: exclusion.TestFilteringFlow, provenance: Provenance
) -> list[Metric]:
    return [
        _count_metric(
            f"realworld.test_filtering.{suffix}",
            getattr(flow, field),
            "Test",
            provenance,
        )
        for suffix, field in TEST_FILTERING_FLOW_FIELDS
    ]


def _validate_filtering_funnel(
    summary: filtering_comparison.FilteringSummary,
    funnel: generation_funnel.GeneralizationFunnel,
) -> None:
    expected = (
        funnel.counts[generation_funnel.PopulationKey.FILTER_RESULT_RECORDED],
        funnel.counts[generation_funnel.PopulationKey.FILTER_PASSED],
    )
    observed = (summary.total, summary.included)
    if observed != expected:
        raise exclusion.ExclusionEvidenceError(
            "RepoReapers filtering comparison disagrees with the generalization funnel: "
            f"observed={observed}, expected={expected}"
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


RECONSTRUCTION_CLAIMS = (
    "no-assertions",
    "assertion-to-mut",
    "output-directories",
)


def _load_reconstruction_audit(path: Path) -> dict[str, object]:
    return reconstruction.validate_audit(json.loads(path.read_text(encoding="utf-8")))


def _validate_reconstruction_inputs(
    audit: dict[str, object], inventory_path: Path, output_population_path: Path
) -> None:
    inventory = reconstruction.validate_inventory(
        json.loads(inventory_path.read_text(encoding="utf-8"))
    )
    if audit["inventory"] != inventory:
        raise ValueError("reconstruction audit inventory differs from its report input")
    population_document = json.loads(output_population_path.read_text(encoding="utf-8"))
    populations = population_document.get("populations")
    if not isinstance(populations, list) or len(populations) != 1:
        raise ValueError(
            "output-directory population input must contain one population"
        )
    population = cast(dict[str, object], populations[0])
    output_claim = next(
        claim
        for claim in cast(list[dict[str, object]], audit["claims"])
        if claim["claim"] == "output-directories"
    )
    if population.get("claim") != "output-directories":
        raise ValueError("output-directory population has the wrong claim")
    if population.get("identity_sha256") != output_claim["population_sha256"]:
        raise ValueError("output-directory population identity differs from the audit")
    if population.get("row_count") != output_claim["total"]:
        raise ValueError("output-directory population total differs from the audit")


def _reconstruction_claims(audit: dict[str, object]) -> list[dict[str, object]]:
    claims = audit["claims"]
    if not isinstance(claims, list):
        raise TypeError("reconstruction audit claims must be an array")
    records = [cast(dict[str, object], claim) for claim in claims]
    by_name = {str(claim["claim"]): claim for claim in records}
    if set(by_name) != set(RECONSTRUCTION_CLAIMS):
        raise ValueError(
            "RQ6 reconstruction claims differ: "
            f"expected={sorted(RECONSTRUCTION_CLAIMS)}, "
            f"actual={sorted(by_name)}"
        )
    return [by_name[name] for name in RECONSTRUCTION_CLAIMS]


def _reconstruction_summary_table(
    claims: list[dict[str, object]], provenance: Provenance
) -> Table:
    frame = pd.DataFrame(claims).rename(
        columns={
            "claim": "claim_key",
            "status": "claim_status",
            "reason": "finding",
        }
    )
    return Table(
        key="rq6_reconstruction_summary",
        df=frame,
        columns=[
            ColumnSpec("Claim", "claim_key", kind=ValueKind.TEXT),
            ColumnSpec("Status", "claim_status", kind=ValueKind.TEXT),
            ColumnSpec("Resolved", "resolved", kind=ValueKind.COUNT),
            ColumnSpec("Unresolved", "unresolved", kind=ValueKind.COUNT),
            ColumnSpec("Unreviewed", "unreviewed_population", kind=ValueKind.COUNT),
            ColumnSpec("Incompatible", "incompatible", kind=ValueKind.COUNT),
            ColumnSpec("Total", "total", kind=ValueKind.COUNT),
            ColumnSpec("Method", "method", kind=ValueKind.TEXT),
            ColumnSpec("Finding", "finding", kind=ValueKind.TEXT),
        ],
        caption="Status of reconstructed RepoReapers evidence claims.",
        label="tab:rq6-reconstruction-summary",
        row_key="claim_key",
        provenance=provenance,
        note=(
            "Resolved, unresolved, and incompatible are audit partitions. "
            "Sample findings are estimates with the stated method and confidence "
            "interval. They are not exact population rates."
        ),
    )


def _reconstruction_outcome_counts(
    audit: dict[str, object],
) -> Counter[tuple[str, str, str]]:
    entities = audit["entities"]
    if not isinstance(entities, list):
        raise TypeError("reconstruction audit entities must be an array")
    return Counter(
        (
            str(cast(dict[str, object], entity)["claim"]),
            str(cast(dict[str, object], entity)["status"]),
            str(cast(dict[str, object], entity)["label"]),
        )
        for entity in entities
    )


def _reconstruction_outcomes_table(
    counts: Counter[tuple[str, str, str]], provenance: Provenance
) -> Table:
    rows = [
        {
            "row_key": f"{claim}:{status}:{outcome}",
            "claim": claim,
            "status": status,
            "outcome": outcome,
            "reviewed_count": count,
        }
        for (claim, status, outcome), count in sorted(counts.items())
    ]
    return Table(
        key="rq6_reconstruction_outcomes",
        df=pd.DataFrame(rows),
        columns=[
            ColumnSpec("Claim", "claim", kind=ValueKind.TEXT),
            ColumnSpec("Evidence status", "status", kind=ValueKind.TEXT),
            ColumnSpec("Reviewed outcome", "outcome", kind=ValueKind.TEXT),
            ColumnSpec("Count", "reviewed_count", kind=ValueKind.COUNT),
        ],
        caption="Reviewed outcomes in the reconstructed RepoReapers evidence.",
        label="tab:rq6-reconstruction-outcomes",
        row_key="row_key",
        provenance=provenance,
        note=(
            "Counts describe reviewed records. For sampled claims, these counts "
            "do not describe the full population."
        ),
    )


RECONSTRUCTION_ESTIMATE_METRIC_KEYS = frozenset(
    {
        "rq6.reconstruction.no_assertions.genuine_absence_estimate_pct",
        "rq6.reconstruction.no_assertions.genuine_absence_ci_lower_pct",
        "rq6.reconstruction.no_assertions.genuine_absence_ci_upper_pct",
    }
)

RECONSTRUCTION_OUTCOME_METRIC_KEYS = {
    ("assertion-to-mut", "resolved", "supported-mapping"): (
        "rq6.reconstruction.assertion_to_mut.reviewed_supported_mapping"
    ),
    ("assertion-to-mut", "resolved", "contradicted-mapping"): (
        "rq6.reconstruction.assertion_to_mut.reviewed_contradicted_mapping"
    ),
    ("assertion-to-mut", "unresolved", "insufficient-specification-evidence"): (
        "rq6.reconstruction.assertion_to_mut.reviewed_insufficient_specification_evidence"
    ),
    ("output-directories", "resolved", "default-directory-mismatch"): (
        "rq6.reconstruction.output_directories.default_directory_mismatch"
    ),
    ("output-directories", "resolved", "absent-artifact"): (
        "rq6.reconstruction.output_directories.absent_artifact"
    ),
    ("output-directories", "resolved", "earlier-build-failure"): (
        "rq6.reconstruction.output_directories.earlier_build_failure"
    ),
    ("output-directories", "incompatible", "incompatible-evidence"): (
        "rq6.reconstruction.output_directories.incompatible_evidence"
    ),
}


def _reconstruction_metrics(
    claims: list[dict[str, object]],
    outcome_counts: Counter[tuple[str, str, str]],
    provenance: Provenance,
) -> list[Metric]:
    metrics: list[Metric] = []
    for claim in claims:
        claim_key = str(claim["claim"]).replace("-", "_")
        for partition in ("resolved", "unresolved", "incompatible", "total"):
            metrics.append(
                _count_metric(
                    f"rq6.reconstruction.{claim_key}.{partition}",
                    int(claim[partition]),
                    "Audit entity",
                    provenance,
                    "reconstruction-audit",
                )
            )

    no_assertions = next(claim for claim in claims if claim["claim"] == "no-assertions")
    estimate = no_assertions["estimate"]
    if not isinstance(estimate, dict) or estimate.get("quantity") != "genuine-absence":
        raise ValueError("NoAssertions reconstruction lacks its structured estimate")
    estimate_population = MetricPopulation(
        "rq6.reconstruction.no_assertions.reviewed",
        "Test",
        "reconstruction-audit",
    )
    for key, field in (
        (
            "rq6.reconstruction.no_assertions.genuine_absence_estimate_pct",
            "value_pct",
        ),
        (
            "rq6.reconstruction.no_assertions.genuine_absence_ci_lower_pct",
            "lower_bound_pct",
        ),
        (
            "rq6.reconstruction.no_assertions.genuine_absence_ci_upper_pct",
            "upper_bound_pct",
        ),
    ):
        metrics.append(
            Metric(
                key,
                float(estimate[field]),
                fmt="percent2",
                provenance=provenance,
                kind=ValueKind.PERCENT,
                population=estimate_population,
            )
        )

    for outcome, key in RECONSTRUCTION_OUTCOME_METRIC_KEYS.items():
        claim, _, _ = outcome
        metrics.append(
            _count_metric(
                key,
                outcome_counts[outcome],
                "Assertion-to-MUT review" if claim == "assertion-to-mut" else "Project",
                provenance,
                "reconstruction-audit",
            )
        )
    return metrics


FILTERING_METRIC_KEYS = frozenset(
    f"rq6.filtering.{dataset}.{suffix}"
    for dataset in ("controlled", "realworld")
    for suffix in ("total", "included", "excluded", "included_pct")
)

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
        "realworld.generalizations_filter_result_recorded",
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
    | FILTERING_METRIC_KEYS
    | TEST_FILTERING_FLOW_METRIC_KEYS
    | TEST_TYPE_CATEGORY_METRIC_KEYS
    | {
        f"rq6.reconstruction.{claim.replace('-', '_')}.{partition}"
        for claim in RECONSTRUCTION_CLAIMS
        for partition in ("resolved", "unresolved", "incompatible", "total")
    }
    | RECONSTRUCTION_ESTIMATE_METRIC_KEYS
    | frozenset(RECONSTRUCTION_OUTCOME_METRIC_KEYS.values())
)

RETAINED_TABLE_KEYS = frozenset(
    {
        "tab-processing-failures",
        "rq6_generalization_funnel",
        "rq6_filtering_comparison",
        "rq6_jpf_exception_causes",
        "rq6_mut_choice_sensitivity",
        "rq6_exclusion_mechanisms",
        "tab-exclusions-breakdown-extended",
        "tab-exclusions-filtering-extended",
        "rq6_widening_refusals",
        "rq6_reconstruction_summary",
        "rq6_reconstruction_outcomes",
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
    controlled_conn = context.corpus("controlled")
    reconstruction_path = context.file("reconstruction-audit")
    reconstruction_inventory_path = context.file("reconstruction-inventory")
    output_population_path = context.file("output-directory-population")
    if (
        reconstruction_path is None
        or reconstruction_inventory_path is None
        or output_population_path is None
    ):
        raise AssertionError("required reconstruction evidence resolved as absent")
    reconstruction_audit = _load_reconstruction_audit(reconstruction_path)
    _validate_reconstruction_inputs(
        reconstruction_audit, reconstruction_inventory_path, output_population_path
    )
    reconstruction_claims = _reconstruction_claims(reconstruction_audit)
    reconstruction_provenance = capture(_load_reconstruction_audit)
    reconstruction_summary = _reconstruction_summary_table(
        reconstruction_claims, reconstruction_provenance
    )
    reconstruction_outcome_counts = _reconstruction_outcome_counts(reconstruction_audit)
    reconstruction_outcomes = _reconstruction_outcomes_table(
        reconstruction_outcome_counts, reconstruction_provenance
    )
    variant = _funnel.resolve_variant(conn)
    exclusion.validate_evidence(conn, variant)
    test_filtering_flow_provenance = capture(
        exclusion.fetch_test_filtering_flow,
        query=exclusion.TEST_FILTERING_FLOW_SQL,
    )
    test_filtering_flow = exclusion.fetch_test_filtering_flow(conn, variant)
    controlled_filtering_provenance = capture(
        filtering_comparison.fetch_controlled_filtering,
        query=filtering_comparison.CONTROLLED_FILTERING_SQL,
    )
    realworld_filtering_provenance = capture(
        filtering_comparison.fetch_realworld_filtering,
        query=filtering_comparison.REALWORLD_FILTERING_SQL,
    )
    controlled_filtering = filtering_comparison.summarize_filtering(
        filtering_comparison.fetch_controlled_filtering(controlled_conn)
    )
    realworld_filtering = filtering_comparison.summarize_filtering(
        filtering_comparison.fetch_realworld_filtering(conn, variant)
    )
    filtering_table_provenance = capture(
        filtering_comparison.build_filtering_comparison_table,
        query=(
            filtering_comparison.CONTROLLED_FILTERING_SQL
            + "\n-- RepoReapers\n"
            + filtering_comparison.REALWORLD_FILTERING_SQL
        ),
    )
    filtering_comparison_table = filtering_comparison.build_filtering_comparison_table(
        controlled_filtering,
        realworld_filtering,
        filtering_table_provenance,
    )
    funnel = _funnel.build_funnel(conn, variant=variant)
    generation_funnel_provenance = capture(
        generation_funnel.build_generalization_funnel,
        query=generation_funnel.GENERALIZATION_LIFECYCLE_SQL,
    )
    generalizations_funnel = generation_funnel.build_generalization_funnel(
        conn, variant, generation_funnel_provenance
    )
    _validate_filtering_funnel(realworld_filtering, generalizations_funnel)
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
        float_spec="!t",
        provenance=mechanism_provenance,
    )

    filtering_data = exclusion.fetch_filter_decisions(conn, variant)
    exclusion.validate_filtering_reconciliation(filtering_data, mechanism_partition)
    test_type_categories = _fetch_test_type_categories(conn, variant)
    _validate_test_type_categories(test_type_categories, filtering_data)
    filtering = build_filtering_table(
        filtering_data,
        key="tab-exclusions-filtering-extended",
        label="tab:exclusions-filtering-extended",
        caption="Filtering results for {entity.variant.improved_c} in the RepoReapers projects.",
        short_caption=(
            "RepoReapers filtering decisions for {entity.variant.improved_c} by level and filter"
        ),
        body_style="\\tabstyle\n\\renewcommand{\\arraystretch}{1.1}",
        full_width=False,
    )
    filtering = replace(
        filtering,
        df=filtering.df.assign(
            row_key=filtering.df["level"] + ":" + filtering.df["filter"]
        ),
        row_key="row_key",
        float_spec="!t",
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
            "realworld.generalizations_filter_result_recorded",
            generalizations_funnel.counts[
                generation_funnel.PopulationKey.FILTER_RESULT_RECORDED
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
    metrics.extend(
        _filtering_metrics(
            "controlled",
            "controlled",
            controlled_filtering,
            controlled_filtering_provenance,
        )
    )
    metrics.extend(
        _filtering_metrics(
            "realworld",
            "real-world",
            realworld_filtering,
            realworld_filtering_provenance,
        )
    )
    metrics.extend(
        _test_filtering_flow_metrics(
            test_filtering_flow, test_filtering_flow_provenance
        )
    )
    test_type_provenance = capture(
        _fetch_test_type_categories, query=TEST_TYPE_CATEGORY_SQL
    )
    metrics.extend(
        _count_metric(
            f"realworld.test_type.{category}",
            test_type_categories[category],
            "Test",
            test_type_provenance,
        )
        for category in TEST_TYPE_CATEGORIES
    )
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
    metrics.extend(
        _reconstruction_metrics(
            reconstruction_claims,
            reconstruction_outcome_counts,
            reconstruction_provenance,
        )
    )
    section = Section(
        title="Project-level exclusions",
        blocks=[
            Prose(
                "Real-world exclusions separate project-level failures from "
                "filtering and downstream test, assertion, and generalization failures."
            ),
            funnel.table,
            Prose(
                "Filtering results use generalized tests that reach filtering as "
                "each dataset's denominator. They do not measure overall success "
                "or project applicability."
            ),
            filtering_comparison_table,
            Prose(
                "Generalization attempts are reported separately from emitted, "
                "filter-result-recorded, validated, reduced, and final-usable tests. "
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
                "generation-gate refusals, and inherited-test inlining limits. "
                "The preceding table preserves the exact mechanisms."
            ),
            widening_table,
            Prose(
                "Reconstructed evidence distinguishes resolved findings from "
                "unresolved and incompatible records. Exact rates are omitted "
                "because none of the three claims has a complete compatible "
                "classification. Sample estimates retain their method and "
                "confidence interval."
            ),
            reconstruction_summary,
            reconstruction_outcomes,
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
    ReportSpec(
        build,
        (
            CorpusInputSpec("real-world", "real-world"),
            CorpusInputSpec(
                "controlled",
                "controlled",
                filtering_comparison.CONTROLLED_REQUIRES,
            ),
            FileInputSpec(
                "reconstruction-audit",
                "analysis/data/report-inputs/reporeapers-reconstruction-audit.json",
            ),
            FileInputSpec(
                "reconstruction-inventory",
                "analysis/data/report-inputs/reporeapers-reconstruction-inventory.json",
            ),
            FileInputSpec(
                "output-directory-population",
                "analysis/data/report-inputs/reporeapers-output-directories-population.json",
            ),
        ),
    ),
)
