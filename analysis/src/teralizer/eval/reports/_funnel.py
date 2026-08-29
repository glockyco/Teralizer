"""RQ6 project-level inclusion and exclusion funnel."""

from __future__ import annotations

from dataclasses import dataclass
from typing import cast

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import BandSummary, ColumnSpec, Table, ValueKind
from teralizer.eval.provenance import capture
from teralizer.eval.reports._taxonomy import (
    UNCODED,
    Attribution,
    CAPABILITY_CODES,
    Cause,
    FILTER_CLASS_PATTERN,
    GATE_CODES,
    QUARANTINE_CODES,
    QUARANTINE_PRODUCER,
    STAGE_ORDER,
    classify,
    paper_stage,
)

INELIGIBLE_STAGES = frozenset(
    {"SETUP_PROJECT", "ADD_DEPENDENCIES", "BUILD_PROJECT_ORIGINAL"}
)

# The frozen corpus does not record the number of tests that Maven executed. These
# projects reached the test-report collector, but their Maven logs show that the
# project disabled its tests or that Surefire discovered no tests. They have no
# baseline test that Teralizer can generalize, so they are corpus exclusions rather
# than pipeline failures.
_NO_EXECUTED_TEST_PROJECTS = frozenset(
    {
        "projects/github_com_FibreFoX_i-can-see-aliens-JSP",
        "projects/github_com_Groupe2_Groupe2",
        "projects/github_com_IISH_file-validation",
        "projects/github_com_KunkkaCoco_dsaij",
        "projects/github_com_PersistentSystemsLimitedSoftLayer_SoftLayerRestClient",
        "projects/github_com_acciente_oacc-core",
        "projects/github_com_bluebibi_springframe",
        "projects/github_com_fit2cloud_qingcloud-api-java-wrapper",
        "projects/github_com_git4sinu_proDesi",
        "projects/github_com_halafi_msg-system",
        "projects/github_com_happyfish100_fastdfs-client-java",
        "projects/github_com_injcristianrojas_swsec-intro",
        "projects/github_com_jpvetterli_time2lib",
        "projects/github_com_ltemal94_SpringMVCMovies",
        "projects/github_com_lucachaves_lattesHyperjaxb3",
        "projects/github_com_madwenoma_tadu-jedis",
        "projects/github_com_mikolai_HomeMultimediaStorage",
        "projects/github_com_mirage22_miko-spring-mongodb",
        "projects/github_com_mirage22_miko-spring-postgresql",
        "projects/github_com_mtedone_podam",
        "projects/github_com_perwendel_spark",
        "projects/github_com_santo74_vertx-arangodb",
        "projects/github_com_sbunciak_test-result-tracking-system",
        "projects/github_com_sebprunier_concours-devoxx-france-2013",
        "projects/github_com_sistar_woodle_backend",
        "projects/github_com_spstorey_hicksfamilyhistory",
        "projects/github_com_sscdotopen_aim3",
        "projects/github_com_stacksync_java-cloudfiles",
        "projects/github_com_svanimpe_reminders",
        "projects/github_com_unisgn_nova",
        "projects/github_com_yiminliu_ims-mvc",
    }
)

# MATERIALIZED: inlined, the planner re-checks eligibility per row of the table the
# consumer scans, which costs 1.2 billion join-filter comparisons over filter_result.
ELIGIBILITY_CTE = """
WITH eligible_projects AS MATERIALIZED (
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
            AND t.stage = ANY(:ineligible_stages)
      )
)
"""


def base_query_params(variant: str) -> dict[str, object]:
    return {
        "variant": variant,
        "ineligible_stages": list(INELIGIBLE_STAGES),
        "no_executed_test_projects": sorted(_NO_EXECUTED_TEST_PROJECTS),
    }


