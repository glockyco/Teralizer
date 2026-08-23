"""RQ0: comparison against JARVIS's reported results and census PVC context."""

from __future__ import annotations

from typing import Any, cast

import re

import pandas as pd

from teralizer.cut_pvc import load_cut_values
from teralizer.eval.evidence import jarvis_values
from teralizer.eval.entities import ref_for_csv
from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec, ReportContext
from teralizer.eval.model import (
    ColumnSpec,
    Metric,
    MetricPopulation,
    Prose,
    RQReport,
    Section,
    Table,
    ValueKind,
    share_value,
)
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.jarvis_scoreboard import (
    JARVIS_TABLE2,
    SWEEP_VARIANTS,
    compare_to_jarvis,
    get_mutation_scores,
    suite_union_pvc,
    summarize_variants,
)


TABLE2_VARIANT = "IMPROVED_100_TRIES"
CENSUS_VARIANT = "IMPROVED_100_TRIES"
TABLE1_PROJECTS = (
    "commons-math-2017-02-01-census",
    "commons-lang-2017-02-01-census",
    "commons-cli-2017-02-01-census",
    "commons-codec-2017-02-01-census",
    "commons-collections-2017-02-01-census",
    "commons-configuration-2017-02-01-census",
    "commons-csv-2017-02-01-census",
    "commons-email-2017-02-01-census",
    "commons-io-2017-02-01-census",
    "commons-jexl-2017-02-01-census",
    "commons-pool-2017-02-01-census",
    "commons-text-2017-02-01-census",
)

# Only these two Table-1 projects have successful PVC values in the published
# Table-2 subset. The remaining values are explicitly zero successful reported
# cases. The zero values encode the published successful-case subset.
JARVIS_PROJECT_PBT_PVC = {
    "commons-lang-2017-02-01-census": 104,
    "commons-math-2017-02-01-census": 1604,
}
JARVIS_PROJECT_MUTS = {
    "commons-lang-2017-02-01-census": 2,
    "commons-math-2017-02-01-census": 7,
}

_REQUIRED_STAGES = (
    "EXECUTE_TESTS_ORIGINAL",
    "COLLECT_JUNIT_REPORTS_ORIGINAL",
    "GENERALIZE_TESTS",
    "EXECUTE_TESTS_GENERALIZED",
    "COLLECT_JUNIT_REPORTS_GENERALIZED",
)
_SUCCESS_STATUSES = {"SUCCEEDED", "SUCCESS", "COMPLETED"}
# Every fixture is pinned to a corpus snapshot date, which the reader does not need
# repeated on all twelve rows; the caption carries the date instead.
_SNAPSHOT_SUFFIX = re.compile(r"-\d{4}-\d{2}-\d{2}$")

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


_JARVIS_SCENARIO_NUMBER = {
    row.table_row: number for number, row in enumerate(JARVIS_TABLE2, start=1)
}


def _scenario_number(table_row: str) -> int:
    """Return the stable row number for a JARVIS Table-2 scenario."""
    return _JARVIS_SCENARIO_NUMBER[table_row]


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


def _census_status_ledger(
    conn: Any, *, marker_present: bool
) -> tuple[pd.DataFrame, str, bool]:
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
    # JARVIS surveys all twelve projects but reports cases for only two, so the
    # rest have no published value. Defaulting them to zero would claim a
    # measurement of none where the paper simply makes none.
    rows.loc[:, "jarvis_successful_pbt_pvc"] = rows["project"].map(
        JARVIS_PROJECT_PBT_PVC.get
    )
    rows.loc[:, "jarvis_successful_muts"] = rows["project"].map(JARVIS_PROJECT_MUTS.get)
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
        rows.loc[:, "sound_properties"] = pd.NA
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
                    rows["jarvis_successful_pbt_pvc"].dropna().sum()
                ),
                "jarvis_successful_muts": int(
                    rows["jarvis_successful_muts"].dropna().sum()
                ),
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
    result = pd.concat([rows, total], ignore_index=True)
    # Reader-facing label drops the internal `-census` DB suffix and the corpus
    # snapshot date every fixture carries; the internal `project` identity stays
    # intact for merges and manifest metric slugs.
    result.loc[:, "project_label"] = result["project"].map(
        lambda project: _SNAPSHOT_SUFFIX.sub("", str(project).removesuffix("-census"))
    )
    # The total is its own group, which is what puts a rule above it.
    result.loc[:, "row_group"] = ["projects"] * len(rows) + ["total"] * len(total)
    return result


