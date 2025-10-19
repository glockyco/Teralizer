"""RQ2: Effects on Test Suite Size and Runtime - Analysis functions for secondary effects.

This module provides functions to analyze how test generalization affects test suite
characteristics: test count, lines of code, and execution runtime changes between
ORIGINAL and generalized variants (NAIVE, IMPROVED).
"""

import pandas as pd
from typing import List, Optional

from .formatting import (
    sort_dataframe_by_project,
    replace_project_names_with_macros,
    replace_variant_names_with_macros,
    build_latex_table_content,
)
from .exports import get_variant_macro, standardize_project_name, get_project_type


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================


def get_generalization_effects_data(
    conn, variants: Optional[List[str]] = None
) -> pd.DataFrame:
    """Get generalization effects data comparing ORIGINAL to specified variants.

    Args:
        conn: Database connection
        variants: List of variants to compare against ORIGINAL (default: NAIVE_200_TRIES, IMPROVED_200_TRIES)

    Returns:
        DataFrame with test count, line count, and runtime changes
    """
    if variants is None:
        variants = ["NAIVE_200_TRIES", "IMPROVED_200_TRIES"]

    variant_list = "', '".join(variants)
    query = f"""
    SELECT *
    FROM mv_generalization_effects
    WHERE a_variant = 'ORIGINAL' AND b_variant IN ('{variant_list}')
    """

    return pd.read_sql_query(query, conn)


def get_test_count_changes_by_project_variant(
    conn, variants: Optional[List[str]] = None
) -> pd.DataFrame:
    """Get test count changes between ORIGINAL and generalized variants.

    Args:
        conn: Database connection
        variants: List of variants to include

    Returns:
        DataFrame with test count statistics per project and variant
    """
    df = get_generalization_effects_data(conn, variants)
    return df[
        [
            "project_name",
            "a_variant",
            "b_variant",
            "tests_before",
            "added_tests",
            "removed_tests",
            "tests_after",
            "tests_delta",
            "tests_delta_pct",
        ]
    ]


def get_line_count_changes_by_project_variant(
    conn, variants: Optional[List[str]] = None
) -> pd.DataFrame:
    """Get line count changes between ORIGINAL and generalized variants.

    Args:
        conn: Database connection
        variants: List of variants to include

    Returns:
        DataFrame with line count statistics per project and variant
    """
    df = get_generalization_effects_data(conn, variants)
    return df[
        [
            "project_name",
            "a_variant",
            "b_variant",
            "lines_before",
            "added_lines",
            "removed_lines",
            "lines_after",
            "lines_delta",
            "lines_delta_pct",
        ]
    ]


def get_runtime_changes_by_project_variant(
    conn, variants: Optional[List[str]] = None
) -> pd.DataFrame:
    """Get runtime changes between ORIGINAL and generalized variants.

    Args:
        conn: Database connection
        variants: List of variants to include

    Returns:
        DataFrame with runtime statistics per project and variant
    """
    df = get_generalization_effects_data(conn, variants)
    return df[
        [
            "project_name",
            "a_variant",
            "b_variant",
            "runtime_before",
            "added_runtime",
            "removed_runtime",
            "runtime_after",
            "runtime_delta",
            "runtime_delta_pct",
        ]
    ]


def get_test_vs_generalization_runtime_comparison(conn) -> pd.DataFrame:
    """Get test execution vs generalization runtime comparison data.

    Args:
        conn: Database connection

    Returns:
        DataFrame with runtime comparison statistics per variant
    """
    query = """
    SELECT 
        rc.variant,
        avg(rc.t_runtime * 1000) AS mean_t_runtime_ms,
        avg(rc.g_runtime * 1000) AS mean_g_runtime_ms,
        avg(rc.runtime_diff * 1000) AS mean_runtime_diff_ms,
        avg(rc.g_runtime) / avg(rc.t_runtime) AS ratio_of_mean_runtimes,
        min(tries) AS tries,
        avg(rc.runtime_diff_per_try * 1000) AS mean_runtime_diff_per_try_ms
    FROM mv_runtime_comparison_test_vs_generalization rc
    GROUP BY rc.variant, rc.variant_order
    ORDER BY rc.variant_order
    """

    return pd.read_sql_query(query, conn)