# The funnel reports all five stages and measures applicability after Stage 5, so the
# headline figure counts projects that complete the whole pipeline, including test suite
# reduction. The Stage-4 figure -- projects holding a validated generalized test before
# reduction -- is reported beside it, because reduction earns its place by removing
# generalized tests that do not improve fault detection (see the RQ3 and RQ4 results).
_PIPELINE_STAGES = ("1 + 2", "3", "4", "5")
_STAGE_TITLES = {
    "1 + 2": "Stage 1 + 2 - Project Analysis",
    "3": "Stage 3 - Spec. Extraction",
    "4": "Stage 4 - Gen. Test Creation",
    "5": "Stage 5 - Test Suite Reduction",
}
_REDUCTION_STAGE = "5"
_BASELINE_REDUCTION_STAGES = frozenset(
    {"COLLECT_PIT_DATA_INITIAL", "COLLECT_JACOCO_DATA_INITIAL"}
)
_ASSERTION_FAILURE_STAGES = frozenset(
    {"ADD_JPF_INSTRUMENTATION", "EXECUTE_JPF", "ANALYZE_JPF"}
)
# These stage budgets mirror src/main/resources/reference.conf. Keeping them in one
# report setting map avoids repeating policy values in classification and rendering.
_TIMEOUT_BUDGETS = {
    "EXECUTE_TESTS_ORIGINAL": 300.0,
    "EXECUTE_TESTS_INITIAL": 300.0,
    "EXECUTE_TESTS_GENERALIZED": 1800.0,
    "COLLECT_JACOCO_DATA_ORIGINAL": 300.0,
    "COLLECT_JACOCO_DATA_INITIAL": 300.0,
    "COLLECT_JACOCO_DATA_GENERALIZED": 300.0,
    "COLLECT_PIT_DATA_ORIGINAL": 3600.0,
    "COLLECT_PIT_DATA_INITIAL": 3600.0,
    "COLLECT_PIT_DATA_GENERALIZED": 3600.0,
}
_ARTIFACT_COLUMNS = {
    "COLLECT_JACOCO_DATA_ORIGINAL": "has_jacoco_original",
    "COLLECT_JACOCO_DATA_INITIAL": "has_jacoco_initial",
    "COLLECT_JACOCO_DATA_GENERALIZED": "has_jacoco_generalized",
    "COLLECT_PIT_DATA_ORIGINAL": "has_pit_original",
    "COLLECT_PIT_DATA_INITIAL": "has_pit_initial",
    "COLLECT_PIT_DATA_GENERALIZED": "has_pit_generalized",
}

_PROJECT_FAILURES_SQL = """
SELECT
    t.project_id,
    t.stage AS internal_stage,
    t.runtime,
    t.step,
    td.reason_code,
    td.detail_json ->> 'message' AS diagnostic_message
FROM task t
LEFT JOIN LATERAL (
    SELECT reason_code, detail_json
    FROM task_diagnostic
    WHERE task_id = t.id
    ORDER BY id
    LIMIT 1
) td ON TRUE
WHERE t.test_id IS NULL
  AND t.assertion_id IS NULL
  AND t.generalization_id IS NULL
  AND t.status <> 'SUCCEEDED'
ORDER BY t.project_id, t.step, t.id
"""

