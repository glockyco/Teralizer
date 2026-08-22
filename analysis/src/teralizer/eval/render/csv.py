"""Stable CSV export for render-agnostic report tables."""

from __future__ import annotations

import csv
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
from teralizer.eval.format import is_missing
from teralizer.eval.model import RQReport, Table, ValueKind


def _decimal(value: object) -> str:
    if not isinstance(value, Decimal):
        raise TypeError(f"decimal value must use Decimal, got {type(value).__name__}")
    return format(value, "f")


def _value(value: object, kind: ValueKind) -> str:
    if is_missing(value):
        return ""
    if kind is ValueKind.COUNT:
        return str(int(value))
    if kind in {
        ValueKind.SHARE,
        ValueKind.PERCENT,
        ValueKind.PERCENT_DELTA,
    }:
        return _decimal(value)
    if kind in {ValueKind.DECIMAL, ValueKind.DELTA}:
        return _decimal(value)
    if kind is ValueKind.RUNTIME:
        return _decimal(value)
    if kind is ValueKind.IDENTIFIER:
        return str(value)
    if kind is ValueKind.TEXT:
        return substitute_entities(str(value), "csv")
    if kind is ValueKind.ENTITY:
        if not isinstance(value, EntityRef):
            raise TypeError(
                f"entity value must use EntityRef, got {type(value).__name__}"
            )
        return render_entity(value, "csv")
    raise AssertionError(f"unsupported CSV value kind: {kind}")


def render_table(table: Table, output_dir: Path) -> Path:
    """Write one table with stable source-column names."""
    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / f"{table.key}.csv"
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        header: list[str] = []
        value_columns: list[tuple[str, ValueKind]] = []
        visible_sources = {
            source
            for column in table.columns
            for source in (column.source, column.share_source)
            if source is not None
        }
        if table.row_key is not None and table.row_key not in visible_sources:
            header.append(table.row_key)
            value_columns.append((table.row_key, ValueKind.IDENTIFIER))
        for column in table.columns:
            header.append(column.source)
            value_columns.append((column.source, column.kind))
            if column.share_source is not None:
                header.append(column.share_source)
                value_columns.append((column.share_source, ValueKind.SHARE))
        writer.writerow(header)
        for _, row in table.df.iterrows():
            writer.writerow(
                [_value(row[source], kind) for source, kind in value_columns]
            )
    return path


def render(report: RQReport, output_dir: Path, *, staging_root: Path) -> ArtifactSet:
    """Write all report tables and return their typed artifacts."""
    artifacts = ArtifactSet(staging_root)
    for table in report.tables():
        path = render_table(table, output_dir)
        artifacts.add(
            RenderedArtifact(ArtifactId(RenderTarget.CSV, table.key), path, report.rq)
        )
    return artifacts
