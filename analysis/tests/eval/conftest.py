from collections.abc import Callable

import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
from teralizer.eval.reports import _funnel
from teralizer.eval.reports.rq6_causes import DEFAULT_DB


@pytest.fixture(scope="session")
def build_report() -> Callable[[str], RQReport]:
    """Build one report per session, however many tests ask for it.

    The rq6 build costs 15 seconds, and both the smoke test and the rq6 tests
    need it. Each report keeps its own build, so a report that fails to build
    fails only the tests that read it.
    """
    built: dict[str, RQReport] = {}

    def build(rq: str) -> RQReport:
        if rq not in built:
            spec = get(rq)
            with connect(
                spec.default_db,
                validate_schema=(spec.schema == "old"),
                require=spec.requires,
            ) as conn:
                built[rq] = spec.build(conn)
        return built[rq]

    return build


@pytest.fixture(scope="session")
def rq6_report(build_report) -> RQReport:
    try:
        return build_report("rq6")
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
