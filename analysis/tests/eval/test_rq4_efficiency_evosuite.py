import pandas as pd
from matplotlib import pyplot as plt
from matplotlib.patches import Rectangle

from teralizer.eval.reports.rq4_efficiency_evosuite import (
    _efficiency_figure,
    _runtime_stage_figure,
)


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
