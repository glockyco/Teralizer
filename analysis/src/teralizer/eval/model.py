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
class MetricPopulation:
    """Semantic population measured by a metric within one declared report input."""

    key: str
    entity_level: str
    input_role: str

    def is_compatible_with(self, other: "MetricPopulation") -> bool:
        return (
            self.entity_level == other.entity_level
            and self.input_role == other.input_role
        )


@dataclass(frozen=True)
class Metric:
    """A single named scalar the report cites. `key` is semantic and stable
    (`realworld.eligible_projects_pct`), never keyed on an RQ number."""

    key: str
    value: float | int | str
    fmt: str = "int"
    provenance: "Provenance | None" = None
    kind: "ValueKind | None" = None
    population: MetricPopulation | None = None
    numerator_key: str | None = None
    denominator_key: str | None = None


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
        if self.latex_resize_to_width and self.full_width:
            raise ValueError("resize-to-width and full-width are mutually exclusive")
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

    def validate_metric_relations(self, *, require_metadata: bool = False) -> None:
        metrics = self.metric_map()
        for metric in metrics.values():
            if require_metadata:
                missing = [
                    name
                    for name, value in (
                        ("kind", metric.kind),
                        ("population", metric.population),
                        ("provenance", metric.provenance),
                    )
                    if value is None
                ]
                if missing:
                    raise ValueError(f"metric {metric.key} lacks {', '.join(missing)}")
            if metric.denominator_key is None:
                if metric.numerator_key is not None:
                    raise ValueError(
                        f"metric {metric.key} has a numerator without a denominator"
                    )
                continue
            if metric.numerator_key is None:
                raise ValueError(f"rate metric {metric.key} lacks a numerator key")
            if metric.kind not in {ValueKind.SHARE, ValueKind.PERCENT}:
                raise ValueError(
                    f"metric {metric.key} has a denominator but is not a rate"
                )
            try:
                numerator = metrics[metric.numerator_key]
                denominator = metrics[metric.denominator_key]
            except KeyError as error:
                raise ValueError(
                    f"metric {metric.key} references missing operand {error.args[0]}"
                ) from error
            if metric.population != numerator.population:
                raise ValueError(
                    f"metric {metric.key} population differs from its numerator"
                )
            if (
                metric.population is None
                or denominator.population is None
                or not metric.population.is_compatible_with(denominator.population)
            ):
                raise ValueError(
                    f"metric {metric.key} has incompatible denominator "
                    f"{metric.denominator_key}"
                )
            denominator_value = float(denominator.value)
            if denominator_value == 0:
                raise ValueError(f"metric {metric.key} has a zero denominator")
            expected = float(numerator.value) / denominator_value
            if abs(float(metric.value) - expected) > 1e-12:
                raise ValueError(
                    f"metric {metric.key} does not equal its declared operands"
                )

    def tables(self) -> list[Table]:
        return [b for s in self.sections for b in s.blocks if isinstance(b, Table)]

    def figures(self) -> list[Figure]:
        return [b for s in self.sections for b in s.blocks if isinstance(b, Figure)]


@dataclass(frozen=True)
class BuiltReport:
    """A renderable report and the runner-captured identities of all its inputs."""

    report: RQReport
    inputs: tuple["InputSnapshot", ...]
