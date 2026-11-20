"""Materialize the committed source tree with every nested source and LFS object.

`git archive` writes a gitlink as an empty directory and an LFS file as its pointer.
The release needs the bytes behind both, so materialization exports each gitlink at
its recorded commit, recursively, and replaces each LFS pointer with its object.
The result needs no Git metadata to build.
"""

from __future__ import annotations

import hashlib
import io
import subprocess
import tarfile
from dataclasses import dataclass
from pathlib import Path

_LFS_POINTER = b"version https://git-lfs.github.com/spec/v1\n"


class SourceError(RuntimeError):
    """The committed source cannot be materialized completely."""


@dataclass(frozen=True)
class NestedSource:
    path: str
    commit: str


def _git(repo: Path, *args: str) -> bytes:
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True)
    if result.returncode != 0:
        raise SourceError(
            f"git {' '.join(args)} in {repo} failed: {result.stderr.decode().strip()}"
        )
    return result.stdout


def _gitlinks(repo: Path, revision: str) -> list[NestedSource]:
    links: list[NestedSource] = []
    listing = _git(repo, "ls-tree", "-r", "-z", revision)
    for record in listing.decode().split("\0"):
        if not record:
            continue
        metadata, path = record.split("\t", 1)
        mode, kind, commit = metadata.split(" ")
        if mode == "160000" and kind == "commit":
            links.append(NestedSource(path, commit))
    return links


def _extract(repo: Path, revision: str, destination: Path) -> None:
    archive = _git(repo, "archive", "--format=tar", revision)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        tar.extractall(destination, filter="tar")


def _lfs_object(repo: Path, pointer: bytes) -> bytes:
    fields = dict(
        line.split(" ", 1) for line in pointer.decode().splitlines() if " " in line
    )
    oid = fields["oid"].removeprefix("sha256:")
    git_dir = Path(_git(repo, "rev-parse", "--git-common-dir").decode().strip())
    if not git_dir.is_absolute():
        git_dir = repo / git_dir
    candidate = git_dir / "lfs" / "objects" / oid[:2] / oid[2:4] / oid
    if not candidate.is_file():
        raise SourceError(f"LFS object {oid} is not present; run `git lfs fetch`")
    content = candidate.read_bytes()
    if hashlib.sha256(content).hexdigest() != oid:
        raise SourceError(f"LFS object {oid} does not match its id")
    return content


def _resolve_lfs(repo: Path, root: Path) -> list[str]:
    resolved: list[str] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        with path.open("rb") as stream:
            head = stream.read(len(_LFS_POINTER))
        if head != _LFS_POINTER:
            continue
        path.write_bytes(_lfs_object(repo, path.read_bytes()))
        resolved.append(path.relative_to(root).as_posix())
    return resolved


def materialize(
    repo: Path, revision: str, destination: Path, prefix: str = ""
) -> tuple[list[NestedSource], list[str]]:
    """Export ``revision`` of ``repo`` and every nested gitlink into ``destination``.

    Returns the nested sources at their recorded commits and the LFS paths that were
    replaced by their objects. A gitlink whose commit is not available in its
    checked-out repository fails instead of leaving an empty directory.
    """
    if destination.exists() and any(destination.iterdir()):
        raise SourceError(f"destination is not empty: {destination}")
    destination.mkdir(parents=True, exist_ok=True)
    _extract(repo, revision, destination)
    # Nested gitlink directories are still empty here, so this resolves only the
    # LFS files of this repository, from this repository's object store.
    lfs = [prefix + path for path in _resolve_lfs(repo, destination)]
    nested: list[NestedSource] = []
    for link in _gitlinks(repo, revision):
        checkout = repo / link.path
        if not (checkout / ".git").exists():
            raise SourceError(f"submodule {prefix}{link.path} is not checked out")
        target = destination / link.path
        target.rmdir()
        nested.append(NestedSource(prefix + link.path, link.commit))
        inner_nested, inner_lfs = materialize(
            checkout, link.commit, target, prefix + link.path + "/"
        )
        nested.extend(inner_nested)
        lfs.extend(inner_lfs)
    return nested, lfs
