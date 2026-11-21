"""Behavioral tests for the replication corpus importer."""

from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess

import pytest

_REPO_ROOT = Path(__file__).resolve().parents[2]
_IMPORTER = _REPO_ROOT / "replication/scripts/import-databases.sh"


def _importer_command(*arguments: str) -> list[str]:
    if os.name != "nt":
        return [str(_IMPORTER), *arguments]

    git = shutil.which("git")
    candidates = []
    if git:
        git_root = Path(git).parent.parent
        candidates.extend([git_root / "bin/bash.exe", git_root / "usr/bin/bash.exe"])
    for variable, installation in (
        ("ProgramFiles", "Git"),
        ("LOCALAPPDATA", "Programs/Git"),
    ):
        if directory := os.environ.get(variable):
            candidates.append(Path(directory) / installation / "bin/bash.exe")
    for bash in candidates:
        if bash.is_file():
            return [str(bash), _IMPORTER.as_posix(), *arguments]
    raise FileNotFoundError(
        "Git for Windows Bash is required for shell integration tests"
    )


def _executable(path: Path, source: str) -> Path:
    path.write_text(source, encoding="utf-8", newline="\n")
    path.chmod(0o755)
    return path


def _fake_docker(tmp_path: Path) -> tuple[Path, Path]:
    commands = tmp_path / "commands"
    commands.mkdir()
    log = tmp_path / "commands.log"
    _executable(
        commands / "docker",
        r"""#!/usr/bin/env bash
set -u
printf 'docker\tproject=%s\t%s\n' "${COMPOSE_PROJECT_NAME:-}" "$*" >> "${FAKE_LOG:?}"
args=$*
if [[ $args == *" ps --status running --services postgres"* ]]; then
    printf 'postgres\n'
elif [[ $args == *"--resolve-release-corpus controlled"* ]]; then
    [[ ${FAIL_MANIFEST:-false} != true ]] || exit 9
    printf 'controlled_db\tcomponents/corpus-controlled/controlled_db.dump\t2\tcurrent\n'
elif [[ $args == *"teralizer.corpora prepare-corpus controlled"* ]]; then
    [[ ${FAIL_CORPUS_COMMAND:-} != prepare ]] || exit 9
    printf 'prepared controlled\n'
elif [[ $args == *"teralizer.corpora verify-corpus controlled"* ]]; then
    [[ ${FAIL_CORPUS_COMMAND:-} != verify ]] || exit 9
    printf 'verified controlled\n'
elif [[ $args == *" -v name=controlled_db -f -"* ]]; then
    cat >/dev/null
    [[ ${DATABASE_EXISTS:-false} != true ]] || printf '1\n'
elif [[ $args == *" createdb "* ]]; then
    :
elif [[ $args == *" cp "* ]]; then
    [[ ${FAIL_DOCKER_AT:-} != copy ]] || exit 9
elif [[ $args == *" pg_restore "* ]]; then
    [[ ${FAIL_DOCKER_AT:-} != restore ]] || exit 9
elif [[ $args == *"SELECT count(*) FROM project"* ]]; then
    [[ ${FAIL_DOCKER_AT:-} != count ]] || exit 9
    printf '%s\n' "${PROJECT_COUNT:-2}"
elif [[ $args == *" rm -f "* ]]; then
    :
elif [[ $args == *" dropdb "* ]]; then
    :
else
    printf 'unexpected fake docker invocation: %s\n' "$args" >&2
    exit 8
fi
""",
    )
    return commands, log


def _run_importer(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    **extra_environment: str,
) -> tuple[subprocess.CompletedProcess[str], list[str]]:
    commands, log = _fake_docker(tmp_path)
    monkeypatch.setenv("PATH", f"{commands}{os.pathsep}{os.environ['PATH']}")
    monkeypatch.setenv("FAKE_LOG", log.as_posix())
    monkeypatch.setenv("COMPOSE_PROJECT_NAME", "reviewer-installation")
    # The importer creates the default output and state directories in the
    # release root, which the test keeps out of the checkout.
    monkeypatch.setenv("TERALIZER_OUTPUT", (tmp_path / "output").as_posix())
    monkeypatch.setenv("TERALIZER_STATE", (tmp_path / "state").as_posix())
    for name, value in extra_environment.items():
        monkeypatch.setenv(name, value)
    result = subprocess.run(
        _importer_command("--force", "controlled"),
        capture_output=True,
        text=True,
        check=False,
    )
    return result, log.read_text(encoding="utf-8").splitlines()


