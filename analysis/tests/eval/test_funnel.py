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
    # Applicability is the count through all five stages. The Stage-4 figure the
    # chapter reports beside it is the reduction band's input, which is larger
    # whenever reduction excludes anything.
    assert result.reduction.entering == stages[-1].entering
    assert result.reduction.entering > result.success_count


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


def test_funnel_survivors_match_independent_sql():
    result = _funnel_result()
    with _connect() as conn:
        variant = _funnel.resolve_variant(conn)
        counts = conn.execute(
            text(
                """
                WITH eligible AS (
                    SELECT p.id
                    FROM project p
                    WHERE p.use_test_generalization
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
                ),
                stage12 AS (
                    SELECT e.id
                    FROM eligible e
                    WHERE EXISTS (
                        SELECT 1 FROM test t
                        WHERE t.project_id = e.id AND t.is_included
                    )
                      AND EXISTS (
                        SELECT 1 FROM assertion a
                        WHERE a.project_id = e.id AND a.is_included
                    )
                ),
                stage3 AS (
                    SELECT s.id
                    FROM stage12 s
                    WHERE EXISTS (
                        SELECT 1 FROM assertion a
                        WHERE a.project_id = s.id
                          AND a.is_included
                          AND a.output_spec_class IS NOT NULL
                    )
                ),
                stage4 AS (
                    SELECT s.id
                    FROM stage3 s
                    WHERE EXISTS (
                        SELECT 1
                        FROM generalization g
                        JOIN generalization_lifecycle l
                          ON l.generalization_id = g.id
                        WHERE g.project_id = s.id
                          AND g.variant = :variant
                          AND l.generated_filter_passed
                    )
                ),
                stage5 AS (
                    SELECT s.id
                    FROM stage4 s
                    WHERE EXISTS (
                        SELECT 1
                        FROM generalization g
                        JOIN generalization_lifecycle l
                          ON l.generalization_id = g.id
                        WHERE g.project_id = s.id
                          AND g.variant = :variant
                          AND l.final_usable
                    )
                )
                SELECT
                    (SELECT count(*) FROM eligible),
                    (SELECT count(*) FROM stage12),
                    (SELECT count(*) FROM stage3),
                    (SELECT count(*) FROM stage4),
                    (SELECT count(*) FROM stage5)
                """
            ),
            {"variant": variant},
        ).one()
    assert tuple(stage.entering for stage in result.stages) == (
        counts[0],
        counts[1],
        counts[2],
        counts[3],
    )
    assert tuple(stage.passing for stage in result.stages) == (
        counts[1],
        counts[2],
        counts[3],
        counts[4],
    )
    assert result.reduction.entering == counts[3]


def test_funnel_stage3_and_stage5_ids_match_direct_oracles():
    result = _funnel_result()
    with _connect() as conn:
        variant = _funnel.resolve_variant(conn)
        stage3_ids = {
            row[0]
            for row in conn.execute(
                text(
                    """
                    SELECT DISTINCT a.project_id
                    FROM assertion a
                    JOIN project p ON p.id = a.project_id
                    WHERE p.use_test_generalization
                      AND a.is_included
                      AND a.output_spec_class IS NOT NULL
                      AND EXISTS (
                          SELECT 1
                          FROM test it
                          WHERE it.project_id = p.id AND it.is_included
                      )
                      AND NOT EXISTS (
                          SELECT 1
                          FROM task ft
                          WHERE ft.project_id = p.id
                            AND ft.test_id IS NULL
                            AND ft.assertion_id IS NULL
                            AND ft.generalization_id IS NULL
                            AND ft.status <> 'SUCCEEDED'
                            AND ft.stage IN (
                                'SETUP_PROJECT',
                                'ADD_DEPENDENCIES',
                                'BUILD_PROJECT_ORIGINAL'
                            )
                      )
                    """
                )
            )
        }
        stage5_ids = {
            row[0]
            for row in conn.execute(
                text(
                    """
                    SELECT DISTINCT g.project_id
                    FROM generalization g
                    JOIN generalization_lifecycle l
                      ON l.generalization_id = g.id
                    JOIN project p ON p.id = g.project_id
                    WHERE p.use_test_generalization
                      AND g.variant = :variant
                      AND l.final_usable
                      AND NOT EXISTS (
                          SELECT 1
                          FROM task ft
                          WHERE ft.project_id = p.id
                            AND ft.test_id IS NULL
                            AND ft.assertion_id IS NULL
                            AND ft.generalization_id IS NULL
                            AND ft.status <> 'SUCCEEDED'
                            AND ft.stage IN (
                                'SETUP_PROJECT',
                                'ADD_DEPENDENCIES',
                                'BUILD_PROJECT_ORIGINAL'
                            )
                      )
                    """
                ),
                {"variant": variant},
            )
        }
    assert result.survivor_project_ids[2] == frozenset(stage3_ids)
    assert result.survivor_project_ids[4] == frozenset(stage5_ids)


def test_funnel_stage4_matches_validated_generalization_projects():
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
                  AND l.generated_filter_passed
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
    assert result.reduction.entering == expected


def test_reduction_causes_are_tabulated_and_define_final_success():
    # The table documents all five stages, success is measured after reduction, and
    # the reduction attrition is quantified so the chapter can state what it costs.
    result = _funnel_result()
    rows = result.table.df
    assert any(rows["stage"].eq("5")), rows
    assert any(
        rows["cause"].str.contains("PIT|JaCoCo|timeout", case=False, regex=True)
    ), rows
    assert (rows["count"] > 0).all()

    reduction = result.reduction
    assert reduction.stage == "5"
    assert reduction.passing == result.success_count
    assert reduction.passing == len(result.survivor_project_ids[4])
    assert reduction.exclusions == reduction.entering - reduction.passing
    assert 0 < result.reduction_excluded_baseline_side <= reduction.exclusions
