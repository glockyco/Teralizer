import pytest


import sqlalchemy.exc

from teralizer.eval.entities import variant_ref
from teralizer.eval.inputs import CorpusInputSpec, resolve_inputs
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
from teralizer.eval.render.latex import render_table
import teralizer.eval.reports.rq5_causes  # noqa: F401  (registers "rq5")


pytestmark = pytest.mark.db


def _report() -> RQReport:
    spec = get("rq5")
    try:
        with resolve_inputs("rq5", spec.inputs) as context:
            return spec.build(context)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_rq5_has_breakdown_and_filtering_tables():
    report = _report()
    assert report.rq == "rq5"
    input_spec = get("rq5").inputs[0]
    assert isinstance(input_spec, CorpusInputSpec)
    assert (input_spec.role, input_spec.corpus_id) == ("controlled", "controlled")
    labels = {t.label for t in report.tables()}
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)
    assert {table.key for table in report.tables()} == {
        "tab-exclusions-breakdown",
        "tab-exclusions-filtering",
    }


def test_rq5_breakdown_levels_and_nonnegative_counts():
    report = _report()
    breakdown = next(t for t in report.tables() if "breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    for col in ("total", "included", "filtering", "failures"):
        assert (breakdown.df[col] >= 0).all()
    reconstructed = breakdown.df[["included", "filtering", "failures"]].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()


def test_rq5_breakdown_matches_published_goldens():
    report = _report()
    breakdown = next(t for t in report.tables() if "breakdown" in t.label)
    df = breakdown.df
    cols = ["total", "included", "filtering", "failures"]

    test_row = df[df["level"] == "Test"]
    assert len(test_row) == 1
    assert tuple(int(test_row.iloc[0][c]) for c in cols) == (
        23246,
        19306,
        3933,
        7,
    )

    assertion_row = df[df["level"] == "Assertion"]
    assert len(assertion_row) == 1
    assert tuple(int(assertion_row.iloc[0][c]) for c in cols) == (
        28923,
        13836,
        12092,
        2995,
    )

    gen = df[df["level"] == "Generalization"]
    assert len(gen) == 7
    assert set(int(v) for v in gen["total"]) == {13836}
    assert set(int(v) for v in gen["included"]) == {
        13814,
        10743,
        9964,
        9881,
        11788,
        11660,
        11597,
    }
    assert set(int(v) for v in gen["filtering"]) == {
        22,
        3061,
        3840,
        3923,
        2016,
        2144,
        2207,
    }
    assert sorted(int(v) for v in gen["failures"]) == [
        0,
        32,
        32,
        32,
        32,
        32,
        32,
    ]


def test_rq5_filtering_is_test_and_assertion_only():
    report = _report()
    filtering = next(t for t in report.tables() if "filtering" in t.label)
    assert set(filtering.df["level"]) == {"Test", "Assertion"}


def test_rq5_breakdown_centers_excluded_spanner():
    report = _report()
    breakdown = next(t for t in report.tables() if "breakdown" in t.label)

    assert breakdown.group_header_align == "c"
    assert "\\multicolumn{2}{c}{Excluded}" in render_table(breakdown)


def test_rq5_generalization_strategy_order():
    report = _report()
    breakdown = next(t for t in report.tables() if "breakdown" in t.label)
    gen = breakdown.df[breakdown.df["level"] == "Generalization"]
    assert list(gen["strategy"]) == [
        variant_ref("BASELINE"),
        variant_ref("NAIVE_10_TRIES"),
        variant_ref("NAIVE_50_TRIES"),
        variant_ref("NAIVE_200_TRIES"),
        variant_ref("IMPROVED_10_TRIES"),
        variant_ref("IMPROVED_50_TRIES"),
        variant_ref("IMPROVED_200_TRIES"),
    ]


def test_rq5_filtering_row_order():
    report = _report()
    filtering = next(t for t in report.tables() if "filtering" in t.label)
    order = list(zip(filtering.df["level"], filtering.df["filter"]))
    assert order == [
        ("Test", "NonPassingTest"),
        ("Test", "TestType"),
        ("Test", "NoAssertions"),
        ("Assertion", "MissingValue"),
        ("Assertion", "ParameterType"),
        ("Assertion", "ExcludedTest"),
        ("Assertion", "AssertionType"),
        ("Assertion", "VoidReturnType"),
    ]
    assert filtering.df["_filter_group"].tolist() == [0, 0, 0, 1, 1, 1, 1, 1]
    assert filtering.row_key == "row_key"
    assert (
        "\\label{tabrow:tab-exclusions-filtering:Assertion@3AExcludedTest}"
        in render_table(filtering)
    )
