"""RQ0: published JARVIS comparison and census PVC context."""

from __future__ import annotations

from pathlib import Path
from typing import Any, cast

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.model import ColumnSpec, Metric, Prose, RQReport, Section, Table
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.jarvis_scoreboard import (
    JARVIS_TABLE2,
    SWEEP_VARIANTS,
    compare_to_jarvis,
    get_census_by_mut,
    get_census_project_pvc,
    get_mutation_scores,
    get_scoreboard,
    summarize_variants,
)
from teralizer.report_basis import open_report_connection


SCOREBOARD_DB = "postgres_jarvis_scoreboard"
CENSUS_DB = "postgres_jarvis_census"
TABLE2_VARIANT = "IMPROVED_100_TRIES"
CENSUS_VARIANT = "IMPROVED_100_TRIES"
CENSUS_COMPLETION_MARKER = Path("data/detached/census-gen.complete")

TABLE1_PROJECTS = (
    "commons-math-3.5-census",
    "commons-lang-3.5-census",
    "commons-cli-1.3.1-census",
    "commons-codec-1.10-census",
    "commons-collections-4.1-census",
    "commons-configuration-2.1-census",
    "commons-csv-1.4-census",
    "commons-email-1.4-census",
    "commons-io-2.5-census",
    "commons-jexl-3.0-census",
    "commons-pool-2.4.2-census",
    "commons-text-1.0-census",
)

# Only these two Table-1 projects have successful PVC values in the published
# Table-2 subset. The remaining values are explicitly zero successful reported
# cases, not a count of attempted or failed JARVIS cases.
JARVIS_PROJECT_PBT_PVC = {
    "commons-lang-3.5-census": 104,
    "commons-math-3.5-census": 1604,
}
JARVIS_PROJECT_MUTS = {
    "commons-lang-3.5-census": 2,
    "commons-math-3.5-census": 7,
}

_REQUIRED_STAGES = (
    "EXECUTE_TESTS_ORIGINAL",
    "COLLECT_JUNIT_REPORTS_ORIGINAL",
    "GENERALIZE_TESTS",
    "EXECUTE_TESTS_GENERALIZED",
    "COLLECT_JUNIT_REPORTS_GENERALIZED",
)
_SUCCESS_STATUSES = {"SUCCEEDED", "SUCCESS", "COMPLETED"}

# Table-2's ten scenario rows collapse to nine MUTs: the three PolynomialFunction
# rows share one value(double) MUT, while the two Precision overloads are distinct.
_JARVIS_ROW_MUT = {
    "CharUtilsTest::isAscii": ("lang.CharUtils.isAscii(char)",),
    "CharUtilsTest::isPrintable": ("lang.CharUtils.isAsciiPrintable(char)",),
    "FastMathTest::testMinMaxDouble": (
        "math.FastMath.min(double,double)",
        "math.FastMath.max(double,double)",
    ),
    "FastMathTest::toIntExact": ("math.FastMath.toIntExact(long)",),
    "IntervalTest": ("math.Interval.getSize(double,double)",),
    "PolynomialFunctionTest::testConstants": ("math.PolynomialFunction.value(double)",),
    "PolynomialFunctionTest::testfirstDerivativeComparison": (
        "math.PolynomialFunction.value(double)",
    ),
    "PolynomialFunctionTest::testLinear": ("math.PolynomialFunction.value(double)",),
    "PrecisionTest": ("math.Precision.equals(double,double,double)",),
    "UnivariateFunctionTest::testAbs": ("math.Abs.value(double)",),
}


def _fetch_task_rows(conn: Any) -> pd.DataFrame:
    """Return project task counts for the census variant and cross-variant stages."""
    query = f"""
    SELECT
        p.root_path AS root_path,
        task.stage AS stage,
        task.variant AS variant,
        task.status AS status,
        COUNT(*) AS task_count
    FROM task
    JOIN project p ON p.id = task.project_id
    WHERE task.variant IS NULL OR task.variant = '{CENSUS_VARIANT}'
    GROUP BY p.root_path, task.stage, task.variant, task.status
    ORDER BY p.root_path, task.stage, task.status
    """
    return pd.read_sql_query(query, conn)


