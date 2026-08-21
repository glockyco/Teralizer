"""Tests for corpus publication provenance and pre-promotion validation."""

from __future__ import annotations

from copy import deepcopy
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
                "derived_view_revision": "current" if entry.derived_views else None,
                "inputs": [input_fact],
                "provenance": {"commits": [], "unattributed_projects": 0},
            }
        )
    return {"schema_version": 1, "corpora": records}, registry


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
