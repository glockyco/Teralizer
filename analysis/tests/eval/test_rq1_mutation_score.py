from decimal import Decimal

import pandas as pd
from matplotlib import pyplot as plt

from teralizer.eval.entities import ref
from teralizer.eval.model import ValueKind
from teralizer.eval.render.latex import render_table
from teralizer.eval.reports.rq1_mutation_score import (
    _coverage_table,
    _figure,
    _mutator_table,
)


def test_rq1_coverage_table_calculates_inclusion_percentages():
    table = _coverage_table(
        pd.DataFrame(
            {
                "project": ["eqbench-es-default-1s"],
                "included_tests": [4],
                "total_tests": [8],
                "included_classes": [3],
                "total_classes": [6],
                "total": [10],
                "covered": [7],
                "uncovered": [3],
            }
        )
    )
    row = table.df.iloc[0]
    assert row["included_tests"] == 4
    assert row["test_inclusion_share"] == Decimal("0.5")
    assert row["included_classes"] == 3
    assert row["class_inclusion_share"] == Decimal("0.5")
    assert row["covered"] == 7
    assert row["covered_share"] == Decimal("0.7")
    assert row["uncovered"] == 3
    assert row["uncovered_share"] == Decimal("0.3")
    assert table.key == "tab-mutants-per-project"
    assert table.label == "tab:mutants-per-project"
    assert table.short_caption == (
        "Included tests and classes with covered and uncovered mutants per project"
    )
    assert table.body_style == ""
    assert table.float_spec == "H"
    assert table.group_header_align == "r"
    assert table.group_by == "project_group"
    assert row["project_group"] == "eqbench"
    assert row["project"] == ref("dataset.eqbench_a")
    assert table.columns[0].kind is ValueKind.ENTITY
    assert [column.header for column in table.columns] == [
        "Project",
        "Test Methods",
        "Impl. Classes",
        "Total",
        "Covered",
        "Uncovered",
    ]


def test_rq1_coverage_table_separates_only_dataset_families():
    projects = [
        "eqbench-es-default-1s",
        "eqbench-es-default-10s",
        "commons-utils-es-default-1s",
        "commons-utils",
    ]
    size = len(projects)
    table = _coverage_table(
        pd.DataFrame(
            {
                "project": projects,
                "included_tests": [4] * size,
                "total_tests": [8] * size,
                "included_classes": [3] * size,
                "total_classes": [6] * size,
                "total": [10] * size,
                "covered": [7] * size,
                "uncovered": [3] * size,
            }
        )
    )

    tex = render_table(table)

    assert tex.splitlines().count("  \\midrule") == 3
    assert r"\DatasetEqBenchA{}" in tex
    assert r"\DatasetCommonsA{}" in tex
    assert r"\DatasetCommonsDev{}" in tex


def test_rq1_figure_matches_notebook_grid():
    data = pd.DataFrame(
        {
            "project_name": ["eqbench-es-1s"] * 3,
            "variant": ["INITIAL", "NAIVE_10_TRIES", "IMPROVED_10_TRIES"],
            "detected_of_covered_pct": [48.1, 50.67, 49.46],
            "absolute_improvement": [0.0, 2.57, 1.36],
            "relative_improvement": [0.0, 5.34, 2.83],
        }
    )
    figure = _figure(data)
    fig, ax = plt.subplots()
    try:
        figure.build(ax)
        assert len(fig.axes) == 2
        assert len(fig.axes[0].patches) == 3
        assert fig.axes[0].get_ylabel() == "Detected (%)"
        assert fig.axes[1].get_ylabel() == "Improvement (%)"
    finally:
        plt.close(fig)


def test_rq1_mutator_table_keeps_missing_variant_columns_renderable():
    table = _mutator_table(
        pd.DataFrame(
            {
                "mutator": ["XMutator"],
                "total_mutants": [2],
                "percent": [100.0],
                "min_percent": [100.0],
                "max_percent": [100.0],
                "INITIAL": [50.0],
            }
        )
    )
    assert table.df.loc[0, "mutator"] == "X"
    assert table.df.loc[0, "NAIVE_200_TRIES"] == 0
    assert table.df.loc[0, "IMPROVED_200_TRIES"] == 0
    assert table.key == "tab-detections-per-mutator"
    assert table.short_caption == "Mutants and detections by mutator and dataset"
    assert table.body_style == r"\tabstyle"
    assert table.full_width
    assert table.group_header_align == "c"
    assert [column.header for column in table.columns] == [
        "Mutator",
        "Total",
        "Total %",
        "{entity.variant.initial}",
        "{entity.variant.naive_c}",
        "{entity.variant.naive_c}",
        "{entity.variant.improved_c}",
        "{entity.variant.improved_c}",
    ]
    assert [column.group_header for column in table.columns[3:]] == ["Detected %"] * 5
    assert table.columns[5].zero_is_absent
    assert table.columns[7].zero_is_absent
