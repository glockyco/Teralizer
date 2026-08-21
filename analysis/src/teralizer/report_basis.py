"""Shared basis header for read-only analysis CLIs."""

from __future__ import annotations

from contextlib import contextmanager
import csv
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator

from sqlalchemy import Connection, text

from teralizer.config import db_config, find_project_root


@dataclass(frozen=True)
class LedgerProgress:
    """Done-marker progress from a corpus attempt ledger."""

    total: int
    done: int
    capped: int
    other: int


@dataclass(frozen=True)
class ReportBasis:
    """Database and corpus counts printed before an analysis table."""

    db_name: str
    projects: int
    tests: int
    assertions: int
    included_assertions: int
    progress: LedgerProgress | None = None


@contextmanager
def open_report_connection(db_name: str) -> Iterator[Connection]:
    """Open one read-only, repeatable snapshot without schema validation."""
    engine = db_config.get_report_engine(db_name)
    try:
        with engine.connect() as raw_conn:
            conn = raw_conn
            if raw_conn.dialect.name == "postgresql":
                conn = raw_conn.execution_options(isolation_level="REPEATABLE READ")
            with conn.begin():
                if conn.dialect.name == "postgresql":
                    conn.execute(text("SET TRANSACTION READ ONLY"))
                elif conn.dialect.name == "sqlite":
                    conn.exec_driver_sql("BEGIN")
                yield conn
    finally:
        engine.dispose()


def _scalar_int(conn: Connection, sql: str) -> int:
    value = conn.execute(text(sql)).scalar_one()
    return int(value or 0)


def resolve_repo_relative_path(path: str | Path) -> Path:
    """Resolve a repository-relative value regardless of the process directory."""
    expanded = Path(path).expanduser()
    if expanded.is_absolute():
        return expanded
    project_env = Path(find_project_root()).expanduser()
    project_root = project_env.parent if project_env.name == ".env" else Path.cwd()
    return project_root / expanded


def resolve_repo_path(path: Path) -> Path:
    """Resolve a path supplied by a user, preserving existing cwd-relative paths."""
    expanded = path.expanduser()
    if expanded.is_absolute() or expanded.exists():
        return expanded
    return resolve_repo_relative_path(expanded)


def _read_ledger_rows(ledger: Path) -> list[dict[str, str]]:
    resolved = resolve_repo_path(ledger)
    if not resolved.exists():
        raise FileNotFoundError(
            f"attempt ledger not found: {resolved} (pass --ledger explicitly)"
        )

    with resolved.open(newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        required = {"n", "root_path", "exit_code"}
        if reader.fieldnames is None or not required.issubset(reader.fieldnames):
            missing = sorted(required - set(reader.fieldnames or ()))
            raise ValueError(f"attempt ledger missing columns {missing}: {resolved}")
        return [
            {key: str(value or "").strip() for key, value in row.items()}
            for row in reader
        ]


def _read_ledger_progress(ledger: Path) -> LedgerProgress:
    rows = _read_ledger_rows(ledger)
    done = sum(row["exit_code"] == "0" for row in rows)
    capped = sum(row["exit_code"] == "124" for row in rows)
    return LedgerProgress(
        total=len(rows), done=done, capped=capped, other=len(rows) - done - capped
    )


def require_complete_corpus(
    conn: Connection, *, data_dir: Path, config_dir: Path
) -> None:
    """Refuse a corpus report unless configs, markers, ledger, and DB projects agree.

    The database query runs first and establishes the connection's repeatable-read snapshot. If a
    runner starts after that point, removing markers makes this check fail while subsequent report
    queries remain pinned to the pre-run database state.
    """
    resolved_data = resolve_repo_path(data_dir)
    resolved_configs = resolve_repo_path(config_dir)
    db_root_paths = [
        str(root_path)
        for root_path in conn.execute(text("SELECT root_path FROM project")).scalars()
    ]
    ledger_rows = _read_ledger_rows(resolved_data / "status.tsv")

    config_numbers = {
        path.stem.removeprefix("project-")
        for path in resolved_configs.glob("project-*.conf")
    }
    marker_numbers = {
        path.name.removeprefix("project-")
        for path in (resolved_data / "done").glob("project-*")
        if path.is_file()
    }
    ledger_numbers = [row["n"] for row in ledger_rows]
    ledger_root_paths = [row["root_path"] for row in ledger_rows]

    errors: list[str] = []
    if not config_numbers:
        errors.append(f"no project configs under {resolved_configs}")
    if len(ledger_numbers) != len(set(ledger_numbers)):
        errors.append("attempt ledger contains duplicate project numbers")
    if len(ledger_root_paths) != len(set(ledger_root_paths)):
        errors.append("attempt ledger contains duplicate root paths")
    if set(ledger_numbers) != config_numbers:
        errors.append(
            f"ledger projects {len(set(ledger_numbers))} != configs {len(config_numbers)}"
        )
    if marker_numbers != config_numbers:
        errors.append(
            f"done markers {len(marker_numbers)} != configs {len(config_numbers)}"
        )
    if len(db_root_paths) != len(set(db_root_paths)):
        errors.append("database contains duplicate project root paths")
    if set(db_root_paths) != set(ledger_root_paths):
        errors.append(
            f"database projects {len(set(db_root_paths))} != ledger projects "
            f"{len(set(ledger_root_paths))}"
        )
    if errors:
        raise RuntimeError("corpus is incomplete or inconsistent: " + "; ".join(errors))


def collect_basis(
    conn: Connection, db_name: str, *, ledger: Path | None = None
) -> ReportBasis:
    """Collect the shared basis counts for an analysis run.

    Core corpus tables are required inputs. Missing tables or columns surface as
    the database error instead of being converted into silent zeroes.
    """
    progress = _read_ledger_progress(ledger) if ledger is not None else None
    return ReportBasis(
        db_name=db_name,
        projects=_scalar_int(conn, "SELECT COUNT(*) FROM project"),
        tests=_scalar_int(conn, "SELECT COUNT(*) FROM test"),
        assertions=_scalar_int(conn, "SELECT COUNT(*) FROM assertion"),
        included_assertions=_scalar_int(
            conn,
            "SELECT SUM(CASE WHEN is_included THEN 1 ELSE 0 END) FROM assertion",
        ),
        progress=progress,
    )


def format_basis_header(basis: ReportBasis) -> str:
    """Render the basis as a short stdout block."""
    lines = [
        "# Analysis basis",
        f"db: {basis.db_name}",
        f"projects: {basis.projects}",
        f"tests: {basis.tests}",
        f"assertions: {basis.assertions} (included: {basis.included_assertions})",
    ]
    if basis.progress is not None:
        progress = basis.progress
        lines.append(
            f"run progress: {progress.done}/{progress.total} done, "
            f"{progress.capped} capped, {progress.other} other"
        )
    return "\n".join(lines)


def print_basis_header(
    conn: Connection, db_name: str, *, ledger: Path | None = None
) -> None:
    """Print the shared basis header for a DB-backed CLI."""
    print(format_basis_header(collect_basis(conn, db_name, ledger=ledger)))
    print()
