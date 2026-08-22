import json
from pathlib import Path
from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.inputs import CorpusInputSnapshot, FileInputSnapshot
from teralizer.eval.model import (
    BuiltReport,
    Metric,
    MetricPopulation,
    RQReport,
    Section,
    ValueKind,
)
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.manifest import ANALYSIS_VERSION, build_manifest, render


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
        metrics=[
            Metric(
                "realworld.eligible_projects",
                632,
                "int",
                provenance=prov,
                kind=ValueKind.COUNT,
                population=MetricPopulation(
                    "realworld.eligible_projects", "Project", "real-world"
                ),
            )
        ],
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
    rendered = ArtifactSet(tmp_path)
    (tmp_path / "rq6.md").write_text("report")
    rendered.add(
        RenderedArtifact(
            ArtifactId(RenderTarget.MARKDOWN, "rq6"), tmp_path / "rq6.md", "rq6"
        )
    )
    written = render(
        (built,),
        rendered,
        tmp_path,
        staging_root=tmp_path,
        repo_url="https://github.com/glockyco/Teralizer",
    )
    path = written.get(ArtifactId(RenderTarget.MANIFEST, "provenance")).path
    data = json.loads(path.read_text())
    assert data["rq6"]["artifacts"] == [
        {"target": "md", "key": "rq6", "path": "rq6.md"}
    ]
    entry = data["rq6"]["metrics"]["realworld.eligible_projects"]
    assert entry["value"] == 632
    assert entry["commit"] == "a" * 40
    assert len(entry["commit"]) == 40
    assert entry["version"] == ANALYSIS_VERSION
    assert entry["dirty"] is False
    assert entry["qualname"] == "project_funnel"
    assert entry["source_url"].endswith("rq6_causes_realworld.py#L30")
    assert entry["value_kind"] == "count"
    assert entry["population"] == {
        "key": "realworld.eligible_projects",
        "entity_level": "Project",
        "input_role": "real-world",
    }
    assert entry["numerator_key"] is None
    assert entry["denominator_key"] is None


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

    manifest = build_manifest(
        built, ArtifactSet(Path.cwd()), repo_url="https://github.com/glockyco/Teralizer"
    )

    assert len(manifest["run"]["source_commit"]) == 40
    assert isinstance(manifest["run"]["dirty"], bool)
    assert len(manifest["run"]["derived_view_revision"]) == 64
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
