from collections.abc import Callable

import pytest
import sqlalchemy.exc

from teralizer import corpora
from teralizer.eval.inputs import resolve_inputs
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
from teralizer.eval.reports import _funnel


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
            with resolve_inputs(rq, spec.inputs) as context:
                built[rq] = spec.build(context)
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
        with corpora.open_corpus("real-world") as (_, conn):
            return _funnel.build_funnel(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


@pytest.fixture(scope="session")
def rq6_conn():
    try:
        with corpora.open_corpus("real-world") as (_, conn):
            yield conn
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
