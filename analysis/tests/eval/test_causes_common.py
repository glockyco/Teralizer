from decimal import Decimal

import pandas as pd

from teralizer.eval.entities import variant_ref
from teralizer.eval.model import Table, ValueKind
from teralizer.eval.render.latex import render_table
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
        "accept",
        "defer",
        "reject",
    ]
    # The frame keeps numbers. Pairing a count with its share is the renderer's
    # job, because only a whole column can be aligned.
    decisions = {c.source: c for c in table.columns[3:]}
    assert [c.kind for c in decisions.values()] == [ValueKind.COUNT] * 3
    assert decisions["accept"].share_source == "accept_pct"
    row = table.df.set_index("filter").loc["NonPassingTest"]
    assert row["reject_pct"] == Decimal("0.12")
    assert row["accept_pct"] == Decimal("0.88")

    latex = render_table(table)
    assert "88\\; (88.0\\%)" in latex
    # A decision the filter never takes reads as an absence, not as a zero share.
    assert "& -- &" in latex


def test_filtering_table_uses_reader_facing_filter_names():
    frame = pd.DataFrame(
        {
            "level": ["Test", "Test"],
            "filter": ["InheritedTestCase", "MockingFramework"],
            "total": [1, 1],
            "accept": [1, 1],
            "defer": [0, 0],
            "reject": [0, 0],
        }
    )

    table = build_filtering_table(frame, key="filtering", label="tab:x", caption="C")

    assert table.df["filter"].tolist() == ["InheritedTest", "Mocking"]


def test_breakdown_table_percentages_over_total():
    df = pd.DataFrame(
        {
            "level": ["Assertion", "Generalization", "Test"],
            "total": [122153, 239, 81810],
            "included": [711, 206, 33385],
            "filtering": [121060, 23, 40583],
            "failures": [382, 10, 7842],
        }
    )
    table = build_breakdown_table(df, key="breakdown", label="tab:y", caption="C")
    assert table.df["level"].tolist() == ["Test", "Assertion", "Generalization"]
    row = table.df.set_index("level").loc["Test"]
    assert round(row["included_pct"], 3) == Decimal("0.408")
    assert round(row["filtering_pct"], 3) == Decimal("0.496")
    assert round(row["failures_pct"], 3) == Decimal("0.096")
    assert [c.source for c in table.columns] == [
        "level",
        "total",
        "included",
        "filtering",
        "failures",
    ]
    assert [c.share_source for c in table.columns[2:]] == [
        "included_pct",
        "filtering_pct",
        "failures_pct",
    ]

    latex = render_table(table)
    assert "33,385\\; (40.8\\%)" in latex
    assert "40,583\\; (49.6\\%)" in latex
    # A share narrower than the widest in its own column is padded, so the
    # parentheses line up. 9.6 sits under 99.1 here, and gains one digit.
    assert "23\\; (\\phantom{0}9.6\\%)" in latex
    # Its neighbour needs none: every share in that column is equally wide.
    assert "7,842\\; (9.6\\%)" in latex
    # Nothing pads a count: its column is right-aligned already.
    assert "\\phantom{}" not in latex


def test_breakdown_table_with_strategy_column():
    df = pd.DataFrame(
        {
            "strategy": ["All", "All", "BASELINE", "IMPROVED_200_TRIES"],
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
    assert table.columns[0].kind is ValueKind.ENTITY
    assert table.columns[1].source == "level"
    assert table.df.iloc[0]["strategy"] == variant_ref("All")
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
        variant_ref("BASELINE"),
        variant_ref("NAIVE_10_TRIES"),
        variant_ref("NAIVE_50_TRIES"),
        variant_ref("NAIVE_200_TRIES"),
        variant_ref("IMPROVED_10_TRIES"),
        variant_ref("IMPROVED_50_TRIES"),
        variant_ref("IMPROVED_200_TRIES"),
    ]


def test_filtering_table_groups_rounds_and_orders_by_rejections():
    rows = [
        ("Assertion", "ParameterType", 7),
        ("Test", "NoAssertions", 10),
        ("Assertion", "AssertionType", 4),
        ("Test", "NonPassingTest", 8),
        ("Assertion", "MissingValue", 9),
        ("Test", "TestType", 2),
        ("Assertion", "VoidReturnType", 1),
        ("Assertion", "ExcludedTest", 4),
        ("Generalization", "NonPassingTest", 3),
        ("Generalization", "WideningLicense", 9),
        ("Generalization", "SeedSpecConsistency", 1),
        ("Test", "MockingFramework", 8),
        ("Test", "InheritedTestMethod", 6),
        ("Test", "InheritedTestCase", 4),
        ("Test", "DisabledTest", 1),
    ]
    df = pd.DataFrame(rows, columns=["level", "filter", "reject"])
    df.loc[:, "total"] = 20
    df.loc[:, "accept"] = df["total"] - df["reject"]
    df.loc[:, "defer"] = 0

    table = build_filtering_table(df, key="f", label="tab:z", caption="C")

    assert table.group_by == "_filter_group"
    assert table.df["_filter_group"].tolist() == [
        0,
        0,
        *([1] * 5),
        *([2] * 5),
        *([3] * 3),
    ]
    assert list(zip(table.df["level"], table.df["filter"])) == [
        ("Test", "NonPassingTest"),
        ("Test", "TestType"),
        ("Test", "NoAssertions"),
        ("Test", "Mocking"),
        ("Test", "InheritedTestMethod"),
        ("Test", "InheritedTest"),
        ("Test", "DisabledTest"),
        ("Assertion", "MissingValue"),
        ("Assertion", "ParameterType"),
        ("Assertion", "AssertionType"),
        ("Assertion", "ExcludedTest"),
        ("Assertion", "VoidReturnType"),
        ("Generalization", "WideningLicense"),
        ("Generalization", "NonPassingTest"),
        ("Generalization", "SeedSpecConsistency"),
    ]
    assert render_table(table).count("  \\midrule") == 4
