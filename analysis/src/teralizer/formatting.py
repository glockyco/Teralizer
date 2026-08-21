"""Data formatting and standardization utilities for teralizer analysis.

This module provides functions for project ordering, data standardization, and
LaTeX table formatting that are commonly used across analysis notebooks.
"""

import pandas as pd
from typing import Dict, List, Optional, Callable, cast
from natsort import natsorted
from .exports import (
    get_table_group_order,
    get_project_within_type_order,
    get_dataset_macro,
    get_variant_macro,
)


# =============================================================================
# DataFrame ordering functions
# =============================================================================


def sort_dataframe_by_project(
    df: pd.DataFrame,
    project_column: str = "project_name",
    variant_column: str = "variant",
) -> pd.DataFrame:
    """Sort DataFrame using standard project ordering.

    Uses get_table_group_order and get_project_within_type_order from exports
    to ensure consistent ordering across all analysis outputs.

    Args:
        df: DataFrame to sort
        project_column: Name of column containing project names
        variant_column: Name of column containing variant names (used for ordering)

    Returns:
        DataFrame sorted by project ordering

    Raises:
        KeyError: If required columns are missing from DataFrame
        ValueError: If DataFrame is empty when ordering is required
    """
    if df.empty:
        raise ValueError("Cannot sort empty DataFrame")

    if project_column not in df.columns:
        raise KeyError(f"Project column '{project_column}' not found in DataFrame")

    # Get project ordering mappings
    project_within_type_order = get_project_within_type_order()

    # Create sorting key function
    def sort_key(idx):
        project_name = cast(str, df.loc[idx, project_column])
        variant = (
            cast(str, df.loc[idx, variant_column])
            if variant_column in df.columns
            else "INITIAL"
        )

        table_group_order = get_table_group_order(project_name, variant)
        within_type_order = project_within_type_order.get(project_name, 99)

        return (table_group_order, within_type_order)

    # Sort using natsorted with custom key
    sorted_index = natsorted(df.index, key=sort_key)
    return df.reindex(sorted_index)


def sort_dataframe_by_variant(
    df: pd.DataFrame, variant_column: str = "variant"
) -> pd.DataFrame:
    """Sort DataFrame by variant in standard order.

    Args:
        df: DataFrame to sort
        variant_column: Name of column containing variant names

    Returns:
        DataFrame sorted by variant ordering

    Raises:
        KeyError: If variant column is missing from DataFrame
        ValueError: If DataFrame is empty when ordering is required
    """
    if df.empty:
        raise ValueError("Cannot sort empty DataFrame")

    if variant_column not in df.columns:
        raise KeyError(f"Variant column '{variant_column}' not found in DataFrame")

    # Standard variant order
    variant_order = {
        "ORIGINAL": 0,
        "INITIAL": 1,
        "SHARED": 2,
        "BASELINE": 3,
        "NAIVE_10_TRIES": 4,
        "NAIVE_50_TRIES": 5,
        "NAIVE_200_TRIES": 6,
        "IMPROVED_10_TRIES": 7,
        "IMPROVED_50_TRIES": 8,
        "IMPROVED_200_TRIES": 9,
    }

    # Create sort key
    df = df.copy()
    df.loc[:, "_sort_key"] = df[variant_column].map(lambda x: variant_order.get(x, 99))
    df_sorted = df.sort_values("_sort_key").drop("_sort_key", axis=1)

    return df_sorted


def apply_standard_ordering(
    df: pd.DataFrame,
    project_column: str = "project_name",
    variant_column: str = "variant",
) -> pd.DataFrame:
    """Apply combined project and variant ordering to DataFrame.

    Args:
        df: DataFrame to sort
        project_column: Name of column containing project names
        variant_column: Name of column containing variant names

    Returns:
        DataFrame sorted by project then variant ordering

    Raises:
        KeyError: If required columns are missing from DataFrame
        ValueError: If DataFrame is empty when ordering is required
    """
    return sort_dataframe_by_project(df, project_column, variant_column)


# =============================================================================
# Data standardization functions
# =============================================================================


def format_percentage_columns(
    df: pd.DataFrame, columns: List[str], decimal_places: int = 1
) -> pd.DataFrame:
    """Format numeric columns as percentages with consistent precision.

    Args:
        df: DataFrame to format
        columns: List of column names to format as percentages
        decimal_places: Number of decimal places to show

    Returns:
        DataFrame with formatted percentage columns

    Raises:
        KeyError: If any specified column is missing from DataFrame
    """
    df = df.copy()

    for col in columns:
        if col not in df.columns:
            raise KeyError(f"Column '{col}' not found in DataFrame")

        # Convert to percentage and format
        df.isetitem(df.columns.get_loc(col), (df[col] * 100).round(decimal_places))

    return df


