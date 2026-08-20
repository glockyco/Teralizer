"""Stable CSV export for render-agnostic report tables."""

from __future__ import annotations

import csv
from pathlib import Path

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.format import COUNT_SHARE, render_value
from teralizer.eval.model import RQReport, Table


def render_table(table: Table, output_dir: Path) -> Path:
    """Write one table using source-column names as the stable CSV schema."""
    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / f"{table.key}.csv"
    with path.open("w", encoding="utf-8", newline="") as stream:
        # csv defaults to the RFC 4180 CRLF terminator. Every consumer of these
        # files -- git, the thesis, the plotting macros -- is line-oriented Unix
        # text, so the export matches the repository instead of the RFC.
        writer = csv.writer(stream, lineterminator="\n")
        # A count and its share are two fields here. Pairing them into one string
        # would hand a consumer a value it has to parse apart again.
        header: list[str] = []
        for column in table.columns:
            header.append(column.csv_source or column.source)
            if column.fmt == COUNT_SHARE:
                assert column.share_source is not None
                header.append(column.share_source)
        writer.writerow(header)
        for _, row in table.df.iterrows():
            fields: list[str] = []
            for column in table.columns:
                source = column.csv_source or column.source
                if column.fmt == COUNT_SHARE:
                    fields.append(render_value(row[source], "count"))
                    assert column.share_source is not None
                    fields.append(render_value(row[column.share_source], "pct1"))
                else:
                    fields.append(render_value(row[source], column.fmt))
            writer.writerow(fields)
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
