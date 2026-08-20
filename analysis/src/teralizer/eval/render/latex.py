"""Render an RQReport to paper artifacts: booktabs tables + a macros file."""

from __future__ import annotations

import re
from collections.abc import Sequence
from pathlib import Path

from pandas import isna

from teralizer.eval.format import COUNT_SHARE, render_value
from teralizer.eval.inputs import CorpusInputSnapshot
from teralizer.eval.macros import macro_name
from teralizer.eval.model import BuiltReport, ColumnSpec, RQReport, Table

_ALIGN = {"l": "l", "r": "r", "c": "c"}


def _cell(value: object, fmt: str) -> str:
    text = render_value(value, fmt)
    if fmt == "tex":
        return text
    if text == "—":
        # An absent value renders as an en dash, matching the thesis's other
        # tables. The formatter keeps the em dash as the renderer-agnostic
        # marker, so CSV exports stay stable.
        return "--"
    return text.replace("%", "\\%").replace("_", "\\_")


def _pad_to(text: str, width: int) -> str:
    """Reserve the width the widest share of a column occupies.

    Digits are tabular, so a zero reserves exactly one digit. Only the share needs
    this: it sits inside parentheses, which would otherwise shift row to row. The
    count beside it needs nothing, because its column is right-aligned already.
    """
    missing = width - len(text)
    if missing <= 0:
        return text
    return f"\\phantom{{{'0' * missing}}}{text}"


def _count_share_parts(table: Table, column: ColumnSpec) -> list[tuple[str, str]]:
    """Every (count, share) pair of one column, already formatted as digits."""
    assert column.share_source is not None
    return [
        (
            _cell(row[column.source], "count"),
            _cell(row[column.share_source], "pct1"),
        )
        for _, row in table.df.iterrows()
    ]


def _count_share_column(table: Table, column: ColumnSpec) -> list[str]:
    """Pair a count with its share, aligned down the column.

    Both parts align independently, because a wide count beside a narrow share
    would otherwise push the parenthesis out of line. A share of zero prints as
    a dash, which is how the thesis marks a decision a filter never takes.
    """
    parts = _count_share_parts(table, column)
    width = max((len(s) for _, s in parts), default=0)
    cells = []
    for (count, share), (_, row) in zip(parts, table.df.iterrows(), strict=True):
        if int(row[column.source]) == 0 and column.zero_is_absent:
            cells.append("--")
            continue
        cells.append(f"{count}\\; ({_pad_to(share, width)})")
    return cells


def _column_cells(table: Table, column: ColumnSpec) -> list[str]:
    """Render one column, which is the unit alignment is decided over."""
    if column.fmt == COUNT_SHARE:
        return _count_share_column(table, column)
    return [_cell(row[column.source], column.fmt) for _, row in table.df.iterrows()]


def _band_row(text: str, columns: int, label_width: str = "13.25em") -> str:
    """A row spanning the table that states the totals of the group beneath it.

    The label is set to a fixed width so the figures of every band line up down
    the table, which is what makes the bands readable as a column of totals.
    """
    label, _, rest = text.partition("\t")
    boxed = f"\\makebox[{label_width}][l]{{{label}}}"
    body = f"{boxed} {rest}" if rest else boxed
    return f"  \\multicolumn{{{columns}}}{{l}}{{\\textit{{{body}}}}} \\\\"


def _is_empty_group(value: object) -> bool:
    if value is None:
        return True
    if isinstance(value, str):
        return not value
    try:
        return bool(isna(value))
    except (TypeError, ValueError):
        return False


def _spanned_cells(
    labels: Sequence[str | None], align: str
) -> tuple[list[str], list[str]]:
    """Merge runs of columns sharing a label into one cell, and rule those spans.

    A run of two or more becomes a `\\multicolumn` underlined by a `\\cmidrule`; a
    lone column keeps a plain cell, so a label covering one column renders exactly
    as it did before spans existed. A column labelled `None` never joins a run --
    it spaces the row out and stays unruled.
    """
    cells: list[str] = []
    rules: list[str] = []
    start = 0
    while start < len(labels):
        label = labels[start]
        end = start + 1
        if label is not None:
            while end < len(labels) and labels[end] == label:
                end += 1
        span = end - start
        if label is None:
            cells.append("")
        elif span == 1:
            cells.append(label)
        else:
            cells.append(f"\\multicolumn{{{span}}}{{{align}}}{{{label}}}")
            rules.append(f"\\cmidrule(lr){{{start + 1}-{end}}}")
        start = end
    return cells, rules


def _group_header_rows(columns: Sequence[ColumnSpec], align: str) -> list[str]:
    cells, rules = _spanned_cells([c.group_header for c in columns], align)
    rows = ["  " + " & ".join(cells) + " \\\\"]
    if rules:
        rows.append("  " + " ".join(rules))
    return rows


