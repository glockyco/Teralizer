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
        writer = csv.writer(stream)
        writer.writerow([column.source for column in table.columns])
        for _, row in table.df.iterrows():
            writer.writerow(
                [
                    render_value(row[column.source], column.fmt)
                    for column in table.columns
                ]
            )
    return path


def render(report: RQReport, output_dir: Path) -> list[Path]:
    """Write all report tables and return their paths."""
    return [render_table(table, output_dir) for table in report.tables()]
