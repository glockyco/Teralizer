"""Tests for declared report inputs and their run-scoped resolution."""

from __future__ import annotations

import hashlib
from contextlib import contextmanager
from dataclasses import FrozenInstanceError
from pathlib import Path

import pytest

from teralizer import corpora
from teralizer.eval import inputs
from teralizer.eval.data import Required


def _entry(corpus_id: str) -> corpora.CorpusEntry:
    return corpora.CorpusEntry(
        id=corpus_id,
        database=f"database_{corpus_id}",
        data_dir=None,
        config_dir=None,
        expected_projects=1,
        notes="fixture",
    )


def _content_snapshot(role: str, relative: str, root: Path) -> inputs.FileInputSnapshot:
    path = root / relative
    if not path.is_file():
        return inputs.FileInputSnapshot(role, relative, False, None, None, False)
    return inputs.FileInputSnapshot(
        role,
        relative,
        True,
        hashlib.sha256(path.read_bytes()).hexdigest(),
        "fixture-commit",
        False,
    )


def test_declarations_are_closed_immutable_and_role_unique():
    declaration = inputs.CorpusInputSpec(
        "controlled",
        "controlled",
        (Required("project", "table", ("id",)),),
    )

    with pytest.raises(FrozenInstanceError):
        declaration.role = "other"  # type: ignore[misc]
    with pytest.raises(TypeError, match="requires must be a tuple"):
        inputs.CorpusInputSpec("controlled", "controlled", [])  # type: ignore[arg-type]
    with pytest.raises(TypeError, match="declarations must be a tuple"):
        inputs.validate_declarations([declaration])  # type: ignore[arg-type]
    with pytest.raises(ValueError, match="duplicate report input role 'controlled'"):
        inputs.validate_declarations((declaration, declaration))
    with pytest.raises(ValueError, match="path must be repository-relative"):
        inputs.FileInputSpec("facts", "../outside.json")


def test_resolver_opens_two_corpus_roles_and_closes_both(monkeypatch):
    events: list[str] = []
    connections = {"controlled": object(), "real-world": object()}

    @contextmanager
    def open_corpus(corpus_id, _registry=None):
        events.append(f"open:{corpus_id}")
        try:
            yield _entry(corpus_id), connections[corpus_id]
        finally:
            events.append(f"close:{corpus_id}")

    validated: list[tuple[object, tuple[Required, ...]]] = []
    monkeypatch.setattr(inputs.corpora, "open_corpus", open_corpus)
    monkeypatch.setattr(
        inputs,
        "validate_required",
        lambda conn, requires: validated.append((conn, requires)),
    )
    declarations = (
        inputs.CorpusInputSpec("controlled", "controlled"),
        inputs.CorpusInputSpec("real-world", "real-world"),
    )

    with inputs.resolve_inputs("test", declarations) as context:
        assert context.corpus("controlled") is connections["controlled"]
        assert context.corpus("real-world") is connections["real-world"]
        assert events == ["open:controlled", "open:real-world"]

    assert events == [
        "open:controlled",
        "open:real-world",
        "close:real-world",
        "close:controlled",
    ]
    assert validated == [
        (connections["controlled"], ()),
        (connections["real-world"], ()),
    ]


def test_resolver_checks_declared_corpus_definition(monkeypatch, tmp_path: Path):
    entry = corpora.CorpusEntry(
        id="real-world",
        database="database_real-world",
        data_dir="data/run",
        config_dir="project-configs/run",
        expected_projects=1,
        notes="fixture",
    )
    connection = object()

    @contextmanager
    def open_corpus(_corpus_id, _registry=None):
        yield entry, connection

    checked: list[tuple[object, Path, Path]] = []
    monkeypatch.setattr(inputs.corpora, "open_corpus", open_corpus)
    monkeypatch.setattr(inputs, "validate_required", lambda *_args: None)
    monkeypatch.setattr(
        inputs,
        "require_complete_corpus",
        lambda conn, *, data_dir, config_dir: checked.append(
            (conn, data_dir, config_dir)
        ),
    )

    with inputs.resolve_inputs(
        "rq6",
        (inputs.CorpusInputSpec("real-world", "real-world"),),
        repo_root=tmp_path,
    ):
        pass

    assert checked == [
        (
            connection,
            tmp_path / "data/run",
            tmp_path / "project-configs/run",
        )
    ]


