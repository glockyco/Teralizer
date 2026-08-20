"""RQ4 EvoSuite-versus-generalization efficiency report."""

from __future__ import annotations

import numpy as np
import pandas as pd
from matplotlib.lines import Line2D
from matplotlib.patches import Rectangle

from teralizer.eval.data import Required
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import (
    ColumnSpec,
    Figure,
    Metric,
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
from teralizer.plotting import FIGURE_CONFIG, get_wong_variant_colors
from teralizer.rq3_runtime_requirements import (
    compute_evosuite_phase_statistics,
    compute_pareto_efficiency_analysis,
    compute_stage_runtime_breakdown,
    get_evosuite_runtime_analysis,
    get_evosuite_vs_teralizer_efficiency,
    get_teralizer_runtime_by_stage,
)

REQUIRES = (
    Required("project", "table", ("id",)),
    Required("v_projects_successes", "view", ("project_id",)),
    Required(
        "mv_efficiency_comparison_evosuite_vs_teralizer",
        "view",
        ("project_name", "teralizer_variant"),
    ),
    Required("mv_evosuite_runtime_pivoted", "view", ("project_id", "total")),
    Required(
        "mv_teralizer_runtime_by_stage",
        "view",
        ("project_id", "project_name", "stage_group", "variant", "total_runtime"),
    ),
)


def _pareto_table(data: pd.DataFrame, project_name: str) -> Table:
    """Build one of the side-by-side Pareto tables used by the thesis."""
    table_info = {
        "eqbench": (
            "tab-pareto-eqbench",
            r"\ToolEvoSuite{} and \ToolTeralizer{} Pareto points for \DatasetEqBench{}",
            "Pareto points for eqbench.",
            "tab:pareto-eqbench",
        ),
        "commons-utils": (
            "tab-pareto-commons",
            r"\ToolEvoSuite{} and \ToolTeralizer{} Pareto points for commons-utils",
            "Pareto points for commons-utils.",
            "tab:pareto-commons",
        ),
    }
    try:
        key, short_caption, caption, label = table_info[project_name]
    except KeyError as error:
        raise ValueError(
            f"No thesis Pareto table configured for {project_name!r}"
        ) from error

    result = (
        data[data["project_name"].eq(project_name) & data["is_pareto_optimal"].eq(True)]
        .sort_values("runtime_seconds")
        .reset_index(drop=True)
    )
    result = result.assign(
        point=np.arange(1, len(result) + 1),
        teralizer_display=result.apply(
            lambda row: _format_variant(row["teralizer_variant"], row["type"]),
            axis=1,
        ),
        detection_display=result["detection_rate"].map(lambda value: f"{value:.1f}"),
        runtime_display=result["runtime_seconds"].map(
            lambda value: f"{int(round(value)):,}"
        ),
    )
    columns = [
        ColumnSpec("Pt.", "point", "int", "r"),
        ColumnSpec("EvoSuite", "evosuite_budget", align="r"),
        ColumnSpec("Teralizer", "teralizer_display", "tex", "l"),
        ColumnSpec("Det. \\%", "detection_display", align="r"),
        ColumnSpec("Runtime (s)", "runtime_display", align="r"),
    ]
    return Table(
        key,
        result,
        columns,
        caption,
        label,
        short_caption=short_caption,
        body_style="\\tabstyle[\\footnotesize]\n\\setlength{\\tabcolsep}{3pt}",
        floating=False,
        provenance=capture(compute_pareto_efficiency_analysis),
    )


def _format_variant(variant: object, approach_type: object) -> str:
    if approach_type == "ES_ONLY":
        return "-"
    text = str(variant)
    if "_" in text:
        name, tries, suffix = text.rpartition("_")
        if suffix == "TRIES" and name.rsplit("_", 1)[-1].isdigit():
            variant_name, count = name.rsplit("_", 1)
            return f"{variant_name}$_{{{count}}}$"
    return text


def _phase_table(data: pd.DataFrame) -> Table:
    wanted = [
        "project_name",
        "search_budget",
        "total",
        "search",
        "inlining",
        "minimization",
        "coverage_analysis",
        "assertion_generation",
        "junit_check",
        "writing_tests",
    ]
    result = data.copy()
    for col in wanted:
        if col not in result:
            result[col] = 0
    result = result[wanted]
    # One EvoSuite run per row arrives here. The table reports one row per project and budget,
    # which is the same grouping the phase figure draws.
    if not result.empty:
        result = (
            result.groupby(["project_name", "search_budget"], as_index=False)[
                wanted[2:]
            ]
            .mean()
            .sort_values(["project_name", "search_budget"])
            .reset_index(drop=True)
        )
    cols = [
        ColumnSpec("Project", "project_name"),
        ColumnSpec("Budget (s)", "search_budget", "float2"),
    ]
    cols.extend(
        ColumnSpec(col.replace("_", " ").title(), col, "float2") for col in wanted[2:]
    )
    return Table(
        "evosuite_runtime_analysis",
        result,
        cols,
        "Mean EvoSuite runtime per phase, by project and search budget.",
        "tab:evosuite-runtime-analysis",
        provenance=capture(compute_evosuite_phase_statistics),
        note="Each row is the mean over the runs of one project at one search budget.",
    )


def _efficiency_figure(data: pd.DataFrame) -> Figure:
    def build(ax) -> None:
        if data.empty:
            return
        colors = get_wong_variant_colors()
        projects = [
            project
            for project in ("eqbench", "commons-utils")
            if project in set(data["project_name"])
        ]
        projects.extend(sorted(set(data["project_name"]) - set(projects)))
        figure = ax.figure
        figure.clear()
        figure.set_size_inches(
            FIGURE_CONFIG["width_scatter"], FIGURE_CONFIG["comparison_height"]
        )
        axes = figure.subplots(1, len(projects), squeeze=False)[0]
        for axis, project in zip(axes, projects):
            frame = data[data["project_name"].eq(project)].copy()
            for kind, marker, color, label in (
                ("ES_ONLY", "o", colors["INITIAL"], "EvoSuite only"),
                ("NAIVE", "x", colors["NAIVE_10_TRIES"], "EvoSuite + NAIVE"),
                ("IMPROVED", "^", colors["IMPROVED_10_TRIES"], "EvoSuite + IMPROVED"),
            ):
                points = frame[frame["type"].eq(kind)]
                axis.scatter(
                    points["runtime_seconds"],
                    points["detection_rate"],
                    marker=marker,
                    color=color,
                    alpha=0.3,
                    s=40,
                    label=label,
                )
            frontier = frame[frame["is_pareto_optimal"]].sort_values("runtime_seconds")
            axis.plot(
                frontier["runtime_seconds"],
                frontier["detection_rate"],
                "--",
                color="black",
                linewidth=1.2,
            )
            y_min = float(frame["detection_rate"].min())
            y_max = float(frame["detection_rate"].max())
            margin = 0.2 * (y_max - y_min) if y_max > y_min else 0.5
            axis.set_ylim(y_min - margin / 2, y_max + margin)
            offset = 0.025 * (y_max + margin - (y_min - margin / 2))
            for number, (_, row) in enumerate(frontier.iterrows(), start=1):
                color = (
                    colors["INITIAL"]
                    if row["type"] == "ES_ONLY"
                    else colors["NAIVE_10_TRIES"]
                    if row["type"] == "NAIVE"
                    else colors["IMPROVED_10_TRIES"]
                )
                marker = (
                    "o"
                    if row["type"] == "ES_ONLY"
                    else "x"
                    if row["type"] == "NAIVE"
                    else "^"
                )
                axis.scatter(
                    row["runtime_seconds"],
                    row["detection_rate"],
                    marker=marker,
                    color=color,
                    s=90,
                    edgecolor="black" if marker != "x" else None,
                    zorder=3,
                )
                axis.text(
                    row["runtime_seconds"],
                    row["detection_rate"] + offset,
                    str(number),
                    fontweight="bold",
                    color=color,
                    ha="center",
                    va="bottom",
                )
            axis.set_title(f"Project: {project}")
            axis.set_xlabel("Runtime (s)")
            axis.set_ylabel("Detected (%)")
            axis.ticklabel_format(style="plain", axis="x")
        handles = [
            Line2D(
                [],
                [],
                marker="o",
                color=colors["INITIAL"],
                markersize=8,
                linestyle="None",
                label="EvoSuite only",
            ),
            Line2D(
                [],
                [],
                marker="x",
                color=colors["NAIVE_10_TRIES"],
                markersize=8,
                linestyle="None",
                label="EvoSuite + NAIVE",
            ),
            Line2D(
                [],
                [],
                marker="^",
                color=colors["IMPROVED_10_TRIES"],
                markersize=8,
                linestyle="None",
                label="EvoSuite + IMPROVED",
            ),
            Line2D(
                [],
                [],
                linestyle="--",
                color="black",
                linewidth=1.2,
                label="Pareto front",
            ),
        ]
        figure.legend(handles=handles, loc="upper center", ncol=4, frameon=False)
        figure.tight_layout(rect=[0, 0, 1, 0.93])

    return Figure(
        "teralizer_efficiency",
        build,
        "Pareto fronts for EvoSuite and Teralizer variants across projects.",
        "fig:teralizer-efficiency",
        data=data,
        provenance=capture(compute_pareto_efficiency_analysis),
    )


def _runtime_stage_figure(data: pd.DataFrame) -> Figure:
    def build(ax) -> None:
        if data.empty:
            return
        groups = ["Stage 1 + 2", "Stage 3", "Stage 4", "Stage 5"]
        variant_order = (
            data.drop_duplicates("variant")
            .set_index("variant")["variant_order"]
            .to_dict()
            if "variant_order" in data
            else {}
        )
        variants = sorted(
            data["variant"].dropna().unique(),
            key=lambda value: variant_order.get(value, 999),
        )
        stage_variants = {
            "Stage 1 + 2": ["SHARED"],
            "Stage 3": ["SHARED"],
            "Stage 4": [v for v in variants if v != "SHARED"],
            "Stage 5": [v for v in variants if v != "SHARED"],
        }
        width, spacing, group_spacing = 0.3, 0.05, 0.3
        centers: dict[str, float] = {}
        extents: dict[str, tuple[float, float]] = {}
        positions: dict[tuple[str, str], float] = {}
        current = 0.0
        for group in groups:
            members = stage_variants[group]
            group_width = len(members) * width + max(0, len(members) - 1) * spacing
            centers[group] = current + group_width / 2
            extents[group] = (current, current + group_width)
            for index, variant in enumerate(members):
                positions[(group, variant)] = (
                    current + index * (width + spacing) + width / 2
                )
            current += group_width + group_spacing
        order = get_project_within_type_order()
        projects = sorted(
            data["project_name"].drop_duplicates(),
            key=lambda project: (
                get_table_group_order(project, "INITIAL"),
                order.get(project, 99),
            ),
        )
        figure = ax.figure
        figure.clear()
        figure.set_size_inches(
            FIGURE_CONFIG["width_multibar"], FIGURE_CONFIG["max_height"]
        )
        figure.subplots_adjust(hspace=FIGURE_CONFIG["subplot_hspace"])
        axes = figure.subplots(len(projects), 1, squeeze=False)[:, 0]
        colors = get_wong_variant_colors()
        scaling = {
            "eqbench-es": [
                "eqbench-es-default-1s",
                "eqbench-es-default-10s",
                "eqbench-es-default-60s",
            ],
            "commons-utils-es": [
                "commons-utils-es-default-1s",
                "commons-utils-es-default-10s",
                "commons-utils-es-default-60s",
            ],
            "commons-utils-dev": ["commons-utils"],
        }
        limits: dict[str, float] = {}
        for names in scaling.values():
            maximum = data[data["project_name"].isin(names)]["total_runtime"].max()
            for name in names:
                limits[name] = float(maximum) * 1.4
        for axis, project in zip(axes, projects):
            subset = data[data["project_name"].eq(project)]
            max_value = limits.get(project, float(subset["total_runtime"].max()) * 1.4)
            for group in groups:
                for variant in stage_variants[group]:
                    row = subset[
                        (subset["stage_group"].eq(group))
                        & (subset["variant"].eq(variant))
                    ]
                    if row.empty:
                        continue
                    value = float(row["total_runtime"].sum())
                    if value <= 0:
                        continue
                    position = positions[(group, variant)]
                    axis.bar(
                        position,
                        value,
                        width=width,
                        color=colors.get(variant, "#999999"),
                    )
                    label_y = value + max_value * 0.02
                    if group != "Stage 5":
                        marker = (
                            "o"
                            if variant == "SHARED"
                            else "s"
                            if variant == "BASELINE"
                            else "^"
                            if str(variant).startswith("NAIVE")
                            else "D"
                        )
                        marker_size = (
                            6
                            if variant in {"SHARED", "BASELINE"}
                            else 6.5
                            if str(variant).startswith("NAIVE")
                            else 5
                        )
                        axis.plot(
                            position,
                            label_y + max_value * 0.35,
                            marker=marker,
                            markersize=marker_size,
                            color=colors.get(variant, "#999999"),
                            markeredgewidth=0,
                            zorder=10,
                        )
                    if value < 10:
                        label = f"{value:.1f}"
                    elif value < 1000:
                        label = f"{value:.0f}"
                    else:
                        label = f"{value / 1000:.1f}k"
                    axis.text(
                        position,
                        value + max_value * 0.02,
                        label,
                        ha="center",
                        va="bottom",
                        fontsize=8,
                    )
            # In the gap between two groups, not midway between their centres: a
            # centre midpoint is a boundary only when both groups are the same
            # width, and Stage 3 holds one variant against Stage 4's seven, so it
            # landed inside Stage 4 and put BASELINE on the Stage 3 side.
            for group, following in zip(groups, groups[1:]):
                axis.axvline(
                    (extents[group][1] + extents[following][0]) / 2,
                    color="gray",
                    linestyle="--",
                    linewidth=0.8,
                    alpha=0.5,
                )
            axis.set_title(standardize_project_name(project))
            axis.set_ylabel("Runtime (s)")
            axis.set_xticks([centers[group] for group in groups])
            axis.set_xticklabels(groups)
            axis.set_xlim(-group_spacing / 2, current - group_spacing / 2)
            axis.set_ylim(0, max_value)
        handles = [
            Rectangle((0, 0), 1, 1, color=colors["SHARED"], label="SHARED"),
            Rectangle((0, 0), 1, 1, color=colors["BASELINE"], label="BASELINE"),
            Rectangle(
                (0, 0),
                1,
                1,
                color=colors["NAIVE_10_TRIES"],
                label="NAIVE$_{10/50/200}$",
            ),
            Rectangle(
                (0, 0),
                1,
                1,
                color=colors["IMPROVED_10_TRIES"],
                label="IMPROVED$_{10/50/200}$",
            ),
        ]
        figure.legend(handles=handles, loc="upper center", ncol=4, frameon=False)
        figure.tight_layout()
        figure.subplots_adjust(top=0.93, right=1)

    return Figure(
        "teralizer_runtimes",
        build,
        "Teralizer runtime by pipeline stage and variant.",
        "fig:teralizer-runtimes",
        data=data,
        provenance=capture(compute_stage_runtime_breakdown),
    )


def _phase_figure(data: pd.DataFrame) -> Figure:
    def build(ax) -> None:
        if data.empty:
            return
        import matplotlib.pyplot as plt

        phase_columns = [
            "search",
            "inlining",
            "minimization",
            "coverage_analysis",
            "assertion_generation",
            "junit_check",
            "writing_tests",
            "writing_statistics",
            "done",
            "finished",
        ]
        available = [column for column in phase_columns if column in data]
        means = data.groupby("search_budget")[available].mean().transpose()
        figure = ax.figure
        figure.clear()
        figure.set_size_inches(16, 4)
        axis = figure.add_subplot(1, 1, 1)
        n_budgets = len(means.columns)
        width = 0.8 / n_budgets
        positions = np.arange(len(available))
        colors = plt.cm.get_cmap("tab10")(np.arange(n_budgets) % 10)
        maximum = float(means.max().max())
        for index, (budget, color) in enumerate(zip(means.columns, colors)):
            bars = axis.bar(
                positions + (index - n_budgets / 2 + 0.5) * width,
                means[budget],
                width,
                label=f"Budget: {budget}s",
                color=color,
            )
            for bar in bars:
                height = bar.get_height()
                axis.text(
                    bar.get_x() + bar.get_width() / 2,
                    height + 1,
                    f"{height:.1f}" if height >= 1 else f"{height:.2f}",
                    ha="center",
                    va="bottom",
                    fontsize=9,
                )
        axis.set_xticks(positions)
        axis.set_xticklabels(available, rotation=45, ha="right")
        axis.set_ylabel("Mean Runtime (seconds)")
        axis.set_title("Mean EvoSuite Runtime by Phase and Search Budget")
        axis.legend(title="Search Budget (seconds)")
        axis.set_ylim(0, maximum * 1.15)
        figure.tight_layout()

    return Figure(
        "evosuite_runtime_phases",
        build,
        "Mean EvoSuite runtime by phase and search budget.",
        "fig:evosuite-runtime-phases",
        data=data,
        provenance=capture(compute_evosuite_phase_statistics),
    )


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("controlled")
    pareto = compute_pareto_efficiency_analysis(
        get_evosuite_vs_teralizer_efficiency(conn)
    )
    phases = compute_evosuite_phase_statistics(get_evosuite_runtime_analysis(conn))
    stages = compute_stage_runtime_breakdown(get_teralizer_runtime_by_stage(conn))
    tables = [
        _pareto_table(pareto, "eqbench"),
        _pareto_table(pareto, "commons-utils"),
        _phase_table(phases),
    ]
    metrics = [
        Metric(
            "rq4.pareto_points",
            len(pareto),
            "count",
            capture(compute_pareto_efficiency_analysis),
        )
    ]
    section = Section(
        "Efficiency versus EvoSuite",
        [
            tables[0],
            tables[1],
            _efficiency_figure(pareto),
            _runtime_stage_figure(stages),
            tables[2],
            _phase_figure(phases),
        ],
    )
    return RQReport("rq4", "RQ4 - Efficiency versus EvoSuite", [section], metrics)


register(
    "rq4",
    ReportSpec(build, (CorpusInputSpec("controlled", "controlled", REQUIRES),)),
)
