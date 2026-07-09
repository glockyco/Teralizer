"""Presentation shared by RQ5 and RQ6: exclusion breakdown and filter-rejection
tables. Each RQ supplies a normalized frame from its own schema-specific query
layer; the Table shape, columns, and percentage formatting live here once."""

from __future__ import annotations

import re

import pandas as pd

from teralizer.eval.model import ColumnSpec, Table

_VARIANT_HEAD = {"ORIGINAL": 0, "INITIAL": 1, "SHARED": 2, "BASELINE": 3}
_VARIANT_GROUP = {"NAIVE": 4, "IMPROVED": 5}
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


def variant_sort_key(variant: str) -> int:
    """Canonical rank: baseline first, then naive then improved, each ascending by tries."""
    if variant in _VARIANT_HEAD:
        return _VARIANT_HEAD[variant] * 1000
    m = re.match(r"^(NAIVE|IMPROVED)_(\d+)_TRIES$", variant)
    if m:
        return _VARIANT_GROUP[m.group(1)] * 1000 + int(m.group(2))
    return 99000


def build_filtering_table(
    df: pd.DataFrame, *, key: str, label: str, caption: str
) -> Table:
    """df columns: level, filter, total, accept, defer, reject (integer counts)."""
    out = df.copy()
    for decision in ("accept", "defer", "reject"):
        out[f"{decision}_pct"] = out[decision] / out["total"]
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
        ColumnSpec("Accept", "accept_pct", fmt="pct1", align="r"),
        ColumnSpec("Defer", "defer_pct", fmt="pct1", align="r"),
        ColumnSpec("Reject", "reject_pct", fmt="pct1", align="r"),
    ]
    return Table(
        key=key, df=out, columns=columns, caption=caption, label=label, group_by="level"
    )


def build_breakdown_table(
    df: pd.DataFrame,
    *,
    key: str,
    label: str,
    caption: str,
    include_strategy: bool = False,
) -> Table:
    """df columns: level, total, included, filtering, failures (and optional strategy)."""
    out = df.copy()
    for part in ("included", "filtering", "failures"):
        out[f"{part}_pct"] = out[part] / out["total"]
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Total", "total", fmt="count", align="r"),
        ColumnSpec("Included", "included", fmt="count", align="r"),
        ColumnSpec("Incl. %", "included_pct", fmt="pct1", align="r"),
        ColumnSpec("Filtering", "filtering", fmt="count", align="r"),
        ColumnSpec("Filt. %", "filtering_pct", fmt="pct1", align="r"),
        ColumnSpec("Failures", "failures", fmt="count", align="r"),
        ColumnSpec("Fail. %", "failures_pct", fmt="pct1", align="r"),
    ]
    group_by = None
    if include_strategy:
        columns = [ColumnSpec("Strategy", "strategy"), *columns]
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
    )
