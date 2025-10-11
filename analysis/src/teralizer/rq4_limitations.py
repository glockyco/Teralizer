"""RQ4: Limitations - Analysis functions for filtering causes and processing failures.

This module provides functions to analyze the limitations of the test generalization
approach by examining filtering causes in the evaluation dataset and processing
pipeline failures in the extended dataset of open source projects.
"""

import pandas as pd
import re
from typing import Dict

from .formatting import (
    replace_variant_names_with_macros,
    replace_project_names_with_macros,
    build_latex_table_content,
    sort_dataframe_by_project,
)
from .exports import get_variant_macro, get_project_type
from .stages import map_internal_stage_to_paper_stage, get_stage_order


# =============================================================================
# Helper Functions
# =============================================================================


def get_variant_type(variant_name: str) -> str:
    """Group variant names by type for midrule insertion.

    Args:
        variant_name: The variant name or macro (e.g., "NAIVE_10_TRIES", "\\VariantNaiveA{}")

    Returns:
        Group name for midrule grouping
    """
    # Handle both original names and macro names
    if variant_name in ["ORIGINAL", "BASELINE", "INITIAL", "SHARED"] or any(
        x in variant_name
        for x in [
            "\\VariantOriginal{}",
            "\\VariantBaseline{}",
            "\\VariantInitial{}",
            "\\VariantShared{}",
        ]
    ):
        return "baseline"
    elif variant_name.startswith("NAIVE_") or "\\VariantNaive" in variant_name:
        return "naive"
    elif variant_name.startswith("IMPROVED_") or "\\VariantImproved" in variant_name:
        return "improved"
    else:
        return "other"


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================


def get_exclusions_summary_data(conn) -> pd.DataFrame:
    """Get overall exclusions summary by variant and level.

    Args:
        conn: Database connection

    Returns:
        DataFrame with columns: variant, level, is_included, excluded_by, count
    """
    query = "SELECT * FROM mv_exclusions_all"
    df = pd.read_sql_query(query, conn)
    return df


def get_filtering_exclusions_data(conn) -> pd.DataFrame:
    """Get filtering-based exclusions data where reject > 0.

    Args:
        conn: Database connection

    Returns:
        DataFrame with filtering results by variant, level, and filter
    """
    query = "SELECT * FROM mv_exclusions_filtering WHERE reject > 0"
    df = pd.read_sql_query(query, conn)
    return df


def get_spf_failures_data(conn) -> pd.DataFrame:
    """Get SPF execution failures data.

    Args:
        conn: Database connection

    Returns:
        DataFrame with columns: error_category, count
    """
    query = "SELECT * FROM mv_exclusions_jpf"
    df = pd.read_sql_query(query, conn)
    return df


def get_test_failures_data(conn) -> pd.DataFrame:
    """Get test execution failures data.

    Args:
        conn: Database connection

    Returns:
        DataFrame with test failure counts by failure_type and variant
    """
    query = "SELECT * FROM mv_exclusions_test_fails"
    df = pd.read_sql_query(query, conn)
    return df


def get_test_failures_by_projects_data(conn) -> pd.DataFrame:
    """Get test execution failures data grouped by projects using test generalization.

    Args:
        conn: Database connection

    Returns:
        DataFrame with test failure counts by failure_type and project
    """
    query = """
    SELECT
        project_name(jtr.project_id) AS project_name,
        jtr.project_id,
        jtr.failure_type,
        count(*) as count
    FROM junit_test_report jtr
    JOIN project p ON p.id = jtr.project_id
    WHERE p.use_test_generalization
    GROUP BY jtr.project_id, jtr.failure_type
    ORDER BY project_name, jtr.project_id, jtr.failure_type
    """
    df = pd.read_sql_query(query, conn)
    return df


def get_test_executions_by_variant_data(conn) -> pd.DataFrame:
    """Get test execution data grouped by variant.

    Args:
        conn: Database connection

    Returns:
        DataFrame with test execution counts by failure_type and variant
    """
    query = """
    SELECT
        variant_name(stage, variant) AS variant,
        variant_order(variant_name(stage, variant)) AS variant_order,
        failure_type,
        count(*) AS count
    FROM junit_test_report
    GROUP BY stage, variant, failure_type
    ORDER BY variant_order, failure_type
    """
    df = pd.read_sql_query(query, conn)
    return df