_BUDGET_VARIANTS = (
    "IMPROVED_100_TRIES",
    "IMPROVED_200_TRIES",
    "IMPROVED_1000_TRIES",
)


def _build_budget_table(
    scoreboard: pd.DataFrame, mutation: pd.DataFrame
) -> pd.DataFrame:
    """Return generalized-test counts, PVC, and mutation outcomes by budget."""
    summary = summarize_variants(scoreboard, mutation)
    summary.isetitem(
        summary.columns.get_loc("variant"),
        pd.Categorical(
            summary["variant"], categories=list(_BUDGET_VARIANTS), ordered=True
        ),
    )
    summary = summary.sort_values("variant").reset_index(drop=True)
    summary.loc[:, "budget_entity"] = summary["variant"].map(
        lambda variant: ref_for_csv("budget", variant)
    )
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

    summary = summary.assign(
        covered_mutation_score=summary.apply(
            lambda row: (
                share_value(row["killed_mutants"], row["covered_mutants"])
                if not pd.isna(row["killed_mutants"])
                and not pd.isna(row["covered_mutants"])
                and int(row["covered_mutants"]) != 0
                else None
            ),
            axis=1,
        )
    )
    return summary


def _metric(
    key: str,
    value: float | int | str,
    fmt: str,
    source,
    *,
    kind: ValueKind | None = None,
    population: MetricPopulation | None = None,
) -> Metric:
    return Metric(
        key,
        value,
        fmt=fmt,
        provenance=capture(source),
        kind=kind,
        population=population,
    )


def _project_population(key: str) -> MetricPopulation:
    return MetricPopulation(key, "Project", "controlled")


