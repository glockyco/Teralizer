"""Tests for corpus publication provenance and pre-promotion validation."""

from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from teralizer import corpora, corpus_publish


def test_producer_provenance_counts_each_commit_and_unattributed_projects():
    conn = MagicMock()
    conn.execute.return_value = [
        ("a" * 40, 7),
        ("b" * 40, 3),
        (None, 2),
    ]

    assert corpus_publish.producer_provenance(conn) == {
        "commits": [
            {"commit": "a" * 40, "projects": 7},
            {"commit": "b" * 40, "projects": 3},
        ],
        "unattributed_projects": 2,
    }


def test_publication_plan_names_every_missing_published_corpus(monkeypatch):
    entries = (
        corpora.CorpusEntry(
            "controlled", "controlled_db", None, None, 2, True, "fixture", True
        ),
        corpora.CorpusEntry(
            "real-world", "real_world_db", None, None, 3, True, "fixture", False
        ),
        corpora.CorpusEntry(
            "jarvis", "jarvis_db", None, None, 1, True, "fixture", False
        ),
    )
    registry = corpora.CorpusRegistry(entries)

    monkeypatch.setattr(corpus_publish, "require_publishable_tree", lambda: None)
    monkeypatch.setattr(corpus_publish, "checkout_snapshot", lambda: ("a" * 40, False))
    monkeypatch.setattr(corpus_publish.corpora, "load", lambda: registry)
    monkeypatch.setattr(
        corpus_publish, "_installed_databases", lambda: {"postgres", "controlled_db"}
    )

    with pytest.raises(RuntimeError) as raised:
        corpus_publish.publication_plan()

    assert str(raised.value) == (
        "complete corpus publication cannot continue; missing corpora: "
        "real-world (real_world_db), jarvis (jarvis_db)"
    )


def _manifest_fixture(tmp_path: Path, monkeypatch):
    entries = (
        corpora.CorpusEntry(
            "controlled", "controlled_db", None, None, 2, True, "fixture", True
        ),
        corpora.CorpusEntry(
            "auxiliary", "auxiliary_db", None, None, 1, True, "fixture", False
        ),
    )
    registry = corpora.CorpusRegistry(entries)
    monkeypatch.setattr(corpora, "derived_view_revision", lambda: "current")
    input_fact = corpus_publish._file_fact(
        corpora.REGISTRY_PATH, relative_to=corpus_publish._REPO_ROOT
    ).__dict__
    records = []
    for entry in entries:
        dump_path = tmp_path / f"{entry.database}.dump"
        dump_path.write_bytes(entry.id.encode())
        records.append(
            {
                "corpus_id": entry.id,
                "database": entry.database,
                "dump": corpus_publish._file_fact(
                    dump_path, relative_to=tmp_path
                ).__dict__,
                "expected_projects": entry.expected_projects,
                "observed_projects": entry.expected_projects,
                "database_bytes": entry.expected_projects * 1000,
                "derived_view_revision": "current" if entry.derived_views else None,
                "inputs": [input_fact],
                "provenance": {"commits": [], "unattributed_projects": 0},
            }
        )
    return {
        "schema_version": 1,
        "producer": {"source_commit": "a" * 40, "dirty": False},
        "corpora": records,
    }, registry


def test_manifest_validation_accepts_one_complete_fact_set(tmp_path: Path, monkeypatch):
    document, registry = _manifest_fixture(tmp_path, monkeypatch)

    corpus_publish.validate_manifest(document, tmp_path, registry)


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        (lambda records: records.pop(), "do not match published ids"),
        (
            lambda records: records[0].__setitem__("database", "wrong"),
            "database disagrees",
        ),
        (
            lambda records: records.append(deepcopy(records[0])),
            "do not match published ids",
        ),
        (
            lambda records: records[0].__setitem__("derived_view_revision", "stale"),
            "derived-view revision disagrees",
        ),
        (
            lambda records: records[0].__setitem__(
                "inputs", [{"path": "missing", "sha256": "0" * 64, "bytes": 0}]
            ),
            "input fact disagrees",
        ),
        (
            lambda records: records[0]["dump"].__setitem__("sha256", "0" * 64),
            "dump fact disagrees",
        ),
    ],
)
def test_manifest_validation_rejects_inconsistent_facts(
    tmp_path: Path, monkeypatch, mutation, message
):
    document, registry = _manifest_fixture(tmp_path, monkeypatch)
    records = document["corpora"]
    assert isinstance(records, list)
    mutation(records)

    with pytest.raises(ValueError, match=message):
        corpus_publish.validate_manifest(document, tmp_path, registry)


