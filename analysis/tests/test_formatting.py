"""Tests for the formatting module.

Tests for project ordering, data formatting, and LaTeX table generation functions.
"""

import pytest
import pandas as pd
from teralizer.formatting import (
    sort_dataframe_by_project,
    sort_dataframe_by_variant,
    format_percentage_columns,
    format_thousands_separator,
    standardize_column_names,
    replace_project_names_with_macros,
    replace_variant_names_with_macros,
    format_table_with_macros,
    add_midrules_between_project_groups,
    build_latex_table_content,
)


def test_sort_dataframe_by_project_empty_raises_error():
    """Test that sorting empty DataFrame raises ValueError."""
    df = pd.DataFrame()

    with pytest.raises(ValueError, match="Cannot sort empty DataFrame"):
        sort_dataframe_by_project(df)


def test_sort_dataframe_by_project_missing_column_raises_error():
    """Test that missing project column raises KeyError."""
    df = pd.DataFrame({"variant": ["ORIGINAL", "INITIAL"]})

    with pytest.raises(KeyError, match="Project column 'project_name' not found"):
        sort_dataframe_by_project(df)


def test_sort_dataframe_by_project_basic_ordering(known_projects):
    """Test basic project ordering functionality."""
    # Create test data with mixed project order
    df = pd.DataFrame(
        {
            "project_name": [
                "commons-utils",
                "eqbench-es-default-1s",
                "commons-utils-es-default-1s",
            ],
            "variant": ["INITIAL", "INITIAL", "INITIAL"],
            "value": [1, 2, 3],
        }
    )

    result = sort_dataframe_by_project(df)

    # Should be ordered: eqbench first, then commons-es, then commons-dev
    expected_order = [
        "eqbench-es-default-1s",
        "commons-utils-es-default-1s",
        "commons-utils",
    ]
    assert result["project_name"].tolist() == expected_order


def test_sort_dataframe_by_variant_empty_raises_error():
    """Test that sorting empty DataFrame by variant raises ValueError."""
    df = pd.DataFrame()

    with pytest.raises(ValueError, match="Cannot sort empty DataFrame"):
        sort_dataframe_by_variant(df)


def test_sort_dataframe_by_variant_missing_column_raises_error():
    """Test that missing variant column raises KeyError."""
    df = pd.DataFrame({"project_name": ["test"]})

    with pytest.raises(KeyError, match="Variant column 'variant' not found"):
        sort_dataframe_by_variant(df)


def test_sort_dataframe_by_variant_ordering():
    """Test variant ordering functionality."""
    df = pd.DataFrame(
        {
            "variant": ["IMPROVED_10_TRIES", "ORIGINAL", "BASELINE", "INITIAL"],
            "value": [1, 2, 3, 4],
        }
    )

    result = sort_dataframe_by_variant(df)

    expected_order = ["ORIGINAL", "INITIAL", "BASELINE", "IMPROVED_10_TRIES"]
    assert result["variant"].tolist() == expected_order


def test_format_percentage_columns_missing_column_raises_error():
    """Test that missing column raises KeyError."""
    df = pd.DataFrame({"value": [0.5, 0.75]})

    with pytest.raises(KeyError, match="Column 'missing' not found"):
        format_percentage_columns(df, ["missing"])


def test_format_percentage_columns_basic():
    """Test percentage formatting."""
    df = pd.DataFrame({"rate1": [0.123, 0.456, 0.789], "rate2": [0.1, 0.2, 0.3]})

    result = format_percentage_columns(df, ["rate1", "rate2"], decimal_places=1)

    assert result["rate1"].tolist() == [12.3, 45.6, 78.9]
    assert result["rate2"].tolist() == [10.0, 20.0, 30.0]


def test_format_thousands_separator_missing_column_raises_error():
    """Test that missing column raises KeyError."""
    df = pd.DataFrame({"value": [1000, 2000]})

    with pytest.raises(KeyError, match="Column 'missing' not found"):
        format_thousands_separator(df, ["missing"])


def test_format_thousands_separator_basic():
    """Test thousands separator formatting."""
    df = pd.DataFrame(
        {"big_numbers": [1000, 50000, 1234567], "small_numbers": [100, 200, 300]}
    )

    result = format_thousands_separator(df, ["big_numbers"])

    assert result["big_numbers"].tolist() == ["1,000", "50,000", "1,234,567"]
    # small_numbers should be unchanged
    assert result["small_numbers"].tolist() == [100, 200, 300]


def test_standardize_column_names():
    """Test column renaming."""
    df = pd.DataFrame({"old_name1": [1, 2], "old_name2": [3, 4], "keep_name": [5, 6]})

    mapping = {"old_name1": "new_name1", "old_name2": "new_name2"}
    result = standardize_column_names(df, mapping)

    expected_columns = ["new_name1", "new_name2", "keep_name"]
    assert list(result.columns) == expected_columns


def test_replace_project_names_with_macros_missing_column_raises_error():
    """Test that missing project column raises KeyError."""
    df = pd.DataFrame({"variant": ["ORIGINAL"]})

    with pytest.raises(KeyError, match="Project column 'project_name' not found"):
        replace_project_names_with_macros(df)