def _census_status_ledger(conn: Any) -> tuple[pd.DataFrame, str, bool]:
    """Build an all-project task ledger and aggregate census status."""
    task_rows = _fetch_task_rows(conn)
    records: list[dict[str, object]] = []
    for project in TABLE1_PROJECTS:
        rows = task_rows[
            task_rows["root_path"].map(
                lambda value: str(value).rstrip("/").endswith(project)
            )
        ]
        failed_rows = rows[
            rows["status"].astype(str).str.upper().eq("FAILED")
            & rows["stage"].isin(_REQUIRED_STAGES)
        ]
        failed_stage_names = set(failed_rows["stage"])
        first_failed = next(
            (stage for stage in _REQUIRED_STAGES if stage in failed_stage_names),
            None,
        )
        stage_success = {
            stage: bool(
                (
                    rows["stage"].eq(stage)
                    & rows["status"].astype(str).str.upper().isin(_SUCCESS_STATUSES)
                ).any()
            )
            for stage in _REQUIRED_STAGES
        }
        all_complete = not failed_stage_names and all(stage_success.values())
        if all_complete:
            status = "complete"
        elif first_failed is not None:
            status = "failed"
        else:
            status = "not_reached"
        records.append(
            {
                "project": project,
                "generalization_status": status,
                "first_failed_stage": first_failed or "",
                "failed_task_count": int(failed_rows["task_count"].sum()),
                "completed_stage_count": int(sum(stage_success.values())),
                "generalization_task_count": int(
                    rows.loc[rows["stage"].eq("GENERALIZE_TESTS"), "task_count"].sum()
                ),
            }
        )
    ledger = pd.DataFrame(records)
    marker_present = CENSUS_COMPLETION_MARKER.is_file()
    census_status = (
        "complete"
        if marker_present and (ledger["generalization_status"] == "complete").all()
        else "partial"
    )
    return ledger, census_status, marker_present


def _table2_mut_counts(comparison: pd.DataFrame) -> tuple[int, int]:
    observed: set[str] = set()
    for row in comparison.loc[comparison["probe_count"].gt(0), "table_row"]:
        observed.update(_JARVIS_ROW_MUT.get(row, ()))
    return int(comparison["probe_count"].gt(0).sum()), len(observed)


def _build_breadth_table(
    ledger: pd.DataFrame, project_pvc: pd.DataFrame
) -> pd.DataFrame:
    rows = pd.DataFrame({"project": list(TABLE1_PROJECTS)})
    rows["jarvis_successful_pbt_pvc"] = rows["project"].map(
        lambda project: JARVIS_PROJECT_PBT_PVC.get(project, 0)
    )
    rows["jarvis_successful_muts"] = rows["project"].map(
        lambda project: JARVIS_PROJECT_MUTS.get(project, 0)
    )
    project_rows = project_pvc[project_pvc["variant"].eq(CENSUS_VARIANT)]
    pvc_columns = ["project", "aggregate_pvc", "sound_muts"]
    if "sound_properties" in project_rows.columns:
        pvc_columns.append("sound_properties")
    rows = rows.merge(
        project_rows[pvc_columns],
        on="project",
        how="left",
    )
    if "sound_properties" not in rows.columns:
        rows["sound_properties"] = pd.NA
    rows = rows.merge(
        ledger[["project", "generalization_status"]], on="project", how="left"
    )
    # Task/PIT status is diagnostic. A project with persisted sound jqwik logs keeps
    # its PVC and sound-MUT values even when an unrelated upstream task failed.
    total = pd.DataFrame(
        [
            {
                "project": "all 12 projects",
                "jarvis_successful_pbt_pvc": int(
                    rows["jarvis_successful_pbt_pvc"].sum()
                ),
                "jarvis_successful_muts": int(rows["jarvis_successful_muts"].sum()),
                "aggregate_pvc": (
                    int(rows["aggregate_pvc"].dropna().sum())
                    if rows["aggregate_pvc"].notna().any()
                    else None
                ),
                "sound_muts": (
                    int(rows["sound_muts"].dropna().sum())
                    if rows["sound_muts"].notna().any()
                    else None
                ),
                "sound_properties": (
                    int(rows["sound_properties"].dropna().sum())
                    if rows["sound_properties"].notna().any()
                    else None
                ),
                "generalization_status": "diagnostic",
            }
        ]
    )
    return pd.concat([rows, total], ignore_index=True)


