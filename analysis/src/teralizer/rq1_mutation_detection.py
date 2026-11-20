"""RQ1: Effects on Mutation Score - Analysis functions for mutation detection effectiveness.

This module provides functions to analyze how test generalization affects mutation
detection rates, comparing different variants (INITIAL, NAIVE, IMPROVED) and their
effectiveness at detecting various types of mutants.
"""

import pandas as pd
from typing import List

from .formatting import (
    sort_dataframe_by_project,
)


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================


MUTATION_RESULTS_BY_PROJECT_VARIANT_SQL = """
SELECT mr.project_id, mr.project_name, mr.variant,
       mr.total, mr.covered, mr.uncovered,
       mr.covered_pct, mr.uncovered_pct,
       mr.survived_of_covered_pct, mr.detected_of_covered_pct,
       mr.killed_of_covered_pct, mr.timed_out_of_covered_pct,
       mr.memory_error_of_covered_pct, mr.run_error_of_covered_pct,
       mr.survived_of_covered_pct_diff, mr.detected_of_covered_pct_diff,
       mr.killed_of_covered_pct_diff, mr.timed_out_of_covered_pct_diff,
       mr.memory_error_of_covered_pct_diff, mr.run_error_of_covered_pct_diff
FROM mv_mutation_results_by_project_variant mr
JOIN v_projects_successes ps ON ps.project_id = mr.project_id
WHERE mr.variant NOT IN ('ORIGINAL', 'BASELINE')
"""


def get_mutation_results_by_project_variant(conn) -> pd.DataFrame:
    """Get mutation results aggregated by project and variant.

    Args:
        conn: Database connection

    Returns:
        DataFrame with columns: project_id, project_name, variant, total,
        covered, uncovered, covered_pct, uncovered_pct, detected, survived, etc.
    """
    return pd.read_sql_query(MUTATION_RESULTS_BY_PROJECT_VARIANT_SQL, conn)


def get_mutation_results_by_mutator(conn, variants: List[str]) -> pd.DataFrame:
    """Get mutation results broken down by mutator type.

    Args:
        conn: Database connection
        variants: List of variant names to include

    Returns:
        DataFrame with mutation statistics per mutator
    """
    variant_list = "', '".join(variants)
    query = f"""
    SELECT
        mr.mutator,
        mr.variant,
        SUM(mr.total) as total_mutants,
        SUM(mr.covered) as covered_mutants,
        SUM(mr.detected) as detected_mutants
    FROM mv_mutation_results_by_project_variant_mutator mr
    JOIN v_projects_successes ps ON ps.project_id = mr.project_id
    WHERE mr.variant IN ('{variant_list}')
    GROUP BY mr.mutator, mr.variant
    """
    return pd.read_sql_query(query, conn)


def get_mutation_detection_comparison(conn) -> pd.DataFrame:
    """Get model properties of detected vs undetected mutants.

    Args:
        conn: Database connection

    Returns:
        DataFrame with model complexity metrics for detected/undetected mutants
    """
    return pd.read_sql_query("SELECT * FROM mv_mutation_detection_comparison", conn)


