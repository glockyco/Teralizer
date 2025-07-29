"""Tests for the plotting module.

Tests for matplotlib configuration, color management, and plotting utilities.
"""

import pytest
import matplotlib as mpl
import matplotlib.pyplot as plt
from unittest.mock import MagicMock
from teralizer.plotting import (
    setup_paper_style,
    setup_presentation_style,
    reset_to_defaults,
    configure_font_sizes,
    prettify_variant_label,
    format_runtime_display,
    seconds_to_hours_minutes_seconds,
    format_runtime_short,
    get_distinct_colors,
    get_project_color_map,
    get_variant_color_map,
    extend_color_palette,
    calculate_bar_positions,
    setup_multi_subplot_layout,
    add_value_labels_on_bars,
    style_runtime_axis,
    setup_legend_horizontal,
    add_grid_styling,
    create_pareto_front_line,
    prepare_multiline_xtick_labels,
    format_detection_percentage
)


def test_setup_paper_style():
    """Test that paper style configuration sets expected rcParams."""
    # Reset to defaults first
    reset_to_defaults()
    
    setup_paper_style()
    
    # Check key parameters
    assert mpl.rcParams['font.family'][0] == 'serif'
    assert 'Linux Libertine' in mpl.rcParams['font.serif']
    assert mpl.rcParams['axes.facecolor'] == 'white'
    assert mpl.rcParams['savefig.dpi'] == 300
    assert mpl.rcParams['savefig.format'] == 'pdf'
    assert mpl.rcParams['axes.grid']


def test_setup_presentation_style():
    """Test that presentation style configuration sets expected rcParams."""
    reset_to_defaults()
    
    setup_presentation_style()
    
    # Check key parameters
    assert mpl.rcParams['font.family'][0] == 'sans-serif'
    assert 'Arial' in mpl.rcParams['font.sans-serif']
    assert mpl.rcParams['axes.linewidth'] == 1.5
    assert mpl.rcParams['grid.linestyle'] == '-'


def test_reset_to_defaults():
    """Test that reset_to_defaults restores original settings."""
    # Change some settings
    setup_paper_style()
    
    # Reset
    reset_to_defaults()
    
    # Should be back to matplotlib defaults
    assert mpl.rcParams['font.family'] == mpl.rcParamsDefault['font.family']
    assert mpl.rcParams['axes.facecolor'] == mpl.rcParamsDefault['axes.facecolor']


def test_configure_font_sizes_valid():
    """Test valid font size configuration."""
    configure_font_sizes(20)
    
    assert plt.rcParams['font.size'] == 20
    assert plt.rcParams['axes.titlesize'] == 22
    assert plt.rcParams['axes.labelsize'] == 20
    assert plt.rcParams['xtick.labelsize'] == 18
    assert plt.rcParams['ytick.labelsize'] == 18
    assert plt.rcParams['legend.fontsize'] == 18


def test_configure_font_sizes_invalid():
    """Test that invalid font sizes raise ValueError."""
    with pytest.raises(ValueError, match="Base font size must be positive"):
        configure_font_sizes(0)
    
    with pytest.raises(ValueError, match="Base font size must be positive"):
        configure_font_sizes(-5)


def test_prettify_variant_label():
    """Test variant label formatting with LaTeX subscripts."""
    assert prettify_variant_label('NAIVE_10_TRIES') == 'NAIVE$_{10}$'
    assert prettify_variant_label('IMPROVED_200_TRIES') == 'IMPROVED$_{200}$'
    assert prettify_variant_label('BASELINE') == 'BASELINE'
    assert prettify_variant_label('ORIGINAL') == 'ORIGINAL'
    assert prettify_variant_label('123') == '123'


def test_format_runtime_display_auto():
    """Test automatic runtime formatting."""
    # Less than 10 seconds
    assert format_runtime_display(5.7) == '5.7'
    
    # 10+ seconds but less than 1 hour
    assert format_runtime_display(45.0) == '45'
    
    # 1+ hours
    result = format_runtime_display(3661.0)  # 1h 1min 1s
    assert 'h' in result and 'min' in result and 's' in result


def test_format_runtime_display_specific_formats():
    """Test specific runtime format types."""
    seconds = 3661.0  # 1h 1min 1s
    
    hms_result = format_runtime_display(seconds, 'hms')
    assert hms_result == '1h 01min 01s'
    
    numeric_result = format_runtime_display(seconds, 'numeric')
    assert numeric_result == '3661.0'
    
    short_result = format_runtime_display(seconds, 'short')
    assert short_result == '3.7k'


def test_format_runtime_display_invalid():
    """Test invalid runtime formatting inputs."""
    with pytest.raises(ValueError, match="Runtime cannot be negative"):
        format_runtime_display(-1.0)
    
    with pytest.raises(ValueError, match="Unknown format_type"):
        format_runtime_display(100.0, 'invalid')


