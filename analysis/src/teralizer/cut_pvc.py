"""Standalone CUT-PVC capture aggregation for the RQ0 JARVIS comparison.

The capture itself is a one-off measurement (``scripts/run-cut-pvc-capture.sh``
plus ``tools/cut-pvc-capture``): a javaagent records the parameter values the
original Commons test suites pass to the JARVIS Table-2 methods under test on
the pinned fixture checkouts. This module turns the raw per-case TSVs into the
``analysis/data/jarvis-cut-values/cut_values.tsv`` dataset consumed by the RQ0
report and validates the measured CUT PVC against the values reported by
JARVIS. It is deliberately not part of the processing pipeline.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import TypedDict, cast

import pandas as pd

from teralizer.jarvis_scoreboard import JARVIS_TABLE2, parse_jqwik_value_log
from teralizer.report_basis import resolve_repo_relative_path

CUT_VALUES_PATH = resolve_repo_relative_path(
    "analysis/data/jarvis-cut-values/cut_values.tsv"
)

_LANG = "org.apache.commons.lang3"
_MATH = "org.apache.commons.math4"


class CaptureTarget(TypedDict):
    """One intercepted method: agent spec plus its probe-side MUT identity."""

    agent: str
    file: str
    mut_class: str
    mut_method: str


class CaptureCase(TypedDict):
    slug: str
    table_row: str
    project: str
    caller: str
    tests: tuple[str, ...]
    targets: tuple[CaptureTarget, ...]


# One entry per JARVIS Table-2 row. `tests` are surefire -Dtest filters run in
# the pinned fixture checkout; an empty tuple documents a reported case with no
# counterpart in the pinned original suite (measured CUT PVC stays unavailable).
CAPTURE_PLAN: tuple[CaptureCase, ...] = (
    {
        "slug": "isascii",
        "table_row": "CharUtilsTest::isAscii",
        "project": "commons-lang",
        "caller": f"{_LANG}.CharUtilsTest",
        "tests": ("CharUtilsTest#testIsAscii_char",),
        "targets": (
            {
                "agent": f"{_LANG}.CharUtils#isAscii(char)",
                "file": "CharUtils.isAscii.tsv",
                "mut_class": f"{_LANG}.CharUtils",
                "mut_method": "isAscii",
            },
        ),
    },
    {
        "slug": "isprintable",
        "table_row": "CharUtilsTest::isPrintable",
        "project": "commons-lang",
        "caller": f"{_LANG}.CharUtilsTest",
        "tests": ("CharUtilsTest#testIsAsciiPrintable_char",),
        "targets": (
            {
                "agent": f"{_LANG}.CharUtils#isAsciiPrintable(char)",
                "file": "CharUtils.isAsciiPrintable.tsv",
                "mut_class": f"{_LANG}.CharUtils",
                "mut_method": "isAsciiPrintable",
            },
        ),
    },
    {
        "slug": "minmaxdouble",
        "table_row": "FastMathTest::testMinMaxDouble",
        "project": "commons-math",
        "caller": f"{_MATH}.util.FastMathTest",
        "tests": ("FastMathTest#testMinMaxDouble",),
        "targets": (
            {
                "agent": f"{_MATH}.util.FastMath#min(double,double)",
                "file": "FastMath.min.tsv",
                "mut_class": f"{_MATH}.util.FastMath",
                "mut_method": "min",
            },
            {
                "agent": f"{_MATH}.util.FastMath#max(double,double)",
                "file": "FastMath.max.tsv",
                "mut_class": f"{_MATH}.util.FastMath",
                "mut_method": "max",
            },
        ),
    },
    {
        "slug": "tointexact",
        "table_row": "FastMathTest::toIntExact",
        "project": "commons-math",
        "caller": f"{_MATH}.util.FastMathTest",
        # The toIntExact scenario spans the passing loop test and the two
        # expected-exception tests (JARVIS learns from negative examples too).
        "tests": (
            "FastMathTest#testToIntExact",
            "FastMathTest#testToIntExactTooLow",
            "FastMathTest#testToIntExactTooHigh",
        ),
        "targets": (
            {
                "agent": f"{_MATH}.util.FastMath#toIntExact(long)",
                "file": "FastMath.toIntExact.tsv",
                "mut_class": f"{_MATH}.util.FastMath",
                "mut_method": "toIntExact",
            },
        ),
    },
    {
        "slug": "interval",
        "table_row": "IntervalTest",
        "project": "commons-math",
        "caller": f"{_MATH}.geometry.euclidean.oned.IntervalTest",
        # The JARVIS publication (section 9.1) documents this case as the
        # getSize scenario built from testInterval and testSinglePoint, so the
        # capture runs exactly those two methods rather than the whole class.
        "tests": ("IntervalTest#testInterval", "IntervalTest#testSinglePoint"),
        # The reported values are the Interval constructor arguments, which is
        # also how the Teralizer probe parameterizes getSize.
        "targets": (
            {
                "agent": f"{_MATH}.geometry.euclidean.oned.Interval#<init>(double,double)",
                "file": "Interval.init.tsv",
                "mut_class": f"{_MATH}.geometry.euclidean.oned.Interval",
                "mut_method": "getSize",
            },
        ),
    },
    {
        "slug": "polyconstants",
        "table_row": "PolynomialFunctionTest::testConstants",
        "project": "commons-math",
        "caller": f"{_MATH}.analysis.polynomials.PolynomialFunctionTest",
        "tests": ("PolynomialFunctionTest#testConstants",),
        "targets": (
            {
                "agent": f"{_MATH}.analysis.polynomials.PolynomialFunction#value(double)",
                "file": "PolynomialFunction.value.tsv",
                "mut_class": f"{_MATH}.analysis.polynomials.PolynomialFunction",
                "mut_method": "value",
            },
        ),
    },
    {
        "slug": "polyderivative",
        "table_row": "PolynomialFunctionTest::testfirstDerivativeComparison",
        "project": "commons-math",
        "caller": f"{_MATH}.analysis.polynomials.PolynomialFunctionTest",
        "tests": ("PolynomialFunctionTest#testfirstDerivativeComparison",),
        "targets": (
            {
                "agent": f"{_MATH}.analysis.polynomials.PolynomialFunction#value(double)",
                "file": "PolynomialFunction.value.tsv",
                "mut_class": f"{_MATH}.analysis.polynomials.PolynomialFunction",
                "mut_method": "value",
            },
        ),
    },
    {
        "slug": "polylinear",
        "table_row": "PolynomialFunctionTest::testLinear",
        "project": "commons-math",
        "caller": f"{_MATH}.analysis.polynomials.PolynomialFunctionTest",
        "tests": ("PolynomialFunctionTest#testLinear",),
        "targets": (
            {
                "agent": f"{_MATH}.analysis.polynomials.PolynomialFunction#value(double)",
                "file": "PolynomialFunction.value.tsv",
                "mut_class": f"{_MATH}.analysis.polynomials.PolynomialFunction",
                "mut_method": "value",
            },
        ),
    },
    {
        "slug": "precision",
        "table_row": "PrecisionTest",
        "project": "commons-math",
        "caller": f"{_MATH}.util.PrecisionTest",
        "tests": ("PrecisionTest",),
        "targets": (
            {
                "agent": f"{_MATH}.util.Precision#equals(double,double,double)",
                "file": "Precision.equals.tsv",
                "mut_class": f"{_MATH}.util.Precision",
                "mut_method": "equals",
            },
        ),
    },
    {
        "slug": "testabs",
        "table_row": "UnivariateFunctionTest::testAbs",
        "project": "commons-math",
        "caller": f"{_MATH}.analysis.function.UnivariateFunctionTest",
        "tests": ("UnivariateFunctionTest#testAbs",),
        "targets": (
            {
                "agent": f"{_MATH}.analysis.function.Abs#value(double)",
                "file": "Abs.value.tsv",
                "mut_class": f"{_MATH}.analysis.function.Abs",
                "mut_method": "value",
            },
        ),
    },
)


def load_capture(raw_dir: Path) -> pd.DataFrame:
    """Read every captured per-case TSV into one row per parameter value.

    Returns columns ``slug, table_row, mut_class, mut_method, parameter,
    value, trial_index, file``. Cases without captured files contribute no
    rows (unavailable, never zero).
    """
    frames: list[pd.DataFrame] = []
    for case in CAPTURE_PLAN:
        case_dir = raw_dir / case["slug"]
        for target in case["targets"]:
            path = case_dir / target["file"]
            if not path.is_file():
                continue
            values = parse_jqwik_value_log(path)
            if values.empty:
                continue
            values = values.assign(
                slug=case["slug"],
                table_row=case["table_row"],
                mut_class=target["mut_class"],
                mut_method=target["mut_method"],
                file=target["file"],
            )
            frames.append(values)
    if not frames:
        return pd.DataFrame(
            columns=[
                "trial_index",
                "parameter_name",
                "value",
                "slug",
                "table_row",
                "mut_class",
                "mut_method",
                "file",
            ]
        )
    combined = pd.concat(frames, ignore_index=True)
    return combined.rename(columns={"parameter_name": "parameter"})


def cut_pvc_per_row(values: pd.DataFrame) -> pd.DataFrame:
    """Measured CUT PVC per Table-2 row.

    ``measured_cut_pvc`` uses the same construct as the Teralizer PVC column:
    distinct values per (MUT, parameter), summed. ``distinct_tuples`` counts
    distinct full argument tuples per intercepted method as a diagnostic for
    reconciling multi-parameter rows with the values reported by JARVIS.
    """
    rows: list[dict[str, object]] = []
    for table_row, row_values in values.groupby("table_row", sort=False):
        per_parameter = row_values.groupby(["file", "parameter"])["value"].nunique()
        tuples = (
            row_values.pivot_table(
                index=["file", "trial_index"],
                columns="parameter",
                values="value",
                aggfunc="first",
            )
            .apply(tuple, axis=1)
            .groupby(level="file")
            .nunique()
        )
        rows.append(
            {
                "table_row": table_row,
                "measured_cut_pvc": int(per_parameter.sum()),
                "distinct_tuples": int(tuples.sum()),
            }
        )
    return pd.DataFrame(
        rows, columns=["table_row", "measured_cut_pvc", "distinct_tuples"]
    )


def write_cut_values(values: pd.DataFrame, out_path: Path) -> Path:
    """Write the distinct (row, MUT, parameter, value) dataset."""
    distinct = (
        cast(
            pd.DataFrame,
            values[["table_row", "mut_class", "mut_method", "parameter", "value"]],
        )
        .drop_duplicates()
        .sort_values(by=["table_row", "mut_class", "mut_method", "parameter", "value"])
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    distinct.to_csv(out_path, sep="\t", index=False)
    return out_path


def load_cut_values(path: Path = CUT_VALUES_PATH) -> pd.DataFrame:
    """Read the committed dataset. Missing file returns an empty frame."""
    columns = ["table_row", "mut_class", "mut_method", "parameter", "value"]
    if not path.is_file():
        return pd.DataFrame(columns=columns)
    values = pd.read_csv(path, sep="\t", dtype=str)
    missing = set(columns).difference(values.columns)
    if missing:
        raise ValueError(f"Unexpected cut_values schema, missing: {sorted(missing)}")
    return cast(pd.DataFrame, values[columns])


def validation_table(values: pd.DataFrame) -> pd.DataFrame:
    """Measured CUT PVC per row next to the CUT PVC reported by JARVIS."""
    measured = cut_pvc_per_row(values).set_index("table_row")
    rows: list[dict[str, object]] = []
    for jarvis_row in JARVIS_TABLE2:
        table_row = jarvis_row.table_row
        if table_row in measured.index:
            measured_pvc: object = int(measured.loc[table_row, "measured_cut_pvc"])
            tuples: object = int(measured.loc[table_row, "distinct_tuples"])
        else:
            measured_pvc = None
            tuples = None
        rows.append(
            {
                "table_row": table_row,
                "reported_cut_pvc": jarvis_row.cut_pvc,
                "measured_cut_pvc": measured_pvc,
                "distinct_tuples": tuples,
            }
        )
    return pd.DataFrame(rows)


def _print_plan() -> None:
    for case in CAPTURE_PLAN:
        if not case["tests"]:
            continue
        print(
            "\t".join(
                [
                    case["slug"],
                    case["project"],
                    case["caller"],
                    ";".join(case["tests"]),
                    ";".join(target["agent"] for target in case["targets"]),
                ]
            )
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="teralizer.cut_pvc", description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("plan", help="print the capture plan for the runner script")
    aggregate = subparsers.add_parser(
        "aggregate", help="aggregate raw capture TSVs and validate against JARVIS"
    )
    aggregate.add_argument("raw_dir", type=Path)
    aggregate.add_argument("--out", type=Path, default=CUT_VALUES_PATH)
    args = parser.parse_args(argv)

    if args.command == "plan":
        _print_plan()
        return 0

    values = load_capture(args.raw_dir)
    if values.empty:
        print(f"No capture data under {args.raw_dir}", file=sys.stderr)
        return 1
    out_path = write_cut_values(values, args.out)
    print(f"Wrote {out_path}")
    print(validation_table(values).to_string(index=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