def test_package_verification_checks_manifest_dumps_inputs_and_inventory(
    tmp_path: Path, monkeypatch
):
    document, registry = _manifest_fixture(tmp_path, monkeypatch)
    monkeypatch.setattr(corpus_publish.corpora, "load", lambda: registry)
    (tmp_path / corpus_publish.MANIFEST_NAME).write_text(
        json.dumps(document), encoding="utf-8"
    )
    (tmp_path / corpus_publish.CHECKSUMS_NAME).write_text(
        corpus_publish._checksums(document), encoding="utf-8"
    )

    assert corpus_publish.verify_package(tmp_path) == (
        tmp_path / corpus_publish.MANIFEST_NAME
    )
    assert corpus_publish.package_corpus_fields(tmp_path, "controlled") == (
        "controlled_db",
        "controlled_db.dump",
        2,
        "current",
    )
    with pytest.raises(ValueError, match="no unique entry"):
        corpus_publish.package_corpus_fields(tmp_path, "missing")
    destination = tmp_path / "package-root"
    assert corpus_publish.copy_package_inputs(tmp_path, destination) == 1
    assert (destination / "src/main/resources/db/corpora.toml").is_file()
    assert corpus_publish.required_disk_bytes(tmp_path) == 6019
    assert corpus_publish.package_preflight(tmp_path)[0] == "required_disk_bytes=6019"
    package_destination = tmp_path / "package-artifacts"
    assert corpus_publish.copy_package_artifacts(tmp_path, package_destination) == 4
    assert (package_destination / "controlled_db.dump").read_bytes() == b"controlled"

    complete_destination = tmp_path / "complete-package"
    assert corpus_publish.copy_complete_package(tmp_path, complete_destination) == (
        4,
        1,
    )
    assert (
        complete_destination / "replication/datasets/controlled_db.dump"
    ).read_bytes() == b"controlled"
    assert (complete_destination / "src/main/resources/db/corpora.toml").is_file()

    (tmp_path / corpus_publish.CHECKSUMS_NAME).write_text("stale\n", encoding="utf-8")
    with pytest.raises(ValueError, match="checksum inventory disagrees"):
        corpus_publish.verify_package(tmp_path)


def test_assembly_consumes_explicit_dumps_without_exporting(
    tmp_path: Path, monkeypatch
):
    document, registry = _manifest_fixture(tmp_path, monkeypatch)
    plan = deepcopy(document)
    records = plan["corpora"]
    assert isinstance(records, list)
    for record in records:
        dump = record.pop("dump")
        record["dump_path"] = dump["path"]
    monkeypatch.setattr(corpus_publish, "publication_plan", lambda: plan)
    monkeypatch.setattr(corpus_publish.corpora, "load", lambda: registry)
    output = tmp_path / "output"

    manifest = corpus_publish.assemble(tmp_path, output)

    assert manifest == output / corpus_publish.MANIFEST_NAME
    assert corpus_publish.verify_package(output) == manifest
    assert (tmp_path / "controlled_db.dump").is_file()


def test_promotion_removes_stale_dumps_only_after_complete_stage(tmp_path: Path):
    stage = tmp_path / "stage"
    output = tmp_path / "output"
    stage.mkdir()
    output.mkdir()
    (output / "stale.dump").write_bytes(b"stale")
    for name in (
        "current.dump",
        corpus_publish.MANIFEST_NAME,
        corpus_publish.CHECKSUMS_NAME,
    ):
        (stage / name).write_bytes(b"current")

    corpus_publish._promote(stage, output, {"current.dump"})

    assert not (output / "stale.dump").exists()
    assert (output / "current.dump").read_bytes() == b"current"