_PROJECT_SIGNALS_SQL = """
WITH
    included_tests AS (
        SELECT project_id, count(*) AS included_tests
        FROM test
        WHERE is_included
        GROUP BY project_id
    ),
    included_assertions AS (
        SELECT project_id, count(*) AS included_assertions
        FROM assertion
        WHERE is_included
        GROUP BY project_id
    ),
    stage12_test_survivors AS (
        SELECT t.project_id, count(*) AS stage12_surviving_tests
        FROM test t
        WHERE NOT EXISTS (
            SELECT 1
            FROM filter_result fr
            WHERE fr.test_id = t.id
              AND fr.decision = 'REJECT'
              AND fr.filter_name ~ :filter_class_pattern
        )
          AND (split_part(t.exclusion_info, ':', 1) IS NULL
               OR split_part(t.exclusion_info, ':', 1) <> ALL(:capability_codes))
        GROUP BY t.project_id
    ),
    stage12_assertion_survivors AS (
        SELECT a.project_id, count(*) AS stage12_surviving_assertions
        FROM assertion a
        WHERE NOT EXISTS (
            SELECT 1
            FROM filter_result fr
            WHERE fr.assertion_id = a.id
              AND fr.decision = 'REJECT'
              AND fr.filter_name ~ :filter_class_pattern
        )
        GROUP BY a.project_id
    ),
    spec_assertions AS (
        SELECT project_id, count(*) AS spec_surviving_assertions
        FROM assertion
        WHERE is_included
          -- Instrumentation fills spec paths before JPF; output_spec_class is
          -- written only after an extracted invocation produces specifications.
          AND output_spec_class IS NOT NULL
        GROUP BY project_id
    ),
    generated_filter_passed AS (
        SELECT g.project_id, count(*) AS generated_filter_passed
        FROM generalization g
        JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant
          AND l.generated_filter_passed
        GROUP BY g.project_id
    ),
    final_usable AS (
        SELECT g.project_id, count(*) AS final_usable
        FROM generalization g
        JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant
          AND l.final_usable
        GROUP BY g.project_id
    ),
    excluded_tests AS (
        SELECT t.id, t.project_id, split_part(t.exclusion_info, ':', 1) AS exclusion_code
        FROM test t
        WHERE NOT t.is_included
    ),
    test_exclusions AS (
        SELECT
            et.project_id,
            count(*) AS excluded_tests,
            bool_or(EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.test_id = et.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name ~ :filter_class_pattern
            )) AS has_test_filter_rejection,
            bool_or(
                EXISTS (
                    SELECT 1
                    FROM task failed
                    WHERE failed.test_id = et.id
                      AND failed.assertion_id IS NULL
                      AND failed.generalization_id IS NULL
                      AND failed.status <> 'SUCCEEDED'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM filter_result fr
                    WHERE fr.test_id = et.id
                      AND fr.decision = 'REJECT'
                      AND fr.filter_name ~ :filter_class_pattern
                )
                AND (et.exclusion_code IS NULL
                     OR et.exclusion_code <> ALL(:capability_codes))
            ) AS has_test_failure
        FROM excluded_tests et
        GROUP BY et.project_id
    ),
    excluded_assertions AS (
        SELECT a.id, a.project_id,
               split_part(a.exclusion_info, ':', 1) AS exclusion_code
        FROM assertion a
        WHERE NOT a.is_included
    ),
    assertion_evidence AS (
        SELECT
            ea.*,
            EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.assertion_id = ea.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name ~ :filter_class_pattern
            ) AS filter_rejected,
            EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.assertion_id = ea.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name = :quarantine_producer
            ) OR ea.exclusion_code = ANY(:quarantine_codes) AS build_quarantined,
            EXISTS (
                SELECT 1
                FROM task failed
                WHERE failed.assertion_id = ea.id
                  AND failed.generalization_id IS NULL
                  AND failed.status <> 'SUCCEEDED'
                  AND failed.stage = ANY(:assertion_failure_stages)
            ) OR ea.exclusion_code LIKE 'Excluded by%' AS task_failed
        FROM excluded_assertions ea
    ),
    assertion_exclusions AS (
        SELECT
            project_id,
            count(*) AS excluded_assertions,
            count(*) FILTER (WHERE filter_rejected) AS filter_rejected_assertions,
            count(*) FILTER (WHERE build_quarantined)
                AS build_quarantined_assertions,
            count(*) FILTER (
                WHERE task_failed
                  AND NOT filter_rejected
                  AND NOT build_quarantined
            ) AS task_exception_assertions
        FROM assertion_evidence
        GROUP BY project_id
    ),
    generalization_exclusions AS (
        SELECT
            g.project_id,
            count(*) AS generalization_attempts,
            bool_or(split_part(g.exclusion_info, ':', 1) = 'ORACLE_NOT_WIDENABLE')
                AS has_widening_refusal,
            bool_or(EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.generalization_id = g.id
                  AND fr.decision = 'REJECT'
                  AND fr.filter_name ~ :filter_class_pattern
            )) AS has_generalization_filter_rejection,
            bool_or(
                EXISTS (
                    SELECT 1
                    FROM filter_result fr
                    WHERE fr.generalization_id = g.id
                      AND fr.decision = 'REJECT'
                      AND fr.filter_name = :quarantine_producer
                )
                OR (
                    (
                        l.final_failure_stage IS NOT NULL
                        OR EXISTS (
                            SELECT 1
                            FROM task failed
                            WHERE failed.generalization_id = g.id
                              AND failed.status <> 'SUCCEEDED'
                        )
                    )
                    AND NOT EXISTS (
                        SELECT 1
                        FROM filter_result fr
                        WHERE fr.generalization_id = g.id
                          AND fr.decision = 'REJECT'
                          AND fr.filter_name ~ :filter_class_pattern
                    )
                    AND (split_part(g.exclusion_info, ':', 1) IS NULL
                         OR split_part(g.exclusion_info, ':', 1) <> ALL(:gate_codes))
                    AND (split_part(g.exclusion_info, ':', 1) IS NULL
                         OR split_part(g.exclusion_info, ':', 1) <> ALL(:quarantine_codes))
                )
            ) AS has_generalization_failure
        FROM generalization g
        LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
        WHERE g.variant = :variant
        GROUP BY g.project_id
    ),
    jacoco_artifacts AS (
        SELECT
            project_id,
            bool_or(stage = 'COLLECT_JACOCO_DATA_ORIGINAL') AS jacoco_original,
            bool_or(stage = 'COLLECT_JACOCO_DATA_INITIAL') AS jacoco_initial,
            bool_or(stage = 'COLLECT_JACOCO_DATA_GENERALIZED') AS jacoco_generalized
        FROM jacoco_coverage_report
        GROUP BY project_id
    ),
    pit_artifacts AS (
        SELECT
            project_id,
            bool_or(stage = 'COLLECT_PIT_DATA_ORIGINAL') AS pit_original,
            bool_or(stage = 'COLLECT_PIT_DATA_INITIAL') AS pit_initial,
            bool_or(stage = 'COLLECT_PIT_DATA_GENERALIZED') AS pit_generalized
        FROM pit_mutation_report
        GROUP BY project_id
    ),
    initial_gate_failures AS (
        SELECT DISTINCT project_id
        FROM task
        WHERE test_id IS NULL
          AND assertion_id IS NULL
          AND generalization_id IS NULL
          AND status <> 'SUCCEEDED'
          AND stage = ANY(:ineligible_stages)
    )
SELECT
    p.id AS project_id,
    NOT EXISTS (
        SELECT 1 FROM initial_gate_failures i WHERE i.project_id = p.id
    ) AND p.root_path != ALL(:no_executed_test_projects) AS eligible,
    EXISTS (
        SELECT 1 FROM initial_gate_failures i WHERE i.project_id = p.id
    ) AS failed_initial_gate,
    p.root_path = ANY(:no_executed_test_projects) AS no_executed_test,
    coalesce(it.included_tests, 0) AS included_tests,
    coalesce(ia.included_assertions, 0) AS included_assertions,
    coalesce(s12t.stage12_surviving_tests, 0) AS stage12_surviving_tests,
    coalesce(s12a.stage12_surviving_assertions, 0)
        AS stage12_surviving_assertions,
    coalesce(sa.spec_surviving_assertions, 0) AS spec_surviving_assertions,
    coalesce(gfp.generated_filter_passed, 0) AS generated_filter_passed,
    coalesce(fu.final_usable, 0) AS final_usable,
    coalesce(te.excluded_tests, 0) AS excluded_tests,
    coalesce(te.has_test_filter_rejection, false) AS has_test_filter_rejection,
    coalesce(te.has_test_failure, false) AS has_test_failure,
    coalesce(ae.excluded_assertions, 0) AS excluded_assertions,
    coalesce(ae.filter_rejected_assertions, 0) AS filter_rejected_assertions,
    coalesce(ae.build_quarantined_assertions, 0)
        AS build_quarantined_assertions,
    coalesce(ae.task_exception_assertions, 0) AS task_exception_assertions,
    coalesce(ge.generalization_attempts, 0) AS generalization_attempts,
    coalesce(ge.has_widening_refusal, false) AS has_widening_refusal,
    coalesce(ge.has_generalization_filter_rejection, false)
        AS has_generalization_filter_rejection,
    coalesce(ge.has_generalization_failure, false) AS has_generalization_failure,
    coalesce(ja.jacoco_original, false) AS has_jacoco_original,
    coalesce(ja.jacoco_initial, false) AS has_jacoco_initial,
    coalesce(ja.jacoco_generalized, false) AS has_jacoco_generalized,
    coalesce(pa.pit_original, false) AS has_pit_original,
    coalesce(pa.pit_initial, false) AS has_pit_initial,
    coalesce(pa.pit_generalized, false) AS has_pit_generalized
FROM project p
LEFT JOIN included_tests it ON it.project_id = p.id
LEFT JOIN included_assertions ia ON ia.project_id = p.id
LEFT JOIN stage12_test_survivors s12t ON s12t.project_id = p.id
LEFT JOIN stage12_assertion_survivors s12a ON s12a.project_id = p.id
LEFT JOIN spec_assertions sa ON sa.project_id = p.id
LEFT JOIN generated_filter_passed gfp ON gfp.project_id = p.id
LEFT JOIN final_usable fu ON fu.project_id = p.id
LEFT JOIN test_exclusions te ON te.project_id = p.id
LEFT JOIN assertion_exclusions ae ON ae.project_id = p.id
LEFT JOIN generalization_exclusions ge ON ge.project_id = p.id
LEFT JOIN jacoco_artifacts ja ON ja.project_id = p.id
LEFT JOIN pit_artifacts pa ON pa.project_id = p.id
WHERE p.use_test_generalization
"""

