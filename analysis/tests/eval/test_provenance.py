from teralizer.eval.provenance import Provenance, capture, git_commit


def sample_fn():
    return capture(sample_fn, query="SELECT 1")


def test_capture_records_function_location_and_query():
    p = sample_fn()
    assert p.qualname == "sample_fn"
    assert p.module.endswith("test_provenance")
    assert p.lineno > 0
    assert p.query == "SELECT 1"


def test_git_commit_is_hex_maybe_dirty():
    c = git_commit()
    core = c.removesuffix("-dirty")
    assert len(core) >= 7 and all(ch in "0123456789abcdef" for ch in core)


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
