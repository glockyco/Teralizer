"""Exercise packaged collection with fake Docker and a disposable release tree."""

from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import subprocess

import pytest

_REPO_ROOT = Path(__file__).resolve().parents[2]
_SCRIPTS = (
    "artifact",
    "replication/scripts/run.sh",
    "replication/docker-compose.yml",
    "scripts/corpus-registry",
    "scripts/lib/db-guard.sh",
    "scripts/lib/db-lifecycle.sh",
    "scripts/lib/jarvis-run.sh",
    "scripts/lib/psql.sh",
    "scripts/lib/run-supervisor.sh",
    "scripts/run-jarvis-census.sh",
    "scripts/run-jarvis-scoreboard.sh",
)
_CLI = "commons-cli-2017-02-01-census"
_CSV = "commons-csv-2017-02-01-census"
_LANG = "commons-lang-2017-02-01-census"
_CENSUS_DATA = "data/jarvis-census"

_FAKE_DOCKER = r"""#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "${FAKE_DOCKER_LOG:?}"
case "$*" in
    info) ;;
    'info --format {{.Architecture}}') printf 'x86_64\n' ;;
    'compose version --short') printf '2.25.0\n' ;;
    *' build -q analysis'|*' build -q teralizer'|*' up -d --wait postgres'|*' down --volumes --remove-orphans') ;;
    *' -m teralizer.release preflight '*) ;;
    *' -m teralizer.corpora export controlled')
        printf 'DB_NAME=published_controlled\nCONFIG_DIR=project-configs/primary\n' ;;
    *' -m teralizer.corpora export real-world')
        printf 'DB_NAME=published_real_world\nCONFIG_DIR=project-configs/replication/extended\n' ;;
    *' -m teralizer.corpora get jarvis-'*' database') printf 'published_jarvis\n' ;;
    *' -m teralizer.corpora classify scratch_'*) printf 'scratch\n' ;;
    *' -m teralizer.corpora classify '*) printf 'protected\n' ;;
    *' exec -T postgres psql '*" -c SELECT 1"|*' exec -T postgres psql '*"SELECT 1 FROM pg_database "*) printf '1\n' ;;
    *' exec -T postgres psql '*"FROM task"*) printf '0\n' ;;
    *' exec -T postgres psql '*|*' exec -T postgres createdb '*) ;;
    'rm -f '*) ;;
    *' teralizer find '*)
        while [[ $1 != find ]]; do shift; done
        directory=$2
        shift 2
        find "${TERALIZER_COLLECT_DATA:?}${directory#/app/data}" "$@" ;;
    *' teralizer ./gradlew run '*)
        printf 'pipeline output\n'
        if [[ -n ${FAKE_PAUSE_CONFIG:-} && "$*" == *"$FAKE_PAUSE_CONFIG"* ]]; then
            touch "${REPLICATION_RUN_STATE_DIR:?}/STOP"
        fi ;;
    *) printf 'unexpected Docker command: %s\n' "$*" >&2; exit 8 ;;
esac
"""


