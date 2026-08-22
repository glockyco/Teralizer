"""Machine-readable provenance sidecar: every metric/table/figure -> its source."""

from __future__ import annotations

import json
from decimal import Decimal
from importlib.metadata import version
from pathlib import Path

from teralizer import corpora
from teralizer.eval import provenance
from teralizer.eval.artifacts import (
    ArtifactSet,
    RenderedArtifact,
    ArtifactId,
    RenderTarget,
    RunAggregate,
)
from teralizer.eval.inputs import CorpusInputSnapshot, FileInputSnapshot, InputSnapshot
from teralizer.eval.model import BuiltReport

ANALYSIS_VERSION = version("teralizer-analysis")


def _json_value(value: object) -> object:
    if isinstance(value, Decimal):
        return int(value) if value == value.to_integral_value() else float(value)
    return value


def _entry(value, prov, repo_url: str) -> dict:
    return {
        "value": _json_value(value),
        "module": prov.module,
        "qualname": prov.qualname,
        "query": prov.query,
        "commit": prov.commit,
        "dirty": prov.dirty,
        "version": ANALYSIS_VERSION,
        "source_url": prov.source_url(repo_url),
    }


def _input_entry(snapshot: InputSnapshot) -> dict[str, object]:
    if isinstance(snapshot, CorpusInputSnapshot):
        return {
            "kind": "corpus",
            "corpus_id": snapshot.corpus_id,
            "database": snapshot.database,
            "expected_projects": snapshot.expected_projects,
            "observed_projects": snapshot.observed_projects,
            "data_dir": snapshot.data_dir,
            "config_dir": snapshot.config_dir,
        }
    if isinstance(snapshot, FileInputSnapshot):
        return {
            "kind": "file",
            "path": snapshot.path,
            "present": snapshot.present,
            "sha256": snapshot.sha256,
            "commit": snapshot.commit,
            "dirty": snapshot.dirty,
        }
    raise TypeError(f"unsupported report input snapshot: {type(snapshot)!r}")


def build_manifest(
    built: BuiltReport, artifacts: ArtifactSet, *, repo_url: str
) -> dict:
    report = built.report
    artifact_records = sorted(
        (
            {
                "target": artifact.id.target.value,
                "key": artifact.id.key,
                "path": str(artifact.path.relative_to(artifacts.root)),
            }
            for artifact in artifacts
            if artifact.owner == report.rq
        ),
        key=lambda record: (record["target"], record["key"]),
    )
    source_commit, source_dirty = provenance.checkout_snapshot()
    manifest = {
        "run": {
            "source_commit": source_commit,
            "dirty": source_dirty,
            "derived_view_revision": corpora.derived_view_revision(),
        },
        "inputs": {snapshot.role: _input_entry(snapshot) for snapshot in built.inputs},
        "artifacts": artifact_records,
        "metrics": {
            m.key: _entry(m.value, m.provenance, repo_url)
            for m in report.metrics
            if m.provenance
        },
        "tables": {
            t.key: _entry(t.caption, t.provenance, repo_url)
            for t in report.tables()
            if t.provenance
        },
        "figures": {
            f.key: _entry(f.caption, f.provenance, repo_url)
            for f in report.figures()
            if f.provenance
        },
    }
    return manifest


def render(
    built_reports: tuple[BuiltReport, ...],
    rendered: ArtifactSet,
    reports_dir: Path,
    *,
    staging_root: Path,
    repo_url: str,
    base_document: dict[str, object] | None = None,
) -> ArtifactSet:
    """Write one run-owned provenance manifest, replacing selected entries."""
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / "provenance.json"
    document = dict(base_document or {})
    for built in built_reports:
        entry = build_manifest(built, rendered, repo_url=repo_url)
        prior = document.get(built.report.rq)
        prior_artifacts = prior.get("artifacts", []) if isinstance(prior, dict) else []
        current_targets = {record["target"] for record in entry["artifacts"]}
        if isinstance(prior_artifacts, list):
            entry["artifacts"] = sorted(
                entry["artifacts"]
                + [
                    record
                    for record in prior_artifacts
                    if isinstance(record, dict)
                    and record.get("target") not in current_targets
                ],
                key=lambda record: (record["target"], record["key"]),
            )
        document[built.report.rq] = entry
    path.write_text(
        json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    artifacts = ArtifactSet(staging_root)
    artifacts.add(
        RenderedArtifact(
            ArtifactId(RenderTarget.MANIFEST, "provenance"),
            path,
            RunAggregate.RUN,
        )
    )
    return artifacts
