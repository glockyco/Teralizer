import os
from collections.abc import Sequence
import shutil
import subprocess
import unicodedata
from pathlib import Path

import pytest
from teralizer.release import realworld

_ORIGIN = "https://github.com/example/project.git"
_PROJECT = "github_com_example_project"
# "中文.txt" in GBK, which is not valid UTF-8.
_GBK_NAME = b"src/\xd6\xd0\xce\xc4.txt"
_MEASURED_GBK_NAME = unicodedata.normalize("NFD", _GBK_NAME.decode("latin-1"))


def _git(repo: Path, *arguments: str, stdin: bytes | None = None, env=None) -> str:
    return (
        subprocess.run(
            ["git", "-C", str(repo), *arguments],
            input=stdin,
            capture_output=True,
            check=True,
            env=env,
        )
        .stdout.decode()
        .strip()
    )


def _blob(repo: Path, content: bytes) -> str:
    return _git(repo, "hash-object", "-w", "--stdin", stdin=content)


def _repository(
    tmp_path: Path, extra: Sequence[tuple[str, bytes, bytes]] = ()
) -> tuple[Path, str]:
    """A checkout whose revision is built with plumbing, so that paths differing
    only in case or not valid UTF-8 exist on any file system."""
    repo = tmp_path / "checkouts" / _PROJECT
    repo.mkdir(parents=True)
    _git(repo, "init", "-q")
    _git(repo, "remote", "add", "origin", _ORIGIN)
    entries = [
        ("100644", b"<project/>\n", b"pom.xml"),
        ("100755", b"#!/bin/sh\n", b"mvnw"),
        ("120000", b"pom.xml", b"alias.xml"),
        *extra,
    ]
    index_info = b"".join(
        f"{mode} {_blob(repo, content)}\t".encode() + path + b"\n"
        for mode, content, path in entries
    )
    index = {**os.environ, "GIT_INDEX_FILE": str(tmp_path / "index")}
    _git(repo, "update-index", "--index-info", stdin=index_info, env=index)
    tree = _git(repo, "write-tree", env=index)
    author = {
        **os.environ,
        "GIT_AUTHOR_NAME": "a",
        "GIT_AUTHOR_EMAIL": "a@example.org",
        "GIT_COMMITTER_NAME": "a",
        "GIT_COMMITTER_EMAIL": "a@example.org",
    }
    return repo, _git(repo, "commit-tree", tree, "-m", "measured", env=author)


def _map(tmp_path: Path, revision: str, origin: str = _ORIGIN) -> Path:
    path = tmp_path / "checkout-revisions.tsv"
    path.write_text(f"project\trevision\torigin\n{_PROJECT}\t{revision}\t{origin}\n")
    return path


def _deviations(tmp_path: Path, *rows: tuple[str, str, str]) -> Path:
    path = tmp_path / "deviations.tsv"
    path.write_text(
        "project\tkind\tpath\tblob\n"
        + "".join(f"{_PROJECT}\t{kind}\t{p}\t{blob}\n" for kind, p, blob in rows)
    )
    return path


class _GitCheckout:
    """Files of a revision with every LF written as CRLF, standing in for the JGit
    checkout, which tests in this module cannot assume a JVM for."""

    def files(self, repository: Path, revision: str):
        listing = subprocess.run(
            ["git", "-C", str(repository), "ls-tree", "-r", "-z", revision],
            capture_output=True,
            check=True,
        ).stdout
        for record in filter(None, listing.split(b"\0")):
            meta, raw = record.split(b"\t", 1)
            mode, _, blob = meta.decode().split(" ")
            content = subprocess.run(
                ["git", "-C", str(repository), "cat-file", "blob", blob],
                capture_output=True,
                check=True,
            ).stdout
            if mode != "120000":
                content = content.replace(b"\n", b"\r\n")
            yield realworld.CheckoutFile(int(mode, 8), os.fsdecode(raw), blob, content)


def _export(tmp_path: Path, revision: str, deviations: Path) -> Path:
    realworld.export(
        tmp_path / "checkouts",
        _map(tmp_path, revision),
        deviations,
        tmp_path / "out",
        _GitCheckout(),
    )
    return tmp_path / "out/projects" / _PROJECT


_COLLIDING = [
    ("100644", b"upper\n", b"src/Data"),
    ("100644", b"lower\n", b"src/data"),
]


def test_export_writes_the_measured_checkout_with_modes_and_links(tmp_path: Path):
    repo, revision = _repository(tmp_path)
    # Pipeline output in the checkout stays out of the export.
    (repo / "pom.teralizer.xml").write_text("residue")

    project = _export(tmp_path, revision, _deviations(tmp_path))

    assert sorted(p.name for p in project.iterdir()) == ["alias.xml", "mvnw", "pom.xml"]
    assert os.readlink(project / "alias.xml") == "pom.xml"
    assert os.access(project / "mvnw", os.X_OK)
    assert not os.access(project / "pom.xml", os.X_OK)
    # The evaluation host checked text files out with CRLF line endings.
    assert (project / "pom.xml").read_bytes() == b"<project/>\r\n"


