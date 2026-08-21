"""RQ4: Limitations - Analysis functions for filtering causes and processing failures.

This module provides functions to analyze the limitations of the test generalization
approach by examining filtering causes in the evaluation dataset and processing
pipeline failures in the extended dataset of open source projects.
"""

import pandas as pd
import re
from typing import Dict, cast
from sqlalchemy import text

from .formatting import (
    replace_variant_names_with_macros,
    replace_project_names_with_macros,
    build_latex_table_content,
    sort_dataframe_by_project,
)
from .exports import get_variant_macro, get_project_type
from .stages import map_internal_stage_to_paper_stage, get_stage_order
from .exclusions import get_excluded_project_ids, is_project_excluded


# =============================================================================
# Helper Functions
# =============================================================================


def categorize_failure_type(cause: str) -> str:
    """Categorize failure as internal or external to Teralizer.

    Internal failures: Things Teralizer can fix/improve
    External failures: Things outside Teralizer's control

    Args:
        cause: Failure cause description

    Returns:
        "Internal" or "External"
    """
    # External: Infrastructure/tool execution errors
    # Compilation errors are external in both contexts:
    # - BUILD_PROJECT_ORIGINAL: Project can't compile in original state
    # - BUILD_PROJECT_INSTRUMENTED: Spoon bug where it recognizes a specific static
    #   import is needed (org.mockito.Matchers.any) but fails to write it when
    #   generating instrumented test files, causing compilation failure
    external_causes = {
        "JUnit execution error",
        "Spoon execution error",
        "JaCoCo execution error",
        "PIT execution error",
        "compilation error",
    }

    if cause in external_causes:
        return "External"

    # Internal: Filtering outcomes (can improve filters/support)
    filtering_causes = {
        "all tests excluded",
        "all assertions excluded",
        "no generalizations created",
        "all generalizations excluded",
        "all classes excluded",
    }

    if cause in filtering_causes:
        return "Internal"

    # Internal: Project structure detection issues
    structure_causes = {
        "JUnit outputs not found",
        "compilation outputs not found",
        "JaCoCo outputs not found",
        "PIT outputs not found",
    }

    if cause in structure_causes:
        return "Internal"

    # Internal: Data mapping failures (Teralizer bugs)
    data_mapping_causes = {
        "failed to map PIT data to a generalization",
        "failed to map PIT data to a test",
    }

    if cause in data_mapping_causes:
        return "Internal"

    # Internal: Timeouts (can optimize or increase limits)
    if cause == "timeout exceeded":
        return "Internal"

    # If we get here, the cause is not properly categorized
    raise ValueError(
        f"Unclassified failure cause: '{cause}'. "
        "All failure causes must be explicitly categorized as Internal or External."
    )


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

    This function queries filter results directly and applies project exclusions
    to exclude projects with basic setup failures (dependency errors, missing
    sources, zero coverage, compilation errors).

    IMPORTANT: This replaces the old mv_exclusions_filtering materialized view
    which did not apply project exclusions. The view excluded some tests that
    should have been filtered out.

    Args:
        conn: Database connection

    Returns:
        DataFrame with filtering results by variant, level, and filter
    """
    # Get projects to exclude (those with setup failures)
    excluded_ids = get_excluded_project_ids(conn)

    # Build exclusion clause - only add if there are projects to exclude
    # (main dataset has no exclusions, extended dataset excludes 529 projects)
    if excluded_ids:
        excluded_ids_str = ",".join(str(id) for id in excluded_ids)
        exclusion_clause = f"AND p.id NOT IN ({excluded_ids_str})"
    else:
        exclusion_clause = ""

    # Query filter results with exclusions applied
    # This replicates the logic from mv_exclusions_filtering but adds
    # the exclusion filter for datasets that have setup failures
    query = text(f"""
        WITH
            base_data AS (
                SELECT
                    coalesce(g.variant, 'SHARED') AS variant,
                    CASE
                        WHEN fr.test_id IS NOT NULL THEN '1-TEST'
                        WHEN fr.assertion_id IS NOT NULL THEN '2-ASSERTION'
                        WHEN fr.generalization_id IS NOT NULL THEN '3-GENERALIZATION'
                    END AS level,
                    substring(fr.filter_name from 'filter\\.(\\w+)Filter$') AS filter_name,
                    fr.decision,
                    count(*) AS count
                FROM filter_result fr
                JOIN project p ON fr.project_id = p.id
                LEFT JOIN generalization g ON fr.generalization_id = g.id
                WHERE p.use_test_generalization
                  {exclusion_clause}
                GROUP BY
                    g.variant,
                    fr.filter_name,
                    CASE
                        WHEN fr.test_id IS NOT NULL THEN '1-TEST'
                        WHEN fr.assertion_id IS NOT NULL THEN '2-ASSERTION'
                        WHEN fr.generalization_id IS NOT NULL THEN '3-GENERALIZATION'
                    END,
                    fr.decision
            ),
            pivoted AS (
                SELECT
                    variant,
                    level,
                    filter_name,
                    SUM(count) AS total,
                    SUM(CASE WHEN decision = 'ACCEPT' THEN count ELSE 0 END) AS accept,
                    SUM(CASE WHEN decision = 'REJECT' THEN count ELSE 0 END) AS reject,
                    SUM(CASE WHEN decision = 'DEFER' THEN count ELSE 0 END) AS defer
                FROM base_data
                GROUP BY
                    variant,
                    level,
                    filter_name
            )
        SELECT
            variant,
            level,
            filter_name,
            total,
            accept,
            reject,
            defer
        FROM pivoted
        WHERE reject > 0
    """)

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

    # Sort for deterministic output: level groups rows by Type, then filter and variant
    df = df.sort_values(["level", "filter_name", "variant"]).reset_index(drop=True)

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
        "OutOfMemoryError: Java heap space": "OutOfMemoryError",
        "OutOfMemoryError: GC overhead": "OutOfMemoryError",
    }

    # Apply the mapping, keeping unmapped categories as is
    df["merged_category"] = (
        df["error_category"].map(category_map).fillna(df["error_category"])
    )

    # Group by merged_category and sum the counts
    df_merged = cast(
        pd.DataFrame, df.groupby("merged_category", as_index=False)["count"].sum()
    )

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
        cast(pd.DataFrame, df[["variant", "variant_order"]])
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


def compute_exclusion_breakdown_filtering_vs_failures(
    conn, df: pd.DataFrame
) -> pd.DataFrame:
    """Compute exclusion breakdown split into Filtering vs Failures.

    Distinguishes between proactive filtering (filter rejections with REJECT decisions)
    and reactive failures (exceptions during filtering without REJECT decisions).

    Args:
        conn: Database connection to query filter_result table
        df: Raw exclusions data from mv_exclusions_all

    Returns:
        DataFrame with columns: variant, Type, Total, Included, Filtering, Failures
    """
    # Map level to Type
    level_map = {
        "1-TEST": "Test",
        "2-ASSERTION": "Assertion",
        "3-GENERALIZATION": "Generalization",
    }
    df["Type"] = df["level"].map(level_map)

    # Controlled corpora have no project-level exclusion records, so this is empty there.
    excluded_project_ids = get_excluded_project_ids(conn)

    # Build exclusion clause
    if excluded_project_ids:
        excluded_ids_str = ",".join(str(id) for id in excluded_project_ids)
        exclusion_clause = f"AND p.id NOT IN ({excluded_ids_str})"
    else:
        exclusion_clause = ""

    # For items excluded by TestFilteringTask, we need to check if they have REJECT decisions
    # Query which tests/assertions excluded by TestFilteringTask have REJECT decisions
    test_reject_query = f"""
        SELECT DISTINCT t.id
        FROM test t
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        {exclusion_clause}
        AND t.is_included = false
        AND t.exclusion_info LIKE '%TestFilteringTask%'
        AND EXISTS (
            SELECT 1 FROM filter_result fr
            WHERE fr.test_id = t.id AND fr.decision = 'REJECT'
        )
    """

    assertion_reject_query = f"""
        SELECT DISTINCT a.id
        FROM assertion a
        JOIN test t ON a.test_id = t.id
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        {exclusion_clause}
        AND a.is_included = false
        AND a.exclusion_info LIKE '%TestFilteringTask%'
        AND EXISTS (
            SELECT 1 FROM filter_result fr
            WHERE fr.assertion_id = a.id AND fr.decision = 'REJECT'
        )
    """

    tests_with_reject = set(pd.read_sql_query(text(test_reject_query), conn)["id"])
    assertions_with_reject = set(
        pd.read_sql_query(text(assertion_reject_query), conn)["id"]
    )

    # Query the actual excluded items to categorize them properly
    excluded_tests_query = f"""
        SELECT
            t.id,
            t.is_included,
            t.exclusion_info
        FROM test t
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        {exclusion_clause}
    """

    excluded_assertions_query = f"""
        SELECT
            a.id,
            a.is_included,
            a.exclusion_info
        FROM assertion a
        JOIN test t ON a.test_id = t.id
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        {exclusion_clause}
    """

    tests_df = pd.read_sql_query(text(excluded_tests_query), conn)
    assertions_df = pd.read_sql_query(text(excluded_assertions_query), conn)

    # Categorize exclusion reasons for tests
    def categorize_test_exclusion(row):
        if row["is_included"]:
            return "Included"
        elif "TestFilteringTask" in str(row["exclusion_info"]):
            # Check if this test has a REJECT decision
            if row["id"] in tests_with_reject:
                return "Filtering"  # Proactive filter rejection
            else:
                return "Failures"  # Exception during filtering
        else:
            return "Failures"  # JpfExecutionTask, TestAnalysisTask, etc.

    def categorize_assertion_exclusion(row):
        if row["is_included"]:
            return "Included"
        elif "TestFilteringTask" in str(row["exclusion_info"]):
            # Check if this assertion has a REJECT decision
            if row["id"] in assertions_with_reject:
                return "Filtering"  # Proactive filter rejection
            else:
                return "Failures"  # Exception during filtering
        else:
            return "Failures"  # JpfExecutionTask, TestAnalysisTask, etc.

    tests_df["exclusion_type"] = tests_df.apply(categorize_test_exclusion, axis=1)
    assertions_df["exclusion_type"] = assertions_df.apply(
        categorize_assertion_exclusion, axis=1
    )

    # Count exclusions by type for test level
    test_counts = (
        tests_df.groupby("exclusion_type").size().rename("count").reset_index()
    )
    test_counts["variant"] = "SHARED"  # Tests belong to SHARED variant
    test_counts["Type"] = "Test"

    # Count exclusions by type for assertion level
    assertion_counts = (
        assertions_df.groupby("exclusion_type").size().rename("count").reset_index()
    )
    assertion_counts["variant"] = "SHARED"  # Assertions belong to SHARED variant
    assertion_counts["Type"] = "Assertion"

    # For generalizations, use the original df logic (no filtering exceptions at that level)
    gen_df = df[df["Type"] == "Generalization"].copy()

    def categorize_exclusion_reason(excluded_by):
        if pd.isna(excluded_by) or excluded_by is None:
            return "Included"
        elif excluded_by == "TestFilteringTask":
            return "Filtering"
        else:
            return "Failures"

    gen_df["exclusion_type"] = gen_df["excluded_by"].apply(categorize_exclusion_reason)
    gen_counts = (
        gen_df.groupby(["variant", "Type", "exclusion_type"])["count"]
        .sum()
        .reset_index()
    )

    # Combine all counts
    combined = pd.concat([test_counts, assertion_counts, gen_counts], ignore_index=True)

    # Get unique (variant, Type) pairs from combined data
    ordered_pairs = combined[["variant", "Type"]].drop_duplicates()

    # Calculate counts by exclusion type
    included = (
        combined[combined["exclusion_type"] == "Included"]
        .groupby(["variant", "Type"])["count"]
        .sum()
        .reset_index()
    )
    filtering = (
        combined[combined["exclusion_type"] == "Filtering"]
        .groupby(["variant", "Type"])["count"]
        .sum()
        .reset_index()
    )
    failures = (
        combined[combined["exclusion_type"] == "Failures"]
        .groupby(["variant", "Type"])["count"]
        .sum()
        .reset_index()
    )

    included = included.rename(columns={"count": "included_count"})
    filtering = filtering.rename(columns={"count": "filtering_count"})
    failures = failures.rename(columns={"count": "failures_count"})

    # Merge all counts
    result = pd.merge(ordered_pairs, included, on=["variant", "Type"], how="left")
    result = pd.merge(result, filtering, on=["variant", "Type"], how="left")
    result = pd.merge(result, failures, on=["variant", "Type"], how="left")

    # Fill NaNs with 0 and ensure integer type
    result["included_count"] = result["included_count"].fillna(0).astype(int)
    result["filtering_count"] = result["filtering_count"].fillna(0).astype(int)
    result["failures_count"] = result["failures_count"].fillna(0).astype(int)

    # Compute Total column
    result["Total"] = (
        result["included_count"] + result["filtering_count"] + result["failures_count"]
    )

    # Compute percentages relative to Total
    result["included_pct"] = (result["included_count"] / result["Total"] * 100).round(1)
    result["filtering_pct"] = (result["filtering_count"] / result["Total"] * 100).round(
        1
    )
    result["failures_pct"] = (result["failures_count"] / result["Total"] * 100).round(1)

    return result


def generate_exclusions_breakdown_table(
    result_df: pd.DataFrame, label: str, caption: str
) -> str:
    """Generate LaTeX table for exclusions breakdown with Filtering vs Failures split.

    Args:
        result_df: DataFrame from compute_exclusion_breakdown_filtering_vs_failures
        label: LaTeX label for the table
        caption: LaTeX caption for the table

    Returns:
        LaTeX table string
    """

    # Determine max percentage values to calculate phantom spacing
    max_included_pct = result_df["included_pct"].max()
    max_filtering_pct = result_df["filtering_pct"].max()
    max_failures_pct = result_df["failures_pct"].max()

    # Helper function to format count with percentage and phantom spacing
    def format_count_pct_with_phantom(count, pct, max_count_digits, max_pct):
        # Calculate phantom spacing for count alignment
        count_str = f"{count:,}"
        count_digits = len(str(count).replace(",", ""))
        phantom_count = (
            "\\phantom{"
            + ("0," * ((max_count_digits - count_digits + 1) // 2))
            + ("0" * ((max_count_digits - count_digits) % 2))
            + "}"
        )

        # Phantom spacing for percentage based on actual max value
        if max_pct >= 100:
            # Need space for 3 digits (100.0)
            if pct >= 100:
                phantom_pct = ""
            elif pct >= 10:
                phantom_pct = "\\phantom{0}"
            else:
                phantom_pct = "\\phantom{00}"
        else:
            # Max is < 100, only need space for 2 digits
            if pct >= 10:
                phantom_pct = ""
            else:
                phantom_pct = "\\phantom{0}"

        return f"{phantom_count}{count_str}\\; ({phantom_pct}{pct:.1f}\\%)"

    # Determine max digits for each column for phantom spacing
    max_included_digits = len(str(result_df["included_count"].max()))
    max_filtering_digits = len(str(result_df["filtering_count"].max()))
    max_failures_digits = len(str(result_df["failures_count"].max()))

    # Format columns
    result_df["Total_formatted"] = result_df["Total"].apply(lambda x: f"{x:,}")
    result_df["Included"] = result_df.apply(
        lambda row: format_count_pct_with_phantom(
            row["included_count"],
            row["included_pct"],
            max_included_digits,
            max_included_pct,
        ),
        axis=1,
    )
    result_df["Filtering"] = result_df.apply(
        lambda row: format_count_pct_with_phantom(
            row["filtering_count"],
            row["filtering_pct"],
            max_filtering_digits,
            max_filtering_pct,
        ),
        axis=1,
    )
    result_df["Failures"] = result_df.apply(
        lambda row: format_count_pct_with_phantom(
            row["failures_count"],
            row["failures_pct"],
            max_failures_digits,
            max_failures_pct,
        ),
        axis=1,
    )

    # Use variant macros for LaTeX output
    result_df = replace_variant_names_with_macros(result_df, "variant")

    # Prepare display columns
    columns = [
        "variant",
        "Type",
        "Total_formatted",
        "Included",
        "Filtering",
        "Failures",
    ]
    df_display = result_df[columns].copy()
    df_display.columns = [
        "Variant",
        "Type",
        "Total",
        "Included",
        "Filtering",
        "Failures",
    ]

    # Create multi-level header rows
    header_rows = [
        "& & & & \\multicolumn{2}{r}{Excluded} \\\\",
        "\\cmidrule(lr){5-6}",
        "Variant & Type & Total & Included & Filtering & Failures \\\\",
    ]

    latex_content = build_latex_table_content(
        cast(pd.DataFrame, df_display),
        caption=caption,
        label=label,
        column_spec="llrrrr",
        header_rows=header_rows,
        add_midrules=True,
        grouping_column="Type",
    )

    return latex_content


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
        lambda row: f"{row['included_count']:,}\\; ({row['included_pct_str']}\\%)",
        axis=1,
    )
    result_df["Excluded"] = result_df.apply(
        lambda row: f"{row['excluded_count']:,}\\; ({row['excluded_pct_str']}\\%)",
        axis=1,
    )

    # Format Total column with thousands separators for LaTeX display
    result_df["Total_formatted"] = result_df["Total"].apply(lambda x: f"{x:,}")

    # Use variant macros for LaTeX output
    result_df = replace_variant_names_with_macros(result_df, "variant")

    # Use build_latex_table_content for consistent formatting
    columns = ["variant", "Type", "Total_formatted", "Included", "Excluded"]
    df_display = result_df[columns].copy()
    df_display.columns = ["variant", "Type", "Total", "Included", "Excluded"]

    latex_content = build_latex_table_content(
        cast(pd.DataFrame, df_display),
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
        return f"{count:,}\\; ({phantom}{pct:.1f}\\%)"

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

    # Format total column with thousands separators
    df["total"] = df["total"].apply(lambda x: f"{x:,}")

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
        cast(pd.DataFrame, df_display),
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
    # Format Total column with thousands separators for LaTeX display
    df_display = df_merged.copy()
    df_display["Total"] = df_display["Total"].apply(lambda x: f"{x:,}")

    latex_content = build_latex_table_content(
        df_display,
        caption="Number of SPF execution failures by error type.",
        label="tab:exclusions-spf",
        column_spec="l" + "r" * (len(df_display.columns) - 1),
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
        return f"{count:,}\\; ({phantom}{pct:.1f}\\%)"

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

    # Format total_executions with thousands separators
    display_df["total_executions"] = display_df["total_executions"].apply(
        lambda x: f"{x:,}"
    )

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
        cast(pd.DataFrame, display_df_final),
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
        return f"{count:,}\\; ({phantom}{pct:.1f}\\%)"

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

    # Format total_executions with thousands separators
    display_df["total_executions"] = display_df["total_executions"].apply(
        lambda x: f"{x:,}"
    )

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
        cast(pd.DataFrame, display_df_final),
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


def generate_exclusions_breakdown_csv(result_df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for exclusions breakdown with Filtering vs Failures split.

    Args:
        result_df: DataFrame with exclusion breakdown data

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
                "filtering_count": int(row["filtering_count"]),
                "failures_count": int(row["failures_count"]),
                "included_percentage": float(row["included_pct"]),
                "filtering_percentage": float(row["filtering_pct"]),
                "failures_percentage": float(row["failures_pct"]),
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

    Filters out projects that fail basic setup requirements (excluded projects).

    Performs root cause analysis for "all generalizations excluded" failures:
    - If project has 0 generalizations created → "no generalizations created" (Stage 3 failure)
    - If project has generalizations but all excluded → "all generalizations excluded" (Stage 4 failure)

    Args:
        conn: Database connection (should be extended dataset connection)

    Returns:
        DataFrame with columns: internal_stage, cause_desc, count
    """
    excluded_ids = get_excluded_project_ids(conn)

    query = """
        SELECT pf.project_id, pf.stage, pf.info
        FROM v_project_failures pf
    """
    failures_df = pd.read_sql_query(text(query), conn)

    # Filter out excluded projects
    failures_df = failures_df[~failures_df["project_id"].isin(excluded_ids)].copy()

    # Identify projects with "all generalizations excluded" failure
    gen_excluded_projects = failures_df[
        (failures_df["stage"] == "COLLECT_PIT_DATA_GENERALIZED")
        & (
            failures_df["info"].str.contains(
                "All generalized tests of the project are excluded.", na=False
            )
        )
    ]["project_id"].unique()

    if len(gen_excluded_projects) > 0:
        # Query which of these projects have 0 generalizations created
        # AND which have assertions that passed ALL filters
        ids_str = ",".join(str(pid) for pid in gen_excluded_projects)
        gen_assertion_query = f"""
            WITH included_assertions AS (
                SELECT DISTINCT a.id, t.project_id
                FROM assertion a
                JOIN test t ON t.id = a.test_id
                WHERE t.project_id IN ({ids_str})
                AND a.is_included = true
            )
            SELECT
                p.id as project_id,
                COUNT(DISTINCT g.id) as generalization_count,
                COUNT(DISTINCT ia.id) as included_assertion_count
            FROM project p
            LEFT JOIN generalization g ON g.project_id = p.id
            LEFT JOIN included_assertions ia ON ia.project_id = p.id
            WHERE p.id IN ({ids_str})
            GROUP BY p.id
        """
        counts = pd.read_sql_query(text(gen_assertion_query), conn)

        # Map project IDs to their counts
        project_gen_counts = dict(
            zip(counts["project_id"], counts["generalization_count"])
        )
        project_assertion_counts = dict(
            zip(counts["project_id"], counts["included_assertion_count"])
        )

        # Add columns to mark which failures should be reclassified
        failures_df["has_generalizations"] = failures_df["project_id"].map(
            project_gen_counts
        )
        failures_df["has_included_assertions"] = failures_df["project_id"].map(
            project_assertion_counts
        )
    else:
        failures_df["has_generalizations"] = None
        failures_df["has_included_assertions"] = None

    # Classify failure causes using existing logic
    cause_dict = compute_failure_causes_by_stage(failures_df[["stage", "info"]].copy())

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

    result_df = pd.DataFrame(rows)

    # Split "all generalizations excluded" by root cause into THREE categories
    if "all generalizations excluded" in result_df["cause_desc"].values:
        gen_excluded_rows = failures_df[
            (failures_df["stage"] == "COLLECT_PIT_DATA_GENERALIZED")
            & (
                failures_df["info"].str.contains(
                    "All generalized tests of the project are excluded.", na=False
                )
            )
        ]

        # Category 1: No assertions are included
        # Need to determine WHERE assertions were excluded: proactive vs reactive
        no_assertions_projects = gen_excluded_rows[
            gen_excluded_rows["has_included_assertions"] == 0
        ]["project_id"].tolist()

        if no_assertions_projects:
            # Query assertion exclusions to determine proactive vs reactive
            ids_str = ",".join(str(pid) for pid in no_assertions_projects)
            exclusion_query = f"""
                SELECT
                    t.project_id,
                    SUM(CASE WHEN a.exclusion_info LIKE '%FILTER_ASSERTIONS%' THEN 1 ELSE 0 END) as filter_assertions_count,
                    SUM(CASE WHEN a.exclusion_info LIKE '%EXECUTE_JPF%' OR a.exclusion_info LIKE '%JpfExecutionTask%' THEN 1 ELSE 0 END) as execute_jpf_count,
                    COUNT(*) as total_excluded_assertions
                FROM assertion a
                JOIN test t ON t.id = a.test_id
                WHERE t.project_id IN ({ids_str})
                AND a.is_included = false
                GROUP BY t.project_id
            """
            exclusion_counts = pd.read_sql_query(text(exclusion_query), conn)

            # Categorize projects:
            # - Proactive: ALL assertions excluded at FILTER_ASSERTIONS → Stage "1 + 2"
            # - Reactive: ANY assertions excluded at EXECUTE_JPF (including mixed) → Stage 3
            proactive_projects = exclusion_counts[
                (exclusion_counts["filter_assertions_count"] > 0)
                & (exclusion_counts["execute_jpf_count"] == 0)
            ]
            reactive_projects = exclusion_counts[
                exclusion_counts["execute_jpf_count"] > 0
            ]

            proactive_count = len(proactive_projects)
            reactive_count = len(reactive_projects)
        else:
            proactive_count = 0
            reactive_count = 0

        # Category 2: Assertions included but no generalizations created
        # This should be 0 - if assertions are included (is_included=true), generalizations
        # should have been created. This is a sanity check for pipeline correctness.
        assertions_but_no_gens_count = (
            (gen_excluded_rows["has_included_assertions"] > 0)
            & (gen_excluded_rows["has_generalizations"] == 0)
        ).sum()

        # Category 3: Generalizations created but all excluded
        all_gens_excluded_count = (gen_excluded_rows["has_generalizations"] > 0).sum()

        # Remove the aggregated "all generalizations excluded" row
        result_df = result_df[
            result_df["cause_desc"] != "all generalizations excluded"
        ].copy()

        # Add split rows
        if proactive_count > 0:
            result_df = pd.concat(
                [
                    result_df,
                    pd.DataFrame(
                        [
                            {
                                "internal_stage": "FILTER_ASSERTIONS",
                                "cause_desc": "all assertions excluded",
                                "count": proactive_count,
                            }
                        ]
                    ),
                ],
                ignore_index=True,
            )

        if reactive_count > 0:
            result_df = pd.concat(
                [
                    result_df,
                    pd.DataFrame(
                        [
                            {
                                "internal_stage": "EXECUTE_JPF",
                                "cause_desc": "all assertions excluded",
                                "count": reactive_count,
                            }
                        ]
                    ),
                ],
                ignore_index=True,
            )

        if assertions_but_no_gens_count > 0:
            result_df = pd.concat(
                [
                    result_df,
                    pd.DataFrame(
                        [
                            {
                                "internal_stage": "COLLECT_PIT_DATA_GENERALIZED",
                                "cause_desc": "no generalizations created",
                                "count": assertions_but_no_gens_count,
                            }
                        ]
                    ),
                ],
                ignore_index=True,
            )

        if all_gens_excluded_count > 0:
            result_df = pd.concat(
                [
                    result_df,
                    pd.DataFrame(
                        [
                            {
                                "internal_stage": "COLLECT_PIT_DATA_GENERALIZED",
                                "cause_desc": "all generalizations excluded",
                                "count": all_gens_excluded_count,
                            }
                        ]
                    ),
                ],
                ignore_index=True,
            )

    return cast(pd.DataFrame, result_df)


