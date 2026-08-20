"""Connection resolution and a read_sql helper for eval reports."""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from contextlib import contextmanager
from dataclasses import dataclass

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer.report_basis import open_report_connection


@dataclass(frozen=True)
class Required:
    """One schema object a report reads, with the columns it depends on.

    Validation checks existence, kind, and columns: a same-named object with
    renamed or dropped columns would pass an object-only check, then fail at
    query time.
    """

    name: str
    kind: str  # "table" | "view"
    columns: Sequence[str]


_KIND_RELKINDS = {
    "table": {"r", "p"},  # ordinary + partitioned table
    "view": {"v", "m"},  # view + materialized view (the report only SELECTs from it)
}


def validate_required(conn: Connection, require: Sequence[Required]) -> None:
    """Check each required object exists with the right kind and columns.

    Uses pg_catalog, not information_schema: a materialized view (e.g.
    mv_exclusions_all) is absent from information_schema.tables/columns, so an
    information_schema check would falsely report it missing.
    """
    missing: list[str] = []
    for obj in require:
        relkind = conn.execute(
            text(
                "SELECT c.relkind FROM pg_class c "
                "JOIN pg_namespace n ON n.oid = c.relnamespace "
                "WHERE n.nspname = 'public' AND c.relname = :name"
            ),
            {"name": obj.name},
        ).scalar()
        if relkind is None:
            missing.append(f"{obj.kind} {obj.name} (absent)")
            continue
        if relkind not in _KIND_RELKINDS[obj.kind]:
            missing.append(
                f"{obj.name} (expected {obj.kind}, found relkind {relkind!r})"
            )
            continue
        present_cols = {
            row[0]
            for row in conn.execute(
                text(
                    "SELECT a.attname FROM pg_attribute a "
                    "JOIN pg_class c ON c.oid = a.attrelid "
                    "JOIN pg_namespace n ON n.oid = c.relnamespace "
                    "WHERE n.nspname = 'public' AND c.relname = :name "
                    "AND a.attnum > 0 AND NOT a.attisdropped"
                ),
                {"name": obj.name},
            )
        }
        for col in obj.columns:
            if col not in present_cols:
                missing.append(f"{obj.name}.{col}")
    if missing:
        raise RuntimeError(
            "database is missing required schema objects/columns: " + ", ".join(missing)
        )


@contextmanager
def connect(
    db: str,
    *,
    validate_schema: bool = False,
    require: Sequence[Required] | None = None,
) -> Iterator[Connection]:
    """Open a read-only connection to `db`.

    validate_schema=True checks that every object in `require` exists with its
    declared kind and columns (the old-schema RQ path). validate_schema=False
    uses the report_basis open connection unchanged (RQ0/RQ6, new schema).
    """
    if validate_schema and not require:
        raise ValueError("validate_schema=True requires a non-empty `require` list")
    with open_report_connection(db) as conn:
        if validate_schema:
            assert require is not None
            validate_required(conn, require)
        yield conn


def read_sql(conn: Connection, sql: str, params: dict | None = None) -> pd.DataFrame:
    return pd.read_sql_query(text(sql), conn, params=params or {})
