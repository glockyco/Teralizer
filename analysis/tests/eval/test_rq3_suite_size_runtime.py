import pandas as pd
from matplotlib import pyplot as plt
from sqlalchemy import create_engine

from teralizer.eval.reports.rq3_suite_size_runtime import (
    _effects,
    _effects_table,
    _overhead_figure,
)


def test_rq3_effects_preserve_thesis_test_counts():
    engine = create_engine("sqlite://")
    frame = pd.DataFrame(
        {
            "project_name": ["eqbench-es-1s"],
            "a_variant": ["ORIGINAL"],
            "b_variant": ["NAIVE_200_TRIES"],
            "tests_before": [4718],
            "added_tests": [177],
            "removed_tests": [177],
            "tests_after": [4718],
            "tests_delta": [0],
            "tests_delta_pct": [0.0],
        }
    )
    frame.to_sql("mv_generalization_effects", engine, index=False)
    with engine.connect() as conn:
        result = _effects(conn, "test")
    assert result.loc[0, "tests_before"] == 4718
    assert result.loc[0, "added_tests"] == 177
    table = _effects_table("tests_per_project", result, "test", "tab:tests-per-project")
    assert [column.source for column in table.columns] == [
        "display_project",
        "b_variant",
        "tests_before",
        "added_tests",
        "removed_tests",
        "tests_after",
        "delta_display",
        "delta_pct_display",
    ]
    assert table.df.loc[0, "display_project"] == "eqbench-es-1s"
    assert table.df.loc[0, "delta_display"] == "+0"
    assert table.df.loc[0, "delta_pct_display"] == "+0.0%"


def test_rq3_runtime_effects_use_singular_runtime_columns():
    engine = create_engine("sqlite://")
    frame = pd.DataFrame(
        {
            "project_name": ["eqbench-es-1s"],
            "a_variant": ["ORIGINAL"],
            "b_variant": ["NAIVE_200_TRIES"],
            "runtime_before": [17.44],
            "added_runtime": [100.94],
            "removed_runtime": [0.74],
            "runtime_after": [117.65],
            "runtime_delta": [100.20],
            "runtime_delta_pct": [574.5],
        }
    )
    frame.to_sql("mv_generalization_effects", engine, index=False)
    with engine.connect() as conn:
        result = _effects(conn, "runtime")
    table = _effects_table(
        "runtime_per_project", result, "runtime", "tab:runtime-per-project"
    )
    assert [column.source for column in table.columns] == [
        "display_project",
        "b_variant",
        "runtime_before",
        "added_runtime",
        "removed_runtime",
        "runtime_after",
        "delta_display",
        "delta_pct_display",
    ]
    assert table.df.loc[0, "delta_display"] == "+100.20"
    assert table.df.loc[0, "delta_pct_display"] == "+574.5%"
    data = pd.DataFrame(
        {
            "variant": [
                "BASELINE",
                "NAIVE_10_TRIES",
                "NAIVE_50_TRIES",
                "NAIVE_200_TRIES",
                "IMPROVED_10_TRIES",
                "IMPROVED_50_TRIES",
                "IMPROVED_200_TRIES",
            ],
            "mean_runtime_diff_ms": [
                149.56,
                286.13,
                348.56,
                1136.21,
                189.17,
                246.85,
                395.26,
            ],
            "mean_runtime_diff_per_try_ms": [
                149.56,
                28.61,
                6.97,
                5.68,
                18.92,
                4.94,
                1.98,
            ],
        }
    )
    figure = _overhead_figure(data)
    fig, ax = plt.subplots()
    try:
        figure.build(ax)
        assert len(fig.axes) == 2
        assert len(fig.axes[0].patches) == 7
        assert len(fig.axes[1].patches) == 7
        assert fig.axes[0].get_title() == "Mean Runtime Difference Per Test"
        assert fig.axes[1].get_title() == "Mean Runtime Difference Per Try"
    finally:
        plt.close(fig)