def _count_or_unavailable(value) -> str:
    """Thousands-formatted count, or the explicit unavailable marker."""
    return "unavailable" if pd.isna(value) else f"{int(value):,}"


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("scenarios")
    census_conn = context.corpus("benchmark")
    facts_path = context.file("jarvis-pvc-facts")
    cut_values_path = context.file("cut-values")
    if facts_path is None or cut_values_path is None:
        raise AssertionError("required RQ0 evidence resolved as absent")
    scoreboard = jarvis_values.scoreboard_frame(facts_path)
    scoreboard = cast(
        pd.DataFrame, scoreboard.loc[scoreboard["variant"].isin(SWEEP_VARIANTS)]
    )
    full_scoreboard = scoreboard
    if "diagnostic_kind" in scoreboard.columns:
        full_scoreboard = cast(
            pd.DataFrame, scoreboard.loc[scoreboard["diagnostic_kind"].eq("FULL")]
        )
    comparison = compare_to_jarvis(full_scoreboard, variant=TABLE2_VARIANT)
    cut_values = load_cut_values(cut_values_path)
    suite = suite_union_pvc(full_scoreboard, cut_values, variant=TABLE2_VARIANT)
    comparison = comparison.merge(suite, on="table_row", how="left")
    # Each published JARVIS scenario is one observation in the comparison table.
    comparison.loc[:, "scenario_number"] = comparison["table_row"].map(_scenario_number)
    comparison.loc[:, "scenario_entity"] = comparison["table_row"].map(
        lambda value: ref_for_csv("scenario", value)
    )
    mutation = get_mutation_scores(conn, variants=SWEEP_VARIANTS)
    budget = _build_budget_table(full_scoreboard, mutation)

    mut_rows = jarvis_values.census_by_mut_frame(facts_path)
    mut_rows = cast(pd.DataFrame, mut_rows.loc[mut_rows["variant"].eq(CENSUS_VARIANT)])
    project_pvc = jarvis_values.census_project_frame(facts_path)
    project_pvc = cast(
        pd.DataFrame, project_pvc.loc[project_pvc["variant"].eq(CENSUS_VARIANT)]
    )
    ledger, census_status, marker_present = _census_status_ledger(
        census_conn,
        marker_present=context.file("completion-marker") is not None,
    )

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
            "rq0.table2.suite_basis",
            "measured_cut_union_generalized" if not cut_values.empty else "unavailable",
            "str",
            suite_union_pvc,
        ),
        _metric(
            "rq0.table2.suite_total_pvc",
            _count_or_unavailable(
                comparison["suite_pvc"].dropna().sum()
                if comparison["suite_pvc"].notna().any()
                else pd.NA
            ),
            "str",
            suite_union_pvc,
        ),
        _metric(
            "rq0.breadth.published_projects",
            len(JARVIS_PROJECT_PBT_PVC),
            "count",
            _build_breadth_table,
            kind=ValueKind.COUNT,
            population=_project_population("rq0.breadth.published_projects"),
        ),
        _metric(
            "rq0.breadth.jarvis_total_pbt_pvc",
            int(breadth.iloc[-1].jarvis_successful_pbt_pvc),
            "count",
            _build_breadth_table,
        ),
        _metric(
            "rq0.breadth.jarvis_total_muts",
            int(breadth.iloc[-1].jarvis_successful_muts),
            "count",
            _build_breadth_table,
        ),
        _metric(
            "rq0.breadth.teralizer_total_pvc",
            _count_or_unavailable(breadth.iloc[-1].aggregate_pvc),
            "str",
            _build_breadth_table,
        ),
        _metric(
            "rq0.breadth.teralizer_total_sound_muts",
            _count_or_unavailable(breadth.iloc[-1].sound_muts),
            "str",
            _build_breadth_table,
        ),
        _metric(
            "rq0.census.pvc_basis",
            "deduplicated_jqwik_value_logs_no_pit_reduction",
            "str",
            jarvis_values.census_project_frame,
        ),
        _metric("rq0.table2.variant", TABLE2_VARIANT, "str", compare_to_jarvis),
        _metric(
            "rq0.census.variant",
            CENSUS_VARIANT,
            "str",
            jarvis_values.census_project_frame,
        ),
        _metric(
            "rq0.census.intended_projects",
            len(TABLE1_PROJECTS),
            "count",
            _census_status_ledger,
            kind=ValueKind.COUNT,
            population=_project_population("rq0.census.intended_projects"),
        ),
        _metric(
            "rq0.census.unresolved_mut_rows",
            int((~mut_rows["signature_known"]).sum()) if not mut_rows.empty else 0,
            "count",
            jarvis_values.census_by_mut_frame,
        ),
        _metric(
            "rq0.census.populated_projects",
            int(project_pvc["project"].nunique()),
            "count",
            jarvis_values.census_project_frame,
        ),
        _metric(
            "rq0.census.completed_projects",
            int((ledger["generalization_status"] == "complete").sum()),
            "count",
            _census_status_ledger,
            kind=ValueKind.COUNT,
            population=_project_population("rq0.census.completed_projects"),
        ),
        _metric(
            "rq0.census.failed_projects",
            int((ledger["generalization_status"] == "failed").sum()),
            "count",
            _census_status_ledger,
        ),
        _metric(
            "rq0.census.not_reached_projects",
            int((ledger["generalization_status"] == "not_reached").sum()),
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
            jarvis_values.census_project_frame,
        ),
        _metric(
            "rq0.census.sound_muts",
            int(project_pvc["sound_muts"].sum()) if not project_pvc.empty else 0,
            "count",
            jarvis_values.census_project_frame,
        ),
    ]
    for row in comparison.to_dict("records"):
        row_slug = re.sub(r"[^a-z0-9]+", "_", str(row["table_row"]).lower()).strip("_")
        metrics.append(
            _metric(
                f"rq0.table2.row.{row_slug}.measured_cut_pvc",
                _count_or_unavailable(row["measured_cut_pvc"]),
                "str",
                suite_union_pvc,
            )
        )
    for row in breadth.iloc[:-1].to_dict("records"):
        slug = str(row["project"]).replace("-", "_")
        ledger_row = ledger.loc[ledger["project"].eq(row["project"])].iloc[0]
        metrics.extend(
            [
                # JARVIS publishes a value for two of the twelve projects. The other ten carry
                # no measurement, so they render like every other absent figure in this table.
                _metric(
                    f"rq0.census.project.{slug}.jarvis_pbt_pvc",
                    _count_or_unavailable(row["jarvis_successful_pbt_pvc"]),
                    "str",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.jarvis_muts",
                    _count_or_unavailable(row["jarvis_successful_muts"]),
                    "str",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.teralizer_pvc",
                    _count_or_unavailable(row["aggregate_pvc"]),
                    "str",
                    _build_breadth_table,
                ),
                _metric(
                    f"rq0.census.project.{slug}.teralizer_sound_properties",
                    _count_or_unavailable(row.get("sound_properties")),
                    "str",
                    jarvis_values.census_project_frame,
                ),
                _metric(
                    f"rq0.census.project.{slug}.sound_muts",
                    _count_or_unavailable(row["sound_muts"]),
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
    for row in budget.to_dict("records"):
        variant = str(row["variant"])
        token = variant.lower()
        for field in (
            "probes",
            "killed_mutants",
            "covered_mutants",
            "covered_mutation_score",
        ):
            value = row[field]
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

    comparison_table = comparison.assign(table_row=comparison["scenario_entity"])
    breadth_table_data = breadth.assign(project=breadth["project_label"])
    budget_table_data = budget.assign(variant=budget["budget_entity"])

    table2 = Table(
        key="rq0-table2-comparison",
        df=comparison_table,
        columns=[
            # Both labels belong in the lower header row, where the thesis puts
            # the labels of columns that carry no spanning group header.
            ColumnSpec(
                "#",
                "scenario_number",
                ValueKind.COUNT,
                "r",
            ),
            ColumnSpec(
                "JARVIS scenario",
                "table_row",
                kind=ValueKind.ENTITY,
            ),
            ColumnSpec(
                "CUT PVC",
                "original_cut_pvc",
                ValueKind.COUNT,
                "r",
                group_header="Original",
            ),
            ColumnSpec(
                "PBT PVC",
                "jarvis_pbt_pvc",
                ValueKind.COUNT,
                "r",
                group_header="JARVIS",
            ),
            # Measured CUT PVC is deliberately not shown. It is computed and kept
            # in provenance for auditing, but the table compares JARVIS's reported
            # values against Teralizer's suite PVC and nothing else.
            ColumnSpec(
                "PBT PVC",
                "suite_pvc",
                ValueKind.COUNT,
                "r",
                group_header="{entity.tool.teralizer}",
            ),
        ],
        full_width=True,
        caption=(
            "PVC before generalization, after generalization with JARVIS, and "
            "after generalization with {entity.tool.teralizer} for each of the 10 "
            "scenarios reported by JARVIS."
        ),
        short_caption="PVC per JARVIS scenario before and after generalization",
        label="tab:teralizer-rq0-table2",
        latex_resize_to_width=False,
        note=(
            "JARVIS CUT and PBT PVC are the published values, with PBT PVC "
            "measuring the synthesized properties alone. For Teralizer, PVC "
            "includes the reconstructed original tests' values and the "
            "generalized tests' values. A dash marks a scenario Teralizer "
            "excludes from generalization."
        ),
        provenance=capture(compare_to_jarvis),
    )
    breadth_table = Table(
        key="rq0-breadth-summary",
        df=breadth_table_data,
        columns=[
            ColumnSpec("Benchmark project", "project"),
            ColumnSpec(
                "PBT PVC",
                "jarvis_successful_pbt_pvc",
                ValueKind.COUNT,
                "r",
                group_header="JARVIS",
            ),
            ColumnSpec(
                "MUTs",
                "jarvis_successful_muts",
                ValueKind.COUNT,
                "r",
                group_header="JARVIS",
            ),
            ColumnSpec(
                "PBT PVC",
                "aggregate_pvc",
                ValueKind.COUNT,
                "r",
                group_header="{entity.tool.teralizer}",
            ),
            ColumnSpec(
                "MUTs",
                "sound_muts",
                ValueKind.COUNT,
                "r",
                group_header="{entity.tool.teralizer}",
            ),
        ],
        group_by="row_group",
        caption=(
            "MUTs with a generalized test and the PVC of those tests, per project."
        ),
        short_caption="MUTs with generalized tests and their PVC per project",
        label="tab:teralizer-rq0-breadth",
        latex_resize_to_width=False,
        full_width=True,
        # The spanning tool labels sit over numeric columns, so they follow the
        # same right alignment as the values and as the scenario comparison table.
        group_header_align="r",
        note=(
            "A dash in the JARVIS columns marks a project that the publication "
            "reports no case for. A dash in the Teralizer columns marks a project "
            "for which the pipeline produced no generalized test. Teralizer "
            "aggregate PVC counts distinct values exercised by "
            "generalized tests for each MUT and parameter. Generalized MUTs have "
            "at least one generalized test."
        ),
        provenance=capture(jarvis_values.census_project_frame),
    )
    budget_table = Table(
        key="rq0-pvc-budget",
        df=budget_table_data,
        columns=[
            ColumnSpec(
                "Sampling Budget",
                "variant",
                kind=ValueKind.ENTITY,
                # Every row holds the input-selection strategy fixed, so the
                # variant belongs over the budget column, not in each cell.
                group_header="{entity.variant.improved}",
            ),
            ColumnSpec("Tests", "probes", ValueKind.COUNT, "r"),
            ColumnSpec("PVC", "total_pvc", ValueKind.COUNT, "r"),
            ColumnSpec(
                "Killed",
                "killed_mutants",
                ValueKind.COUNT,
                "r",
                group_header="Mutation Testing Results",
            ),
            ColumnSpec(
                "Covered",
                "covered_mutants",
                ValueKind.COUNT,
                "r",
                group_header="Mutation Testing Results",
            ),
            ColumnSpec(
                "Score",
                "covered_mutation_score",
                ValueKind.SHARE,
                "r",
                group_header="Mutation Testing Results",
            ),
        ],
        caption=(
            "PVC and mutation testing results for the same 10 generalized tests "
            "at 3 sampling budgets. Only the sampling budget changes between the "
            "rows. PVC sums the distinct input values that the tests exercise, "
            "and Score is the killed mutants divided by the covered mutants."
        ),
        short_caption="PVC and mutation testing results per sampling budget",
        label="tab:teralizer-rq0-pvc",
        body_style="\\tabstyle",
        float_spec="H",
        latex_resize_to_width=False,
        full_width=True,
        group_header_align="r",
        # The caption defines PVC and the score, so the note carries only what a
        # reader cannot read off the rows.
        note="The same mutant sets are covered and killed at every budget.",
        provenance=capture(_build_budget_table),
    )
    sections = [
        Section(
            title="Reported-case comparison",
            blocks=[
                Prose(
                    "The JARVIS publication reports CUT and PBT PVC for ten cases "
                    "from commons-lang and commons-math (its Table 2), with PBT PVC "
                    "collected from the synthesized properties alone at the "
                    "ScalaCheck default of 100 samples. Cases aggregate all "
                    "properties JARVIS synthesized for one scenario while Teralizer "
                    "creates one generalized test per assertion, so the comparison "
                    "aligns on distinct MUTs. {rq0.table2.sound_table2_rows} of "
                    "{rq0.table2.reported_rows} reported cases have a matching "
                    "generalized test, covering "
                    "{rq0.table2.sound_jarvis_muts} of {rq0.table2.distinct_muts} "
                    "distinct MUTs."
                ),
                Prose(
                    "The JARVIS implementation and template library are "
                    "unavailable, so JARVIS is not rerun and its Table-2 rows serve "
                    "as the comparison reference. A reported PBT PVC counts the "
                    "values that the synthesized properties sampled. IntervalTest "
                    "reports 2 because its property stopped on the second sample, "
                    "so that cell counts a run that ended rather than the values a "
                    "passing property covered."
                ),
                Prose(
                    "Teralizer extracts its specification from a single execution. "
                    "Generalized tests exercise the original inputs as their first "
                    "samples by design, so coverage after generalization never "
                    "falls below the original tests' values. The Teralizer column "
                    "reports the measured value coverage after generalization, "
                    "joining the captured original-suite values with the "
                    "generalized tests' value logs."
                ),
                table2,
            ],
        ),
        Section(
            title="Applicability breadth",
            blocks=[
                Prose(
                    "RQ0 uses a separate, pinned fixture set reproducing the twelve "
                    "Apache Commons project versions of the JARVIS evaluation. "
                    "RQ1--RQ5 use the constructed commons-utils dataset. The JARVIS "
                    "columns aggregate the reported cases by project. Teralizer PVC "
                    "deduplicates values per MUT and parameter across generalized "
                    "tests, so a value exercised by several tests counts once."
                ),
                breadth_table,
                Prose(
                    "Census status {rq0.census.status}. The census intended "
                    "{rq0.census.intended_projects} projects: "
                    "{rq0.census.completed_projects} completed, "
                    "{rq0.census.failed_projects} failed, and the run did not reach "
                    "{rq0.census.not_reached_projects}. "
                    "{rq0.census.populated_projects} projects carry persisted PVC "
                    "rows. Completion marker {rq0.census.completion_marker}."
                ),
            ],
        ),
        Section(
            title="PVC and mutation score",
            blocks=[
                Prose(
                    "A larger sampling budget can raise PVC by exercising more "
                    "distinct values. In this sweep, the same 10 tests kill "
                    "{rq0.budget.improved_100_tries.killed_mutants} of "
                    "{rq0.budget.improved_100_tries.covered_mutants} covered mutants "
                    "at every budget. Their covered mutation score therefore stays "
                    "at {rq0.budget.improved_100_tries.covered_mutation_score}. More "
                    "sampling effort does not change detection of the selected "
                    "mutants in this sweep."
                ),
                budget_table,
            ],
        ),
    ]
    return RQReport(
        rq="rq0",
        title="RQ0 - JARVIS Comparison",
        sections=sections,
        metrics=metrics,
    )


register(
    "rq0",
    ReportSpec(
        build,
        (
            CorpusInputSpec("scenarios", "jarvis-scenarios"),
            CorpusInputSpec("benchmark", "jarvis-benchmark"),
            FileInputSpec(
                "jarvis-pvc-facts",
                "analysis/data/report-inputs/jarvis-value-facts.json",
            ),
            FileInputSpec(
                "cut-values",
                "analysis/data/jarvis-cut-values/cut_values.tsv",
            ),
            FileInputSpec(
                "completion-marker",
                "data/detached/census-gen.complete",
                required=False,
                content_addressed=True,
            ),
        ),
    ),
)
