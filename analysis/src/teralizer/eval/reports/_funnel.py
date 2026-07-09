"""RQ6 project-level inclusion and exclusion funnel."""

from __future__ import annotations

import re
from dataclasses import dataclass

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
)

VARIANT = "IMPROVED_100_TRIES"
INELIGIBLE_STAGES = frozenset(
    {"SETUP_PROJECT", "ADD_DEPENDENCIES", "BUILD_PROJECT_ORIGINAL"}
)
_RUNTIME_CEILINGS = {
    "EXECUTE_TESTS_ORIGINAL": 60.0,
    "EXECUTE_TESTS_INITIAL": 60.0,
    "COLLECT_PIT_DATA_ORIGINAL": 300.0,
    "COLLECT_JACOCO_DATA_INITIAL": 300.0,
    "COLLECT_PIT_DATA_INITIAL": 300.0,
    "COLLECT_JACOCO_DATA_GENERALIZED": 300.0,
    "COLLECT_PIT_DATA_GENERALIZED": 300.0,
}
_JACOCO_STAGES = frozenset(
    {
        "COLLECT_JACOCO_DATA_ORIGINAL",
        "COLLECT_JACOCO_DATA_INITIAL",
        "COLLECT_JACOCO_DATA_GENERALIZED",
    }
)
_PIT_STAGES = frozenset(
    {
        "COLLECT_PIT_DATA_ORIGINAL",
        "COLLECT_PIT_DATA_INITIAL",
        "COLLECT_PIT_DATA_GENERALIZED",
    }
)
_ASSERTION_FAILURE_STAGES = frozenset(
    {"ADD_JPF_INSTRUMENTATION", "EXECUTE_JPF", "ANALYZE_JPF"}
)

_FIRST_FAILURE_SQL = """
WITH first_fail AS (
  SELECT DISTINCT ON (t.project_id)
         t.id AS task_id, t.project_id, t.stage AS internal_stage, t.runtime, t.info, t.step
  FROM task t
  WHERE t.test_id IS NULL AND t.assertion_id IS NULL AND t.generalization_id IS NULL
    AND t.status <> 'SUCCEEDED'
  ORDER BY t.project_id, t.step
)
SELECT ff.*, td.reason_code
FROM first_fail ff
LEFT JOIN task_diagnostic td
  ON td.task_id = ff.task_id
 AND td.test_id IS NULL AND td.assertion_id IS NULL AND td.generalization_id IS NULL
"""

_PROJECT_SIGNALS_SQL = """
WITH included_tests AS (
    SELECT project_id, COUNT(*) AS included_tests
    FROM test
    WHERE is_included
    GROUP BY project_id
), included_assertions AS (
    SELECT project_id, COUNT(*) AS included_assertions
    FROM assertion
    WHERE is_included
    GROUP BY project_id
), included_generalizations AS (
    SELECT project_id, COUNT(*) AS included_generalizations
    FROM generalization
    WHERE variant = :variant AND is_included
    GROUP BY project_id
), actual_coverage AS (
    SELECT DISTINCT project_id
    FROM jacoco_coverage_report
    WHERE instruction_covered > 0 OR branch_covered > 0 OR line_covered > 0
), jacoco_artifacts AS (
    SELECT DISTINCT project_id
    FROM jacoco_coverage_report
), pit_artifacts AS (
    SELECT DISTINCT project_id
    FROM pit_mutation_report
), excluded_assertions AS (
    SELECT a.id, a.project_id
    FROM assertion a
    WHERE NOT a.is_included
), assertion_exclusions AS (
    SELECT
        ea.project_id,
        COUNT(*) AS excluded_assertions,
        COUNT(*) FILTER (
            WHERE EXISTS (
                SELECT 1
                FROM filter_result fr
                WHERE fr.assertion_id = ea.id AND fr.decision = 'REJECT'
            )
        ) AS filter_rejected_assertions,
        COUNT(*) FILTER (
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
)
SELECT
    p.id AS project_id,
    actual_coverage.project_id IS NOT NULL AS has_actual_coverage,
    COALESCE(included_tests.included_tests, 0) AS included_tests,
    COALESCE(included_assertions.included_assertions, 0) AS included_assertions,
    COALESCE(included_generalizations.included_generalizations, 0) AS included_generalizations,
    COALESCE(assertion_exclusions.excluded_assertions, 0) AS excluded_assertions,
    COALESCE(assertion_exclusions.filter_rejected_assertions, 0) AS filter_rejected_assertions,
    COALESCE(assertion_exclusions.failure_excluded_assertions, 0) AS failure_excluded_assertions,
    jacoco_artifacts.project_id IS NOT NULL AS has_jacoco_artifact,
    pit_artifacts.project_id IS NOT NULL AS has_pit_artifact
FROM project p
LEFT JOIN included_tests ON included_tests.project_id = p.id
LEFT JOIN included_assertions ON included_assertions.project_id = p.id
LEFT JOIN included_generalizations ON included_generalizations.project_id = p.id
LEFT JOIN actual_coverage ON actual_coverage.project_id = p.id
LEFT JOIN assertion_exclusions ON assertion_exclusions.project_id = p.id
LEFT JOIN jacoco_artifacts ON jacoco_artifacts.project_id = p.id
LEFT JOIN pit_artifacts ON pit_artifacts.project_id = p.id
"""

