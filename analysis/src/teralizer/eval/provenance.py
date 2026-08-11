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

# analysis/src/teralizer/eval/provenance.py -> repo root is parents[4]
_REPO_ROOT = Path(__file__).resolve().parents[4]


@dataclass(frozen=True)
class Provenance:
    module: str
    qualname: str
    lineno: int
    query: str | None
    commit: str
    dirty: bool = False

    def rel_path(self) -> str:
        parts = self.module.split(".")
        return "analysis/src/" + "/".join(parts) + ".py"

    def source_url(self, repo_url: str) -> str:
        return f"{repo_url}/blob/{self.commit}/{self.rel_path()}#L{self.lineno}"


DIRTY_PROVENANCE_ENV = "TERALIZER_ALLOW_DIRTY_PROVENANCE"


@lru_cache(maxsize=1)
def _git_snapshot() -> tuple[str, bool]:
    head = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    dirty = bool(
        subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=_REPO_ROOT,
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    )
    return head, dirty


def require_publishable_tree() -> None:
    """Reject publishing from a dirty tree unless local iteration is enabled."""
    _, dirty = _git_snapshot()
    if dirty and os.environ.get(DIRTY_PROVENANCE_ENV) != "1":
        raise RuntimeError(
            "cannot publish provenance from a dirty tree; "
            f"set {DIRTY_PROVENANCE_ENV}=1 for local iteration"
        )


def git_commit() -> str:
    return _git_snapshot()[0]


def capture(fn: Callable[..., object], *, query: str | None = None) -> Provenance:
    module = getattr(fn, "__module__", "?")
    qualname = getattr(fn, "__qualname__", getattr(fn, "__name__", "?"))
    try:
        lineno = inspect.getsourcelines(fn)[1]
    except (OSError, TypeError):
        lineno = 0
    commit, dirty = _git_snapshot()
    return Provenance(
        module=module,
        qualname=qualname,
        lineno=lineno,
        query=query,
        commit=commit,
        dirty=dirty,
    )
