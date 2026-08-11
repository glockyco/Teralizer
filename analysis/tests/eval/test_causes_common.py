import pandas as pd

from teralizer.eval.model import Table
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
    variant_sort_key,
)


def test_filtering_table_shapes_columns_and_percentages():
    df = pd.DataFrame(
        {
            "level": ["Test", "Assertion"],
            "filter": ["NonPassingTest", "AssertionType"],
            "total": [100, 200],
            "accept": [88, 152],
            "defer": [0, 0],
            "reject": [12, 48],
        }
    )
    table = build_filtering_table(df, key="filtering", label="tab:x", caption="C")
    assert isinstance(table, Table)
    assert [c.source for c in table.columns] == [
        "level",
        "filter",
        "total",
        "accept_display",
        "defer_display",
        "reject_display",
    ]
    row = table.df.set_index("filter").loc["NonPassingTest"]
    assert row["reject_pct"] == 0.12
    assert row["accept_pct"] == 0.88
    assert row["accept_display"] == "88 (88.0%)"
    assert row["defer_display"] == "-"
    assert table.columns[3].csv_source == "accept"


def test_breakdown_table_percentages_over_total():
    df = pd.DataFrame(
        {
            "level": ["Test", "Assertion", "Generalization"],
            "total": [81810, 122153, 239],
            "included": [33385, 711, 206],
            "filtering": [40583, 121060, 23],
            "failures": [7842, 382, 10],
        }
    )
    table = build_breakdown_table(df, key="breakdown", label="tab:y", caption="C")
    row = table.df.set_index("level").loc["Test"]
    assert round(row["included_pct"], 3) == 0.408
    assert round(row["filtering_pct"], 3) == 0.496
    assert round(row["failures_pct"], 3) == 0.096
    assert [c.source for c in table.columns] == [
        "level",
        "total",
        "included_display",
        "filtering_display",
        "failures_display",
    ]
    assert row["included_display"] == "33,385 (40.8%)"
    assert row["filtering_display"] == "40,583 (49.6%)"
    assert row["failures_display"] == "7,842 (9.6%)"
    assert [c.csv_source for c in table.columns[2:]] == [
        "included",
        "filtering",
        "failures",
    ]


def test_breakdown_table_with_strategy_column():
    df = pd.DataFrame(
        {
            "strategy": ["All", "All", "Baseline", "ImprovedC"],
            "level": ["Test", "Assertion", "Generalization", "Generalization"],
            "total": [100, 200, 50, 50],
            "included": [90, 150, 48, 40],
            "filtering": [8, 40, 2, 9],
            "failures": [2, 10, 0, 1],
        }
    )
    table = build_breakdown_table(
        df, key="b", label="tab:z", caption="C", include_strategy=True
    )
    assert table.columns[0].source == "strategy_display"
    assert table.columns[0].csv_source == "strategy"
    assert table.columns[1].source == "level"
    assert table.df.iloc[0]["strategy_display"] == r"\VariantAll{}"
    assert table.group_by == "level"
    # two generalization rows survive (per strategy), not collapsed
    assert (table.df["level"] == "Generalization").sum() == 2


def test_variant_sort_key_orders_baseline_naive_improved():
    order = [
        "BASELINE",
        "NAIVE_10_TRIES",
        "NAIVE_50_TRIES",
        "NAIVE_200_TRIES",
        "IMPROVED_10_TRIES",
        "IMPROVED_50_TRIES",
        "IMPROVED_200_TRIES",
    ]
    keys = [variant_sort_key(v) for v in order]
    assert keys == sorted(keys)
    assert len(set(keys)) == len(keys)


def test_breakdown_strategy_rows_ordered():
    df = pd.DataFrame(
        {
            "strategy": [
                "All",
                "All",
                "IMPROVED_200_TRIES",
                "NAIVE_50_TRIES",
                "BASELINE",
                "IMPROVED_10_TRIES",
                "NAIVE_200_TRIES",
                "IMPROVED_50_TRIES",
                "NAIVE_10_TRIES",
            ],
            "level": ["Test", "Assertion"] + ["Generalization"] * 7,
            "total": [10, 10, 1, 1, 1, 1, 1, 1, 1],
            "included": [9, 9, 1, 1, 1, 1, 1, 1, 1],
            "filtering": [1, 1, 0, 0, 0, 0, 0, 0, 0],
            "failures": [0, 0, 0, 0, 0, 0, 0, 0, 0],
        }
    )
    table = build_breakdown_table(
        df, key="b", label="tab:z", caption="C", include_strategy=True
    )
    assert list(table.df["level"])[:2] == ["Test", "Assertion"]
    gen = table.df[table.df["level"] == "Generalization"]
    assert list(gen["strategy"]) == [
        "BASELINE",
        "NAIVE_10_TRIES",
        "NAIVE_50_TRIES",
        "NAIVE_200_TRIES",
        "IMPROVED_10_TRIES",
        "IMPROVED_50_TRIES",
        "IMPROVED_200_TRIES",
    ]


def test_filtering_table_orders_level_then_filter():
    df = pd.DataFrame(
        {
            "level": [
                "Assertion",
                "Test",
                "Assertion",
                "Test",
                "Assertion",
                "Test",
                "Assertion",
                "Assertion",
            ],
            "filter": [
                "ParameterType",
                "NoAssertions",
                "AssertionType",
                "NonPassingTest",
                "MissingValue",
                "TestType",
                "VoidReturnType",
                "ExcludedTest",
            ],
            "total": [1] * 8,
            "accept": [1] * 8,
            "defer": [0] * 8,
            "reject": [1] * 8,
        }
    )
    table = build_filtering_table(df, key="f", label="tab:z", caption="C")
    order = list(zip(table.df["level"], table.df["filter"]))
    assert order == [
        ("Test", "NonPassingTest"),
        ("Test", "TestType"),
        ("Test", "NoAssertions"),
        ("Assertion", "AssertionType"),
        ("Assertion", "ExcludedTest"),
        ("Assertion", "MissingValue"),
        ("Assertion", "ParameterType"),
        ("Assertion", "VoidReturnType"),
    ]