def compute_processing_failures_by_stage_and_cause(df: pd.DataFrame) -> pd.DataFrame:
    """Map internal stages to paper stages and aggregate causes.

    Filters out excluded failure causes (dependency resolution error,
    sources/tests not found, compilation error in BUILD_PROJECT_ORIGINAL).
    Adds categorization of failures as Internal or External to Teralizer.

    Args:
        df: DataFrame with columns: internal_stage, cause_desc, count

    Returns:
        DataFrame with columns: stage, type, cause, count (sorted by stage, count desc, cause)
    """
    df = df.copy()

    df["stage"] = df["internal_stage"].apply(map_internal_stage_to_paper_stage)

    unmapped = df[df["stage"].isna()]
    if not unmapped.empty:
        unmapped_stages = unmapped["internal_stage"].unique().tolist()
        raise ValueError(f"Unmapped internal stages found: {unmapped_stages}")

    # Filter out excluded causes
    # These should already be filtered by get_processing_failures_by_cause_data,
    # but we ensure they don't appear in the final output
    df = df[
        ~df.apply(
            lambda row: is_project_excluded(row["internal_stage"], row["cause_desc"]),
            axis=1,
        )
    ]

    df_aggregated = df.groupby(["stage", "cause_desc"], as_index=False)["count"].sum()

    other_causes = df_aggregated[df_aggregated["cause_desc"] == "other"]
    if not other_causes.empty:
        print("WARNING: Found 'other' category failures that need classification:")
        print(other_causes)

    # Special case: "all tests excluded" is detected in Stage 3 (EXECUTE_TESTS_INITIAL)
    # but should be attributed to Stage 1+2 where test filtering occurs
    # Database evidence shows tests are excluded at FILTER_TESTS_ORIGINAL, FILTER_TESTS,
    # or COLLECT_JUNIT_REPORTS_ORIGINAL - all of which map to Paper Stage "1 + 2"
    # The failure is only detected later when EXECUTE_TESTS_INITIAL tries to run tests
    df_aggregated.loc[
        (df_aggregated["stage"] == "3")
        & (df_aggregated["cause_desc"] == "all tests excluded"),
        "stage",
    ] = "1 + 2"

    # Special case: "all assertions excluded" needs stage attribution based on WHERE exclusions occurred
    # Now handled upstream by setting internal_stage to FILTER_ASSERTIONS or EXECUTE_JPF
    # FILTER_ASSERTIONS maps to Stage "1 + 2" (proactive filtering)
    # EXECUTE_JPF maps to Stage 3 (reactive SPF failures)
    # No special handling needed here - stage mapping happens automatically via map_internal_stage_to_paper_stage

    # Special case: "no generalizations created" is detected in Stage 5
    # Root cause needs investigation: assertions passed filters AND SPF succeeded, but no generalizations created
    # Could be Stage 3 (specification extraction) or Stage 4 (GENERALIZE_TESTS) failure
    # Attributing to Stage 3 for now as the most likely point of failure
    df_aggregated.loc[
        (df_aggregated["stage"] == "5")
        & (df_aggregated["cause_desc"] == "no generalizations created"),
        "stage",
    ] = "3"

    # Special case: "all generalizations excluded" is detected in Stage 5
    # but should be attributed to Stage 4 (where FILTER_GENERALIZATIONS runs - see stages.py line 66)
    df_aggregated.loc[
        (df_aggregated["stage"] == "5")
        & (df_aggregated["cause_desc"] == "all generalizations excluded"),
        "stage",
    ] = "4"

    # Add Internal/External/Mixed categorization
    df_aggregated["type"] = df_aggregated["cause_desc"].apply(categorize_failure_type)

    # Stage-specific type overrides:
    # "all assertions excluded" in Stage 3 is Mixed (SPF errors, Teralizer errors, resource limits)
    # "all assertions excluded" in Stage "1 + 2" is Internal (proactive filtering)
    df_aggregated.loc[
        (df_aggregated["stage"] == "3")
        & (df_aggregated["cause_desc"] == "all assertions excluded"),
        "type",
    ] = "Mixed"

    # Note: "all tests excluded" was moved to Stage "1 + 2", so Stage 3 timeouts
    # are no longer merged. Timeouts during test execution (Stage 3) are different
    # from test filtering issues (Stage "1 + 2").

    # Merge Stage 5 "failed to map PIT data" causes into "failed to process PIT reports"
    stage_5_mapping_failures = df_aggregated[
        (df_aggregated["stage"] == "5")
        & (
            df_aggregated["cause_desc"].isin(
                [
                    "failed to map PIT data to a generalization",
                    "failed to map PIT data to a test",
                ]
            )
        )
    ]
    if not stage_5_mapping_failures.empty:
        mapping_count = stage_5_mapping_failures["count"].sum()
        # Add new row for "failed to process PIT reports"
        new_row = pd.DataFrame(
            [
                {
                    "stage": "5",
                    "cause_desc": "failed to process PIT reports",
                    "count": mapping_count,
                    "type": "Internal",
                }
            ]
        )
        df_aggregated = pd.concat([df_aggregated, new_row], ignore_index=True)
        # Remove the old mapping failure rows
        df_aggregated = df_aggregated[
            ~(
                (df_aggregated["stage"] == "5")
                & (
                    df_aggregated["cause_desc"].isin(
                        [
                            "failed to map PIT data to a generalization",
                            "failed to map PIT data to a test",
                        ]
                    )
                )
            )
        ].copy()

    stage_order_map = get_stage_order()
    df_aggregated["stage_order"] = df_aggregated["stage"].map(stage_order_map)

    df_sorted = (
        cast(pd.DataFrame, df_aggregated)
        .sort_values(
            by=["stage_order", "count", "cause_desc"], ascending=[True, False, True]
        )
        .reset_index(drop=True)
    )

    df_sorted = df_sorted[["stage", "type", "cause_desc", "count"]].rename(
        columns={"cause_desc": "cause"}
    )

    return df_sorted


