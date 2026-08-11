import pytest

import teralizer.eval.provenance as provenance
from teralizer.eval.provenance import (
    DIRTY_PROVENANCE_ENV,
    Provenance,
    capture,
    git_commit,
    require_publishable_tree,
)


def sample_fn():
    return capture(sample_fn, query="SELECT 1")


def test_capture_records_function_location_and_query(monkeypatch):
    monkeypatch.setattr(provenance, "_git_snapshot", lambda: ("a" * 40, True))
    p = sample_fn()
    assert p.qualname == "sample_fn"
    assert p.module.endswith("test_provenance")
    assert p.lineno > 0
    assert p.query == "SELECT 1"


def test_git_commit_is_full_hex(monkeypatch):
    monkeypatch.setenv(DIRTY_PROVENANCE_ENV, "1")
    c = git_commit()
    assert len(c) == 40 and all(ch in "0123456789abcdef" for ch in c)


def test_dirty_tree_fails_without_opt_out(monkeypatch):
    monkeypatch.delenv(DIRTY_PROVENANCE_ENV, raising=False)
    monkeypatch.setattr(provenance, "_git_snapshot", lambda: ("a" * 40, True))
    with pytest.raises(RuntimeError, match=DIRTY_PROVENANCE_ENV):
        require_publishable_tree()


def test_dirty_tree_opt_out_records_flag(monkeypatch):
    monkeypatch.setenv(DIRTY_PROVENANCE_ENV, "1")
    monkeypatch.setattr(provenance, "_git_snapshot", lambda: ("b" * 40, True))
    result = capture(sample_fn)
    assert result.commit == "b" * 40
    assert result.dirty is True


def test_source_url_builds_permalink():
    p = Provenance(
        module="teralizer.eval.reports.rq6_causes_realworld",
        qualname="build_report",
        lineno=42,
        query=None,
        commit="abc1234",
    )
    url = p.source_url("https://github.com/glockyco/Teralizer")
    assert url == (
        "https://github.com/glockyco/Teralizer/blob/abc1234/"
        "analysis/src/teralizer/eval/reports/rq6_causes_realworld.py#L42"
    )