def format_thousands_separator(df: pd.DataFrame, columns: List[str]) -> pd.DataFrame:
    """Add thousands separators to numeric columns.

    Args:
        df: DataFrame to format
        columns: List of column names to add thousands separators

    Returns:
        DataFrame with formatted numeric columns

    Raises:
        KeyError: If any specified column is missing from DataFrame
    """
    df = df.copy()

    for col in columns:
        if col not in df.columns:
            raise KeyError(f"Column '{col}' not found in DataFrame")

        if pd.api.types.is_numeric_dtype(df[col]):
            df.isetitem(
                df.columns.get_loc(col),
                df[col].apply(lambda x: f"{x:,.0f}" if pd.notna(x) else x),
            )

    return df


def standardize_column_names(
    df: pd.DataFrame, mapping_dict: Dict[str, str]
) -> pd.DataFrame:
    """Rename columns using provided mapping dictionary.

    Args:
        df: DataFrame to rename columns
        mapping_dict: Dictionary mapping old names to new names

    Returns:
        DataFrame with renamed columns
    """
    return df.rename(columns=mapping_dict)


def replace_project_names_with_macros(
    df: pd.DataFrame, project_column: str = "project_name"
) -> pd.DataFrame:
    """Replace project names with LaTeX macros for paper integration.

    Args:
        df: DataFrame to modify
        project_column: Name of column containing project names

    Returns:
        DataFrame with LaTeX macros instead of project names

    Raises:
        KeyError: If project column is missing from DataFrame
    """
    if project_column not in df.columns:
        raise KeyError(f"Project column '{project_column}' not found in DataFrame")

    df = df.copy()

    # Known project names that have macros
    known_projects = {
        "eqbench-es-default-1s",
        "eqbench-es-default-10s",
        "eqbench-es-default-60s",
        "commons-utils-es-default-1s",
        "commons-utils-es-default-10s",
        "commons-utils-es-default-60s",
        "commons-utils",
        "repo-reapers",
    }

    def replace_with_macro(project_name):
        if project_name in known_projects:
            return get_dataset_macro(project_name)
        # Handle repo-reapers summary rows
        elif project_name.startswith("repo-reapers "):
            suffix = project_name.replace("repo-reapers ", "")
            return f"{get_dataset_macro('repo-reapers')} {suffix}"
        else:
            return project_name

    df.isetitem(
        df.columns.get_loc(project_column),
        df[project_column].apply(replace_with_macro),
    )

    return df


def replace_variant_names_with_macros(
    df: pd.DataFrame, variant_column: str = "variant"
) -> pd.DataFrame:
    """Replace variant names with LaTeX macros for paper integration.

    Args:
        df: DataFrame to modify
        variant_column: Name of column containing variant names

    Returns:
        DataFrame with LaTeX macros instead of variant names

    Raises:
        KeyError: If variant column is missing from DataFrame
    """
    if variant_column not in df.columns:
        raise KeyError(f"Variant column '{variant_column}' not found in DataFrame")

    df = df.copy()

    # Known variant names that have macros
    known_variants = {
        "ORIGINAL",
        "INITIAL",
        "SHARED",
        "BASELINE",
        "NAIVE_10_TRIES",
        "NAIVE_50_TRIES",
        "NAIVE_200_TRIES",
        "IMPROVED_10_TRIES",
        "IMPROVED_50_TRIES",
        "IMPROVED_200_TRIES",
    }

    df.isetitem(
        df.columns.get_loc(variant_column),
        df[variant_column].apply(
            lambda x: get_variant_macro(x) if x in known_variants else x
        ),
    )

    return df


# =============================================================================
# LaTeX table helpers
# =============================================================================


def add_midrules_between_groups(
    table_rows: List[str],
    df: pd.DataFrame,
    grouping_column: str,
    grouping_func: Optional[Callable] = None,
) -> List[str]:
    """Add LaTeX midrules between different groups in tables.

    Args:
        table_rows: List of LaTeX table row strings
        df: Original DataFrame to determine groupings
        grouping_column: Name of column to use for grouping
        grouping_func: Optional function to transform column value for grouping.
                      If None, uses column value directly.

    Returns:
        List of table rows with midrules inserted

    Raises:
        KeyError: If grouping column is missing from DataFrame
        ValueError: If table_rows and DataFrame lengths don't match
    """
    if grouping_column not in df.columns:
        raise KeyError(f"Grouping column '{grouping_column}' not found in DataFrame")

    if len(table_rows) != len(df):
        raise ValueError(
            f"Length mismatch: {len(table_rows)} table rows vs {len(df)} DataFrame rows"
        )

    result_rows = []
    prev_group = None

    for i, (row, idx) in enumerate(zip(table_rows, df.index)):
        column_value = df.loc[idx, grouping_column]
        current_group = grouping_func(column_value) if grouping_func else column_value

        # Add midrule if group changes (but not at the beginning)
        if prev_group is not None and current_group != prev_group:
            result_rows.append(r"\midrule")

        result_rows.append(row)
        prev_group = current_group

    return result_rows