def translate_cause_for_display(stage: str, cause: str) -> str:
    """Translate base cause descriptions to detailed, stage-aware display text.

    Args:
        stage: Paper stage (e.g., "1 + 2", "3", "4", "5")
        cause: Base cause description

    Returns:
        Detailed cause description with LaTeX macros
    """
    # Stage-specific translations
    translations = {
        ("1 + 2", "timeout exceeded"): "timeout exceeded (60 seconds per project)",
        ("1 + 2", "JUnit outputs not found"): "JUnit reports not found",
        ("1 + 2", "compilation outputs not found"): "compilation outputs not found",
        (
            "1 + 2",
            "JUnit execution error",
        ): "JUnit execution error during test execution",
        (
            "1 + 2",
            "Spoon execution error",
        ): "Spoon execution error during test analysis",
        ("1 + 2", "all tests excluded"): "all tests excluded (filter rejections)",
        (
            "1 + 2",
            "all assertions excluded",
        ): "all assertions excluded (filter rejections)",
        (
            "3",
            "compilation error",
        ): "Spoon execution error during test instrumentation",
        ("3", "timeout exceeded"): "timeout exceeded (60 seconds per test method)",
        (
            "3",
            "all assertions excluded",
        ): "all assertions excluded (\\ToolSPF{} errors, \\ToolTeralizer{} errors, resource limits exceeded)",
        (
            "3",
            "no generalizations created",
        ): "no generalizations created (specification extraction or generalization creation failure)",
        (
            "4",
            "all generalizations excluded",
        ): "all generalizations excluded (filter rejections)",
        ("5", "JaCoCo outputs not found"): "JaCoCo outputs not found",
        (
            "5",
            "timeout exceeded",
        ): "timeout exceeded (60 seconds per project)",
        (
            "5",
            "PIT execution error",
        ): "\\ToolPit{} execution error during mutation testing",
        ("5", "PIT outputs not found"): "\\ToolPit{} reports not found",
        ("5", "all classes excluded"): "all classes excluded",
        ("5", "failed to process PIT reports"): "failed to process \\ToolPit{} reports",
        (
            "5",
            "JaCoCo execution error",
        ): "JaCoCo execution error during coverage collection",
    }

    # Return translated version or original if no translation exists
    return translations.get((stage, cause), cause)


