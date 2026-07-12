import pandas as pd
import pytest
import sqlalchemy.exc
from sqlalchemy import text

from teralizer.eval.data import connect
from teralizer.eval.reports import _funnel
from teralizer.eval.reports.rq6_causes import DEFAULT_DB


# Keep the database-specific integration assertions below separate from the
# pure funnel arithmetic checks so failures identify the broken contract.
def _connect():
    return connect(DEFAULT_DB)


def _funnel_result():
    try:
        with _connect() as conn:
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
    assert [stage.stage for stage in stages] == ["1 + 2", "3", "4", "5"]
    assert stages[0].entering == result.eligible
    for prev, cur in zip(stages, stages[1:]):
        assert cur.entering == prev.passing
    for stage in stages:
        assert stage.passing == stage.entering - stage.exclusions
    assert stages[-1].passing == result.success_count
    assert (
        sum(stage.exclusions for stage in stages) + result.success_count
        == result.eligible
    )


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


def test_survivorship_band_overrides_upstream_taxonomy_stage():
    row = pd.Series(
        {
            "included_tests": 4,
            "included_assertions": 0,
            "generated_filter_passed": 0,
            "excluded_assertions": 4,
            "filter_rejected_assertions": 0,
            "failure_excluded_assertions": 4,
            "has_jacoco_original": False,
            "has_jacoco_initial": False,
            "has_jacoco_generalized": False,
            "has_pit_original": False,
            "has_pit_initial": False,
            "has_pit_generalized": False,
        }
    )
    failure = _funnel.ProjectFailure(
        project_id=1,
        internal_stage="ANALYZE_JPF",
        reason_code="NO_INPUT_SPEC",
        runtime=None,
        step=1,
    )
    cause = _funnel._cause_for_exclusion("1 + 2", row, (failure,), ())
    assert cause.stage == "1 + 2"
    assert "all assertions excluded" in cause.cause


def test_funnel_success_matches_final_usable_projects():
    result = _funnel_result()
    with _connect() as conn:
        variant = _funnel.resolve_variant(conn)
        expected = conn.execute(
            text(
                """
                SELECT count(DISTINCT g.project_id)
                FROM generalization g
                JOIN generalization_lifecycle l
                  ON l.generalization_id = g.id
                JOIN project p ON p.id = g.project_id
                WHERE g.variant = :variant
                  AND l.final_usable
                  AND p.use_test_generalization
                  AND NOT EXISTS (
                      SELECT 1
                      FROM task t
                      WHERE t.project_id = p.id
                        AND t.test_id IS NULL
                        AND t.assertion_id IS NULL
                        AND t.generalization_id IS NULL
                        AND t.status <> 'SUCCEEDED'
                        AND t.stage IN (
                            'SETUP_PROJECT',
                            'ADD_DEPENDENCIES',
                            'BUILD_PROJECT_ORIGINAL'
                        )
                  )
                """
            ),
            {"variant": variant},
        ).scalar_one()
    assert result.success_count == expected


def test_funnel_includes_reduction_failure_causes():
    result = _funnel_result()
    rows = result.table.df
    assert any(rows["stage"].eq("5")), rows
    assert any(
        rows["cause"].str.contains("PIT|JaCoCo|timeout", case=False, regex=True)
    ), rows
    assert (rows["count"] > 0).all()