# =============================================================================
# Computation Functions (compute_*)
# =============================================================================


def compute_test_suite_change_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Compute test count change statistics with proper sorting.

    Args:
        df: DataFrame from get_test_count_changes_by_project_variant

    Returns:
        DataFrame sorted by project group order with computed statistics
    """
    # Sort by project group order
    df_sorted = sort_dataframe_by_project(df, "project_name")

    # Convert numeric columns to proper types
    numeric_cols = [
        "tests_before",
        "added_tests",
        "removed_tests",
        "tests_after",
        "tests_delta",
    ]
    for col in numeric_cols:
        df_sorted[col] = (
            pd.to_numeric(df_sorted[col], errors="coerce").fillna(0).astype(int)
        )

    df_sorted["tests_delta_pct"] = pd.to_numeric(
        df_sorted["tests_delta_pct"], errors="coerce"
    ).fillna(0.0)

    return df_sorted


def compute_line_count_change_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Compute line count change statistics with proper sorting.

    Args:
        df: DataFrame from get_line_count_changes_by_project_variant

    Returns:
        DataFrame sorted by project group order with computed statistics
    """
    # Sort by project group order
    df_sorted = sort_dataframe_by_project(df, "project_name")

    # Convert numeric columns to proper types
    numeric_cols = [
        "lines_before",
        "added_lines",
        "removed_lines",
        "lines_after",
        "lines_delta",
    ]
    for col in numeric_cols:
        df_sorted[col] = (
            pd.to_numeric(df_sorted[col], errors="coerce").fillna(0).astype(int)
        )

    df_sorted["lines_delta_pct"] = pd.to_numeric(
        df_sorted["lines_delta_pct"], errors="coerce"
    ).fillna(0.0)

    return df_sorted


def compute_runtime_change_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Compute runtime change statistics with proper sorting.

    Args:
        df: DataFrame from get_runtime_changes_by_project_variant

    Returns:
        DataFrame sorted by project group order with computed statistics
    """
    # Sort by project group order
    df_sorted = sort_dataframe_by_project(df, "project_name")

    # Convert numeric columns to proper types
    numeric_cols = [
        "runtime_before",
        "added_runtime",
        "removed_runtime",
        "runtime_after",
        "runtime_delta",
    ]
    for col in numeric_cols:
        df_sorted[col] = pd.to_numeric(df_sorted[col], errors="coerce").fillna(0.0)

    df_sorted["runtime_delta_pct"] = pd.to_numeric(
        df_sorted["runtime_delta_pct"], errors="coerce"
    ).fillna(0.0)

    return df_sorted


def compute_test_vs_generalization_runtime_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Compute test vs generalization runtime comparison statistics.

    Args:
        df: DataFrame from get_test_vs_generalization_runtime_comparison

    Returns:
        DataFrame with computed runtime comparison statistics
    """
    # Convert numeric columns to proper types
    numeric_cols = [
        "mean_t_runtime_ms",
        "mean_g_runtime_ms",
        "mean_runtime_diff_ms",
        "ratio_of_mean_runtimes",
        "mean_runtime_diff_per_try_ms",
    ]
    for col in numeric_cols:
        df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0.0)

    df["tries"] = pd.to_numeric(df["tries"], errors="coerce").fillna(0).astype(int)

    return df


# =============================================================================
# Generation Functions (generate_*)
# =============================================================================


