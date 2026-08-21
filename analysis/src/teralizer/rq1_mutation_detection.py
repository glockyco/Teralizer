"""RQ1: Effects on Mutation Score - Analysis functions for mutation detection effectiveness.

This module provides functions to analyze how test generalization affects mutation
detection rates, comparing different variants (INITIAL, NAIVE, IMPROVED) and their
effectiveness at detecting various types of mutants.
"""

import pandas as pd
from pathlib import Path
from typing import List
import os
import re

from .formatting import (
    sort_dataframe_by_project,
    replace_project_names_with_macros,
    build_latex_table_content,
)
from .exports import (
    get_variant_macro,
    format_detection_rate_decimal,
    standardize_project_name,
    get_project_type,
    get_data_output_dir,
)
from .report_basis import resolve_repo_relative_path


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================


def get_mutation_results_by_project_variant(conn) -> pd.DataFrame:
    """Get mutation results aggregated by project and variant.

    Args:
        conn: Database connection

    Returns:
        DataFrame with columns: project_id, project_name, variant, total,
        covered, uncovered, covered_pct, uncovered_pct, detected, survived, etc.
    """
    query = """
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
    return pd.read_sql_query(query, conn)


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


def get_generalization_effects(conn) -> pd.DataFrame:
    """Get newly killed mutations between variant comparisons.

    Args:
        conn: Database connection

    Returns:
        DataFrame with generalization effects between variants
    """
    return pd.read_sql_query("SELECT * FROM mv_generalization_effects", conn)


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


def _get_reference_mutants_csv_path() -> Path:
    """Get path to pre-computed reference mutants CSV."""
    reference_path = (
        Path(__file__).parent.parent.parent / "output" / "original" / "data"
    )
    csv_path = reference_path / "mutants-per-project-data.csv"
    if csv_path.exists():
        return csv_path
    return get_data_output_dir() / "mutants-per-project-data.csv"


def _load_precomputed_class_counts(conn) -> pd.DataFrame:
    """Load class counts from pre-computed reference CSV."""
    csv_path = _get_reference_mutants_csv_path()
    if not csv_path.exists():
        raise FileNotFoundError(
            f"Pre-computed mutants data not found at {csv_path}. "
            "Either provide projects/ directory or ensure reference outputs exist."
        )

    df = pd.read_csv(csv_path)

    # Get project IDs from database to map names to IDs
    query = """
    SELECT p.id AS project_id, project_name(p.id) AS project_name
    FROM project p
    JOIN v_projects_successes ps ON ps.project_id = p.id
    WHERE p.use_test_generalization
    """
    df_projects = pd.read_sql_query(query, conn)

    # CSV uses standardized names, so standardize database names before merge
    df_projects["project_name"] = df_projects["project_name"].apply(
        standardize_project_name
    )

    # Merge to get project_id
    df = df.merge(df_projects, on="project_name", how="inner")
    return df[["project_id", "total_impl_classes"]].rename(
        columns={"total_impl_classes": "total_classes"}
    )


def get_total_classes_from_filesystem(conn) -> pd.DataFrame:
    """Get total implementation classes by analyzing the filesystem.

    Falls back to pre-computed reference data when projects directory is unavailable.

    Args:
        conn: Database connection

    Returns:
        DataFrame with project_id and total_classes columns
    """
    # Get project source directories
    query = """
    SELECT p.id AS project_id, project_name(p.id) AS project_name,
           p.main_source_path AS main_source_path
    FROM project p
    JOIN v_projects_successes ps ON ps.project_id = p.id
    WHERE p.use_test_generalization
    """
    df_projects = pd.read_sql_query(query, conn)
    df_projects["impl_source_directory"] = df_projects["main_source_path"].map(
        resolve_repo_relative_path
    )

    # Check if any project directory exists
    any_directory_exists = False
    for _, row in df_projects.iterrows():
        if os.path.exists(row["impl_source_directory"]):
            any_directory_exists = True
            break

    if not any_directory_exists:
        print("Projects directory not found. Loading pre-computed class counts...")
        return _load_precomputed_class_counts(conn)

    def collect_stats(directory):
        """Count files and classes in a directory."""
        num_files = 0
        num_classes = 0

        if not os.path.exists(directory):
            return {"total_files": 0, "total_classes": 0}

        for root, _, files in os.walk(directory):
            for file in files:
                if file.endswith(".java"):
                    num_files += 1
                    file_path = os.path.join(root, file)
                    with open(file_path, encoding="utf-8", errors="ignore") as f:
                        content = f.read()
                        num_classes += len(re.findall(r"\b(class|enum)\s+\w+", content))

        return {"total_files": num_files, "total_classes": num_classes}

    # Apply stats collection to each project
    stats_data = []
    for _, row in df_projects.iterrows():
        stats = collect_stats(row["impl_source_directory"])
        stats_data.append(
            {"project_id": row["project_id"], "total_classes": stats["total_classes"]}
        )

    return pd.DataFrame(stats_data)


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


# =============================================================================
# Generation Functions (generate_*)
# =============================================================================


def generate_detections_per_mutator_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table for mutation detection rates by mutator.

    Args:
        df: DataFrame from compute_mutator_statistics

    Returns:
        Complete LaTeX table string
    """
    # Prepare data for LaTeX
    df_table = df.copy()

    # Clean up mutator names
    df_table["mutator"] = df_table["mutator"].str.replace("Mutator", "").str.strip()
    df_table["mutator"] = df_table["mutator"].str.replace(
        "RemoveConditional_ORDER_ELSE", "RemoveConditionalOrderElse"
    )
    df_table["mutator"] = df_table["mutator"].str.replace(
        "RemoveConditional_EQUAL_ELSE", "RemoveConditionalEqualElse"
    )

    # Format data for table
    df_table["total"] = df_table["total_mutants"].astype(int)

    # Format percentages
    percent_cols = [
        "percent",
        "min_percent",
        "max_percent",
        "INITIAL",
        "NAIVE_200_TRIES",
        "IMPROVED_200_TRIES",
    ]
    for col in percent_cols:
        if col in df_table.columns:
            df_table[col] = df_table[col].round(2)

    # Build table rows manually
    table_rows = []
    for _, row in df_table.iterrows():
        naive_val = row.get("NAIVE_200_TRIES", row["INITIAL"])
        improved_val = row.get("IMPROVED_200_TRIES", row["INITIAL"])
        naive_diff = row.get("detected_diff_naive_200_tries", 0)
        improved_diff = row.get("detected_diff_improved_200_tries", 0)

        # Format difference strings
        naive_diff_str = (
            f"(+{naive_diff:.2f})"
            if naive_diff > 0
            else f"({naive_diff:.2f})"
            if naive_diff < 0
            else "--"
        )
        improved_diff_str = (
            f"(+{improved_diff:.2f})"
            if improved_diff > 0
            else f"({improved_diff:.2f})"
            if improved_diff < 0
            else "--"
        )

        row_str = (
            f"{row['mutator']} & {row['total']:,} & {row['percent']:.2f} & "
            f"{row['min_percent']:.2f} & {row['max_percent']:.2f} & {row['INITIAL']:.2f} & "
            f"{naive_val:.2f} & {naive_diff_str} & {improved_val:.2f} & {improved_diff_str}"
        )
        table_rows.append(row_str)

    # Build complete table
    header_rows = [
        "& & & & & \\multicolumn{5}{c}{Detected \\%} \\\\",
        "\\cmidrule{6-10}",
        "Mutator & Total & Total \\% & Min \\% & Max \\% & INITIAL & \\multicolumn{2}{c}{NAIVE$_{200}$} & \\multicolumn{2}{c}{IMPROVED$_{200}$} \\\\",
    ]

    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(" & ") for row in table_rows])

    table_content = build_latex_table_content(
        rows_df,
        caption="Number of mutants and percentage of detections per mutator.",
        label="tab:detections-per-mutator",
        column_spec="lrrrrcrrrr",
        header_rows=header_rows,
        add_midrules=False,  # No midrules needed for this table
    )

    return table_content


