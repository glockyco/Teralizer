"""Semantic registry for immutable evaluation corpora."""

from __future__ import annotations

import argparse
import re
import tomllib
from collections.abc import Iterable, Iterator, Mapping
from contextlib import contextmanager
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from types import MappingProxyType

from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer.report_basis import open_report_connection

_REPO_ROOT = Path(__file__).resolve().parents[3]
REGISTRY_PATH = _REPO_ROOT / "src/main/resources/db/corpora.toml"
SCRATCH_DATABASE_PATTERN = re.compile(r"^scratch_[a-z0-9][a-z0-9_]*$")
_REQUIRED_FIELDS = frozenset(
    {
        "id",
        "database",
        "data_dir",
        "config_dir",
        "expected_projects",
        "published",
        "notes",
    }
)


class DatabaseKind(StrEnum):
    """Lifecycle class assigned to an observed database."""

    CORPUS = "registered corpus"
    SCRATCH = "scratch"
    UNCLASSIFIED = "unclassified"


@dataclass(frozen=True)
class DatabaseClassification:
    """Lifecycle classification of one observed physical database."""

    database: str
    kind: DatabaseKind
    corpus_id: str | None = None


@dataclass(frozen=True)
class CorpusEntry:
    """One semantic corpus and the current physical database that provides it."""

    id: str
    database: str
    data_dir: str | None
    config_dir: str | None
    expected_projects: int
    published: bool
    notes: str


class CorpusRegistry:
    """Immutable corpus entries indexed by semantic id."""

    def __init__(self, entries: tuple[CorpusEntry, ...]) -> None:
        by_id: dict[str, CorpusEntry] = {}
        physical_owners: dict[str, str] = {}
        for entry in entries:
            if entry.id in by_id:
                raise ValueError(f"duplicate corpus id: {entry.id!r}")
            if previous := physical_owners.get(entry.database):
                raise ValueError(
                    f"corpora {previous!r} and {entry.id!r} declare duplicate "
                    f"physical database {entry.database!r}"
                )
            by_id[entry.id] = entry
            physical_owners[entry.database] = entry.id
        self._entries = entries
        self._by_id: Mapping[str, CorpusEntry] = MappingProxyType(by_id)
        self._physical_owners: Mapping[str, str] = MappingProxyType(physical_owners)

    @property
    def entries(self) -> tuple[CorpusEntry, ...]:
        return self._entries

    @property
    def published_entries(self) -> tuple[CorpusEntry, ...]:
        return tuple(entry for entry in self._entries if entry.published)

    def classify(self, database: str) -> DatabaseClassification:
        if corpus_id := self._physical_owners.get(database):
            return DatabaseClassification(database, DatabaseKind.CORPUS, corpus_id)
        if SCRATCH_DATABASE_PATTERN.fullmatch(database):
            return DatabaseClassification(database, DatabaseKind.SCRATCH)
        return DatabaseClassification(database, DatabaseKind.UNCLASSIFIED)

    def classify_all(
        self, databases: Iterable[str]
    ) -> tuple[DatabaseClassification, ...]:
        return tuple(self.classify(database) for database in databases)

    def get(self, corpus_id: str) -> CorpusEntry:
        try:
            return self._by_id[corpus_id]
        except KeyError:
            raise KeyError(
                f"unknown corpus id {corpus_id!r} (known: {sorted(self._by_id)})"
            ) from None


def _required_string(raw: Mapping[str, object], field: str, label: str) -> str:
    value = raw[field]
    if not isinstance(value, str):
        raise ValueError(f"corpus {label!r} field {field!r} must be a string")
    return value


def _entry(raw: Mapping[str, object], index: int) -> CorpusEntry:
    label_value = raw.get("id", f"entry {index}")
    label = str(label_value)
    missing = sorted(_REQUIRED_FIELDS.difference(raw))
    if missing:
        raise ValueError(f"corpus {label!r} is missing required field {missing[0]!r}")

    expected_projects = raw["expected_projects"]
    if isinstance(expected_projects, bool) or not isinstance(expected_projects, int):
        raise ValueError(
            f"corpus {label!r} field 'expected_projects' must be an integer"
        )
    if expected_projects < 0:
        raise ValueError(
            f"corpus {label!r} field 'expected_projects' must not be negative"
        )

    published = raw["published"]
    if not isinstance(published, bool):
        raise ValueError(f"corpus {label!r} field 'published' must be a boolean")

    data_dir = _required_string(raw, "data_dir", label) or None
    config_dir = _required_string(raw, "config_dir", label) or None
    if (data_dir is None) != (config_dir is None):
        raise ValueError(
            f"corpus {label!r} must declare both data_dir and config_dir or neither"
        )

    return CorpusEntry(
        id=_required_string(raw, "id", label),
        database=_required_string(raw, "database", label),
        data_dir=data_dir,
        config_dir=config_dir,
        expected_projects=expected_projects,
        published=published,
        notes=_required_string(raw, "notes", label),
    )


def load(path: Path = REGISTRY_PATH) -> CorpusRegistry:
    """Load and validate a corpus registry from TOML."""
    document = tomllib.loads(path.read_text(encoding="utf-8"))
    raw_entries = document.get("corpus")
    if not isinstance(raw_entries, list):
        raise ValueError("corpus registry must declare one or more [[corpus]] entries")
    entries: list[CorpusEntry] = []
    for index, raw in enumerate(raw_entries, start=1):
        if not isinstance(raw, dict):
            raise ValueError(f"corpus entry {index} must be a table")
        entries.append(_entry(raw, index))
    return CorpusRegistry(tuple(entries))


def resolve(corpus_id: str, registry: CorpusRegistry | None = None) -> CorpusEntry:
    """Resolve a semantic corpus id without exposing a primary-corpus alias."""
    return (registry or load()).get(corpus_id)


def validate_project_count(conn: Connection, entry: CorpusEntry) -> int:
    """Fail when a connected database is not the declared corpus shape."""
    observed = int(conn.execute(text("SELECT COUNT(*) FROM project")).scalar_one())
    if observed != entry.expected_projects:
        raise RuntimeError(
            f"corpus {entry.id!r} expects {entry.expected_projects} projects in "
            f"{entry.database!r}; observed {observed}"
        )
    return observed


@contextmanager
def open_corpus(
    corpus_id: str, registry: CorpusRegistry | None = None
) -> Iterator[tuple[CorpusEntry, Connection]]:
    """Open and validate one registry corpus as a read-only repeatable snapshot."""
    entry = resolve(corpus_id, registry)
    with open_report_connection(entry.database) as conn:
        validate_project_count(conn, entry)
        yield entry, conn


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.corpora")
    parser.add_argument("corpus_id")
    parser.add_argument(
        "field",
        choices=(
            "database",
            "data-dir",
            "config-dir",
            "expected-projects",
            "notes",
        ),
    )
    args = parser.parse_args(argv)
    entry = resolve(args.corpus_id)
    value: str | int | None = getattr(entry, args.field.replace("-", "_"))
    print("" if value is None else value)


if __name__ == "__main__":
    main()