def get_mutation_coverage_data(conn) -> pd.DataFrame:
    """Get mutation coverage statistics including test/class inclusion.

    Args:
        conn: Database connection

    Returns:
        DataFrame with mutation coverage and inclusion statistics
    """
    # Get basic mutation data
    mutation_query = """
    SELECT mr.project_id, mr.project_name AS project, mr.variant, 
           mr.total, mr.covered, mr.uncovered, mr.covered_pct, mr.uncovered_pct
    FROM mv_mutation_results_by_project_variant mr
    JOIN v_projects_successes ps ON ps.project_id = mr.project_id
    WHERE mr.variant = 'INITIAL'
    """
    df_mutations = pd.read_sql_query(mutation_query, conn)

    # Get total tests
    total_tests_query = """
    SELECT t.project_id, count(*) AS total_tests
    FROM test t
    JOIN project p ON t.project_id = p.id
    JOIN v_projects_successes ps ON ps.project_id = t.project_id
    WHERE p.use_test_generalization
    GROUP BY t.project_id
    """
    df_total_tests = pd.read_sql_query(total_tests_query, conn)

    # Get included tests
    included_tests_query = """
    SELECT r.project_id, count(*) AS included_tests
    FROM junit_test_report r
    JOIN v_projects_successes ps ON ps.project_id = r.project_id
    WHERE r.stage = 'COLLECT_JUNIT_REPORTS_INITIAL'
    GROUP BY r.project_id
    """
    df_included_tests = pd.read_sql_query(included_tests_query, conn)

    # Get included classes
    included_classes_query = """
    SELECT r.project_id, count(*) AS included_classes
    FROM jacoco_coverage_report r
    JOIN v_projects_successes ps ON ps.project_id = r.project_id
    WHERE r.stage = 'COLLECT_JACOCO_DATA_INITIAL' 
          AND instruction_covered > 0 
          AND covered_class NOT LIKE '%%...%%'
    GROUP BY r.project_id
    """
    df_included_classes = pd.read_sql_query(included_classes_query, conn)

    # Merge all data
    df = df_mutations
    df = df.merge(df_total_tests, on="project_id", how="left")
    df = df.merge(df_included_tests, on="project_id", how="left")
    df = df.merge(df_included_classes, on="project_id", how="left")

    return df


def get_project_mutator_data(conn) -> pd.DataFrame:
    """Get per-project mutator data for calculating min/max percentages.

    Args:
        conn: Database connection

    Returns:
        DataFrame with mutator counts per project
    """
    query = """
    SELECT
        mr.project_id,
        mr.mutator,
        mr.total
    FROM mv_mutation_results_by_project_variant_mutator mr
    JOIN v_projects_successes ps ON ps.project_id = mr.project_id
    WHERE mr.variant = 'INITIAL'
    """
    return pd.read_sql_query(query, conn)


# =============================================================================
# Computation Functions (compute_*)
# =============================================================================


def compute_mutator_statistics(
    variant_data: pd.DataFrame, project_data: pd.DataFrame
) -> pd.DataFrame:
    """Compute detection rates and improvements per mutator.

    Args:
        variant_data: DataFrame from get_mutation_results_by_mutator
        project_data: DataFrame from get_project_mutator_data

    Returns:
        DataFrame with computed statistics per mutator
    """
    # Calculate total mutants per project
    project_totals = project_data.groupby("project_id")["total"].sum().reset_index()
    project_totals.rename(columns={"total": "project_total"}, inplace=True)

    # Merge to get project totals
    project_data = pd.merge(project_data, project_totals, on="project_id")

    # Calculate percentage of each mutator within each project
    project_data.loc[:, "project_percent"] = (
        project_data["total"] / project_data["project_total"] * 100
    )

    # Calculate min and max percentages for each mutator
    min_max_percent = (
        project_data.groupby("mutator")["project_percent"]
        .agg(["min", "max"])
        .reset_index()
    )
    min_max_percent.columns = ["mutator", "min_percent", "max_percent"]

    # Get INITIAL variant data for overall percentages
    initial_data = variant_data[variant_data["variant"] == "INITIAL"]
    total_initial_mutants = initial_data["total_mutants"].sum()

    # Calculate overall percentage for each mutator
    initial_summary = initial_data.copy()
    initial_summary.loc[:, "percent"] = (
        initial_summary["total_mutants"] / total_initial_mutants * 100
    )

    # Calculate detected percentage for each variant
    variant_data.loc[:, "detected_of_covered_pct"] = (
        variant_data["detected_mutants"] / variant_data["covered_mutants"] * 100
    ).fillna(0)

    # Pivot to get columns for each variant
    variant_pivot = variant_data.pivot(
        index="mutator", columns="variant", values="detected_of_covered_pct"
    ).reset_index()

    # Calculate improvements for all NAIVE and IMPROVED variants
    naive_variants = [col for col in variant_pivot.columns if col.startswith("NAIVE_")]
    improved_variants = [
        col for col in variant_pivot.columns if col.startswith("IMPROVED_")
    ]

    for variant in naive_variants + improved_variants:
        diff_col_name = f"detected_diff_{variant.lower()}"
        variant_pivot.loc[:, diff_col_name] = (
            variant_pivot[variant] - variant_pivot["INITIAL"]
        )

    # Merge all data
    result = pd.merge(
        initial_summary[["mutator", "total_mutants", "percent"]],
        min_max_percent,
        on="mutator",
    )
    result = pd.merge(result, variant_pivot, on="mutator")

    # Sort by total mutants descending, then by mutator name
    result = result.sort_values(["total_mutants", "mutator"], ascending=[False, True])

    return result