def add_midrules_between_project_groups(
    table_rows: List[str], df: pd.DataFrame, project_column: str = "project_name"
) -> List[str]:
    """Add LaTeX midrules between different project groups in tables.

    Args:
        table_rows: List of LaTeX table row strings
        df: Original DataFrame to determine groupings
        project_column: Name of column containing project names

    Returns:
        List of table rows with midrules inserted

    Raises:
        KeyError: If project column is missing from DataFrame
        ValueError: If table_rows and DataFrame lengths don't match
    """
    from .exports import get_project_type

    return add_midrules_between_groups(table_rows, df, project_column, get_project_type)


def format_table_with_macros(
    df: pd.DataFrame,
    project_column: str = "project_name",
    variant_column: str = "variant",
) -> pd.DataFrame:
    """Apply all macro replacements for LaTeX table output.

    Args:
        df: DataFrame to format
        project_column: Name of column containing project names
        variant_column: Name of column containing variant names

    Returns:
        DataFrame with LaTeX macros applied

    Raises:
        KeyError: If required columns are missing from DataFrame
    """
    df = df.copy()

    # Replace project names with macros
    if project_column in df.columns:
        df = replace_project_names_with_macros(df, project_column)

    # Replace variant names with macros
    if variant_column in df.columns:
        df = replace_variant_names_with_macros(df, variant_column)

    return df


def build_latex_table_content(
    df: pd.DataFrame,
    caption: Optional[str] = None,
    label: Optional[str] = None,
    column_spec: Optional[str] = None,
    header_rows: Optional[List[str]] = None,
    add_midrules: bool = False,
    grouping_column: Optional[str] = None,
    grouping_func: Optional[Callable] = None,
) -> str:
    """Generate complete LaTeX table content from DataFrame without pandas to_latex.

    Args:
        df: DataFrame to convert to LaTeX
        caption: Table caption text
        label: LaTeX label for referencing
        column_spec: LaTeX column specification (e.g., 'lrrrrrrr')
        header_rows: List of custom header row strings to insert after toprule
        add_midrules: Whether to add midrules between groups
        grouping_column: Name of column to use for grouping when add_midrules is True
        grouping_func: Optional function to transform column value for grouping

    Returns:
        Complete LaTeX table content string

    Raises:
        ValueError: If DataFrame is empty
        KeyError: If grouping_column is missing when add_midrules is True
    """
    if df.empty:
        raise ValueError("Cannot build LaTeX table from empty DataFrame")

    # Build LaTeX table manually
    lines = []

    # Table environment start
    lines.append("\\begin{table}[H]")

    # Caption and label
    if caption:
        lines.append(f"  \\caption{{{caption}}}")
    if label:
        lines.append(f"  \\label{{{label}}}")

    # Tabular environment start
    col_spec = column_spec or ("l" * len(df.columns))
    lines.append(f"  \\begin{{tabular}}{{{col_spec}}}")
    lines.append("    \\toprule")

    # Custom header rows
    if header_rows:
        for header_row in header_rows:
            lines.append(f"    {header_row}")
    else:
        # Default header from DataFrame columns
        header = " & ".join(df.columns) + " \\\\"
        lines.append(f"    {header}")

    lines.append("    \\midrule")

    # Data rows
    data_rows = []
    for _, row in df.iterrows():
        # Convert each value to string, handling NaN and None
        row_values = []
        for val in row:
            if pd.isna(val):
                row_values.append("")
            else:
                row_values.append(str(val))

        data_row = " & ".join(row_values) + " \\\\"
        data_rows.append(data_row)

    # Add midrules between groups if requested
    if add_midrules and grouping_column and grouping_column in df.columns:
        processed_rows = add_midrules_between_groups(
            data_rows, df, grouping_column, grouping_func
        )
        for row in processed_rows:
            lines.append(f"    {row}")
    else:
        for row in data_rows:
            lines.append(f"    {row}")

    # Table end
    lines.append("    \\bottomrule")
    lines.append("  \\end{tabular}")
    lines.append("\\end{table}")

    return "\n".join(lines)
