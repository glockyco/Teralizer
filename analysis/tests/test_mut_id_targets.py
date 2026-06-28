"""Tests for MUT-id project targeting tier logic."""

from __future__ import annotations

import pandas as pd

from teralizer.mut_id_targets import (
    _TIER_GENERALIZES,
    _TIER_NONE,
    _TIER_PARTIAL,
    assign_tier,
    rank_targets,
)


def _row(generalizations: int, included_assertions: int) -> pd.Series:
    return pd.Series(
        {
            "generalizations": generalizations,
            "included_assertions": included_assertions,
        }
    )


def test_tier_generalizes_when_project_already_generalizes():
    assert assign_tier(_row(72, 72)) == _TIER_GENERALIZES


def test_tier_partial_when_included_assertions_but_no_generalization():
    assert assign_tier(_row(0, 37)) == _TIER_PARTIAL


def test_tier_none_when_no_pipeline_progress():
    assert assign_tier(_row(0, 0)) == _TIER_NONE


def test_rank_targets_assigns_tier_per_row():
    df = pd.DataFrame(
        [
            {
                "project": "joschi_JadConfig",
                "mut_id_blocked": 885,
                "generalizations": 72,
                "included_assertions": 72,
                "oracle_eligible": True,
                "has_pit_data": True,
            },
            {
                "project": "frizbog_gedcom4j",
                "mut_id_blocked": 1225,
                "generalizations": 0,
                "included_assertions": 4,
                "oracle_eligible": True,
                "has_pit_data": False,
            },
            {
                "project": "urbanairship_java-library",
                "mut_id_blocked": 1020,
                "generalizations": 0,
                "included_assertions": 0,
                "oracle_eligible": True,
                "has_pit_data": False,
            },
        ]
    )

    ranked = rank_targets(df)

    assert list(ranked["tier"]) == [_TIER_GENERALIZES, _TIER_PARTIAL, _TIER_NONE]
    # rank_targets must not mutate the input frame.
    assert "tier" not in df.columns
