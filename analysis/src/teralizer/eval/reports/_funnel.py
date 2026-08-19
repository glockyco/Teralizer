"""RQ6 project-level inclusion and exclusion funnel."""

from __future__ import annotations

from dataclasses import dataclass
from typing import cast

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import ColumnSpec, Table
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

ELIGIBILITY_CTE = """
WITH eligible_projects AS (
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
            AND t.stage = ANY(:ineligible_stages)
      )
)
"""


def base_query_params(variant: str) -> dict[str, object]:
    return {
        "variant": variant,
        "ineligible_stages": list(INELIGIBLE_STAGES),
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
    ineligible AS (
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
    NOT EXISTS (SELECT 1 FROM ineligible i WHERE i.project_id = p.id) AS eligible,
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
    eligible: int
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
    eligible = len(eligible_ids)
    success_count = len(survivor_sets[-1])
    note = _funnel_note(eligible, stages, success_count)

    return FunnelResult(
        eligible=eligible,
        success_count=success_count,
        stages=stages,
        table=_build_table(
            table_df, note, *_stage_bands_text(stages, eligible, success_count)
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


def _stage_band_text(
    title: str, entering: int, passing: int, exclusions: int, widths: dict[str, int]
) -> str:
    """One band: a fixed-width title, then the stage's totals.

    Every figure is padded to the widest in its position so the bands read down
    the table as a column of totals rather than as ragged sentences.
    """

    def pad(value: str, key: str) -> str:
        missing = widths[key] - len(value)
        return f"\\phantom{{{'0' * missing}}}{value}" if missing > 0 else value

    rate = f"{passing / entering:.1%}" if entering else "0.0%"
    fields = [
        f"{pad(f'{entering:,}', 'entering')} projects",
        f"{pad(f'{passing:,}', 'passing')} inclusions",
        f"{pad(f'{exclusions:,}', 'exclusions')} exclusions",
        # Padded before escaping, because a width is counted in glyphs and `\%` is
        # one glyph written as two characters. A bare `%` would comment out the
        # rest of the row.
        f"{pad(rate, 'rate').replace('%', chr(92) + '%')} inclusion rate",
    ]
    return f"{title}:\t" + "\\enspace{}".join(fields)


def _stage_bands_text(
    stages: "list[StageBand]", eligible: int, success_count: int
) -> tuple[dict[str, str], str]:
    """A band per stage plus the closing overall band, aligned as one block."""
    entries = [
        (_STAGE_TITLES[b.stage], b.stage, b.entering, b.passing, b.exclusions)
        for b in stages
    ]
    entries.append(("Overall", "", eligible, success_count, eligible - success_count))
    widths = {
        "entering": max(len(f"{e:,}") for _, _, e, _, _ in entries),
        "passing": max(len(f"{p:,}") for _, _, _, p, _ in entries),
        "exclusions": max(len(f"{x:,}") for _, _, _, _, x in entries),
        "rate": max(len(f"{p / e:.1%}" if e else "0.0%") for _, _, e, p, _ in entries),
    }
    bands = {
        stage: _stage_band_text(title, entering, passing, exclusions, widths)
        for title, stage, entering, passing, exclusions in entries
        if stage
    }
    title, _, entering, passing, exclusions = entries[-1]
    overall = _stage_band_text(title, entering, passing, exclusions, widths)
    return bands, overall


def _build_table(
    df: pd.DataFrame,
    note: str,
    bands: dict[str, str] | None = None,
    overall_band: str | None = None,
) -> Table:
    display = df.copy()
    cause_macros = {
        "PIT execution error during mutation testing": (
            r"\ToolPit{} execution error during mutation testing"
        ),
        "PIT reports not found": r"\ToolPit{} reports not found",
        "failed to process PIT reports": r"failed to process \ToolPit{} reports",
    }
    timeout_macros = {
        "per original test suite": r"per \VariantOriginal{} test suite",
        "per initial test suite": r"per \VariantInitial{} test suite",
        "per generalized test suite": r"per \VariantImprovedC{} test suite",
    }

    def cause_display(cause: object) -> str:
        text = str(cause)
        if text in cause_macros:
            return cause_macros[text]
        for raw, macro in timeout_macros.items():
            if raw in text:
                return text.replace(raw, macro)
        return text

    display["cause_display"] = display["cause"].map(cause_display)
    # Numbering is data, because this is where row order is decided. A band is not
    # a row of the frame, so it consumes no number, and the CSV keeps both the
    # number and the stage.
    display["number"] = range(1, len(display) + 1)
    return Table(
        key="tab-processing-failures",
        df=display,
        columns=[
            ColumnSpec("\\#", "number", fmt="int", align="r"),
            ColumnSpec("Type", "type"),
            ColumnSpec(
                "Cause of Project-level Exclusion", "cause_display", csv_source="cause"
            ),
            ColumnSpec("Count", "count", fmt="int", align="r"),
        ],
        caption=(
            "Project-level exclusions by stage and cause for the "
            "\\VariantImprovedC{} generalization strategy in RepoReapers projects. "
            "Internal causes are due to configured resource limits or current "
            "limitations of \\ToolTeralizer{}. External causes are due to "
            "\\ToolTeralizer{}'s dependencies (i.e., JUnit, Spoon, "
            "\\ToolJPF{} / \\ToolSPF{}, \\ToolJacoco{}, and \\ToolPit{}). "
            "Mixed causes are influenced by both internal and external factors."
        ),
        label="tab:processing-failures",
        short_caption="RepoReapers exclusions by stage and cause",
        body_style="\\tabstyle",
        float_spec="tbp",
        full_width=True,
        group_by="stage",
        bands=bands,
        overall_band=overall_band,
        note=note,
        provenance=capture(build_funnel, query=_PROJECT_SIGNALS_SQL),
    )