def generate_tests_per_project_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table for test count changes per project and variant.

    Args:
        df: DataFrame from compute_test_suite_change_statistics

    Returns:
        Complete LaTeX table string
    """
    # Prepare data for LaTeX generation using the rewritten build_latex_table_content
    df_table = df.copy()

    # Replace project and variant names with macros
    df_table = replace_project_names_with_macros(df_table, "project_name")
    df_table = replace_variant_names_with_macros(df_table, "b_variant")

    # Build table rows manually (like legacy implementation)
    table_rows = []
    for _, row in df_table.iterrows():
        delta_sign = "+" if row["tests_delta"] >= 0 else ""
        delta_pct_sign = "+" if row["tests_delta_pct"] >= 0 else ""

        row_str = (
            f"{row['project_name']} & {row['b_variant']} & "
            f"{int(row['tests_before']):,} & {int(row['added_tests']):,} & {int(row['removed_tests']):,} & "
            f"{int(row['tests_after']):,} & {delta_sign}{int(row['tests_delta']):,} & "
            f"{delta_pct_sign}{row['tests_delta_pct']:.1f}\\%"
        )
        table_rows.append(row_str)

    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(" & ") for row in table_rows])
    rows_df.columns = [
        "Project",
        "Variant",
        "Before",
        "Added",
        "Removed",
        "After",
        "Delta",
        "DeltaPct",
    ]

    # Header rows for the table
    header_rows = [
        " & & \\multicolumn{6}{c}{Tests} \\\\",
        "\\cmidrule(lr){3-8}",
        "Project & Variant & Before & Added & Removed & After & Delta & Delta \\% \\\\",
    ]

    table_content = build_latex_table_content(
        rows_df,
        caption="Number of tests before and after generalization, with changes, per project.",
        label="tab:tests-per-project",
        column_spec="llrrrrrr",
        header_rows=header_rows,
        add_midrules=True,
        grouping_column="Project",
        grouping_func=get_project_type,
    )

    return table_content


def generate_lines_per_project_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table for line count changes per project and variant.

    Args:
        df: DataFrame from compute_line_count_change_statistics

    Returns:
        Complete LaTeX table string
    """
    # Prepare data for LaTeX generation
    df_table = df.copy()

    # Replace project and variant names with macros
    df_table = replace_project_names_with_macros(df_table, "project_name")
    df_table = replace_variant_names_with_macros(df_table, "b_variant")

    # Build table rows manually
    table_rows = []
    for _, row in df_table.iterrows():
        delta_sign = "+" if row["lines_delta"] >= 0 else ""
        delta_pct_sign = "+" if row["lines_delta_pct"] >= 0 else ""

        row_str = (
            f"{row['project_name']} & {row['b_variant']} & "
            f"{int(row['lines_before']):,} & {int(row['added_lines']):,} & {int(row['removed_lines']):,} & "
            f"{int(row['lines_after']):,} & {delta_sign}{int(row['lines_delta']):,} & "
            f"{delta_pct_sign}{row['lines_delta_pct']:.1f}\\%"
        )
        table_rows.append(row_str)

    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(" & ") for row in table_rows])
    rows_df.columns = [
        "Project",
        "Variant",
        "Before",
        "Added",
        "Removed",
        "After",
        "Delta",
        "DeltaPct",
    ]

    # Header rows for the table
    header_rows = [
        " & & \\multicolumn{6}{c}{Lines} \\\\",
        "\\cmidrule(lr){3-8}",
        "Project & Variant & Before & Added & Removed & After & Delta & Delta \\% \\\\",
    ]

    table_content = build_latex_table_content(
        rows_df,
        caption="Number of test lines before and after generalization, with changes, per project.",
        label="tab:lines-per-project",
        column_spec="llrrrrrr",
        header_rows=header_rows,
        add_midrules=True,
        grouping_column="Project",
        grouping_func=get_project_type,
    )

    return table_content


