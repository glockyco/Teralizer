"""Named value formatters. One source of truth used by every renderer and by
`ColumnSpec.fmt` / `Metric.fmt`."""

from __future__ import annotations

from collections.abc import Callable
import math


def _optional_int(value: object) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return "—"
    return f"{int(value):,}"


def _runtime(value: float) -> str:
    total = int(round(float(value)))
    h, rem = divmod(total, 3600)
    m, s = divmod(rem, 60)
    parts = []
    if h:
        parts.append(f"{h}h")
    if h or m:
        parts.append(f"{m}m")
    parts.append(f"{s}s")
    return " ".join(parts)


_FORMATTERS: dict[str, Callable[[object], str]] = {
    "str": lambda v: str(v),
    "int": lambda v: str(int(v)),
    "count": lambda v: f"{int(v):,}",
    "pvc": _optional_int,
    "pct1": lambda v: f"{float(v) * 100:.1f}%",
    "pct2": lambda v: f"{float(v) * 100:.2f}%",
    "float2": lambda v: f"{float(v):.2f}",
    "runtime": lambda v: _runtime(float(v)),
}


def render_value(value: object, fmt: str) -> str:
    if fmt not in _FORMATTERS:
        raise KeyError(f"unknown formatter: {fmt}")
    return _FORMATTERS[fmt](value)
