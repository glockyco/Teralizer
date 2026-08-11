"""Machine-readable provenance sidecar: every metric/table/figure -> its source."""

from __future__ import annotations

import json
from importlib.metadata import version
from pathlib import Path

from teralizer.eval.model import RQReport

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


def build_manifest(report: RQReport, *, repo_url: str) -> dict:
    manifest = {
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
    if report.rq == "rq0":
        metrics = report.metric_map()

        def metric_value(key: str):
            return metrics[key].value

        manifest["report_basis"] = {
            "databases": {
                "scoreboard": report.db,
                "census": metric_value("rq0.census.database"),
            },
            "variants": {
                "table2": metric_value("rq0.table2.variant"),
                "census": metric_value("rq0.census.variant"),
            },
            "table_keys": [table.key for table in report.tables()],
            "census_status": metric_value("rq0.census.status"),
            "census_scope": "partial applicability snapshot",
            "census_pvc_basis": metric_value("rq0.census.pvc_basis"),
            "census_pit_reduction_required_for_pvc": False,
            "census_is_mutation_result": False,
            "census_diagnostics": {
                "intended_projects": metric_value("rq0.census.intended_projects"),
                "populated_projects": metric_value("rq0.census.populated_projects"),
                "completed_projects": metric_value("rq0.census.completed_projects"),
                "failed_projects": metric_value("rq0.census.failed_projects"),
                "failed_task_count": metric_value("rq0.census.failed_task_count"),
                "completion_marker": metric_value("rq0.census.completion_marker"),
            },
        }
    return manifest


def write_manifest(report: RQReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / "provenance.json"
    existing = json.loads(path.read_text()) if path.exists() else {}
    existing[report.rq] = build_manifest(report, repo_url=repo_url)
    path.write_text(
        json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return path