def _build_budget_table(
    scoreboard: pd.DataFrame, mutation: pd.DataFrame
) -> pd.DataFrame:
    summary = summarize_variants(scoreboard, mutation)
    summary["variant"] = pd.Categorical(
        summary["variant"], categories=list(SWEEP_VARIANTS), ordered=True
    )
    summary = summary.sort_values("variant").reset_index(drop=True)
    available_variants = set(mutation["variant"].astype(str))
    for index, variant in enumerate(summary["variant"].astype(str)):
        if variant not in available_variants:
            summary.loc[
                index,
                [
                    "killed_mutants",
                    "covered_mutants",
                    "total_mutants",
                    "covered_mutation_score",
                ],
            ] = None
    return summary


def _metric(key: str, value: float | int | str, fmt: str, source) -> Metric:
    return Metric(key, value, fmt=fmt, provenance=capture(source))


def build(conn: Connection) -> RQReport:
    scoreboard = get_scoreboard(conn, variants=SWEEP_VARIANTS)
    full_scoreboard = scoreboard
    if "diagnostic_kind" in scoreboard.columns:
        full_scoreboard = cast(
            pd.DataFrame, scoreboard.loc[scoreboard["diagnostic_kind"].eq("FULL")]
        )
    comparison = compare_to_jarvis(full_scoreboard, variant=TABLE2_VARIANT)
    mutation = get_mutation_scores(conn, variants=SWEEP_VARIANTS)
    budget = _build_budget_table(scoreboard, mutation)

    with open_report_connection(CENSUS_DB) as census_conn:
        mut_rows = get_census_by_mut(census_conn, variants=[CENSUS_VARIANT])
        project_pvc = get_census_project_pvc(census_conn, variants=[CENSUS_VARIANT])
        ledger, census_status, marker_present = _census_status_ledger(census_conn)

    breadth = _build_breadth_table(ledger, project_pvc)
    sound_rows, sound_muts = _table2_mut_counts(comparison)
    metrics: list[Metric] = [
        _metric(
            "rq0.table2.reported_rows", len(JARVIS_TABLE2), "count", compare_to_jarvis
        ),
        _metric(
            "rq0.table2.probes",
            int(comparison["probe_count"].sum()),
            "count",
            compare_to_jarvis,
        ),
        _metric(
            "rq0.table2.distinct_muts",
            len({mut for muts in _JARVIS_ROW_MUT.values() for mut in muts}),
            "count",
            compare_to_jarvis,
        ),
        _metric("rq0.table2.sound_table2_rows", sound_rows, "count", compare_to_jarvis),
        _metric("rq0.table2.sound_jarvis_muts", sound_muts, "count", compare_to_jarvis),
        _metric(
            "rq0.breadth.published_projects",
            len(JARVIS_PROJECT_PBT_PVC),
            "count",
            _build_breadth_table,
        ),
        _metric("rq0.census.database", CENSUS_DB, "str", _census_status_ledger),
        _metric(
            "rq0.census.pvc_basis",
            "deduplicated_jqwik_value_logs_no_pit_reduction",
            "str",
            get_census_project_pvc,
        ),
        _metric("rq0.table2.variant", TABLE2_VARIANT, "str", compare_to_jarvis),
        _metric("rq0.census.variant", CENSUS_VARIANT, "str", get_census_project_pvc),
        _metric(
            "rq0.census.intended_projects",
            len(TABLE1_PROJECTS),
            "count",
            _census_status_ledger,
        ),
        _metric(
            "rq0.census.unresolved_mut_rows",
            int((~mut_rows["signature_known"]).sum()) if not mut_rows.empty else 0,
            "count",
            get_census_by_mut,
        ),
        _metric(
            "rq0.census.populated_projects",
            int(project_pvc["project"].nunique()),
            "count",
            get_census_project_pvc,
        ),
        _metric(
            "rq0.census.completed_projects",
            int((ledger["generalization_status"] == "complete").sum()),
            "count",
            _census_status_ledger,
        ),
        _metric(
            "rq0.census.failed_projects",
            int((ledger["generalization_status"] == "failed").sum()),
            "count",
            _census_status_ledger,
        ),
        _metric(
            "rq0.census.failed_task_count",
            int(ledger["failed_task_count"].sum()),
            "count",
            _census_status_ledger,
        ),
        _metric("rq0.census.status", census_status, "str", _census_status_ledger),
        _metric(
            "rq0.census.completion_marker",
            "present" if marker_present else "absent",
            "str",
            _census_status_ledger,
        ),
        _metric(
            "rq0.census.sound_properties",
            int(project_pvc["sound_properties"].sum()) if not project_pvc.empty else 0,
            "count",
            get_census_project_pvc,
        ),
        _metric(
            "rq0.census.sound_muts",
            int(project_pvc["sound_muts"].sum()) if not project_pvc.empty else 0,
            "count",
            get_census_project_pvc,
        ),
    ]
    for row in breadth.iloc[:-1].itertuples(index=False):
        slug = str(row.project).replace("-", "_")
        ledger_row = ledger.loc[ledger["project"].eq(row.project)].iloc[0]
        metrics.extend(
            [
                _metric(
                    f"rq0.census.project.{slug}.jarvis_pbt_pvc",
                    int(row.jarvis_successful_pbt_pvc),
                    "count",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.jarvis_muts",
                    int(row.jarvis_successful_muts),
                    "count",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.teralizer_pvc",
                    "unavailable"
                    if pd.isna(row.aggregate_pvc)
                    else int(row.aggregate_pvc),
                    "str",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.teralizer_sound_properties",
                    "unavailable"
                    if "sound_properties" not in breadth.columns
                    or pd.isna(getattr(row, "sound_properties", None))
                    else int(row.sound_properties),
                    "str",
                    get_census_project_pvc,
                ),
                _metric(
                    f"rq0.census.project.{slug}.sound_muts",
                    "unavailable" if pd.isna(row.sound_muts) else int(row.sound_muts),
                    "str",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.status",
                    str(ledger_row.generalization_status),
                    "str",
                    _census_status_ledger,
                ),
                _metric(
                    f"rq0.census.project.{slug}.first_failed_stage",
                    str(ledger_row.first_failed_stage),
                    "str",
                    _census_status_ledger,
                ),
                _metric(
                    f"rq0.census.project.{slug}.failed_task_count",
                    int(ledger_row.failed_task_count),
                    "count",
                    _census_status_ledger,
                ),
            ]
        )
    for row in budget.itertuples(index=False):
        variant = str(row.variant)
        token = variant.lower()
        for field in (
            "probes",
            "total_pvc",
            "killed_mutants",
            "covered_mutants",
            "covered_mutation_score",
        ):
            value = getattr(row, field)
            if pd.isna(value):
                value = "unavailable"
            metrics.append(
                _metric(
                    f"rq0.budget.{token}.{field}",
                    value,
                    "str"
                    if isinstance(value, str)
                    else ("pct1" if field == "covered_mutation_score" else "count"),
                    _build_budget_table,
                )
            )

    table2 = Table(
        key="rq0-table2-comparison",
        df=comparison,
        columns=[
            ColumnSpec("Reported case", "table_row"),
            ColumnSpec("JARVIS CUT PVC", "jarvis_cut_pvc", "count", "r"),
            ColumnSpec("JARVIS PBT PVC", "jarvis_pbt_pvc", "count", "r"),
            ColumnSpec("Teralizer PVC", "teralizer_pvc", "pvc", "r"),
            ColumnSpec("Δ (Tz−PBT)", "pvc_delta", "pvc", "r"),
        ],
        caption="Published JARVIS Table-2 PVC references and Teralizer generated PVC.",
        label="tab:teralizer-rq0-table2",
        note=(
            "Teralizer PVC is generated-property PVC; JARVIS CUT/PBT values are "
            "published references. An em dash means no matching Teralizer probe. "
            "Δ is Teralizer PVC minus JARVIS PBT PVC; no Teralizer CUT denominator "
            "is available."
        ),
        provenance=capture(compare_to_jarvis),
    )
    breadth_table = Table(
        key="rq0-breadth-summary",
        df=breadth,
        columns=[
            ColumnSpec("Table-1 project", "project"),
            ColumnSpec(
                "JARVIS successful PBT PVC", "jarvis_successful_pbt_pvc", "count", "r"
            ),
            ColumnSpec(
                "JARVIS successful MUTs", "jarvis_successful_muts", "count", "r"
            ),
            ColumnSpec("Teralizer aggregate PVC", "aggregate_pvc", "pvc", "r"),
            ColumnSpec("Teralizer sound MUTs", "sound_muts", "pvc", "r"),
        ],
        caption="Project-level PVC and distinct MUT breadth across the JARVIS Table-1 population.",
        label="tab:teralizer-rq0-breadth",
        note=(
            "JARVIS zero means no successful Table-2 PVC result was reported for "
            "that Table-1 project; it is not an attempted-case failure count. "
            "Teralizer aggregate PVC unions duplicate generated values by MUT and "
            "parameter. Sound MUTs have at least one sound generalization."
        ),
        provenance=capture(get_census_project_pvc),
    )
    budget_table = Table(
        key="rq0-pvc-budget",
        df=budget,
        columns=[
            ColumnSpec("Improved variant", "variant"),
            ColumnSpec("Probes", "probes", "count", "r"),
            ColumnSpec("Total PVC", "total_pvc", "count", "r"),
            ColumnSpec("Killed mutants", "killed_mutants", "pvc", "r"),
            ColumnSpec("Covered mutants", "covered_mutants", "pvc", "r"),
            ColumnSpec("Covered mutation score", "covered_mutation_score", "pct1", "r"),
        ],
        caption="PVC rises with the tries budget while covered mutation score stays flat.",
        label="tab:teralizer-rq0-pvc",
        note=(
            "PVC is a generation-volume diagnostic. Missing PIT rows render as "
            "unavailable rather than zero kills or gain."
        ),
        provenance=capture(summarize_variants),
    )
    status_summary = "; ".join(
        (
            f"{row.project}: {row.generalization_status}"
            + (
                f" (first failed stage {row.first_failed_stage}; "
                f"failed tasks {row.failed_task_count})"
                if row.first_failed_stage
                else ""
            )
        )
        for row in ledger.itertuples(index=False)
    )
    sections = [
        Section(
            title="Published-case comparison",
            blocks=[
                Prose(
                    "JARVIS is represented by published Table-2 CUT/PBT values; it is "
                    "not rerun. The Table-2 table preserves the historical CUT→PBT→"
                    "Teralizer order. {rq0.table2.sound_table2_rows} of {rq0.table2.reported_rows} "
                    "reported rows have a sound Teralizer generated result, covering "
                    "{rq0.table2.sound_jarvis_muts} of {rq0.table2.distinct_muts} distinct "
                    "JARVIS MUTs."
                ),
                table2,
            ],
        ),
        Section(
            title="Table-1 population breadth",
            blocks=[
                Prose(
                    "The breadth table keeps the 12-project Table-1 population separate "
                    "from the 10-row successful Table-2 subset. JARVIS zero means no "
                    "successful Table-2 PVC result was reported for that project; it "
                    "does not establish how many cases JARVIS attempted. Teralizer "
                    "PVC is a deduplicated execution-log aggregate, and sound MUTs "
                    "count distinct production methods with at least one sound "
                    "generalization."
                ),
                breadth_table,
                Prose(
                    "Census diagnostics: status {rq0.census.status}; intended projects "
                    "{rq0.census.intended_projects}, projects with persisted PVC rows "
                    "{rq0.census.populated_projects}, completed projects "
                    "{rq0.census.completed_projects}, failed projects "
                    "{rq0.census.failed_projects}; completion marker "
                    "{rq0.census.completion_marker}. Project statuses: "
                    + status_summary
                    + ". PIT/reduction is not required for PVC and remains provenance "
                    "diagnostic context."
                ),
            ],
        ),
        Section(
            title="Why PVC is not the effectiveness metric",
            blocks=[
                Prose(
                    "Across the tries sweep, PVC increases with the generation budget "
                    "while mutation kills and covered mutation score remain the "
                    "effectiveness quantities used by later RQs."
                ),
                budget_table,
            ],
        ),
    ]
    return RQReport(
        rq="rq0",
        title="RQ0 - Published JARVIS Comparison and PVC Context",
        db=SCOREBOARD_DB,
        sections=sections,
        metrics=metrics,
    )


register("rq0", ReportSpec(build, SCOREBOARD_DB, "new"))
