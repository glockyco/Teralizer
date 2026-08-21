"""Semantic registry for immutable evaluation corpora."""

from __future__ import annotations

import argparse
import hashlib
import re
import shlex
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
DERIVED_VIEW_PATH = _REPO_ROOT / "src/main/resources/db/create-views.sql"
SCRATCH_DATABASE_PATTERN = re.compile(r"^scratch_[a-z0-9][a-z0-9_]*$")
_REQUIRED_FIELDS = frozenset(
    {
        "id",
        "database",
        "data_dir",
        "config_dir",
        "expected_projects",
        "derived_views",
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
    derived_views: bool = True


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
        if is_scratch_database(database):
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

    derived_views = raw["derived_views"]
    if not isinstance(derived_views, bool):
        raise ValueError(f"corpus {label!r} field 'derived_views' must be a boolean")

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
        derived_views=derived_views,
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


def is_scratch_database(database: str) -> bool:
    """Return whether a physical name belongs to the reserved scratch namespace."""
    return SCRATCH_DATABASE_PATTERN.fullmatch(database) is not None


def derived_view_revision(path: Path = DERIVED_VIEW_PATH) -> str:
    """Return the canonical SHA-256 revision of the checked-in view definition."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


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
    from teralizer.corpus_preparation import require_current_revision

    entry = resolve(corpus_id, registry)
    with open_report_connection(entry.database) as conn:
        if entry.derived_views:
            require_current_revision(conn, entry.id)
        validate_project_count(conn, entry)
        yield entry, conn


_FIELDS = (
    "database",
    "data-dir",
    "config-dir",
    "expected-projects",
    "derived-views",
    "published",
    "notes",
)


def field_value(entry: CorpusEntry, field: str) -> str:
    """Render one declared field without exposing Python representation details."""
    if field not in _FIELDS:
        raise ValueError(f"unknown corpus field {field!r}")
    value = getattr(entry, field.replace("-", "_"))
    if isinstance(value, bool):
        return str(value).lower()
    return "" if value is None else str(value)


def shell_exports(entry: CorpusEntry) -> str:
    """Render safely quoted exports consumed by Java and shell launchers."""
    values = {
        "TERALIZER_CORPUS_ID": entry.id,
        "DB_NAME": entry.database,
        "DATA_DIR": entry.data_dir or "",
        "CONFIG_DIR": entry.config_dir or "",
        "EXPECTED_PROJECTS": str(entry.expected_projects),
        "CORPUS_DERIVED_VIEWS": str(entry.derived_views).lower(),
        "CORPUS_PUBLISHED": str(entry.published).lower(),
    }
    return "\n".join(
        f"export {name}={shlex.quote(value)}" for name, value in values.items()
    )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.corpora")
    commands = parser.add_subparsers(dest="command", required=True)

    get_parser = commands.add_parser("get", help="print one field")
    get_parser.add_argument("corpus_id")
    get_parser.add_argument("field", choices=_FIELDS)

    export_parser = commands.add_parser(
        "export", help="print safely quoted shell connection settings"
    )
    export_parser.add_argument("corpus_id")

    list_parser = commands.add_parser("list", help="print corpus ids")
    list_parser.add_argument(
        "--published", action="store_true", help="include only published corpora"
    )

    prepare_parser = commands.add_parser(
        "prepare-corpus", help="install and verify one corpus's derived schema"
    )
    prepare_parser.add_argument("corpus_id")

    classify_parser = commands.add_parser(
        "classify", help="classify one physical database name"
    )
    classify_parser.add_argument("database")

    verify_parser = commands.add_parser(
        "verify-corpus", help="exercise one corpus through the report-only boundary"
    )
    verify_parser.add_argument("corpus_id")

    args = parser.parse_args(argv)
    if args.command == "classify":
        classification = load().classify(args.database)
        print(classification.kind.value)
        return
    if args.command == "list":
        registry = load()
        entries = registry.published_entries if args.published else registry.entries
        for entry in entries:
            print(entry.id)
        return
    if args.command == "verify-corpus":
        from teralizer.report_basis import require_complete_corpus

        with open_corpus(args.corpus_id) as (entry, conn):
            if entry.data_dir is not None:
                assert entry.config_dir is not None
                require_complete_corpus(
                    conn,
                    data_dir=_REPO_ROOT / entry.data_dir,
                    config_dir=_REPO_ROOT / entry.config_dir,
                )
            print(f"verified {entry.id}: {entry.database}")
        return
    if args.command == "prepare-corpus":
        from teralizer.corpus_preparation import prepare

        result = prepare(args.corpus_id)
        print(f"corpus: {result.corpus_id}")
        print(f"database: {result.database}")
        print(f"projects: {result.projects}")
        print(
            f"derived-view revision: {result.derived_view_revision or 'not applicable'}"
        )
        print(f"verified views: {len(result.views)}")
        print(f"report role: {result.report_role}")
        return

    entry = resolve(args.corpus_id)
    if args.command == "get":
        print(field_value(entry, args.field))
    else:
        print(shell_exports(entry))


if __name__ == "__main__":
    main()