_VARIANT_SQL = """
SELECT COUNT(*) AS rows
FROM generalization
WHERE variant = :variant
"""

_DEPENDENCY_OR_SOURCES_PATTERNS = tuple(
    re.compile(pattern)
    for pattern in (
        r"artifacts could not be resolved",
        r"Could not find artifact",
        r"PluginVersionResolutionException",
        r"Could not resolve dependencies",
        r"Unresolveable build extension",
        r"Detected the following recursive expression cycle",
        r"must be a valid version",
        r"must specify an absolute path",
        r"Could not find goal 'build-classpath' in plugin",
        r"Error injecting:",
        r"No supported test framework identified",
        r"Test source path .+ does not exist\.",
        r"Main source path .+ does not exist\.",
        r"Malformed POM ",
        r"Plugin .+ could not be resolved",
        r"Failed to resolve classpath from pom",
    )
)
_BUILD_PROJECT_PATTERNS = tuple(
    re.compile(pattern)
    for pattern in (
        r"teralizer\.util\.ConsoleCommandException",
        r"Cannot setup project .+ compiled path '.+' does not exist\.",
    )
)


@dataclass(frozen=True)
class StageBand:
    stage: str
    entering: int
    exclusions: int
    passing: int


@dataclass(frozen=True)
class FunnelResult:
    eligible: int
    success_count: int
    stages: list[StageBand]
    table: Table
    uncoded_projects: list[int]
    eligibility_audit_unexpected: list[int]


def build_funnel(conn: Connection) -> FunnelResult:
    _require_variant(conn)

    first_failures = read_sql(conn, _FIRST_FAILURE_SQL)
    signals = read_sql(
        conn,
        _PROJECT_SIGNALS_SQL,
        {
            "variant": VARIANT,
            "assertion_failure_stages": list(_ASSERTION_FAILURE_STAGES),
        },
    )

    data = signals.merge(first_failures, on="project_id", how="left")
    data["has_first_failure"] = data["task_id"].notna()
    data["ineligible_by_stage"] = data["internal_stage"].isin(INELIGIBLE_STAGES)
    data["ineligible"] = data["ineligible_by_stage"] | (
        data["has_jacoco_artifact"] & ~data["has_actual_coverage"]
    )
    eligible_data = data[~data["ineligible"]].copy()

    eligible = int(len(eligible_data))
    success_count = int((~eligible_data["has_first_failure"]).sum())
    eligibility_audit_unexpected = _audit_unexpected_projects(data)

    causes: list[Cause] = []
    uncoded_projects: list[int] = []
    for _, row in eligible_data[eligible_data["has_first_failure"]].iterrows():
        attribution = _attribution(row)
        cause = classify(attribution)
        if cause == UNCODED:
            uncoded_projects.append(int(row["project_id"]))
            continue
        causes.append(cause)

    table_df = _cause_table_df(causes)
    stage_exclusions = _stage_exclusions(table_df)
    stages = _stage_bands(eligible, stage_exclusions)
    band_parts = [f"Eligible projects: {eligible}."]
    for band in stages:
        rate = band.passing / band.entering if band.entering else 0.0
        band_parts.append(
            f"Stage {band.stage}: {band.entering} entering, "
            f"{band.passing} included, {band.exclusions} excluded ({rate:.1%})."
        )
    overall = success_count / eligible if eligible else 0.0
    band_parts.append(
        f"Overall: {success_count} of {eligible} included ({overall:.1%})."
    )
    note = " ".join(band_parts)

    return FunnelResult(
        eligible=eligible,
        success_count=success_count,
        stages=stages,
        table=_build_table(table_df, note),
        uncoded_projects=uncoded_projects,
        eligibility_audit_unexpected=eligibility_audit_unexpected,
    )


