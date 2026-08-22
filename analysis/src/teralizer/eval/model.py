"""Render-agnostic result types for the eval reports.

A report is computed once into an RQReport and rendered independently by each
renderer. Nothing here imports a renderer, pandas styling, or LaTeX.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from decimal import Decimal
from enum import StrEnum
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import pandas as pd
    from matplotlib.axes import Axes

    from teralizer.eval.inputs import InputSnapshot
    from teralizer.eval.provenance import Provenance


@dataclass(frozen=True)
class Metric:
    """A single named scalar the report cites. `key` is semantic and stable
    (`realworld.eligible_projects_pct`), never keyed on an RQ number."""

    key: str
    value: float | int | str
    fmt: str = "int"
    provenance: "Provenance | None" = None


def decimal_value(value: object, places: int) -> Decimal:
    """Return a numeric value with stable significant precision."""
    return Decimal(format(value, f".{places}f"))


def share_value(numerator: object, denominator: object) -> Decimal:
    """Return an exact ratio for two integer counts."""
    return Decimal(int(numerator)) / Decimal(int(denominator))


class ValueKind(StrEnum):
    COUNT = "count"
    SHARE = "share"
    PERCENT = "percent"
    PERCENT_DELTA = "percent_delta"
    DECIMAL = "decimal"
    DELTA = "delta"
    RUNTIME = "runtime"
    IDENTIFIER = "identifier"
    TEXT = "text"
    ENTITY = "entity"


@dataclass(frozen=True)
class ColumnSpec:
    """One table column: semantic values only, never target presentation."""

    header: str
    source: str  # source DataFrame column name
    kind: ValueKind = ValueKind.TEXT
    align: str = "l"  # l | r | c
    group_header: str | None = None
    # A count and its share stay as two values in the frame. Human targets
    # compose one cell. CSV exports both numeric fields.
    share_source: str | None = None
    # A decision a filter never takes reads as a dash rather than a zero share.
    zero_is_absent: bool = False

    def __post_init__(self) -> None:
        if self.share_source is not None and self.kind is not ValueKind.COUNT:
            raise ValueError("share_source requires a count column")


@dataclass(frozen=True)
class BandSummary:
    title: str
    entering: int
    inclusions: int
    exclusions: int


@dataclass(frozen=True)
class Table:
    key: str
    df: "pd.DataFrame"
    columns: list[ColumnSpec]
    caption: str
    label: str
    group_by: str | None = None  # column that drives midrules / section splits
    group_style: str = "midrule"
    row_key: str | None = None
    ordinal_header: str | None = None
    # A band is a row spanning every column that states the totals of the group
    # beneath it, keyed by that group's value. `overall_band` closes the table.
    # The report supplies the text, because only it knows what the group totals
    # mean; the renderer decides how a spanning row is typeset.
    bands: dict[str, BandSummary] | None = None
    overall_band: BandSummary | None = None
    latex_resize_to_width: bool = False
    # A consuming document sets its own house style. These carry the parts of it
    # that the generator must emit itself, because they sit inside the float it
    # writes; everything else stays in the document's preamble.
    short_caption: str | None = None  # \caption[short]{caption} for the list of tables
    body_style: str = "\\centering"  # a document's own style macros, one per line
    float_spec: str | None = None  # placement, e.g. H or tbp
    # A table the document places itself -- side by side in minipages, say --
    # cannot open its own float, and captions itself with \captionof instead.
    floating: bool = True
    full_width: bool = False  # tabular* stretched to \textwidth
    group_header_align: str = "c"  # alignment of spanning group headers
    # Opt-in, because two columns may legitimately carry the same header without
    # one label being meant to cover both.
    merge_equal_headers: bool = False  # adjacent equal headers span as one cell
    note: str | None = None
    provenance: "Provenance | None" = None

    def __post_init__(self) -> None:
        if self.row_key is None:
            return
        if self.row_key not in self.df:
            raise ValueError(f"row key column is missing: {self.row_key}")
        duplicate = self.df[self.row_key].duplicated(keep=False)
        if duplicate.any():
            keys = self.df.loc[duplicate, self.row_key].tolist()
            raise ValueError(f"row keys must be unique: {keys}")


@dataclass(frozen=True)
class Figure:
    key: str
    build: "Callable[[Axes], None]"  # draws onto a single Axes
    caption: str
    label: str
    data: "pd.DataFrame | None" = None  # underlying data, for CSV export + provenance
    provenance: "Provenance | None" = None


@dataclass(frozen=True)
class Prose:
    """Markdown text with metric and ``{entity.<key>}`` placeholders.

    The Markdown renderer substitutes both placeholder types. The LaTeX track
    ignores prose because the consuming document owns its narrative text.
    """

    text: str


Block = Prose | Table | Figure


@dataclass(frozen=True)
class Section:
    title: str
    blocks: list[Block]


@dataclass(frozen=True)
class RQReport:
    rq: str  # "rq6"
    title: str
    sections: list[Section]
    metrics: list[Metric] = field(default_factory=list)

    def metric_map(self) -> dict[str, Metric]:
        out: dict[str, Metric] = {}
        for m in self.metrics:
            if m.key in out:
                raise ValueError(f"duplicate metric key: {m.key}")
            out[m.key] = m
        return out

    def metric(self, key: str) -> Metric:
        return self.metric_map()[key]

    def tables(self) -> list[Table]:
        return [b for s in self.sections for b in s.blocks if isinstance(b, Table)]

    def figures(self) -> list[Figure]:
        return [b for s in self.sections for b in s.blocks if isinstance(b, Figure)]


@dataclass(frozen=True)
class BuiltReport:
    """A renderable report and the runner-captured identities of all its inputs."""

    report: RQReport
    inputs: tuple["InputSnapshot", ...]