def compute_project_mutation_coverage(
    df_mutations: pd.DataFrame, df_total_classes: pd.DataFrame
) -> pd.DataFrame:
    """Compute covered/uncovered mutants with test/class inclusion rates.

    Args:
        df_mutations: DataFrame from get_mutation_coverage_data
        df_total_classes: DataFrame from get_total_classes_from_filesystem

    Returns:
        DataFrame with complete mutation coverage statistics
    """
    # Merge total classes data
    df = df_mutations.merge(df_total_classes, on="project_id", how="left")

    # Sort by project order
    df = df.drop(columns=["variant"])  # Not needed for this analysis
    df = sort_dataframe_by_project(df, "project")

    return df


def compute_detection_improvements(
    df: pd.DataFrame, baseline_variant: str = "INITIAL"
) -> pd.DataFrame:
    """Calculate absolute and relative improvements over baseline.

    Args:
        df: DataFrame with mutation results by project and variant
        baseline_variant: Variant to use as baseline for comparison

    Returns:
        DataFrame with improvement calculations added
    """
    # Group by project to get baseline values
    baseline_data = df[df["variant"] == baseline_variant].set_index("project_name")

    # Calculate improvements for each row
    improvements = []
    for _, row in df.iterrows():
        if row["variant"] == baseline_variant:
            abs_improvement = 0.0
            rel_improvement = 0.0
        else:
            project = row["project_name"]
            if project in baseline_data.index:
                baseline_detected = baseline_data.loc[
                    project, "detected_of_covered_pct"
                ]
                abs_improvement = row["detected_of_covered_pct"] - baseline_detected
                rel_improvement = (
                    (abs_improvement / baseline_detected * 100)
                    if baseline_detected != 0
                    else 0.0
                )
            else:
                abs_improvement = 0.0
                rel_improvement = 0.0

        improvements.append(
            {
                "absolute_improvement": abs_improvement,
                "relative_improvement": rel_improvement,
            }
        )

    # Add improvements to dataframe
    improvements_df = pd.DataFrame(improvements)
    df = pd.concat([df, improvements_df], axis=1)

    return df


def compute_mutation_model_complexity(df: pd.DataFrame) -> pd.DataFrame:
    """Analyze model complexity factors for detected vs undetected mutants.

    Args:
        df: DataFrame from get_mutation_detection_comparison

    Returns:
        DataFrame with model complexity analysis
    """
    # Convert is_detected boolean to categorical
    df = df.copy()
    is_detected = pd.Categorical(
        df["is_detected"].map({True: "yes", False: "no"}),
        categories=["yes", "no"],
        ordered=True,
    )
    df.isetitem(df.columns.get_loc("is_detected"), is_detected)

    # Calculate percent of mutants per project
    project_totals = df.groupby("project_name", observed=False)["count"].transform(
        "sum"
    )
    df.loc[:, "mutant_percent"] = df["count"] / project_totals * 100

    # Sort by project order
    df = sort_dataframe_by_project(df, "project_name")

    return df
