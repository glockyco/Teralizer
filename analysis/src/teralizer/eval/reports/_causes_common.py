"""Presentation shared by RQ5 and RQ6: exclusion breakdown and filter-rejection
tables. Each RQ supplies a normalized frame from its own schema-specific query
layer; the Table shape, columns, and percentage formatting live here once."""

from __future__ import annotations

import re
from collections.abc import Sequence
from dataclasses import dataclass

import pandas as pd

from teralizer.eval.model import ColumnSpec, Table


@dataclass(frozen=True)
class Outcome:
    """One breakdown column: the frame column and its count/percentage headers."""

    column: str
    header: str
    pct_header: str


# The old schema cannot tell the exclusion mechanisms apart, so RQ5 keeps the
# two-way split it was collected under.
LEGACY_OUTCOMES: tuple[Outcome, ...] = (
    Outcome("included", "Included", "Incl. %"),
    Outcome("filtering", "Filtering", "Filt. %"),
    Outcome("failures", "Failures", "Fail. %"),
)

# Internal only. Reports collapse this before rendering, so do not add a column
# here expecting it to appear in a table.
MECHANISM_OUTCOMES: tuple[Outcome, ...] = (
    Outcome("included", "Included", "Incl. %"),
    Outcome("filtered", "Filtered", "Filt. %"),
    Outcome("refused", "Refused", "Ref. %"),
    Outcome("unsupported", "Unsupported", "Unsup. %"),
    Outcome("failed", "Failed", "Fail. %"),
)

# The reported split. Filtering is the tool declining an unsuitable candidate.
# Failures are breakage. Two mechanisms are easy to put on the wrong side: a
# javac quarantine is breakage despite recording its verdict in `filter_result`,
# and an unflattenable test shape is a decision despite nothing having failed.
MECHANISM_COLLAPSE: dict[str, tuple[str, ...]] = {
    "included": ("included",),
    "filtering": ("filtered", "refused", "unsupported"),
    "failures": ("failed",),
}


def collapse_mechanisms(df: pd.DataFrame) -> pd.DataFrame:
    """Fold per-mechanism counts into the reader-facing three-way split."""
    out = df.copy()
    for target, sources in MECHANISM_COLLAPSE.items():
        out[target] = sum(out[source] for source in sources)
    keep = ["level", "total", *MECHANISM_COLLAPSE]
    if "strategy" in out.columns:
        keep.insert(0, "strategy")
    return pd.DataFrame(out, columns=keep)


_VARIANT_HEAD = {"ORIGINAL": 0, "INITIAL": 1, "SHARED": 2, "BASELINE": 3}
_VARIANT_GROUP = {"NAIVE": 4, "IMPROVED": 5}
_VARIANT_MACROS = {
    "All": r"\VariantAll{}",
    "BASELINE": r"\VariantBaseline{}",
    "NAIVE_10_TRIES": r"\VariantNaiveA{}",
    "NAIVE_50_TRIES": r"\VariantNaiveB{}",
    "NAIVE_200_TRIES": r"\VariantNaiveC{}",
    "IMPROVED_10_TRIES": r"\VariantImprovedA{}",
    "IMPROVED_50_TRIES": r"\VariantImprovedB{}",
    "IMPROVED_200_TRIES": r"\VariantImprovedC{}",
}
_LEVEL_ORDER = {"Test": 0, "Assertion": 1, "Generalization": 2}
# Paper/table display order for filters. This is the hand-curated order used in
# the published tables, deliberately NOT the pipeline application order.
_FILTER_ORDER = {
    "NonPassingTest": 0,
    "TestType": 1,
    "NoAssertions": 2,
    "AssertionType": 0,
    "ExcludedTest": 1,
    "MissingValue": 2,
    "ParameterType": 3,
    "ReturnType": 4,
    "VoidReturnType": 4,
}


def filter_sort_key(filter_name: str) -> int:
    return _FILTER_ORDER.get(filter_name, 99)


def _count_with_pct(count: object, pct: object) -> str:
    """Render a publication cell while leaving raw counts and rates in the frame."""
    return f"{int(count):,} ({float(pct) * 100:.1f}%)"


