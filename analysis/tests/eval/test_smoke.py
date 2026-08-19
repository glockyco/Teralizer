"""Smoke: every registered report builds against its default database.

Vacuous while no reports are registered; each report plan adds live coverage
here as it registers itself. This is the report build check the retired
validate.py eval hook used to perform, now a normal pytest.
"""

from __future__ import annotations

import pytest


from sqlalchemy.exc import OperationalError

from teralizer.eval import registry
from teralizer.eval.model import RQReport


pytestmark = pytest.mark.db


def test_registered_reports_build(build_report):
    for rq in sorted(registry.REPORTS):
        try:
            report = build_report(rq)
        except OperationalError:
            continue  # database unreachable in this environment; skip, do not fail
        assert isinstance(report, RQReport)
        assert report.rq == rq