def render_table(table: Table) -> str:
    cols = "".join(_ALIGN[c.align] for c in table.columns)
    command = "\\caption" if table.floating else "\\captionof{table}"
    short = "" if table.short_caption is None else f"[{table.short_caption}]"
    # The trailing comment keeps the line break out of the caption, which would
    # otherwise put a stray space before the label.
    end = "%" if table.short_caption else ""
    indent = "  " if table.floating else ""
    style = "\n".join(f"{indent}{line}" for line in table.body_style.splitlines())
    lines = [
        f"{indent}{command}{short}{{{table.caption}}}{end}",
        f"{indent}\\label{{{table.label}}}",
        style,
    ]
    if table.floating:
        lines.insert(
            0, "\\begin{table}" + (f"[{table.float_spec}]" if table.float_spec else "")
        )
    if table.latex_resize_to_width:
        lines.append("  \\resizebox{\\textwidth}{!}{%")
    header_rows: list[str] = []
    if any(c.group_header is not None for c in table.columns):
        header_rows += _group_header_rows(table.columns, table.group_header_align)
    if table.merge_equal_headers:
        # Where a label covers a pair of columns -- a value and its delta, say --
        # it sits on the leaf row itself. No rule: the group row above already
        # carries one, and a second would box the header in.
        leaf, _ = _spanned_cells(
            [c.header for c in table.columns], table.group_header_align
        )
    else:
        # A composite cell is wider than its header, so the header reads centred
        # over the pair rather than pinned to the right edge of the column.
        leaf = [
            f"\\multicolumn{{1}}{{c}}{{{c.header}}}"
            if c.fmt == COUNT_SHARE
            else c.header
            for c in table.columns
        ]
    header_rows.append("  " + " & ".join(leaf) + " \\\\")
    if table.full_width:
        # \extracolsep{\fill} spreads the slack between columns, so the table
        # occupies the text width instead of being scaled down to fit it.
        opening = (
            "  \\begin{tabular*}{\\textwidth}"
            f"{{@{{\\hspace{{\\tabcolsep}}\\extracolsep{{\\fill}}}}{cols}}}"
        )
    else:
        opening = f"  \\begin{{tabular}}{{{cols}}}"
    lines += [
        opening,
        "  \\toprule",
        *header_rows,
        "  \\midrule",
    ]
    # Alignment is a property of a column, so every column is rendered before any
    # row is assembled. A row-at-a-time loop cannot know how wide its neighbours
    # below will be.
    rendered = {c.source: _column_cells(table, c) for c in table.columns}
    prev_group = None
    has_previous_group = False
    for position, (_, row) in enumerate(table.df.iterrows()):
        indent = False
        if table.group_by is not None:
            group = row[table.group_by]
            if table.group_style == "label-row":
                if _is_empty_group(group):
                    has_previous_group = False
                else:
                    if not has_previous_group or group != prev_group:
                        if lines[-1] != "  \\midrule":
                            lines.append("  \\addlinespace")
                        lines.append("  " + _cell(group, "str") + " \\\\")
                    prev_group = group
                    has_previous_group = True
                    indent = True
            elif table.bands is not None:
                if group != prev_group:
                    if lines[-1] != "  \\midrule":
                        lines.append("  \\midrule")
                    band = table.bands.get(str(group))
                    if band is not None:
                        lines.append(_band_row(band, len(table.columns)))
                        lines.append("  \\midrule")
                prev_group = group
            else:
                if prev_group is not None and group != prev_group:
                    lines.append("  \\midrule")
                prev_group = group
        cells = [rendered[c.source][position] for c in table.columns]
        if indent:
            cells[0] = "\\qquad " + cells[0]
        lines.append("  " + " & ".join(cells) + " \\\\")
    if table.overall_band is not None:
        lines.append("  \\midrule")
        lines.append(_band_row(table.overall_band, len(table.columns)))
    lines += [
        "  \\bottomrule",
        "  \\end{tabular*}" if table.full_width else "  \\end{tabular}",
    ]
    if table.latex_resize_to_width:
        lines.append("  }")
    if table.floating:
        lines.append("\\end{table}")
    lines.append("")
    return "\n".join(lines)


def render_macros(report: RQReport) -> str:
    lines = [
        f"\\newcommand{{\\{macro_name(m.key)}}}{{{_cell(m.value, m.fmt)}}}"
        for m in report.metrics
    ]
    return "\n".join(lines) + ("\n" if lines else "")


_NEWCOMMAND = re.compile(r"\\newcommand\{\\(\w+)\}")


def write_macros(built: BuiltReport, build_dir: Path) -> Path:
    """Write this report's macros and rebuild the aggregate from all reports.

    Each report owns one file under `macros/`. `macros.tex` is derived by
    concatenation, so never write to it directly: a report that does will drop
    every other report's macros.
    """
    report = built.report
    database = next(
        snapshot.database
        for snapshot in built.inputs
        if isinstance(snapshot, CorpusInputSnapshot)
    )
    owned_dir = build_dir / "macros"
    owned_dir.mkdir(parents=True, exist_ok=True)
    header = f"% {report.rq} from {database}\n"
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


def render(built: BuiltReport, build_dir: Path) -> list[Path]:
    report = built.report
    build_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for table in report.tables():
        out = build_dir / f"{table.key}.tex"
        out.write_text(render_table(table), encoding="utf-8")
        written.append(out)
    written.append(write_macros(built, build_dir))
    return written
