"""Provenance: link every generated artifact to the exact code + commit.

Captured at build time, never hand-maintained. The commit carries a `-dirty`
suffix when the working tree has uncommitted changes, so a number is never
falsely pinned to a clean commit.
"""

from __future__ import annotations

import inspect
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

    def rel_path(self) -> str:
        parts = self.module.split(".")
        return "analysis/src/" + "/".join(parts) + ".py"

    def source_url(self, repo_url: str) -> str:
        return f"{repo_url}/blob/{self.commit}/{self.rel_path()}#L{self.lineno}"


@lru_cache(maxsize=1)
def git_commit() -> str:
    head = subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    dirty = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    return f"{head}-dirty" if dirty else head


def capture(fn: Callable[..., object], *, query: str | None = None) -> Provenance:
    module = getattr(fn, "__module__", "?")
    qualname = getattr(fn, "__qualname__", getattr(fn, "__name__", "?"))
    try:
        lineno = inspect.getsourcelines(fn)[1]
    except (OSError, TypeError):
        lineno = 0
    return Provenance(
        module=module,
        qualname=qualname,
        lineno=lineno,
        query=query,
        commit=git_commit(),
    )
