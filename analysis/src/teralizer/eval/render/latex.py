"""Render an RQReport to paper artifacts: booktabs tables + a macros file."""

from __future__ import annotations

from pathlib import Path

from teralizer.eval.format import render_value
from teralizer.eval.macros import macro_name
from teralizer.eval.model import RQReport, Table

_ALIGN = {"l": "l", "r": "r", "c": "c"}


def _cell(value: object, fmt: str) -> str:
    return render_value(value, fmt).replace("%", "\\%").replace("_", "\\_")


def render_table(table: Table) -> str:
    cols = "".join(_ALIGN[c.align] for c in table.columns)
    lines = [
        "\\begin{table}",
        f"  \\caption{{{table.caption}}}",
        f"  \\label{{{table.label}}}",
        "  \\centering",
        f"  \\begin{{tabular}}{{{cols}}}",
        "  \\toprule",
        "  " + " & ".join(c.header for c in table.columns) + " \\\\",
        "  \\midrule",
    ]
    prev_group = None
    for _, row in table.df.iterrows():
        if table.group_by is not None:
            group = row[table.group_by]
            if prev_group is not None and group != prev_group:
                lines.append("  \\midrule")
            prev_group = group
        lines.append(
            "  "
            + " & ".join(_cell(row[c.source], c.fmt) for c in table.columns)
            + " \\\\"
        )
    lines += ["  \\bottomrule", "  \\end{tabular}", "\\end{table}", ""]
    return "\n".join(lines)


def render_macros(report: RQReport) -> str:
    lines = [
        f"\\newcommand{{\\{macro_name(m.key)}}}{{{_cell(m.value, m.fmt)}}}"
        for m in report.metrics
    ]
    return "\n".join(lines) + ("\n" if lines else "")


def render(report: RQReport, build_dir: Path) -> list[Path]:
    build_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for table in report.tables():
        out = build_dir / f"{table.key}.tex"
        out.write_text(render_table(table), encoding="utf-8")
        written.append(out)
    macros = build_dir / "macros.tex"
    macros.write_text(render_macros(report), encoding="utf-8")
    written.append(macros)
    return written
