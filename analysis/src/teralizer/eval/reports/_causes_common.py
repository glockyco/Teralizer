"""Presentation shared by RQ5 and RQ6: exclusion breakdown and filter-rejection
tables. Each RQ supplies a normalized frame from its own schema-specific query
layer; the Table shape, columns, and percentage formatting live here once."""

from __future__ import annotations

import re
from collections.abc import Sequence
from dataclasses import dataclass

import pandas as pd

from teralizer.eval.entities import variant_ref
from teralizer.eval.model import ColumnSpec, Table, ValueKind, share_value
from teralizer.eval.provenance import Provenance
from teralizer.eval.reports._exclusion_evidence import (
    MECHANISMS,
    READER_COLLAPSE,
)


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
MECHANISM_OUTCOMES: tuple[Outcome, ...] = tuple(
    Outcome(mechanism.key.value, mechanism.label, f"{mechanism.label[:4]}. %")
    for mechanism in MECHANISMS
)

# The reader-facing split is part of the canonical mechanism registry. In
# particular, quarantine remains a failure even though its producer writes a
# filter_result row, while an inherited-test inlining limit remains a declined
# candidate even though it has no filter_result row.
MECHANISM_COLLAPSE: dict[str, tuple[str, ...]] = READER_COLLAPSE


def collapse_mechanisms(df: pd.DataFrame) -> pd.DataFrame:
    """Fold per-mechanism counts into the reader-facing three-way split."""
    out = df.copy()
    for target, sources in MECHANISM_COLLAPSE.items():
        out.loc[:, target] = sum(out[source] for source in sources)
    keep = ["level", "total", *MECHANISM_COLLAPSE]
    if "strategy" in out.columns:
        keep.insert(0, "strategy")
    return pd.DataFrame(out, columns=keep)


def build_mechanism_table(
    partition: pd.DataFrame,
    *,
    provenance: Provenance,
) -> Table:
    """Render the stable mechanism partition without recomputing its shares."""
    out = partition.copy()
    level_order = {"Test": 0, "Assertion": 1, "Generalization": 2}
    mechanism_order = {
        mechanism.key.value: index for index, mechanism in enumerate(MECHANISMS)
    }
    out.loc[:, "_level_order"] = out["level"].map(level_order)
    out.loc[:, "_mechanism_order"] = out["mechanism"].map(mechanism_order)
    out = out.sort_values(["_level_order", "_mechanism_order"]).drop(
        columns=["_level_order", "_mechanism_order"]
    )
    return Table(
        key="rq6_exclusion_mechanisms",
        df=out.reset_index(drop=True),
        columns=[
            ColumnSpec("Level", "level", kind=ValueKind.TEXT),
            ColumnSpec("Mechanism", "mechanism_label", kind=ValueKind.TEXT),
            ColumnSpec("Outcome", "reader_outcome", kind=ValueKind.TEXT),
            ColumnSpec(
                "Entities",
                "entity_count",
                kind=ValueKind.COUNT,
                align="r",
                share_source="share",
            ),
            ColumnSpec("Level total", "level_total", kind=ValueKind.COUNT, align="r"),
        ],
        caption=(
            "Included entities and exclusion mechanisms for "
            "{entity.variant.improved_c}, with shares of each entity-level population."
        ),
        label="tab:rq6-exclusion-mechanisms",
        group_by="level",
        row_key="row_key",
        provenance=provenance,
        full_width=True,
    )


_VARIANT_HEAD = {"ORIGINAL": 0, "INITIAL": 1, "SHARED": 2, "BASELINE": 3}
_VARIANT_GROUP = {"NAIVE": 4, "IMPROVED": 5}
_LEVEL_ORDER = {"Test": 0, "Assertion": 1, "Generalization": 2}
_FILTER_DISPLAY_NAMES = {
    "InheritedTestCase": "InheritedTest",
    "MockingFramework": "Mocking",
}
_FIRST_ROUND_TEST_FILTERS = frozenset({"NonPassingTest", "TestType"})


def filter_group_key(level: str, filter_name: str) -> int:
    """Group filter decisions by their entity level and test-filter round."""
    if level == "Test":
        return 0 if filter_name in _FIRST_ROUND_TEST_FILTERS else 1
    return {"Assertion": 2, "Generalization": 3}.get(level, 99)


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
        out.loc[:, f"{decision}_pct"] = out.apply(
            lambda row: share_value(row[decision], row["total"]), axis=1
        )
    out.loc[:, "_filter_group"] = out.apply(
        lambda row: filter_group_key(row["level"], row["filter"]), axis=1
    )
    out = out.sort_values(
        ["_filter_group", "reject", "filter"],
        ascending=[True, False, True],
    ).reset_index(drop=True)
    out.loc[:, "filter"] = out["filter"].replace(_FILTER_DISPLAY_NAMES)
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Filter Name", "filter"),
        ColumnSpec("Total", "total", kind=ValueKind.COUNT, align="r"),
        ColumnSpec(
            "Accept",
            "accept",
            kind=ValueKind.COUNT,
            align="r",
            share_source="accept_pct",
        ),
        ColumnSpec(
            "Defer",
            "defer",
            kind=ValueKind.COUNT,
            align="r",
            share_source="defer_pct",
            zero_is_absent=True,
        ),
        ColumnSpec(
            "Reject",
            "reject",
            kind=ValueKind.COUNT,
            align="r",
            share_source="reject_pct",
        ),
    ]
    return Table(
        key=key,
        df=out,
        columns=columns,
        caption=caption,
        label=label,
        group_by="_filter_group",
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
        out.loc[:, f"{outcome.column}_pct"] = out.apply(
            lambda row: share_value(row[outcome.column], row["total"]), axis=1
        )
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Total", "total", kind=ValueKind.COUNT, align="r"),
    ]
    for outcome in outcomes:
        group_header = (
            "Excluded" if outcome.column in {"filtering", "failures"} else None
        )
        columns.append(
            ColumnSpec(
                outcome.header,
                outcome.column,
                kind=ValueKind.COUNT,
                align="r",
                group_header=group_header,
                share_source=f"{outcome.column}_pct",
            )
        )
    group_by = None
    if include_strategy:
        columns = [
            ColumnSpec("Strategy", "strategy", kind=ValueKind.ENTITY),
            *columns,
        ]
        group_by = "level"
    out.loc[:, "_lvl"] = out["level"].map(lambda level: _LEVEL_ORDER.get(level, 99))
    sort_columns = ["_lvl"]
    if include_strategy:
        out.loc[:, "_var"] = out["strategy"].map(variant_sort_key)
        sort_columns.append("_var")
    out = (
        out.sort_values(sort_columns).drop(columns=sort_columns).reset_index(drop=True)
    )
    if include_strategy:
        out.loc[:, "strategy"] = out["strategy"].map(variant_ref)
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
