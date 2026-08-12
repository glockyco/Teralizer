"""RQ3 test-suite size and runtime report."""

from __future__ import annotations

import re
import numpy as np
import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required
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
from teralizer.exports import get_project_type
from teralizer.rq2_test_suite_effects import (
    compute_line_count_change_statistics,
    compute_runtime_change_statistics,
    compute_test_suite_change_statistics,
    compute_test_vs_generalization_runtime_statistics,
    get_line_count_changes_by_project_variant,
    get_runtime_changes_by_project_variant,
    get_test_count_changes_by_project_variant,
    get_test_vs_generalization_runtime_comparison,
)
from teralizer.plotting import (
    FIGURE_CONFIG,
    calculate_label_offset,
    get_font_size,
    get_variant_color,
)

VARIANTS = ("NAIVE_200_TRIES", "IMPROVED_200_TRIES")
REQUIRES = (
    Required("project", "table", ("id", "use_test_generalization")),
    Required("v_projects_successes", "view", ("project_id",)),
    Required(
        "mv_generalization_effects", "view", ("project_name", "a_variant", "b_variant")
    ),
    Required(
        "mv_runtime_comparison_test_vs_generalization",
        "view",
        (
            "variant",
            "t_runtime",
            "g_runtime",
            "runtime_diff",
            "tries",
            "runtime_diff_per_try",
        ),
    ),
)


def _effects(conn: Connection, unit: str) -> pd.DataFrame:
    if unit == "test":
        result = compute_test_suite_change_statistics(
            get_test_count_changes_by_project_variant(conn, list(VARIANTS))
        )
    elif unit == "line":
        result = compute_line_count_change_statistics(
            get_line_count_changes_by_project_variant(conn, list(VARIANTS))
        )
    elif unit == "runtime":
        result = compute_runtime_change_statistics(
            get_runtime_changes_by_project_variant(conn, list(VARIANTS))
        )
    else:
        raise ValueError(f"unknown effects unit: {unit}")

    # The shared computation establishes corpus order; stabilize each project's
    # rows in the thesis's Naive-then-Improved order without replacing that order
    # with lexical project names.
    project_order, _ = pd.factorize(result["project_name"], sort=False)
    variant_order = (
        result["b_variant"]
        .map({variant: index for index, variant in enumerate(VARIANTS)})
        .fillna(len(VARIANTS))
    )
    return (
        result.assign(_project_order=project_order, _variant_order=variant_order)
        .sort_values(["_project_order", "_variant_order"], kind="stable")
        .drop(columns=["_project_order", "_variant_order"])
        .reset_index(drop=True)
    )


def _runtime_overhead(conn: Connection) -> pd.DataFrame:
    return compute_test_vs_generalization_runtime_statistics(
        get_test_vs_generalization_runtime_comparison(conn)
    )


def _effects_table(key: str, df: pd.DataFrame, unit: str, label: str) -> Table:
    prefix = "runtime" if unit == "runtime" else f"{unit}s"
    result = df.copy()
    delta = f"{prefix}_delta"
    delta_pct = f"{prefix}_delta_pct"
    result["delta_display"] = result[delta].map(
        lambda value: f"{float(value):+.2f}"
        if unit == "runtime"
        else f"{int(value):+d}"
    )
    result["delta_pct_display"] = result[delta_pct].map(
        lambda value: f"{float(value):+.1f}%"
    )
    result["display_project"] = (
        result["project_name"]
        .astype(str)
        .str.replace("-default", "", regex=False)
        .replace({"commons-utils": "commons-utils-dev"})
    )
    # The legacy tables separate the EqBench, Commons-ES, and Commons-dev rows.
    result["project_group"] = result["project_name"].map(get_project_type)
    numeric_fmt = "count" if unit in {"test", "line"} else "float2"
    group_header = {
        "test": "Tests",
        "line": "Lines",
        "runtime": "Runtime (in seconds)",
    }[unit]
    columns = [
        ColumnSpec("Project", "display_project"),
        ColumnSpec("Variant", "b_variant"),
        ColumnSpec(
            "Before", f"{prefix}_before", numeric_fmt, "r", group_header=group_header
        ),
        ColumnSpec(
            "Added", f"added_{prefix}", numeric_fmt, "r", group_header=group_header
        ),
        ColumnSpec(
            "Removed", f"removed_{prefix}", numeric_fmt, "r", group_header=group_header
        ),
        ColumnSpec(
            "After", f"{prefix}_after", numeric_fmt, "r", group_header=group_header
        ),
        ColumnSpec("Delta", "delta_display", align="r", group_header=group_header),
        ColumnSpec(
            "Delta \\%", "delta_pct_display", align="r", group_header=group_header
        ),
    ]
    if unit == "runtime":
        caption = "Test suite runtime before and after generalization, with changes, per project."
        short_caption = (
            "Test-suite runtime additions, removals, and deltas after generalization"
        )
        body_style = "\\tabstyle\n\\setlength{\\tabcolsep}{3pt}"
        float_spec = "tbp"
    elif unit == "test":
        caption = "Number of tests before and after generalization, with changes, per project."
        short_caption = (
            "Test count additions, removals, and deltas after generalization"
        )
        body_style = "\\tabstyle"
        float_spec = None
    else:
        caption = "Number of test lines before and after generalization, with changes, per project."
        short_caption = "Test-line additions, removals, and deltas after generalization"
        body_style = "\\tabstyle"
        float_spec = None
    return Table(
        key,
        result,
        columns,
        caption,
        label,
        group_by="project_group",
        short_caption=short_caption,
        body_style=body_style,
        float_spec=float_spec,
        full_width=True,
        provenance=capture(_effects),
    )


