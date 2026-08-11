"""Stable CSV export for render-agnostic report tables."""

from __future__ import annotations

import csv
from pathlib import Path

from teralizer.eval.format import render_value
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
        sources = [column.csv_source or column.source for column in table.columns]
        writer.writerow(sources)
        for _, row in table.df.iterrows():
            writer.writerow(
                [
                    render_value(row[source], column.fmt)
                    for source, column in zip(sources, table.columns, strict=True)
                ]
            )
    return path


def render(report: RQReport, output_dir: Path) -> list[Path]:
    """Write all report tables and return their paths."""
    return [render_table(table, output_dir) for table in report.tables()]