_LIFECYCLE_FAILURES_SQL = """
SELECT
    g.project_id,
    l.final_failure_stage AS internal_stage,
    l.final_failure_code AS reason_code,
    count(*) AS failures
FROM generalization g
JOIN generalization_lifecycle l ON l.generalization_id = g.id
WHERE g.variant = :variant
  AND l.generated_filter_passed
  AND NOT l.final_usable
GROUP BY g.project_id, l.final_failure_stage, l.final_failure_code
"""

_VARIANT_SQL = """
SELECT variant
FROM generalization
WHERE variant IS NOT NULL
GROUP BY variant
ORDER BY variant
"""


@dataclass(frozen=True)
class StageBand:
    stage: str
    entering: int
    exclusions: int
    passing: int


def _funnel_note(eligible: int, stages: "list[StageBand]", success_count: int) -> str:
    """Describe each stage of the funnel in one sentence.

    The rate is the share of entering projects that the stage includes, so it is written next to
    the included count.
    """
    parts = [f"Eligible projects: {eligible}."]
    for band in stages:
        rate = band.passing / band.entering if band.entering else 0.0
        parts.append(
            f"Stage {band.stage}: {band.entering} entering, "
            f"{band.passing} included ({rate:.1%}), {band.exclusions} excluded."
        )
    overall = success_count / eligible if eligible else 0.0
    parts.append(f"Overall: {success_count} of {eligible} included ({overall:.1%}).")
    return " ".join(parts)


@dataclass(frozen=True)
class ProjectFailure:
    project_id: int
    internal_stage: str
    reason_code: str | None
    runtime: float | None
    step: int


@dataclass(frozen=True)
class FunnelResult:
    selected: int
    eligible: int
    initial_gate_excluded: int
    no_executed_test_excluded: int
    # Projects through all five stages, which the funnel table reports as its overall
    # row and the chapter cites as its applicability figure. The count holding a
    # validated generalized test before reduction is the reduction band's input.
    success_count: int
    stages: list[StageBand]
    table: Table
    uncoded_projects: list[int]
    eligibility_audit_unexpected: list[int]
    survivor_project_ids: tuple[frozenset[int], ...]
    reduction: StageBand
    reduction_excluded_baseline_side: int


