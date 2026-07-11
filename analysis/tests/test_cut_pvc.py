from pathlib import Path

import pandas as pd

from teralizer.cut_pvc import (
    cut_pvc_per_row,
    load_capture,
    load_cut_values,
    validation_table,
    write_cut_values,
)


def _write_capture(raw_dir: Path, slug: str, file_name: str, content: str) -> None:
    case_dir = raw_dir / slug
    case_dir.mkdir(parents=True, exist_ok=True)
    (case_dir / file_name).write_text(content, encoding="utf-8")


def test_per_row_pvc_sums_distinct_values_per_parameter(tmp_path):
    # Two parameters with overlapping values across trials: 3 + 2 distinct.
    _write_capture(
        tmp_path,
        "minmaxdouble",
        "FastMath.min.tsv",
        "p0=1.0\tp1=2.0\np0=2.0\tp1=2.0\np0=3.0\tp1=1.0\n",
    )
    values = load_capture(tmp_path)
    per_row = cut_pvc_per_row(values).set_index("table_row")
    assert per_row.loc["FastMathTest::testMinMaxDouble", "measured_cut_pvc"] == 5
    # Distinct tuples: (1,2), (2,2), (3,1).
    assert per_row.loc["FastMathTest::testMinMaxDouble", "distinct_tuples"] == 3


def test_validation_marks_missing_cases_unavailable(tmp_path):
    _write_capture(tmp_path, "isascii", "CharUtils.isAscii.tsv", "p0=a\np0=b\n")
    values = load_capture(tmp_path)
    validation = validation_table(values).set_index("table_row")
    assert validation.loc["CharUtilsTest::isAscii", "measured_cut_pvc"] == 2
    assert validation.loc["CharUtilsTest::isAscii", "reported_cut_pvc"] == 6
    # No captured files: unavailable, never zero.
    assert pd.isna(validation.loc["PrecisionTest", "measured_cut_pvc"])


def test_cut_values_round_trip_deduplicates(tmp_path):
    _write_capture(tmp_path, "isascii", "CharUtils.isAscii.tsv", "p0=a\np0=a\np0=b\n")
    values = load_capture(tmp_path)
    out = tmp_path / "cut_values.tsv"
    write_cut_values(values, out)
    loaded = load_cut_values(out)
    assert len(loaded) == 2
    assert set(loaded["value"]) == {"a", "b"}
    assert set(loaded.columns) == {
        "table_row",
        "mut_class",
        "mut_method",
        "parameter",
        "value",
    }


def test_load_cut_values_missing_file_is_empty(tmp_path):
    loaded = load_cut_values(tmp_path / "absent.tsv")
    assert loaded.empty


def test_escaped_values_round_trip(tmp_path):
    # A tab inside a value must survive the escape/unescape round trip.
    _write_capture(tmp_path, "isascii", "CharUtils.isAscii.tsv", "p0=a\\tb\n")
    values = load_capture(tmp_path)
    assert values["value"].tolist() == ["a\tb"]