def variant_sort_key(variant: str) -> int:
    """Canonical rank: baseline first, then naive then improved, each ascending by tries."""
    if variant in _VARIANT_HEAD:
        return _VARIANT_HEAD[variant] * 1000
    m = re.match(r"^(NAIVE|IMPROVED)_(\d+)_TRIES$", variant)
    if m:
        return _VARIANT_GROUP[m.group(1)] * 1000 + int(m.group(2))
    return 99000


def build_filtering_table(
    df: pd.DataFrame,
    *,
    key: str,
    label: str,
    caption: str,
    short_caption: str | None = None,
    body_style: str | None = None,
    full_width: bool = False,
) -> Table:
    """df columns: level, filter, total, accept, defer, reject (integer counts)."""
    out = df.copy()
    for decision in ("accept", "defer", "reject"):
        out[f"{decision}_pct"] = out[decision] / out["total"]
        out[f"{decision}_display"] = [
            "-"
            if decision == "defer" and int(count) == 0
            else _count_with_pct(count, pct)
            for count, pct in zip(out[decision], out[f"{decision}_pct"], strict=True)
        ]
    out = out.assign(
        _lvl=out["level"].map(lambda lvl: _LEVEL_ORDER.get(lvl, 99)),
        _fil=out["filter"].map(filter_sort_key),
    )
    out = (
        out.sort_values(["_lvl", "_fil", "filter"])
        .drop(columns=["_lvl", "_fil"])
        .reset_index(drop=True)
    )
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Filter Name", "filter"),
        ColumnSpec("Total", "total", fmt="count", align="r"),
        ColumnSpec(
            "Accept", "accept_display", fmt="str", align="r", csv_source="accept"
        ),
        ColumnSpec(
            "Defer", "defer_display", fmt="str", align="r", csv_source="defer"
        ),
        ColumnSpec(
            "Reject", "reject_display", fmt="str", align="r", csv_source="reject"
        ),
    ]
    return Table(
        key=key,
        df=out,
        columns=columns,
        caption=caption,
        label=label,
        group_by="level",
        short_caption=short_caption,
        body_style="\\centering" if body_style is None else body_style,
        full_width=full_width,
    )


def build_breakdown_table(
    df: pd.DataFrame,
    *,
    key: str,
    label: str,
    caption: str,
    include_strategy: bool = False,
    outcomes: Sequence[Outcome] = LEGACY_OUTCOMES,
    short_caption: str | None = None,
    body_style: str | None = None,
    full_width: bool = False,
    group_header_align: str = "c",
) -> Table:
    """df columns: level, total, one column per outcome (and optional strategy)."""
    out = df.copy()
    for outcome in outcomes:
        out[f"{outcome.column}_pct"] = out[outcome.column] / out["total"]
        out[f"{outcome.column}_display"] = [
            _count_with_pct(count, pct)
            for count, pct in zip(
                out[outcome.column], out[f"{outcome.column}_pct"], strict=True
            )
        ]
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Total", "total", fmt="count", align="r"),
    ]
    for outcome in outcomes:
        group_header = "Excluded" if outcome.column in {"filtering", "failures"} else None
        columns.append(
            ColumnSpec(
                outcome.header,
                f"{outcome.column}_display",
                fmt="str",
                align="r",
                group_header=group_header,
                csv_source=outcome.column,
            )
        )
    group_by = None
    if include_strategy:
        out["strategy_display"] = out["strategy"].map(
            lambda value: _VARIANT_MACROS.get(str(value), str(value))
        )
        columns = [
            ColumnSpec("Strategy", "strategy_display", csv_source="strategy"),
            *columns,
        ]
        group_by = "level"
    if include_strategy:
        out = out.assign(
            _lvl=out["level"].map(lambda lvl: _LEVEL_ORDER.get(lvl, 99)),
            _var=out["strategy"].map(variant_sort_key),
        )
        out = (
            out.sort_values(["_lvl", "_var"])
            .drop(columns=["_lvl", "_var"])
            .reset_index(drop=True)
        )
    return Table(
        key=key,
        df=out,
        columns=columns,
        caption=caption,
        label=label,
        group_by=group_by,
        short_caption=short_caption,
        body_style="\\centering" if body_style is None else body_style,
        full_width=full_width,
        group_header_align=group_header_align,
    )
