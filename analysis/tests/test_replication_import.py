"""Behavioral tests for the replication corpus importer."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess

import pytest

_REPO_ROOT = Path(__file__).resolve().parents[2]
_IMPORTER = _REPO_ROOT / "replication/scripts/import-databases.sh"


def _executable(path: Path, source: str) -> Path:
    path.write_text(source, encoding="utf-8")
    path.chmod(0o755)
    return path


def _fake_commands(tmp_path: Path) -> tuple[Path, Path]:
    commands = tmp_path / "commands"
    commands.mkdir()
    log = tmp_path / "commands.log"
    _executable(
        commands / "uv",
        r"""#!/usr/bin/env bash
set -u
printf 'uv\t%s\n' "$*" >> "${FAKE_LOG:?}"
args=$*
if [[ $args == *"--verify-package"* ]]; then
    [[ ${FAIL_PACKAGE:-false} != true ]] || exit 9
    printf '%s/manifest.json\n' "${FAKE_PACKAGE:?}"
elif [[ $args == *"--resolve-package-corpus controlled"* ]]; then
    printf 'controlled_db\tcontrolled_db.dump\t2\tcurrent\n'
elif [[ $args == *"teralizer.corpora prepare-corpus controlled"* ]]; then
    printf 'registry-cwd\t%s\n' "$PWD" >> "${FAKE_LOG:?}"
    [[ ${FAIL_CORPUS_COMMAND:-} != prepare ]] || exit 9
    printf 'connection\t%s\t%s\t%s\t%s\t%s\n' \
        "${DB_HOST:-}" "${DB_PORT:-}" "${DB_USER:-}" \
        "${REPORT_DB_USER:-}" "${REPORT_DB_PASSWORD:-}" >> "${FAKE_LOG:?}"
    printf 'prepared controlled\n'
elif [[ $args == *"teralizer.corpora verify-corpus controlled"* ]]; then
    printf 'registry-cwd\t%s\n' "$PWD" >> "${FAKE_LOG:?}"
    [[ ${FAIL_CORPUS_COMMAND:-} != verify ]] || exit 9
    printf 'verified controlled\n'
else
    printf 'unexpected fake uv invocation: %s\n' "$args" >&2
    exit 8
fi
""",
    )
    _executable(
        commands / "docker",
        r"""#!/usr/bin/env bash
set -u
printf 'docker\tproject=%s\t%s\n' "${COMPOSE_PROJECT_NAME:-}" "$*" >> "${FAKE_LOG:?}"
args=$*
if [[ $args == *" ps --status running --services postgres"* ]]; then
    printf 'postgres\n'
elif [[ $args == *"SELECT 1 FROM pg_database"* ]]; then
    :
elif [[ $args == *" createdb "* ]]; then
    :
elif [[ $args == *" cp "* ]]; then
    [[ ${FAIL_DOCKER_AT:-} != copy ]] || exit 9
elif [[ $args == *" pg_restore "* ]]; then
    [[ ${FAIL_DOCKER_AT:-} != restore ]] || exit 9
elif [[ $args == *"SELECT count(*) FROM project"* ]]; then
    [[ ${FAIL_DOCKER_AT:-} != count ]] || exit 9
    printf '2\n'
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
    commands, log = _fake_commands(tmp_path)
    package = tmp_path / "package"
    package.mkdir()
    monkeypatch.setenv("PATH", f"{commands}{os.pathsep}{os.environ['PATH']}")
    monkeypatch.setenv("FAKE_LOG", str(log))
    monkeypatch.setenv("FAKE_PACKAGE", str(package))
    monkeypatch.setenv("COMPOSE_PROJECT_NAME", "reviewer-installation")
    for name, value in extra_environment.items():
        monkeypatch.setenv(name, value)
    result = subprocess.run(
        [str(_IMPORTER), "--force", "--corpus", "controlled", str(package)],
        capture_output=True,
        text=True,
        check=False,
    )
    return result, log.read_text(encoding="utf-8").splitlines()


def _line_index(lines: list[str], fragment: str) -> int:
    return next(index for index, line in enumerate(lines) if fragment in line)


def test_importer_verifies_then_restores_prepares_and_preflights(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(tmp_path, monkeypatch)

    assert result.returncode == 0, result.stderr
    package_check = _line_index(lines, "--verify-package")
    package_entry = _line_index(lines, "--resolve-package-corpus controlled")
    database_create = _line_index(lines, " createdb ")
    prepare = _line_index(lines, "teralizer.corpora prepare-corpus controlled")
    report_preflight = _line_index(lines, "teralizer.corpora verify-corpus controlled")
    assert package_check < package_entry < database_create < prepare < report_preflight
    assert all(
        "project=reviewer-installation" in line
        for line in lines
        if line.startswith("docker\t")
    )
    assert all("postgres-teralizer" not in line for line in lines)
    assert [line for line in lines if line.startswith("registry-cwd\t")] == [
        f"registry-cwd\t{_REPO_ROOT}",
        f"registry-cwd\t{_REPO_ROOT}",
    ]


def test_importer_rejects_package_before_database_changes(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(tmp_path, monkeypatch, FAIL_PACKAGE="true")

    assert result.returncode != 0
    assert any("--verify-package" in line for line in lines)
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


def test_importer_ignores_author_database_environment(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(
        tmp_path,
        monkeypatch,
        DB_HOST="author-host",
        DB_PORT="6500",
        DB_USER="author-user",
        DB_PASSWORD="author-password",
        REPORT_DB_USER="author-report",
        REPORT_DB_PASSWORD="author-report-password",
    )

    assert result.returncode == 0, result.stderr
    assert (
        "connection\t127.0.0.1\t5432\tteralizer\tteralizer_report\tteralizer-report"
        in lines
    )


def test_importer_maps_explicit_replication_environment(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    result, lines = _run_importer(
        tmp_path,
        monkeypatch,
        REPLICATION_DB_HOST="replication-host",
        REPLICATION_DB_PORT="6543",
        REPLICATION_DB_USER="replication-user",
        REPLICATION_DB_PASSWORD="replication-password",
        REPLICATION_REPORT_DB_USER="replication-report",
        REPLICATION_REPORT_DB_PASSWORD="replication-report-password",
    )

    assert result.returncode == 0, result.stderr
    assert (
        "connection\treplication-host\t6543\treplication-user"
        "\treplication-report\treplication-report-password"
    ) in lines
