"""Tests for shared analysis report-basis headers."""

from __future__ import annotations

from pathlib import Path

import pytest
from sqlalchemy import create_engine, text

from teralizer.report_basis import collect_basis, format_basis_header


def _create_basis_schema(conn) -> None:
    conn.execute(text("CREATE TABLE project (id INTEGER PRIMARY KEY)"))
    conn.execute(text("CREATE TABLE test (id INTEGER PRIMARY KEY, project_id INTEGER)"))
    conn.execute(
        text(
            "CREATE TABLE assertion ("
            "id INTEGER PRIMARY KEY, project_id INTEGER, is_included BOOLEAN)"
        )
    )
    conn.execute(text("INSERT INTO project (id) VALUES (1), (2)"))
    conn.execute(
        text("INSERT INTO test (id, project_id) VALUES (10, 1), (11, 1), (12, 2)")
    )
    conn.execute(
        text(
            "INSERT INTO assertion (id, project_id, is_included) VALUES "
            "(20, 1, true), (21, 1, false), (22, 2, false), (23, 2, true)"
        )
    )


def test_basis_header_formats_counts_and_ledger_progress(tmp_path: Path):
    engine = create_engine("sqlite:///:memory:")
    ledger = tmp_path / "status.tsv"
    ledger.write_text(
        "n\troot_path\texit_code\tlog\n"
        "1\tprojects/a\t0\ta.log\n"
        "2\tprojects/b\t124\tb.log\n"
        "3\tprojects/c\t1\tc.log\n"
    )
    try:
        with engine.begin() as conn:
            _create_basis_schema(conn)
            basis = collect_basis(conn, "postgres_reporeapers_rerun2", ledger=ledger)
    finally:
        engine.dispose()

    assert format_basis_header(basis) == (
        "# Analysis basis\n"
        "db: postgres_reporeapers_rerun2\n"
        "projects: 2\n"
        "tests: 3\n"
        "assertions: 4 (included: 2)\n"
        "run progress: 1/3 done, 1 capped, 1 other"
    )


def test_basis_header_omits_progress_without_ledger():
    engine = create_engine("sqlite:///:memory:")
    try:
        with engine.begin() as conn:
            _create_basis_schema(conn)
            basis = collect_basis(conn, "postgres_test")
    finally:
        engine.dispose()

    assert "run progress" not in format_basis_header(basis)


def test_basis_header_fails_loudly_when_ledger_is_missing(tmp_path: Path):
    engine = create_engine("sqlite:///:memory:")
    try:
        with engine.begin() as conn:
            _create_basis_schema(conn)
            with pytest.raises(FileNotFoundError, match="attempt ledger not found"):
                collect_basis(conn, "postgres_test", ledger=tmp_path / "missing.tsv")
    finally:
        engine.dispose()
