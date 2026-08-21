"""Tests for database-local corpus export and resumable transfer."""

from __future__ import annotations

import hashlib
import os
import subprocess
from pathlib import Path

import pytest

from teralizer import corpora, corpus_export

_REPO_ROOT = Path(__file__).resolve().parents[2]
_EXPORT_WRAPPER = _REPO_ROOT / "replication/scripts/export-databases.sh"


def _entry(corpus_id: str = "controlled", database: str = "controlled_db"):
    return corpora.CorpusEntry(
        corpus_id, database, None, None, 2, True, "fixture", True
    )


def _fake_docker(path: Path) -> Path:
    path.write_text(
        """#!/bin/sh
case "$*" in
  *"pg_dump --version"*) echo "pg_dump (PostgreSQL) 17.10" ;;
  *"SELECT count(*) FROM project"*) echo 2 ;;
  *"SELECT pg_database_size(current_database())"*) echo 100 ;;
  *" pg_dump "*)
    if [ "${FAIL_DUMP:-false}" = true ]; then exit 9; fi
    printf archive
    ;;
  *) echo "unexpected fake docker invocation: $*" >&2; exit 8 ;;
esac
""",
        encoding="utf-8",
    )
    path.chmod(0o755)
    return path


def test_remote_worker_keeps_complete_export_when_later_export_fails(
    tmp_path: Path, monkeypatch
):
    docker = _fake_docker(tmp_path / "docker")
    script = corpus_export._REMOTE_SCRIPT
    spool = tmp_path / "spool"
    first = _entry()
    command = [
        str(script),
        str(spool),
        first.id,
        first.database,
        "2",
        str(docker),
        "postgres",
        "postgres",
        "false",
    ]

    subprocess.run(command, check=True, capture_output=True, text=True)
    completed = spool / "controlled.complete"
    assert (completed / "controlled_db.dump").read_bytes() == b"archive"

    monkeypatch.setenv("FAIL_DUMP", "true")
    subprocess.run(command, check=True, capture_output=True, text=True)
    failed = _entry("later", "later_db")
    failed_command = command.copy()
    failed_command[2:4] = [failed.id, failed.database]
    with pytest.raises(subprocess.CalledProcessError):
        subprocess.run(failed_command, check=True, capture_output=True, text=True)

    assert (completed / "controlled_db.dump").read_bytes() == b"archive"
    assert (spool / "later.partial").is_dir()
    assert not (spool / "later.complete").exists()


def test_transfer_uses_partial_file_then_reuses_verified_archive(tmp_path: Path):
    payload = b"completed archive"
    entry = _entry()
    fact = corpus_export.ExportFact(
        entry.id,
        entry.database,
        entry.expected_projects,
        100,
        hashlib.sha256(payload).hexdigest(),
        len(payload),
    )
    endpoint = corpus_export.ExportEndpoint(
        "data-host", "/exports", "/docker", "postgres", "postgres"
    )
    output = tmp_path / "exports"
    output.mkdir()
    partial = output / ".controlled_db.dump.partial"
    partial.write_bytes(b"interrupted")
    calls = []

    def resume(args, **_kwargs):
        calls.append(args)
        Path(args[-1]).write_bytes(payload)
        return subprocess.CompletedProcess(args, 0, "", "")

    destination = corpus_export.transfer_corpus(
        entry, fact, endpoint, output, runner=resume
    )

    assert destination.read_bytes() == payload
    assert calls[0][0:2] == ["rsync", "--partial"]
    assert "ControlMaster=no" in calls[0][3]
    assert not partial.exists()

    def unexpected(_args, **_kwargs):
        raise AssertionError("verified destination must not be transferred again")

    assert (
        corpus_export.transfer_corpus(entry, fact, endpoint, output, runner=unexpected)
        == destination
    )


def test_export_wrapper_resolves_paths_before_uv_changes_directory(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    commands = tmp_path / "commands"
    commands.mkdir()
    log = tmp_path / "uv.log"
    uv = commands / "uv"
    uv.write_text(
        '#!/bin/sh\nprintf \'%s\\n\' "$*" >> "${FAKE_LOG:?}"\n',
        encoding="utf-8",
    )
    uv.chmod(0o755)
    monkeypatch.setenv("PATH", f"{commands}:{os.environ['PATH']}")
    monkeypatch.setenv("FAKE_LOG", str(log))
    monkeypatch.setenv("CORPUS_EXPORT_HOST", "data-host")
    monkeypatch.setenv("CORPUS_EXPORT_SPOOL", "/exports")
    monkeypatch.setenv("CORPUS_EXPORT_DOCKER", "/docker")
    monkeypatch.setenv("CORPUS_EXPORT_CONTAINER", "postgres")
    monkeypatch.setenv("CORPUS_EXPORT_DUMP_DIR", "staging")

    result = subprocess.run(
        [str(_EXPORT_WRAPPER), "package"],
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 0, result.stderr
    lines = log.read_text(encoding="utf-8").splitlines()
    assert f"--output-dir {tmp_path / 'staging'}" in lines[1]
    assert f"--assemble-from {tmp_path / 'staging'}" in lines[2]
    assert f"--output-dir {tmp_path / 'package'}" in lines[2]
