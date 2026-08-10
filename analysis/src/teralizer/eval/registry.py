"""rq id -> how to build and where to read it. Reports self-register on import."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy.engine import Connection

from teralizer.eval.data import Required
from teralizer.eval.model import RQReport


@dataclass(frozen=True)
class Corpus:
    """Run outputs and project configs a report's database must agree with."""

    data_dir: str
    config_dir: str


@dataclass(frozen=True)
class ReportSpec:
    build: Callable[[Connection], RQReport]
    default_db: str
    schema: str  # "new" | "old"
    requires: tuple[Required, ...] = ()
    # Reports read different databases, so a corpus is a property of the report
    # rather than of the invocation. Without one, `all` would check every report
    # against whichever corpus the caller named.
    corpus: Corpus | None = None


REPORTS: dict[str, ReportSpec] = {}


def register(rq: str, spec: ReportSpec) -> None:
    REPORTS[rq] = spec


def get(rq: str) -> ReportSpec:
    if rq not in REPORTS:
        raise KeyError(f"unknown report: {rq} (known: {sorted(REPORTS)})")
    return REPORTS[rq]