def generate_mutants_per_project_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table for mutation coverage by project.

    Args:
        df: DataFrame from compute_project_mutation_coverage

    Returns:
        Complete LaTeX table string
    """
    # Prepare data for LaTeX
    df_table = df.copy()

    # Calculate percentages
    df_table["test_inclusion_pct"] = (
        df_table["included_tests"] / df_table["total_tests"] * 100
    ).round(1)
    df_table["class_inclusion_pct"] = (
        df_table["included_classes"] / df_table["total_classes"] * 100
    ).round(1)

    # Replace project names with macros
    df_table = replace_project_names_with_macros(df_table, "project")

    # Build table rows manually
    table_rows = []
    for _, row in df_table.iterrows():
        phantom_zero = r"\phantom{0}"
        covered_phantom = phantom_zero if row["covered_pct"] < 10 else ""
        uncovered_phantom = phantom_zero if row["uncovered_pct"] < 10 else ""

        row_str = (
            f"{row['project']} & "
            f"{int(row['included_tests']):,}\\; ({row['test_inclusion_pct']:.1f}\\%) & "
            f"{int(row['included_classes']):,}\\; ({row['class_inclusion_pct']:.1f}\\%) & "
            f"{int(row['total']):,} & "
            f"{int(row['covered']):,}\\; ({covered_phantom}{row['covered_pct']:.1f}\\%) & "
            f"{int(row['uncovered']):,}\\; ({uncovered_phantom}{row['uncovered_pct']:.1f}\\%)"
        )
        table_rows.append(row_str)

    # Build complete table
    header_rows = [
        "& Included     & Included      & \\multicolumn{3}{r}{Mutants} \\\\",
        "                                         \\cmidrule(lr){4-6}",
        "Project & Test Methods & Impl. Classes & Total & Covered & Uncovered \\\\",
    ]

    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(" & ") for row in table_rows])
    rows_df.columns = [
        "Project",
        "Test Methods",
        "Impl. Classes",
        "Total",
        "Covered",
        "Uncovered",
    ]

    table_content = build_latex_table_content(
        rows_df,
        caption="Number of total, covered, and uncovered mutants in included classes per project.",
        label="tab:mutants-per-project",
        column_spec="lrrrrr",
        header_rows=header_rows,
        add_midrules=True,
        grouping_column="Project",
        grouping_func=get_project_type,
    )

    return table_content


def generate_mutation_detection_comparison_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table comparing detected vs undetected mutant properties.

    Args:
        df: DataFrame from compute_mutation_model_complexity

    Returns:
        Complete LaTeX table string
    """
    # Prepare data for LaTeX
    df_table = df.copy()

    # Replace project names with macros
    df_table = replace_project_names_with_macros(df_table, "project_name")

    # Utility functions for formatting
    def phantom_pad(num, width=2):
        if pd.isna(num):
            return "-"
        s = f"{int(num)}"
        return r"\phantom{0}" * (width - len(s)) + s

    def percent_pad(num, width=2):
        if pd.isna(num):
            return "-"
        s = f"{int(num)}"
        return r"\phantom{0}" * (width - len(s)) + s + r"\%"

    # Build table rows manually
    table_rows = []
    for _, row in df_table.iterrows():
        row_str = (
            f"{row['project_name']} & "
            f"{row['is_detected']} & "
            f"{int(row['count']):,} & "
            f"{phantom_pad(row['avg_model_operation_count'], width=3)} & "
            f"{phantom_pad(row['median_model_operation_count'], width=2)} & "
            f"{phantom_pad(row['avg_total_constraint_count'], width=2)} & "
            f"{phantom_pad(row['median_total_constraint_count'], width=1)} & "
            f"{percent_pad(row['avg_used_constraint_pct'], width=3)} & "
            f"{percent_pad(row['median_used_constraint_pct'], width=3)}"
        )
        table_rows.append(row_str)

    # Build complete table
    header_rows = [
        "& & & \\multicolumn{2}{c}{Operations} & \\multicolumn{2}{c}{Constraints} & \\multicolumn{2}{c}{Constraints Used} \\\\",
        "\\cmidrule(lr){4-5} \\cmidrule(lr){6-7} \\cmidrule(lr){8-9}",
        "Project & Detected & Mutants & Mean & Median & Mean & Median & Mean & Median \\\\",
    ]

    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(" & ") for row in table_rows])
    rows_df.columns = [
        "Project",
        "Detected",
        "Mutants",
        "AvgOps",
        "MedianOps",
        "AvgConstraints",
        "MedianConstraints",
        "AvgUsed",
        "MedianUsed",
    ]

    table_content = build_latex_table_content(
        rows_df,
        caption="Model properties of mutants that are (not) detected by the \\VariantImprovedC{} variant.",
        label="tab:mutation-detection-comparison",
        column_spec="lcrrrrrrr",
        header_rows=header_rows,
        add_midrules=True,
        grouping_column="Project",
        grouping_func=get_project_type,
    )

    return table_content


