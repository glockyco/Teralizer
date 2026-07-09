import json
from pathlib import Path
from teralizer.eval.model import Metric, RQReport, Section
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.manifest import write_manifest


def test_manifest_maps_metric_to_source(tmp_path: Path):
    prov = Provenance(
        "teralizer.eval.reports.rq6_causes_realworld",
        "project_funnel",
        30,
        "SELECT 1",
        "abc1234",
    )
    report = RQReport(
        "rq6",
        "T",
        "postgres_reporeapers",
        [Section("S", [])],
        metrics=[Metric("realworld.eligible_projects", 632, "int", provenance=prov)],
    )
    path = write_manifest(
        report, tmp_path, repo_url="https://github.com/glockyco/Teralizer"
    )
    data = json.loads(Path(path).read_text())
    entry = data["rq6"]["metrics"]["realworld.eligible_projects"]
    assert entry["value"] == 632
    assert entry["commit"] == "abc1234"
    assert entry["qualname"] == "project_funnel"
    assert entry["source_url"].endswith("rq6_causes_realworld.py#L30")
