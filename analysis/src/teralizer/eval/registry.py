"""rq id -> how to build and where to read it. Reports self-register on import."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from teralizer.eval.inputs import ReportContext, ReportInputSpec, validate_declarations
from teralizer.eval.model import RQReport


@dataclass(frozen=True)
class ReportSpec:
    build: Callable[[ReportContext], RQReport]
    inputs: tuple[ReportInputSpec, ...]

    def __post_init__(self) -> None:
        validate_declarations(self.inputs)


REPORTS: dict[str, ReportSpec] = {}


def register(rq: str, spec: ReportSpec) -> None:
    REPORTS[rq] = spec


def get(rq: str) -> ReportSpec:
    if rq not in REPORTS:
        raise KeyError(f"unknown report: {rq} (known: {sorted(REPORTS)})")
    return REPORTS[rq]
