import pandas as pd

from teralizer.eval.model import Table
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
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
        "accept_pct",
        "defer_pct",
        "reject_pct",
    ]
    row = table.df.set_index("filter").loc["NonPassingTest"]
    assert row["reject_pct"] == 0.12
    assert row["accept_pct"] == 0.88


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
        "included",
        "included_pct",
        "filtering",
        "filtering_pct",
        "failures",
        "failures_pct",
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
    assert table.columns[0].source == "strategy"
    assert table.columns[1].source == "level"
    assert table.group_by == "level"
    # two generalization rows survive (per strategy), not collapsed
    assert (table.df["level"] == "Generalization").sum() == 2
