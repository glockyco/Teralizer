import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.reports import _funnel


def _funnel_result():
    try:
        with connect("postgres_reporeapers") as conn:
            return _funnel.build_funnel(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_no_uncoded_attributions():
    result = _funnel_result()
    assert result.uncoded_projects == [], (
        f"unclassified projects: {result.uncoded_projects[:10]}"
    )


def test_eligibility_audit_only_ineligible_causes_at_setup_stages():
    result = _funnel_result()
    assert result.eligibility_audit_unexpected == [], (
        f"eligible-looking failures at fail-at-start stages: "
        f"{result.eligibility_audit_unexpected[:10]}"
    )


def test_funnel_arithmetic_is_consistent():
    result = _funnel_result()
    stages = result.stages
    assert stages[0].entering == result.eligible
    for prev, cur in zip(stages, stages[1:]):
        assert cur.entering == prev.entering - prev.exclusions
        assert prev.passing == prev.entering - prev.exclusions
    assert stages[-1].passing == result.success_count


def test_every_cause_row_has_a_known_type():
    result = _funnel_result()
    assert set(result.table.df["type"]) <= {"Internal", "External", "Mixed"}
    assert (result.table.df["count"] > 0).all()


def test_funnel_table_has_band_summary_note():
    result = _funnel_result()
    note = result.table.note
    assert note is not None and note.strip()
    assert str(result.eligible) in note
    for band in result.stages:
        assert band.stage in note
    assert "excluded" in note
    for band in result.stages:
        assert str(band.exclusions) in note
