import subprocess
from pathlib import Path

import pytest
from teralizer.release import provenance, source


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
def project(tmp_path: Path) -> tuple[Path, dict[str, str]]:
    library = tmp_path / "library"
    library.mkdir()
    _git(library, "init", "-q")
    _git(library, "remote", "add", "origin", "git@github.com:example/library.git")
    (library / "Lib.java").write_text("class Lib {}\n")
    library_commit = _commit(library, "library")

    project = tmp_path / "project"
    project.mkdir()
    _git(project, "init", "-q")
    _git(project, "remote", "add", "origin", "https://github.com/example/project.git")
    (project / "old.py").write_text("old\n")
    (project / "new.py").write_text("v1\n")
    old_commit = _commit(project, "first")
    (project / "new.py").write_text("v2\n")
    new_commit = _commit(project, "second")
    _git(project, "submodule", "add", "-q", str(library), "vendor/library")
    _git(
        project / "vendor/library",
        "remote",
        "set-url",
        "origin",
        "git@github.com:example/library.git",
    )
    submodule_commit = _commit(project, "library")
    return project, {
        "old": old_commit,
        "new": new_commit,
        "library": library_commit,
        "gitmodules": submodule_commit,
    }


def test_each_file_gets_its_own_commit_and_repository(
    project: tuple[Path, dict[str, str]],
):
    repo, commits = project
    nested = [source.NestedSource("vendor/library", commits["library"])]

    revisions = provenance.file_revisions(
        repo, nested, ["old.py", "new.py", "vendor/library/Lib.java"]
    )

    assert revisions["old.py"]["commit"] == commits["old"]
    assert revisions["new.py"]["commit"] == commits["new"]
    assert revisions["old.py"]["repository"] == "https://github.com/example/project"
    assert revisions["vendor/library/Lib.java"] == {
        "commit": commits["library"],
        "repository": "https://github.com/example/library",
        "path": "Lib.java",
    }


def test_dirty_checkout_is_refused(project: tuple[Path, dict[str, str]]):
    repo, _ = project
    (repo / "new.py").write_text("local change\n")

    with pytest.raises(provenance.ProvenanceError, match="clean checkout"):
        provenance.file_revisions(repo, [], ["new.py"])


def test_unattributed_files_are_refused(project: tuple[Path, dict[str, str]]):
    repo, _ = project

    with pytest.raises(provenance.ProvenanceError, match="no producing commit"):
        provenance.file_revisions(repo, [], ["generated.txt"])


def test_release_revisions_equal_checkout_revisions():
    """The commit a release records for a file is the one checkout mode reports."""
    from teralizer.eval import provenance as checkout

    repo = Path(__file__).resolve().parents[3]
    recorded = provenance.last_changing_commits(repo)
    for path in (
        "analysis/src/teralizer/eval/provenance.py",
        "analysis/src/teralizer/eval/reports/rq1_mutation_score.py",
        "src/main/resources/db/corpora.toml",
    ):
        assert recorded[path] == checkout.file_snapshot(path)[0]
