import pandas as pd
import pytest
from sqlalchemy import text

from teralizer.eval.render.csv import render_table
from teralizer.eval.reports import _funnel


# Keep the database-specific integration assertions below separate from the
# pure funnel arithmetic checks so failures identify the broken contract.
def test_processing_table_exports_plain_causes_without_ordinals(tmp_path):
    table = _funnel._build_table(
        pd.DataFrame(
            {
                "stage": ["5"],
                "cause": ["PIT execution error during mutation testing"],
                "count": [2],
            }
        ),
        "note",
    )
    assert table.df.iloc[0]["cause"] == (
        "{entity.tool.pit} execution error during mutation testing"
    )
    assert table.row_key == "row_key"
    assert table.ordinal_header == "#"
    path = render_table(table, tmp_path)
    assert path.read_text(encoding="utf-8").splitlines() == [
        "row_key,cause,count",
        "5:PIT execution error during mutation testing,"
        "PIT execution error during mutation testing,2",
    ]


def test_no_uncoded_attributions(funnel_result):
    result = funnel_result
    assert result.uncoded_projects == [], (
        f"unclassified projects: {result.uncoded_projects[:10]}"
    )


def test_eligibility_audit_only_ineligible_causes_at_setup_stages(funnel_result):
    result = funnel_result
    assert result.eligibility_audit_unexpected == [], (
        f"eligible-looking failures at fail-at-start stages: "
        f"{result.eligibility_audit_unexpected[:10]}"
    )


def test_funnel_arithmetic_is_consistent(funnel_result):
    result = funnel_result
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


def test_stage_bands_use_boundary_evidence(funnel_result):
    assert [
        (stage.stage, stage.entering, stage.passing, stage.exclusions)
        for stage in funnel_result.stages
    ] == [
        ("1 + 2", 584, 293, 291),
        ("3", 293, 179, 114),
        ("4", 179, 98, 81),
        ("5", 98, 85, 13),
    ]


def test_every_cause_row_has_stage_and_description(funnel_result):
    result = funnel_result
    assert list(result.table.df.columns) == ["stage", "cause", "count", "row_key"]
    assert result.table.df["stage"].notna().all()
    assert result.table.df["cause"].str.len().gt(0).all()
    assert (result.table.df["count"] > 0).all()


def test_funnel_table_has_band_summary_note(funnel_result):
    result = funnel_result
    note = result.table.note
    assert note is not None and note.strip()
    assert str(result.eligible) in note
    for band in result.stages:
        assert band.stage in note
    assert "excluded" in note
    for band in result.stages:
        assert str(band.exclusions) in note


def test_survivor_sets_use_historical_transitions_not_final_status():
    signals = pd.DataFrame(
        {
            "project_id": [1, 2, 3],
            "stage12_surviving_assertions": [1, 1, 1],
            "generalization_attempts": [0, 1, 1],
            "generated_filter_passed": [0, 0, 1],
            "final_usable": [0, 0, 1],
        }
    )
    assert _funnel._survivor_sets(signals) == [
        {1, 2, 3},
        {1, 2, 3},
        {2, 3},
        {3},
        {3},
    ]


def test_survivor_sets_reject_bypassed_stage():
    signals = pd.DataFrame(
        {
            "project_id": [1],
            "stage12_surviving_assertions": [0],
            "generalization_attempts": [1],
            "generated_filter_passed": [0],
            "final_usable": [0],
        }
    )
    with pytest.raises(RuntimeError, match="bypasses its input"):
        _funnel._survivor_sets(signals)


@pytest.mark.parametrize(
    ("build_quarantines", "task_exceptions", "expected"),
    (
        (0, 1, "earlier filter rejections and task exceptions"),
        (1, 0, "earlier filter rejections and build quarantines"),
        (
            1,
            1,
            "earlier filter rejections, build quarantines, and task exceptions",
        ),
    ),
)
def test_stage3_complete_loss_preserves_mechanism_set(
    build_quarantines, task_exceptions, expected
):
    row = pd.Series(
        {
            "spec_surviving_assertions": 0,
            "included_assertions": 0,
            "filter_rejected_assertions": 1,
            "build_quarantined_assertions": build_quarantines,
            "task_exception_assertions": task_exceptions,
        }
    )
    cause = _funnel._fallback_cause("3", row)
    assert cause.stage == "3"
    assert expected in cause.cause


def test_funnel_survivors_match_independent_sql(funnel_result, rq6_conn):
    result = funnel_result
    with rq6_conn.begin_nested():
        conn = rq6_conn
        variant = _funnel.resolve_variant(conn)
        counts = conn.execute(
            text(
                """
                WITH eligible AS (
                    SELECT p.id
                    FROM project p
                    WHERE p.use_test_generalization
                      AND p.root_path != ALL(:no_executed_test_projects)
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
                        SELECT 1
                        FROM assertion a
                        WHERE a.project_id = e.id
                          AND NOT EXISTS (
                              SELECT 1
                              FROM filter_result fr
                              WHERE fr.assertion_id = a.id
                                AND fr.decision = 'REJECT'
                                AND fr.filter_name ~ :filter_class_pattern
                          )
                    )
                ),
                stage3 AS (
                    SELECT s.id
                    FROM stage12 s
                    WHERE EXISTS (
                        SELECT 1
                        FROM generalization g
                        WHERE g.project_id = s.id
                          AND g.variant = :variant
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
            {
                "variant": variant,
                "filter_class_pattern": _funnel.FILTER_CLASS_PATTERN,
                "no_executed_test_projects": sorted(_funnel._NO_EXECUTED_TEST_PROJECTS),
            },
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


def test_funnel_stage3_and_stage5_ids_match_direct_oracles(funnel_result, rq6_conn):
    result = funnel_result
    with rq6_conn.begin_nested():
        conn = rq6_conn
        variant = _funnel.resolve_variant(conn)
        stage3_ids = {
            row[0]
            for row in conn.execute(
                text(
                    """
                    SELECT DISTINCT g.project_id
                    FROM generalization g
                    JOIN project p ON p.id = g.project_id
                    WHERE p.use_test_generalization
                      AND g.variant = :variant
                      AND EXISTS (
                          SELECT 1
                          FROM assertion a
                          WHERE a.project_id = p.id
                            AND NOT EXISTS (
                                SELECT 1
                                FROM filter_result fr
                                WHERE fr.assertion_id = a.id
                                  AND fr.decision = 'REJECT'
                                  AND fr.filter_name ~ :filter_class_pattern
                            )
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
                ),
                {
                    "variant": variant,
                    "filter_class_pattern": _funnel.FILTER_CLASS_PATTERN,
                },
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


def test_funnel_stage4_matches_validated_generalization_projects(
    funnel_result, rq6_conn
):
    result = funnel_result
    with rq6_conn.begin_nested():
        conn = rq6_conn
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


def test_reduction_causes_are_tabulated_and_define_final_success(funnel_result):
    # The table documents all five stages, success is measured after reduction, and
    # the reduction attrition is quantified so the chapter can state what it costs.
    result = funnel_result
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
