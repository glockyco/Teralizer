"""Observation-only RepoReapers evidence reconstruction contracts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import cast

import pytest

from teralizer import corpora
from teralizer.eval.evidence import reporeapers_reconstruction as reconstruction

_REVISION = "a" * 40


def _records(document: dict[str, object], key: str) -> list[dict[str, object]]:
    return cast(list[dict[str, object]], document[key])


def _inventory_spec(path: Path) -> dict[str, object]:
    return {
        "schema_version": 1,
        "population": {
            "corpus_id": "real-world",
            "database": corpora.resolve("real-world").database,
            "variant": "rq6",
            "producer_revision": _REVISION,
        },
        "sources": [
            {
                "role": "project-logs",
                "path": str(path),
                "configured_path": "/recorded/logs",
                "producer_revision": _REVISION,
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
                    "project_root": "/projects/example",
                    "level": "Test",
                    "local_key": "ExampleTest#works",
                },
                "claim": "no-assertions",
                "status": "reconstructed",
                "confidence": "T2_CORROBORATED",
                "reason": "The source and preserved log agree.",
                "source_roles": ["project-logs"],
                "filter_outcome": "NO_ASSERTIONS",
                "reviewer": "fixture-reviewer",
            },
            {
                "identity": {
                    "corpus_id": "real-world",
                    "project_root": "/projects/example",
                    "level": "Assertion",
                    "local_key": "ExampleTest#works:12:assertThat",
                },
                "claim": "assertion-to-mut",
                "status": "evidence-gap",
                "confidence": "NONE",
                "reason": "The preserved source declaration is absent.",
                "source_roles": [],
                "filter_outcome": "UNRESOLVED_SOURCE_DECLARATION",
                "reviewer": "fixture-reviewer",
            },
        ],
        "claims": [
            {
                "claim": "no-assertions",
                "status": "reconstructed",
                "numerator": 1,
                "denominator": 1,
                "method": "complete classification",
                "reason": "One frozen entity was reconstructed.",
            },
            {
                "claim": "assertion-to-mut",
                "status": "evidence-gap",
                "numerator": 1,
                "denominator": 1,
                "method": "complete classification",
                "reason": "The only frozen entity lacks source evidence.",
            },
        ],
        "integrity_issues": [],
    }


def test_current_registry_has_one_canonical_reporeapers_mapping():
    reconstruction.validate_canonical_registry()


def test_inventory_hashes_collected_sources_without_copying_contents(tmp_path: Path):
    source = tmp_path / "logs"
    source.mkdir()
    (source / "project-1.log").write_text("first run\n", encoding="utf-8")
    (source / "nested").mkdir()
    (source / "nested/output.log").write_text("failure\n", encoding="utf-8")

    inventory = reconstruction.build_inventory(_inventory_spec(source))

    record = _records(inventory, "sources")[0]
    assert record["role"] == "project-logs"
    assert record["kind"] == "directory"
    assert record["file_count"] == 2
    assert record["total_bytes"] == 18
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


def test_inventory_rejects_missing_collected_source(tmp_path: Path):
    with pytest.raises(reconstruction.ReconstructionError, match="source is missing"):
        reconstruction.build_inventory(_inventory_spec(tmp_path / "absent"))


def test_inventory_rejects_duplicate_source_roles(tmp_path: Path):
    source = tmp_path / "log"
    source.write_text("collected", encoding="utf-8")
    specification = _inventory_spec(source)
    sources = _records(specification, "sources")
    sources.append(dict(sources[0]))

    with pytest.raises(reconstruction.ReconstructionError, match="duplicate.*roles"):
        reconstruction.build_inventory(specification)


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


def test_audit_reconciles_entity_statuses_and_source_roles(tmp_path: Path):
    source = tmp_path / "project.log"
    source.write_text("collected", encoding="utf-8")
    inventory = reconstruction.build_inventory(_inventory_spec(source))

    actual = reconstruction.validate_audit(_audit(inventory))

    claims = _records(actual, "claims")
    assert claims[0]["numerator"] == 1
    assert claims[1]["status"] == "evidence-gap"


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
    _records(audit, "claims")[0]["numerator"] = 0

    with pytest.raises(reconstruction.ReconstructionError, match="does not reconcile"):
        reconstruction.validate_audit(audit)


@pytest.mark.parametrize("forbidden", ["pipeline", "project", "task", "build", "retry"])
def test_command_surface_refuses_execution_paths(forbidden: str):
    with pytest.raises(SystemExit):
        reconstruction.parser().parse_args([forbidden])
