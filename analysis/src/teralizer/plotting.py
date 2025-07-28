"""Matplotlib configuration and plotting utilities for teralizer analysis.

This module provides functions for matplotlib setup, color management, and common
plotting patterns that are used across analysis notebooks.
"""

import matplotlib as mpl
import matplotlib.pyplot as plt
import numpy as np
import re
from typing import List, Dict, Optional, Tuple, Any


# =============================================================================
# Core configuration functions
# =============================================================================

def setup_paper_style() -> None:
    """Apply standard matplotlib settings for paper figures.
    
    Configures fonts, colors, grid, and output settings for academic publications.
    """
    mpl.rcParams.update({
        'font.family': 'serif',
        'font.serif': ['Linux Libertine', 'Libertine', 'Linux Libertine O', 'Times New Roman', 'Times'],
        'axes.facecolor': 'white',
        'figure.facecolor': 'white',
        'axes.edgecolor': 'black',
        'axes.linewidth': 1.2,
        'grid.color': '#cccccc',
        'grid.linestyle': '--',
        'grid.linewidth': 0.7,
        'axes.grid': True,
        'axes.axisbelow': True,
        'savefig.dpi': 300,
        'savefig.format': 'pdf',
        'pdf.fonttype': 42,
        'ps.fonttype': 42
    })


def setup_presentation_style() -> None:
    """Apply matplotlib settings optimized for presentations.
    
    Uses larger fonts and higher contrast for better visibility in presentations.
    """
    mpl.rcParams.update({
        'font.family': 'sans-serif',
        'font.sans-serif': ['Arial', 'DejaVu Sans', 'Liberation Sans'],
        'axes.facecolor': 'white',
        'figure.facecolor': 'white',
        'axes.edgecolor': 'black',
        'axes.linewidth': 1.5,
        'grid.color': '#888888',
        'grid.linestyle': '-',
        'grid.linewidth': 0.8,
        'axes.grid': True,
        'axes.axisbelow': True,
        'savefig.dpi': 300,
        'savefig.format': 'pdf',
        'pdf.fonttype': 42,
        'ps.fonttype': 42
    })


def reset_to_defaults() -> None:
    """Reset matplotlib to default settings."""
    mpl.rcParams.clear()
    mpl.rcParams.update(mpl.rcParamsDefault)


# =============================================================================
# Font and text utilities
# =============================================================================

def configure_font_sizes(base_size: int = 18) -> None:
    """Set consistent font sizes with scaling from base size.
    
    Args:
        base_size: Base font size in points
        
    Raises:
        ValueError: If base_size is not positive
    """
    if base_size <= 0:
        raise ValueError(f"Base font size must be positive, got {base_size}")
    
    plt.rcParams.update({
        'font.size': base_size,
        'axes.titlesize': base_size + 2,
        'axes.labelsize': base_size,
        'xtick.labelsize': base_size - 2,
        'ytick.labelsize': base_size - 2,
        'legend.fontsize': base_size - 2,
    })


def prettify_variant_label(label: str) -> str:
    """Format variant names with LaTeX subscripts.
    
    Converts NAIVE_10_TRIES to NAIVE$_{10}$ format.
    
    Args:
        label: Raw variant label
        
    Returns:
        Formatted label with LaTeX subscripts
    """
    if not isinstance(label, str):
        return str(label)
    
    return re.sub(r'_(\d+)_TRIES$', r'$_{\1}$', label)


def format_runtime_display(seconds: float, format_type: str = 'auto') -> str:
    """Format runtime values for display.
    
    Args:
        seconds: Runtime in seconds
        format_type: 'auto', 'hms', 'short', or 'numeric'
        
    Returns:
        Formatted runtime string
        
    Raises:
        ValueError: If format_type is not recognized
    """
    if seconds < 0:
        raise ValueError(f"Runtime cannot be negative, got {seconds}")
    
    if format_type == 'hms':
        return seconds_to_hours_minutes_seconds(seconds)
    elif format_type == 'short':
        return format_runtime_short(seconds)
    elif format_type == 'numeric':
        return f"{seconds:.1f}"
    elif format_type == 'auto':
        if seconds >= 3600:  # >= 1 hour
            return seconds_to_hours_minutes_seconds(seconds)
        elif seconds >= 10:
            return format_runtime_short(seconds)
        else:
            return f"{seconds:.1f}"
    else:
        raise ValueError(f"Unknown format_type: {format_type}")


