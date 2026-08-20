"""Machine-readable provenance sidecar: every metric/table/figure -> its source."""

from __future__ import annotations

import json
from importlib.metadata import version
from pathlib import Path

from teralizer.eval.inputs import CorpusInputSnapshot, FileInputSnapshot, InputSnapshot
from teralizer.eval.model import BuiltReport

ANALYSIS_VERSION = version("teralizer-analysis")


def _entry(value, prov, repo_url: str) -> dict:
    return {
        "value": value,
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


def build_manifest(built: BuiltReport, *, repo_url: str) -> dict:
    report = built.report
    manifest = {
        "inputs": {snapshot.role: _input_entry(snapshot) for snapshot in built.inputs},
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


def write_manifest(built: BuiltReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / "provenance.json"
    existing = json.loads(path.read_text()) if path.exists() else {}
    existing[built.report.rq] = build_manifest(built, repo_url=repo_url)
    path.write_text(
        json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return path
