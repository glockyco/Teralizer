import copy

import pytest
from teralizer.release import documents

_RELEASE = {
    "name": "teralizer-replication",
    "version": "2.0.0",
    "version_doi": "10.5281/zenodo.1",
    "concept_doi": "10.5281/zenodo.0",
    "source_revision": "a" * 40,
}
_SHA = "b" * 64


def _component() -> dict[str, object]:
    return {
        "format": "teralizer-component-manifest",
        "format_version": 1,
        "component": "corpus-controlled",
        "release": dict(_RELEASE),
        "root": "teralizer/components/corpus-controlled",
        "purpose": "Controlled corpus dump.",
        "requires": ["core"],
        "incompatible_with": [],
        "licenses": [
            {
                "scope": "payload",
                "license": "CC-BY-4.0",
                "file": "teralizer/components/corpus-controlled/LICENSE",
            }
        ],
        "provenance": {"corpus": "controlled"},
        "payload": [
            {
                "path": "teralizer/components/corpus-controlled/controlled.dump",
                "sha256": _SHA,
                "bytes": 10,
                "executable": False,
            }
        ],
    }


def _release_manifest() -> dict[str, object]:
    return {
        "format": "teralizer-release-manifest",
        "format_version": 1,
        "release": dict(_RELEASE),
        "paper": {"title": "Teralizer", "authors": ["A"], "doi": "10.1/x"},
        "archives": [
            {
                "component": component,
                "filename": f"teralizer-{component}.zip",
                "sha256": _SHA,
                "bytes": 1,
                "unpacked_bytes": 2,
                "purpose": "Fixture.",
                "requires": [] if component == "core" else ["core"],
                "component_manifest_sha256": _SHA,
            }
            for component in ("core", "corpus-controlled")
        ],
        "workflows": [
            {
                "id": "reproduce",
                "command": "artifact reproduce-results",
                "components": ["core", "corpus-controlled"],
            }
        ],
    }


def _acceptance(outcome: str = "passed") -> dict[str, object]:
    return {
        "format": "teralizer-acceptance-record",
        "format_version": 1,
        "release_manifest_sha256": _SHA,
        "release": dict(_RELEASE),
        "environment": {
            "host_architecture": "x86_64",
            "container_architecture": "amd64",
            "os": "Ubuntu 24.04",
            "docker": "27.0",
            "images": [{"reference": "postgres:17.1", "digest": "sha256:" + _SHA}],
        },
        "steps": [
            {
                "workflow": "smoke",
                "components": ["core"],
                "command": "artifact smoke",
                "network": False,
                "outcome": outcome,
                "wall_seconds": 12.5,
                "peak_memory_bytes": 1024,
                "peak_disk_bytes": {"workspace": 2048},
            }
        ],
        "cleanup": {"command": "artifact clean", "outcome": "passed", "remaining": []},
        "result": outcome,
    }


def test_valid_documents_pass():
    documents.validate_component_manifest(_component())
    documents.validate_release_manifest(_release_manifest())
    documents.validate_acceptance_record(_acceptance())
    documents.validate_acceptance_record(_acceptance("failed"))


def test_payload_outside_the_component_root_is_rejected():
    manifest = _component()
    payload = manifest["payload"]
    assert isinstance(payload, list)
    payload[0]["path"] = "teralizer/components/corpus-real-world/real-world.dump"

    with pytest.raises(documents.DocumentError, match="outside the component root"):
        documents.validate_component_manifest(manifest)


def test_duplicate_payload_paths_are_rejected():
    manifest = _component()
    payload = manifest["payload"]
    assert isinstance(payload, list)
    payload.append(copy.deepcopy(payload[0]))

    with pytest.raises(documents.DocumentError, match="listed twice"):
        documents.validate_component_manifest(manifest)


def test_workflow_cannot_need_a_component_the_release_lacks():
    manifest = _release_manifest()
    workflows = manifest["workflows"]
    assert isinstance(workflows, list)
    workflows[0]["components"] = ["core", "corpus-real-world"]

    with pytest.raises(documents.DocumentError, match="not in the release"):
        documents.validate_release_manifest(manifest)


def test_acceptance_result_must_follow_from_its_steps():
    record = _acceptance("failed")
    record["result"] = "passed"

    with pytest.raises(documents.DocumentError, match="result must be failed"):
        documents.validate_acceptance_record(record)


def test_other_format_versions_are_rejected():
    manifest = _release_manifest()
    manifest["format_version"] = 2

    with pytest.raises(documents.DocumentError, match="format_version must be 1"):
        documents.validate_release_manifest(manifest)
