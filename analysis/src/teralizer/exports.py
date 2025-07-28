"""Data export utilities for generating both LaTeX tables and CSV datasets.

This module provides functions to export analysis results in multiple formats:
- LaTeX tables for paper inclusion with consistent macro mappings
- CSV files for LLM processing and analysis
- Project and variant naming standardization
- Table ordering for paper consistency
"""

import pandas as pd
from pathlib import Path
from typing import Optional, Any, Union, List
import matplotlib.pyplot as plt
import matplotlib.figure


def _find_project_root() -> Path:
    """Find project root by looking for .env file.
    
    Returns:
        Path: Absolute path to project root directory
        
    Raises:
        RuntimeError: If project root cannot be found
    """
    current = Path(__file__).parent
    for _ in range(10):  # Prevent infinite loop
        env_file = current / '.env'
        if env_file.exists():
            return current
        if current == current.parent:
            # Fallback: assume we're in analysis/src/teralizer/
            return Path(__file__).parent.parent.parent.parent
        current = current.parent
    
    # Last fallback
    return Path(__file__).parent.parent.parent.parent


def get_data_output_dir() -> Path:
    """Get the data output directory, creating it if it doesn't exist.
    
    Returns:
        Path: Absolute path to analysis/output/data/ directory
    """
    project_root = _find_project_root()
    data_dir = project_root / 'analysis' / 'output' / 'data'
    data_dir.mkdir(parents=True, exist_ok=True)
    return data_dir


def get_tables_output_dir() -> Path:
    """Get the tables output directory, creating it if it doesn't exist.
    
    Returns:
        Path: Absolute path to analysis/output/tables/ directory
    """
    project_root = _find_project_root()
    tables_dir = project_root / 'analysis' / 'output' / 'tables'
    tables_dir.mkdir(parents=True, exist_ok=True)
    return tables_dir


def get_figures_output_dir() -> Path:
    """Get the figures output directory, creating it if it doesn't exist.
    
    Returns:
        Path: Absolute path to analysis/output/figures/ directory
    """
    project_root = _find_project_root()
    figures_dir = project_root / 'analysis' / 'output' / 'figures'
    figures_dir.mkdir(parents=True, exist_ok=True)
    return figures_dir


def save_csv_data(
    dataframe: pd.DataFrame, 
    filename: str, 
    description: Optional[str] = None
) -> Path:
    """Save a pandas DataFrame as a CSV file.
    
    Args:
        dataframe: The data to export
        filename: Base filename (without .csv extension)
        description: Human-readable description (used in print output)
        
    Returns:
        Path: Full path to the saved CSV file
        
    Example:
        >>> df = pd.DataFrame({'project': ['A', 'B'], 'runtime': [100, 200]})
        >>> save_csv_data(df, 'runtime-data', 'Project runtime analysis')
    """
    data_dir = get_data_output_dir()
    
    # Ensure filename has .csv extension
    if not filename.endswith('.csv'):
        filename += '.csv'
    
    csv_path = data_dir / filename
    
    # Save the CSV file
    dataframe.to_csv(csv_path, index=False)
    
    return csv_path


def standardize_project_name(project_name: str) -> str:
    """Standardize project name for cross-dataset consistency.
    
    Args:
        project_name: Raw project name from database
        
    Returns:
        str: Standardized project name
    """
    # Remove common path prefixes and suffixes
    name = project_name.strip()
    
    # Handle common patterns
    if name.startswith('/'):
        name = name.split('/')[-1]  # Take last path component
    
    return name



def format_runtime_seconds(runtime_value: Any) -> float:
    """Convert runtime value to standardized seconds format.
    
    Args:
        runtime_value: Runtime value (may be in different units)
        
    Returns:
        float: Runtime in seconds
    """
    if pd.isna(runtime_value):
        return 0.0
    
    # Assume input is already in seconds for now
    # Future: add conversion logic for other units if needed
    return float(runtime_value)