def _line_index(lines: list[str], fragment: str) -> int:
    return next(index for index, line in enumerate(lines) if fragment in line)


def test_importer_verifies_then_restores_prepares_and_checks_report_access(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(tmp_path, monkeypatch)

    assert result.returncode == 0, result.stderr
    manifest_check = _line_index(lines, "--resolve-release-corpus controlled")
    database_create = _line_index(lines, " createdb ")
    copy = _line_index(lines, " cp ")
    prepare = _line_index(lines, "teralizer.corpora prepare-corpus controlled")
    report_check = _line_index(lines, "teralizer.corpora verify-corpus controlled")
    assert manifest_check < database_create < copy < prepare < report_check
    assert (
        f"{_REPO_ROOT.as_posix()}/components/corpus-controlled/controlled_db.dump"
        in lines[copy]
    )
    assert all("project=reviewer-installation" in line for line in lines)


def test_only_preparation_receives_the_owner_role(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(
        tmp_path,
        monkeypatch,
        REPLICATION_DB_USER="replication-user",
        REPLICATION_DB_PASSWORD="replication-password",
    )

    assert result.returncode == 0, result.stderr
    owner = "-e DB_USER=replication-user -e DB_PASSWORD=replication-password"
    prepare = lines[_line_index(lines, "prepare-corpus controlled")]
    report_check = lines[_line_index(lines, "verify-corpus controlled")]
    manifest_check = lines[_line_index(lines, "--resolve-release-corpus controlled")]
    assert owner in prepare
    assert "DB_PASSWORD" not in report_check
    assert "DB_PASSWORD" not in manifest_check


def test_author_database_environment_does_not_reach_the_services(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(
        tmp_path,
        monkeypatch,
        DB_HOST="author-host",
        DB_USER="author-user",
        DB_PASSWORD="author-password",
    )

    assert result.returncode == 0, result.stderr
    assert not any("author-" in line for line in lines)
    prepare = lines[_line_index(lines, "prepare-corpus controlled")]
    assert "-e DB_USER=teralizer -e DB_PASSWORD=teralizer" in prepare


def test_force_import_replaces_an_existing_corpus(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(tmp_path, monkeypatch, DATABASE_EXISTS="true")

    assert result.returncode == 0, result.stderr
    assert _line_index(lines, " dropdb ") < _line_index(lines, " createdb ")


def test_importer_requires_a_corpus():
    result = subprocess.run(
        _importer_command("--force"),
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 2
    assert "Name at least one corpus to restore" in result.stderr


def test_importer_rejects_a_mismatching_dump_before_database_changes(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(tmp_path, monkeypatch, FAIL_MANIFEST="true")

    assert result.returncode != 0
    assert "does not match the release manifest" in result.stderr
    assert not any(" createdb " in line for line in lines)


@pytest.mark.parametrize(
    ("environment", "value"),
    [
        ("FAIL_DOCKER_AT", "copy"),
        ("FAIL_DOCKER_AT", "restore"),
        ("FAIL_DOCKER_AT", "count"),
        ("FAIL_CORPUS_COMMAND", "prepare"),
        ("FAIL_CORPUS_COMMAND", "verify"),
    ],
)
def test_importer_removes_incomplete_database_after_failure(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    environment: str,
    value: str,
):
    result, lines = _run_importer(tmp_path, monkeypatch, **{environment: value})

    assert result.returncode != 0
    assert _line_index(lines, " createdb ") < _line_index(lines, " dropdb ")


def test_importer_rejects_wrong_project_count_with_observed_value(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(tmp_path, monkeypatch, PROJECT_COUNT="1")

    assert result.returncode != 0
    assert (
        "Corpus 'controlled' expects 2 projects. The restored database has 1."
        in result.stderr
    )
    assert any(" dropdb " in line for line in lines)