def test_seconds_to_hours_minutes_seconds():
    """Test HMS formatting."""
    # Simple case
    assert seconds_to_hours_minutes_seconds(3661) == '1h 01min 01s'
    
    # Zero case
    assert seconds_to_hours_minutes_seconds(0) == '0h 00min 00s'
    
    # Large values
    assert seconds_to_hours_minutes_seconds(25 * 3600 + 30 * 60 + 45) == '25h 30min 45s'


def test_format_runtime_short():
    """Test short runtime formatting."""
    assert format_runtime_short(5.7) == '5.7'
    assert format_runtime_short(45.0) == '45'
    assert format_runtime_short(150.0) == '150'
    assert format_runtime_short(1500.0) == '1.5k'


def test_get_distinct_colors_valid():
    """Test distinct color generation."""
    colors = get_distinct_colors(5)
    assert len(colors) == 5
    assert all(color.startswith('#') for color in colors)
    assert len(set(colors)) == 5  # All unique
    
    # Test larger number requiring colormap extension
    colors_large = get_distinct_colors(20)
    assert len(colors_large) == 20
    assert len(set(colors_large)) == 20


def test_get_distinct_colors_invalid():
    """Test invalid color count."""
    with pytest.raises(ValueError, match="Number of colors must be positive"):
        get_distinct_colors(0)
    
    with pytest.raises(ValueError, match="Number of colors must be positive"):
        get_distinct_colors(-3)


def test_get_project_color_map():
    """Test project color mapping."""
    projects = ['project1', 'project2', 'project3']
    color_map = get_project_color_map(projects)
    
    assert len(color_map) == 3
    assert all(project in color_map for project in projects)
    assert all(color.startswith('#') for color in color_map.values())
    assert len(set(color_map.values())) == 3  # All unique colors
    
    # Empty list
    assert get_project_color_map([]) == {}


def test_get_variant_color_map():
    """Test variant color mapping."""
    variants = ['ORIGINAL', 'INITIAL', 'BASELINE']
    color_map = get_variant_color_map(variants)
    
    assert len(color_map) == 3
    assert all(variant in color_map for variant in variants)
    assert all(color.startswith('#') for color in color_map.values())
    
    # Empty list
    assert get_variant_color_map([]) == {}


def test_extend_color_palette():
    """Test color palette extension."""
    base_colors = ['#ff0000', '#00ff00', '#0000ff']
    
    # Same length
    result = extend_color_palette(base_colors, 3)
    assert result == base_colors
    
    # Extension needed  
    result = extend_color_palette(base_colors, 5)
    assert len(result) == 5
    assert result[:3] == base_colors
    assert all(color.startswith('#') for color in result[3:])


def test_extend_color_palette_invalid():
    """Test invalid color palette extension."""
    base_colors = ['#ff0000', '#00ff00']
    
    with pytest.raises(ValueError, match="Needed count .* must be >= base colors length"):
        extend_color_palette(base_colors, 1)


def test_calculate_bar_positions():
    """Test bar position calculations."""
    groups = ['group1', 'group2']
    variants = ['var1', 'var2', 'var3']
    
    group_centers, bar_positions = calculate_bar_positions(groups, variants)
    
    # Check group centers
    assert len(group_centers) == 2
    assert 'group1' in group_centers
    assert 'group2' in group_centers
    
    # Check bar positions
    assert len(bar_positions) == 6  # 2 groups * 3 variants
    assert ('group1', 'var1') in bar_positions
    assert ('group2', 'var3') in bar_positions
    
    # Positions should be numeric
    assert all(isinstance(pos, (int, float)) for pos in bar_positions.values())


def test_calculate_bar_positions_invalid():
    """Test invalid bar position calculations."""
    with pytest.raises(ValueError, match="Groups list cannot be empty"):
        calculate_bar_positions([], ['var1'])
    
    with pytest.raises(ValueError, match="Variants list cannot be empty"):
        calculate_bar_positions(['group1'], [])
    
    with pytest.raises(ValueError, match="Bar width must be positive"):
        calculate_bar_positions(['group1'], ['var1'], bar_width=0)
    
    with pytest.raises(ValueError, match="Bar spacing cannot be negative"):
        calculate_bar_positions(['group1'], ['var1'], bar_spacing=-0.1)


def test_setup_multi_subplot_layout():
    """Test multi-subplot layout setup."""
    fig, axes = setup_multi_subplot_layout(3)
    
    assert len(axes) == 3
    assert all(ax is not None for ax in axes)
    
    # Single subplot should still return list
    fig_single, axes_single = setup_multi_subplot_layout(1)
    assert len(axes_single) == 1
    
    plt.close(fig)
    plt.close(fig_single)


def test_setup_multi_subplot_layout_invalid():
    """Test invalid subplot layout setup."""
    with pytest.raises(ValueError, match="Number of subplots must be positive"):
        setup_multi_subplot_layout(0)


def test_add_value_labels_on_bars():
    """Test adding value labels on bars."""
    fig, ax = plt.subplots()
    bars = ax.bar([1, 2, 3], [10, 20, 30])
    
    # Should not raise error
    add_value_labels_on_bars(ax, bars)
    
    # Custom formatter
    def custom_formatter(x):
        return f"{x:.0f}s"
    
    add_value_labels_on_bars(ax, bars, custom_formatter)
    
    plt.close(fig)