def seconds_to_hours_minutes_seconds(seconds: float) -> str:
    """Convert seconds to HH:MM:SS format.
    
    Args:
        seconds: Runtime in seconds
        
    Returns:
        Formatted string like "24h 46min 27s"
    """
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    return f"{hours}h {minutes:02d}min {secs:02d}s"


def format_runtime_short(seconds: float) -> str:
    """Format runtime with appropriate precision for display.
    
    Args:
        seconds: Runtime in seconds
        
    Returns:
        Formatted string with appropriate precision
    """
    if seconds < 10:
        return f"{seconds:.1f}"
    elif seconds < 100:
        return f"{seconds:.0f}"
    elif seconds < 1000:
        return f"{seconds:.0f}"
    else:
        return f"{seconds/1000:.1f}k"


# =============================================================================
# Color management
# =============================================================================

def get_distinct_colors(n_colors: int) -> List[str]:
    """Generate distinct color palette.
    
    Args:
        n_colors: Number of colors needed
        
    Returns:
        List of hex color strings
        
    Raises:
        ValueError: If n_colors is not positive
    """
    if n_colors <= 0:
        raise ValueError(f"Number of colors must be positive, got {n_colors}")
    
    # Base distinct colors from matplotlib defaults
    base_colors = [
        '#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b', 
        '#e377c2', '#7f7f7f', '#bcbd22', '#17becf', '#aec7e8', '#ffbb78', 
        '#98df8a', '#ff9896', '#c5b0d5'
    ]
    
    if n_colors <= len(base_colors):
        return base_colors[:n_colors]
    
    # Extend with colormap if more colors needed
    additional_needed = n_colors - len(base_colors)
    additional_colors = plt.cm.Set3(np.linspace(0, 1, additional_needed))
    additional_hex = [f"#{int(c[0]*255):02x}{int(c[1]*255):02x}{int(c[2]*255):02x}" 
                     for c in additional_colors]
    
    return base_colors + additional_hex


def get_project_color_map(projects: List[str]) -> Dict[str, str]:
    """Get consistent color mapping for projects.
    
    Args:
        projects: List of project names
        
    Returns:
        Dictionary mapping project names to hex colors
    """
    if not projects:
        return {}
    
    colors = get_distinct_colors(len(projects))
    return {project: color for project, color in zip(projects, colors)}


def get_variant_color_map(variants: List[str]) -> Dict[str, str]:
    """Get consistent color mapping for variants.
    
    Args:
        variants: List of variant names
        
    Returns:
        Dictionary mapping variant names to hex colors
    """
    if not variants:
        return {}
    
    colors = get_distinct_colors(len(variants))
    return {variant: color for variant, color in zip(variants, colors)}


def extend_color_palette(base_colors: List[str], needed_count: int) -> List[str]:
    """Extend color palette when more colors are needed.
    
    Args:
        base_colors: Existing color list
        needed_count: Total number of colors needed
        
    Returns:
        Extended color list
        
    Raises:
        ValueError: If needed_count is less than base_colors length
    """
    if needed_count < len(base_colors):
        raise ValueError(f"Needed count ({needed_count}) must be >= base colors length ({len(base_colors)})")
    
    if needed_count == len(base_colors):
        return base_colors.copy()
    
    additional_needed = needed_count - len(base_colors)
    additional_colors = plt.cm.Set3(np.linspace(0, 1, additional_needed))
    additional_hex = [f"#{int(c[0]*255):02x}{int(c[1]*255):02x}{int(c[2]*255):02x}" 
                     for c in additional_colors]
    
    return base_colors + additional_hex


# =============================================================================
# Layout and spacing helpers
# =============================================================================

