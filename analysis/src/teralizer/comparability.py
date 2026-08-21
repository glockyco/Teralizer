"""Refuse to compare two corpus runs that were not produced the same way.

AGENTS.md states the metric rule: confirm that two databases were computed through the same
code path before comparing their figures. Nothing enforced it, and a comparison of runs whose
schemas differed produced a day of wrong conclusions. A column that one run records and the
other does not is enough to make a per-project comparison meaningless, and the difference is
invisible in a query that never mentions the column.

The check is split so that the decision is pure and testable without a database. ``compare``
takes two :class:`RunMetadata` values and returns findings. ``read_metadata`` is the only part
that talks to Postgres.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from sqlalchemy import text

BLOCK = "BLOCK"
WARN = "WARN"

#: Tables whose shape decides whether funnel figures mean the same thing in both runs.
FUNNEL_TABLES = (
    "project",
    "generalization",
    "generalization_lifecycle",
    "task",
    "task_diagnostic",
    "filter_result",
)


@dataclass(frozen=True)
class RunMetadata:
    """What a run recorded about how it was produced."""

    name: str
    tool_versions: frozenset[str]
    columns: Mapping[str, frozenset[str]]
    project_count: int


@dataclass(frozen=True)
class Finding:
    severity: str
    message: str


@dataclass(frozen=True)
class Report:
    findings: tuple[Finding, ...]

    @property
    def comparable(self) -> bool:
        return not any(f.severity == BLOCK for f in self.findings)

    def render(self) -> str:
        if not self.findings:
            return "comparable: the two runs agree on tool version, schema and project count"
        lines = [f"{f.severity}: {f.message}" for f in self.findings]
        verdict = "comparable" if self.comparable else "NOT comparable"
        return "\n".join(lines + [f"verdict: {verdict}"])


def compare(a: RunMetadata, b: RunMetadata) -> Report:
    """Report every reason the two runs cannot be compared figure for figure."""
    findings: list[Finding] = []

    if a.tool_versions != b.tool_versions:
        findings.append(
            Finding(
                BLOCK,
                f"tool version differs: {a.name} ran {_show(a.tool_versions)} and "
                f"{b.name} ran {_show(b.tool_versions)}, so a figure can carry a different "
                f"definition in each",
            )
        )

    for table in FUNNEL_TABLES:
        only_a = a.columns.get(table, frozenset()) - b.columns.get(table, frozenset())
        only_b = b.columns.get(table, frozenset()) - a.columns.get(table, frozenset())
        if only_a or only_b:
            parts = []
            if only_a:
                parts.append(f"only in {a.name}: {_show(only_a)}")
            if only_b:
                parts.append(f"only in {b.name}: {_show(only_b)}")
            findings.append(
                Finding(BLOCK, f"{table} has a different shape, {', '.join(parts)}")
            )

    if a.project_count != b.project_count:
        findings.append(
            Finding(
                WARN,
                f"project count differs: {a.name} has {a.project_count} and "
                f"{b.name} has {b.project_count}, so totals are over different corpora",
            )
        )

    return Report(tuple(findings))


def read_metadata(engine, name: str) -> RunMetadata:
    """Read the metadata a comparison needs from one database."""
    with engine.connect() as conn:
        versions = conn.execute(
            text(
                "SELECT DISTINCT tool_git_version FROM project "
                "WHERE tool_git_version IS NOT NULL"
            )
        ).scalars()
        tool_versions = frozenset(str(v) for v in versions)

        rows = conn.execute(
            text(
                "SELECT table_name, column_name FROM information_schema.columns "
                "WHERE table_schema = 'public'"
            )
        ).all()
        columns: dict[str, set[str]] = {}
        for table_name, column_name in rows:
            columns.setdefault(table_name, set()).add(column_name)

        project_count = conn.execute(text("SELECT count(*) FROM project")).scalar_one()

    return RunMetadata(
        name=name,
        tool_versions=tool_versions,
        columns={table: frozenset(cols) for table, cols in columns.items()},
        project_count=int(project_count),
    )


def _show(values) -> str:
    return ", ".join(sorted(values)) if values else "nothing"


def main(argv=None) -> int:
    """Compare two registered corpora. Return nonzero when they are not comparable."""
    import argparse

    from teralizer import corpora
    from teralizer.config import DatabaseConfig

    parser = argparse.ArgumentParser(
        description="Check that two registered corpus runs can be compared."
    )
    parser.add_argument("corpus_a")
    parser.add_argument("corpus_b")
    args = parser.parse_args(argv)

    entry_a = corpora.resolve(args.corpus_a)
    entry_b = corpora.resolve(args.corpus_b)
    config = DatabaseConfig()
    a = read_metadata(config.get_engine(entry_a.database, validate=False), entry_a.id)
    b = read_metadata(config.get_engine(entry_b.database, validate=False), entry_b.id)

    report = compare(a, b)
    print(report.render())
    return 0 if report.comparable else 1


if __name__ == "__main__":
    raise SystemExit(main())
