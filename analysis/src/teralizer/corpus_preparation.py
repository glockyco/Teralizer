"""Owner-only installation and verification of derived corpus schema."""

from __future__ import annotations

import re
from dataclasses import dataclass

from psycopg2 import sql
from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer import corpora
from teralizer.config import db_config

METADATA_TABLE = "teralizer_corpus_metadata"
DERIVED_VIEW_REVISION_KEY = "derived_view_revision"
_DERIVED_VIEW_PATTERN = re.compile(
    r"^CREATE\s+(?:MATERIALIZED\s+)?VIEW\s+([a-z_][a-z0-9_]*)\s+AS\b",
    re.IGNORECASE | re.MULTILINE,
)


@dataclass(frozen=True)
class PreparationResult:
    corpus_id: str
    database: str
    projects: int
    derived_view_revision: str | None
    views: tuple[str, ...]
    report_role: str


def _derived_view_names(source: str) -> tuple[str, ...]:
    names = tuple(
        dict.fromkeys(
            match.group(1) for match in _DERIVED_VIEW_PATTERN.finditer(source)
        )
    )
    if not names:
        raise RuntimeError(f"no derived views declared in {corpora.DERIVED_VIEW_PATH}")
    return names


def _configure_report_role(conn: Connection, database: str) -> str:
    role = db_config.report_user
    password = db_config.report_password
    raw = conn.connection.driver_connection
    if raw is None:
        raise RuntimeError("database driver connection is unavailable")
    with raw.cursor() as cursor:
        cursor.execute("SELECT 1 FROM pg_roles WHERE rolname = %s", (role,))
        role_exists = cursor.fetchone() is not None
        if role_exists:
            cursor.execute(
                sql.SQL(
                    "ALTER ROLE {} WITH LOGIN PASSWORD {} NOSUPERUSER NOCREATEDB "
                    "NOCREATEROLE NOREPLICATION NOINHERIT"
                ).format(sql.Identifier(role), sql.Literal(password))
            )
        else:
            cursor.execute(
                sql.SQL(
                    "CREATE ROLE {} WITH LOGIN PASSWORD {} NOSUPERUSER NOCREATEDB "
                    "NOCREATEROLE NOREPLICATION NOINHERIT"
                ).format(sql.Identifier(role), sql.Literal(password))
            )
        cursor.execute(
            sql.SQL("REVOKE ALL ON DATABASE {} FROM {}").format(
                sql.Identifier(database), sql.Identifier(role)
            )
        )
        cursor.execute(
            sql.SQL("GRANT CONNECT ON DATABASE {} TO {}").format(
                sql.Identifier(database), sql.Identifier(role)
            )
        )
        cursor.execute(
            sql.SQL("REVOKE ALL ON SCHEMA public FROM {}").format(sql.Identifier(role))
        )
        cursor.execute(
            sql.SQL("GRANT USAGE ON SCHEMA public TO {}").format(sql.Identifier(role))
        )
        cursor.execute(
            sql.SQL("GRANT SELECT ON ALL TABLES IN SCHEMA public TO {}").format(
                sql.Identifier(role)
            )
        )
        cursor.execute(
            sql.SQL("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO {}").format(
                sql.Identifier(role)
            )
        )
        cursor.execute(
            sql.SQL(
                "ALTER ROLE {} IN DATABASE {} SET default_transaction_read_only TO on"
            ).format(sql.Identifier(role), sql.Identifier(database))
        )
    return role


def _apply_derived_schema(conn: Connection, source: str) -> None:
    raw = conn.connection.driver_connection
    if raw is None:
        raise RuntimeError("database driver connection is unavailable")
    with raw.cursor() as cursor:
        cursor.execute(source)


def _record_revision(conn: Connection, revision: str) -> None:
    conn.execute(
        text(
            f"CREATE TABLE IF NOT EXISTS {METADATA_TABLE} ("
            "key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        )
    )
    conn.execute(
        text(
            f"INSERT INTO {METADATA_TABLE} (key, value) VALUES (:key, :value) "
            "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value"
        ),
        {"key": DERIVED_VIEW_REVISION_KEY, "value": revision},
    )


def installed_revision(conn: Connection) -> str | None:
    """Return the installed derived-view revision, or ``None`` when unprepared."""
    table_exists = conn.execute(
        text("SELECT to_regclass(:name)"), {"name": f"public.{METADATA_TABLE}"}
    ).scalar_one()
    if table_exists is None:
        return None
    value = conn.execute(
        text(f"SELECT value FROM {METADATA_TABLE} WHERE key = :key"),
        {"key": DERIVED_VIEW_REVISION_KEY},
    ).scalar_one_or_none()
    return None if value is None else str(value)


def require_current_revision(conn: Connection, corpus_id: str) -> str:
    """Reject an unprepared corpus or a derived schema from another revision."""
    expected = corpora.derived_view_revision()
    observed = installed_revision(conn)
    if observed is None:
        raise RuntimeError(
            f"corpus {corpus_id!r} has no installed derived-view revision. "
            f"run prepare-corpus {corpus_id}"
        )
    if observed != expected:
        raise RuntimeError(
            f"corpus {corpus_id!r} has derived-view revision {observed}. "
            f"expected {expected}. Run prepare-corpus {corpus_id}"
        )
    return observed


def _installed_view_names(conn: Connection, names: tuple[str, ...]) -> set[str]:
    return {
        str(row[0])
        for row in conn.execute(
            text(
                "SELECT c.relname FROM pg_class c "
                "JOIN pg_namespace n ON n.oid = c.relnamespace "
                "WHERE n.nspname = 'public' AND c.relkind IN ('v', 'm') "
                "AND c.relname = ANY(:names)"
            ),
            {"names": list(names)},
        )
    }


def _verify_views(conn: Connection, names: tuple[str, ...]) -> None:
    missing = set(names) - _installed_view_names(conn, names)
    if missing:
        raise RuntimeError(f"derived view {sorted(missing)[0]!r} was not installed")
    raw = conn.connection.driver_connection
    if raw is None:
        raise RuntimeError("database driver connection is unavailable")
    with raw.cursor() as cursor:
        for name in names:
            cursor.execute(
                sql.SQL("SELECT * FROM public.{} LIMIT 0").format(sql.Identifier(name))
            )


def prepare(corpus_id: str) -> PreparationResult:
    """Install one registered corpus's derived schema in a single transaction."""
    entry = corpora.resolve(corpus_id)
    source = corpora.DERIVED_VIEW_PATH.read_text(encoding="utf-8")
    revision = corpora.derived_view_revision() if entry.derived_views else None
    views = _derived_view_names(source) if entry.derived_views else ()
    engine = db_config.get_engine(entry.database, validate=False)
    try:
        with engine.begin() as conn:
            projects = corpora.validate_project_count(conn, entry)
            if revision is not None:
                installed_views = _installed_view_names(conn, views)
                if installed_revision(conn) != revision or installed_views != set(
                    views
                ):
                    _apply_derived_schema(conn, source)
                    _record_revision(conn, revision)
                _verify_views(conn, views)
            report_role = _configure_report_role(conn, entry.database)
    finally:
        engine.dispose()
    return PreparationResult(
        corpus_id=entry.id,
        database=entry.database,
        projects=projects,
        derived_view_revision=revision,
        views=views,
        report_role=report_role,
    )
