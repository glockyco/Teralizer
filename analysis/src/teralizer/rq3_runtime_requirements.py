"""RQ3: Runtime Requirements - Analysis functions for tool efficiency and performance.

This module provides functions to analyze Teralizer runtime requirements across different
pipeline stages, compare efficiency with EvoSuite using Pareto fronts, and analyze
EvoSuite runtime breakdown by phase and search budget.
"""

import pandas as pd
import re
from typing import List, Optional, cast


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================


def get_teralizer_runtime_by_stage(conn) -> pd.DataFrame:
    """Get Teralizer runtime breakdown by pipeline stage and variant.

    Args:
        conn: Database connection

    Returns:
        DataFrame with runtime statistics per stage group, project, and variant
    """
    query = """
    SELECT *
    FROM mv_teralizer_runtime_by_stage
    ORDER BY project_id, variant_order, stage_group
    """
    return pd.read_sql_query(query, conn)


def get_evosuite_vs_teralizer_efficiency(
    conn, variants: Optional[List[str]] = None
) -> pd.DataFrame:
    """Get EvoSuite vs Teralizer efficiency comparison data.

    Args:
        conn: Database connection
        variants: List of Teralizer variants to include (default: TRIES variants)

    Returns:
        DataFrame with efficiency comparison statistics
    """
    if variants is None:
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
        WHERE ec.teralizer_variant LIKE '%%_TRIES'
        """
    else:
        variant_list = "', '".join(variants)
        query = (
            """
        SELECT
            ec.project_name,
            ec.teralizer_variant,
            ec.evosuite_runtime,
            ec.teralizer_runtime,
            ec.evosuite_runtime + ec.teralizer_runtime AS total_runtime,
            ec.evosuite_detected,
            ec.teralizer_detected
        FROM mv_efficiency_comparison_evosuite_vs_teralizer ec
        WHERE ec.teralizer_variant IN ('"""
            + variant_list
            + """')
        """
        )
    return pd.read_sql_query(query, conn)


def get_evosuite_runtime_analysis(conn) -> pd.DataFrame:
    """Get EvoSuite runtime breakdown by phase and search budget.

    Args:
        conn: Database connection

    Returns:
        DataFrame with EvoSuite phase runtime statistics per project and budget
    """
    query = """
    SELECT 
        mv.*,
        project_name(mv.project_id) AS project_name,
        p.configuration::json->'evosuite'->>'search-budget' AS search_budget
    FROM 
        mv_evosuite_runtime_pivoted mv
    JOIN 
        project p ON mv.project_id = p.id
    """
    return pd.read_sql_query(query, conn)


# =============================================================================
# Computation Functions (compute_*)
# =============================================================================


def compute_stage_runtime_breakdown(df: pd.DataFrame) -> pd.DataFrame:
    """Process pipeline stage runtime data for visualization.

    Args:
        df: DataFrame from get_teralizer_runtime_by_stage

    Returns:
        DataFrame with processed stage breakdown data
    """

    # Extract base project names using regex
    def get_base_project_name(project_name):
        match = re.match(r"^(.*?)(?:-es-)", project_name)
        if match:
            return match.group(1)
        else:
            return project_name

    df_processed = df.copy()
    df_processed.loc[:, "base_project_name"] = df_processed["project_name"].apply(
        get_base_project_name
    )

    # Use categorical data types for better performance
    ordered_groups = [
        "Stage 1 + 2",
        "Stage 3",
        "Stage 4",
        "Stage 5",
    ]

    df_processed.isetitem(
        df_processed.columns.get_loc("stage_group"),
        pd.Categorical(
            df_processed["stage_group"], categories=ordered_groups, ordered=True
        ),
    )
    df_processed.isetitem(
        df_processed.columns.get_loc("variant"),
        pd.Categorical(df_processed["variant"]),
    )

    # Convert runtime to numeric
    df_processed.isetitem(
        df_processed.columns.get_loc("total_runtime"),
        cast(
            pd.Series,
            pd.to_numeric(df_processed["total_runtime"], errors="coerce"),
        ).fillna(0.0),
    )

    return df_processed


