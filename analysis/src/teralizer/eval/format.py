"""Scalar metric formatting and missing-value detection."""

from __future__ import annotations

from collections.abc import Callable
from decimal import Decimal
import math


def is_missing(value: object) -> bool:
    """Identify a value that the source could not supply."""
    return (
        value is None
        or (isinstance(value, float) and math.isnan(value))
        or (isinstance(value, Decimal) and value.is_nan())
    )


_METRIC_FORMATTERS: dict[str, Callable[[object], str]] = {
    "str": str,
    "int": lambda value: str(int(value)),
    "count": lambda value: f"{int(value):,}",
    "pct1": lambda value: f"{Decimal(str(value)) * 100:.1f}%",
    "decimal2": lambda value: f"{Decimal(str(value)):.2f}",
    "percent2": lambda value: f"{Decimal(str(value)):.2f}%",
}


def render_metric(value: object, fmt: str) -> str:
    """Render one scalar metric for prose or a generated macro."""
    if fmt not in _METRIC_FORMATTERS:
        raise KeyError(f"unknown metric formatter: {fmt}")
    return _METRIC_FORMATTERS[fmt](value)
