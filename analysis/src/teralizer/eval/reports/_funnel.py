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
    Cause,
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
    td.reason_code
FROM task t
LEFT JOIN LATERAL (
    SELECT reason_code
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
    excluded_assertions AS (
        SELECT a.id, a.project_id
        FROM assertion a
        WHERE NOT a.is_included
    ),
    assertion_exclusions AS (
        SELECT
            ea.project_id,
            count(*) AS excluded_assertions,
            count(*) FILTER (
                WHERE EXISTS (
                    SELECT 1
                    FROM filter_result fr
                    WHERE fr.assertion_id = ea.id
                      AND fr.decision = 'REJECT'
                )
            ) AS filter_rejected_assertions,
            count(*) FILTER (
                WHERE EXISTS (
                    SELECT 1
                    FROM task t
                    WHERE t.assertion_id = ea.id
                      AND t.status <> 'SUCCEEDED'
                      AND t.stage = ANY(:assertion_failure_stages)
                )
            ) AS failure_excluded_assertions
        FROM excluded_assertions ea
        GROUP BY ea.project_id
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
    coalesce(sa.spec_surviving_assertions, 0) AS spec_surviving_assertions,
    coalesce(gfp.generated_filter_passed, 0) AS generated_filter_passed,
    coalesce(fu.final_usable, 0) AS final_usable,
    coalesce(ae.excluded_assertions, 0) AS excluded_assertions,
    coalesce(ae.filter_rejected_assertions, 0) AS filter_rejected_assertions,
    coalesce(ae.failure_excluded_assertions, 0) AS failure_excluded_assertions,
    coalesce(ja.jacoco_original, false) AS has_jacoco_original,
    coalesce(ja.jacoco_initial, false) AS has_jacoco_initial,
    coalesce(ja.jacoco_generalized, false) AS has_jacoco_generalized,
    coalesce(pa.pit_original, false) AS has_pit_original,
    coalesce(pa.pit_initial, false) AS has_pit_initial,
    coalesce(pa.pit_generalized, false) AS has_pit_generalized
FROM project p
LEFT JOIN included_tests it ON it.project_id = p.id
LEFT JOIN included_assertions ia ON ia.project_id = p.id
LEFT JOIN spec_assertions sa ON sa.project_id = p.id
LEFT JOIN generated_filter_passed gfp ON gfp.project_id = p.id
LEFT JOIN final_usable fu ON fu.project_id = p.id
LEFT JOIN assertion_exclusions ae ON ae.project_id = p.id
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
            cause = _cause_for_exclusion(
                stage,
                project_rows[project_id],
                failures_by_project.get(project_id, ()),
                lifecycle_by_project.get(project_id, ()),
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


def _fetch_project_failures(conn: Connection) -> pd.DataFrame:
    return read_sql(conn, _PROJECT_FAILURES_SQL)


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
    stage12 = set(
        project_ids[
            (signals["included_tests"] > 0) & (signals["included_assertions"] > 0)
        ]
    )
    stage3 = stage12 & set(project_ids[signals["spec_surviving_assertions"] > 0])
    stage4 = stage3 & set(project_ids[signals["generated_filter_passed"] > 0])
    stage5 = stage4 & set(project_ids[signals["final_usable"] > 0])
    return [set(project_ids), stage12, stage3, stage4, stage5]


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
        if int(project_row["included_tests"]) == 0:
            return Cause(
                "1 + 2",
                "all tests excluded due to filter rejections and failures",
                "Mixed",
            )
        if _assertions_all_filtered(project_row):
            return Cause(
                "1 + 2", "all assertions excluded due to filter rejections", "Mixed"
            )
        return Cause(
            "1 + 2",
            "all assertions excluded due to filter rejections and failures",
            "Mixed",
        )
    if stage == "3":
        return Cause(
            "3",
            "all assertions excluded due to earlier filter rejections and new failures",
            "Mixed",
        )
    if stage == "4":
        return Cause(
            "4",
            "all generalizations excluded due to filter rejections and failures",
            "Internal",
        )
    # No guess for reduction. A reduction exclusion the taxonomy cannot type is a defect to
    # investigate, so it surfaces as UNCODED rather than being labelled plausibly.
    return UNCODED


def _assertions_all_filtered(project_row: pd.Series) -> bool:
    excluded = int(project_row["excluded_assertions"])
    rejected = int(project_row["filter_rejected_assertions"])
    failures = int(project_row["failure_excluded_assertions"])
    return excluded > 0 and rejected == excluded and failures == 0


def _attribution(project_row: pd.Series, failure: ProjectFailure) -> Attribution:
    return Attribution(
        internal_stage=failure.internal_stage,
        reason_code=failure.reason_code,
        at_ceiling=failure.reason_code in {"EXECUTION_TIMEOUT", "SUITE_TIMEOUT"},
        included_tests=int(project_row["included_tests"]),
        included_assertions=int(project_row["included_assertions"]),
        included_generalizations=int(project_row["generated_filter_passed"]),
        assertion_exclusions_all_filtered=_assertions_all_filtered(project_row),
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
        return pd.DataFrame(columns=["stage", "type", "cause", "count"])
    df = pd.DataFrame(
        [
            {"stage": cause.stage, "type": cause.type, "cause": cause.cause}
            for cause in causes
        ]
    )
    return (
        df.groupby(["stage", "type", "cause"], as_index=False)
        .agg(count=("cause", "size"))
        .sort_values(
            by=["stage", "type", "cause"],
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
    timeout_templates = {
        "per original test suite": r"per {entity.variant.original} test suite",
        "per initial test suite": r"per {entity.variant.initial} test suite",
        "per generalized test suite": r"per {entity.variant.improved_c} test suite",
    }

    def cause_text(cause: object) -> str:
        text = str(cause)
        if text in cause_templates:
            return cause_templates[text]
        for raw, macro in timeout_templates.items():
            if raw in text:
                return text.replace(raw, macro)
        return text

    display.loc[:, "row_key"] = display.apply(
        lambda row: f"{row['stage']}:{row['cause']}", axis=1
    )
    display.loc[:, "cause"] = display["cause"].map(cause_text)
    return Table(
        key="tab-processing-failures",
        df=display,
        columns=[
            ColumnSpec("Type", "type"),
            ColumnSpec("Cause of Project-level Exclusion", "cause"),
            ColumnSpec("Count", "count", kind=ValueKind.COUNT, align="r"),
        ],
        caption=(
            "Project-level exclusions by stage and cause for the "
            "{entity.variant.improved_c} generalization strategy in RepoReapers projects. "
            "Internal causes are due to configured resource limits or current "
            "limitations of {entity.tool.teralizer}. External causes are due to "
            "{entity.tool.teralizer}'s dependencies (i.e., JUnit, Spoon, "
            "{entity.tool.jpf} / {entity.tool.spf}, {entity.tool.jacoco}, and {entity.tool.pit}). "
            "Mixed causes are influenced by both internal and external factors."
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