def format_detection_rate_decimal(detection_rate: Any) -> float:
    """Convert detection rate to standardized decimal format (0.0-1.0).
    
    Args:
        detection_rate: Detection rate (may be percentage or decimal)
        
    Returns:
        float: Detection rate as decimal between 0.0 and 1.0
    """
    if pd.isna(detection_rate):
        return 0.0
    
    rate = float(detection_rate)
    
    # If rate > 1, assume it's a percentage and convert to decimal
    if rate > 1.0:
        rate = rate / 100.0
    
    # Clamp to valid range
    return max(0.0, min(1.0, rate))


# Dataset name mappings to LaTeX macros
DATASETS = {
    'eqbench': r'\DatasetEqBench{}',
    'eqbench-es-default-1s': r'\DatasetEqBenchA{}',
    'eqbench-es-default-10s': r'\DatasetEqBenchB{}',
    'eqbench-es-default-60s': r'\DatasetEqBenchC{}',
    'commons-utils': r'\DatasetCommonsDev{}',
    'commons-utils-es-default-1s': r'\DatasetCommonsA{}',
    'commons-utils-es-default-10s': r'\DatasetCommonsB{}',
    'commons-utils-es-default-60s': r'\DatasetCommonsC{}',
    'repo-reapers': r'\DatasetRepoReapers{}',
}

# Algorithm variant mappings to LaTeX macros
VARIANTS = {
    'ORIGINAL': r'\VariantOriginal{}',
    'INITIAL': r'\VariantInitial{}',
    'BASELINE': r'\VariantBaseline{}',
    'NAIVE_10_TRIES': r'\VariantNaiveA{}',
    'NAIVE_50_TRIES': r'\VariantNaiveB{}',
    'NAIVE_200_TRIES': r'\VariantNaiveC{}',
    'IMPROVED_10_TRIES': r'\VariantImprovedA{}',
    'IMPROVED_50_TRIES': r'\VariantImprovedB{}',
    'IMPROVED_200_TRIES': r'\VariantImprovedC{}',
    'SHARED': r'\VariantShared{}',
}

# Tool name mappings to LaTeX macros
TOOLS = {
    'evosuite': r'\ToolEvoSuite{}',
    'teralizer': r'\ToolTeralizer{}',
    'spf': r'\ToolSPFLong{}',
}


def get_dataset_macro(dataset_name: str) -> str:
    """Get LaTeX macro for dataset name.
    
    Args:
        dataset_name: Dataset identifier
        
    Returns:
        LaTeX macro string
        
    Raises:
        KeyError: If dataset name not found in mappings
    """
    return DATASETS[dataset_name]


def get_variant_macro(variant_name: str) -> str:
    """Get LaTeX macro for algorithm variant.
    
    Args:
        variant_name: Variant identifier
        
    Returns:
        LaTeX macro string
        
    Raises:
        KeyError: If variant name not found in mappings
    """
    return VARIANTS[variant_name]


def get_tool_macro(tool_name: str) -> str:
    """Get LaTeX macro for tool name.
    
    Args:
        tool_name: Tool identifier
        
    Returns:
        LaTeX macro string
        
    Raises:
        KeyError: If tool name not found in mappings
    """
    return TOOLS[tool_name]


def format_table_row(*values) -> str:
    """Format values as LaTeX table row.
    
    Args:
        *values: Cell values to format
        
    Returns:
        LaTeX table row string ending with \\\\
    """
    return ' & '.join(str(v) for v in values) + r' \\'


def get_project_type(project_name: str) -> str:
    """Classify project into type groups for proper ordering.
    
    Args:
        project_name: Project name from database
        
    Returns:
        Project type classification
    """
    if project_name.startswith('eqbench'):
        return 'eqbench'
    elif project_name.startswith('commons-utils-es'):
        return 'commons-es'  
    elif project_name == 'commons-utils':
        return 'commons-dev'
    else:
        return 'other'


