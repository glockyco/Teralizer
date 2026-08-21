"""Tests for the registered physical-name source boundary."""

from __future__ import annotations

from pathlib import Path

from teralizer.corpus_literal_check import LiteralOccurrence, find_occurrences


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
