"""Render an RQReport to paper artifacts: booktabs tables + a macros file."""

from __future__ import annotations

import re
from collections.abc import Sequence
from decimal import Decimal
from pathlib import Path

from pandas import isna

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
    RunAggregate,
)
from teralizer.eval.entities import EntityRef, render as render_entity
from teralizer.eval.entities import substitute as substitute_entities
from teralizer.eval.format import is_missing, render_metric
from teralizer.eval.inputs import CorpusInputSnapshot
from teralizer.eval.macros import macro_name
from teralizer.eval.model import (
    BandSummary,
    BuiltReport,
    ColumnSpec,
    RQReport,
    Table,
    ValueKind,
)

_ALIGN = {"l": "l", "r": "r", "c": "c"}


def _decimal(value: object) -> str:
    if not isinstance(value, Decimal):
        raise TypeError(f"decimal value must use Decimal, got {type(value).__name__}")
    return format(value, "f")


def _escape(text: str) -> str:
    return text.replace("%", "\\%").replace("_", "\\_").replace("#", "\\#")


def _text(text: str) -> str:
    return _escape(substitute_entities(text, "latex"))


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
        return "--"
    if kind is ValueKind.COUNT:
        return f"{int(value):,}"
    if kind is ValueKind.SHARE:
        return f"{Decimal(_decimal(value)) * 100:.1f}\\%"
    if kind is ValueKind.PERCENT:
        return f"{_decimal(value)}\\%"
    if kind is ValueKind.PERCENT_DELTA:
        return f"{Decimal(_decimal(value)):+f}\\%"
    if kind is ValueKind.DECIMAL:
        return _decimal(value)
    if kind is ValueKind.DELTA:
        return f"{Decimal(_decimal(value)):+,f}"
    if kind is ValueKind.RUNTIME:
        return _runtime(value)
    if kind is ValueKind.IDENTIFIER:
        return f"\\texttt{{{_escape(str(value))}}}"
    if kind is ValueKind.TEXT:
        return _text(str(value))
    if kind is ValueKind.ENTITY:
        if not isinstance(value, EntityRef):
            raise TypeError(
                f"entity value must use EntityRef, got {type(value).__name__}"
            )
        return render_entity(value, "latex")
    raise AssertionError(f"unsupported LaTeX value kind: {kind}")


def _metric_cell(value: object, fmt: str) -> str:
    text = render_metric(value, fmt)
    if text == "—":
        return "--"
    return _escape(text)


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
            _value(row[column.source], ValueKind.COUNT),
            _value(row[column.share_source], ValueKind.SHARE),
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
    if column.share_source is not None:
        return _count_share_column(table, column)
    return [
        "--"
        if column.zero_is_absent
        and not is_missing(row[column.source])
        and Decimal(str(row[column.source])) == 0
        else _value(row[column.source], column.kind)
        for _, row in table.df.iterrows()
    ]


def _band_widths(table: Table) -> dict[str, int]:
    summaries = list((table.bands or {}).values())
    if table.overall_band is not None:
        summaries.append(table.overall_band)
    return {
        "entering": max(len(f"{band.entering:,}") for band in summaries),
        "inclusions": max(len(f"{band.inclusions:,}") for band in summaries),
        "exclusions": max(len(f"{band.exclusions:,}") for band in summaries),
        "rate": max(
            len(f"{band.inclusions / band.entering:.1%}" if band.entering else "0.0%")
            for band in summaries
        ),
    }


def _band_row(
    band: BandSummary,
    columns: int,
    widths: dict[str, int],
    label_width: str = "13.25em",
) -> str:
    """Render one typed band as a table-spanning LaTeX row."""

    def pad(value: str, key: str) -> str:
        missing = widths[key] - len(value)
        return f"\\phantom{{{'0' * missing}}}{value}" if missing > 0 else value

    rate = f"{band.inclusions / band.entering:.1%}" if band.entering else "0.0%"
    fields = [
        f"{pad(f'{band.entering:,}', 'entering')} projects",
        f"{pad(f'{band.inclusions:,}', 'inclusions')} inclusions",
        f"{pad(f'{band.exclusions:,}', 'exclusions')} exclusions",
        f"{pad(rate, 'rate').replace('%', chr(92) + '%')} inclusion rate",
    ]
    label = f"{_text(band.title)}:"
    boxed = f"\\makebox[{label_width}][l]{{{label}}}"
    body = f"{boxed} " + "\\enspace{}".join(fields)
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
    cells, rules = _spanned_cells(
        [
            _text(c.group_header) if c.group_header is not None else None
            for c in columns
        ],
        align,
    )
    rows = ["  " + " & ".join(cells) + " \\\\"]
    if rules:
        rows.append("  " + " ".join(rules))
    return rows