def _file(path: Path, contents: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8", newline="\n")
    return path


def _executable(path: Path, source: str) -> None:
    _file(path, source).chmod(0o755)


def _bash() -> str:
    if os.name != "nt":
        return "/bin/bash"
    git = shutil.which("git")
    installations = [Path(git).parent.parent] if git else []
    for variable, relative in (
        ("ProgramFiles", "Git"),
        ("LOCALAPPDATA", "Programs/Git"),
    ):
        if location := os.environ.get(variable):
            installations.append(Path(location) / relative)
    for installation in installations:
        bash = installation / "bin/bash.exe"
        if bash.is_file():
            return str(bash)
    raise FileNotFoundError(
        "Git for Windows Bash is required for shell integration tests"
    )


@dataclass
class Release:
    root: Path
    state: Path
    docker_log: Path
    environment: dict[str, str]

    def run(
        self, script: str, *args: str, **env: str
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [_bash(), script, *args],
            cwd=self.root,
            env={**self.environment, **env},
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )

    def docker_commands(self) -> list[str]:
        return self.docker_log.read_text(encoding="utf-8").splitlines()

    def pipeline_runs(self) -> list[str]:
        return [
            line
            for line in self.docker_commands()
            if " teralizer ./gradlew run " in line
        ]

    def config(self, name: str, project: str) -> None:
        _file(
            self.root / "project-configs" / f"{name}.conf",
            f'    root-path = "{project}"\n',
        )


@pytest.fixture
def release(tmp_path: Path) -> Release:
    root = tmp_path / "teralizer"
    for name in _SCRIPTS:
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(_REPO_ROOT / name, target)
    _executable(root / "gradlew", "#!/usr/bin/env bash\nexit 91\n")
    manifest = _file(tmp_path / "release-manifest.json", "{}")
    commands = tmp_path / "commands"
    log = _file(tmp_path / "docker.log", "")
    _executable(commands / "docker", _FAKE_DOCKER)
    # The supervisor's 15-second wait is unnecessary for fake commands.
    _executable(commands / "sleep", "#!/usr/bin/env bash\nexit 0\n")
    _executable(
        commands / "uv", "#!/usr/bin/env bash\nprintf 'host uv ran\n' >&2\nexit 92\n"
    )
    state = tmp_path / "scratch state"
    output = tmp_path / "output"
    output.mkdir()
    state.mkdir()
    env = {
        name: value
        for name, value in os.environ.items()
        if not name.startswith(
            ("TERALIZER_", "REPLICATION_", "JARVIS_", "COMPOSE_", "DB_")
        )
    }
    env.update(
        PATH=f"{commands}{os.pathsep}{os.environ['PATH']}",
        COMPOSE_PROJECT_NAME="other-reviewer",
        FAKE_DOCKER_LOG=log.as_posix(),
        TERALIZER_RELEASE_MANIFEST=manifest.as_posix(),
        TERALIZER_OUTPUT=output.as_posix(),
        TERALIZER_STATE=state.as_posix(),
    )
    return Release(root, state, log, env)


def test_sample_selects_exact_config_roots_in_compose(release: Release):
    projects_root = release.state / "work"
    for number, project in ((1, "alpha-extra"), (2, "alpha"), (10, "beta")):
        release.config(f"replication/extended/project-{number}", f"projects/{project}")
        (projects_root / "projects" / project).mkdir(parents=True)
    sample = _file(release.root / "picked.txt", "beta\nalpha")
    ledger = release.state / "ledger"

    result = release.run(
        "replication/scripts/run.sh",
        "--corpus",
        "real-world",
        "--sample",
        sample.as_posix(),
        "--docker",
        "--yes",
        TERALIZER_COMPOSE_FILE="replication/docker-compose.yml",
        REPLICATION_PROJECTS_ROOT=projects_root.as_posix(),
        REPLICATION_RUN_STATE_DIR=ledger.as_posix(),
    )

    assert result.returncode == 0, result.stdout + result.stderr
    assert "Continue?" not in result.stdout
    commands = release.pipeline_runs()
    assert len(commands) == 2
    for config, command in zip(("project-2", "project-10"), commands, strict=True):
        assert f"--name other-reviewer-collect-real-world-{config} " in command
        assert f"/app/project-configs/replication/extended/{config}.conf" in command
    assert (ledger / "status.tsv").read_text(encoding="utf-8").splitlines()[1:] == [
        "project-2\t0\t-",
        "project-10\t0\t-",
    ]
    assert not (release.root / "projects").exists()


def test_sample_reports_each_missing_project_config_or_directory(release: Release):
    projects_root = release.state / "work"
    (projects_root / "projects/unconfigured").mkdir(parents=True)
    (release.root / "projects/only-in-core").mkdir(parents=True)
    release.config("replication/extended/project-1", "projects/only-in-core")
    sample = _file(release.root / "picked.txt", "unconfigured\nonly-in-core\n")

    result = release.run(
        "replication/scripts/run.sh",
        "--corpus",
        "real-world",
        "--sample",
        sample.as_posix(),
        "--yes",
        TERALIZER_COMPOSE_FILE="replication/docker-compose.yml",
        REPLICATION_PROJECTS_ROOT=projects_root.as_posix(),
    )

    assert result.returncode != 0
    assert "'unconfigured' has no config" in result.stderr
    assert "'only-in-core' has no directory" in result.stderr
    assert not release.pipeline_runs()


def test_jarvis_compose_runs_in_container_and_writes_ledger_to_data_root(
    release: Release,
):
    config = f"project-configs/jarvis-scoreboard/{_CLI}.conf"
    release.config(f"jarvis-scoreboard/{_CLI}", f"{_CENSUS_DATA}/fixtures/{_CLI}")
    data_root = release.state / "work"
    data = data_root / _CENSUS_DATA
    leftover = _file(data / f"fixtures/{_CLI}/_ExampleGeneralized_Test.java", "old")

    result = release.run(
        "scripts/run-jarvis-census.sh",
        config,
        TERALIZER_COMPOSE_FILE="replication/docker-compose.yml",
        JARVIS_DATA_ROOT=data_root.as_posix(),
        DB_USER="unrelated_author_role",
    )

    assert result.returncode == 0, result.stdout + result.stderr
    (command,) = release.pipeline_runs()
    assert f"--name other-reviewer-collect-census-{_CLI} " in command
    assert f"-Dteralizer.config={config} " in command
    assert f"-Dteralizer.data-dir={_CENSUS_DATA} " in command
    assert not leftover.exists()
    assert (data / "complete").is_file()
    assert (data / f"run-logs/{_CLI}.log").read_text(
        encoding="utf-8"
    ) == "pipeline output\n"
    assert (data / "status.tsv").read_text(encoding="utf-8").splitlines()[1:] == [
        f"{_CLI}\t0\t{_CENSUS_DATA}/run-logs/{_CLI}.log"
    ]
    assert not (release.root / _CENSUS_DATA).exists()
    assert any(
        " exec -T postgres psql -U teralizer " in line
        for line in release.docker_commands()
    )
    assert "unrelated_author_role" not in "\n".join(release.docker_commands())


def test_jarvis_compose_refuses_fixture_preparation(release: Release):
    result = release.run(
        "scripts/run-jarvis-census.sh",
        "--prepare-fixtures",
        TERALIZER_COMPOSE_FILE="replication/docker-compose.yml",
    )

    assert result.returncode != 0
    assert "--prepare-fixtures is not available in Compose mode" in result.stderr
    assert not release.docker_commands()


def test_reduced_census_copies_and_runs_only_two_fixtures(release: Release):
    component = release.root / "components/projects-jarvis-census" / _CENSUS_DATA
    for project in (_CLI, _CSV, _LANG):
        release.config(
            f"jarvis-scoreboard/{project}", f"{_CENSUS_DATA}/fixtures/{project}"
        )
        _file(component / f"fixtures/{project}/pom.xml", f"original {project}")

    result = release.run("artifact", "collect-reduced", "jarvis-benchmark")

    assert result.returncode == 0, result.stdout + result.stderr
    data = release.state / "collect/jarvis-benchmark" / _CENSUS_DATA
    assert {path.name for path in (data / "fixtures").iterdir()} == {_CLI, _CSV}
    assert len(release.pipeline_runs()) == 2
    for project, command in zip((_CLI, _CSV), release.pipeline_runs(), strict=True):
        assert (
            f"-Dteralizer.config=project-configs/jarvis-scoreboard/{project}.conf "
            in command
        )
        assert (data / f"fixtures/{project}/pom.xml").read_text(
            encoding="utf-8"
        ) == f"original {project}"
    assert any(
        "preflight collect-jarvis-census " in line for line in release.docker_commands()
    )
    assert "Scratch database: scratch_jarvis_census" in result.stdout
    assert "Run ledger: " in result.stdout

    _file(data / f"fixtures/{_CLI}/pom.xml", "changed working copy")
    result = release.run("artifact", "collect-reduced", "jarvis-benchmark")

    assert result.returncode == 0, result.stdout + result.stderr
    assert (data / f"fixtures/{_CLI}/pom.xml").read_text(
        encoding="utf-8"
    ) == "changed working copy"
    assert (component / f"fixtures/{_CLI}/pom.xml").read_text(
        encoding="utf-8"
    ) == f"original {_CLI}"
    assert len(release.pipeline_runs()) == 4
    assert len((data / "status.tsv").read_text(encoding="utf-8").splitlines()) == 3


def test_controlled_full_pauses_then_resumes_into_generalization(release: Release):
    project = "commons-utils-es-default-1s"
    _file(
        release.root / f"components/projects-controlled/projects/{project}/pom.xml",
        "fixture",
    )
    for phase in ("generation", "generalization"):
        release.config(f"primary/{phase}/commons-utils-1s", f"projects/{project}")

    paused = release.run(
        "artifact",
        "collect-full",
        "controlled",
        FAKE_PAUSE_CONFIG="/primary/generation/commons-utils-1s.conf",
    )

    assert paused.returncode == 0, paused.stdout + paused.stderr
    assert "Collection paused" in paused.stdout
    (generation,) = release.pipeline_runs()
    assert "/primary/generation/commons-utils-1s.conf" in generation
    run_state = release.state / "collect/controlled/run-state"
    assert not (run_state / "STOP").exists()

    resumed = release.run("artifact", "collect-full", "controlled")

    assert resumed.returncode == 0, resumed.stdout + resumed.stderr
    first, generalization = release.pipeline_runs()
    assert first == generation
    assert "/primary/generalization/commons-utils-1s.conf" in generalization
    for phase in ("generation", "generalization"):
        assert (run_state / phase / "done/commons-utils-1s").is_file()


def test_real_world_scopes_use_separate_project_copies_and_databases(release: Release):
    release.config("replication/extended/project-1", "projects/alpha")
    release.config("replication/extended/project-2", "projects/beta")
    _file(release.root / "project-configs/replication/real-world-sample.txt", "alpha\n")
    for scope in ("sample", "full"):
        projects = release.root / f"components/projects-real-world-{scope}/projects"
        _file(projects / "alpha/pom.xml", scope)
        _file(projects / "beta/pom.xml", "other project")

    reduced = release.run("artifact", "collect-reduced", "real-world")
    assert reduced.returncode == 0, reduced.stdout + reduced.stderr
    (sample_run,) = release.pipeline_runs()
    assert (
        "-Dteralizer.database.name=scratch_replication_real_world_sample " in sample_run
    )
    sample = release.state / "collect/real-world-sample/projects"
    assert {path.name for path in sample.iterdir()} == {"alpha"}
    assert (sample / "alpha/pom.xml").read_text(encoding="utf-8") == "sample"

    full = release.run("artifact", "collect-full", "real-world")
    assert full.returncode == 0, full.stdout + full.stderr
    _, *full_runs = release.pipeline_runs()
    assert len(full_runs) == 2
    assert all(
        "-Dteralizer.database.name=scratch_replication_real_world " in line
        for line in full_runs
    )
    full_projects = release.state / "collect/real-world/projects"
    assert {path.name for path in full_projects.iterdir()} == {"alpha", "beta"}
    assert (full_projects / "alpha/pom.xml").read_text(encoding="utf-8") == "full"


def test_clean_removes_workspace_files_but_keeps_component(release: Release):
    working_file = _file(
        release.state / "collect/controlled/projects/project/target/generated", "work"
    )
    source_file = _file(
        release.root / "components/projects-controlled/projects/project/pom.xml",
        "source",
    )

    result = release.run("artifact", "clean")

    assert result.returncode == 0, result.stdout + result.stderr
    assert not working_file.exists()
    assert not release.state.exists()
    assert source_file.read_text(encoding="utf-8") == "source"
    assert any(
        " teralizer find /app/data -mindepth 1 -delete" in line
        for line in release.docker_commands()
    )