def generate_runtime_per_project_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table for runtime changes per project and variant.

    Args:
        df: DataFrame from compute_runtime_change_statistics

    Returns:
        Complete LaTeX table string
    """
    # Prepare data for LaTeX generation
    df_table = df.copy()

    # Replace project and variant names with macros
    df_table = replace_project_names_with_macros(df_table, "project_name")
    df_table = replace_variant_names_with_macros(df_table, "b_variant")

    # Build table rows manually
    table_rows = []
    for _, row in df_table.iterrows():
        delta_sign = "+" if row["runtime_delta"] >= 0 else ""
        delta_pct_sign = "+" if row["runtime_delta_pct"] >= 0 else ""

        row_str = (
            f"{row['project_name']} & {row['b_variant']} & "
            f"{row['runtime_before']:.2f} & {row['added_runtime']:.2f} & {row['removed_runtime']:.2f} & "
            f"{row['runtime_after']:.2f} & {delta_sign}{row['runtime_delta']:.2f} & "
            f"{delta_pct_sign}{row['runtime_delta_pct']:.1f}\\%"
        )
        table_rows.append(row_str)

    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(" & ") for row in table_rows])
    rows_df.columns = [
        "Project",
        "Variant",
        "Before",
        "Added",
        "Removed",
        "After",
        "Delta",
        "DeltaPct",
    ]

    # Header rows for the table
    header_rows = [
        " & & \\multicolumn{6}{c}{Runtime (in seconds)} \\\\",
        "\\cmidrule(lr){3-8}",
        "Project & Variant & Before & Added & Removed & After & Delta & Delta \\% \\\\",
    ]

    table_content = build_latex_table_content(
        rows_df,
        caption="Test suite runtime before and after generalization, with changes, per project.",
        label="tab:runtime-per-project",
        column_spec="llrrrrrr",
        header_rows=header_rows,
        add_midrules=True,
        grouping_column="Project",
        grouping_func=get_project_type,
    )

    return table_content


# =============================================================================
# CSV Export Functions
# =============================================================================


def generate_tests_per_project_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for test count changes per project and variant.

    Args:
        df: DataFrame from compute_test_suite_change_statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project_name"]),
                "baseline_variant": get_variant_macro(row["a_variant"]),
                "generalized_variant": get_variant_macro(row["b_variant"]),
                "tests_before": int(row["tests_before"]),
                "tests_added": int(row["added_tests"]),
                "tests_removed": int(row["removed_tests"]),
                "tests_after": int(row["tests_after"]),
                "tests_delta": int(row["tests_delta"]),
                "tests_delta_percentage": float(row["tests_delta_pct"]),
            }
        )

    return pd.DataFrame(csv_data)


def generate_lines_per_project_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for line count changes per project and variant.

    Args:
        df: DataFrame from compute_line_count_change_statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project_name"]),
                "baseline_variant": get_variant_macro(row["a_variant"]),
                "generalized_variant": get_variant_macro(row["b_variant"]),
                "lines_before": int(row["lines_before"]),
                "lines_added": int(row["added_lines"]),
                "lines_removed": int(row["removed_lines"]),
                "lines_after": int(row["lines_after"]),
                "lines_delta": int(row["lines_delta"]),
                "lines_delta_percentage": float(row["lines_delta_pct"]),
            }
        )

    return pd.DataFrame(csv_data)


def generate_runtime_per_project_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for runtime changes per project and variant.

    Args:
        df: DataFrame from compute_runtime_change_statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project_name"]),
                "baseline_variant": get_variant_macro(row["a_variant"]),
                "generalized_variant": get_variant_macro(row["b_variant"]),
                "runtime_before_seconds": float(row["runtime_before"]),
                "runtime_added_seconds": float(row["added_runtime"]),
                "runtime_removed_seconds": float(row["removed_runtime"]),
                "runtime_after_seconds": float(row["runtime_after"]),
                "runtime_delta_seconds": float(row["runtime_delta"]),
                "runtime_delta_percentage": float(row["runtime_delta_pct"]),
            }
        )

    return pd.DataFrame(csv_data)


def generate_test_runtime_differences_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for test vs generalization runtime comparison.

    Args:
        df: DataFrame from compute_test_vs_generalization_runtime_statistics

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "variant": get_variant_macro(row["variant"]),
                "mean_test_runtime_ms": float(row["mean_t_runtime_ms"]),
                "mean_generalization_runtime_ms": float(row["mean_g_runtime_ms"]),
                "mean_runtime_difference_ms": float(row["mean_runtime_diff_ms"]),
                "runtime_ratio_generalization_to_test": float(
                    row["ratio_of_mean_runtimes"]
                ),
                "tries_count": int(row["tries"]),
                "mean_runtime_difference_per_try_ms": float(
                    row["mean_runtime_diff_per_try_ms"]
                ),
            }
        )

    return pd.DataFrame(csv_data)


def get_test_filtering_impact_data(conn) -> pd.DataFrame:
    """Get data showing impact of filtering non-contributing generalized tests.

    Args:
        conn: Database connection

    Returns:
        DataFrame with total vs contributing generalization counts per project/variant
    """
    query = """
    WITH total_generalizations AS (
        SELECT 
            g.project_id,
            project_name(g.project_id) as project_name,
            g.variant,
            COUNT(*) as total_generated
        FROM generalization g
        JOIN project p ON g.project_id = p.id
        JOIN v_projects_successes ps ON ps.project_id = p.id
        WHERE 
            g.is_included = true
            AND p.use_test_generalization = true
            AND g.variant IN ('NAIVE_10_TRIES', 'NAIVE_50_TRIES', 'NAIVE_200_TRIES', 
                             'IMPROVED_10_TRIES', 'IMPROVED_50_TRIES', 'IMPROVED_200_TRIES')
        GROUP BY g.project_id, g.variant
    ),
    contributing_generalizations AS (
        SELECT 
            project_id,
            project_name,
            b_variant as variant,
            added_tests as contributing_count
        FROM mv_generalization_effects
        WHERE a_variant = 'ORIGINAL'
            AND b_variant IN ('NAIVE_10_TRIES', 'NAIVE_50_TRIES', 'NAIVE_200_TRIES', 
                             'IMPROVED_10_TRIES', 'IMPROVED_50_TRIES', 'IMPROVED_200_TRIES')
    )
    SELECT 
        tg.project_id,
        tg.project_name,
        tg.variant,
        tg.total_generated,
        COALESCE(cg.contributing_count, 0) as contributing_count,
        tg.total_generated - COALESCE(cg.contributing_count, 0) as would_be_filtered
    FROM total_generalizations tg
    LEFT JOIN contributing_generalizations cg 
        ON tg.project_id = cg.project_id AND tg.variant = cg.variant
    ORDER BY tg.project_name, tg.variant
    """
    return pd.read_sql_query(query, conn)


def compute_test_filtering_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Compute filtering statistics including retention percentages.

    Args:
        df: DataFrame from get_test_filtering_impact_data

    Returns:
        DataFrame with filtering statistics and retention percentages
    """
    # Calculate retention percentage
    df = df.copy()
    df["retention_percentage"] = (
        df["contributing_count"] / df["total_generated"] * 100
    ).round(1)

    # Add algorithm type for analysis
    df["algorithm"] = df["variant"].str.extract(r"^(NAIVE|IMPROVED)")[0]
    df["tries"] = df["variant"].str.extract(r"_(\d+)_TRIES")[0].astype(int)

    return df