# =============================================================================
# CSV Export Functions
# =============================================================================


def generate_detections_per_mutator_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for detection rates by mutator with all variants.

    Args:
        df: DataFrame from compute_mutator_statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    # Clean up mutator names
    df = df.copy()
    df.loc[:, "mutator"] = df["mutator"].str.replace("Mutator", "").str.strip()
    df.loc[:, "mutator"] = df["mutator"].str.replace(
        "RemoveConditional_ORDER_ELSE", "RemoveConditionalOrderElse"
    )
    df.loc[:, "mutator"] = df["mutator"].str.replace(
        "RemoveConditional_EQUAL_ELSE", "RemoveConditionalEqualElse"
    )

    for _, row in df.iterrows():
        # Base data
        csv_row = {
            "mutator": row["mutator"],
            "total_mutants": int(row["total_mutants"]),
            "percentage_of_total": format_detection_rate_decimal(row["percent"]),
            "min_percentage_across_projects": format_detection_rate_decimal(
                row["min_percent"]
            ),
            "max_percentage_across_projects": format_detection_rate_decimal(
                row["max_percent"]
            ),
            "initial_detection_rate": format_detection_rate_decimal(
                row.get("INITIAL", 0)
            ),
        }

        # Add all NAIVE variants
        for tries in [10, 50, 200]:
            variant_col = f"NAIVE_{tries}_TRIES"
            csv_row[f"naive_{tries}_detection_rate"] = format_detection_rate_decimal(
                row.get(variant_col, 0)
            )
            csv_row[f"naive_{tries}_improvement"] = row.get(
                f"detected_diff_{variant_col.lower()}", 0.0
            )

        # Add all IMPROVED variants
        for tries in [10, 50, 200]:
            variant_col = f"IMPROVED_{tries}_TRIES"
            csv_row[f"improved_{tries}_detection_rate"] = format_detection_rate_decimal(
                row.get(variant_col, 0)
            )
            csv_row[f"improved_{tries}_improvement"] = row.get(
                f"detected_diff_{variant_col.lower()}", 0.0
            )

        csv_data.append(csv_row)

    return pd.DataFrame(csv_data)


