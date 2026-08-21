"""Tests for semantic evaluation-corpus resolution."""

from __future__ import annotations

from dataclasses import FrozenInstanceError
from pathlib import Path

import pytest
from sqlalchemy import create_engine, text

from teralizer import corpora


def _write_registry(path: Path, entries: str) -> Path:
    path.write_text(entries, encoding="utf-8")
    return path


def test_repository_registry_declares_each_published_corpus_once():
    registry = corpora.load()

    assert tuple(entry.id for entry in registry.entries) == (
        "controlled",
        "real-world",
        "jarvis-benchmark",
        "jarvis-scenarios",
    )
    assert len({entry.database for entry in registry.entries}) == 4
    assert registry.get("real-world").expected_projects == 1161
    assert registry.get("real-world").data_dir == "data/reporeapers-rerun-v7"
    assert registry.get("controlled").data_dir is None
    assert registry.published_entries == registry.entries


def test_registry_classifies_registered_scratch_and_unclassified_databases():
    registry = corpora.load()

    assert registry.classify("postgres_dev") == corpora.DatabaseClassification(
        "postgres_dev", corpora.DatabaseKind.CORPUS, "controlled"
    )
    assert registry.classify("scratch_verification") == corpora.DatabaseClassification(
        "scratch_verification", corpora.DatabaseKind.SCRATCH
    )
    assert registry.classify("scratch_").kind is corpora.DatabaseKind.UNCLASSIFIED
    assert registry.classify("postgres_test").kind is corpora.DatabaseKind.UNCLASSIFIED
    assert tuple(
        classification.kind
        for classification in registry.classify_all(
            ("postgres_dev", "scratch_verification", "postgres_test")
        )
    ) == (
        corpora.DatabaseKind.CORPUS,
        corpora.DatabaseKind.SCRATCH,
        corpora.DatabaseKind.UNCLASSIFIED,
    )


def test_registry_rejects_a_missing_required_field(tmp_path: Path):
    path = _write_registry(
        tmp_path / "corpora.toml",
        """
[[corpus]]
id = "controlled"
database = "postgres_dev"
data_dir = ""
config_dir = ""
expected_projects = 13
""",
    )

    with pytest.raises(
        ValueError, match="corpus 'controlled' is missing required field 'notes'"
    ):
        corpora.load(path)


def test_registry_requires_boolean_publication_status(tmp_path: Path):
    missing_path = _write_registry(
        tmp_path / "missing-published.toml",
        """
[[corpus]]
id = "controlled"
database = "postgres_dev"
data_dir = ""
config_dir = ""
expected_projects = 13
notes = "fixture"
""",
    )
    invalid_path = _write_registry(
        tmp_path / "invalid-published.toml",
        """
[[corpus]]
id = "controlled"
database = "postgres_dev"
data_dir = ""
config_dir = ""
expected_projects = 13
published = "yes"
notes = "fixture"
""",
    )

    with pytest.raises(ValueError, match="missing required field 'published'"):
        corpora.load(missing_path)
    with pytest.raises(ValueError, match="field 'published' must be a boolean"):
        corpora.load(invalid_path)


def test_registry_rejects_duplicate_physical_database_names(tmp_path: Path):
    path = _write_registry(
        tmp_path / "corpora.toml",
        """
[[corpus]]
id = "controlled"
database = "same_database"
data_dir = ""
config_dir = ""
expected_projects = 13
published = true
notes = "first"

[[corpus]]
id = "real-world"
database = "same_database"
data_dir = "data/run"
config_dir = "project-configs/run"
expected_projects = 632
published = true
notes = "second"
""",
    )

    with pytest.raises(
        ValueError,
        match=(
            "corpora 'controlled' and 'real-world' declare duplicate physical "
            "database 'same_database'"
        ),
    ):
        corpora.load(path)


def test_registry_lookup_is_semantic_and_immutable():
    registry = corpora.load()
    entry = corpora.resolve("jarvis-benchmark", registry)

    assert entry.database == "postgres_jarvis_census"
    with pytest.raises(FrozenInstanceError):
        entry.database = "other"  # type: ignore[misc]
    with pytest.raises(KeyError, match="unknown corpus id 'missing'"):
        registry.get("missing")


def test_registry_cli_returns_one_field_and_shell_safe_exports(capsys):
    corpora.main(["get", "real-world", "database"])
    assert capsys.readouterr().out == "postgres_reporeapers_rq6_v7\n"

    corpora.main(["list", "--published"])
    assert capsys.readouterr().out.splitlines() == [
        "controlled",
        "real-world",
        "jarvis-benchmark",
        "jarvis-scenarios",
    ]

    entry = corpora.CorpusEntry(
        id="fixture",
        database="database with spaces; still one value",
        data_dir=None,
        config_dir=None,
        expected_projects=1,
        published=False,
        notes="fixture",
    )
    assert corpora.shell_exports(entry).splitlines() == [
        "export TERALIZER_CORPUS_ID=fixture",
        "export DB_NAME='database with spaces; still one value'",
        "export DATA_DIR=''",
        "export CONFIG_DIR=''",
        "export EXPECTED_PROJECTS=1",
        "export CORPUS_PUBLISHED=false",
    ]
    assert corpora.field_value(entry, "published") == "false"


def test_project_count_validation_names_expected_and_observed_counts():
    engine = create_engine("sqlite:///:memory:")
    entry = corpora.CorpusEntry(
        id="controlled",
        database="postgres_dev",
        data_dir=None,
        config_dir=None,
        expected_projects=2,
        published=True,
        notes="fixture",
    )
    try:
        with engine.begin() as conn:
            conn.execute(text("CREATE TABLE project (id INTEGER PRIMARY KEY)"))
            conn.execute(text("INSERT INTO project VALUES (1)"))
            with pytest.raises(
                RuntimeError,
                match=(
                    "corpus 'controlled' expects 2 projects in 'postgres_dev'; "
                    "observed 1"
                ),
            ):
                corpora.validate_project_count(conn, entry)
    finally:
        engine.dispose()
