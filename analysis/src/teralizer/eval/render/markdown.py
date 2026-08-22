"""Render an RQReport to a human-facing Markdown report."""

from __future__ import annotations

import re
from decimal import Decimal
from pathlib import Path

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.entities import EntityRef, render as render_entity
from teralizer.eval.entities import substitute as substitute_entities
from teralizer.eval.format import is_missing, render_metric
from teralizer.eval.inputs import CorpusInputSnapshot
from teralizer.eval.model import (
    BandSummary,
    BuiltReport,
    Figure,
    Prose,
    Table,
    ValueKind,
)

_PLACEHOLDER = re.compile(r"\{([a-zA-Z0-9_.]+)\}")


def _decimal(value: object) -> str:
    if not isinstance(value, Decimal):
        raise TypeError(f"decimal value must use Decimal, got {type(value).__name__}")
    return format(value, "f")


def _runtime(value: object) -> str:
    if not isinstance(value, Decimal):
        raise TypeError(f"runtime value must use Decimal, got {type(value).__name__}")
    total = int(value.to_integral_value())
    hours, remainder = divmod(total, 3600)
    minutes, seconds = divmod(remainder, 60)
    parts: list[str] = []
    if hours:
        parts.append(f"{hours}h")
    if hours or minutes:
        parts.append(f"{minutes}m")
    parts.append(f"{seconds}s")
    return " ".join(parts)


def _value(value: object, kind: ValueKind) -> str:
    if is_missing(value):
        return "—"
    if kind is ValueKind.COUNT:
        return f"{int(value):,}"
    if kind is ValueKind.SHARE:
        return f"{Decimal(_decimal(value)) * 100:.1f}%"
    if kind is ValueKind.PERCENT:
        return f"{_decimal(value)}%"
    if kind is ValueKind.PERCENT_DELTA:
        return f"{Decimal(_decimal(value)):+f}%"
    if kind is ValueKind.DECIMAL:
        return _decimal(value)
    if kind is ValueKind.DELTA:
        return f"{Decimal(_decimal(value)):+,f}"
    if kind is ValueKind.RUNTIME:
        return _runtime(value)
    if kind is ValueKind.IDENTIFIER:
        return f"`{value}`"
    if kind is ValueKind.TEXT:
        return substitute_entities(str(value), "markdown")
    if kind is ValueKind.ENTITY:
        if not isinstance(value, EntityRef):
            raise TypeError(
                f"entity value must use EntityRef, got {type(value).__name__}"
            )
        return render_entity(value, "markdown")
    raise AssertionError(f"unsupported Markdown value kind: {kind}")


def _substitute(text: str, metrics: dict) -> str:
    text = substitute_entities(text, "markdown")

    def repl(match: re.Match[str]) -> str:
        key = match.group(1)
        if key not in metrics:
            return match.group(0)
        metric = metrics[key]
        return render_metric(metric.value, metric.fmt)

    return _PLACEHOLDER.sub(repl, text)


def _source_link(provenance, repo_url: str) -> str:
    return f"\n\nsource: [`{provenance.qualname}`]({provenance.source_url(repo_url)})"


def _md_cell(row, column, *, paired_delta: bool = False) -> str:
    if column.zero_is_absent and not is_missing(row[column.source]):
        if Decimal(str(row[column.source])) == 0:
            return "—"
    if column.share_source is None:
        value = _value(row[column.source], column.kind)
        return f"({value})" if paired_delta and value != "—" else value
    count = _value(row[column.source], ValueKind.COUNT)
    share = _value(row[column.share_source], ValueKind.SHARE)
    return f"{count} ({share})"


def _band_text(band: BandSummary) -> str:
    rate = band.inclusions / band.entering if band.entering else 0
    return (
        f"{band.title}: {band.entering:,} projects, "
        f"{band.inclusions:,} inclusions, {band.exclusions:,} exclusions, "
        f"{rate:.1%} inclusion rate"
    )


def _table_md(table: Table, repo_url: str) -> str:
    headers = [
        substitute_entities(column.header, "markdown") for column in table.columns
    ]
    if table.ordinal_header is not None:
        headers.insert(0, table.ordinal_header)
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    previous_group = None
    for position, (_, row) in enumerate(table.df.iterrows()):
        if table.bands is not None and table.group_by is not None:
            group = str(row[table.group_by])
            if group != previous_group and group in table.bands:
                band_cells = [
                    _band_text(table.bands[group]),
                    *([""] * (len(headers) - 1)),
                ]
                lines.append("| " + " | ".join(band_cells) + " |")
            previous_group = group
        cells = [
            _md_cell(
                row,
                column,
                paired_delta=(
                    table.merge_equal_headers
                    and position_in_table > 0
                    and column.kind is ValueKind.DELTA
                    and column.header == table.columns[position_in_table - 1].header
                ),
            )
            for position_in_table, column in enumerate(table.columns)
        ]
        if table.ordinal_header is not None:
            cells.insert(0, str(position + 1))
        lines.append("| " + " | ".join(cells) + " |")
    if table.overall_band is not None:
        band_cells = [_band_text(table.overall_band), *([""] * (len(headers) - 1))]
        lines.append("| " + " | ".join(band_cells) + " |")
    caption = substitute_entities(table.caption, "markdown")
    block = f"**{caption}**\n\n" + "\n".join(lines)
    if table.note:
        block += f"\n\n_{substitute_entities(table.note, 'markdown')}_"
    if table.provenance:
        block += _source_link(table.provenance, repo_url)
    return block


def _figure_md(fig: Figure, rq: str, repo_url: str) -> str:
    caption = substitute_entities(fig.caption, "markdown")
    block = f"![{caption}](figures/{rq}/{fig.key}.png)\n\n**{caption}**"
    if fig.provenance:
        block += _source_link(fig.provenance, repo_url)
    return block


def render_str(built: BuiltReport, *, repo_url: str) -> str:
    report = built.report
    database = next(
        (
            snapshot.database
            for snapshot in built.inputs
            if isinstance(snapshot, CorpusInputSnapshot)
        ),
        None,
    )
    metrics = report.metric_map()
    parts = [f"# {report.title}"]
    if database is not None:
        parts.append(f"_Source database: `{database}`._")
    for section in report.sections:
        parts.append(f"## {section.title}")
        for block in section.blocks:
            if isinstance(block, Prose):
                parts.append(_substitute(block.text, metrics))
            elif isinstance(block, Table):
                parts.append(_table_md(block, repo_url))
            elif isinstance(block, Figure):
                parts.append(_figure_md(block, report.rq, repo_url))
    output = "\n\n".join(parts) + "\n"
    if "\\" in output:
        raise ValueError("rendered markdown contains a backslash")
    return output


def render(
    built: BuiltReport, reports_dir: Path, *, staging_root: Path, repo_url: str
) -> ArtifactSet:
    report = built.report
    reports_dir.mkdir(parents=True, exist_ok=True)
    out = reports_dir / f"{report.rq}.md"
    out.write_text(render_str(built, repo_url=repo_url), encoding="utf-8")
    artifacts = ArtifactSet(staging_root)
    artifacts.add(
        RenderedArtifact(ArtifactId(RenderTarget.MARKDOWN, report.rq), out, report.rq)
    )
    return artifacts
