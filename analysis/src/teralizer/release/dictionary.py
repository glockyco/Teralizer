"""Data dictionary of the published corpora and the report result files.

`replication/data-dictionary.toml` describes every table of the four corpora,
every derived view that a registered report reads, and every result CSV file of
the reports: what a row is, and for each column its meaning, unit and the meaning
of a null or empty cell. Column names, types and nullability come from the
restored corpora, and CSV headers from the reference result files, so the rendered
`replication/DATA-DICTIONARY.md` cannot drift from them.

The corpora were collected with different pipeline versions, so a table or column
can be missing from some of them. The dictionary describes their union and names
the corpora of every table and column that not all of them have.
"""

from __future__ import annotations

import csv
import json
import tomllib
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.engine import Connection

DEFAULT_DICTIONARY = "replication/data-dictionary.toml"
DEFAULT_RENDERED = "replication/DATA-DICTIONARY.md"
REFERENCE_RESULTS = "analysis/output/original/data"
RUN_MANIFEST = "analysis/reports/provenance.json"
FORMAT = ("teralizer-data-dictionary", 1)

_KINDS = {"r": "table", "v": "view", "m": "materialized view"}


class DictionaryError(ValueError):
    """The dictionary document cannot be read."""


@dataclass(frozen=True)
class Entry:
    name: str
    description: str
    columns: Mapping[str, str]


@dataclass
class Column:
    name: str
    types: list[str]
    nullable: bool
    corpora: list[str]


@dataclass
class Relation:
    name: str
    kind: str
    corpora: list[str]
    columns: dict[str, Column]


@dataclass(frozen=True)
class ResultFile:
    name: str
    report: str
    path: str
    columns: tuple[str, ...]


def load(path: Path, section: str = "relation") -> list[Entry]:
    """The entries of one section: ``relation`` or ``result``."""
    document = tomllib.loads(path.read_text())
    if (document.get("format"), document.get("format_version")) != FORMAT:
        raise DictionaryError(f"{path} is not a {FORMAT[0]} version 1 document")
    return [
        Entry(item["name"], item["description"], dict(item.get("columns", {})))
        for item in document.get(section, [])
    ]


def result_files(repo: Path) -> dict[str, ResultFile]:
    """Every reference result CSV with its header and the report that writes it."""
    manifest = json.loads((repo / RUN_MANIFEST).read_text())
    owners = {
        Path(artifact["path"]).stem: (report, artifact["path"])
        for report, record in manifest.items()
        for artifact in record["artifacts"]
        if artifact["target"] == "csv"
    }
    files: dict[str, ResultFile] = {}
    for path in sorted((repo / REFERENCE_RESULTS).glob("*.csv")):
        if path.stem not in owners:
            raise DictionaryError(f"no report run writes {path.name}")
        with path.open(newline="") as stream:
            header = next(csv.reader(stream))
        report, run_path = owners[path.stem]
        files[path.stem] = ResultFile(path.stem, report, run_path, tuple(header))
    return files


def add_corpus(relations: dict[str, Relation], corpus: str, conn: Connection) -> None:
    """Merge the tables and views of one corpus into ``relations``."""
    rows = conn.execute(
        text(
            """
            SELECT c.relname, c.relkind, a.attname,
                   format_type(a.atttypid, a.atttypmod), NOT a.attnotnull
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_attribute a ON a.attrelid = c.oid
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'v', 'm')
              AND a.attnum > 0 AND NOT a.attisdropped
            ORDER BY c.relname, a.attnum
            """
        )
    ).all()
    for name, kind, column_name, type_name, nullable in rows:
        relation = relations.setdefault(name, Relation(name, _KINDS[kind], [], {}))
        if corpus not in relation.corpora:
            relation.corpora.append(corpus)
        column = relation.columns.setdefault(
            column_name, Column(column_name, [], False, [])
        )
        if type_name not in column.types:
            column.types.append(type_name)
        column.nullable = column.nullable or nullable
        column.corpora.append(corpus)


def problems(entries: Sequence[Entry], relations: Mapping[str, Relation]) -> list[str]:
    """Differences between the dictionary and the corpora."""
    found: list[str] = []
    described = {entry.name for entry in entries}
    for relation in relations.values():
        if relation.kind == "table" and relation.name not in described:
            found.append(f"table {relation.name} is not described")
    for entry in entries:
        relation = relations.get(entry.name)
        if relation is None:
            found.append(f"{entry.name} is in no corpus")
            continue
        if not entry.description.strip():
            found.append(f"{entry.name} has no description")
        for name in relation.columns:
            if not entry.columns.get(name, "").strip():
                found.append(f"{entry.name}.{name} is not described")
        for name in sorted(set(entry.columns) - set(relation.columns)):
            found.append(f"{entry.name}.{name} is in no corpus")
    return found


