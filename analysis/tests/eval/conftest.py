import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
from teralizer.eval.reports import _funnel
from teralizer.eval.reports.rq6_causes import DEFAULT_DB


@pytest.fixture(scope="session")
def rq6_report() -> RQReport:
    spec = get("rq6")
    try:
        with connect(spec.default_db) as conn:
            return spec.build(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


@pytest.fixture(scope="session")
def funnel_result():
    try:
        with connect(DEFAULT_DB) as conn:
            return _funnel.build_funnel(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


@pytest.fixture(scope="session")
def rq6_conn():
    try:
        with connect(DEFAULT_DB) as conn:
            yield conn
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