def generate_mutants_per_project_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for mutation coverage statistics.

    Args:
        df: DataFrame from compute_project_mutation_coverage

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project"]),
                "total_test_methods": int(row["total_tests"])
                if pd.notna(row["total_tests"])
                else 0,
                "included_test_methods": int(row["included_tests"])
                if pd.notna(row["included_tests"])
                else 0,
                "test_inclusion_rate": (row["included_tests"] / row["total_tests"])
                if pd.notna(row["total_tests"]) and row["total_tests"] > 0
                else 0.0,
                "total_impl_classes": int(row["total_classes"])
                if pd.notna(row["total_classes"])
                else 0,
                "included_impl_classes": int(row["included_classes"])
                if pd.notna(row["included_classes"])
                else 0,
                "class_inclusion_rate": (row["included_classes"] / row["total_classes"])
                if pd.notna(row["total_classes"]) and row["total_classes"] > 0
                else 0.0,
                "total_mutants": int(row["total"]) if pd.notna(row["total"]) else 0,
                "covered_mutants": int(row["covered"])
                if pd.notna(row["covered"])
                else 0,
                "uncovered_mutants": int(row["uncovered"])
                if pd.notna(row["uncovered"])
                else 0,
                "covered_mutants_percentage": float(row["covered_pct"])
                if pd.notna(row["covered_pct"])
                else 0.0,
                "uncovered_mutants_percentage": float(row["uncovered_pct"])
                if pd.notna(row["uncovered_pct"])
                else 0.0,
            }
        )

    return pd.DataFrame(csv_data)