def get_processing_failure_causes_data(conn) -> pd.DataFrame:
    """Get detailed processing failure causes data.

    Args:
        conn: Database connection (should be extended dataset connection)

    Returns:
        DataFrame with stage and failure info for cause analysis
    """
    query = "SELECT stage, info FROM v_project_failures"
    df = pd.read_sql_query(query, conn)
    return df


# =============================================================================
# Computation Functions (compute_*)
# =============================================================================


def compute_exclusion_percentages(df: pd.DataFrame) -> pd.DataFrame:
    """Compute exclusion percentages and format for display.

    Args:
        df: Raw exclusions data

    Returns:
        DataFrame with computed percentages and formatted columns
    """
    # Map level to Type
    level_map = {
        "1-TEST": "Test",
        "2-ASSERTION": "Assertion",
        "3-GENERALIZATION": "Generalization",
    }
    df["Type"] = df["level"].map(level_map)

    # Get unique (variant, level) pairs in original order
    ordered_pairs = df[["variant", "Type"]].drop_duplicates()

    # Calculate included and excluded counts
    included = (
        df[df["is_included"]].groupby(["variant", "Type"])["count"].sum().reset_index()
    )
    excluded = (
        df[~df["is_included"]].groupby(["variant", "Type"])["count"].sum().reset_index()
    )

    included = included.rename(columns={"count": "included_count"})
    excluded = excluded.rename(columns={"count": "excluded_count"})

    # Merge counts
    result = pd.merge(ordered_pairs, included, on=["variant", "Type"], how="left")
    result = pd.merge(result, excluded, on=["variant", "Type"], how="left")

    # Fill NaNs with 0 and ensure integer type
    result["included_count"] = result["included_count"].fillna(0).astype(int)
    result["excluded_count"] = result["excluded_count"].fillna(0).astype(int)

    # Compute Total column
    result["Total"] = result["included_count"] + result["excluded_count"]

    # Compute percentages
    result["included_pct"] = (result["included_count"] / result["Total"] * 100).round(1)
    result["excluded_pct"] = (result["excluded_count"] / result["Total"] * 100).round(1)

    return result


