"""Metric key -> LaTeX-legal macro name.

`\\newcommand` names must be letters-only, so digits are spelled out and every
separator becomes a CamelCase boundary. A short `Tz` prefix avoids clobbering
existing paper/package commands.
"""

from __future__ import annotations

import re

_PREFIX = "Tz"
_DIGIT_WORDS = {
    "0": "zero",
    "1": "one",
    "2": "two",
    "3": "three",
    "4": "four",
    "5": "five",
    "6": "six",
    "7": "seven",
    "8": "eight",
    "9": "nine",
}


def _spell_digits(token: str) -> str:
    return "".join(_DIGIT_WORDS.get(ch, ch) for ch in token)


def macro_name(key: str) -> str:
    tokens = [t for t in re.split(r"[^0-9a-zA-Z]+", key) if t]
    if not tokens:
        raise ValueError(f"macro key has no alphanumeric content: {key!r}")
    camel = "".join(_spell_digits(t)[:1].upper() + _spell_digits(t)[1:] for t in tokens)
    name = _PREFIX + camel
    if not name.isalpha():
        raise ValueError(f"macro name not letters-only: {name!r} (from {key!r})")
    return name
