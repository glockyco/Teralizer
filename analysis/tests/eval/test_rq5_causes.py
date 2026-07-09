import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
import teralizer.eval.reports.rq5_causes  # noqa: F401  (registers "rq5")


def _report() -> RQReport:
    spec = get("rq5")
    try:
        with connect(
            spec.default_db, validate_schema=True, require=spec.requires
        ) as conn:
            return spec.build(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_rq5_has_breakdown_and_filtering_tables():
    report = _report()
    assert report.rq == "rq5"
    assert report.db == "postgres_dev"
    labels = {t.label for t in report.tables()}
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)


def test_rq5_breakdown_levels_and_nonnegative_counts():
    report = _report()
    breakdown = next(t for t in report.tables() if "breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    for col in ("total", "included", "filtering", "failures"):
        assert (breakdown.df[col] >= 0).all()
    reconstructed = breakdown.df[["included", "filtering", "failures"]].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()
