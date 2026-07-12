import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
import teralizer.eval.reports.rq6_causes  # noqa: F401  (registers "rq6")


def _report() -> RQReport:
    spec = get("rq6")
    try:
        with connect(spec.default_db) as conn:
            return spec.build(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_rq6_has_funnel_and_shared_tables():
    report = _report()
    assert report.rq == "rq6"
    assert report.db == get("rq6").default_db
    labels = {t.label for t in report.tables()}
    assert any("processing-failures" in lbl for lbl in labels)
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)


def test_rq6_funnel_causes_are_typed():
    report = _report()
    funnel = next(t for t in report.tables() if "processing-failures" in t.label)
    assert set(funnel.df["type"]) <= {"Internal", "External", "Mixed"}


def test_rq6_breakdown_conservation():
    report = _report()
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    reconstructed = breakdown.df[["included", "filtering", "failures"]].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()


def test_rq6_overall_inclusion_metric_is_a_fraction():
    report = _report()
    pct = report.metric("realworld.overall_inclusion_pct")
    assert pct.fmt == "pct1"
    assert 0.0 <= float(pct.value) <= 1.0
    eligible = report.metric("realworld.eligible_projects")
    assert int(eligible.value) > 0
