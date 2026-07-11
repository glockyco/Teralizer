import pytest
from teralizer.eval.format import render_value


@pytest.mark.parametrize(
    "value,fmt,expected",
    [
        (0.794, "pct1", "79.4%"),
        (0.7943, "pct2", "79.43%"),
        (11547, "int", "11547"),
        (3598, "count", "3,598"),
        (1.5, "float2", "1.50"),
        (None, "pvc", "—"),
        (3661.0, "runtime", "1h 1m 1s"),
        ("FULL", "str", "FULL"),
    ],
)
def test_render_value(value, fmt, expected):
    assert render_value(value, fmt) == expected


def test_unknown_format_raises():
    with pytest.raises(KeyError):
        render_value(1, "nope")
