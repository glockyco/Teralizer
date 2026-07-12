"""RQ1 mutation-score report built from the validated old-schema views."""

from __future__ import annotations

import re
from typing import cast

import numpy as np
import pandas as pd
from matplotlib.axes import Axes
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
from teralizer.exports import (
    get_project_within_type_order,
    get_table_group_order,
    standardize_project_name,
)
from teralizer.plotting import (
    FIGURE_CONFIG,
    calculate_label_offset,
    get_font_size,
    get_variant_color,
)
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
        frame = data.copy()
        frame["base_project"] = frame["project_name"].map(
            lambda name: re.sub(r"-\d+s$", "", str(name))
        )
        within_order = get_project_within_type_order()
        projects = sorted(
            frame["project_name"].unique(),
            key=lambda project: (
                get_table_group_order(project, "INITIAL"),
                within_order.get(project, 99),
            ),
        )
        base_projects = sorted(
            frame["base_project"].unique(),
            key=lambda project: (
                get_table_group_order(project, "INITIAL"),
                within_order.get(project, 99),
            ),
        )
        improvement_range: dict[str, tuple[float, float]] = {}
        for base in base_projects:
            subset = frame[frame["base_project"].eq(base)]
            non_initial = subset[subset["variant"].ne("INITIAL")]
            maximum = (
                float(non_initial["absolute_improvement"].max())
                if not non_initial.empty
                else 0.0
            )
            improvement_range[base] = (0.0, maximum + 0.45 * abs(maximum))

        fig = ax.figure
        fig.clear()
        fig.set_size_inches(
            FIGURE_CONFIG["width_multibar"], FIGURE_CONFIG["max_height"]
        )
        axes = fig.subplots(len(projects), 2, squeeze=False)
        for index, project in enumerate(projects):
            project_data = frame[frame["project_name"].eq(project)].copy()
            variants = project_data["variant"].tolist()
            x_positions = np.arange(len(variants))
            ax_left = cast(Axes, axes[index, 0])
            ax_right = cast(Axes, axes[index, 1])
            left_bars = ax_left.bar(
                x_positions,
                project_data["detected_of_covered_pct"],
                color=[get_variant_color(variant) for variant in variants],
            )
            ax_left.set_title(standardize_project_name(project))
            ax_left.set_ylabel("Detected (%)")
            y_max = 100 * FIGURE_CONFIG["y_padding_multiplier"]
            ax_left.set_ylim(0, y_max)
            offset = calculate_label_offset(0, y_max, FIGURE_CONFIG["label_offset_pct"])
            for bar in left_bars:
                height = bar.get_height()
                ax_left.text(
                    bar.get_x() + bar.get_width() / 2,
                    height + offset,
                    f"{height:.2f}",
                    ha="center",
                    va="bottom",
                    fontsize=get_font_size("small"),
                )
            improved = project_data[project_data["variant"].ne("INITIAL")]
            initial = project_data[project_data["variant"].eq("INITIAL")]
            right_bars = ax_right.bar(
                np.arange(len(improved)),
                improved["absolute_improvement"],
                color=[get_variant_color(variant) for variant in improved["variant"]],
            )
            ax_right.axhline(0, color="gray", linewidth=0.8)
            ax_right.set_title(standardize_project_name(project))
            ax_right.set_ylabel("Improvement (%)")
            lower, upper = improvement_range[project_data["base_project"].iloc[0]]
            ax_right.set_ylim(lower, upper * FIGURE_CONFIG["y_padding_multiplier"])
            right_offset = calculate_label_offset(
                lower,
                upper * FIGURE_CONFIG["y_padding_multiplier"],
                FIGURE_CONFIG["label_offset_pct"],
            )
            for position, bar in enumerate(right_bars):
                height = bar.get_height()
                relative = (
                    improved.iloc[position]["relative_improvement"]
                    if not initial.empty
                    else 0.0
                )
                ax_right.text(
                    bar.get_x() + bar.get_width() / 2,
                    height + (right_offset if height >= 0 else -right_offset),
                    f"{height:.2f}\n({relative:+.2f}%)",
                    ha="center",
                    va="bottom" if height >= 0 else "top",
                    fontsize=get_font_size("small"),
                )
            if index == len(projects) - 1:

                def format_label(value: str) -> str:
                    match = re.match(r"([A-Z]+)_([0-9]+)_TRIES", value)
                    return (
                        f"{match.group(1)}$_{{{match.group(2)}}}$" if match else value
                    )

                ax_left.set_xticks(x_positions)
                ax_left.set_xticklabels(
                    [format_label(value) for value in variants], rotation=45, ha="right"
                )
                ax_right.set_xticks(np.arange(len(improved)))
                ax_right.set_xticklabels(
                    [format_label(value) for value in improved["variant"]],
                    rotation=45,
                    ha="right",
                )
            else:
                ax_left.set_xticks([])
                ax_right.set_xticks([])
            ax_left.set_yticks([])
            ax_right.set_yticks([])
        fig.tight_layout()

    return Figure(
        "mutation_detection_comparison",
        build,
        "Mutation detection rates and improvements across projects and variants.",
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