def compute_filtering_exclusions_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Compute filtering exclusions with proper formatting.

    Args:
        df: Raw filtering exclusions data

    Returns:
        DataFrame with computed statistics and formatting
    """
    # Map level to Type
    level_map = {
        "1-TEST": "Test",
        "2-ASSERTION": "Assertion",
        "3-GENERALIZATION": "Generalization",
    }
    df["Type"] = df["level"].map(level_map)

    # Ensure integer columns are int type
    df["total"] = df["total"].astype(int)
    df["accept"] = df["accept"].astype(int)
    df["defer"] = df["defer"].astype(int)
    df["reject"] = df["reject"].astype(int)

    # Remove Filter suffix from filter names
    df["filter_name"] = df["filter_name"].str.replace(r"Filter$", "", regex=True)

    # Rename specific filters for clarity
    df["filter_name"] = df["filter_name"].replace(
        {"UnsupportedAssertion": "AssertionType"}
    )

    # Calculate percentages
    df["accept_pct"] = (df["accept"] / df["total"] * 100).round(1)
    df["defer_pct"] = (df["defer"] / df["total"] * 100).round(1)
    df["reject_pct"] = (df["reject"] / df["total"] * 100).round(1)

    return df


def compute_spf_error_categorization(df: pd.DataFrame) -> pd.DataFrame:
    """Categorize SPF errors and compute percentages.

    Args:
        df: Raw SPF failures data

    Returns:
        DataFrame with categorized errors and percentages
    """
    category_map = {
        "ArithmeticException: div by 0": "SPF exception",
        "NoSuchMethodException": "SPF exception",
        "AssertionFailedError": "SPF exception",
        "RuntimeException: symbolic array length": "SPF exception",
        "NoUncaughtExceptionsProperty": "SPF exception",
        "ArrayIndexOutOfBoundsException (setDoubleValue)": "SPF exception",
        "ArrayIndexOutOfBoundsException (simple)": "SPF exception",
        "NullPointerException (queueMark)": "SPF exception",
        "ArrayIndexOutOfBoundsException (setLongValue)": "SPF exception",
        "NullPointerException (writeSpecificationFiles:147)": "Teralizer exception",
        "NullPointerException (writeSpecificationFiles:124)": "Teralizer exception",
        "Failed to collect specification": "Teralizer exception",
        "OutOfMemoryError: Java heap space": "OutOfMemoryError",
        "OutOfMemoryError: GC overhead": "OutOfMemoryError",
    }

    # Apply the mapping, keeping unmapped categories as is
    df["merged_category"] = (
        df["error_category"].map(category_map).fillna(df["error_category"])
    )

    # Group by merged_category and sum the counts
    df_merged = df.groupby("merged_category", as_index=False)["count"].sum()

    # Sort by count in descending order
    df_merged = df_merged.sort_values(by="count", ascending=False).reset_index(
        drop=True
    )

    # Add percent column
    total = df_merged["count"].sum()
    df_merged["percent"] = (df_merged["count"] / total * 100).apply(
        lambda x: f"{x:.2f}"
    )

    # Rename columns for consistency
    df_merged.rename(
        columns={
            "merged_category": "Error Type",
            "count": "Total",
            "percent": "Percent",
        },
        inplace=True,
    )

    return df_merged


def compute_test_failures_by_variant_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Compute test execution summary statistics by variant with failure type categorization.

    Args:
        df: Raw test execution data by variant

    Returns:
        DataFrame with variant-level execution statistics and percentages
    """

    # Categorize failure types
    def categorize_failure_type(failure_type):
        if pd.isna(failure_type) or failure_type is None:
            return "null"
        elif failure_type == "net.jqwik.api.TooManyFilterMissesException":
            return "TooManyFilterMissesException"
        else:
            return "other"

    df["failure_category"] = df["failure_type"].apply(categorize_failure_type)

    # Get ordered variants
    ordered_variants = (
        df[["variant", "variant_order"]]
        .drop_duplicates()
        .sort_values("variant_order")["variant"]
        .tolist()
    )

    # Calculate statistics per variant
    variant_stats = []
    for variant in ordered_variants:
        variant_data = df[df["variant"] == variant]

        # Sum up executions by category
        null_count = variant_data[variant_data["failure_category"] == "null"][
            "count"
        ].sum()
        too_many_filter_count = variant_data[
            variant_data["failure_category"] == "TooManyFilterMissesException"
        ]["count"].sum()
        other_count = variant_data[variant_data["failure_category"] == "other"][
            "count"
        ].sum()

        total_executions = null_count + too_many_filter_count + other_count

        null_pct = (null_count / total_executions * 100) if total_executions > 0 else 0
        too_many_filter_pct = (
            (too_many_filter_count / total_executions * 100)
            if total_executions > 0
            else 0
        )
        other_pct = (
            (other_count / total_executions * 100) if total_executions > 0 else 0
        )

        variant_stats.append(
            {
                "variant": variant,
                "variant_order": variant_data["variant_order"].iloc[0]
                if not variant_data.empty
                else 999,
                "total_executions": total_executions,
                "null_count": null_count,
                "null_pct": null_pct,
                "too_many_filter_count": too_many_filter_count,
                "too_many_filter_pct": too_many_filter_pct,
                "other_count": other_count,
                "other_pct": other_pct,
            }
        )

    results_df = pd.DataFrame(variant_stats)
    # Sort by variant order
    results_df = results_df.sort_values("variant_order").reset_index(drop=True)

    return results_df


