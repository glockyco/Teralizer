import json
from pathlib import Path
from teralizer.eval.inputs import CorpusInputSnapshot, FileInputSnapshot
from teralizer.eval.model import BuiltReport, Metric, RQReport, Section
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.manifest import (
    ANALYSIS_VERSION,
    build_manifest,
    write_manifest,
)


def test_manifest_maps_metric_to_source(tmp_path: Path):
    prov = Provenance(
        "teralizer.eval.reports.rq6_causes_realworld",
        "project_funnel",
        30,
        "SELECT 1",
        "a" * 40,
        "analysis/src/teralizer/eval/reports/rq6_causes_realworld.py",
    )
    report = RQReport(
        "rq6",
        "T",
        [Section("S", [])],
        metrics=[Metric("realworld.eligible_projects", 632, "int", provenance=prov)],
    )
    built = BuiltReport(
        report,
        (
            CorpusInputSnapshot(
                "real-world",
                "real-world",
                "postgres_reporeapers_rq6",
                1161,
                1161,
                "data/run",
                "project-configs/run",
            ),
        ),
    )
    path = write_manifest(
        built, tmp_path, repo_url="https://github.com/glockyco/Teralizer"
    )
    data = json.loads(Path(path).read_text())
    entry = data["rq6"]["metrics"]["realworld.eligible_projects"]
    assert entry["value"] == 632
    assert entry["commit"] == "a" * 40
    assert len(entry["commit"]) == 40
    assert entry["version"] == ANALYSIS_VERSION
    assert entry["dirty"] is False
    assert entry["qualname"] == "project_funnel"
    assert entry["source_url"].endswith("rq6_causes_realworld.py#L30")


def test_manifest_records_all_corpus_file_and_absent_roles():
    report = RQReport("rq0", "RQ0", [Section("S", [])])
    built = BuiltReport(
        report,
        (
            CorpusInputSnapshot(
                "scenarios", "jarvis-scenarios", "scoreboard", 2, 2, None, None
            ),
            CorpusInputSnapshot(
                "benchmark", "jarvis-benchmark", "census", 12, 12, None, None
            ),
            FileInputSnapshot(
                "jarvis-pvc-facts",
                "analysis/data/report-inputs/jarvis-value-facts.json",
                True,
                "a" * 64,
                "b" * 40,
                True,
            ),
            FileInputSnapshot(
                "completion-marker",
                "data/detached/census-gen.complete",
                False,
                None,
                None,
                False,
            ),
        ),
    )

    manifest = build_manifest(built, repo_url="https://github.com/glockyco/Teralizer")

    assert manifest["inputs"]["scenarios"]["corpus_id"] == "jarvis-scenarios"
    assert manifest["inputs"]["benchmark"]["database"] == "census"
    assert manifest["inputs"]["jarvis-pvc-facts"] == {
        "kind": "file",
        "path": "analysis/data/report-inputs/jarvis-value-facts.json",
        "present": True,
        "sha256": "a" * 64,
        "commit": "b" * 40,
        "dirty": True,
    }
    assert manifest["inputs"]["completion-marker"]["present"] is False
    assert "report_basis" not in manifest