def resolve_variant(conn: Connection) -> str:
    """Resolve the sole variant in the full-pipeline RQ6 database."""
    variants = read_sql(conn, _VARIANT_SQL)["variant"].tolist()
    if len(variants) != 1:
        raise RuntimeError(
            f"RQ6 requires exactly one generalization variant; found {variants!r}"
        )
    return str(variants[0])


def build_funnel(conn: Connection, variant: str | None = None) -> FunnelResult:
    selected_variant = variant or resolve_variant(conn)
    signals = read_sql(
        conn,
        _PROJECT_SIGNALS_SQL,
        {
            "variant": selected_variant,
            "assertion_failure_stages": list(_ASSERTION_FAILURE_STAGES),
            "capability_codes": sorted(CAPABILITY_CODES),
            "filter_class_pattern": FILTER_CLASS_PATTERN,
            "gate_codes": sorted(GATE_CODES),
            "quarantine_codes": sorted(QUARANTINE_CODES),
            "quarantine_producer": QUARANTINE_PRODUCER,
            "ineligible_stages": list(INELIGIBLE_STAGES),
            "no_executed_test_projects": sorted(_NO_EXECUTED_TEST_PROJECTS),
        },
    )
    failures = _fetch_project_failures(conn)
    lifecycle_failures = read_sql(
        conn,
        _LIFECYCLE_FAILURES_SQL,
        {"variant": selected_variant},
    )

    eligible_data = cast(
        pd.DataFrame, signals.loc[signals["eligible"].astype(bool), :].copy()
    )
    project_rows = {int(row["project_id"]): row for _, row in eligible_data.iterrows()}
    eligible_ids = set(project_rows)
    failures_by_project = _group_project_failures(failures)
    lifecycle_by_project = _group_lifecycle_failures(lifecycle_failures)

    survivor_sets = _survivor_sets(eligible_data)
    eligibility_audit_unexpected = _audit_eligibility(eligible_ids, failures_by_project)
    causes: list[Cause] = []
    uncoded_projects: list[int] = []
    reduction_excluded: set[int] = set()
    for index, stage in enumerate(_PIPELINE_STAGES):
        excluded = survivor_sets[index] - survivor_sets[index + 1]
        if stage == _REDUCTION_STAGE:
            reduction_excluded = excluded
        for project_id in sorted(excluded):
            cause = _reader_facing_cause(
                _cause_for_exclusion(
                    stage,
                    project_rows[project_id],
                    failures_by_project.get(project_id, ()),
                    lifecycle_by_project.get(project_id, ()),
                )
            )
            if cause == UNCODED:
                uncoded_projects.append(project_id)
            else:
                causes.append(cause)

    table_df = _cause_table_df(causes)
    stages = _stage_bands(survivor_sets)
    reduction = next(band for band in stages if band.stage == _REDUCTION_STAGE)
    selected = len(signals)
    failed_initial_gate = signals["failed_initial_gate"].astype(bool)
    no_executed_test = signals["no_executed_test"].astype(bool)
    initial_gate_excluded = int(failed_initial_gate.sum())
    no_executed_test_excluded = int((no_executed_test & ~failed_initial_gate).sum())
    eligible = len(eligible_ids)
    success_count = len(survivor_sets[-1])
    note = _funnel_note(eligible, stages, success_count)

    return FunnelResult(
        selected=selected,
        eligible=eligible,
        initial_gate_excluded=initial_gate_excluded,
        no_executed_test_excluded=no_executed_test_excluded,
        success_count=success_count,
        stages=stages,
        table=_build_table(
            table_df, note, *_stage_band_summaries(stages, eligible, success_count)
        ),
        uncoded_projects=uncoded_projects,
        eligibility_audit_unexpected=eligibility_audit_unexpected,
        survivor_project_ids=tuple(frozenset(ids) for ids in survivor_sets),
        reduction=reduction,
        reduction_excluded_baseline_side=_count_baseline_side(
            reduction_excluded, failures_by_project
        ),
    )


def _count_baseline_side(
    reduction_excluded: set[int],
    failures_by_project: dict[int, tuple[ProjectFailure, ...]],
) -> int:
    """Reduction exclusions whose earliest reduction failure measures the original suite."""
    count = 0
    for project_id in reduction_excluded:
        failure = next(
            (
                failure
                for failure in failures_by_project.get(project_id, ())
                if paper_stage(failure.internal_stage) == _REDUCTION_STAGE
            ),
            None,
        )
        if failure is not None and failure.internal_stage in _BASELINE_REDUCTION_STAGES:
            count += 1
    return count


def _audit_eligibility(
    eligible_ids: set[int],
    failures_by_project: dict[int, tuple[ProjectFailure, ...]],
) -> list[int]:
    unexpected = {
        project_id
        for project_id in eligible_ids
        if any(
            failure.internal_stage in INELIGIBLE_STAGES
            for failure in failures_by_project.get(project_id, ())
        )
    }
    return sorted(unexpected)


