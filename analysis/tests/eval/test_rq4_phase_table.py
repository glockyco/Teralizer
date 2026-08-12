"""The EvoSuite phase table reports one row per project and search budget.

The query behind it returns one row per EvoSuite run, and the corpus holds nearly two thousand of
them, so the table aggregates before it is rendered.
"""

import pandas as pd

from teralizer.eval.reports.rq4_efficiency_evosuite import _phase_table

PHASES = [
    "total",
    "search",
    "inlining",
    "minimization",
    "coverage_analysis",
    "assertion_generation",
    "junit_check",
    "writing_tests",
]


def runs_frame():
    """Two projects at one budget each, with two runs apiece."""
    rows = []
    for project, budget, values in [
        ("commons-utils-es-default-10s", 10, (20.0, 30.0)),
        ("eqbench-es-default-60s", 60, (100.0, 140.0)),
    ]:
        for value in values:
            row = {"project_name": project, "search_budget": budget}
            row.update({phase: value for phase in PHASES})
            rows.append(row)
    return pd.DataFrame(rows)


def test_one_row_per_project_and_budget():
    table = _phase_table(runs_frame())
    assert len(table.df) == 2


def test_each_row_is_the_mean_of_its_runs():
    table = _phase_table(runs_frame())
    by_project = table.df.set_index("project_name")
    assert by_project.loc["commons-utils-es-default-10s", "total"] == 25.0
    assert by_project.loc["eqbench-es-default-60s", "total"] == 120.0


def test_the_budget_survives_aggregation():
    table = _phase_table(runs_frame())
    by_project = table.df.set_index("project_name")
    assert by_project.loc["commons-utils-es-default-10s", "search_budget"] == 10
    assert by_project.loc["eqbench-es-default-60s", "search_budget"] == 60


def test_an_empty_frame_produces_an_empty_table():
    table = _phase_table(pd.DataFrame(columns=["project_name", "search_budget"]))
    assert table.df.empty
