"""Tests for the registered physical-name source boundary."""

from __future__ import annotations

from pathlib import Path
import subprocess

import pytest

from teralizer.corpus_literal_check import (
    LiteralOccurrence,
    PayloadOccurrence,
    find_indexed_payload_occurrences,
    find_occurrences,
    find_payload_occurrences,
)


def test_live_consumer_literal_is_reported_with_its_location(tmp_path: Path):
    script = tmp_path / "scripts/run-report.sh"
    script.parent.mkdir(parents=True)
    script.write_text("DB_NAME=postgres_published\n", encoding="utf-8")

    assert find_occurrences(tmp_path, ("postgres_published",)) == (
        LiteralOccurrence(Path("scripts/run-report.sh"), 1, "postgres_published"),
    )


def test_registry_and_generated_package_inventory_are_allowed(tmp_path: Path):
    registry = tmp_path / "src/main/resources/db/corpora.toml"
    registry.parent.mkdir(parents=True)
    registry.write_text('database = "postgres_published"\n', encoding="utf-8")
    checksums = tmp_path / "replication/datasets/checksums.sha256"
    checksums.parent.mkdir(parents=True)
    checksums.write_text("sha postgres_published.dump\n", encoding="utf-8")

    assert find_occurrences(tmp_path, ("postgres_published",)) == ()


@pytest.mark.parametrize(
    ("path", "content", "kind"),
    [
        (Path("scratch/renamed.dump"), b"PGDMP synthetic", "PostgreSQL custom dump"),
        (
            Path("scratch/package.json"),
            b'{"schema_version":1,"corpora":[],"producer":{}}',
            "generated corpus manifest",
        ),
        (
            Path("scratch/inventory.sha256"),
            b"a" * 64 + b"  corpus.dump\n",
            "generated dump checksums",
        ),
    ],
)
def test_generated_corpus_payload_is_rejected(path: Path, content: bytes, kind: str):
    assert find_payload_occurrences(((path, content),)) == (
        PayloadOccurrence(path, kind),
    )


def test_source_declarations_and_synthetic_fixture_are_allowed():
    files = (
        (Path("src/main/resources/db/corpora.toml"), b'corpus_id = "controlled"'),
        (Path("replication/release-reference.json"), b'{"version":"2"}'),
        (
            Path("verification/fixtures/corpus-package/synthetic.dump"),
            b"PGDMP synthetic",
        ),
    )

    assert find_payload_occurrences(files) == ()


def test_force_added_ignored_dump_is_rejected(tmp_path: Path):
    subprocess.run(["git", "init", "-q"], cwd=tmp_path, check=True)
    (tmp_path / ".gitignore").write_text("/generated/\n", encoding="utf-8")
    dump = tmp_path / "generated/corpus.dump"
    dump.parent.mkdir()
    dump.write_bytes(b"PGDMP synthetic")
    subprocess.run(
        ["git", "add", ".gitignore", "-f", "generated/corpus.dump"],
        cwd=tmp_path,
        check=True,
    )

    assert find_indexed_payload_occurrences(tmp_path) == (
        PayloadOccurrence(Path("generated/corpus.dump"), "PostgreSQL custom dump"),
    )
