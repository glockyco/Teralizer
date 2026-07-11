import json
from pathlib import Path
from teralizer.eval.model import Metric, RQReport, Section, Table
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.manifest import build_manifest, write_manifest


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


def test_rq0_manifest_records_report_basis():
    import pandas as pd

    tables = [
        Table(
            key=key,
            df=pd.DataFrame(),
            columns=[],
            caption=key,
            label=f"tab:{key}",
        )
        for key in (
            "rq0-table2-comparison",
            "rq0-breadth-summary",
            "rq0-pvc-budget",
        )
    ]
    report = RQReport(
        "rq0",
        "RQ0",
        "postgres_jarvis_scoreboard",
        [Section("S", tables)],
        metrics=[
            Metric("rq0.census.database", "postgres_jarvis_census"),
            Metric("rq0.table2.variant", "IMPROVED_100_TRIES"),
            Metric("rq0.census.variant", "IMPROVED_100_TRIES"),
            Metric("rq0.census.status", "partial"),
            Metric(
                "rq0.census.pvc_basis", "deduplicated_jqwik_value_logs_no_pit_reduction"
            ),
            Metric("rq0.census.intended_projects", 12),
            Metric("rq0.census.populated_projects", 9),
            Metric("rq0.census.completed_projects", 8),
            Metric("rq0.census.failed_projects", 2),
            Metric("rq0.census.failed_task_count", 4),
            Metric("rq0.census.completion_marker", "absent"),
        ],
    )

    manifest = build_manifest(report, repo_url="https://github.com/glockyco/Teralizer")
    basis = manifest["report_basis"]
    assert basis["databases"] == {
        "scoreboard": "postgres_jarvis_scoreboard",
        "census": "postgres_jarvis_census",
    }
    assert basis["variants"] == {
        "table2": "IMPROVED_100_TRIES",
        "census": "IMPROVED_100_TRIES",
    }
    assert basis["table_keys"] == [
        "rq0-table2-comparison",
        "rq0-breadth-summary",
        "rq0-pvc-budget",
    ]
    assert basis["census_status"] == "partial"
    assert basis["census_pit_reduction_required_for_pvc"] is False
    assert basis["census_is_mutation_result"] is False
