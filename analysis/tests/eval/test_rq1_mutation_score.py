import pandas as pd

from teralizer.eval.reports.rq1_mutation_score import _coverage_table, _mutator_table


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
    assert table.label == "tab:mutants-per-project"


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
