"""Observation-only RepoReapers evidence reconstruction contracts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import cast

import pytest

from teralizer import corpora
from teralizer.eval.evidence import reporeapers_populations as populations
from teralizer.eval.evidence import reporeapers_reconstruction as reconstruction

_REVISION = "a" * 40
_REPO_ROOT = Path(__file__).resolve().parents[3]
_INVENTORY = (
    _REPO_ROOT / "analysis/data/report-inputs/reporeapers-reconstruction-inventory.json"
)


def _records(document: dict[str, object], key: str) -> list[dict[str, object]]:
    return cast(list[dict[str, object]], document[key])


def _inventory_spec(path: Path) -> dict[str, object]:
    return {
        "schema_version": 1,
        "population": {
            "corpus_id": "real-world",
            "database": corpora.resolve("real-world").database,
            "variant": "rq6",
            "producer_revisions": [_REVISION],
        },
        "sources": [
            {
                "source_id": "project-logs-v7",
                "role": "project-logs",
                "path": str(path),
                "configured_path": "/recorded/logs",
                "producer_revisions": [_REVISION],
                "expected_paths": [],
                "attributes": {"run": "version-7"},
            }
        ],
    }


def _audit(inventory: dict[str, object]) -> dict[str, object]:
    return {
        "schema_version": 1,
        "inventory": inventory,
        "entities": [
            {
                "identity": {
                    "corpus_id": "real-world",
                    "project_root": "projects/example",
                    "project_revision": _REVISION,
                    "level": "Test",
                    "local_key": "ExampleTest#works",
                },
                "claim": "no-assertions",
                "status": "resolved",
                "label": "true-positive",
                "confidence": "T2_CORROBORATED",
                "rationale": "The source and preserved log agree.",
                "source_ids": ["project-logs-v7"],
                "filter_outcome": "NO_ASSERTIONS",
                "reviews": [
                    {
                        "reviewer": "fixture-reviewer",
                        "label": "true-positive",
                        "rationale": "The source and preserved log agree.",
                        "confidence": "T2_CORROBORATED",
                        "source_ids": ["project-logs-v7"],
                    }
                ],
                "review_state": "single-reviewed",
            },
            {
                "identity": {
                    "corpus_id": "real-world",
                    "project_root": "projects/example",
                    "project_revision": _REVISION,
                    "level": "Assertion",
                    "local_key": "ExampleTest#works:12:assertThat",
                },
                "claim": "assertion-to-mut",
                "status": "unresolved",
                "label": "unresolved",
                "confidence": "NONE",
                "rationale": "The preserved source declaration is absent.",
                "source_ids": [],
                "filter_outcome": "UNRESOLVED_SOURCE_DECLARATION",
                "reviews": [],
                "review_state": "unreviewed",
            },
        ],
        "claims": [
            {
                "claim": "no-assertions",
                "status": "supported",
                "population_definition": "Version 7 NO_ASSERTIONS exclusions.",
                "population_sha256": "a" * 64,
                "resolved": 1,
                "unresolved": 0,
                "incompatible": 0,
                "total": 1,
                "method": "complete classification",
                "reason": "One frozen entity was reconstructed.",
            },
            {
                "claim": "assertion-to-mut",
                "status": "evidence-gap",
                "population_definition": "Version 7 unresolved assertions.",
                "population_sha256": "b" * 64,
                "resolved": 0,
                "unresolved": 1,
                "incompatible": 0,
                "total": 1,
                "method": "complete classification",
                "reason": "The only frozen entity lacks source evidence.",
            },
        ],
        "integrity_issues": [],
    }


def test_current_registry_has_one_canonical_reporeapers_mapping():
    reconstruction.validate_canonical_registry()


def test_shipped_inventory_contains_only_version_seven_evidence():
    inventory = reconstruction.validate_inventory(
        json.loads(_INVENTORY.read_text(encoding="utf-8"))
    )
    population = cast(dict[str, object], inventory["population"])
    records = _records(inventory, "sources")

    assert population["database"] == "postgres_reporeapers_rq6_v7"
    assert {record["source_id"] for record in records} == {
        "version-seven-database-export",
        "version-seven-facts-record",
        "version-seven-run-status",
        "version-seven-project-logs",
        "version-seven-configurations",
        "version-seven-project-checkouts",
        "version-seven-run-artifacts",
        "version-seven-project-mapping",
    }
    assert all("version-seven" in cast(str, record["source_id"]) for record in records)
    facts = next(
        record
        for record in records
        if record["source_id"] == "version-seven-facts-record"
    )
    assert cast(dict[str, object], facts["attributes"])["project_count"] == 1161


def test_inventory_hashes_collected_sources_without_copying_contents(tmp_path: Path):
    source = tmp_path / "logs"
    source.mkdir()
    (source / "project-1.log").write_text("first run\n", encoding="utf-8")
    (source / "nested").mkdir()
    (source / "nested/output.log").write_text("failure\n", encoding="utf-8")

    inventory = reconstruction.build_inventory(_inventory_spec(source))

    record = _records(inventory, "sources")[0]
    assert record["source_id"] == "project-logs-v7"
    assert record["role"] == "project-logs"
    assert record["available"] is True
    assert record["kind"] == "directory"
    assert record["paths"] == ["project-1.log", "nested/output.log"]
    assert record["file_count"] == 2
    assert record["total_bytes"] == 18
    assert record["attributes"] == {"run": "version-7"}
    assert len(cast(str, record["sha256"])) == 64
    assert "first run" not in json.dumps(inventory)
    assert inventory["integrity_issues"] == []


def test_inventory_digest_changes_with_member_path_or_content(tmp_path: Path):
    source = tmp_path / "logs"
    source.mkdir()
    member = source / "a.log"
    member.write_text("same", encoding="utf-8")
    first = reconstruction.build_inventory(_inventory_spec(source))
    member.rename(source / "b.log")
    renamed = reconstruction.build_inventory(_inventory_spec(source))
    (source / "b.log").write_text("changed", encoding="utf-8")
    changed = reconstruction.build_inventory(_inventory_spec(source))

    digests = {
        _records(first, "sources")[0]["sha256"],
        _records(renamed, "sources")[0]["sha256"],
        _records(changed, "sources")[0]["sha256"],
    }
    assert len(digests) == 3


def test_inventory_records_missing_collected_source(tmp_path: Path):
    inventory = reconstruction.build_inventory(_inventory_spec(tmp_path / "absent"))

    record = _records(inventory, "sources")[0]
    assert record["available"] is False
    assert record["sha256"] is None
    assert inventory["integrity_issues"] == [
        f"project-logs-v7: collected source is missing: {tmp_path / 'absent'}"
    ]


def test_validate_inventory_command_accepts_manifest(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
):
    source = tmp_path / "source.log"
    source.write_text("collected", encoding="utf-8")
    inventory_path = tmp_path / "inventory.json"
    inventory_path.write_text(
        json.dumps(reconstruction.build_inventory(_inventory_spec(source))),
        encoding="utf-8",
    )

    reconstruction.main(["validate-inventory", str(inventory_path)])

    assert capsys.readouterr().out.strip() == str(inventory_path)


def test_inventory_rejects_duplicate_source_ids(tmp_path: Path):
    source = tmp_path / "log"
    source.write_text("collected", encoding="utf-8")
    specification = _inventory_spec(source)
    sources = _records(specification, "sources")
    sources.append(dict(sources[0]))

    with pytest.raises(reconstruction.ReconstructionError, match="duplicate.*ids"):
        reconstruction.build_inventory(specification)


def test_inventory_allows_repeated_roles_and_records_integrity_issues(tmp_path: Path):
    first = tmp_path / "first"
    second = tmp_path / "second"
    first.mkdir()
    second.mkdir()
    (first / "project-1.txt").write_text("first", encoding="utf-8")
    (second / "project-2.txt").write_text("second", encoding="utf-8")
    specification = _inventory_spec(first)
    sources = _records(specification, "sources")
    sources[0]["expected_paths"] = ["project-1.txt", "project-2.txt"]
    sources.append(
        {
            "source_id": "project-logs-v6",
            "role": "project-logs",
            "path": str(second),
            "configured_path": "/recorded/logs",
            "producer_revisions": ["b" * 40],
            "expected_paths": ["project-2.txt"],
            "attributes": {},
        }
    )

    inventory = reconstruction.build_inventory(specification)

    assert [record["role"] for record in _records(inventory, "sources")] == [
        "project-logs",
        "project-logs",
    ]
    assert inventory["integrity_issues"] == [
        "project-logs-v7: missing paths: ['project-2.txt']",
        "project-logs-v6: producer revisions differ from the population",
    ]


def test_inventory_records_duplicate_source_locations(tmp_path: Path):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    specification = _inventory_spec(source)
    duplicate = dict(_records(specification, "sources")[0])
    duplicate["source_id"] = "same-log-second-identity"
    _records(specification, "sources").append(duplicate)

    inventory = reconstruction.build_inventory(specification)

    assert inventory["integrity_issues"] == [
        f"duplicate collected-source location: {source}"
    ]


def test_registry_rejects_historical_reporeapers_alias(tmp_path: Path):
    registry = tmp_path / "corpora.toml"
    canonical_database = corpora.resolve("real-world").database
    registry.write_text(
        f"""