def compute_pareto_efficiency_analysis(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate Pareto fronts and efficiency metrics.

    Args:
        df: DataFrame from get_evosuite_vs_teralizer_efficiency

    Returns:
        DataFrame with Pareto efficiency analysis including optimal points
    """

    def extract_prefix_and_budget(name):
        """Extract project prefix and search budget from project name."""
        match = re.match(r"(.+)-es-default-(\d+s)", name)
        if match:
            return match.group(1), match.group(2)
        return name, None

    def pareto_front(df_points: pd.DataFrame, x_col: str, y_col: str) -> pd.DataFrame:
        """Calculate Pareto front for given x and y columns."""
        sorted_df = df_points.sort_values(x_col)
        pareto = []
        max_y = -float("inf")
        for _, row in sorted_df.iterrows():
            if row[y_col] > max_y:
                pareto.append(row)
                max_y = row[y_col]
        return pd.DataFrame(pareto)

    # Extract project prefix and budget information
    prefix_budget = df["project_name"].apply(
        lambda x: pd.Series(extract_prefix_and_budget(x))
    )
    prefix_budget.columns = ["project_prefix", "search_budget"]
    df_temp = pd.concat([df.reset_index(drop=True), prefix_budget], axis=1)

    # Process each project separately for Pareto analysis
    all_efficiency_data = []
    project_prefixes = df_temp["project_prefix"].unique()

    for project_prefix in project_prefixes:
        proj_df = df_temp[df_temp["project_prefix"] == project_prefix].copy()
        evosuite_points = (
            proj_df.groupby(["project_prefix", "search_budget"]).first().reset_index()
        )

        # Prepare all points for this project (EvoSuite + Teralizer variants)
        # EvoSuite-only points
        es_points = cast(
            pd.DataFrame,
            evosuite_points[
                [
                    "project_prefix",
                    "search_budget",
                    "evosuite_runtime",
                    "evosuite_detected",
                ]
            ],
        ).copy()
        es_points.loc[:, "type"] = "ES_ONLY"
        es_points.loc[:, "teralizer_variant"] = "ES_ONLY"
        es_points.rename(
            columns={
                "evosuite_runtime": "total_runtime",
                "evosuite_detected": "detection_rate",
            },
            inplace=True,
        )

        # Teralizer variants (EvoSuite + Teralizer)
        naive_points = proj_df[
            proj_df["teralizer_variant"].str.startswith("NAIVE", na=False)
        ][
            [
                "project_prefix",
                "search_budget",
                "total_runtime",
                "teralizer_detected",
                "teralizer_variant",
            ]
        ].copy()
        naive_points.loc[:, "type"] = "NAIVE"
        naive_points.rename(
            columns={"teralizer_detected": "detection_rate"}, inplace=True
        )

        improved_points = proj_df[
            proj_df["teralizer_variant"].str.startswith("IMPROVED", na=False)
        ][
            [
                "project_prefix",
                "search_budget",
                "total_runtime",
                "teralizer_detected",
                "teralizer_variant",
            ]
        ].copy()
        improved_points.loc[:, "type"] = "IMPROVED"
        improved_points.rename(
            columns={"teralizer_detected": "detection_rate"}, inplace=True
        )

        # Combine all points for this project
        all_points = pd.concat(
            [es_points, naive_points, improved_points], ignore_index=True, sort=False
        )

        # Calculate Pareto front for this project
        pf = pareto_front(all_points, "total_runtime", "detection_rate")

        # Mark which points are Pareto optimal
        pareto_optimal_indices = set()
        for _, pf_row in pf.iterrows():
            matches = all_points[
                (abs(all_points["total_runtime"] - pf_row["total_runtime"]) < 0.1)
                & (abs(all_points["detection_rate"] - pf_row["detection_rate"]) < 0.001)
            ]
            pareto_optimal_indices.update(matches.index)

        # Add all points to the efficiency data
        for idx, row in all_points.iterrows():
            detection_decimal = (
                row["detection_rate"] / 100.0
                if row["detection_rate"] > 1
                else row["detection_rate"]
            )
            runtime_secs = row["total_runtime"]
            detection_per_second = (
                detection_decimal / runtime_secs if runtime_secs > 0 else 0.0
            )

            all_efficiency_data.append(
                {
                    "project_name": row["project_prefix"],
                    "evosuite_budget": str(row["search_budget"])
                    if pd.notna(row["search_budget"])
                    else "unknown",
                    "teralizer_variant": row["teralizer_variant"],
                    "detection_rate": row["detection_rate"],
                    "runtime_seconds": runtime_secs,
                    "detection_per_second": detection_per_second,
                    "is_pareto_optimal": idx in pareto_optimal_indices,
                    "type": row.get("type", "UNKNOWN"),
                }
            )

    return pd.DataFrame(all_efficiency_data)


def compute_evosuite_phase_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Process EvoSuite phase breakdown data.

    Args:
        df: DataFrame from get_evosuite_runtime_analysis

    Returns:
        DataFrame with computed EvoSuite phase statistics
    """
    # Convert search_budget to numeric
    df_processed = df.copy()
    df_processed.isetitem(
        df_processed.columns.get_loc("search_budget"),
        pd.to_numeric(df_processed["search_budget"], errors="coerce"),
    )

    # Define the runtime columns to analyze
    runtime_columns = [
        "total",
        "search",
        "inlining",
        "minimization",
        "coverage_analysis",
        "assertion_generation",
        "junit_check",
        "writing_tests",
        "writing_statistics",
        "done",
        "finished",
    ]

    # Convert runtime columns to numeric
    for col in runtime_columns:
        if col in df_processed.columns:
            df_processed.isetitem(
                df_processed.columns.get_loc(col),
                cast(
                    pd.Series,
                    pd.to_numeric(df_processed[col], errors="coerce"),
                ).fillna(0.0),
            )

    # Calculate percentage of time spent in each phase
    percentage_columns = [col for col in runtime_columns if col != "total"]

    for col in percentage_columns:
        if col in df_processed.columns:
            df_processed.loc[:, f"{col}_pct"] = (
                df_processed[col] / df_processed["total"]
            ) * 100

    return df_processed