def result_problems(
    entries: Sequence[Entry], files: Mapping[str, ResultFile]
) -> list[str]:
    """Differences between the dictionary and the reference result files."""
    found: list[str] = []
    described = {entry.name for entry in entries}
    for name in files:
        if name not in described:
            found.append(f"result {name}.csv is not described")
    for entry in entries:
        result = files.get(entry.name)
        if result is None:
            found.append(f"result {entry.name}.csv does not exist")
            continue
        if not entry.description.strip():
            found.append(f"result {entry.name}.csv has no description")
        for column in result.columns:
            if not entry.columns.get(column, "").strip():
                found.append(
                    f"result {entry.name}.csv column {column} is not described"
                )
        for column in sorted(set(entry.columns) - set(result.columns)):
            found.append(f"result {entry.name}.csv has no column {column}")
    return found


def _cell(value: str) -> str:
    return " ".join(value.split()).replace("|", "\\|")


def render(
    entries: Sequence[Entry],
    relations: Mapping[str, Relation],
    corpora: Sequence[str],
    results: Sequence[Entry],
    files: Mapping[str, ResultFile],
) -> str:
    lines = [
        "# Data dictionary",
        "",
        f"Generated from `{DEFAULT_DICTIONARY}`, the restored corpora and the reference",
        "result files by",
        "`uv run --directory analysis python -m teralizer.release data-dictionary`.",
        "Edit the descriptions there.",
        "",
        "## Corpus tables and views",
        "",
        "The corpora were collected with different pipeline versions. A table or",
        "column that not every corpus has names the corpora that have it. The reports",
        "read the views, which `src/main/resources/db/create-views.sql` defines over",
        "the tables. PostgreSQL reports every view column as nullable.",
        "",
    ]
    for entry in entries:
        relation = relations[entry.name]
        scope = (
            ""
            if len(relation.corpora) == len(corpora)
            else f" Only in {', '.join(relation.corpora)}."
        )
        lines += [
            f"### `{relation.name}`",
            "",
            f"{relation.kind.capitalize()}.{scope} {_cell(entry.description)}",
            "",
            "| Column | Type | Null | Meaning |",
            "| --- | --- | --- | --- |",
        ]
        for column in relation.columns.values():
            only = (
                ""
                if len(column.corpora) == len(relation.corpora)
                else f" Only in {', '.join(column.corpora)}."
            )
            lines.append(
                f"| `{column.name}` | {' or '.join(column.types)} | "
                f"{'yes' if column.nullable else 'no'} | "
                f"{_cell(entry.columns[column.name])}{only} |"
            )
        lines.append("")
    lines += [
        "## Result CSV files",
        "",
        "A report run writes each file below its output directory, and",
        "`reports/provenance.json` there records the inputs, the producing commit and the",
        "checksum of every file. The reference copies are in",
        f"`{REFERENCE_RESULTS}/`.",
        "",
    ]
    for entry in results:
        result = files[entry.name]
        lines += [
            f"### `{result.name}.csv`",
            "",
            f"Report `{result.report}`, `{result.path}`. {_cell(entry.description)}",
            "",
            "| Column | Meaning |",
            "| --- | --- |",
        ]
        for column in result.columns:
            lines.append(f"| `{column}` | {_cell(entry.columns[column])} |")
        lines.append("")
    return "\n".join(lines)


def current(repo: Path) -> tuple[str, list[str]]:
    """The rendering from every registered corpus, and the dictionary's problems."""
    from teralizer import corpora

    entries = load(repo / DEFAULT_DICTIONARY)
    results = load(repo / DEFAULT_DICTIONARY, "result")
    files = result_files(repo)
    ids = [entry.id for entry in corpora.load().entries]
    relations: dict[str, Relation] = {}
    for corpus in ids:
        with corpora.open_corpus(corpus) as (_, conn):
            add_corpus(relations, corpus, conn)
    found = problems(entries, relations) + result_problems(results, files)
    if found:
        return "", found
    return render(entries, relations, ids, results, files), found