def test_resolver_closes_prior_corpora_when_a_later_id_is_invalid(monkeypatch):
    events: list[str] = []

    @contextmanager
    def open_corpus(corpus_id, _registry=None):
        if corpus_id == "missing":
            raise KeyError("unknown corpus")
        events.append(f"open:{corpus_id}")
        try:
            yield _entry(corpus_id), object()
        finally:
            events.append(f"close:{corpus_id}")

    monkeypatch.setattr(inputs.corpora, "open_corpus", open_corpus)
    monkeypatch.setattr(inputs, "validate_required", lambda *_args: None)
    declarations = (
        inputs.CorpusInputSpec("first", "controlled"),
        inputs.CorpusInputSpec("second", "missing"),
    )

    with pytest.raises(KeyError, match="unknown corpus"):
        with inputs.resolve_inputs("test", declarations):
            pytest.fail("resolution must fail before construction")

    assert events == ["open:controlled", "close:controlled"]


def test_required_missing_file_fails_and_optional_absence_is_recorded(
    monkeypatch, tmp_path: Path
):
    monkeypatch.setattr(inputs, "_snapshot_file", _content_snapshot)
    required = (inputs.FileInputSpec("required", "data/required.json"),)
    optional = (inputs.FileInputSpec("optional", "data/optional.json", required=False),)

    with pytest.raises(
        FileNotFoundError, match="report 'test' required input 'required'"
    ):
        with inputs.resolve_inputs("test", required, repo_root=tmp_path):
            pytest.fail("missing required input must fail before construction")

    with inputs.resolve_inputs("test", optional, repo_root=tmp_path) as context:
        assert context.file("optional") is None
        assert context.snapshots == (
            inputs.FileInputSnapshot(
                "optional", "data/optional.json", False, None, None, False
            ),
        )


def test_resolver_rejects_a_file_changed_during_construction(
    monkeypatch, tmp_path: Path
):
    path = tmp_path / "data/facts.json"
    path.parent.mkdir()
    path.write_text("before", encoding="utf-8")
    monkeypatch.setattr(inputs, "_snapshot_file", _content_snapshot)
    declarations = (inputs.FileInputSpec("facts", "data/facts.json"),)

    with pytest.raises(
        RuntimeError, match="report 'test' input changed during construction: facts"
    ):
        with inputs.resolve_inputs("test", declarations, repo_root=tmp_path):
            path.write_text("after", encoding="utf-8")


def test_file_snapshot_records_content_commit_and_dirty_state(
    monkeypatch, tmp_path: Path
):
    path = tmp_path / "facts.json"
    path.write_text("evidence", encoding="utf-8")

    def git(_root: Path, args: list[str]) -> str:
        return "abc123" if args[0] == "log" else " M facts.json"

    monkeypatch.setattr(inputs, "_git", git)

    assert inputs._snapshot_file("facts", "facts.json", tmp_path) == (
        inputs.FileInputSnapshot(
            role="facts",
            path="facts.json",
            present=True,
            sha256=hashlib.sha256(b"evidence").hexdigest(),
            commit="abc123",
            dirty=True,
        )
    )


def test_untracked_file_has_no_fabricated_commit(monkeypatch, tmp_path: Path):
    path = tmp_path / "facts.json"
    path.write_text("evidence", encoding="utf-8")
    monkeypatch.setattr(inputs, "_git", lambda _root, _args: "")

    snapshot = inputs._snapshot_file("facts", "facts.json", tmp_path)

    assert snapshot.commit is None
    assert snapshot.dirty is True


def test_context_rejects_a_role_with_the_wrong_input_kind(monkeypatch, tmp_path: Path):
    path = tmp_path / "facts.json"
    path.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(inputs, "_snapshot_file", _content_snapshot)

    with inputs.resolve_inputs(
        "test", (inputs.FileInputSpec("facts", "facts.json"),), repo_root=tmp_path
    ) as context:
        with pytest.raises(
            TypeError, match="report 'test' input role 'facts' is not a corpus"
        ):
            context.corpus("facts")
        with pytest.raises(KeyError, match="report 'test' has no input role 'missing'"):
            context.file("missing")