def _normalize_task_reason(reason_code: object, diagnostic_message: object) -> object:
    if (
        reason_code == "LISTENER_BUG"
        and isinstance(diagnostic_message, str)
        and diagnostic_message.startswith(
            'SQL [insert into "public"."pit_coverage_report"'
        )
    ):
        return "PIT_REPORT_PERSISTENCE_FAILURE"
    return reason_code


def _fetch_project_failures(conn: Connection) -> pd.DataFrame:
    failures = read_sql(conn, _PROJECT_FAILURES_SQL)
    normalized_reasons = failures.apply(
        lambda row: _normalize_task_reason(
            row["reason_code"], row["diagnostic_message"]
        ),
        axis=1,
    )
    return failures.assign(reason_code=normalized_reasons).drop(
        columns=["diagnostic_message"]
    )


def _group_project_failures(
    failures: pd.DataFrame,
) -> dict[int, tuple[ProjectFailure, ...]]:
    grouped: dict[int, list[ProjectFailure]] = {}
    for _, row in failures.iterrows():
        project_id = int(row["project_id"])
        grouped.setdefault(project_id, []).append(
            ProjectFailure(
                project_id=project_id,
                internal_stage=str(row["internal_stage"]),
                reason_code=_nullable_string(row["reason_code"]),
                runtime=_nullable_float(row["runtime"]),
                step=int(row["step"]),
            )
        )
    return {project_id: tuple(rows) for project_id, rows in grouped.items()}


def _group_lifecycle_failures(
    failures: pd.DataFrame,
) -> dict[int, tuple[ProjectFailure, ...]]:
    grouped: dict[int, list[ProjectFailure]] = {}
    for _, row in failures.iterrows():
        project_id = int(row["project_id"])
        grouped.setdefault(project_id, []).append(
            ProjectFailure(
                project_id=project_id,
                internal_stage=str(row["internal_stage"]),
                reason_code=_nullable_string(row["reason_code"]),
                runtime=None,
                step=0,
            )
        )
    return {project_id: tuple(rows) for project_id, rows in grouped.items()}


def _survivor_sets(signals: pd.DataFrame) -> list[set[int]]:
    project_ids = signals["project_id"].astype(int)
    survivors = [
        set(project_ids),
        set(project_ids[signals["stage12_surviving_assertions"] > 0]),
        set(project_ids[signals["generalization_attempts"] > 0]),
        set(project_ids[signals["generated_filter_passed"] > 0]),
        set(project_ids[signals["final_usable"] > 0]),
    ]
    for stage, earlier, later in zip(_PIPELINE_STAGES, survivors, survivors[1:]):
        bypassed = sorted(later - earlier)
        if bypassed:
            raise RuntimeError(
                f"Stage {stage} transition evidence bypasses its input: {bypassed[:10]}"
            )
    return survivors


def _reader_facing_cause(cause: Cause) -> Cause:
    if cause == UNCODED:
        return cause

    stage12_groups = {
        "all assertions excluded due to filter rejections": (
            "all tests or assertions excluded due to filter rejections"
        ),
        "all tests excluded due to filter rejections": (
            "all tests or assertions excluded due to filter rejections"
        ),
        "timeout exceeded (300 seconds per original test suite)": (
            "JUnit execution of the original test suite exceeds 300 seconds"
        ),
        "JUnit execution error during test execution": (
            "JUnit test execution or Spoon test analysis fails"
        ),
        "Spoon execution error during test analysis": (
            "JUnit test execution or Spoon test analysis fails"
        ),
        "no test records collected": "no test or assertion records collected",
        "no assertion records collected": "no test or assertion records collected",
        "unsupported JUnit report layout": "JUnit report collection fails",
        "JUnit report directory not found": "JUnit report collection fails",
    }
    if cause.stage == "1 + 2":
        if cause.cause in stage12_groups:
            return Cause(cause.stage, stage12_groups[cause.cause])
        raise RuntimeError(f"Unmapped Stage 1 + 2 project cause: {cause.cause}")

    if cause.stage == "3":
        if cause.cause in {
            "no generalization attempts recorded",
            "no specifications extracted from retained assertions",
        }:
            return Cause(
                cause.stage,
                "no generalization attempt recorded despite retained assertions",
            )
        processing_causes = {
            "instrumented project compilation failed",
            "JUnit execution error during initial test execution",
            "no specifications extracted due to earlier filter rejections and task exceptions",
            "no specifications extracted due to earlier filter rejections and build quarantines",
            "no specifications extracted due to earlier filter rejections, build quarantines, and task exceptions",
        }
        if cause.cause in processing_causes:
            return Cause(
                cause.stage, "processing failures prevent specification extraction"
            )
        raise RuntimeError(f"Unmapped Stage 3 project cause: {cause.cause}")

    if cause.stage == "4":
        widening_causes = {
            "all generalizations excluded due to widening refusals",
            "all generalizations excluded due to widening refusals and filter rejections",
            "all generalizations excluded due to widening refusals and task exceptions",
        }
        if cause.cause in widening_causes:
            return Cause(
                cause.stage,
                "widening refusals contribute to exclusion of all generalization attempts",
            )
        stage4_groups = {
            "all generalizations excluded due to filter rejections": (
                "filter rejections alone exclude all generalization attempts"
            ),
            "all generalizations excluded due to task exceptions": (
                "processing failures alone exclude all generalization attempts"
            ),
        }
        if cause.cause in stage4_groups:
            return Cause(cause.stage, stage4_groups[cause.cause])
        raise RuntimeError(f"Unmapped Stage 4 project cause: {cause.cause}")

    stage5_groups = {
        "timeout exceeded (3600 seconds during PIT mutation testing for the initial test suite)": (
            "PIT mutation testing of the initial test suite exceeds 3,600 seconds"
        ),
        "failed to persist PIT reports for the initial test suite": (
            "required PIT reports or JaCoCo outputs unavailable for the initial test suite"
        ),
        "JaCoCo outputs not found for the initial test suite": (
            "required PIT reports or JaCoCo outputs unavailable for the initial test suite"
        ),
    }
    if cause.stage == "5":
        if cause.cause in stage5_groups:
            return Cause(cause.stage, stage5_groups[cause.cause])
        passthrough_causes = {
            "generalized test suite has failing tests before mutation",
            "initial test suite has failing tests before mutation",
        }
        if cause.cause in passthrough_causes:
            return cause
        raise RuntimeError(f"Unmapped Stage 5 project cause: {cause.cause}")

    raise RuntimeError(f"Unmapped project cause stage: {cause.stage}")