def test_case_collisions_export_the_declared_measured_file(tmp_path: Path):
    repo, revision = _repository(tmp_path, _COLLIDING)
    upper = _blob(repo, b"upper\n")

    project = _export(
        tmp_path, revision, _deviations(tmp_path, ("collision", "src/data", upper))
    )

    assert os.listdir(project / "src") == ["data"]
    assert (project / "src/data").read_bytes() == b"upper\r\n"


def test_undeclared_case_collisions_fail(tmp_path: Path):
    _, revision = _repository(tmp_path, _COLLIDING)

    with pytest.raises(realworld.ExportError, match="differ only in case"):
        _export(tmp_path, revision, _deviations(tmp_path))
    assert not (tmp_path / "out").exists()


def test_declared_collision_must_name_one_of_the_colliding_blobs(tmp_path: Path):
    _, revision = _repository(tmp_path, _COLLIDING)
    declared = _deviations(tmp_path, ("collision", "src/data", "0" * 40))

    with pytest.raises(realworld.ExportError, match="is not one of"):
        _export(tmp_path, revision, declared)


def test_directories_differing_in_case_merge_into_the_declared_spelling(
    tmp_path: Path,
):
    _, revision = _repository(
        tmp_path,
        [("100644", b"a\n", b"src/Pkg/A.java"), ("100644", b"b\n", b"src/pkg/B.java")],
    )

    project = _export(
        tmp_path, revision, _deviations(tmp_path, ("directory", "src/pkg", "-"))
    )

    assert os.listdir(project / "src") == ["pkg"]
    assert sorted(os.listdir(project / "src/pkg")) == ["A.java", "B.java"]
    assert (project / "src/pkg/A.java").read_bytes() == b"a\r\n"


def test_a_name_that_is_not_utf8_is_written_under_its_declared_reading(
    tmp_path: Path,
):
    repo, revision = _repository(tmp_path, [("100644", b"text\n", _GBK_NAME)])
    blob = _blob(repo, b"text\n")

    project = _export(
        tmp_path, revision, _deviations(tmp_path, ("renamed", _MEASURED_GBK_NAME, blob))
    )

    assert (project / _MEASURED_GBK_NAME).read_bytes() == b"text\r\n"


def test_a_name_that_is_not_utf8_needs_a_declaration(tmp_path: Path):
    _, revision = _repository(tmp_path, [("100644", b"text\n", _GBK_NAME)])

    with pytest.raises(realworld.ExportError, match="not valid UTF-8"):
        _export(tmp_path, revision, _deviations(tmp_path))


def test_declared_absent_paths_stay_out_of_the_export(tmp_path: Path):
    repo, revision = _repository(tmp_path)
    blob = _blob(repo, b"#!/bin/sh\n")

    project = _export(
        tmp_path, revision, _deviations(tmp_path, ("absent", "mvnw", blob))
    )

    assert sorted(p.name for p in project.iterdir()) == ["alias.xml", "pom.xml"]


def test_stale_declarations_fail(tmp_path: Path):
    _, revision = _repository(tmp_path)
    declared = _deviations(tmp_path, ("collision", "src/data", "0" * 40))

    with pytest.raises(realworld.ExportError, match="no revision has"):
        _export(tmp_path, revision, declared)


def test_checkout_of_another_origin_fails(tmp_path: Path):
    _, revision = _repository(tmp_path)

    with pytest.raises(realworld.ExportError, match="checkout origin"):
        realworld.export(
            tmp_path / "checkouts",
            _map(tmp_path, revision, "https://github.com/other/project.git"),
            _deviations(tmp_path),
            tmp_path / "out",
            _GitCheckout(),
        )


_REPOSITORY = Path(__file__).resolve().parents[3]


def _measured_checkout_available() -> bool:
    """Whether the JDK and the Gradle build of nix develop are present.

    macOS ships a javac stub that only asks to install Java, and the Gradle build
    needs the nested JPF sources, which the analysis CI job does not check out.
    """
    javac = shutil.which("javac")
    if javac is None or subprocess.run([javac, "-version"]).returncode != 0:
        return False
    resolved = subprocess.run(
        ["./gradlew", "-q", "measuredCheckoutClasspath"],
        cwd=_REPOSITORY,
        capture_output=True,
    )
    return resolved.returncode == 0


@pytest.mark.skipif(
    not _measured_checkout_available(),
    reason="needs the JDK and the Gradle build of nix develop",
)
def test_measured_checkout_converts_like_the_pipeline_clone(tmp_path: Path):
    # JGit converts every lone LF, also in files that already contain CRLF, where
    # Git keeps the bytes. The measured checkouts show the JGit behavior.
    mixed = b"a\r\nb\nc\n"
    repo, revision = _repository(tmp_path, [("100644", mixed, b"Mixed.java")])
    runner = realworld.MeasuredCheckoutRunner(_REPOSITORY)
    try:
        files = {f.path: f for f in runner.files(repo, revision)}
    finally:
        runner.close()

    assert files["Mixed.java"].content == b"a\r\nb\r\nc\r\n"
    assert files["pom.xml"].content == b"<project/>\r\n"
    assert files["alias.xml"].content == b"pom.xml"
    assert files["mvnw"].mode == 0o100755
