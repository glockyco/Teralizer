"""RQ1 mutation-score report built from the validated old-schema views."""

from __future__ import annotations

import re
from pathlib import Path
from typing import cast

import numpy as np
import pandas as pd
from matplotlib.axes import Axes
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required, read_sql
from teralizer.eval.evidence import project_sources
from teralizer.eval.entities import ref_for_csv
from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec, ReportContext
from teralizer.eval.model import (
    ColumnSpec,
    Figure,
    Metric,
    MetricPopulation,
    RQReport,
    Section,
    Table,
    ValueKind,
    decimal_value,
    share_value,
)
from teralizer.eval.provenance import Provenance, capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.exports import (
    get_project_type,
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
    MUTATION_RESULTS_BY_PROJECT_VARIANT_SQL,
    compute_detection_improvements,
    compute_mutator_statistics,
    compute_project_mutation_coverage,
    get_mutation_coverage_data,
    get_mutation_results_by_mutator,
    get_mutation_results_by_project_variant,
    get_project_mutator_data,
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

_EFFECTIVENESS_COHORTS = {
    "eqbench_evosuite": (
        "eqbench-es-default-1s",
        "eqbench-es-default-10s",
        "eqbench-es-default-60s",
    ),
    "commons_evosuite": (
        "commons-utils-es-default-1s",
        "commons-utils-es-default-10s",
        "commons-utils-es-default-60s",
    ),
    "commons_developer": ("commons-utils",),
}
_EFFECTIVENESS_RANGE_KEYS = frozenset(
    f"effectiveness.{cohort}.mutation_improvement_{bound}_pp"
    for cohort in _EFFECTIVENESS_COHORTS
    for bound in ("min", "max")
)
EFFECTIVENESS_METRIC_KEYS = _EFFECTIVENESS_RANGE_KEYS | {
    "effectiveness.commons_developer.baseline_mutation_score_pct"
}

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
    for count, total, share in (
        ("included_tests", "total_tests", "test_inclusion_share"),
        ("included_classes", "total_classes", "class_inclusion_share"),
        ("covered", "total", "covered_share"),
        ("uncovered", "total", "uncovered_share"),
    ):
        result.loc[:, share] = result.apply(
            lambda row: (
                share_value(row[count], row[total])
                if row[total]
                else decimal_value(0, 0)
            ),
            axis=1,
        )
    result.loc[:, "project_group"] = result["project"].map(get_project_type)
    result.loc[:, "project"] = result["project"].map(
        lambda value: ref_for_csv("dataset", value)
    )
    columns = [
        ColumnSpec("Project", "project", ValueKind.ENTITY),
        ColumnSpec(
            "Test Methods",
            "included_tests",
            kind=ValueKind.COUNT,
            align="r",
            share_source="test_inclusion_share",
            group_header="Included",
        ),
        ColumnSpec(
            "Impl. Classes",
            "included_classes",
            kind=ValueKind.COUNT,
            align="r",
            share_source="class_inclusion_share",
            # Keep the two singleton ``Included`` cells from being merged into
            # one span: the thesis distinguishes the two measures in this row.
            group_header="Included ",
        ),
        ColumnSpec("Total", "total", ValueKind.COUNT, "r", group_header="Mutants"),
        ColumnSpec(
            "Covered",
            "covered",
            ValueKind.COUNT,
            "r",
            group_header="Mutants",
            share_source="covered_share",
        ),
        ColumnSpec(
            "Uncovered",
            "uncovered",
            ValueKind.COUNT,
            "r",
            group_header="Mutants",
            share_source="uncovered_share",
        ),
    ]
    return Table(
        "tab-mutants-per-project",
        result,
        columns,
        "Number of total, covered, and uncovered mutants in included classes per project.",
        "tab:mutants-per-project",
        group_by="project_group",
        short_caption="Included tests and classes with covered and uncovered mutants per project",
        body_style="",
        float_spec="H",
        group_header_align="r",
        provenance=capture(compute_project_mutation_coverage),
    )


def _mutator_table(df: pd.DataFrame) -> Table:
    result = df.copy()
    result.loc[:, "mutator"] = (
        result["mutator"]
        .astype(str)
        .str.replace("Mutator", "", regex=False)
        .str.strip()
    )
    result.loc[:, "mutator"] = result["mutator"].replace(
        {
            "RemoveConditional_ORDER_ELSE": "RemoveConditionalOrderElse",
            "RemoveConditional_EQUAL_ELSE": "RemoveConditionalEqualElse",
        }
    )
    for column in ("detected_diff_naive_200_tries", "detected_diff_improved_200_tries"):
        if column not in result:
            result.loc[:, column] = 0
    wanted = [
        "mutator",
        "total_mutants",
        "percent",
        "INITIAL",
        "NAIVE_200_TRIES",
        "detected_diff_naive_200_tries",
        "IMPROVED_200_TRIES",
        "detected_diff_improved_200_tries",
    ]
    for column in wanted:
        if column not in result:
            result.loc[:, column] = 0
    numeric_columns = [
        column for column in wanted if column not in {"mutator", "total_mutants"}
    ]
    for column in numeric_columns:
        result = result.assign(
            **{column: result[column].map(lambda value: decimal_value(value, 2))}
        )
    result = result[wanted]
    columns = [
        ColumnSpec("Mutator", "mutator"),
        ColumnSpec("Total", "total_mutants", ValueKind.COUNT, "r"),
        ColumnSpec("Total %", "percent", ValueKind.DECIMAL, "r"),
        ColumnSpec(
            "{entity.variant.initial}",
            "INITIAL",
            ValueKind.DECIMAL,
            "c",
            group_header="Detected %",
        ),
        ColumnSpec(
            "{entity.variant.naive_c}",
            "NAIVE_200_TRIES",
            ValueKind.DECIMAL,
            "r",
            group_header="Detected %",
        ),
        ColumnSpec(
            "{entity.variant.naive_c}",
            "detected_diff_naive_200_tries",
            kind=ValueKind.DELTA,
            align="r",
            group_header="Detected %",
            zero_is_absent=True,
        ),
        ColumnSpec(
            "{entity.variant.improved_c}",
            "IMPROVED_200_TRIES",
            ValueKind.DECIMAL,
            "r",
            group_header="Detected %",
        ),
        ColumnSpec(
            "{entity.variant.improved_c}",
            "detected_diff_improved_200_tries",
            kind=ValueKind.DELTA,
            align="r",
            group_header="Detected %",
            zero_is_absent=True,
        ),
    ]
    return Table(
        "tab-detections-per-mutator",
        result,
        columns,
        "Number of mutants and percentage of detections per mutator in {entity.dataset.eqbench_es} and {entity.dataset.commons} projects.",
        "tab:detections-per-mutator",
        short_caption="Mutants and detections by mutator and dataset",
        body_style="\\tabstyle",
        full_width=True,
        group_header_align="c",
        merge_equal_headers=True,
        provenance=capture(compute_mutator_statistics),
    )


def _figure(data: pd.DataFrame) -> Figure:
    def build(ax) -> None:
        if data.empty:
            ax.set_title("Mutation detection comparison")
            return
        frame = data.copy()
        frame.loc[:, "base_project"] = frame["project_name"].map(
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


_PROJECT_IDENTITIES_SQL = """
SELECT p.id AS project_id, project_name(p.id) AS project
FROM project p
JOIN v_projects_successes ps ON ps.project_id = p.id
WHERE p.use_test_generalization
"""


def _source_class_counts(conn: Connection, facts_path: Path) -> pd.DataFrame:
    projects = read_sql(conn, _PROJECT_IDENTITIES_SQL)
    facts = project_sources.frame(facts_path).loc[:, ["project", "main_classes"]]
    merged = projects.merge(facts, on="project", how="left", validate="one_to_one")
    missing = merged.loc[merged["main_classes"].isna(), "project"].astype(str).tolist()
    if missing:
        raise ValueError(f"project-source facts lack controlled projects: {missing}")
    return merged.loc[:, ["project_id", "main_classes"]].rename(
        columns={"main_classes": "total_classes"}
    )


def _headline_effectiveness_values(frame: pd.DataFrame) -> dict[str, float]:
    """Validate the published RQ1 matrix and return its headline values."""
    identity_columns = ["project_name", "variant"]
    duplicate = frame.duplicated(identity_columns, keep=False)
    if duplicate.any():
        identities = sorted(
            tuple(row)
            for row in frame.loc[duplicate, identity_columns].itertuples(
                index=False, name=None
            )
        )
        raise ValueError(f"RQ1 effectiveness rows are duplicated: {identities}")

    expected = {
        (project, variant)
        for projects in _EFFECTIVENESS_COHORTS.values()
        for project in projects
        for variant in VARIANTS
    }
    observed = set(frame.loc[:, identity_columns].itertuples(index=False, name=None))
    missing = sorted(expected - observed)
    unexpected = sorted(observed - expected)
    if missing or unexpected:
        raise ValueError(
            "RQ1 effectiveness matrix differs from the declared cohorts: "
            f"missing={missing}, unexpected={unexpected}"
        )

    required_values = frame.loc[:, ["detected_of_covered_pct", "absolute_improvement"]]
    if required_values.isna().any().any():
        identities = frame.loc[
            required_values.isna().any(axis=1), identity_columns
        ].itertuples(index=False, name=None)
        raise ValueError(
            f"RQ1 effectiveness rows contain missing values: {sorted(identities)}"
        )

    values: dict[str, float] = {}
    for cohort, projects in _EFFECTIVENESS_COHORTS.items():
        generalized = frame[
            frame["project_name"].isin(projects) & frame["variant"].ne("INITIAL")
        ]
        values[f"effectiveness.{cohort}.mutation_improvement_min_pp"] = round(
            float(generalized["absolute_improvement"].min()), 2
        )
        values[f"effectiveness.{cohort}.mutation_improvement_max_pp"] = round(
            float(generalized["absolute_improvement"].max()), 2
        )

    developer_baseline = frame[
        frame["project_name"].eq("commons-utils") & frame["variant"].eq("INITIAL")
    ]
    values["effectiveness.commons_developer.baseline_mutation_score_pct"] = round(
        float(developer_baseline["detected_of_covered_pct"].iloc[0]), 2
    )
    return values


def _effectiveness_metrics(frame: pd.DataFrame, provenance: Provenance) -> list[Metric]:
    values = _headline_effectiveness_values(frame)
    metrics: list[Metric] = []
    for key in sorted(_EFFECTIVENESS_RANGE_KEYS):
        metrics.append(
            Metric(
                key,
                values[key],
                fmt="decimal2",
                provenance=provenance,
                kind=ValueKind.PERCENT_DELTA,
                population=MetricPopulation(key, "Mutant", "controlled"),
            )
        )
    baseline_key = "effectiveness.commons_developer.baseline_mutation_score_pct"
    metrics.append(
        Metric(
            baseline_key,
            values[baseline_key],
            fmt="percent2",
            provenance=provenance,
            kind=ValueKind.PERCENT,
            population=MetricPopulation(baseline_key, "Mutant", "controlled"),
        )
    )
    return metrics


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("controlled")
    facts_path = context.file("project-source-facts")
    if facts_path is None:
        raise AssertionError("required project-source facts resolved as absent")
    coverage = compute_project_mutation_coverage(
        get_mutation_coverage_data(conn), _source_class_counts(conn, facts_path)
    )
    detection = compute_detection_improvements(
        get_mutation_results_by_project_variant(conn)
    )
    mutator = compute_mutator_statistics(
        get_mutation_results_by_mutator(conn, list(VARIANTS)),
        get_project_mutator_data(conn),
    )
    tables = [_coverage_table(coverage), _mutator_table(mutator)]
    effectiveness_provenance = capture(
        get_mutation_results_by_project_variant,
        query=MUTATION_RESULTS_BY_PROJECT_VARIANT_SQL,
    )
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
    metrics.extend(_effectiveness_metrics(detection, effectiveness_provenance))
    section = Section(
        "Mutation score",
        [
            tables[0],
            _figure(detection),
            tables[1],
        ],
    )
    return RQReport("rq1", "RQ1 - Mutation-score improvement", [section], metrics)


register(
    "rq1",
    ReportSpec(
        build,
        (
            CorpusInputSpec("controlled", "controlled", REQUIRES),
            FileInputSpec(
                "project-source-facts",
                "analysis/data/report-inputs/project-source-facts.json",
            ),
        ),
    ),
)
