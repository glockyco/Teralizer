"""Tests for shared analysis report-basis headers."""

from __future__ import annotations

from pathlib import Path

import pytest
from sqlalchemy import create_engine, text

from teralizer import report_basis
from teralizer.report_basis import (
    collect_basis,
    format_basis_header,
    open_report_connection,
    require_complete_corpus,
)


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
            basis = collect_basis(conn, "postgres_reporeapers", ledger=ledger)
    finally:
        engine.dispose()

    assert format_basis_header(basis) == (
        "# Analysis basis\n"
        "db: postgres_reporeapers\n"
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


def test_basis_header_resolves_relative_ledger_from_project_root(
    monkeypatch, tmp_path: Path
):
    root = tmp_path / "repo"
    analysis_dir = root / "analysis"
    ledger = root / "data" / "status.tsv"
    analysis_dir.mkdir(parents=True)
    ledger.parent.mkdir()
    (root / ".env").write_text("")
    ledger.write_text("n\troot_path\texit_code\tlog\n1\tprojects/a\t0\ta.log\n")

    monkeypatch.chdir(analysis_dir)
    monkeypatch.setattr(report_basis, "find_project_root", lambda: root / ".env")

    engine = create_engine("sqlite:///:memory:")
    try:
        with engine.begin() as conn:
            _create_basis_schema(conn)
            basis = collect_basis(conn, "postgres_test", ledger=Path("data/status.tsv"))
    finally:
        engine.dispose()

    assert basis.progress is not None
    assert basis.progress.done == 1


def test_basis_header_fails_loudly_when_ledger_is_missing(tmp_path: Path):
    engine = create_engine("sqlite:///:memory:")
    try:
        with engine.begin() as conn:
            _create_basis_schema(conn)
            with pytest.raises(FileNotFoundError, match="attempt ledger not found"):
                collect_basis(conn, "postgres_test", ledger=tmp_path / "missing.tsv")
    finally:
        engine.dispose()


def test_report_connection_holds_one_snapshot(monkeypatch, tmp_path: Path):
    engine = create_engine(f"sqlite:///{tmp_path / 'snapshot.sqlite'}")
    with engine.begin() as conn:
        conn.execute(text("PRAGMA journal_mode=WAL"))
        conn.execute(text("CREATE TABLE state (value INTEGER NOT NULL)"))
        conn.execute(text("INSERT INTO state VALUES (1)"))
    monkeypatch.setattr(
        report_basis.db_config, "get_engine", lambda *_args, **_kwargs: engine
    )

    with open_report_connection("snapshot") as conn:
        first = conn.execute(text("SELECT value FROM state")).scalar_one()
        with engine.begin() as writer:
            writer.execute(text("UPDATE state SET value = 2"))
        second = conn.execute(text("SELECT value FROM state")).scalar_one()

    engine.dispose()
    assert first == second == 1


def test_complete_corpus_requires_configs_markers_ledger_and_projects(
    tmp_path: Path,
):
    config_dir = tmp_path / "configs"
    data_dir = tmp_path / "data"
    done_dir = data_dir / "done"
    config_dir.mkdir()
    done_dir.mkdir(parents=True)
    for number in ("1", "2"):
        (config_dir / f"project-{number}.conf").write_text("")
        (done_dir / f"project-{number}").write_text("")
    (data_dir / "status.tsv").write_text(
        "n\troot_path\texit_code\tlog\n"
        "1\tprojects/a\t0\ta.log\n"
        "2\tprojects/b\t1\tb.log\n"
    )

    engine = create_engine("sqlite:///:memory:")
    try:
        with engine.begin() as conn:
            conn.execute(
                text("CREATE TABLE project (id INTEGER PRIMARY KEY, root_path TEXT)")
            )
            conn.execute(
                text(
                    "INSERT INTO project (id, root_path) VALUES "
                    "(1, 'projects/a'), (2, 'projects/b')"
                )
            )
            require_complete_corpus(conn, data_dir=data_dir, config_dir=config_dir)

            (done_dir / "project-2").unlink()
            with pytest.raises(RuntimeError, match="done markers 1 != configs 2"):
                require_complete_corpus(conn, data_dir=data_dir, config_dir=config_dir)
    finally:
        engine.dispose()