def compute_test_failures_by_projects_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Compute test failure summary statistics by project with failure type categorization.

    Args:
        df: Raw test failures data by project

    Returns:
        DataFrame with project-level failure statistics and percentages
    """

    # Categorize failure types
    def categorize_failure_type(failure_type):
        if pd.isna(failure_type) or failure_type is None:
            return "null"
        elif failure_type == "net.jqwik.api.TooManyFilterMissesException":
            return "TooManyFilterMissesException"
        else:
            return "other"

    df["failure_category"] = df["failure_type"].apply(categorize_failure_type)

    # Calculate percentages per project
    project_stats = []
    for project_id, group in df.groupby("project_id"):
        project_name = group["project_name"].iloc[0]
        total_executions = group["count"].sum()

        null_count = group[group["failure_category"] == "null"]["count"].sum()
        too_many_filter_count = group[
            group["failure_category"] == "TooManyFilterMissesException"
        ]["count"].sum()
        other_count = group[group["failure_category"] == "other"]["count"].sum()

        null_pct = (null_count / total_executions * 100) if total_executions > 0 else 0
        too_many_filter_pct = (
            (too_many_filter_count / total_executions * 100)
            if total_executions > 0
            else 0
        )
        other_pct = (
            (other_count / total_executions * 100) if total_executions > 0 else 0
        )

        project_stats.append(
            {
                "project_name": project_name,
                "project_id": project_id,
                "total_executions": total_executions,
                "null_count": null_count,
                "null_pct": null_pct,
                "too_many_filter_count": too_many_filter_count,
                "too_many_filter_pct": too_many_filter_pct,
                "other_count": other_count,
                "other_pct": other_pct,
            }
        )

    results_df = pd.DataFrame(project_stats)
    # Sort by project using standard project ordering
    results_df = sort_dataframe_by_project(results_df, "project_name")

    return results_df


def compute_failure_causes_by_stage(failures_df: pd.DataFrame) -> Dict[str, str]:
    """Classify failure causes by processing stage using regex patterns.

    Args:
        failures_df: DataFrame with stage and info columns

    Returns:
        Dictionary mapping stage to formatted cause descriptions
    """
    # Define cause patterns as established in legacy notebook
    cause_patterns = pd.DataFrame(
        [
            # SETUP_PROJECT
            (
                "SETUP_PROJECT",
                r"artifacts could not be resolved",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Could not find artifact",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"PluginVersionResolutionException",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Could not resolve dependencies",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Unresolveable build extension",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Detected the following recursive expression cycle",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"must be a valid version",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"must specify an absolute path",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Could not find goal 'build-classpath' in plugin",
                "dependency resolution error",
            ),
            ("SETUP_PROJECT", r"Error injecting:", "dependency resolution error"),
            (
                "SETUP_PROJECT",
                r"No supported test framework identified",
                "sources / tests not found",
            ),
            (
                "SETUP_PROJECT",
                r"Test source path .+ does not exist.",
                "sources / tests not found",
            ),
            (
                "SETUP_PROJECT",
                r"Main source path .+ does not exist.",
                "sources / tests not found",
            ),
            # BUILD_PROJECT_ORIGINAL
            (
                "BUILD_PROJECT_ORIGINAL",
                r"teralizer.util.ConsoleCommandException",
                "compilation error",
            ),
            (
                "BUILD_PROJECT_ORIGINAL",
                r"Main compiled path .+ does not exist.",
                "compilation outputs not found",
            ),
            (
                "BUILD_PROJECT_ORIGINAL",
                r"Test compiled path .+ does not exist.",
                "compilation outputs not found",
            ),
            # BUILD_SPOON_MODEL
            (
                "BUILD_SPOON_MODEL",
                r"Modules are only available since Java 9.",
                "Spoon execution error",
            ),
            (
                "BUILD_SPOON_MODEL",
                r"The type package-info is already defined",
                "Spoon execution error",
            ),
            ("BUILD_SPOON_MODEL", None, "Spoon execution error"),
            # EXECUTE_TESTS_ORIGINAL
            (
                "EXECUTE_TESTS_ORIGINAL",
                r"teralizer.util.ConsoleCommandException",
                "JUnit execution error",
            ),
            (
                "EXECUTE_TESTS_ORIGINAL",
                r"Command execution timeout exceeded.",
                "timeout exceeded",
            ),
            # COLLECT_JUNIT_REPORTS_ORIGINAL
            (
                "COLLECT_JUNIT_REPORTS_ORIGINAL",
                r"Report directory .+ does not exist.",
                "JUnit outputs not found",
            ),
            (
                "COLLECT_JUNIT_REPORTS_ORIGINAL",
                r"Test file .+ does not exist.",
                "JUnit outputs not found",
            ),
            # BUILD_PROJECT_INSTRUMENTED
            (
                "BUILD_PROJECT_INSTRUMENTED",
                r"teralizer.util.ConsoleCommandException",
                "compilation error",
            ),
            # EXECUTE_TESTS_INITIAL
            (
                "EXECUTE_TESTS_INITIAL",
                r"All tests of the project are excluded.",
                "all tests excluded",
            ),
            (
                "EXECUTE_TESTS_INITIAL",
                r"Command execution timeout exceeded.",
                "timeout exceeded",
            ),
            # COLLECT_JACOCO_DATA_INITIAL
            (
                "COLLECT_JACOCO_DATA_INITIAL",
                r"Report file .+ does not exist.",
                "JaCoCo outputs not found",
            ),
            (
                "COLLECT_JACOCO_DATA_INITIAL",
                r"teralizer.util.ConsoleCommandException",
                "JaCoCo execution error",
            ),
            # COLLECT_PIT_DATA_INITIAL
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"Command execution timeout exceeded.",
                "timeout exceeded",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"All classes of the project are excluded.",
                "all classes excluded",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"Report file .+ does not exist.",
                "PIT outputs not found",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"teralizer.util.ConsoleCommandException",
                "PIT execution error",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"Failed to map coverage record to a test / generalization.",
                "failed to map PIT data to a test",
            ),
            # COLLECT_PIT_DATA_GENERALIZED
            (
                "COLLECT_PIT_DATA_GENERALIZED",
                r"All generalized tests of the project are excluded.",
                "all generalizations excluded",
            ),
            (
                "COLLECT_PIT_DATA_GENERALIZED",
                r"Failed to map coverage record to a test / generalization.",
                "failed to map PIT data to a generalization",
            ),
        ],
        columns=["stage", "pattern", "desc"],
    )

    # Classify each failure
    def match_cause(row):
        info = row["info"]
        for _, pat in cause_patterns.iterrows():
            # Handle null pattern: match if info is null
            if (
                pat["pattern"] is None
                and row["stage"] == pat["stage"]
                and pd.isnull(info)
            ):
                return pat["desc"]
            # Handle normal regex pattern: match if info is string and pattern is not None
            if (
                row["stage"] == pat["stage"]
                and pat["pattern"] is not None
                and isinstance(info, str)
                and re.search(pat["pattern"], info)
            ):
                return pat["desc"]
        return "other"

    failures_df["cause_desc"] = failures_df.apply(match_cause, axis=1)

    # Aggregate cause counts per stage
    stage_cause_counts = (
        failures_df.groupby(["stage", "cause_desc"])
        .size()
        .to_frame("count")
        .reset_index()
    )

    # Build stage -> "desc1 (n1), desc2 (n2), ..." mapping
    def format_causes(df):
        return ", ".join(
            f"{row['cause_desc']} ({row['count']})" for _, row in df.iterrows()
        )

    stage_to_causes = (
        stage_cause_counts.groupby("stage")[["cause_desc", "count"]]
        .apply(format_causes)
        .to_dict()
    )

    return stage_to_causes


# =============================================================================
# Generation Functions (generate_*)
# =============================================================================


def generate_exclusions_summary_table(result_df: pd.DataFrame) -> str:
    """Generate LaTeX table for overall exclusions summary.

    Args:
        result_df: DataFrame with exclusion percentages computed

    Returns:
        LaTeX table string
    """

    # Format percentages for LaTeX display
    def format_pct(val):
        return ("\\phantom{0}" if val < 10 else "") + f"{val:.1f}"

    result_df["included_pct_str"] = result_df["included_pct"].apply(format_pct)
    result_df["excluded_pct_str"] = result_df["excluded_pct"].apply(format_pct)

    # Format Included and Excluded columns as "count (pct%)"
    result_df["Included"] = result_df.apply(
        lambda row: f"{row['included_count']}\\; ({row['included_pct_str']}\\%)", axis=1
    )
    result_df["Excluded"] = result_df.apply(
        lambda row: f"{row['excluded_count']}\\; ({row['excluded_pct_str']}\\%)", axis=1
    )

    # Use variant macros for LaTeX output
    result_df = replace_variant_names_with_macros(result_df, "variant")

    # Use build_latex_table_content for consistent formatting
    columns = ["variant", "Type", "Total", "Included", "Excluded"]
    latex_content = build_latex_table_content(
        result_df[columns],
        caption="Included and excluded counts by variant and level.",
        label="tab:exclusions-summary",
        column_spec="llrrr",
        header_rows=[
            "Variant & Type & Total & \\multicolumn{1}{c}{Included} & \\multicolumn{1}{c}{Excluded} \\\\"
        ],
        add_midrules=True,
        grouping_column="Type",
    )

    return latex_content


def generate_filtering_results_table(df: pd.DataFrame, label: str, caption: str) -> str:
    """Generate LaTeX table for filtering results using shared formatting functions.

    Args:
        df: Processed filtering data
        label: LaTeX label for table
        caption: LaTeX caption for table

    Returns:
        LaTeX table string
    """

    def format_count_pct(count, pct):
        if count == 0:
            return "-"
        phantom = "\\phantom{0}" if pct < 10 else ""
        return f"{count}\\; ({phantom}{pct:.1f}\\%)"

    df["Accept"] = df.apply(
        lambda row: format_count_pct(row["accept"], row["accept_pct"]), axis=1
    )
    df["Defer"] = df.apply(
        lambda row: format_count_pct(row["defer"], row["defer_pct"]), axis=1
    )
    df["Reject"] = df.apply(
        lambda row: format_count_pct(row["reject"], row["reject_pct"]), axis=1
    )

    # Use variant macros
    df = replace_variant_names_with_macros(df, "variant")

    # Use build_latex_table_content for consistent formatting
    columns = ["variant", "Type", "filter_name", "total", "Accept", "Defer", "Reject"]
    df_display = df[columns].copy()
    df_display.columns = [
        "Variant",
        "Type",
        "Filter Name",
        "Total",
        "Accept",
        "Defer",
        "Reject",
    ]

    latex_content = build_latex_table_content(
        df_display,
        caption=caption,
        label=label,
        column_spec="lllrrrr",
        header_rows=[
            "Variant & Type & Filter Name & Total & \\multicolumn{1}{c}{Accept} & \\multicolumn{1}{c}{Defer} & \\multicolumn{1}{c}{Reject} \\\\"
        ],
        add_midrules=True,
        grouping_column="Type",
    )

    return latex_content


def generate_spf_failures_table(df_merged: pd.DataFrame) -> str:
    """Generate LaTeX table for SPF failures using shared formatting functions.

    Args:
        df_merged: Categorized SPF failures data

    Returns:
        LaTeX table string
    """
    latex_content = build_latex_table_content(
        df_merged,
        caption="Number of SPF execution failures by error type.",
        label="tab:exclusions-spf",
        column_spec="l" + "r" * (len(df_merged.columns) - 1),
        add_midrules=False,
    )

    return latex_content


def generate_test_failures_by_projects_table(results_df: pd.DataFrame) -> str:
    """Generate LaTeX table for test failures by projects.

    Args:
        results_df: DataFrame with project-level failure statistics

    Returns:
        LaTeX table string
    """

    def format_count_pct(count, pct):
        phantom = "\\phantom{0}" if pct < 10 else ""
        return f"{count}\\; ({phantom}{pct:.1f}\\%)"

    display_df = results_df.copy()

    display_df["No Error"] = display_df.apply(
        lambda row: format_count_pct(row["null_count"], row["null_pct"]), axis=1
    )
    display_df["TooManyFilterMisses"] = display_df.apply(
        lambda row: format_count_pct(
            row["too_many_filter_count"], row["too_many_filter_pct"]
        ),
        axis=1,
    )
    display_df["Inaccurate Specification"] = display_df.apply(
        lambda row: format_count_pct(row["other_count"], row["other_pct"]), axis=1
    )

    # Use project macros
    display_df = replace_project_names_with_macros(display_df, "project_name")

    columns = [
        "project_name",
        "total_executions",
        "No Error",
        "TooManyFilterMisses",
        "Inaccurate Specification",
    ]
    display_df_final = display_df[columns].copy()
    display_df_final.columns = [
        "Project",
        "Total",
        "No Error",
        "TooManyFilterMisses",
        "Inaccurate Specification",
    ]

    latex_content = build_latex_table_content(
        display_df_final,
        caption="Test execution failure analysis by project.",
        label="tab:exclusions-test-fails-by-project",
        column_spec="lrrrr",
        header_rows=[
            "Project & Total & \\multicolumn{1}{c}{No Error} & \\multicolumn{1}{c}{TooManyFilterMisses} & \\multicolumn{1}{c}{Inaccurate Specification} \\\\"
        ],
        add_midrules=True,
        grouping_column="Project",
        grouping_func=get_project_type,
    )

    return latex_content


def generate_test_failures_by_variant_table(results_df: pd.DataFrame) -> str:
    """Generate LaTeX table for test failures by variant.

    Args:
        results_df: DataFrame with variant-level failure statistics

    Returns:
        LaTeX table string
    """

    def format_count_pct(count, pct):
        phantom = "\\phantom{0}" if pct < 10 else ""
        return f"{count}\\; ({phantom}{pct:.1f}\\%)"

    display_df = results_df.copy()

    display_df["No Error"] = display_df.apply(
        lambda row: format_count_pct(row["null_count"], row["null_pct"]), axis=1
    )
    display_df["TooManyFilterMisses"] = display_df.apply(
        lambda row: format_count_pct(
            row["too_many_filter_count"], row["too_many_filter_pct"]
        ),
        axis=1,
    )
    display_df["Inaccurate Specification"] = display_df.apply(
        lambda row: format_count_pct(row["other_count"], row["other_pct"]), axis=1
    )

    # Use variant macros
    display_df = replace_variant_names_with_macros(display_df, "variant")

    columns = [
        "variant",
        "total_executions",
        "No Error",
        "TooManyFilterMisses",
        "Inaccurate Specification",
    ]
    display_df_final = display_df[columns].copy()
    display_df_final.columns = [
        "Variant",
        "Total",
        "No Error",
        "TooManyFilterMisses",
        "Inaccurate Specification",
    ]

    latex_content = build_latex_table_content(
        display_df_final,
        caption="Test execution failure analysis by variant.",
        label="tab:exclusions-test-fails-by-variant",
        column_spec="lrrrr",
        header_rows=[
            "Variant & Total & \\multicolumn{1}{c}{No Error} & \\multicolumn{1}{c}{TooManyFilterMisses} & \\multicolumn{1}{c}{Inaccurate Specification} \\\\"
        ],
        add_midrules=True,
        grouping_column="Variant",
        grouping_func=get_variant_type,
    )

    return latex_content


# =============================================================================
# CSV Generation Functions (generate_*_csv)
# =============================================================================


def generate_exclusions_summary_csv(result_df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for exclusions summary.

    Args:
        result_df: DataFrame with exclusion data

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in result_df.iterrows():
        csv_data.append(
            {
                "variant": get_variant_macro(row["variant"]),
                "exclusion_level": row["Type"],
                "total_count": int(row["Total"]),
                "included_count": int(row["included_count"]),
                "excluded_count": int(row["excluded_count"]),
                "included_percentage": float(row["included_pct"]),
                "excluded_percentage": float(row["excluded_pct"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_filtering_results_csv(df: pd.DataFrame, dataset_type: str) -> pd.DataFrame:
    """Generate CSV data for filtering results.

    Args:
        df: Filtering data
        dataset_type: Type of dataset ('main' or 'extended')

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in df.iterrows():
        csv_data.append(
            {
                "dataset": dataset_type,
                "variant": get_variant_macro(row["variant"]),
                "exclusion_level": row["Type"],
                "filter_name": row["filter_name"],
                "total_count": int(row["total"]),
                "accept_count": int(row["accept"]),
                "defer_count": int(row["defer"]),
                "reject_count": int(row["reject"]),
                "accept_percentage": float(row["accept_pct"]),
                "defer_percentage": float(row["defer_pct"]),
                "reject_percentage": float(row["reject_pct"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_spf_failures_csv(df_merged: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for SPF failures.

    Args:
        df_merged: Categorized SPF failures data

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in df_merged.iterrows():
        csv_data.append(
            {
                "error_type": row["Error Type"],
                "failure_count": int(row["Total"]),
                "percentage": float(row["Percent"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_test_failures_by_variant_csv(results_df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for test failures by variant.

    Args:
        results_df: DataFrame with variant-level failure statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in results_df.iterrows():
        csv_data.append(
            {
                "variant": get_variant_macro(row["variant"]),
                "total_executions": int(row["total_executions"]),
                "null_count": int(row["null_count"]),
                "null_percentage": float(row["null_pct"]),
                "too_many_filter_count": int(row["too_many_filter_count"]),
                "too_many_filter_percentage": float(row["too_many_filter_pct"]),
                "other_count": int(row["other_count"]),
                "other_percentage": float(row["other_pct"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_test_failures_by_projects_csv(results_df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for test failures by projects.

    Args:
        results_df: DataFrame with project-level failure statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in results_df.iterrows():
        csv_data.append(
            {
                "project_name": row["project_name"],
                "total_executions": int(row["total_executions"]),
                "null_count": int(row["null_count"]),
                "null_percentage": float(row["null_pct"]),
                "too_many_filter_count": int(row["too_many_filter_count"]),
                "too_many_filter_percentage": float(row["too_many_filter_pct"]),
                "other_count": int(row["other_count"]),
                "other_percentage": float(row["other_pct"]),
            }
        )
    return pd.DataFrame(csv_data)


# =============================================================================
# Processing Failures by Stage and Cause
# =============================================================================


def get_processing_failures_by_cause_data(conn) -> pd.DataFrame:
    """Get processing failures with individual causes from extended dataset.

    Args:
        conn: Database connection (should be extended dataset connection)

    Returns:
        DataFrame with columns: internal_stage, cause_desc, count
    """
    failures_df = get_processing_failure_causes_data(conn)
    cause_dict = compute_failure_causes_by_stage(failures_df)

    rows = []
    for stage, causes_str in cause_dict.items():
        parts = causes_str.split(", ")
        for part in parts:
            match = re.match(r"(.+?) \((\d+)\)$", part)
            if match:
                cause_desc = match.group(1)
                count = int(match.group(2))
                rows.append(
                    {"internal_stage": stage, "cause_desc": cause_desc, "count": count}
                )

    return pd.DataFrame(rows)


def compute_processing_failures_by_stage_and_cause(df: pd.DataFrame) -> pd.DataFrame:
    """Map internal stages to paper stages and aggregate causes.

    Args:
        df: DataFrame with columns: internal_stage, cause_desc, count

    Returns:
        DataFrame with columns: stage, cause, count (sorted by stage, count desc, cause)
    """
    df = df.copy()

    df["stage"] = df["internal_stage"].apply(map_internal_stage_to_paper_stage)

    unmapped = df[df["stage"].isna()]
    if not unmapped.empty:
        unmapped_stages = unmapped["internal_stage"].unique().tolist()
        raise ValueError(f"Unmapped internal stages found: {unmapped_stages}")

    df_aggregated = df.groupby(["stage", "cause_desc"], as_index=False)["count"].sum()

    other_causes = df_aggregated[df_aggregated["cause_desc"] == "other"]
    if not other_causes.empty:
        print("WARNING: Found 'other' category failures that need classification:")
        print(other_causes)

    stage_order_map = get_stage_order()
    df_aggregated["stage_order"] = df_aggregated["stage"].map(stage_order_map)

    df_sorted = df_aggregated.sort_values(
        by=["stage_order", "count", "cause_desc"], ascending=[True, False, True]
    ).reset_index(drop=True)

    df_sorted = df_sorted[["stage", "cause_desc", "count"]].rename(
        columns={"cause_desc": "cause"}
    )

    return df_sorted


def generate_processing_failures_table(df: pd.DataFrame) -> str:
    """Generate unified LaTeX table for processing failures by stage and cause.

    Args:
        df: DataFrame from compute_processing_failures_by_stage_and_cause

    Returns:
        LaTeX table string
    """
    display_df = df.copy()
    display_df.columns = ["Stage", "Cause of Failure", "Count"]

    latex_content = build_latex_table_content(
        display_df,
        caption="Processing failures by stage and cause.",
        label="tab:processing-failures",
        column_spec="llr",
        add_midrules=False,
    )

    return latex_content


def generate_processing_failures_by_cause_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for unified processing failures table.

    Args:
        df: DataFrame from compute_processing_failures_by_stage_and_cause

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in df.iterrows():
        csv_data.append(
            {
                "stage": row["stage"],
                "cause_of_failure": row["cause"],
                "count": int(row["count"]),
            }
        )
    return pd.DataFrame(csv_data)