def _overhead_table(df: pd.DataFrame) -> Table:
    columns = [
        ColumnSpec("Variant", "variant"),
        ColumnSpec("Test mean (ms)", "mean_t_runtime_ms", "float2"),
        ColumnSpec("Generalized mean (ms)", "mean_g_runtime_ms", "float2"),
        ColumnSpec("Difference (ms)", "mean_runtime_diff_ms", "float2"),
        ColumnSpec("Ratio", "ratio_of_mean_runtimes", "float2"),
        ColumnSpec("Tries", "tries", "count"),
        ColumnSpec("Difference / try (ms)", "mean_runtime_diff_per_try_ms", "float2"),
    ]
    return Table(
        "test_runtime_differences",
        df,
        columns,
        "Runtime overhead of generalized tests per test and per try.",
        "fig:test-runtime-differences",
        provenance=capture(_runtime_overhead),
    )


def _overhead_figure(data: pd.DataFrame) -> Figure:
    def build(ax) -> None:
        if data.empty:
            return
        frame = data.reset_index(drop=True)
        variants = frame["variant"].astype(str).tolist()
        x_positions = np.arange(len(variants))
        colors = [get_variant_color(variant) for variant in variants]
        figure = ax.figure
        figure.clear()
        figure.set_size_inches(
            FIGURE_CONFIG["width_comparison"], FIGURE_CONFIG["comparison_height"]
        )
        ax1, ax2 = figure.subplots(1, 2)

        def format_variant_label(value: str) -> str:
            match = re.match(r"([A-Z]+)_([0-9]+)_TRIES", value)
            return f"{match.group(1)}$_{{{match.group(2)}}}$" if match else value

        for axis, column, title in (
            (ax1, "mean_runtime_diff_ms", "Mean Runtime Difference Per Test"),
            (ax2, "mean_runtime_diff_per_try_ms", "Mean Runtime Difference Per Try"),
        ):
            values = frame[column].astype(float).tolist()
            bars = axis.bar(x_positions, values, color=colors)
            low, high = axis.get_ylim()
            offset = calculate_label_offset(
                low, high, FIGURE_CONFIG["label_offset_pct"]
            )
            for bar in bars:
                height = bar.get_height()
                axis.text(
                    bar.get_x() + bar.get_width() / 2,
                    height + (offset if height >= 0 else -offset),
                    f"{height:.1f}",
                    ha="center",
                    va="bottom" if height >= 0 else "top",
                    fontsize=get_font_size("normal"),
                )
            axis.set_title(title)
            axis.set_ylabel("Runtime Difference (ms)")
            axis.set_xticks(x_positions)
            axis.set_xticklabels(
                [format_variant_label(value) for value in variants],
                rotation=45,
                ha="right",
            )
            y_range = high - low
            bottom = 0 if min(values) >= 0 else low - abs(y_range * 0.05)
            axis.set_ylim(bottom, high + abs(y_range * 0.15))
        figure.tight_layout()

    return Figure(
        "test_runtime_differences",
        build,
        "Runtime comparison between original and generalized tests.",
        "fig:test-runtime-differences",
        data=data,
        provenance=capture(_runtime_overhead),
    )


def build(conn: Connection) -> RQReport:
    tests = _effects(conn, "test")
    lines = _effects(conn, "line")
    runtimes = _effects(conn, "runtime")
    overhead = _runtime_overhead(conn)
    tables = [
        _effects_table("tab-tests-per-project", tests, "test", "tab:tests-per-project"),
        _effects_table("tab-lines-per-project", lines, "line", "tab:lines-per-project"),
        _effects_table(
            "tab-runtime-per-project", runtimes, "runtime", "tab:runtime-per-project"
        ),
        _overhead_table(overhead),
    ]
    metrics = [
        Metric("rq3.project_variant_rows", len(tests), "count", capture(_effects))
    ]
    section = Section(
        "Test-suite size and runtime",
        [
            *tables,
            _overhead_figure(overhead),
        ],
    )
    return RQReport(
        "rq3", "RQ3 - Test-suite size and runtime", "postgres_dev", [section], metrics
    )


register("rq3", ReportSpec(build, "postgres_dev", "old", REQUIRES))
