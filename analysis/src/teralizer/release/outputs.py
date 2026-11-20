"""Manifest-driven comparison of a report run with the reference outputs.

A registered report run lists every artifact it emitted in
``analysis/reports/provenance.json``. The reference manifest records, for each of
those artifacts, the comparison rule and the digest of its canonical form.
Verification compares a new run artifact by artifact and reports missing, extra
and changed outputs per output kind.

Rules:

- ``bytes``: the file must be byte-identical. Tables, CSV files, macros, reports
  and figure sources are deterministic.
- ``image``: the file must exist and be a PNG or PDF. Its pixels depend on the
  installed fonts, so the figure's canonical source carries the comparison.
- ``run-manifest``: the run manifest without the checkout position, which names
  the commit the run started from rather than anything a report shows.
"""

from __future__ import annotations

import hashlib
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

REFERENCE_FORMAT = ("teralizer-reference-outputs", 1)
RUN_MANIFEST = "reports/provenance.json"
_SIGNATURES = {".png": b"\x89PNG\r\n\x1a\n", ".pdf": b"%PDF-"}


@dataclass(frozen=True)
class Artifact:
    report: str
    target: str
    key: str
    path: str

    @property
    def identity(self) -> str:
        return f"{self.report}/{self.target}/{self.key}"


def rule(artifact: Artifact) -> str:
    if artifact.path == RUN_MANIFEST:
        return "run-manifest"
    if Path(artifact.path).suffix in _SIGNATURES:
        return "image"
    return "bytes"


def run_artifacts(analysis: Path) -> list[Artifact]:
    """Every artifact of the run in ``analysis``, plus the run manifest itself."""
    manifest = json.loads((analysis / RUN_MANIFEST).read_text())
    artifacts = [
        Artifact(report, str(item["target"]), str(item["key"]), str(item["path"]))
        for report, record in sorted(manifest.items())
        for item in record["artifacts"]
    ]
    artifacts.append(Artifact("run", "manifest", "provenance", RUN_MANIFEST))
    return artifacts


def canonical_digest(path: Path, kind: str) -> str:
    content = path.read_bytes()
    if kind == "image":
        signature = _SIGNATURES[path.suffix]
        if not content.startswith(signature):
            raise ValueError(f"{path} is not a {path.suffix[1:].upper()} file")
        return "image"
    if kind == "run-manifest":
        document = json.loads(content)
        for record in document.values():
            record["run"].pop("source_commit", None)
        content = json.dumps(document, indent=2, sort_keys=True).encode()
    return hashlib.sha256(content).hexdigest()


def reference(analysis: Path) -> dict[str, Any]:
    """The reference manifest of the run in ``analysis``."""
    entries = []
    for artifact in run_artifacts(analysis):
        kind = rule(artifact)
        entries.append(
            {
                "report": artifact.report,
                "target": artifact.target,
                "key": artifact.key,
                "path": artifact.path,
                "rule": kind,
                "digest": canonical_digest(analysis / artifact.path, kind),
            }
        )
    return {
        "format": REFERENCE_FORMAT[0],
        "format_version": REFERENCE_FORMAT[1],
        "artifacts": entries,
    }


@dataclass(frozen=True)
class Comparison:
    identity: str
    target: str
    status: str  # passed, missing, extra, changed

    @property
    def passed(self) -> bool:
        return self.status == "passed"


def compare(expected: dict[str, Any], analysis: Path) -> list[Comparison]:
    if (expected.get("format"), expected.get("format_version")) != REFERENCE_FORMAT:
        raise ValueError("unsupported reference manifest format")
    references = {
        f"{item['report']}/{item['target']}/{item['key']}": item
        for item in expected["artifacts"]  # type: ignore[union-attr]
    }
    observed = {artifact.identity: artifact for artifact in run_artifacts(analysis)}
    results: list[Comparison] = []
    for identity, item in sorted(references.items()):
        artifact = observed.get(identity)
        target = str(item["target"])
        if artifact is None or not (analysis / artifact.path).is_file():
            results.append(Comparison(identity, target, "missing"))
            continue
        try:
            digest = canonical_digest(analysis / artifact.path, str(item["rule"]))
        except ValueError:
            digest = "invalid"
        status = "passed" if digest == item["digest"] else "changed"
        results.append(Comparison(identity, target, status))
    for identity in sorted(set(observed) - set(references)):
        results.append(Comparison(identity, observed[identity].target, "extra"))
    return results


def summary(results: list[Comparison]) -> dict[str, Any]:
    by_target: dict[str, Counter[str]] = {}
    for result in results:
        by_target.setdefault(result.target, Counter())[result.status] += 1
    return {
        "passed": all(result.passed for result in results),
        "targets": {
            target: dict(counts) for target, counts in sorted(by_target.items())
        },
        "failures": [
            {"artifact": result.identity, "status": result.status}
            for result in results
            if not result.passed
        ],
    }
