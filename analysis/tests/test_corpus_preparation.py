"""Tests for derived-schema preparation and report preflight."""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from teralizer import corpora, corpus_preparation


def test_revision_preflight_accepts_only_the_checked_in_revision(monkeypatch):
    monkeypatch.setattr(corpora, "derived_view_revision", lambda: "current")

    monkeypatch.setattr(corpus_preparation, "installed_revision", lambda _conn: None)
    with pytest.raises(RuntimeError, match="no installed derived-view revision"):
        corpus_preparation.require_current_revision(MagicMock(), "controlled")

    monkeypatch.setattr(corpus_preparation, "installed_revision", lambda _conn: "stale")
    with pytest.raises(RuntimeError, match="revision stale. expected current"):
        corpus_preparation.require_current_revision(MagicMock(), "controlled")

    monkeypatch.setattr(
        corpus_preparation, "installed_revision", lambda _conn: "current"
    )
    assert (
        corpus_preparation.require_current_revision(MagicMock(), "controlled")
        == "current"
    )


def test_repeated_preparation_verifies_without_rebuilding_current_views(monkeypatch):
    entry = corpora.CorpusEntry(
        id="controlled",
        database="corpus_db",
        data_dir=None,
        config_dir=None,
        expected_projects=13,
        published=True,
        notes="fixture",
    )
    conn = MagicMock()
    engine = MagicMock()
    engine.begin.return_value.__enter__.return_value = conn
    monkeypatch.setattr(corpora, "resolve", lambda _corpus_id: entry)
    monkeypatch.setattr(corpora, "derived_view_revision", lambda: "current")
    monkeypatch.setattr(
        corpus_preparation, "_derived_view_names", lambda _source: ("v",)
    )
    monkeypatch.setattr(
        corpus_preparation.db_config, "get_engine", lambda *_args, **_kwargs: engine
    )
    monkeypatch.setattr(corpora, "validate_project_count", lambda _conn, _entry: 13)
    monkeypatch.setattr(
        corpus_preparation, "installed_revision", lambda _conn: "current"
    )
    monkeypatch.setattr(
        corpus_preparation, "_installed_view_names", lambda _conn, _names: {"v"}
    )
    apply_schema = MagicMock()
    verify_views = MagicMock()
    monkeypatch.setattr(corpus_preparation, "_apply_derived_schema", apply_schema)
    monkeypatch.setattr(corpus_preparation, "_verify_views", verify_views)
    monkeypatch.setattr(
        corpus_preparation, "_configure_report_role", lambda *_args: "report"
    )

    first = corpus_preparation.prepare("controlled")
    second = corpus_preparation.prepare("controlled")

    assert first == second
    apply_schema.assert_not_called()
    assert verify_views.call_count == 2
    assert engine.dispose.call_count == 2


def test_missing_preparation_installs_and_records_current_revision(monkeypatch):
    entry = corpora.CorpusEntry(
        id="controlled",
        database="corpus_db",
        data_dir=None,
        config_dir=None,
        expected_projects=13,
        published=True,
        notes="fixture",
    )
    conn = MagicMock()
    engine = MagicMock()
    engine.begin.return_value.__enter__.return_value = conn
    monkeypatch.setattr(corpora, "resolve", lambda _corpus_id: entry)
    monkeypatch.setattr(corpora, "derived_view_revision", lambda: "current")
    monkeypatch.setattr(
        corpus_preparation, "_derived_view_names", lambda _source: ("v",)
    )
    monkeypatch.setattr(
        corpus_preparation.db_config, "get_engine", lambda *_args, **_kwargs: engine
    )
    monkeypatch.setattr(corpora, "validate_project_count", lambda _conn, _entry: 13)
    monkeypatch.setattr(corpus_preparation, "installed_revision", lambda _conn: None)
    monkeypatch.setattr(
        corpus_preparation, "_installed_view_names", lambda _conn, _names: set()
    )
    apply_schema = MagicMock()
    record_revision = MagicMock()
    monkeypatch.setattr(corpus_preparation, "_apply_derived_schema", apply_schema)
    monkeypatch.setattr(corpus_preparation, "_record_revision", record_revision)
    monkeypatch.setattr(corpus_preparation, "_verify_views", MagicMock())
    monkeypatch.setattr(
        corpus_preparation, "_configure_report_role", lambda *_args: "report"
    )

    corpus_preparation.prepare("controlled")

    apply_schema.assert_called_once()
    record_revision.assert_called_once_with(conn, "current")
