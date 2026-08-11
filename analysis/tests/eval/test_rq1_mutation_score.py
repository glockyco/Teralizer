import pandas as pd
from matplotlib import pyplot as plt

from teralizer.eval.reports.rq1_mutation_score import (
    _coverage_table,
    _figure,
    _mutator_table,
)


def test_rq1_coverage_table_calculates_inclusion_percentages():
    table = _coverage_table(
        pd.DataFrame(
            {
                "project": ["p"],
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
    assert row["included_tests_display"] == "4 (50.0%)"
    assert row["included_classes_display"] == "3 (50.0%)"
    assert row["covered_display"] == "7 (70.0%)"
    assert row["uncovered_display"] == "3 (30.0%)"
    assert table.key == "tab-mutants-per-project"
    assert table.label == "tab:mutants-per-project"
    assert table.short_caption == (
        "Included tests and classes with covered and uncovered mutants per project"
    )
    assert table.body_style == ""
    assert table.float_spec == "H"
    assert table.group_header_align == "r"
    assert [column.header for column in table.columns] == [
        "Project",
        "Test Methods",
        "Impl. Classes",
        "Total",
        "Covered",
        "Uncovered",
    ]


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
        r"Total \%",
        r"\VariantInitial{}",
        r"\VariantNaiveC{}",
        r"\VariantNaiveC{}",
        r"\VariantImprovedC{}",
        r"\VariantImprovedC{}",
    ]
    assert [column.group_header for column in table.columns[3:]] == [
        r"Detected \%"
    ] * 5