def calculate_bar_positions(groups: List[str], variants: List[str], 
                          bar_width: float = 0.3, bar_spacing: float = 0.05, 
                          group_spacing: float = 0.3) -> Tuple[Dict[str, float], Dict[Tuple[str, str], float]]:
    """Calculate bar positions for grouped bar charts.
    
    Args:
        groups: List of group names
        variants: List of variant names
        bar_width: Width of each bar
        bar_spacing: Spacing between bars within a group
        group_spacing: Spacing between groups
        
    Returns:
        Tuple of (group_centers, bar_positions) dictionaries
        
    Raises:
        ValueError: If groups or variants are empty, or if spacing values are negative
    """
    if not groups:
        raise ValueError("Groups list cannot be empty")
    if not variants:
        raise ValueError("Variants list cannot be empty")
    if bar_width <= 0:
        raise ValueError(f"Bar width must be positive, got {bar_width}")
    if bar_spacing < 0:
        raise ValueError(f"Bar spacing cannot be negative, got {bar_spacing}")
    if group_spacing < 0:
        raise ValueError(f"Group spacing cannot be negative, got {group_spacing}")
    
    # Calculate group widths
    num_bars = len(variants)
    group_width = (num_bars * bar_width) + ((num_bars - 1) * bar_spacing)
    
    # Calculate group centers
    group_centers = {}
    current_position = 0
    for group in groups:
        group_centers[group] = current_position + group_width / 2
        current_position += group_width + group_spacing
    
    # Calculate individual bar positions
    bar_positions = {}
    for group in groups:
        group_center = group_centers[group]
        start_pos = group_center - group_width / 2
        
        for i, variant in enumerate(variants):
            bar_positions[(group, variant)] = start_pos + i * (bar_width + bar_spacing) + bar_width / 2
    
    return group_centers, bar_positions


def setup_multi_subplot_layout(n_subplots: int, figsize_per_plot: Tuple[float, float] = (17, 3.1)) -> Tuple[Any, List[Any]]:
    """Setup consistent multi-subplot layout.
    
    Args:
        n_subplots: Number of subplots needed
        figsize_per_plot: Size per individual subplot
        
    Returns:
        Tuple of (figure, axes_list)
        
    Raises:
        ValueError: If n_subplots is not positive
    """
    if n_subplots <= 0:
        raise ValueError(f"Number of subplots must be positive, got {n_subplots}")
    
    total_height = figsize_per_plot[1] * n_subplots
    fig, axes = plt.subplots(n_subplots, 1, figsize=(figsize_per_plot[0], total_height))
    
    # Ensure axes is always a list
    if n_subplots == 1:
        axes = [axes]
    
    plt.subplots_adjust(hspace=0.3)
    
    return fig, axes


def add_value_labels_on_bars(ax: Any, bars: Any, formatter: Optional[callable] = None) -> None:
    """Add text labels on top of bars.
    
    Args:
        ax: Matplotlib axes object
        bars: Bar objects from ax.bar()
        formatter: Optional function to format values
        
    Raises:
        ValueError: If ax or bars is None
    """
    if ax is None:
        raise ValueError("Axes object cannot be None")
    if bars is None:
        raise ValueError("Bars object cannot be None")
    
    if formatter is None:
        formatter = format_runtime_short
    
    # Get y-axis range for offset calculation
    y_min, y_max = ax.get_ylim()
    offset = (y_max - y_min) * 0.02
    
    for bar in bars:
        height = bar.get_height()
        if height > 0:
            formatted_value = formatter(height)
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                height + offset,
                formatted_value,
                ha='center',
                va='bottom',
                fontsize=16,
                rotation=0
            )


# =============================================================================
# Common plot styling
# =============================================================================