def _cause_for_exclusion(
    stage: str,
    project_row: pd.Series,
    failures: tuple[ProjectFailure, ...],
    lifecycle_failures: tuple[ProjectFailure, ...],
) -> Cause:
    failure = next(
        (
            failure
            for failure in failures
            if paper_stage(failure.internal_stage) == stage
        ),
        None,
    )
    if stage == "5" and failure is None:
        failure = next(
            (
                failure
                for failure in lifecycle_failures
                if paper_stage(failure.internal_stage) == stage
            ),
            None,
        )
    if failure is not None:
        cause = classify(_attribution(project_row, failure))
        if cause != UNCODED and cause.stage == stage:
            return cause
    return _fallback_cause(stage, project_row)


def _fallback_cause(stage: str, project_row: pd.Series) -> Cause:
    if stage == "1 + 2":
        test_records = int(project_row["included_tests"]) + int(
            project_row["excluded_tests"]
        )
        assertion_records = int(project_row["included_assertions"]) + int(
            project_row["excluded_assertions"]
        )
        if test_records == 0:
            return Cause("1 + 2", "no test records collected")
        if int(project_row["stage12_surviving_tests"]) == 0:
            if bool(project_row["has_test_filter_rejection"]):
                return Cause("1 + 2", "all tests excluded due to filter rejections")
            return UNCODED
        if assertion_records == 0:
            return Cause("1 + 2", "no assertion records collected")
        if (
            int(project_row["stage12_surviving_assertions"]) == 0
            and int(project_row["filter_rejected_assertions"]) == assertion_records
        ):
            return Cause("1 + 2", "all assertions excluded due to filter rejections")
        return UNCODED
    if stage == "3":
        if int(project_row["spec_surviving_assertions"]) > 0:
            return Cause("3", "no generalization attempts recorded")
        if int(project_row["included_assertions"]) > 0:
            return Cause("3", "no specifications extracted from retained assertions")
        mechanisms = []
        if int(project_row["filter_rejected_assertions"]) > 0:
            mechanisms.append("earlier filter rejections")
        if int(project_row["build_quarantined_assertions"]) > 0:
            mechanisms.append("build quarantines")
        if int(project_row["task_exception_assertions"]) > 0:
            mechanisms.append("task exceptions")
        if not mechanisms:
            return UNCODED
        return Cause(
            "3",
            f"no specifications extracted due to {_join_causes(mechanisms)}",
        )
    if stage == "4":
        return _complete_generalization_loss_cause(project_row)
    # Do not guess a reduction cause. An unclassified reduction exclusion is a defect,
    # so surface it as UNCODED instead of assigning a plausible description.
    return UNCODED


def _complete_generalization_loss_cause(project_row: pd.Series) -> Cause:
    if int(project_row["generalization_attempts"]) == 0:
        return UNCODED
    mechanisms = []
    if bool(project_row["has_widening_refusal"]):
        mechanisms.append("widening refusals")
    if bool(project_row["has_generalization_filter_rejection"]):
        mechanisms.append("filter rejections")
    if bool(project_row["has_generalization_failure"]):
        mechanisms.append("task exceptions")
    if not mechanisms:
        return UNCODED
    return Cause(
        "4",
        f"all generalizations excluded due to {_join_causes(mechanisms)}",
    )


