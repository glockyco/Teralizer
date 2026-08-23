"""The analysis reads the refusal code that `WideningLicense` recorded.

That only works while both sides agree on the set of codes, so the first test reads the Java and
fails when a code is added on one side alone.
"""

import re
from pathlib import Path

import pandas as pd
import pytest

from teralizer.eval.reports._widening import (
    WIDENING_REFUSALS,
    widening_refusal_metrics,
)

WIDENING_LICENSE = (
    Path(__file__).resolve().parents[3]
    / "src/main/java/teralizer/generalization/WideningLicense.java"
)

# The verdict for a refused generalization, written to `generalization.exclusion_info`. Every
# refusal carries it, so it names no cause and gets no entry in the table.
EXCLUSION_LABEL = "ORACLE_NOT_WIDENABLE"


def java_refusal_codes() -> set[str]:
    source = WIDENING_LICENSE.read_text(encoding="utf-8")
    declared = set(re.findall(r'public static final String (\w+) = "\1";', source))
    assert declared, f"no refusal codes found in {WIDENING_LICENSE}"
    return declared - {EXCLUSION_LABEL}


def test_every_java_refusal_code_has_a_label():
    assert java_refusal_codes() == set(WIDENING_REFUSALS)


def test_slugs_are_unique():
    slugs = [slug for slug, _ in WIDENING_REFUSALS.values()]
    assert len(slugs) == len(set(slugs))


def test_metrics_reject_a_code_the_table_does_not_know():
    df = pd.DataFrame(
        [
            {
                "code": "NEW_REFUSAL_CODE",
                "cause": "?",
                "refusals": 1,
                "refusal_total": 1,
                "refusals_pct": 1.0,
                "attempts": 1,
            }
        ]
    )
    with pytest.raises(RuntimeError, match="unmapped widening refusal code"):
        widening_refusal_metrics(df, provenance=None)


def test_metrics_name_each_code_by_its_slug():
    df = pd.DataFrame(
        [
            {
                "code": "NULL_CONCRETE_OUTPUT_NOT_LITERAL",
                "cause": "?",
                "refusals": 7,
                "refusal_total": 7,
                "refusals_pct": 1.0,
                "attempts": 14,
            }
        ]
    )
    metrics = widening_refusal_metrics(df, provenance=None)
    keys = {metric.key for metric in metrics}
    assert "realworld.widening_refusal_output_not_literal" in keys
    assert "realworld.widening_refusal_output_not_literal_pct" in keys
    assert "realworld.widening_refusals" in keys
    overall = next(
        metric for metric in metrics if metric.key == "realworld.widening_refusals_pct"
    )
    assert overall.value == pytest.approx(0.5)
    assert overall.numerator_key == "realworld.widening_refusals"
    assert overall.denominator_key == "realworld.generalization_attempts"


def test_metrics_reject_nonconserving_refusal_branches():
    df = pd.DataFrame(
        [
            {
                "code": "NULL_CONCRETE_OUTPUT_NOT_LITERAL",
                "cause": "?",
                "refusals": 1,
                "refusal_total": 2,
                "refusals_pct": 0.5,
                "attempts": 3,
            }
        ]
    )

    with pytest.raises(RuntimeError, match="branches do not conserve"):
        widening_refusal_metrics(df, provenance=None)


def test_metrics_reject_inconsistent_attempt_denominators():
    df = pd.DataFrame(
        [
            {
                "code": "NULL_CONCRETE_OUTPUT_NOT_LITERAL",
                "cause": "?",
                "refusals": 1,
                "refusal_total": 2,
                "refusals_pct": 0.5,
                "attempts": 2,
            },
            {
                "code": "NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS",
                "cause": "?",
                "refusals": 1,
                "refusal_total": 2,
                "refusals_pct": 0.5,
                "attempts": 3,
            },
        ]
    )

    with pytest.raises(RuntimeError, match="disagree on attempt count"):
        widening_refusal_metrics(df, provenance=None)