def test_replace_project_names_with_macros():
    """Test project name to macro replacement."""
    df = pd.DataFrame(
        {
            "project_name": [
                "eqbench-es-default-1s",
                "commons-utils",
                "unknown-project",
            ],
            "value": [1, 2, 3],
        }
    )

    result = replace_project_names_with_macros(df)

    # Known projects should be replaced with macros
    assert result.loc[0, "project_name"] == r"\DatasetEqBenchA{}"
    assert result.loc[1, "project_name"] == r"\DatasetCommonsDev{}"
    # Unknown projects should remain unchanged
    assert result.loc[2, "project_name"] == "unknown-project"


def test_replace_variant_names_with_macros_missing_column_raises_error():
    """Test that missing variant column raises KeyError."""
    df = pd.DataFrame({"project_name": ["test"]})

    with pytest.raises(KeyError, match="Variant column 'variant' not found"):
        replace_variant_names_with_macros(df)


def test_replace_variant_names_with_macros():
    """Test variant name to macro replacement."""
    df = pd.DataFrame(
        {
            "variant": ["ORIGINAL", "IMPROVED_10_TRIES", "UNKNOWN_VARIANT"],
            "value": [1, 2, 3],
        }
    )

    result = replace_variant_names_with_macros(df)

    # Known variants should be replaced with macros
    assert result.loc[0, "variant"] == r"\VariantOriginal{}"
    assert result.loc[1, "variant"] == r"\VariantImprovedA{}"
    # Unknown variants should remain unchanged
    assert result.loc[2, "variant"] == "UNKNOWN_VARIANT"


def test_format_table_with_macros():
    """Test combined macro formatting."""
    df = pd.DataFrame(
        {
            "project_name": ["eqbench-es-default-1s", "commons-utils"],
            "variant": ["ORIGINAL", "INITIAL"],
            "value": [1, 2],
        }
    )

    result = format_table_with_macros(df)

    assert result.loc[0, "project_name"] == r"\DatasetEqBenchA{}"
    assert result.loc[0, "variant"] == r"\VariantOriginal{}"
    assert result.loc[1, "project_name"] == r"\DatasetCommonsDev{}"
    assert result.loc[1, "variant"] == r"\VariantInitial{}"


def test_add_midrules_missing_column_raises_error():
    """Test that missing project column raises KeyError."""
    df = pd.DataFrame({"variant": ["ORIGINAL"]})
    table_rows = ["row1"]

    with pytest.raises(KeyError, match="Grouping column 'project_name' not found"):
        add_midrules_between_project_groups(table_rows, df)


def test_add_midrules_length_mismatch_raises_error():
    """Test that length mismatch raises ValueError."""
    df = pd.DataFrame({"project_name": ["test1", "test2"]})
    table_rows = ["row1"]  # Only one row for two DataFrame rows

    with pytest.raises(ValueError, match="Length mismatch"):
        add_midrules_between_project_groups(table_rows, df)


def test_add_midrules_between_project_groups():
    """Test midrule insertion between different project types."""
    df = pd.DataFrame(
        {
            "project_name": [
                "eqbench-es-default-1s",
                "eqbench-es-default-10s",
                "commons-utils-es-default-1s",
                "commons-utils",
            ]
        }
    )
    table_rows = ["row1 \\\\", "row2 \\\\", "row3 \\\\", "row4 \\\\"]

    result = add_midrules_between_project_groups(table_rows, df)

    # Should have midrules between eqbench -> commons-es and commons-es -> commons-dev
    expected = [
        "row1 \\\\",
        "row2 \\\\",
        "\\midrule",
        "row3 \\\\",
        "\\midrule",
        "row4 \\\\",
    ]
    assert result == expected


def test_build_latex_table_content_empty_raises_error():
    """Test that empty DataFrame raises ValueError."""
    df = pd.DataFrame()

    with pytest.raises(
        ValueError, match="Cannot build LaTeX table from empty DataFrame"
    ):
        build_latex_table_content(df)


def test_build_latex_table_content_with_midrules_no_grouping_column():
    """Test that midrules are disabled when no valid grouping column is provided."""
    df = pd.DataFrame({"variant": ["ORIGINAL"]})

    # Should not raise error, just disable midrules
    result = build_latex_table_content(
        df, add_midrules=True, grouping_column="missing_column"
    )

    # Should generate table without midrules (no error)
    assert "\\begin{table}[H]" in result
    assert "\\bottomrule" in result


def test_build_latex_table_content_basic():
    """Test basic LaTeX table generation."""
    df = pd.DataFrame(
        {"project_name": ["eqbench-es-default-1s", "commons-utils"], "value": [1, 2]}
    )

    result = build_latex_table_content(df, add_midrules=False)

    # Check that it contains LaTeX table structure
    assert "\\begin{tabular}" in result
    assert "\\toprule" in result
    assert "\\bottomrule" in result
    assert "eqbench-es-default-1s" in result
    assert "commons-utils" in result


@pytest.mark.parametrize(
    "func",
    [sort_dataframe_by_project, sort_dataframe_by_variant, format_table_with_macros],
)
def test_functions_return_dataframes(func):
    """Test that formatting functions return DataFrames."""
    # Create minimal valid test data for each function
    if func == sort_dataframe_by_variant:
        df = pd.DataFrame({"variant": ["ORIGINAL", "INITIAL"]})
        result = func(df)
    else:
        df = pd.DataFrame(
            {"project_name": ["eqbench-es-default-1s"], "variant": ["ORIGINAL"]}
        )
        result = func(df)

    assert isinstance(result, pd.DataFrame)
    assert not result.empty
