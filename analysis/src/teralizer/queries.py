"""
Common database queries for teralizer analysis.

This module provides named functions for database queries that are used across
multiple notebooks, eliminating duplication and making queries easier to maintain.
"""

import pandas as pd
from .config import db_config


# =============================================================================
# Connection helpers
# =============================================================================


def get_dev_connection():
    """Get connection to postgres_dev database (eqbench and commons-utils projects)."""
    return db_config.get_dev_engine()


def get_test_connection():
    """Get connection to postgres_test database (repo-reapers projects)."""
    return db_config.get_test_engine()


# =============================================================================
# Project and runtime queries
# =============================================================================


def get_teralizer_projects_with_runtime(conn=None):
    """Get projects that used test generalization with their total runtimes."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT id, project_name(id) AS project_name, runtime
    FROM project AS p
    JOIN v_projects_successes sp ON p.id = sp.project_id
    WHERE p.use_test_generalization = true
    """
    return pd.read_sql_query(query, conn)


def get_teralizer_runtime_by_stage(conn=None):
    """Get runtime breakdown by stage group from mv_teralizer_runtime_by_stage."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT *
    FROM mv_teralizer_runtime_by_stage
    ORDER BY project_id, variant_order, stage_group
    """
    return pd.read_sql_query(query, conn)


def get_evosuite_vs_teralizer_efficiency_data(conn=None):
    """Get EvoSuite vs Teralizer efficiency comparison data."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT
        ec.project_name,
        ec.teralizer_variant,
        ec.evosuite_runtime,
        ec.teralizer_runtime,
        ec.evosuite_runtime + ec.teralizer_runtime AS total_runtime,
        ec.evosuite_detected,
        ec.teralizer_detected
    FROM mv_efficiency_comparison_evosuite_vs_teralizer ec
    WHERE ec.teralizer_variant LIKE '%_TRIES'
    """
    return pd.read_sql_query(query, conn)


# =============================================================================
# Mutation analysis queries
# =============================================================================


def get_mutation_results_by_project_variant(conn=None):
    """Get mutation results aggregated by project and variant."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT mr.project_id, mr.project_name, mr.variant, sum(mr.total) AS total
    FROM mv_mutation_results_by_project_variant_mutator mr
    JOIN v_projects_successes ps ON ps.project_id = mr.project_id
    WHERE mr.variant IN ('ORIGINAL', 'INITIAL')
    GROUP BY mr.project_id, mr.project_name, mr.variant
    ORDER BY mr.project_id, variant_order(mr.variant)
    """
    return pd.read_sql_query(query, conn)


def get_mutation_results_by_project_variant_mutator(conn=None):
    """Get detailed mutation results by project, variant, and mutator."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT *
    FROM mv_mutation_results_by_project_variant_mutator mr
    JOIN v_projects_successes ps ON ps.project_id = mr.project_id
    ORDER BY mr.project_id, variant_order(mr.variant), mr.mutator
    """
    return pd.read_sql_query(query, conn)


def get_mutation_detection_comparison(conn=None):
    """Get mutation detection comparison data across variants."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT *
    FROM mv_mutation_detection_comparison
    ORDER BY project_id, variant_order, mutator
    """
    return pd.read_sql_query(query, conn)


# =============================================================================
# Exclusion analysis queries
# =============================================================================


def get_exclusions_all(conn=None):
    """Get all exclusion data from mv_exclusions_all."""
    if conn is None:
        conn = get_dev_connection()

    query = "SELECT * FROM mv_exclusions_all"
    return pd.read_sql_query(query, conn)


def get_exclusions_filtering_with_rejects(conn=None):
    """Get filtering exclusions where reject count > 0."""
    if conn is None:
        conn = get_dev_connection()

    query = "SELECT * FROM mv_exclusions_filtering WHERE reject > 0"
    return pd.read_sql_query(query, conn)


def get_exclusions_jpf_errors(conn=None):
    """Get JPF/SPF execution failures from mv_exclusions_jpf."""
    if conn is None:
        conn = get_dev_connection()

    query = "SELECT * FROM mv_exclusions_jpf"
    return pd.read_sql_query(query, conn)


def get_exclusions_test_failures(conn=None):
    """Get test execution failures from mv_exclusions_test_fails."""
    if conn is None:
        conn = get_dev_connection()

    query = "SELECT * FROM mv_exclusions_test_fails"
    return pd.read_sql_query(query, conn)


# =============================================================================
# Processing failure queries (extended dataset)
# =============================================================================


def get_project_failures_summary(conn=None):
    """Get processing failures summary from extended dataset."""
    if conn is None:
        conn = get_test_connection()

    query = """
    SELECT NULL as step, NULL as stage, 'Total projects' AS status, 
           (SELECT SUM(count) FROM v_project_failures_summary) + (SELECT COUNT(*) FROM v_projects_successes) AS count
    UNION ALL
    SELECT step, stage, stage, count FROM v_project_failures_summary
    UNION ALL
    SELECT NULL, NULL, 'Successfully processed', COUNT(*) FROM v_projects_successes
    """
    return pd.read_sql_query(query, conn)


def get_project_failures_detailed(conn=None):
    """Get detailed project failures with stage and info."""
    if conn is None:
        conn = get_test_connection()

    query = "SELECT stage, info FROM v_project_failures"
    return pd.read_sql_query(query, conn)


# =============================================================================
# Test runtime analysis queries
# =============================================================================


def get_test_runtimes_by_project_variant(conn=None):
    """Get test runtimes by project and variant."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT 
        t.project_id,
        project_name(t.project_id) AS project_name,
        te.variant,
        te.variant_order,
        te.runtime
    FROM test t
    JOIN mv_test_extension te ON t.id = te.test_id
    """
    return pd.read_sql_query(query, conn)


def get_generalization_runtimes_by_project_variant(conn=None):
    """Get generalization runtimes by project and variant."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT 
        g.project_id,
        project_name(g.project_id) AS project_name,
        ge.variant,
        ge.variant_order,
        ge.runtime
    FROM generalization g
    JOIN mv_generalization_extension ge ON g.id = ge.generalization_id
    """
    return pd.read_sql_query(query, conn)


def get_runtime_comparison_test_vs_generalization(conn=None):
    """Get test vs generalization runtime comparison data."""
    if conn is None:
        conn = get_dev_connection()

    query = "SELECT * FROM mv_runtime_comparison_test_vs_generalization"
    return pd.read_sql_query(query, conn)


def get_runtime_comparison_by_project_variant(conn=None):
    """Get test vs generalization runtime comparison aggregated by project and variant."""
    if conn is None:
        conn = get_dev_connection()

    query = """
    SELECT 
        rc.project_id,
        rc.project_name,
        rc.variant,
        avg(rc.t_runtime * 1000) AS mean_t_runtime_ms,
        avg(rc.g_runtime * 1000) AS mean_g_runtime_ms,
        avg(rc.runtime_diff * 1000) AS mean_runtime_diff_ms,
        avg(rc.g_runtime) / avg(rc.t_runtime) AS ratio_of_mean_runtimes,
        min(tries) AS tries,
        avg(rc.runtime_diff_per_try * 1000) AS mean_runtime_diff_per_try_ms
    FROM mv_runtime_comparison_test_vs_generalization rc
    GROUP BY rc.project_id, rc.project_name, rc.variant, rc.variant_order
    ORDER BY rc.project_id, rc.project_name, rc.variant_order
    """
    return pd.read_sql_query(query, conn)


def get_runtime_comparison_by_variant(conn=None):
    """Get test vs generalization runtime comparison aggregated by variant only."""
    if conn is None:
        conn = get_dev_connection()

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
