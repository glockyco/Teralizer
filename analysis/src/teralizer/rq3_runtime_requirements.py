"""RQ3: Runtime Requirements - Analysis functions for tool efficiency and performance.

This module provides functions to analyze Teralizer runtime requirements across different
pipeline stages, compare efficiency with EvoSuite using Pareto fronts, and analyze
EvoSuite runtime breakdown by phase and search budget.
"""

import pandas as pd
import matplotlib.pyplot as plt
import re
from typing import List, Dict, Tuple

from .formatting import (
    sort_dataframe_by_project, replace_project_names_with_macros, build_latex_table_content
)
from .exports import (
    get_variant_macro, standardize_project_name, format_runtime_seconds,
    format_detection_rate_decimal
)


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================

def get_teralizer_total_runtimes(conn) -> pd.DataFrame:
    """Get total Teralizer runtime per project.
    
    Args:
        conn: Database connection
        
    Returns:
        DataFrame with project_id, project_name, and total runtime
    """
    query = """
    SELECT id AS project_id, project_name(id) AS project_name, runtime
    FROM project AS p
    JOIN v_projects_successes sp ON p.id = sp.project_id
    WHERE p.use_test_generalization = true
    """
    return pd.read_sql_query(query, conn)


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


def get_evosuite_vs_teralizer_efficiency(conn, variants: List[str] = None) -> pd.DataFrame:
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
        WHERE ec.teralizer_variant IN ('""" + variant_list + """')
        """
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


def get_detailed_efficiency_comparison(conn, variant: str = 'IMPROVED_200_TRIES') -> pd.DataFrame:
    """Get detailed efficiency comparison including validation runtimes.
    
    Args:
        conn: Database connection
        variant: Specific Teralizer variant to analyze
        
    Returns:
        DataFrame with detailed runtime breakdown for efficiency analysis
    """
    query = """
    SELECT
        ec.project_name,
        ec.teralizer_variant,
        ec.e_no_validation,
        ec.e_validation,
        ec.t_no_validation,
        ec.t_validation,
        ec.evosuite_detected,
        ec.teralizer_detected
    FROM mv_efficiency_comparison_evosuite_vs_teralizer ec
    WHERE ec.teralizer_variant = '""" + variant + """'
    """
    return pd.read_sql_query(query, conn)


# =============================================================================
# Computation Functions (compute_*)
# =============================================================================

def compute_teralizer_runtime_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """Process and sort total Teralizer runtime data.
    
    Args:
        df: DataFrame from get_teralizer_total_runtimes
        
    Returns:
        DataFrame sorted by table group order with formatted runtimes
    """
    # Sort by project order using established sorting function
    df_sorted = sort_dataframe_by_project(df, 'project_name')
    
    # Convert runtime to numeric for calculations
    df_sorted['runtime'] = pd.to_numeric(df_sorted['runtime'], errors='coerce').fillna(0.0)
    
    return df_sorted


def compute_stage_runtime_breakdown(df: pd.DataFrame) -> pd.DataFrame:
    """Process pipeline stage runtime data for visualization.
    
    Args:
        df: DataFrame from get_teralizer_runtime_by_stage
        
    Returns:
        DataFrame with processed stage breakdown data
    """
    # Extract base project names using regex
    def get_base_project_name(project_name):
        match = re.match(r'^(.*?)(?:-es-)', project_name)
        if match:
            return match.group(1)
        else:
            return project_name
    
    df_processed = df.copy()
    df_processed['base_project_name'] = df_processed['project_name'].apply(get_base_project_name)
    
    # Use categorical data types for better performance
    ordered_groups = ['Original Validation', 'Specification Extraction', 'Initial Validation', 
                     'Test Transformation', 'Generalization Validation']
    
    df_processed['stage_group'] = pd.Categorical(
        df_processed['stage_group'], 
        categories=ordered_groups, 
        ordered=True
    )
    df_processed['variant'] = pd.Categorical(df_processed['variant'])
    
    # Convert runtime to numeric
    df_processed['total_runtime'] = pd.to_numeric(df_processed['total_runtime'], errors='coerce').fillna(0.0)
    
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
        match = re.match(r'(.+)-es-default-(\d+s)', name)
        if match:
            return match.group(1), match.group(2)
        return name, None
    
    def pareto_front(df_points: pd.DataFrame, x_col: str, y_col: str) -> pd.DataFrame:
        """Calculate Pareto front for given x and y columns."""
        sorted_df = df_points.sort_values(x_col)
        pareto = []
        max_y = -float('inf')
        for _, row in sorted_df.iterrows():
            if row[y_col] > max_y:
                pareto.append(row)
                max_y = row[y_col]
        return pd.DataFrame(pareto)
    
    # Extract project prefix and budget information
    prefix_budget = df['project_name'].apply(lambda x: pd.Series(extract_prefix_and_budget(x)))
    prefix_budget.columns = ['project_prefix', 'search_budget']
    df_temp = pd.concat([df.reset_index(drop=True), prefix_budget], axis=1)
    
    # Process each project separately for Pareto analysis
    all_efficiency_data = []
    project_prefixes = df_temp['project_prefix'].unique()
    
    for project_prefix in project_prefixes:
        proj_df = df_temp[df_temp['project_prefix'] == project_prefix].copy()
        evosuite_points = (
            proj_df.groupby(['project_prefix', 'search_budget'])
            .first()
            .reset_index()
        )
        
        # Prepare all points for this project (EvoSuite + Teralizer variants)
        # EvoSuite-only points
        es_points = evosuite_points[['project_prefix', 'search_budget', 'evosuite_runtime', 'evosuite_detected']].copy()
        es_points['type'] = 'ES_ONLY'
        es_points['teralizer_variant'] = 'ES_ONLY'
        es_points.rename(columns={'evosuite_runtime': 'total_runtime', 'evosuite_detected': 'detection_rate'}, inplace=True)
        
        # Teralizer variants (EvoSuite + Teralizer)
        naive_points = proj_df[proj_df['teralizer_variant'].str.startswith('NAIVE', na=False)][
            ['project_prefix', 'search_budget', 'total_runtime', 'teralizer_detected', 'teralizer_variant']
        ].copy()
        naive_points['type'] = 'NAIVE'
        naive_points.rename(columns={'teralizer_detected': 'detection_rate'}, inplace=True)
        
        improved_points = proj_df[proj_df['teralizer_variant'].str.startswith('IMPROVED', na=False)][
            ['project_prefix', 'search_budget', 'total_runtime', 'teralizer_detected', 'teralizer_variant']
        ].copy()
        improved_points['type'] = 'IMPROVED'
        improved_points.rename(columns={'teralizer_detected': 'detection_rate'}, inplace=True)
        
        # Combine all points for this project
        all_points = pd.concat([es_points, naive_points, improved_points], ignore_index=True, sort=False)
        
        # Calculate Pareto front for this project
        pf = pareto_front(all_points, 'total_runtime', 'detection_rate')
        
        # Mark which points are Pareto optimal
        pareto_optimal_indices = set()
        for _, pf_row in pf.iterrows():
            matches = all_points[
                (abs(all_points['total_runtime'] - pf_row['total_runtime']) < 0.1) &
                (abs(all_points['detection_rate'] - pf_row['detection_rate']) < 0.001)
            ]
            pareto_optimal_indices.update(matches.index)
        
        # Add all points to the efficiency data
        for idx, row in all_points.iterrows():
            detection_decimal = row['detection_rate'] / 100.0 if row['detection_rate'] > 1 else row['detection_rate']
            runtime_secs = row['total_runtime']
            detection_per_second = detection_decimal / runtime_secs if runtime_secs > 0 else 0.0
            
            all_efficiency_data.append({
                'project_name': row['project_prefix'],
                'evosuite_budget': str(row['search_budget']) if pd.notna(row['search_budget']) else 'unknown',
                'teralizer_variant': row['teralizer_variant'],
                'detection_rate': row['detection_rate'],
                'runtime_seconds': runtime_secs,
                'detection_per_second': detection_per_second,
                'is_pareto_optimal': idx in pareto_optimal_indices,
                'type': row.get('type', 'UNKNOWN')
            })
    
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
    df_processed['search_budget'] = pd.to_numeric(df_processed['search_budget'], errors='coerce')
    
    # Define the runtime columns to analyze
    runtime_columns = [
        'total', 'search', 'inlining', 'minimization', 'coverage_analysis', 
        'assertion_generation', 'junit_check', 'writing_tests', 
        'writing_statistics', 'done', 'finished'
    ]
    
    # Convert runtime columns to numeric
    for col in runtime_columns:
        if col in df_processed.columns:
            df_processed[col] = pd.to_numeric(df_processed[col], errors='coerce').fillna(0.0)
    
    # Calculate percentage of time spent in each phase
    percentage_columns = [col for col in runtime_columns if col != 'total']
    
    for col in percentage_columns:
        if col in df_processed.columns:
            df_processed[f'{col}_pct'] = (df_processed[col] / df_processed['total']) * 100
    
    return df_processed


# =============================================================================
# Generation Functions (generate_*)
# =============================================================================

def generate_teralizer_runtimes_table(df: pd.DataFrame) -> str:
    """Generate LaTeX table for total Teralizer runtimes.
    
    Args:
        df: DataFrame from compute_teralizer_runtime_statistics
        
    Returns:
        Complete LaTeX table string
    """
    def seconds_to_hours_minutes_seconds(seconds):
        """Convert seconds to hours:minutes:seconds format."""
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = int(seconds % 60)
        return f"{hours}h {minutes:02d}min {secs:02d}s"
    
    # Prepare data for LaTeX
    df_table = df.copy()
    
    # Replace project names with macros
    df_table = replace_project_names_with_macros(df_table, 'project_name')
    
    # Build table rows manually
    table_rows = []
    for _, row in df_table.iterrows():
        runtime_formatted = seconds_to_hours_minutes_seconds(row['runtime'])
        row_str = f"{row['project_name']} & {runtime_formatted}"
        table_rows.append(row_str)
    
    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(' & ') for row in table_rows])
    rows_df.columns = ['Project', 'Runtime']
    
    table_content = build_latex_table_content(
        rows_df,
        caption="Total runtimes of Teralizer for all evaluated projects.",
        label="tab:teralizer-runtimes",
        column_spec="lr",
        header_rows=["Project & Runtime \\\\"],
        add_midrules=True,
        project_column='Project'
    )
    
    return table_content


def generate_pareto_figure(df_pareto: pd.DataFrame, project_name: str) -> plt.Figure:
    """Generate Pareto efficiency figure for a specific project.
    
    Args:
        df_pareto: DataFrame from compute_pareto_efficiency_analysis
        project_name: Name of project to generate figure for (e.g., 'eqbench', 'commons-utils')
        
    Returns:
        Matplotlib figure object
    """
    import matplotlib.pyplot as plt
    import numpy as np
    
    # Filter data for specific project
    project_data = df_pareto[df_pareto['project_name'] == project_name].copy()
    
    if project_data.empty:
        raise ValueError(f"No data found for project: {project_name}")
    
    # Create figure
    fig, ax = plt.subplots(figsize=(5, 4))
    
    # Plot all points (faded)
    es_points = project_data[project_data['type'] == 'ES_ONLY']
    naive_points = project_data[project_data['type'] == 'NAIVE']
    improved_points = project_data[project_data['type'] == 'IMPROVED']
    
    ax.scatter(
        es_points['runtime_seconds'], es_points['detection_rate'],
        marker='o', color='blue', alpha=0.3, s=40, label='EvoSuite Only'
    )
    ax.scatter(
        naive_points['runtime_seconds'], naive_points['detection_rate'],
        marker='x', color='red', alpha=0.3, s=40, label='EvoSuite + NAIVE'
    )
    ax.scatter(
        improved_points['runtime_seconds'], improved_points['detection_rate'],
        marker='^', color='green', alpha=0.3, s=40, label='EvoSuite + IMPROVED'
    )
    
    # Get Pareto optimal points
    pareto_points = project_data[project_data['is_pareto_optimal']].copy()
    pareto_points = pareto_points.sort_values('runtime_seconds')
    
    # Draw Pareto front line
    ax.plot(
        pareto_points['runtime_seconds'], pareto_points['detection_rate'],
        linestyle='--', color='black', linewidth=1.2, zorder=2, label='Pareto front'
    )
    
    # Plot and label Pareto front points
    y_min = project_data['detection_rate'].min()
    y_max = project_data['detection_rate'].max()
    y_range = y_max - y_min
    margin = 0.2 * y_range if y_range > 0 else 0.5
    ax.set_ylim(y_min - margin/2, y_max + margin)
    
    offset = 0.025 * (y_max + margin - (y_min - margin/2))
    
    for i, (_, row) in enumerate(pareto_points.iterrows(), start=1):
        # Determine color and marker based on approach type
        if row['type'] == 'ES_ONLY':
            color = 'blue'
            marker = 'o'
        elif row['type'] == 'NAIVE':
            color = 'red'
            marker = 'x'
        elif row['type'] == 'IMPROVED':
            color = 'green'
            marker = '^'
        else:
            color = 'black'
            marker = 'o'
        
        # Plot point with emphasis
        if marker in ['o', '^']:
            ax.scatter(row['runtime_seconds'], row['detection_rate'], 
                      marker=marker, color=color, s=90, edgecolor='black', zorder=3)
        else:
            ax.scatter(row['runtime_seconds'], row['detection_rate'], 
                      marker=marker, color=color, s=90, zorder=3)
        
        # Add point number label
        ax.text(
            row['runtime_seconds'], row['detection_rate'] + offset, str(i),
            fontsize=16, fontweight='bold', color=color, ha='center', va='bottom'
        )
    
    # Set labels and formatting
    ax.set_title(f"Project: {project_name}")
    ax.set_xlabel("Runtime (s)")
    ax.set_ylabel("Detected (%)")
    ax.ticklabel_format(style='plain', axis='x')
    ax.legend(loc='upper center', ncol=4, frameon=False)
    
    plt.tight_layout()
    return fig


def generate_pareto_points_table(df_pareto: pd.DataFrame, project_name: str) -> str:
    """Generate LaTeX table for Pareto optimal points for a specific project.
    
    Args:
        df_pareto: DataFrame from compute_pareto_efficiency_analysis
        project_name: Name of project to generate table for
        
    Returns:
        Complete LaTeX table string
    """
    # Filter data for specific project and get Pareto optimal points
    project_data = df_pareto[
        (df_pareto['project_name'] == project_name) & 
        (df_pareto['is_pareto_optimal'])
    ].copy()
    
    if project_data.empty:
        raise ValueError(f"No Pareto optimal points found for project: {project_name}")
    
    # Sort by runtime to match paper ordering
    project_data = project_data.sort_values('runtime_seconds')
    
    def format_variant_label(variant: str, approach_type: str) -> str:
        """Format variant label for LaTeX output."""
        if approach_type == 'ES_ONLY':
            return '-'
        
        # Extract tries number from variant name
        match = re.match(r'(NAIVE|IMPROVED)_(\d+)_TRIES', variant)
        if match:
            variant_type, tries = match.groups()
            return f"{variant_type}$_{{{tries}}}$"
        return str(variant)
    
    # Build table rows
    table_rows = []
    for i, (_, row) in enumerate(project_data.iterrows(), start=1):
        variant_label = format_variant_label(row['teralizer_variant'], row['type'])
        detection_pct = f"{row['detection_rate']:.1f}"
        runtime_s = f"{row['runtime_seconds']:.1f}"
        
        row_str = f"{i} & {row['evosuite_budget']} & {variant_label} & {detection_pct} & {runtime_s}"
        table_rows.append(row_str)
    
    # Create DataFrame for build_latex_table_content
    rows_df = pd.DataFrame([row.split(' & ') for row in table_rows])
    rows_df.columns = ['Pt', 'EvoSuite', 'Teralizer', 'DetPct', 'Runtime']
    
    # Determine label based on project name
    if 'eqbench' in project_name.lower():
        label = "tab:pareto-eqbench"
        caption = f"Pareto points for project: {project_name}."
    elif 'commons' in project_name.lower():
        label = "tab:pareto-commons"
        caption = f"Pareto points for project: {project_name}."
    else:
        label = f"tab:pareto-{project_name.lower().replace('-', '')}"
        caption = f"Pareto points for project: {project_name}."
    
    table_content = build_latex_table_content(
        rows_df,
        caption=caption,
        label=label,
        column_spec="rrlrr",
        header_rows=["Pt. & EvoSuite & Teralizer & Det. \\% & Runtime (s) \\\\"],
        add_midrules=False
    )
    
    return table_content


# =============================================================================
# CSV Export Functions
# =============================================================================

def generate_runtime_breakdown_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for Teralizer runtime breakdown by stage.
    
    Args:
        df: DataFrame from compute_stage_runtime_breakdown
        
    Returns:
        DataFrame formatted for CSV export
    """
    # Define step order mapping for pipeline execution sequence
    step_order_map = {
        'Original Validation': 1,
        'Specification Extraction': 2, 
        'Initial Validation': 3,
        'Test Transformation': 4,
        'Generalization Validation': 5
    }
    
    csv_data = []
    
    for _, row in df.iterrows():
        # Determine if stage is shared (runs once per project) or variant-specific
        is_shared_stage = row['stage_group'] in ['Original Validation', 'Specification Extraction', 'Initial Validation']
        
        csv_data.append({
            'project_name': standardize_project_name(row['project_name']),
            'base_project_name': standardize_project_name(row['base_project_name']),
            'stage_group': row['stage_group'],
            'variant': get_variant_macro(row['variant']),
            'total_runtime_seconds': format_runtime_seconds(row['total_runtime']),
            'is_shared_stage': is_shared_stage,
            'step_order': step_order_map[row['stage_group']]
        })
    
    return pd.DataFrame(csv_data)


