"""RQ2: Effects on Test Suite Size and Runtime - Analysis functions for secondary effects.

This module provides functions to analyze how test generalization affects test suite
characteristics: test count, lines of code, and execution runtime changes between
ORIGINAL and generalized variants (NAIVE, IMPROVED).
"""

import pandas as pd
from typing import List, Optional, cast

from .formatting import (
    sort_dataframe_by_project,
)


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
    return cast(
        pd.DataFrame,
        df[
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
        ],
    )


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
    return cast(
        pd.DataFrame,
        df[
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
        ],
    )


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
    return cast(
        pd.DataFrame,
        df[
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
        ],
    )


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
        values = (
            cast(pd.Series, pd.to_numeric(df_sorted[col], errors="coerce"))
            .fillna(0)
            .astype(int)
        )
        df_sorted.isetitem(df_sorted.columns.get_loc(col), values)

    df_sorted.isetitem(
        df_sorted.columns.get_loc("tests_delta_pct"),
        cast(
            pd.Series,
            pd.to_numeric(df_sorted["tests_delta_pct"], errors="coerce"),
        ).fillna(0.0),
    )

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
        values = (
            cast(pd.Series, pd.to_numeric(df_sorted[col], errors="coerce"))
            .fillna(0)
            .astype(int)
        )
        df_sorted.isetitem(df_sorted.columns.get_loc(col), values)

    df_sorted.isetitem(
        df_sorted.columns.get_loc("lines_delta_pct"),
        cast(
            pd.Series,
            pd.to_numeric(df_sorted["lines_delta_pct"], errors="coerce"),
        ).fillna(0.0),
    )

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
        values = cast(pd.Series, pd.to_numeric(df_sorted[col], errors="coerce")).fillna(
            0.0
        )
        df_sorted.isetitem(df_sorted.columns.get_loc(col), values)

    df_sorted.isetitem(
        df_sorted.columns.get_loc("runtime_delta_pct"),
        cast(
            pd.Series,
            pd.to_numeric(df_sorted["runtime_delta_pct"], errors="coerce"),
        ).fillna(0.0),
    )

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
        values = cast(pd.Series, pd.to_numeric(df[col], errors="coerce")).fillna(0.0)
        df.isetitem(df.columns.get_loc(col), values)

    df.isetitem(
        df.columns.get_loc("tries"),
        cast(pd.Series, pd.to_numeric(df["tries"], errors="coerce"))
        .fillna(0)
        .astype(int),
    )

    return df