def style_runtime_axis(ax: Any, max_seconds: float) -> None:
    """Format y-axis for runtime displays.
    
    Args:
        ax: Matplotlib axes object
        max_seconds: Maximum runtime value for axis scaling
        
    Raises:
        ValueError: If ax is None or max_seconds is negative
    """
    if ax is None:
        raise ValueError("Axes object cannot be None")
    if max_seconds < 0:
        raise ValueError(f"Max seconds cannot be negative, got {max_seconds}")
    
    ax.set_ylabel('Runtime (s)')
    ax.set_ylim(0, max_seconds)
    ax.ticklabel_format(style='plain', axis='y')


def setup_legend_horizontal(fig: Any, handles: List[Any], labels: List[str], 
                          bbox_to_anchor: Tuple[float, float] = (0.5, 1.0),
                          ncol: Optional[int] = None) -> None:
    """Setup horizontal legend at specified position.
    
    Args:
        fig: Matplotlib figure object
        handles: Legend handles
        labels: Legend labels
        bbox_to_anchor: Position tuple for legend placement
        ncol: Number of columns (defaults to len(labels))
        
    Raises:
        ValueError: If fig is None or if handles/labels lengths don't match
    """
    if fig is None:
        raise ValueError("Figure object cannot be None")
    if len(handles) != len(labels):
        raise ValueError(f"Handles and labels length mismatch: {len(handles)} vs {len(labels)}")
    
    if ncol is None:
        ncol = len(labels)
    
    fig.legend(
        handles,
        labels,
        loc='upper center',
        bbox_to_anchor=bbox_to_anchor,
        frameon=False,
        ncol=ncol
    )


def add_grid_styling(ax: Any) -> None:
    """Apply consistent grid appearance to axes.
    
    Args:
        ax: Matplotlib axes object
        
    Raises:
        ValueError: If ax is None
    """
    if ax is None:
        raise ValueError("Axes object cannot be None")
    
    ax.grid(axis='y', linestyle='--', alpha=0.7)
    ax.set_axisbelow(True)


def create_pareto_front_line(ax: Any, x_data: List[float], y_data: List[float]) -> None:
    """Draw Pareto front line on plot.
    
    Args:
        ax: Matplotlib axes object
        x_data: X coordinates of Pareto points
        y_data: Y coordinates of Pareto points
        
    Raises:
        ValueError: If ax is None or data lengths don't match
    """
    if ax is None:
        raise ValueError("Axes object cannot be None")
    if len(x_data) != len(y_data):
        raise ValueError(f"X and Y data length mismatch: {len(x_data)} vs {len(y_data)}")
    
    ax.plot(
        x_data, y_data,
        linestyle='--', color='black', linewidth=1.2, zorder=2, label='Pareto front'
    )


# =============================================================================
# Specialized formatting functions
# =============================================================================

def prepare_multiline_xtick_labels(groups: List[str]) -> List[str]:
    """Prepare multi-line x-tick labels for stage groups.
    
    Args:
        groups: List of stage group names
        
    Returns:
        List of formatted multi-line labels
    """
    label_mapping = {
        'Original Validation': 'Original\nValidation',
        'Specification Extraction': 'Specification\nExtraction', 
        'Initial Validation': 'Initial\nValidation',
        'Test Transformation': 'Test Transformation\n(BASELINE, NAIVE$_{10|50|200}$, IMPROVED$_{10|50|200}$)',
        'Generalization Validation': 'Generalization Validation\n(BASELINE, NAIVE$_{10|50|200}$, IMPROVED$_{10|50|200}$)'
    }
    
    return [label_mapping.get(group, group) for group in groups]


def format_detection_percentage(value: float, decimal_places: int = 1) -> str:
    """Format detection rate as percentage.
    
    Args:
        value: Detection rate (0.0 to 1.0 or 0 to 100)
        decimal_places: Number of decimal places
        
    Returns:
        Formatted percentage string
        
    Raises:
        ValueError: If decimal_places is negative
    """
    if decimal_places < 0:
        raise ValueError(f"Decimal places cannot be negative, got {decimal_places}")
    
    # Handle both decimal (0.0-1.0) and percentage (0-100) formats
    if value <= 1.0:
        percentage = value * 100
    else:
        percentage = value
    
    return f"{percentage:.{decimal_places}f}%"