def generate_pareto_efficiency_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for Pareto efficiency analysis.
    
    Args:
        df: DataFrame from compute_pareto_efficiency_analysis
        
    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    
    for _, row in df.iterrows():
        csv_data.append({
            'project_name': standardize_project_name(row['project_name']),
            'evosuite_budget': str(row['evosuite_budget']),
            'teralizer_variant': get_variant_macro(row['teralizer_variant']) if row['teralizer_variant'] != 'ES_ONLY' else 'ES_ONLY',
            'detection_rate_decimal': format_detection_rate_decimal(row['detection_rate']),
            'runtime_seconds': format_runtime_seconds(row['runtime_seconds']),
            'detection_per_second': float(row['detection_per_second']),
            'is_pareto_optimal': bool(row['is_pareto_optimal']),
            'type': row['type']
        })
    
    return pd.DataFrame(csv_data)


def generate_evosuite_runtime_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for EvoSuite runtime phase analysis.
    
    Args:
        df: DataFrame from compute_evosuite_phase_statistics
        
    Returns:
        DataFrame formatted for CSV export
    """
    # Define the runtime columns to export
    runtime_columns = [
        'total', 'search', 'inlining', 'minimization', 'coverage_analysis', 
        'assertion_generation', 'junit_check', 'writing_tests', 
        'writing_statistics', 'done', 'finished'
    ]
    
    csv_data = []
    
    for _, row in df.iterrows():
        data_row = {
            'project_name': standardize_project_name(row['project_name']),
            'search_budget_seconds': int(row['search_budget']) if pd.notna(row['search_budget']) else 0
        }
        
        # Add runtime columns
        for col in runtime_columns:
            if col in row:
                data_row[f'{col}_runtime_seconds'] = format_runtime_seconds(row[col]) if pd.notna(row[col]) else 0.0
        
        # Add percentage columns
        percentage_columns = [col for col in runtime_columns if col != 'total']
        for col in percentage_columns:
            pct_col = f'{col}_pct'
            if pct_col in row:
                data_row[f'{col}_percentage'] = float(row[pct_col]) if pd.notna(row[pct_col]) else 0.0
        
        csv_data.append(data_row)
    
    return pd.DataFrame(csv_data)


def generate_teralizer_runtimes_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for total Teralizer runtimes.
    
    Args:
        df: DataFrame from compute_teralizer_runtime_statistics
        
    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    
    for _, row in df.iterrows():
        csv_data.append({
            'project_name': standardize_project_name(row['project_name']),
            'total_runtime_seconds': format_runtime_seconds(row['runtime']),
            'runtime_hours': float(row['runtime'] / 3600.0),
            'runtime_formatted': f"{int(row['runtime'] // 3600)}h {int((row['runtime'] % 3600) // 60):02d}min {int(row['runtime'] % 60):02d}s"
        })
    
    return pd.DataFrame(csv_data)