"""
Tests for the queries module.

Basic tests to ensure query functions work correctly and return DataFrames
with expected columns.
"""

import pytest
import pandas as pd
from teralizer.queries import (
    get_teralizer_projects_with_runtime,
    get_mutation_results_by_project_variant,
    get_exclusions_all,
    get_runtime_comparison_by_variant
)


def test_connections(dev_conn, test_conn):
    """Test that database connections work."""
    assert dev_conn is not None
    assert test_conn is not None


def test_teralizer_projects_with_runtime(dev_conn, known_projects):
    """Test that teralizer projects query returns expected columns."""
    df = get_teralizer_projects_with_runtime(dev_conn)
    
    assert isinstance(df, pd.DataFrame)
    assert not df.empty
    expected_columns = {'id', 'project_name', 'runtime'}
    assert expected_columns.issubset(set(df.columns))
    
    # Check data types
    assert df['id'].dtype in ['int64', 'Int64']
    assert df['runtime'].dtype in ['float64', 'Float64']
    
    # Check that returned project names are known projects
    returned_projects = set(df['project_name'].unique())
    known_projects_set = set(known_projects)
    assert returned_projects.issubset(known_projects_set), f"Unknown projects: {returned_projects - known_projects_set}"


def test_mutation_results_by_project_variant(dev_conn, basic_variants):
    """Test that mutation results query returns expected columns."""
    df = get_mutation_results_by_project_variant(dev_conn)
    
    assert isinstance(df, pd.DataFrame)
    assert not df.empty
    expected_columns = {'project_id', 'project_name', 'variant', 'total'}
    assert expected_columns.issubset(set(df.columns))
    
    # Check that only ORIGINAL and INITIAL variants are returned
    returned_variants = set(df['variant'].unique())
    expected_variants = set(basic_variants)
    assert returned_variants == expected_variants, f"Expected {expected_variants}, got {returned_variants}"


def test_exclusions_all(dev_conn, known_variants):
    """Test that exclusions query returns expected columns."""
    df = get_exclusions_all(dev_conn)
    
    assert isinstance(df, pd.DataFrame)
    assert not df.empty
    expected_columns = {'variant', 'level', 'is_included', 'excluded_by', 'count'}
    assert expected_columns.issubset(set(df.columns))
    
    # Check that returned variants are known variants
    returned_variants = set(df['variant'].unique())
    known_variants_set = set(known_variants)
    assert returned_variants.issubset(known_variants_set), f"Unknown variants: {returned_variants - known_variants_set}"


def test_runtime_comparison_by_variant(dev_conn, generalization_variants):
    """Test that runtime comparison query returns expected columns."""
    df = get_runtime_comparison_by_variant(dev_conn)
    
    assert isinstance(df, pd.DataFrame)
    assert not df.empty
    expected_columns = {
        'variant', 'mean_t_runtime_ms', 'mean_g_runtime_ms', 
        'mean_runtime_diff_ms', 'ratio_of_mean_runtimes', 'tries'
    }
    assert expected_columns.issubset(set(df.columns))
    
    # Should only contain generalization variants (not ORIGINAL/INITIAL)
    returned_variants = set(df['variant'].unique())
    generalization_variants_set = set(generalization_variants)
    assert returned_variants.issubset(generalization_variants_set), f"Non-generalization variants found: {returned_variants - generalization_variants_set}"


@pytest.mark.parametrize("query_func", [
    get_teralizer_projects_with_runtime,
    get_mutation_results_by_project_variant,
    get_exclusions_all,
    get_runtime_comparison_by_variant
])
def test_query_functions_return_dataframes(dev_conn, query_func):
    """Test that all query functions return non-empty DataFrames."""
    df = query_func(dev_conn)
    assert isinstance(df, pd.DataFrame)
    assert not df.empty


# Tests can be run with: uv run --dev pytest