def test_add_value_labels_on_bars_invalid():
    """Test invalid value label additions."""
    with pytest.raises(ValueError, match="Axes object cannot be None"):
        add_value_labels_on_bars(None, MagicMock())
    
    with pytest.raises(ValueError, match="Bars object cannot be None"):
        add_value_labels_on_bars(MagicMock(), None)


def test_style_runtime_axis():
    """Test runtime axis styling."""
    fig, ax = plt.subplots()
    
    style_runtime_axis(ax, 100.0)
    
    assert ax.get_ylabel() == 'Runtime (s)'
    assert ax.get_ylim() == (0.0, 100.0)
    
    plt.close(fig)


def test_style_runtime_axis_invalid():
    """Test invalid runtime axis styling."""
    with pytest.raises(ValueError, match="Axes object cannot be None"):
        style_runtime_axis(None, 100.0)
    
    fig, ax = plt.subplots()
    with pytest.raises(ValueError, match="Max seconds cannot be negative"):
        style_runtime_axis(ax, -10.0)
    plt.close(fig)


def test_setup_legend_horizontal():
    """Test horizontal legend setup."""
    fig, ax = plt.subplots()
    handles = [plt.Line2D([0], [0], color='red'), plt.Line2D([0], [0], color='blue')]
    labels = ['Label1', 'Label2']
    
    # Should not raise error
    setup_legend_horizontal(fig, handles, labels)
    
    plt.close(fig)


def test_setup_legend_horizontal_invalid():
    """Test invalid horizontal legend setup."""
    fig, ax = plt.subplots()
    handles = [plt.Line2D([0], [0], color='red')]
    labels = ['Label1', 'Label2']
    
    with pytest.raises(ValueError, match="Figure object cannot be None"):
        setup_legend_horizontal(None, handles, labels)
    
    with pytest.raises(ValueError, match="Handles and labels length mismatch"):
        setup_legend_horizontal(fig, handles, labels)
    
    plt.close(fig)


def test_add_grid_styling():
    """Test grid styling."""
    fig, ax = plt.subplots()
    
    # Should not raise error
    add_grid_styling(ax)
    
    plt.close(fig)


def test_add_grid_styling_invalid():
    """Test invalid grid styling."""
    with pytest.raises(ValueError, match="Axes object cannot be None"):
        add_grid_styling(None)


def test_create_pareto_front_line():
    """Test Pareto front line creation."""
    fig, ax = plt.subplots()
    x_data = [1, 2, 3]
    y_data = [10, 20, 30]
    
    # Should not raise error
    create_pareto_front_line(ax, x_data, y_data)
    
    plt.close(fig)


def test_create_pareto_front_line_invalid():
    """Test invalid Pareto front line creation."""
    fig, ax = plt.subplots()
    
    with pytest.raises(ValueError, match="Axes object cannot be None"):
        create_pareto_front_line(None, [1, 2], [3, 4])
    
    with pytest.raises(ValueError, match="X and Y data length mismatch"):
        create_pareto_front_line(ax, [1, 2], [3, 4, 5])
    
    plt.close(fig)


def test_prepare_multiline_xtick_labels():
    """Test multiline xtick label preparation."""
    groups = ['Original Validation', 'Test Transformation', 'Unknown Group']
    result = prepare_multiline_xtick_labels(groups)
    
    assert len(result) == 3
    assert result[0] == 'Original\nValidation'
    assert 'BASELINE' in result[1]  # Test Transformation should have variant info
    assert result[2] == 'Unknown Group'  # Unknown should pass through unchanged


def test_format_detection_percentage():
    """Test detection percentage formatting."""
    # Decimal format (0.0-1.0)
    assert format_detection_percentage(0.853) == '85.3%'
    assert format_detection_percentage(0.853, 0) == '85%'
    
    # Percentage format (0-100)
    assert format_detection_percentage(85.3) == '85.3%'
    assert format_detection_percentage(85.3, 2) == '85.30%'


def test_format_detection_percentage_invalid():
    """Test invalid detection percentage formatting."""
    with pytest.raises(ValueError, match="Decimal places cannot be negative"):
        format_detection_percentage(0.5, -1)


@pytest.mark.parametrize("func_name,args", [
    ('get_distinct_colors', [5]),
    ('get_project_color_map', [['proj1', 'proj2']]),
    ('get_variant_color_map', [['var1', 'var2']]),
    ('prettify_variant_label', ['NAIVE_10_TRIES']),
    ('format_runtime_short', [123.4]),
    ('seconds_to_hours_minutes_seconds', [3661])
])
def test_functions_return_expected_types(func_name, args):
    """Test that functions return expected types."""
    func = globals()[func_name]
    result = func(*args)
    
    if func_name.endswith('_color_map'):
        assert isinstance(result, dict)
    elif func_name == 'get_distinct_colors':
        assert isinstance(result, list)
        assert all(isinstance(color, str) for color in result)
    else:
        assert isinstance(result, str)