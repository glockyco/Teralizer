"""RQ3 test-suite size and runtime report."""

from __future__ import annotations

import re
from typing import cast

import numpy as np
import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required, read_sql
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
    columns = {
        "test": (
            "project_name",
            "a_variant",
            "b_variant",
            "tests_before",
            "added_tests",
            "removed_tests",
            "tests_after",
            "tests_delta",
            "tests_delta_pct",
        ),
        "line": (
            "project_name",
            "a_variant",
            "b_variant",
            "lines_before",
            "added_lines",
            "removed_lines",
            "lines_after",
            "lines_delta",
            "lines_delta_pct",
        ),
        "runtime": (
            "project_name",
            "a_variant",
            "b_variant",
            "runtime_before",
            "added_runtime",
            "removed_runtime",
            "runtime_after",
            "runtime_delta",
            "runtime_delta_pct",
        ),
    }[unit]
    raw = read_sql(
        conn,
        "SELECT * FROM mv_generalization_effects WHERE a_variant = 'ORIGINAL' AND b_variant IN ('NAIVE_200_TRIES', 'IMPROVED_200_TRIES')",
    )
    result = cast(pd.DataFrame, raw[list(columns)].copy())
    for column in columns[3:]:
        result[column] = cast(
            pd.Series, pd.to_numeric(result[column], errors="coerce")
        ).fillna(0)
    return result.sort_values(["project_name", "b_variant"]).reset_index(drop=True)


def _runtime_overhead(conn: Connection) -> pd.DataFrame:
    query = """
        SELECT variant,
               avg(t_runtime * 1000) AS mean_t_runtime_ms,
               avg(g_runtime * 1000) AS mean_g_runtime_ms,
               avg(runtime_diff * 1000) AS mean_runtime_diff_ms,
               avg(g_runtime) / NULLIF(avg(t_runtime), 0) AS ratio_of_mean_runtimes,
               min(tries) AS tries,
               avg(runtime_diff_per_try * 1000) AS mean_runtime_diff_per_try_ms
        FROM mv_runtime_comparison_test_vs_generalization
        GROUP BY variant, variant_order
        ORDER BY variant_order
    """
    return read_sql(conn, query)


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
    numeric_fmt = "count" if unit in {"test", "line"} else "float2"
    columns = [
        ColumnSpec("Project", "display_project"),
        ColumnSpec("Variant", "b_variant"),
        ColumnSpec("Before", f"{prefix}_before", numeric_fmt),
        ColumnSpec("Added", f"added_{prefix}", numeric_fmt),
        ColumnSpec("Removed", f"removed_{prefix}", numeric_fmt),
        ColumnSpec("After", f"{prefix}_after", numeric_fmt),
        ColumnSpec("Delta", "delta_display"),
        ColumnSpec("Delta %", "delta_pct_display"),
    ]
    return Table(
        key,
        result,
        columns,
        f"{unit.title()} changes per project and variant.",
        label,
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
        _effects_table("tests_per_project", tests, "test", "tab:tests-per-project"),
        _effects_table("lines_per_project", lines, "line", "tab:lines-per-project"),
        _effects_table(
            "runtime_per_project", runtimes, "runtime", "tab:runtime-per-project"
        ),
        _overhead_table(overhead),
    ]
    metrics = [
        Metric("rq3.project_variant_rows", len(tests), "count", capture(_effects))
    ]
    section = Section(
        "Test-suite size and runtime",
        [
            Prose(
                "Generalization changes {rq3.project_variant_rows} project-variant observations."
            ),
            *tables,
            _overhead_figure(overhead),
        ],
    )
    return RQReport(
        "rq3", "RQ3 - Test-suite size and runtime", "postgres_dev", [section], metrics
    )


register("rq3", ReportSpec(build, "postgres_dev", "old", REQUIRES))
