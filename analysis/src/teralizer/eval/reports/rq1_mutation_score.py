"""RQ1 mutation-score report built from the validated old-schema views."""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required
from teralizer.eval.model import (
    ColumnSpec,
    Figure,
    Metric,
    Prose,
    RQReport,
    Section,
    Table,
)
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.rq1_mutation_detection import (
    compute_detection_improvements,
    compute_mutator_statistics,
    compute_project_mutation_coverage,
    get_mutation_coverage_data,
    get_mutation_results_by_mutator,
    get_mutation_results_by_project_variant,
    get_project_mutator_data,
    get_total_classes_from_filesystem,
)

VARIANTS = (
    "INITIAL",
    "NAIVE_10_TRIES",
    "NAIVE_50_TRIES",
    "NAIVE_200_TRIES",
    "IMPROVED_10_TRIES",
    "IMPROVED_50_TRIES",
    "IMPROVED_200_TRIES",
)

REQUIRES: tuple[Required, ...] = (
    Required("project", "table", ("id", "use_test_generalization")),
    Required("test", "table", ("id", "project_id")),
    Required("junit_test_report", "table", ("project_id", "stage")),
    Required("jacoco_coverage_report", "table", ("project_id", "stage")),
    Required("v_projects_successes", "view", ("project_id",)),
    Required(
        "mv_mutation_results_by_project_variant",
        "view",
        ("project_id", "variant", "total", "covered", "detected"),
    ),
    Required(
        "mv_mutation_results_by_project_variant_mutator",
        "view",
        ("project_id", "mutator", "variant", "total", "covered", "detected"),
    ),
)


def _coverage_table(df: pd.DataFrame) -> Table:
    result = df.copy()
    result["test_inclusion_pct"] = (
        result["included_tests"] / result["total_tests"].replace(0, pd.NA) * 100
    ).fillna(0)
    result["class_inclusion_pct"] = (
        result["included_classes"] / result["total_classes"].replace(0, pd.NA) * 100
    ).fillna(0)
    result["covered_pct"] = (
        result["covered"] / result["total"].replace(0, pd.NA) * 100
    ).fillna(0)
    result["uncovered_pct"] = (
        result["uncovered"] / result["total"].replace(0, pd.NA) * 100
    ).fillna(0)
    result["display_project"] = (
        result["project"]
        .astype(str)
        .str.replace("-default", "", regex=False)
        .replace({"commons-utils": "commons-utils-dev"})
    )
    result["included_tests_display"] = result.apply(
        lambda row: f"{int(row['included_tests']):,} ({row['test_inclusion_pct']:.1f}%)",
        axis=1,
    )
    result["included_classes_display"] = result.apply(
        lambda row: f"{int(row['included_classes']):,} ({row['class_inclusion_pct']:.1f}%)",
        axis=1,
    )
    result["covered_display"] = result.apply(
        lambda row: f"{int(row['covered']):,} ({row['covered_pct']:.1f}%)", axis=1
    )
    result["uncovered_display"] = result.apply(
        lambda row: f"{int(row['uncovered']):,} ({row['uncovered_pct']:.1f}%)", axis=1
    )
    columns = [
        ColumnSpec("Project", "display_project"),
        ColumnSpec("Included test methods", "included_tests_display"),
        ColumnSpec("Included implementation classes", "included_classes_display"),
        ColumnSpec("Total mutants", "total", "count"),
        ColumnSpec("Covered mutants", "covered_display"),
        ColumnSpec("Uncovered mutants", "uncovered_display"),
    ]
    return Table(
        "mutants_per_project",
        result,
        columns,
        "Number of total, covered, and uncovered mutants in included classes per project.",
        "tab:mutants-per-project",
        provenance=capture(compute_project_mutation_coverage),
    )


