"""Presentation shared by RQ5 and RQ6: exclusion breakdown and filter-rejection
tables. Each RQ supplies a normalized frame from its own schema-specific query
layer; the Table shape, columns, and percentage formatting live here once."""

from __future__ import annotations

import pandas as pd

from teralizer.eval.model import ColumnSpec, Table


def build_filtering_table(
    df: pd.DataFrame, *, key: str, label: str, caption: str
) -> Table:
    """df columns: level, filter, total, accept, defer, reject (integer counts)."""
    out = df.copy()
    for decision in ("accept", "defer", "reject"):
        out[f"{decision}_pct"] = out[decision] / out["total"]
    out = out.sort_values(["level", "filter"]).reset_index(drop=True)
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
    return Table(
        key=key,
        df=out,
        columns=columns,
        caption=caption,
        label=label,
        group_by=group_by,
    )
