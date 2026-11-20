import subprocess
from pathlib import Path

import pytest
from teralizer.release import source


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-c", "protocol.file.allow=always", "-C", str(repo), *args],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _commit(repo: Path, message: str) -> str:
    _git(repo, "add", ".")
    _git(repo, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", message)
    return _git(repo, "rev-parse", "HEAD")


@pytest.fixture
def superproject(tmp_path: Path) -> tuple[Path, str]:
    library = tmp_path / "library"
    library.mkdir()
    _git(library, "init", "-q")
    (library / "Lib.java").write_text("class Lib {}\n")
    first = _commit(library, "first")
    (library / "Lib.java").write_text("class Lib { int later; }\n")
    _commit(library, "later")

    project = tmp_path / "project"
    project.mkdir()
    _git(project, "init", "-q")
    (project / "README.md").write_text("project\n")
    _git(project, "submodule", "add", "-q", str(library), "vendor/library")
    _git(project / "vendor/library", "checkout", "-q", first)
    _commit(project, "pin library")
    return project, first


def test_nested_sources_are_exported_at_their_recorded_commit(
    superproject: tuple[Path, str], tmp_path: Path
):
    project, first = superproject
    # The worktree moves on, but the release must use the recorded gitlink.
    later = _git(project / "vendor/library", "rev-parse", "origin/HEAD")
    _git(project / "vendor/library", "checkout", "-q", later)

    nested, _ = source.materialize(project, "HEAD", tmp_path / "out")

    assert nested == [source.NestedSource("vendor/library", first)]
    assert (tmp_path / "out/vendor/library/Lib.java").read_text() == "class Lib {}\n"
    assert not (tmp_path / "out/vendor/library/.git").exists()


def test_uninitialized_submodule_fails_instead_of_exporting_a_gitlink(
    superproject: tuple[Path, str], tmp_path: Path
):
    project, _ = superproject
    _git(project, "submodule", "deinit", "-q", "--force", "vendor/library")

    with pytest.raises(source.SourceError, match="vendor/library is not checked out"):
        source.materialize(project, "HEAD", tmp_path / "out")


def test_destination_must_be_empty(superproject: tuple[Path, str], tmp_path: Path):
    project, _ = superproject
    (tmp_path / "out").mkdir()
    (tmp_path / "out/stale.txt").write_text("old\n")

    with pytest.raises(source.SourceError, match="not empty"):
        source.materialize(project, "HEAD", tmp_path / "out")
