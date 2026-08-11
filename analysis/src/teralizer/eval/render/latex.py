"""Render an RQReport to paper artifacts: booktabs tables + a macros file."""

from __future__ import annotations

import re
from collections.abc import Sequence
from pathlib import Path

from pandas import isna

from teralizer.eval.format import render_value
from teralizer.eval.macros import macro_name
from teralizer.eval.model import ColumnSpec, RQReport, Table

_ALIGN = {"l": "l", "r": "r", "c": "c"}


def _cell(value: object, fmt: str) -> str:
    return render_value(value, fmt).replace("%", "\\%").replace("_", "\\_")


def _is_empty_group(value: object) -> bool:
    if value is None:
        return True
    if isinstance(value, str):
        return not value
    try:
        return bool(isna(value))
    except (TypeError, ValueError):
        return False


def _group_header_rows(columns: Sequence[ColumnSpec]) -> list[str]:
    """Header cells for the group row, spanning runs of columns that share a header.

    A run of two or more becomes one `\\multicolumn` cell underlined by a
    `\\cmidrule`; a lone column keeps a plain cell so single-column groups render
    exactly as they did before spans existed. Columns without a group header never
    join a run -- they space the row out and stay unruled.
    """
    cells: list[str] = []
    rules: list[str] = []
    start = 0
    while start < len(columns):
        header = columns[start].group_header
        end = start + 1
        if header is not None:
            while end < len(columns) and columns[end].group_header == header:
                end += 1
        span = end - start
        if header is None:
            cells.append("")
        elif span == 1:
            cells.append(header)
        else:
            cells.append(f"\\multicolumn{{{span}}}{{c}}{{{header}}}")
            rules.append(f"\\cmidrule(lr){{{start + 1}-{end}}}")
        start = end
    rows = ["  " + " & ".join(cells) + " \\\\"]
    if rules:
        rows.append("  " + " ".join(rules))
    return rows


def render_table(table: Table) -> str:
    cols = "".join(_ALIGN[c.align] for c in table.columns)
    lines = [
        "\\begin{table}",
        f"  \\caption{{{table.caption}}}",
        f"  \\label{{{table.label}}}",
        "  \\centering",
    ]
    if table.latex_resize_to_width:
        lines.append("  \\resizebox{\\textwidth}{!}{%")
    header_rows: list[str] = []
    if any(c.group_header is not None for c in table.columns):
        header_rows += _group_header_rows(table.columns)
    header_rows.append("  " + " & ".join(c.header for c in table.columns) + " \\\\")
    lines += [
        f"  \\begin{{tabular}}{{{cols}}}",
        "  \\toprule",
        *header_rows,
        "  \\midrule",
    ]
    prev_group = None
    has_previous_group = False
    for _, row in table.df.iterrows():
        indent = False
        if table.group_by is not None:
            group = row[table.group_by]
            if table.group_style == "label-row":
                if _is_empty_group(group):
                    has_previous_group = False
                else:
                    if not has_previous_group or group != prev_group:
                        lines.append("  " + _cell(group, "str") + " \\\\")
                    prev_group = group
                    has_previous_group = True
                    indent = True
            else:
                if prev_group is not None and group != prev_group:
                    lines.append("  \\midrule")
                prev_group = group
        cells = [_cell(row[c.source], c.fmt) for c in table.columns]
        if indent:
            cells[0] = "\\quad " + cells[0]
        lines.append("  " + " & ".join(cells) + " \\\\")
    lines += ["  \\bottomrule", "  \\end{tabular}"]
    if table.latex_resize_to_width:
        lines.append("  }")
    lines += ["\\end{table}", ""]
    return "\n".join(lines)


def render_macros(report: RQReport) -> str:
    lines = [
        f"\\newcommand{{\\{macro_name(m.key)}}}{{{_cell(m.value, m.fmt)}}}"
        for m in report.metrics
    ]
    return "\n".join(lines) + ("\n" if lines else "")


_NEWCOMMAND = re.compile(r"\\newcommand\{\\(\w+)\}")


def write_macros(report: RQReport, build_dir: Path) -> Path:
    """Write this report's macros and rebuild the aggregate from all reports.

    Each report owns one file under `macros/`. `macros.tex` is derived by
    concatenation, so never write to it directly: a report that does will drop
    every other report's macros.
    """
    owned_dir = build_dir / "macros"
    owned_dir.mkdir(parents=True, exist_ok=True)
    header = f"% {report.rq} from {report.db}\n"
    (owned_dir / f"{report.rq}.tex").write_text(
        header + render_macros(report), encoding="utf-8"
    )

    bodies = {
        path.stem: path.read_text(encoding="utf-8")
        for path in sorted(owned_dir.glob("*.tex"))
    }
    owners: dict[str, str] = {}
    for rq, body in bodies.items():
        for name in _NEWCOMMAND.findall(body):
            if name in owners:
                raise RuntimeError(
                    f"macro \\{name} is emitted by both {owners[name]} and {rq}. "
                    "Two reports cannot own the same metric key."
                )
            owners[name] = rq

    aggregate = build_dir / "macros.tex"
    aggregate.write_text(
        "% Generated by teralizer.eval. Do not edit.\n"
        f"% Reports included: {', '.join(bodies)}\n\n" + "\n".join(bodies.values()),
        encoding="utf-8",
    )
    return aggregate


def render(report: RQReport, build_dir: Path) -> list[Path]:
    build_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for table in report.tables():
        out = build_dir / f"{table.key}.tex"
        out.write_text(render_table(table), encoding="utf-8")
        written.append(out)
    written.append(write_macros(report, build_dir))
    return written