def _join_causes(causes: list[str]) -> str:
    if len(causes) == 1:
        return causes[0]
    if len(causes) == 2:
        return f"{causes[0]} and {causes[1]}"
    return f"{', '.join(causes[:-1])}, and {causes[-1]}"


def _attribution(project_row: pd.Series, failure: ProjectFailure) -> Attribution:
    return Attribution(
        internal_stage=failure.internal_stage,
        reason_code=failure.reason_code,
        at_ceiling=failure.reason_code in {"EXECUTION_TIMEOUT", "SUITE_TIMEOUT"},
        included_tests=int(project_row["included_tests"]),
        included_assertions=int(project_row["included_assertions"]),
        included_generalizations=int(project_row["generated_filter_passed"]),
        artifact_present=_artifact_present(failure.internal_stage, project_row),
        timeout_seconds=(
            _TIMEOUT_BUDGETS.get(failure.internal_stage)
            if failure.reason_code in {"EXECUTION_TIMEOUT", "SUITE_TIMEOUT"}
            else None
        ),
    )


def _nullable_string(value: object) -> str | None:
    if pd.isna(value):
        return None
    return str(value)


def _nullable_float(value: object) -> float | None:
    if pd.isna(value):
        return None
    return float(value)


def _artifact_present(internal_stage: str, project_row: pd.Series) -> bool:
    column = _ARTIFACT_COLUMNS.get(internal_stage)
    if column is None:
        return True
    return bool(project_row[column])


def _cause_table_df(causes: list[Cause]) -> pd.DataFrame:
    if not causes:
        return pd.DataFrame(columns=["stage", "cause", "count"])
    df = pd.DataFrame(
        [{"stage": cause.stage, "cause": cause.cause} for cause in causes]
    )
    return (
        df.groupby(["stage", "cause"], as_index=False)
        .agg(count=("cause", "size"))
        .sort_values(
            by=["stage", "count", "cause"],
            ascending=[True, False, True],
            key=lambda column: column.map(STAGE_ORDER).fillna(99)
            if column.name == "stage"
            else column,
        )
        .reset_index(drop=True)
    )


def _stage_bands(survivor_sets: list[set[int]]) -> list[StageBand]:
    bands: list[StageBand] = []
    for index, stage in enumerate(_PIPELINE_STAGES):
        entering = len(survivor_sets[index])
        passing = len(survivor_sets[index + 1])
        bands.append(
            StageBand(
                stage=stage,
                entering=entering,
                exclusions=entering - passing,
                passing=passing,
            )
        )
    return bands


def _stage_band_summaries(
    stages: "list[StageBand]", eligible: int, success_count: int
) -> tuple[dict[str, BandSummary], BandSummary]:
    """Return typed stage summaries and the closing overall summary."""
    bands = {
        band.stage: BandSummary(
            _STAGE_TITLES[band.stage],
            band.entering,
            band.passing,
            band.exclusions,
        )
        for band in stages
    }
    overall = BandSummary(
        "Overall",
        eligible,
        success_count,
        eligible - success_count,
    )
    return bands, overall


def _build_table(
    df: pd.DataFrame,
    note: str,
    bands: dict[str, BandSummary] | None = None,
    overall_band: BandSummary | None = None,
) -> Table:
    display = df.copy()
    cause_templates = {
        "PIT execution error during mutation testing": (
            r"{entity.tool.pit} execution error during mutation testing"
        ),
        "PIT reports not found": r"{entity.tool.pit} reports not found",
        "failed to process PIT reports": r"failed to process {entity.tool.pit} reports",
    }
    suite_templates = {
        "original test suite": r"{entity.variant.original} test suite",
        "initial test suite": r"{entity.variant.initial} test suite",
        "generalized test suite": r"{entity.variant.improved_c} test suite",
    }

    def cause_text(cause: object) -> str:
        text = str(cause)
        if text in cause_templates:
            return cause_templates[text]
        for raw, macro in suite_templates.items():
            text = text.replace(raw, macro)
        return text

    display.loc[:, "row_key"] = display.apply(
        lambda row: f"{row['stage']}:{row['cause']}", axis=1
    )
    display.loc[:, "cause"] = display["cause"].map(cause_text)
    return Table(
        key="tab-processing-failures",
        df=display,
        columns=[
            ColumnSpec("Cause of Project-level Exclusion", "cause"),
            ColumnSpec("Count", "count", kind=ValueKind.COUNT, align="r"),
        ],
        caption=(
            "Project-level exclusions by stage and cause for the "
            "{entity.variant.improved_c} generalization strategy in RepoReapers projects."
        ),
        label="tab:processing-failures",
        short_caption="RepoReapers exclusions by stage and cause",
        body_style="\\tabstyle",
        float_spec="tbp",
        full_width=True,
        group_by="stage",
        row_key="row_key",
        ordinal_header="#",
        bands=bands,
        overall_band=overall_band,
        note=note,
        provenance=capture(build_funnel, query=_PROJECT_SIGNALS_SQL),
    )
