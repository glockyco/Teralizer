"""Closure of registered report inputs over the release inventory.

Every corpus a registered report reads must be a payload of a corpus component.
Every file it reads must be committed, so that the source-tree input ships it, and
must carry one report-input declaration saying whether the release can regenerate
it or ships it for inspection only.
"""

from __future__ import annotations

import hashlib
import subprocess
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec
from teralizer.eval.registry import ReportSpec
from teralizer.release.inventory import Inventory


@dataclass(frozen=True)
class ResolvedInput:
    report: str
    role: str
    kind: str
    source: str
    owner: str
    sha256: str | None
    status: str


def _tracked(repo: Path, path: str) -> bool:
    result = subprocess.run(
        ["git", "-C", str(repo), "ls-files", "--error-unmatch", "--", path],
        capture_output=True,
    )
    return result.returncode == 0


def resolve(
    inventory: Inventory, reports: Mapping[str, ReportSpec], repo: Path
) -> tuple[list[ResolvedInput], list[str]]:
    """Resolve every declared report input and return the disagreements."""
    corpus_owners = {
        item.path: item.component
        for item in inventory.inputs
        if item.location == "corpus-package" and item.disposition == "payload"
    }
    declarations = {item.path: item for item in inventory.report_inputs}
    used_paths: set[str] = set()
    resolved: list[ResolvedInput] = []
    problems: list[str] = []

    for report, spec in sorted(reports.items()):
        for declared in spec.inputs:
            if isinstance(declared, CorpusInputSpec):
                owner = corpus_owners.get(declared.corpus_id)
                if owner is None:
                    problems.append(
                        f"{report} {declared.role}: corpus {declared.corpus_id} has no corpus component"
                    )
                    continue
                resolved.append(
                    ResolvedInput(
                        report=report,
                        role=declared.role,
                        kind="corpus",
                        source=declared.corpus_id,
                        owner=owner,
                        sha256=None,
                        status="restored from the corpus dump",
                    )
                )
                continue
            assert isinstance(declared, FileInputSpec)
            used_paths.add(declared.path)
            declaration = declarations.get(declared.path)
            if declaration is None:
                problems.append(
                    f"{report} {declared.role}: {declared.path} has no report-input declaration"
                )
                continue
            if not _tracked(repo, declared.path):
                problems.append(
                    f"{report} {declared.role}: {declared.path} is not committed, so no component owns it"
                )
                continue
            content = (repo / declared.path).read_bytes()
            resolved.append(
                ResolvedInput(
                    report=report,
                    role=declared.role,
                    kind="file",
                    source=declared.path,
                    owner="core",
                    sha256=hashlib.sha256(content).hexdigest(),
                    status=declaration.status,
                )
            )

    for path in sorted(set(declarations) - used_paths):
        problems.append(f"report-input declaration {path} is not read by any report")
    return resolved, problems
