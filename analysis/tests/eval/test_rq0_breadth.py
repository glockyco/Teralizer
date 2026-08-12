"""JARVIS published a value for two of the twelve census projects.

The other ten hold no measurement, and the breadth table keeps them absent rather than zero, because
zero would report a measurement the paper never made. Anything that reads the table has to render
that absence instead of converting it.
"""

import math

import pandas as pd

from teralizer.eval.reports.rq0_jarvis import (
    CENSUS_VARIANT,
    JARVIS_PROJECT_PBT_PVC,
    TABLE1_PROJECTS,
    _build_breadth_table,
    _count_or_unavailable,
)


def ledger_frame():
    return pd.DataFrame(
        {
            "project": list(TABLE1_PROJECTS),
            "generalization_status": ["complete"] * len(TABLE1_PROJECTS),
        }
    )


def pvc_frame():
    return pd.DataFrame(
        {
            "project": list(TABLE1_PROJECTS),
            "variant": [CENSUS_VARIANT] * len(TABLE1_PROJECTS),
            "aggregate_pvc": range(len(TABLE1_PROJECTS)),
            "sound_muts": range(len(TABLE1_PROJECTS)),
            "sound_properties": range(len(TABLE1_PROJECTS)),
        }
    )


def test_a_project_jarvis_did_not_publish_stays_absent():
    breadth = _build_breadth_table(ledger_frame(), pvc_frame())
    projects = breadth.iloc[:-1]
    unpublished = projects[~projects["project"].isin(JARVIS_PROJECT_PBT_PVC)]
    assert not unpublished.empty, "expected projects without a published JARVIS value"
    assert unpublished["jarvis_successful_pbt_pvc"].isna().all()


def test_a_project_jarvis_published_keeps_its_value():
    breadth = _build_breadth_table(ledger_frame(), pvc_frame())
    projects = breadth.iloc[:-1]
    for project, expected in JARVIS_PROJECT_PBT_PVC.items():
        row = projects.loc[projects["project"].eq(project)]
        assert not row.empty, f"{project} missing from the breadth table"
        assert int(row.iloc[0]["jarvis_successful_pbt_pvc"]) == expected


def test_an_absent_value_renders_rather_than_converting():
    assert _count_or_unavailable(float("nan")) == "unavailable"
    assert _count_or_unavailable(None) == "unavailable"
    assert _count_or_unavailable(1708) == "1,708"


def test_the_total_row_sums_only_published_values():
    breadth = _build_breadth_table(ledger_frame(), pvc_frame())
    total = breadth.iloc[-1]
    assert total["jarvis_successful_pbt_pvc"] == sum(JARVIS_PROJECT_PBT_PVC.values())
    assert not math.isnan(float(total["jarvis_successful_pbt_pvc"]))