def get_table_group_order(project_name: str, variant: str) -> int:
    """Get explicit table group order for any project + variant combination.
    
    This ensures proper paper ordering: EqBench variants, Commons-ES variants, Commons-dev variants.
    
    Args:
        project_name: Project name from database
        variant: Variant name from database
        
    Returns:
        Integer sort key for table ordering
    """
    project_type = get_project_type(project_name)
    
    # Base group numbers for each project type (large gaps to ensure separation)
    base_order = {
        'eqbench': 0,
        'commons-es': 100,  # Large gap to separate from eqbench
        'commons-dev': 200, # Even larger gap to ensure it's always last
        'other': 300
    }
    
    # Variant order within each project type
    variant_order = {
        'ORIGINAL': 0, 'INITIAL': 1, 'BASELINE': 2,
        'NAIVE_10_TRIES': 3, 'NAIVE_50_TRIES': 4, 'NAIVE_200_TRIES': 5,
        'IMPROVED_10_TRIES': 6, 'IMPROVED_50_TRIES': 7, 'IMPROVED_200_TRIES': 8
    }
    
    return base_order[project_type] + variant_order.get(variant, 99)


def get_project_within_type_order() -> dict:
    """Get project ordering within each project type.
    
    Returns:
        Dictionary mapping project names to their order within their type
    """
    return {
        # EqBench projects (1s, 10s, 60s)
        'eqbench-es-default-1s': 0,
        'eqbench-es-default-10s': 1, 
        'eqbench-es-default-60s': 2,
        # Commons ES projects (1s, 10s, 60s)
        'commons-utils-es-default-1s': 0,
        'commons-utils-es-default-10s': 1,
        'commons-utils-es-default-60s': 2,
        # Commons dev project (only one)
        'commons-utils': 0
    }


def save_latex_table(content: str, filename: str, output_dir: str = None) -> None:
    """Save LaTeX table content to file.
    
    Args:
        content: LaTeX table content
        filename: Output filename (without extension)
        output_dir: Output directory (if None, uses tables output directory)
    """
    # Use tables output directory if not specified
    if output_dir is None:
        output_path = get_tables_output_dir()
    else:
        output_path = Path(output_dir)
    
    # Create output directory if it doesn't exist
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Write content to file
    filepath = output_path / f"{filename}.tex"
    with open(filepath, 'w') as f:
        f.write(content)
    
    print(f"Saved LaTeX table to {filepath}")


def save_figure(
    figure: matplotlib.figure.Figure, 
    filename: str,
    format: str = 'pdf',
    dpi: int = 300,
    bbox_inches: str = 'tight',
    **kwargs
) -> Path:
    """Save a matplotlib figure to the figures output directory.
    
    Args:
        figure: Matplotlib figure object to save
        filename: Output filename (without extension)
        format: Output format (default: 'pdf')
        dpi: Resolution in dots per inch (default: 300)
        bbox_inches: Bounding box setting (default: 'tight')
        **kwargs: Additional arguments passed to savefig
        
    Returns:
        Path: Full path to the saved figure file
        
    Example:
        >>> import matplotlib.pyplot as plt
        >>> fig = plt.figure()
        >>> plt.plot([1, 2, 3], [4, 5, 6])
        >>> save_figure(fig, 'my_plot')
    """
    # Get figures output directory
    figures_dir = get_figures_output_dir()
    
    # Ensure filename has the correct extension
    if not filename.endswith(f'.{format}'):
        filename = f"{filename}.{format}"
    
    # Build full path
    figure_path = figures_dir / filename
    
    # Save the figure
    figure.savefig(
        figure_path,
        format=format,
        dpi=dpi,
        bbox_inches=bbox_inches,
        **kwargs
    )
    
    print(f"Saved figure to {figure_path}")
    return figure_path



__all__ = [
    'save_csv_data', 'save_latex_table', 'save_figure',
    'get_data_output_dir', 'get_tables_output_dir', 'get_figures_output_dir',
    'standardize_project_name',
    'get_dataset_macro', 'get_variant_macro', 'get_tool_macro',
    'format_table_row', 'get_project_type', 'get_table_group_order',
    'get_project_within_type_order', 'DATASETS', 'VARIANTS', 'TOOLS',
    'format_runtime_seconds', 'format_detection_rate_decimal'
]