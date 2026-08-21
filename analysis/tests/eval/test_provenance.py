import json
import subprocess
from pathlib import Path

import pytest

import teralizer.eval.provenance as provenance
from teralizer.eval.inputs import FileInputSnapshot
from teralizer.eval.provenance import (
    DIRTY_PROVENANCE_ENV,
    Provenance,
    capture,
    git_commit,
    require_publishable_tree,
)

THIS_FILE = "analysis/tests/eval/test_provenance.py"
# analysis/tests/eval/test_provenance.py -> repo root is parents[3]
REPO_ROOT = Path(__file__).resolve().parents[3]


def sample_fn():
    return capture(sample_fn, query="SELECT 1")


def _git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=REPO_ROOT, capture_output=True, text=True, check=True
    ).stdout.strip()


def test_capture_records_function_location_and_query():
    p = sample_fn()
    assert p.qualname == "sample_fn"
    assert p.module.endswith("test_provenance")
    assert p.lineno > 0
    assert p.query == "SELECT 1"


def test_capture_uses_the_producing_file_not_head():
    """HEAD says where the checkout stands. Provenance must say when the code that
    produced the value last changed."""
    p = sample_fn()
    assert p.commit == _git("log", "-1", "--format=%H", "--", THIS_FILE)


def test_capture_records_the_real_source_path():
    """The path is the file the interpreter loaded, not one rebuilt from the
    module name -- this file is under analysis/tests, not analysis/src."""
    assert sample_fn().path == THIS_FILE


def test_provenance_of_a_function_outside_this_repository():
    p = capture(json.dumps)
    assert p.path == ""
    assert p.dirty is True


def test_release_archive_uses_embedded_source_identity(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    commit = "c" * 40
    (tmp_path / ".teralizer-source.json").write_text(
        json.dumps({"schema_version": 1, "source_commit": commit}), encoding="utf-8"
    )
    monkeypatch.setattr(provenance, "_REPO_ROOT", tmp_path)
    provenance._git_snapshot.cache_clear()
    provenance._file_snapshot.cache_clear()

    assert provenance.checkout_snapshot() == (commit, False)
    assert provenance._file_snapshot("analysis/src/example.py") == (commit, False)

    provenance._git_snapshot.cache_clear()
    provenance._file_snapshot.cache_clear()


def test_file_snapshot_is_memoised_per_path():
    provenance._file_snapshot.cache_clear()
    provenance._file_snapshot(THIS_FILE)
    provenance._file_snapshot(THIS_FILE)
    assert provenance._file_snapshot.cache_info().hits == 1


def test_never_committed_file_records_head_and_is_uncertain():
    commit, dirty = provenance._file_snapshot("analysis/no/such/module.py")
    assert commit == git_commit()
    assert dirty is True


def test_uncommitted_producing_file_is_marked_dirty(monkeypatch):
    monkeypatch.setattr(provenance, "_file_snapshot", lambda _: ("b" * 40, True))
    result = capture(sample_fn)
    assert result.commit == "b" * 40
    assert result.dirty is True


def test_git_commit_is_full_hex(monkeypatch):
    monkeypatch.setenv(DIRTY_PROVENANCE_ENV, "1")
    c = git_commit()
    assert len(c) == 40 and all(ch in "0123456789abcdef" for ch in c)


def test_dirty_tree_fails_without_opt_out(monkeypatch):
    """The publish guard stays tree-wide: publishing attributes across a
    repository boundary, where a reviewer cannot tell which file mattered."""
    monkeypatch.delenv(DIRTY_PROVENANCE_ENV, raising=False)
    monkeypatch.setattr(provenance, "_git_snapshot", lambda: ("a" * 40, True))
    with pytest.raises(RuntimeError, match=DIRTY_PROVENANCE_ENV):
        require_publishable_tree()


def test_dirty_tree_opt_out_permits_publishing(monkeypatch):
    monkeypatch.setenv(DIRTY_PROVENANCE_ENV, "1")
    monkeypatch.setattr(provenance, "_git_snapshot", lambda: ("a" * 40, True))
    require_publishable_tree()


def test_dirty_declared_input_fails_without_opt_out(monkeypatch):
    monkeypatch.delenv(DIRTY_PROVENANCE_ENV, raising=False)
    snapshot = FileInputSnapshot("facts", "facts.json", True, "a" * 64, "b" * 40, True)
    with pytest.raises(RuntimeError, match="dirty declared inputs: facts"):
        provenance.require_publishable_inputs((snapshot,))


def test_dirty_declared_input_opt_out_permits_publishing(monkeypatch):
    monkeypatch.setenv(DIRTY_PROVENANCE_ENV, "1")
    snapshot = FileInputSnapshot("facts", "facts.json", True, "a" * 64, "b" * 40, True)
    provenance.require_publishable_inputs((snapshot,))


def test_source_url_builds_permalink():
    p = Provenance(
        module="teralizer.eval.reports.rq6_causes_realworld",
        qualname="build_report",
        lineno=42,
        query=None,
        commit="abc1234",
        path="analysis/src/teralizer/eval/reports/rq6_causes_realworld.py",
    )
    url = p.source_url("https://github.com/glockyco/Teralizer")
    assert url == (
        "https://github.com/glockyco/Teralizer/blob/abc1234/"
        "analysis/src/teralizer/eval/reports/rq6_causes_realworld.py#L42"
    )
