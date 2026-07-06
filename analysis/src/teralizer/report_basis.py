"""Shared basis header for read-only analysis CLIs."""

from __future__ import annotations

from contextlib import contextmanager
import csv
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator

from sqlalchemy import Connection, text

from teralizer.config import db_config


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
    """Open a read-only analysis connection without schema validation."""
    engine = db_config.get_engine(db_name, validate=False)
    with engine.connect() as conn:
        yield conn


def _scalar_int(conn: Connection, sql: str) -> int:
    value = conn.execute(text(sql)).scalar_one()
    return int(value or 0)


def _read_ledger_progress(ledger: Path) -> LedgerProgress:
    resolved = ledger.expanduser()
    if not resolved.exists():
        raise FileNotFoundError(
            f"attempt ledger not found: {resolved} (pass --ledger explicitly)"
        )

    total = 0
    done = 0
    capped = 0
    with resolved.open(newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if reader.fieldnames is None or "exit_code" not in reader.fieldnames:
            raise ValueError(f"attempt ledger missing exit_code column: {resolved}")
        for row in reader:
            total += 1
            code = str(row.get("exit_code", "")).strip()
            if code == "0":
                done += 1
            elif code == "124":
                capped += 1
    return LedgerProgress(
        total=total, done=done, capped=capped, other=total - done - capped
    )


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
