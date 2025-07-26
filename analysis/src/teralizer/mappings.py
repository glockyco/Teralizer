"""LaTeX mappings for dataset and variant names used in paper tables.

Provides consistent LaTeX macro mappings for dataset names and algorithm 
variants to ensure consistency between notebook outputs and the paper 
repository.
"""

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


def get_analysis_output_dir() -> 'Path':
    """Get absolute path to analysis output directory.
    
    Uses existing project root detection to reliably find the analysis
    output directory regardless of current working directory.
    
    Returns:
        Absolute path to analysis/output/tables directory
    """
    from pathlib import Path
    
    # Import here to avoid circular imports
    import sys
    import os
    
    # Find project root by looking for .env file (same logic as config.py)
    current = Path(__file__).parent
    for _ in range(10):  # Prevent infinite loop
        env_file = current / '.env'
        if env_file.exists():
            project_root = current
            break
        if current == current.parent:
            # Fallback: assume we're in analysis/src/teralizer/
            project_root = Path(__file__).parent.parent.parent.parent
            break
        current = current.parent
    else:
        # Last fallback
        project_root = Path(__file__).parent.parent.parent.parent
    
    return project_root / 'analysis' / 'output' / 'tables'


def save_latex_table(content: str, filename: str, output_dir: str = None) -> None:
    """Save LaTeX table content to file.
    
    Args:
        content: LaTeX table content
        filename: Output filename (without extension)
        output_dir: Output directory (if None, uses analysis output directory)
    """
    from pathlib import Path
    
    # Use analysis output directory if not specified
    if output_dir is None:
        output_path = get_analysis_output_dir()
    else:
        output_path = Path(output_dir)
    
    # Create output directory if it doesn't exist
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Write content to file
    filepath = output_path / f"{filename}.tex"
    with open(filepath, 'w') as f:
        f.write(content)
    
    print(f"Saved LaTeX table to {filepath}")