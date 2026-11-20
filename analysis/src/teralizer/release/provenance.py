"""Per-file source provenance embedded in a release.

An extracted release has no Git metadata, yet every regenerated report must name
the commit that last changed the code behind each value. The release build
therefore records, from a clean checkout, the last-changing commit and the
repository of every file it ships from the source tree.
"""

from __future__ import annotations

import subprocess
from collections.abc import Iterable
from pathlib import Path

from teralizer.release.source import NestedSource


class ProvenanceError(RuntimeError):
    """The checkout cannot attribute every shipped file to a commit."""


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args], capture_output=True, text=True, check=True
    ).stdout


def require_clean(repo: Path) -> None:
    status = _git(repo, "status", "--porcelain", "--ignore-submodules=none").strip()
    if status:
        raise ProvenanceError(
            "release provenance needs a clean checkout; changed paths:\n" + status
        )


def last_changing_commits(repo: Path, revision: str = "HEAD") -> dict[str, str]:
    """The last commit that changed each file tracked at ``revision``, in one pass."""
    tracked = set(
        _git(repo, "ls-tree", "-r", "--name-only", "-z", revision).split("\0")
    )
    tracked.discard("")
    commits: dict[str, str] = {}
    current = ""
    log = _git(
        repo, "log", "--format=%x00%H", "--name-only", "--no-renames", "-z", revision
    )
    for field in log.split("\0"):
        field = field.strip("\n")
        if not field:
            continue
        if len(field) == 40 and all(
            character in "0123456789abcdef" for character in field
        ):
            current = field
            continue
        if field in tracked and field not in commits:
            commits[field] = current
    return commits


def remote_url(repo: Path) -> str:
    url = _git(repo, "remote", "get-url", "origin").strip()
    if url.startswith("git@github.com:"):
        url = "https://github.com/" + url.removeprefix("git@github.com:")
    return url.removesuffix(".git")


def file_revisions(
    repo: Path, nested: Iterable[NestedSource], paths: Iterable[str]
) -> dict[str, dict[str, str]]:
    """Commit and repository of every shipped repository-relative path.

    A path inside a nested source is attributed to the nested repository at its
    recorded commit, with the path relative to that repository.
    """
    require_clean(repo)
    nested = sorted(nested, key=lambda item: len(item.path), reverse=True)
    histories: dict[str, dict[str, str]] = {"": last_changing_commits(repo)}
    remotes: dict[str, str] = {"": remote_url(repo)}
    for item in nested:
        histories[item.path] = last_changing_commits(repo / item.path, item.commit)
        remotes[item.path] = remote_url(repo / item.path)

    revisions: dict[str, dict[str, str]] = {}
    unattributed: list[str] = []
    for path in paths:
        owner = next(
            (item.path for item in nested if path.startswith(item.path + "/")), ""
        )
        inner = path.removeprefix(owner + "/") if owner else path
        commit = histories[owner].get(inner)
        if commit is None:
            unattributed.append(path)
            continue
        revisions[path] = {
            "commit": commit,
            "repository": remotes[owner],
            "path": inner,
        }
    if unattributed:
        raise ProvenanceError(
            f"{len(unattributed)} shipped files have no producing commit, e.g. {unattributed[:5]}"
        )
    return revisions