def _mutator_table(df: pd.DataFrame) -> Table:
    result = df.copy()
    result["mutator"] = (
        result["mutator"]
        .astype(str)
        .str.replace("Mutator", "", regex=False)
        .str.strip()
    )
    result["mutator"] = result["mutator"].replace(
        {
            "RemoveConditional_ORDER_ELSE": "RemoveConditionalOrderElse",
            "RemoveConditional_EQUAL_ELSE": "RemoveConditionalEqualElse",
        }
    )
    for column in ("detected_diff_naive_200_tries", "detected_diff_improved_200_tries"):
        if column not in result:
            result[column] = 0
    result["naive_delta_display"] = result["detected_diff_naive_200_tries"].map(
        lambda value: "--" if abs(float(value)) < 1e-12 else f"({float(value):+.2f})"
    )
    result["improved_delta_display"] = result["detected_diff_improved_200_tries"].map(
        lambda value: "--" if abs(float(value)) < 1e-12 else f"({float(value):+.2f})"
    )
    wanted = [
        "mutator",
        "total_mutants",
        "percent",
        "min_percent",
        "max_percent",
        "INITIAL",
        "NAIVE_200_TRIES",
        "detected_diff_naive_200_tries",
        "IMPROVED_200_TRIES",
        "detected_diff_improved_200_tries",
        "naive_delta_display",
        "improved_delta_display",
    ]
    for column in wanted:
        if column not in result:
            result[column] = 0
    result = result[wanted]
    columns = [
        ColumnSpec("Mutator", "mutator"),
        ColumnSpec("Total", "total_mutants", "count"),
        ColumnSpec("Total %", "percent", "float2"),
        ColumnSpec("Min %", "min_percent", "float2"),
        ColumnSpec("Max %", "max_percent", "float2"),
        ColumnSpec("Initial", "INITIAL", "float2"),
        ColumnSpec("Naive 200", "NAIVE_200_TRIES", "float2"),
        ColumnSpec("Naive Δ", "naive_delta_display"),
        ColumnSpec("Improved 200", "IMPROVED_200_TRIES", "float2"),
        ColumnSpec("Improved Δ", "improved_delta_display"),
    ]
    return Table(
        "detections_per_mutator",
        result,
        columns,
        "Mutation detection rates by mutator.",
        "tab:detections-per-mutator",
        provenance=capture(compute_mutator_statistics),
    )


def _figure(data: pd.DataFrame) -> Figure:
    def build(ax) -> None:
        if data.empty:
            ax.set_title("Mutation detection comparison")
            return
        pivot = data.pivot_table(
            index="project_name",
            columns="variant",
            values="detected_of_covered_pct",
            aggfunc="first",
        )
        pivot.plot(kind="bar", ax=ax, legend=True)
        ax.set_ylabel("Detected (%)")
        ax.set_xlabel("Project")
        ax.tick_params(axis="x", rotation=45)
        ax.set_title("Mutation detection by project and variant")

    return Figure(
        "mutation_detection_comparison",
        build,
        "Mutation detection rates across projects and variants.",
        "fig:mutation-detection-comparison",
        data=data,
        provenance=capture(compute_detection_improvements),
    )


def build(conn: Connection) -> RQReport:
    coverage = compute_project_mutation_coverage(
        get_mutation_coverage_data(conn), get_total_classes_from_filesystem(conn)
    )
    detection = compute_detection_improvements(
        get_mutation_results_by_project_variant(conn)
    )
    mutator = compute_mutator_statistics(
        get_mutation_results_by_mutator(conn, list(VARIANTS)),
        get_project_mutator_data(conn),
    )
    tables = [_coverage_table(coverage), _mutator_table(mutator)]
    metrics = [
        Metric(
            "rq1.projects",
            int(coverage["project"].nunique()),
            "count",
            capture(compute_project_mutation_coverage),
        ),
        Metric(
            "rq1.mutation_rows",
            len(detection),
            "count",
            capture(compute_detection_improvements),
        ),
    ]
    section = Section(
        "Mutation score",
        [
            Prose(
                "The report covers {rq1.projects} projects and {rq1.mutation_rows} project-variant mutation summaries."
            ),
            tables[0],
            _figure(detection),
            tables[1],
        ],
    )
    return RQReport(
        "rq1", "RQ1 - Mutation-score improvement", "postgres_dev", [section], metrics
    )


register("rq1", ReportSpec(build, "postgres_dev", "old", REQUIRES))
