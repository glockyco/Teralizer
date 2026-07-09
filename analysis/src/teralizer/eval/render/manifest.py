"""Machine-readable provenance sidecar: every metric/table/figure -> its source."""

from __future__ import annotations

import json
from pathlib import Path

from teralizer.eval.model import RQReport


def _entry(value, prov, repo_url: str) -> dict:
    return {
        "value": value,
        "module": prov.module,
        "qualname": prov.qualname,
        "query": prov.query,
        "commit": prov.commit,
        "source_url": prov.source_url(repo_url),
    }


def build_manifest(report: RQReport, *, repo_url: str) -> dict:
    return {
        "db": report.db,
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


def write_manifest(report: RQReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / "provenance.json"
    existing = json.loads(path.read_text()) if path.exists() else {}
    existing[report.rq] = build_manifest(report, repo_url=repo_url)
    path.write_text(
        json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return path
