from decimal import Decimal

import pytest

from teralizer.eval.format import render_metric


@pytest.mark.parametrize(
    "value,fmt,expected",
    [
        ("unavailable", "str", "unavailable"),
        (0.794, "pct1", "79.4%"),
        (Decimal("0.6375"), "pct1", "63.8%"),
        (11547, "int", "11547"),
        (3598, "count", "3,598"),
    ],
)
def test_render_metric(value, fmt, expected):
    assert render_metric(value, fmt) == expected


def test_unknown_metric_format_raises():
    with pytest.raises(KeyError):
        render_metric(1, "nope")
