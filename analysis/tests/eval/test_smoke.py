"""Smoke: every registered report builds against its default database.

Vacuous while no reports are registered; each report plan adds live coverage
here as it registers itself. This is the report build check the retired
validate.py eval hook used to perform, now a normal pytest.
"""

from __future__ import annotations

from sqlalchemy.exc import OperationalError

from teralizer.eval import registry
from teralizer.eval.data import connect
from teralizer.eval.model import RQReport


def test_registered_reports_build():
    for rq in sorted(registry.REPORTS):
        spec = registry.get(rq)
        try:
            with connect(
                spec.default_db, validate_schema=(spec.schema == "old")
            ) as conn:
                report = spec.build(conn)
        except OperationalError:
            continue  # database unreachable in this environment; skip, do not fail
        assert isinstance(report, RQReport)
        assert report.rq == rq