[[corpus]]
id = "real-world"
database = "{canonical_database}"
data_dir = "data/reporeapers-rerun-v7"
config_dir = "project-configs/replication/extended"
expected_projects = 1161
published = true
derived_views = true
notes = "canonical"

[[corpus]]
id = "reporeapers-v6"
database = "postgres_reporeapers_rq6_v6"
data_dir = "data/reporeapers-rerun-v6"
config_dir = "project-configs/replication/extended"
expected_projects = 1161
published = false
derived_views = true
notes = "historical alias"
""".strip(),
        encoding="utf-8",
    )

    with pytest.raises(reconstruction.ReconstructionError, match="aliases remain"):
        reconstruction.validate_canonical_registry(registry)


def test_audit_reconciles_entity_statuses_and_source_ids(tmp_path: Path):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    inventory = reconstruction.build_inventory(_inventory_spec(source))

    actual = reconstruction.validate_audit(_audit(inventory))

    claims = _records(actual, "claims")
    assert claims[0]["resolved"] == 1
    assert claims[1]["status"] == "evidence-gap"


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("status", "reconstructed", "unknown status"),
        ("review_state", "accepted", "unknown status, confidence, or review state"),
        ("confidence", "HIGH", "unknown status, confidence, or review state"),
    ],
)
def test_audit_rejects_unknown_entity_vocabulary(
    tmp_path: Path, field: str, value: str, message: str
):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    audit = _audit(reconstruction.build_inventory(_inventory_spec(source)))
    _records(audit, "entities")[0][field] = value

    with pytest.raises(reconstruction.ReconstructionError, match=message):
        reconstruction.validate_audit(audit)


def test_audit_rejects_project_identity_without_revision_field(tmp_path: Path):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    audit = _audit(reconstruction.build_inventory(_inventory_spec(source)))
    identity = cast(dict[str, object], _records(audit, "entities")[0]["identity"])
    del identity["project_revision"]

    with pytest.raises(
        reconstruction.ReconstructionError, match="identity.*keys differ"
    ):
        reconstruction.validate_audit(audit)


def test_audit_rejects_surrogate_identity_fields(tmp_path: Path):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    audit = _audit(reconstruction.build_inventory(_inventory_spec(source)))
    _records(audit, "entities")[0]["identity"] = {"project_id": 17}

    with pytest.raises(
        reconstruction.ReconstructionError, match="identity.*keys differ"
    ):
        reconstruction.validate_audit(audit)


def test_audit_rejects_unreconciled_claim_summary(tmp_path: Path):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    audit = _audit(reconstruction.build_inventory(_inventory_spec(source)))
    _records(audit, "claims")[0]["resolved"] = 0

    with pytest.raises(reconstruction.ReconstructionError, match="does not reconcile"):
        reconstruction.validate_audit(audit)


def _population_query() -> populations.PopulationQuery:
    return populations.PopulationQuery(
        claim="fixture",
        definition="Complete fixture rows.",
        sql="WITH fixture AS (SELECT 1) SELECT * FROM fixture",
        identity_fields=("project_root", "local_key"),
        fields=("project_root", "local_key", "evidence"),
    )


def test_population_identity_digest_is_order_independent():
    first = {
        "project_root": "/projects/first",
        "local_key": "Example#first",
        "evidence": {"signals": ["A", "B"]},
    }
    second = {
        "project_root": "/projects/second",
        "local_key": "Example#second",
        "evidence": None,
    }

    forward = populations.freeze_population(_population_query(), [first, second])
    reverse = populations.freeze_population(_population_query(), [second, first])

    assert forward["identity_sha256"] == reverse["identity_sha256"]
    assert forward["rows"] == reverse["rows"]


def test_population_rejects_duplicate_stable_identity():
    row = {
        "project_root": "projects/example",
        "local_key": "Example#works",
        "evidence": "first",
    }
    duplicate = dict(row, evidence="second")

    with pytest.raises(populations.PopulationError, match="duplicate identity"):
        populations.freeze_population(_population_query(), [row, duplicate])


def test_population_rejects_missing_query_field():
    incomplete = {
        "project_root": "projects/example",
        "local_key": "Example#works",
    }

    with pytest.raises(populations.PopulationError, match="fields differ"):
        populations.freeze_population(_population_query(), [incomplete])


def test_registered_population_queries_emit_no_database_surrogate_ids():
    for query in populations.REGISTERED_QUERIES:
        assert "id" not in query.fields
        assert "project_root" in query.identity_fields
        assert "project_revision" in query.identity_fields


def _source_query() -> populations.PopulationQuery:
    return populations.PopulationQuery(
        claim="source-fixture",
        definition="One source fixture.",
        sql="WITH fixture AS (SELECT 1) SELECT * FROM fixture",
        identity_fields=("project_root", "test_file_path"),
        fields=("project_root", "test_file_path", "filter_outcome"),
    )


def _source_row() -> dict[str, object]:
    return {
        "project_root": "projects/example",
        "test_file_path": "projects/example/src/test/ExampleTest.java",
        "filter_outcome": "NO_ASSERTIONS",
    }


def test_source_review_packet_reads_text_without_executing_it(tmp_path: Path):
    source = tmp_path / "projects/example/src/test/ExampleTest.java"
    source.parent.mkdir(parents=True)
    source.write_text(
        'class ExampleTest { String command = "touch executed"; }', encoding="utf-8"
    )

    packet = populations.build_source_review_packet(
        _source_query(), _source_row(), tmp_path
    )

    sources = _records(packet, "sources")
    assert sources[0]["content"] == source.read_text(encoding="utf-8")
    assert not (tmp_path / "executed").exists()


def _decision(
    *, reviewer: str, label: str, confidence: str = "T2_CORROBORATED"
) -> dict[str, object]:
    return {
        "identity": {
            "project_root": "projects/example",
            "test_file_path": "projects/example/src/test/ExampleTest.java",
        },
        "reviewer": reviewer,
        "label": label,
        "rationale": f"{reviewer} inspected the collected source.",
        "confidence": confidence,
        "source_ids": ["version-seven-project-checkouts"],
    }


def test_decision_import_preserves_reviewer_disagreement():
    labels = {
        "genuine-absence": reconstruction.EntityStatus.RESOLVED,
        "reachable-helper-assertion": reconstruction.EntityStatus.RESOLVED,
    }

    result = populations.adjudicate_decisions(
        _source_query(),
        _source_row(),
        [
            _decision(reviewer="first", label="genuine-absence"),
            _decision(reviewer="second", label="reachable-helper-assertion"),
        ],
        labels,
    )

    assert result["status"] == "unresolved"
    assert result["review_state"] == "disputed"
    assert len(cast(list[object], result["reviews"])) == 2


def test_decision_import_emits_evidence_gap_for_unreviewed_entity():
    result = populations.adjudicate_decisions(
        _source_query(),
        _source_row(),
        [],
        {"genuine-absence": reconstruction.EntityStatus.RESOLVED},
    )

    assert result == {
        "status": "unresolved",
        "label": "unresolved",
        "confidence": "NONE",
        "rationale": "No reviewer decision is available.",
        "source_ids": [],
        "reviews": [],
        "review_state": "unreviewed",
    }


def test_decision_import_rejects_invalid_population_join():
    decision = _decision(reviewer="first", label="genuine-absence")
    identity = cast(dict[str, object], decision["identity"])
    identity["test_file_path"] = "projects/example/src/test/OtherTest.java"

    with pytest.raises(populations.PopulationError, match="does not join"):
        populations.adjudicate_decisions(
            _source_query(),
            _source_row(),
            [decision],
            {"genuine-absence": reconstruction.EntityStatus.RESOLVED},
        )


@pytest.mark.parametrize("forbidden", ["pipeline", "project", "task", "build", "retry"])
def test_command_surface_refuses_execution_paths(forbidden: str):
    with pytest.raises(SystemExit):
        reconstruction.parser().parse_args([forbidden])
