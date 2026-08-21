"""Provenance: link every generated artifact to the exact code + commit.

Captured at build time, never hand-maintained. Publishable artifacts require a
clean working tree. For local iteration only, set
``TERALIZER_ALLOW_DIRTY_PROVENANCE=1`` to opt out of that check. The resulting
provenance records ``dirty=True`` so it remains self-describing.
"""

from __future__ import annotations

import inspect
import os
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from teralizer.eval.inputs import FileInputSnapshot, InputSnapshot

# analysis/src/teralizer/eval/provenance.py -> repo root is parents[4]
_REPO_ROOT = Path(__file__).resolve().parents[4]


@dataclass(frozen=True)
class Provenance:
    module: str
    qualname: str
    lineno: int
    query: str | None
    commit: str
    path: str
    dirty: bool = False

    def source_url(self, repo_url: str) -> str:
        return f"{repo_url}/blob/{self.commit}/{self.path}#L{self.lineno}"


DIRTY_PROVENANCE_ENV = "TERALIZER_ALLOW_DIRTY_PROVENANCE"


@lru_cache(maxsize=1)
def _git_snapshot() -> tuple[str, bool]:
    """Where the checkout stands, and whether anything in it is uncommitted.

    Used for the publish guard, which is an act of attribution across a
    repository boundary, and as the fallback for a file with no commit. An
    individual artifact resolves through :func:`_file_snapshot` instead.
    """
    return _git(["rev-parse", "HEAD"]), bool(_git(["status", "--porcelain"]))


def _relative_to_repo(source: str | None) -> str | None:
    """The repository-relative path of a loaded source file, or None when it lives
    outside this repository."""
    if source is None:
        return None
    try:
        return str(Path(source).resolve().relative_to(_REPO_ROOT))
    except ValueError:
        return None


@lru_cache(maxsize=None)
def _file_snapshot(path: str) -> tuple[str, bool]:
    """The last commit that changed ``path``, and whether it has uncommitted
    changes.

    This is the identity of the code that produced an artifact: whatever the file
    holds now, it has held since that commit. ``HEAD`` answers a different
    question -- where the checkout stands -- and coincides only just after this
    file was committed. Using it made every artifact record an unrelated commit,
    and made every regeneration rewrite every artifact.
    """
    commit = _git(["log", "-1", "--format=%H", "--", path])
    if not commit:
        # Never committed. A blank commit would render a broken permalink, so
        # record the checkout position and say the value is uncertain.
        return _git_snapshot()[0], True
    return commit, bool(_git(["status", "--porcelain", "--", path]))


def _git(args: list[str]) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()


def require_publishable_tree() -> None:
    """Reject publishing from a dirty tree unless local iteration is enabled."""
    _, dirty = _git_snapshot()
    if dirty and os.environ.get(DIRTY_PROVENANCE_ENV) != "1":
        raise RuntimeError(
            "cannot publish provenance from a dirty tree. "
            f"Set {DIRTY_PROVENANCE_ENV}=1 for local iteration"
        )


def require_publishable_inputs(snapshots: tuple[InputSnapshot, ...]) -> None:
    """Reject dirty declared files unless local iteration is enabled."""
    dirty_roles = sorted(
        snapshot.role
        for snapshot in snapshots
        if isinstance(snapshot, FileInputSnapshot) and snapshot.dirty
    )
    if dirty_roles and os.environ.get(DIRTY_PROVENANCE_ENV) != "1":
        raise RuntimeError(
            "cannot publish with dirty declared inputs: " + ", ".join(dirty_roles)
        )


def checkout_snapshot() -> tuple[str, bool]:
    """Return the source checkout commit and dirty state for run provenance."""
    return _git_snapshot()


def git_commit() -> str:
    return checkout_snapshot()[0]


def capture(fn: Callable[..., object], *, query: str | None = None) -> Provenance:
    module = getattr(fn, "__module__", "?")
    qualname = getattr(fn, "__qualname__", getattr(fn, "__name__", "?"))
    try:
        lineno = inspect.getsourcelines(fn)[1]
    except (OSError, TypeError):
        lineno = 0
    path = _relative_to_repo(inspect.getsourcefile(fn))
    if path is None:
        # Defined outside this repository, so no file here explains the value.
        commit, dirty, path = _git_snapshot()[0], True, ""
    else:
        commit, dirty = _file_snapshot(path)
    return Provenance(
        module=module,
        qualname=qualname,
        lineno=lineno,
        query=query,
        commit=commit,
        path=path,
        dirty=dirty,
    )
