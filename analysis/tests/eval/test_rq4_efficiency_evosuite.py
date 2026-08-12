import pandas as pd
from matplotlib import pyplot as plt
from matplotlib.patches import Rectangle

from teralizer.eval.render.latex import render_table
from teralizer.eval.reports.rq4_efficiency_evosuite import (
    _efficiency_figure,
    _pareto_table,
    _runtime_stage_figure,
)


def test_rq4_pareto_tables_match_thesis_keys_and_nonfloating_layout():
    data = pd.DataFrame(
        {
            "project_name": ["eqbench", "eqbench", "commons-utils"],
            "evosuite_budget": ["1s", "10s", "1s"],
            "teralizer_variant": ["ES_ONLY", "IMPROVED_50_TRIES", "ES_ONLY"],
            "type": ["ES_ONLY", "IMPROVED", "ES_ONLY"],
            "runtime_seconds": [26479.0, 37457.0, 4649.0],
            "detection_rate": [48.1, 51.4, 56.8],
            "is_pareto_optimal": [True, True, True],
        }
    )

    eqbench = _pareto_table(data, "eqbench")
    commons = _pareto_table(data, "commons-utils")

    assert [table.key for table in (eqbench, commons)] == [
        "tab-pareto-eqbench",
        "tab-pareto-commons",
    ]
    assert all(not table.floating for table in (eqbench, commons))
    assert (
        eqbench.body_style
        == "\\tabstyle[\\footnotesize]\n\\setlength{\\tabcolsep}{3pt}"
    )
    assert commons.body_style == eqbench.body_style

    eqbench_tex = render_table(eqbench)
    commons_tex = render_table(commons)
    assert "\\begin{table}" not in eqbench_tex
    assert "\\begin{table}" not in commons_tex
    assert (
        "\\captionof{table}[\\ToolEvoSuite{} and \\ToolTeralizer{} Pareto points"
        in eqbench_tex
    )
    assert "\\label{tab:pareto-eqbench}" in eqbench_tex
    assert "1 & 1s & - & 48.1 & 26,479" in eqbench_tex
    assert "2 & 10s & IMPROVED$_{50}$ & 51.4 & 37,457" in eqbench_tex
    assert "\\label{tab:pareto-commons}" in commons_tex


def test_rq4_efficiency_figure_matches_notebook_two_project_layout():
    data = pd.DataFrame(
        {
            "project_name": ["eqbench", "eqbench", "commons-utils", "commons-utils"],
            "type": ["ES_ONLY", "IMPROVED", "ES_ONLY", "NAIVE"],
            "runtime_seconds": [100.0, 150.0, 50.0, 80.0],
            "detection_rate": [50.0, 52.0, 57.0, 58.0],
            "is_pareto_optimal": [True, True, True, True],
        }
    )
    figure = _efficiency_figure(data)
    fig, ax = plt.subplots()
    try:
        figure.build(ax)
        assert len(fig.axes) == 2
        assert [axis.get_title() for axis in fig.axes] == [
            "Project: eqbench",
            "Project: commons-utils",
        ]
        assert all(len(axis.lines) == 1 for axis in fig.axes)
    finally:
        plt.close(fig)


def test_rq4_runtime_figure_matches_notebook_order_and_legend():
    rows = []
    projects = ["commons-utils-es-default-1s", "eqbench-es-default-1s"]
    variants = [
        ("SHARED", 0),
        ("BASELINE", 1),
        ("NAIVE_10_TRIES", 2),
        ("NAIVE_50_TRIES", 3),
        ("NAIVE_200_TRIES", 4),
        ("IMPROVED_10_TRIES", 5),
        ("IMPROVED_50_TRIES", 6),
        ("IMPROVED_200_TRIES", 7),
    ]
    for project in projects:
        for stage in ("Stage 1 + 2", "Stage 3", "Stage 4", "Stage 5"):
            for variant, order in variants:
                if stage in {"Stage 1 + 2", "Stage 3"} and variant != "SHARED":
                    continue
                rows.append(
                    {
                        "project_name": project,
                        "stage_group": stage,
                        "variant": variant,
                        "variant_order": order,
                        "total_runtime": 10.0 + order,
                    }
                )
    figure = _runtime_stage_figure(pd.DataFrame(rows))
    fig, ax = plt.subplots()
    try:
        figure.build(ax)
        assert [axis.get_title() for axis in fig.axes] == [
            "eqbench-es-1s",
            "commons-utils-es-1s",
        ]
        assert len(fig.legends) == 1
        assert len(fig.axes[0].patches) == 16
        assert len(fig.axes[0].lines) == 12
        assert fig.axes[0].lines[0].get_ydata()[0] > 1
        assert all(
            isinstance(handle, Rectangle) for handle in fig.legends[0].legend_handles
        )
        assert [text.get_text() for text in fig.legends[0].texts] == [
            "SHARED",
            "BASELINE",
            "NAIVE$_{10/50/200}$",
            "IMPROVED$_{10/50/200}$",
        ]
    finally:
        plt.close(fig)