def _require_variant(conn: Connection) -> None:
    rows = read_sql(conn, _VARIANT_SQL, {"variant": VARIANT}).iloc[0]["rows"]
    if int(rows) == 0:
        raise RuntimeError(f"database has no {VARIANT} generalizations")


def _audit_unexpected_projects(data: pd.DataFrame) -> list[int]:
    unexpected: list[int] = []
    for _, row in data[data["ineligible_by_stage"]].iterrows():
        if not _matches_ineligible_cause(str(row["internal_stage"]), row["info"]):
            unexpected.append(int(row["project_id"]))
    return sorted(unexpected)


def _matches_ineligible_cause(stage: str, info: object) -> bool:
    text = "" if pd.isna(info) else str(info)
    if stage in {"SETUP_PROJECT", "ADD_DEPENDENCIES"}:
        return any(pattern.search(text) for pattern in _DEPENDENCY_OR_SOURCES_PATTERNS)
    if stage == "BUILD_PROJECT_ORIGINAL":
        return any(pattern.search(text) for pattern in _BUILD_PROJECT_PATTERNS)
    return False


def _attribution(row: pd.Series) -> Attribution:
    internal_stage = str(row["internal_stage"])
    excluded_assertions = int(row["excluded_assertions"])
    filter_rejected_assertions = int(row["filter_rejected_assertions"])
    failure_excluded_assertions = int(row["failure_excluded_assertions"])
    assertion_exclusions_all_filtered = (
        excluded_assertions > 0
        and filter_rejected_assertions == excluded_assertions
        and failure_excluded_assertions == 0
    )
    return Attribution(
        internal_stage=internal_stage,
        reason_code=_nullable_string(row["reason_code"]),
        at_ceiling=_at_ceiling(internal_stage, row["runtime"]),
        included_tests=int(row["included_tests"]),
        included_assertions=int(row["included_assertions"]),
        included_generalizations=int(row["included_generalizations"]),
        assertion_exclusions_all_filtered=assertion_exclusions_all_filtered,
        artifact_present=_artifact_present(internal_stage, row),
    )


def _nullable_string(value: object) -> str | None:
    if pd.isna(value):
        return None
    return str(value)


def _at_ceiling(internal_stage: str, runtime: object) -> bool:
    if pd.isna(runtime):
        return False
    ceiling = _RUNTIME_CEILINGS.get(internal_stage)
    return ceiling is not None and float(runtime) >= ceiling - 0.5


def _artifact_present(internal_stage: str, row: pd.Series) -> bool:
    if internal_stage in _JACOCO_STAGES:
        return bool(row["has_jacoco_artifact"])
    if internal_stage in _PIT_STAGES:
        return bool(row["has_pit_artifact"])
    return True


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
        .size()
        .rename(columns={"size": "count"})
        .sort_values(
            by=["stage", "type", "cause"],
            key=lambda column: column.map(STAGE_ORDER).fillna(99)
            if column.name == "stage"
            else column,
        )
        .reset_index(drop=True)
    )


def _stage_exclusions(table_df: pd.DataFrame) -> dict[str, int]:
    return {
        str(stage): int(count)
        for stage, count in table_df.groupby("stage")["count"].sum().items()
    }


def _stage_bands(eligible: int, exclusions: dict[str, int]) -> list[StageBand]:
    entering = eligible
    bands: list[StageBand] = []
    for stage in sorted(STAGE_ORDER, key=STAGE_ORDER.get):
        excluded = exclusions.get(stage, 0)
        passing = entering - excluded
        bands.append(
            StageBand(
                stage=stage,
                entering=entering,
                exclusions=excluded,
                passing=passing,
            )
        )
        entering = passing
    return bands


def _build_table(df: pd.DataFrame, note: str) -> Table:
    return Table(
        key="tab:processing-failures",
        df=df,
        columns=[
            ColumnSpec("Stage", "stage"),
            ColumnSpec("Type", "type"),
            ColumnSpec("Cause", "cause"),
            ColumnSpec("Count", "count", fmt="int", align="r"),
        ],
        caption="Project-level processing failures by funnel stage and cause.",
        label="tab:processing-failures",
        group_by="stage",
        note=note,
        provenance=capture(build_funnel, query=_FIRST_FAILURE_SQL),
    )