def generate_mutation_detection_figure_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for mutation detection visualization.

    Args:
        df: DataFrame from get_mutation_results_by_project_variant with improvements already computed

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project_name"]),
                "variant": get_variant_macro(row["variant"]),
                "detected_percentage": float(row["detected_of_covered_pct"]),
                "survived_percentage": float(row["survived_of_covered_pct"]),
                "killed_percentage": float(row["killed_of_covered_pct"]),
                "timed_out_percentage": float(row["timed_out_of_covered_pct"]),
                "memory_error_percentage": float(row["memory_error_of_covered_pct"]),
                "run_error_percentage": float(row["run_error_of_covered_pct"]),
                "detected_improvement_absolute": float(row["absolute_improvement"])
                if pd.notna(row["absolute_improvement"])
                else 0.0,
                "detected_improvement_relative": float(row["relative_improvement"])
                if pd.notna(row["relative_improvement"])
                else 0.0,
                "baseline_initial_detected": float(row["detected_of_covered_pct"])
                if row["variant"] == "INITIAL"
                else 0.0,
            }
        )

    return pd.DataFrame(csv_data)


def generate_mutation_detection_comparison_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for model properties comparison.

    Args:
        df: DataFrame from compute_mutation_model_complexity

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project_name"]),
                "is_detected": row["is_detected"],  # 'yes' or 'no'
                "mutant_count": int(row["count"]) if pd.notna(row["count"]) else 0,
                "mutant_percentage": float(row["mutant_percent"])
                if pd.notna(row["mutant_percent"])
                else 0.0,
                "avg_model_operation_count": float(row["avg_model_operation_count"])
                if pd.notna(row["avg_model_operation_count"])
                else 0.0,
                "median_model_operation_count": float(
                    row["median_model_operation_count"]
                )
                if pd.notna(row["median_model_operation_count"])
                else 0.0,
                "avg_total_constraint_count": float(row["avg_total_constraint_count"])
                if pd.notna(row["avg_total_constraint_count"])
                else 0.0,
                "median_total_constraint_count": float(
                    row["median_total_constraint_count"]
                )
                if pd.notna(row["median_total_constraint_count"])
                else 0.0,
                "avg_used_constraint_percentage": float(row["avg_used_constraint_pct"])
                if pd.notna(row["avg_used_constraint_pct"])
                else 0.0,
                "median_used_constraint_percentage": float(
                    row["median_used_constraint_pct"]
                )
                if pd.notna(row["median_used_constraint_pct"])
                else 0.0,
            }
        )

    return pd.DataFrame(csv_data)
