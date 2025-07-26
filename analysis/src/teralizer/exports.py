"""Data export utilities for generating both LaTeX tables and CSV datasets.

This module provides functions to export analysis results in multiple formats:
- LaTeX tables for paper inclusion (via mappings.py)
- CSV files for LLM processing and analysis
"""

import pandas as pd
import os
from pathlib import Path
from typing import Optional, Any


def get_data_output_dir() -> Path:
    """Get the data output directory, creating it if it doesn't exist.
    
    Uses the same project root detection pattern as mappings.py for consistency.
    
    Returns:
        Path: Absolute path to analysis/output/data/ directory
    """
    # Find project root by looking for .env file (same pattern as mappings.py)
    current_dir = Path.cwd().resolve()
    project_root = None
    
    # Try current directory and its parents
    for path in [current_dir] + list(current_dir.parents):
        if (path / '.env').exists():
            project_root = path
            break
    
    # Fallback: assume we're in analysis/ or analysis/notebooks/
    if project_root is None:
        if current_dir.name == 'analysis':
            project_root = current_dir.parent
        elif current_dir.name == 'notebooks' and current_dir.parent.name == 'analysis':
            project_root = current_dir.parent.parent
        else:
            # Last resort: traverse up looking for analysis directory
            for path in [current_dir] + list(current_dir.parents):
                if (path / 'analysis').exists():
                    project_root = path
                    break
    
    if project_root is None:
        raise RuntimeError("Could not find project root directory")
    
    # Create data output directory
    data_dir = project_root / 'analysis' / 'output' / 'data'
    data_dir.mkdir(parents=True, exist_ok=True)
    
    return data_dir


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


def standardize_variant_name(variant: str) -> str:
    """Standardize variant name for consistency across datasets.
    
    Args:
        variant: Raw variant name (may be None)
        
    Returns:
        str: Standardized variant name
    """
    if pd.isna(variant) or variant is None or variant == '':
        return 'SHARED'
    
    return str(variant).strip()


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




# Re-export save_latex_table from mappings for convenience
try:
    from .mappings import save_latex_table
    __all__ = ['save_csv_data', 'save_latex_table', 'get_data_output_dir', 
               'standardize_project_name', 'standardize_variant_name',
               'format_runtime_seconds', 'format_detection_rate_decimal']
except ImportError:
    __all__ = ['save_csv_data', 'get_data_output_dir', 
               'standardize_project_name', 'standardize_variant_name',
               'format_runtime_seconds', 'format_detection_rate_decimal']