"""Render an RQReport to a human-facing markdown report."""

from __future__ import annotations

import re
from pathlib import Path

from teralizer.eval.format import render_value
from teralizer.eval.model import Figure, Prose, RQReport, Table

_PLACEHOLDER = re.compile(r"\{([a-zA-Z0-9_.]+)\}")


def _substitute(text: str, metrics: dict) -> str:
    def repl(match: re.Match[str]) -> str:
        key = match.group(1)
        if key not in metrics:
            return match.group(0)
        m = metrics[key]
        return render_value(m.value, m.fmt)

    return _PLACEHOLDER.sub(repl, text)


def _source_link(provenance, repo_url: str) -> str:
    return f"\n\nsource: [`{provenance.qualname}`]({provenance.source_url(repo_url)})"


def _table_md(table: Table, repo_url: str) -> str:
    headers = [c.header for c in table.columns]
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for _, row in table.df.iterrows():
        lines.append(
            "| "
            + " | ".join(render_value(row[c.source], c.fmt) for c in table.columns)
            + " |"
        )
    block = f"**{table.caption}**\n\n" + "\n".join(lines)
    if table.note:
        block += f"\n\n_{table.note}_"
    if table.provenance:
        block += _source_link(table.provenance, repo_url)
    return block


def _figure_md(fig: Figure, rq: str, repo_url: str) -> str:
    block = f"![{fig.caption}](figures/{rq}/{fig.key}.png)\n\n**{fig.caption}**"
    if fig.provenance:
        block += _source_link(fig.provenance, repo_url)
    return block


def render_str(report: RQReport, *, repo_url: str) -> str:
    metrics = report.metric_map()
    parts = [f"# {report.title}", f"_Source database: `{report.db}`._"]
    for section in report.sections:
        parts.append(f"## {section.title}")
        for block in section.blocks:
            if isinstance(block, Prose):
                parts.append(_substitute(block.text, metrics))
            elif isinstance(block, Table):
                parts.append(_table_md(block, repo_url))
            elif isinstance(block, Figure):
                parts.append(_figure_md(block, report.rq, repo_url))
    return "\n\n".join(parts) + "\n"


def render(report: RQReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    out = reports_dir / f"{report.rq}.md"
    out.write_text(render_str(report, repo_url=repo_url), encoding="utf-8")
    return out