def generate_processing_failures_table(
    df: pd.DataFrame, total_eligible: int, success_count: int
) -> str:
    """Generate unified LaTeX table for processing failures by stage and cause.

    Creates a table with stage summary headers showing project counts and pass rates,
    followed by detailed failure causes with Internal/External categorization.

    Args:
        df: DataFrame from compute_processing_failures_by_stage_and_cause
                with columns: stage, type, cause, count
        total_eligible: Total number of eligible projects in the evaluation
        success_count: Number of projects that completed all stages successfully

    Returns:
        LaTeX table string
    """
    # Calculate stage progression statistics
    stage_failures = df.groupby("stage")["count"].sum().to_dict()

    # Calculate how many projects reached each stage (funnel effect)
    stage_entering = {
        "1 + 2": total_eligible,
        "3": total_eligible - stage_failures.get("1 + 2", 0),
        "4": total_eligible
        - stage_failures.get("1 + 2", 0)
        - stage_failures.get("3", 0),
        "5": total_eligible
        - stage_failures.get("1 + 2", 0)
        - stage_failures.get("3", 0)
        - stage_failures.get("4", 0),
    }

    # Build table rows with summary headers
    table_rows = []

    # Stage name mapping for headers
    stage_names = {
        "1 + 2": "Stage 1 + 2 - Project Analysis:",
        "3": "Stage 3 - Specification Extraction:",
        "4": "Stage 4 - Generalized Test Creation:",
        "5": "Stage 5 - Test Suite Reduction:",
    }

    for stage in ["1 + 2", "3", "4", "5"]:
        entering = stage_entering[stage]
        failures = stage_failures.get(stage, 0)
        passing = entering - failures
        pass_pct = (passing / entering * 100) if entering > 0 else 0

        # Format numbers with phantom digits for alignment
        entering_str = str(entering)
        passing_str = f"\\phantom{{0}}{passing}" if passing < 100 else str(passing)
        failures_str = (
            f"\\phantom{{00}}{failures}"
            if failures < 10
            else (f"\\phantom{{0}}{failures}" if failures < 100 else str(failures))
        )
        pass_pct_str = (
            f"\\phantom{{0}}{pass_pct:.1f}" if pass_pct < 10 else f"{pass_pct:.1f}"
        )

        # Add stage summary header with aligned numbers
        stage_title = stage_names[stage]
        summary_text = f"\\textit{{\\makebox[16em][l]{{{stage_title}}} {entering_str} projects\\quad{{}}{passing_str} pass\\quad{{}}{failures_str} fail\\quad{{}}{pass_pct_str}\\% pass rate}}"
        table_rows.append(f"\\multicolumn{{3}}{{l}}{{{summary_text}}} \\\\")
        table_rows.append("\\midrule")

        # Add failure detail rows for this stage
        stage_data = df[df["stage"] == stage]
        if stage_data.empty:
            # No failures for this stage
            table_rows.append(" & (no failures recorded) & --- \\\\")
        else:
            for _, row in stage_data.iterrows():
                # Translate cause to detailed description
                display_cause = translate_cause_for_display(stage, row["cause"])
                table_rows.append(
                    f"{row['type']} & {display_cause} & {int(row['count']):,} \\\\"
                )

        table_rows.append("\\midrule")

    # Add final summary row
    total_failures = total_eligible - success_count
    success_pct = success_count / total_eligible * 100

    # Format numbers with phantom digits for alignment (matching stage headers)
    total_str = str(total_eligible)
    success_str = (
        f"\\phantom{{0}}{success_count}" if success_count < 100 else str(success_count)
    )
    failures_str = (
        f"\\phantom{{00}}{total_failures}"
        if total_failures < 10
        else (
            f"\\phantom{{0}}{total_failures}"
            if total_failures < 100
            else str(total_failures)
        )
    )
    success_pct_str = (
        f"\\phantom{{0}}{success_pct:.1f}" if success_pct < 10 else f"{success_pct:.1f}"
    )

    summary_text = f"\\textit{{\\makebox[16em][l]{{Overall:}} {total_str} projects\\quad{{}}{success_str} pass\\quad{{}}{failures_str} fail\\quad{{}}{success_pct_str}\\% pass rate}}"
    table_rows.append(f"\\multicolumn{{3}}{{l}}{{{summary_text}}} \\\\")

    # Build complete LaTeX table
    table_body = "\n".join(table_rows)

    latex_content = f"""\\begin{{table}}[htbp]
\\caption{{%
    Processing failures by stage and cause for the \\VariantImprovedC{{}} generalization strategy.
    Internal failures are caused by configured resource limits
    or current limitations of \\ToolTeralizer{{}}.
    External failures are caused by \\ToolTeralizer{{}}'s dependencies
    (i.e., JUnit, Spoon, \\ToolJPF{{}} / \\ToolSPF{{}}, \\ToolJacoco{{}}, and \\ToolPit{{}}).
    Mixed failures are influenced by both internal as well as external factors.
}}
\\label{{tab:processing-failures}}
\\centering
\\begin{{tabular}}{{llr}}
\\toprule
Type & Cause of Failure & Count \\\\
\\midrule
{table_body}
\\bottomrule
\\end{{tabular}}
\\end{{table}}"""

    return latex_content


def generate_processing_failures_by_cause_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for unified processing failures table.

    Args:
        df: DataFrame from compute_processing_failures_by_stage_and_cause
                with columns: stage, type, cause, count

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in df.iterrows():
        csv_data.append(
            {
                "stage": row["stage"],
                "type": row["type"],
                "cause_of_failure": row["cause"],
                "count": int(row["count"]),
            }
        )
    return pd.DataFrame(csv_data)