def generate_test_filtering_breakdown_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for detailed test filtering breakdown per project/variant.

    Args:
        df: DataFrame from compute_test_filtering_statistics

    Returns:
        DataFrame formatted for CSV export with per-project details
    """
    from .exports import standardize_project_name, get_variant_macro

    csv_data = []

    for _, row in df.iterrows():
        csv_data.append(
            {
                "project_name": standardize_project_name(row["project_name"]),
                "variant": get_variant_macro(row["variant"]),
                "algorithm": row["algorithm"],
                "tries": int(row["tries"]),
                "total_generated": int(row["total_generated"]),
                "contributing_count": int(row["contributing_count"]),
                "would_be_filtered": int(row["would_be_filtered"]),
                "retention_percentage": round(row["retention_percentage"], 1),
            }
        )

    return pd.DataFrame(csv_data)


def generate_test_filtering_summary_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for aggregated test filtering summary statistics.

    Args:
        df: DataFrame from compute_test_filtering_statistics

    Returns:
        DataFrame with summary statistics by algorithm, tries, and overall
    """
    csv_data = []

    # Overall summary
    total_generated = df["total_generated"].sum()
    total_contributing = df["contributing_count"].sum()
    total_filtered = df["would_be_filtered"].sum()
    overall_retention = (
        (total_contributing / total_generated * 100) if total_generated > 0 else 0
    )

    csv_data.append(
        {
            "summary_type": "overall",
            "algorithm": "ALL",
            "tries": "ALL",
            "variant_count": len(df),
            "total_generated": int(total_generated),
            "contributing_count": int(total_contributing),
            "would_be_filtered": int(total_filtered),
            "retention_percentage": round(overall_retention, 1),
        }
    )

    # By algorithm
    algo_summary = (
        df.groupby("algorithm")
        .agg(
            {
                "total_generated": "sum",
                "contributing_count": "sum",
                "would_be_filtered": "sum",
                "variant": "count",
            }
        )
        .reset_index()
    )

    for _, row in algo_summary.iterrows():
        retention = (
            (row["contributing_count"] / row["total_generated"] * 100)
            if row["total_generated"] > 0
            else 0
        )
        csv_data.append(
            {
                "summary_type": "by_algorithm",
                "algorithm": row["algorithm"],
                "tries": "ALL",
                "variant_count": int(row["variant"]),
                "total_generated": int(row["total_generated"]),
                "contributing_count": int(row["contributing_count"]),
                "would_be_filtered": int(row["would_be_filtered"]),
                "retention_percentage": round(retention, 1),
            }
        )

    # By tries count
    tries_summary = (
        df.groupby("tries")
        .agg(
            {
                "total_generated": "sum",
                "contributing_count": "sum",
                "would_be_filtered": "sum",
                "variant": "count",
            }
        )
        .reset_index()
    )

    for _, row in tries_summary.iterrows():
        retention = (
            (row["contributing_count"] / row["total_generated"] * 100)
            if row["total_generated"] > 0
            else 0
        )
        csv_data.append(
            {
                "summary_type": "by_tries",
                "algorithm": "ALL",
                "tries": str(int(row["tries"])),
                "variant_count": int(row["variant"]),
                "total_generated": int(row["total_generated"]),
                "contributing_count": int(row["contributing_count"]),
                "would_be_filtered": int(row["would_be_filtered"]),
                "retention_percentage": round(retention, 1),
            }
        )

    # By algorithm and tries
    algo_tries_summary = (
        df.groupby(["algorithm", "tries"])
        .agg(
            {
                "total_generated": "sum",
                "contributing_count": "sum",
                "would_be_filtered": "sum",
                "variant": "count",
            }
        )
        .reset_index()
    )

    for _, row in algo_tries_summary.iterrows():
        retention = (
            (row["contributing_count"] / row["total_generated"] * 100)
            if row["total_generated"] > 0
            else 0
        )
        csv_data.append(
            {
                "summary_type": "by_algorithm_tries",
                "algorithm": row["algorithm"],
                "tries": str(int(row["tries"])),
                "variant_count": int(row["variant"]),
                "total_generated": int(row["total_generated"]),
                "contributing_count": int(row["contributing_count"]),
                "would_be_filtered": int(row["would_be_filtered"]),
                "retention_percentage": round(retention, 1),
            }
        )

    return pd.DataFrame(csv_data)
