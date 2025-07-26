"""LaTeX mappings for dataset and variant names used in paper tables.

Provides consistent LaTeX macro mappings for dataset names and algorithm 
variants to ensure consistency between notebook outputs and the paper 
repository.
"""

# Dataset name mappings to LaTeX macros
DATASETS = {
    'eqbench': r'\DatasetEqBench{}',
    'eqbench-es-1s': r'\DatasetEqBenchA{}',
    'eqbench-es-10s': r'\DatasetEqBenchB{}',
    'eqbench-es-60s': r'\DatasetEqBenchC{}',
    'commons-utils-dev': r'\DatasetCommonsDev{}',
    'commons-utils-es-1s': r'\DatasetCommonsA{}',
    'commons-utils-es-10s': r'\DatasetCommonsB{}',
    'commons-utils-es-60s': r'\DatasetCommonsC{}',
    'repo-reapers': r'\DatasetRepoReapers{}',
}

# Algorithm variant mappings to LaTeX macros
VARIANTS = {
    'original': r'\VariantOriginal{}',
    'initial': r'\VariantInitial{}',
    'baseline': r'\VariantBaseline{}',
    'naive': r'\VariantNaive{}',
    'naive-10': r'\VariantNaiveA{}',
    'naive-50': r'\VariantNaiveB{}',
    'naive-200': r'\VariantNaiveC{}',
    'improved': r'\VariantImproved{}',
    'improved-10': r'\VariantImprovedA{}',
    'improved-50': r'\VariantImprovedB{}',
    'improved-200': r'\VariantImprovedC{}',
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


def save_latex_table(content: str, filename: str, output_dir: str = 'output/tables') -> None:
    """Save LaTeX table content to file.
    
    Args:
        content: LaTeX table content
        filename: Output filename (without extension)
        output_dir: Output directory relative to analysis root
    """
    from pathlib import Path
    
    # Create output directory if it doesn't exist
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Write content to file
    filepath = output_path / f"{filename}.tex"
    with open(filepath, 'w') as f:
        f.write(content)
    
    print(f"Saved LaTeX table to {filepath}")