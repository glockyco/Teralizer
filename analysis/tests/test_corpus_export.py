"""Tests for database-local corpus export and resumable transfer."""

from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
from pathlib import Path

import pytest

from teralizer import corpora, corpus_export

_REPO_ROOT = Path(__file__).resolve().parents[2]
_EXPORT_WRAPPER = _REPO_ROOT / "replication/scripts/export-databases.sh"


def _shell_command(script: Path, *args: str) -> list[str]:
    if os.name != "nt":
        return [str(script), *args]
    git = shutil.which("git")
    candidates = [
        Path(git).parent.parent / "bin/bash.exe" if git else None,
        Path(git).parent.parent / "usr/bin/bash.exe" if git else None,
        Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe",
        Path(os.environ.get("LOCALAPPDATA", "")) / "Programs/Git/bin/bash.exe",
    ]
    for candidate in candidates:
        if candidate is not None and candidate.is_file():
            return [str(candidate), script.as_posix(), *args]
    raise AssertionError("Git for Windows Bash is required for shell integration tests")


def _shell_path(path: Path) -> str:
    if os.name != "nt":
        return str(path)
    command = _shell_command(_EXPORT_WRAPPER)
    return subprocess.check_output(
        [command[0], "-c", 'cygpath -u "$1"', "bash", str(path)],
        text=True,
    ).strip()


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
        newline="\n",
    )
    path.chmod(0o755)
    return path


def test_remote_worker_keeps_complete_export_when_later_export_fails(
    tmp_path: Path, monkeypatch
):
    if os.name == "nt":
        # Git Bash provides sha256sum rather than the remote host's shasum.
        shasum = tmp_path / "shasum"
        shasum.write_text(
            "#!/bin/sh\n"
            '[ "$1" = "-a" ] && [ "$2" = "256" ] || exit 2\n'
            'shift 2\nexec sha256sum "$@"\n',
            encoding="utf-8",
            newline="\n",
        )
        shasum.chmod(0o755)
        monkeypatch.setenv("PATH", f"{tmp_path}{os.pathsep}{os.environ['PATH']}")
    docker = _fake_docker(tmp_path / "docker")
    script = corpus_export._REMOTE_SCRIPT
    spool = tmp_path / "spool"
    first = _entry()
    command = [
        script.as_posix(),
        _shell_path(spool),
        first.id,
        first.database,
        "2",
        _shell_path(docker),
        "postgres",
        "postgres",
        "false",
    ]

    subprocess.run(
        _shell_command(script, *command[1:]),
        check=True,
        capture_output=True,
        text=True,
    )
    completed = spool / "controlled.complete"
    assert (completed / "controlled_db.dump").read_bytes() == b"archive"
    facts = (completed / "facts.tsv").read_text(encoding="utf-8").split()
    assert facts[-2] == hashlib.sha256(b"archive").hexdigest()
    assert facts[-1] == str(len(b"archive"))

    monkeypatch.setenv("FAIL_DUMP", "true")
    subprocess.run(
        _shell_command(script, *command[1:]),
        check=True,
        capture_output=True,
        text=True,
    )
    failed = _entry("later", "later_db")
    failed_command = command.copy()
    failed_command[2:4] = [failed.id, failed.database]
    with pytest.raises(subprocess.CalledProcessError):
        subprocess.run(
            _shell_command(script, *failed_command[1:]),
            check=True,
            capture_output=True,
            text=True,
        )

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
        newline="\n",
    )
    uv.chmod(0o755)
    monkeypatch.setenv("PATH", f"{commands}{os.pathsep}{os.environ['PATH']}")
    monkeypatch.setenv("FAKE_LOG", _shell_path(log))
    monkeypatch.setenv("CORPUS_EXPORT_HOST", "data-host")
    monkeypatch.setenv("CORPUS_EXPORT_SPOOL", "/exports")
    monkeypatch.setenv("CORPUS_EXPORT_DOCKER", "/docker")
    monkeypatch.setenv("CORPUS_EXPORT_CONTAINER", "postgres")
    monkeypatch.setenv("CORPUS_EXPORT_DUMP_DIR", "staging")

    result = subprocess.run(
        _shell_command(_EXPORT_WRAPPER, "package"),
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 0, result.stderr
    lines = log.read_text(encoding="utf-8").splitlines()
    assert f"--output-dir {_shell_path(tmp_path / 'staging')}" in lines[1]
    assert f"--assemble-from {_shell_path(tmp_path / 'staging')}" in lines[2]
    assert f"--output-dir {_shell_path(tmp_path / 'package')}" in lines[2]


def test_export_cli_rejects_checkout_output():
    with pytest.raises(SystemExit):
        corpus_export.main(
            [
                "--ssh-host",
                "data-host",
                "--remote-spool",
                "/exports",
                "--docker",
                "/docker",
                "--postgres-container",
                "postgres",
                "--output-dir",
                str(_REPO_ROOT / "analysis/build/corpus-exports"),
            ]
        )


def test_export_wrapper_requires_explicit_dump_staging(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    monkeypatch.setenv("CORPUS_EXPORT_HOST", "data-host")
    monkeypatch.setenv("CORPUS_EXPORT_SPOOL", "/exports")
    monkeypatch.setenv("CORPUS_EXPORT_DOCKER", "/docker")
    monkeypatch.setenv("CORPUS_EXPORT_CONTAINER", "postgres")
    monkeypatch.delenv("CORPUS_EXPORT_DUMP_DIR", raising=False)

    result = subprocess.run(
        _shell_command(_EXPORT_WRAPPER, _shell_path(tmp_path / "package")),
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 2
    assert "CORPUS_EXPORT_DUMP_DIR is required" in result.stderr
