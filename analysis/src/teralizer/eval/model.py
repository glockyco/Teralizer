"""Render-agnostic result types for the eval reports.

A report is computed once into an RQReport and rendered independently by each
renderer. Nothing here imports a renderer, pandas styling, or LaTeX.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import pandas as pd
    from matplotlib.axes import Axes

    from teralizer.eval.provenance import Provenance


@dataclass(frozen=True)
class Metric:
    """A single named scalar the report cites. `key` is semantic and stable
    (`realworld.eligible_projects_pct`), never keyed on an RQ number."""

    key: str
    value: float | int | str
    fmt: str = "int"
    provenance: "Provenance | None" = None


@dataclass(frozen=True)
class ColumnSpec:
    """One rendered table column. Formatting is defined here once and reused by
    every renderer, killing the table/CSV duplication of the old modules."""

    header: str
    source: str  # source DataFrame column name
    fmt: str = "str"
    align: str = "l"  # l | r | c
    group_header: str | None = None
    # Where LaTeX grouping abbreviates a cell -- a label row lifting the shared
    # prefix out of every member -- the rectangular CSV still owes readers the
    # qualified value. Naming it here keeps the export unambiguous.
    csv_source: str | None = None


@dataclass(frozen=True)
class Table:
    key: str
    df: "pd.DataFrame"
    columns: list[ColumnSpec]
    caption: str
    label: str
    group_by: str | None = None  # column that drives midrules / section splits
    group_style: str = "midrule"
    latex_resize_to_width: bool = False
    # A consuming document sets its own house style. These carry the parts of it
    # that the generator must emit itself, because they sit inside the float it
    # writes; everything else stays in the document's preamble.
    short_caption: str | None = None  # \caption[short]{caption} for the list of tables
    body_style: str = "\\centering"  # e.g. a document's own \tabstyle macro
    float_spec: str | None = None  # placement, e.g. H or tbp
    full_width: bool = False  # tabular* stretched to \textwidth
    group_header_align: str = "c"  # alignment of spanning group headers
    note: str | None = None
    provenance: "Provenance | None" = None


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
    """Markdown-flavored text. `{metric.key}` placeholders are substituted by the
    markdown renderer; the LaTeX track ignores Prose (Plan 2)."""

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
    db: str
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