def render_table(table: Table) -> str:
    columns = table.columns
    if table.ordinal_header is not None:
        columns = [
            ColumnSpec(table.ordinal_header, "", ValueKind.COUNT, "r"),
            *columns,
        ]
    cols = "".join(_ALIGN[c.align] for c in columns)
    command = "\\caption" if table.floating else "\\captionof{table}"
    short = "" if table.short_caption is None else f"[{_text(table.short_caption)}]"
    # The trailing comment keeps the line break out of the caption, which would
    # otherwise put a stray space before the label.
    end = "%" if table.short_caption else ""
    indent = "  " if table.floating else ""
    style = "\n".join(f"{indent}{line}" for line in table.body_style.splitlines())
    caption = _text(table.caption)
    lines = [
        f"{indent}{command}{short}{{{caption}}}{end}",
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
    if any(c.group_header is not None for c in columns):
        header_rows += _group_header_rows(columns, table.group_header_align)
    if table.merge_equal_headers:
        # Where a label covers a pair of columns -- a value and its delta, say --
        # it sits on the leaf row itself. No rule: the group row above already
        # carries one, and a second would box the header in.
        leaf, _ = _spanned_cells(
            [_text(c.header) for c in columns], table.group_header_align
        )
    else:
        # A composite cell is wider than its header, so the header reads centred
        # over the pair rather than pinned to the right edge of the column.
        leaf = [
            f"\\multicolumn{{1}}{{c}}{{{_text(c.header)}}}"
            if c.share_source is not None
            else _text(c.header)
            for c in columns
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
    rendered: dict[str, list[str]] = {}
    for position_in_table, column in enumerate(table.columns):
        cells = _column_cells(table, column)
        paired_delta = (
            table.merge_equal_headers
            and position_in_table > 0
            and column.kind is ValueKind.DELTA
            and column.header == table.columns[position_in_table - 1].header
        )
        if paired_delta:
            cells = [f"({cell})" if cell != "--" else cell for cell in cells]
        rendered[column.source] = cells
    band_widths = (
        _band_widths(table)
        if table.bands is not None or table.overall_band is not None
        else None
    )
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
                        lines.append("  " + _value(group, ValueKind.TEXT) + " \\\\")
                    prev_group = group
                    has_previous_group = True
                    indent = True
            elif table.bands is not None:
                if group != prev_group:
                    if lines[-1] != "  \\midrule":
                        lines.append("  \\midrule")
                    band = table.bands.get(str(group))
                    if band is not None:
                        if band_widths is None:
                            raise AssertionError("band widths are unavailable")
                        lines.append(_band_row(band, len(columns), band_widths))
                        lines.append("  \\midrule")
                prev_group = group
            else:
                if prev_group is not None and group != prev_group:
                    lines.append("  \\midrule")
                prev_group = group
        cells = [rendered[c.source][position] for c in table.columns]
        if indent:
            cells[0] = "\\qquad " + cells[0]
        if table.ordinal_header is not None:
            cells.insert(0, str(position + 1))
        lines.append("  " + " & ".join(cells) + " \\\\")
    if table.overall_band is not None:
        if band_widths is None:
            raise AssertionError("band widths are unavailable")
        lines.append("  \\midrule")
        lines.append(_band_row(table.overall_band, len(columns), band_widths))
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
        f"\\newcommand{{\\{macro_name(m.key)}}}{{{_metric_cell(m.value, m.fmt)}}}"
        for m in report.metrics
    ]
    return "\n".join(lines) + ("\n" if lines else "")


_NEWCOMMAND = re.compile(r"\\newcommand\{\\(\w+)\}")


def write_owned_macros(built: BuiltReport, build_dir: Path) -> Path:
    """Write the macro file owned by one report."""
    report = built.report
    database = next(
        snapshot.database
        for snapshot in built.inputs
        if isinstance(snapshot, CorpusInputSnapshot)
    )
    owned_dir = build_dir / "macros"
    owned_dir.mkdir(parents=True, exist_ok=True)
    path = owned_dir / f"{report.rq}.tex"
    header = f"% {report.rq} from {database}\n"
    path.write_text(header + render_macros(report), encoding="utf-8")
    return path


def write_aggregate_macros(build_dir: Path) -> Path:
    """Rebuild the run-owned aggregate from the staged report macro files."""
    owned_dir = build_dir / "macros"
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


def render(built: BuiltReport, build_dir: Path, *, staging_root: Path) -> ArtifactSet:
    report = built.report
    build_dir.mkdir(parents=True, exist_ok=True)
    artifacts = ArtifactSet(staging_root)
    for table in report.tables():
        out = build_dir / f"{table.key}.tex"
        out.write_text(render_table(table), encoding="utf-8")
        artifacts.add(
            RenderedArtifact(ArtifactId(RenderTarget.LATEX, table.key), out, report.rq)
        )
    macro_path = write_owned_macros(built, build_dir)
    artifacts.add(
        RenderedArtifact(
            ArtifactId(RenderTarget.LATEX, f"macros/{report.rq}"),
            macro_path,
            report.rq,
        )
    )
    return artifacts


def render_aggregate(build_dir: Path, *, staging_root: Path) -> ArtifactSet:
    path = write_aggregate_macros(build_dir)
    artifacts = ArtifactSet(staging_root)
    artifacts.add(
        RenderedArtifact(
            ArtifactId(RenderTarget.LATEX, "macros"), path, RunAggregate.RUN
        )
    )
    return artifacts
