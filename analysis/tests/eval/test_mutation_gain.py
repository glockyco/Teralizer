import pandas as pd
import pytest

from teralizer.eval.reports._mutation_gain import (
    generalized_only_kill_metrics,
    validate_generalized_only_kills,
)


def _frame(rows: list[dict[str, int]]) -> pd.DataFrame:
    return pd.DataFrame(
        rows,
        columns=[
            "project_id",
            "mutant_pairs",
            "mutants",
            "unpaired_mutants",
            "covered_mutants",
            "generalized_only_kills",
        ],
    )


def _row(project_id: int, covered: int, kills: int, **overrides: int) -> dict[str, int]:
    row = {
        "project_id": project_id,
        "mutant_pairs": covered,
        "mutants": covered,
        "unpaired_mutants": 0,
        "covered_mutants": covered,
        "generalized_only_kills": kills,
    }
    return row | overrides


def test_gain_is_the_share_of_covered_mutants_across_all_projects():
    df = _frame([_row(1, 100, 3), _row(2, 200, 0), _row(3, 100, 1)])

    metrics = {
        metric.key: metric.value
        for metric in generalized_only_kill_metrics(df, provenance=None)
    }

    assert metrics == {
        "realworld.mutation.covered_mutants": 400,
        "realworld.mutation.generalized_only_kills": 4,
        "realworld.mutation.generalized_only_kill_projects": 2,
        "realworld.mutation.generalized_only_kills_pp": 1.0,
    }


def test_a_project_without_pit_reports_is_rejected():
    df = _frame([_row(1, 100, 3)])

    with pytest.raises(RuntimeError, match=r"without PIT mutation reports: \[2\]"):
        validate_generalized_only_kills(df, {1, 2})


def test_runs_that_report_different_mutants_are_rejected():
    df = _frame([_row(1, 100, 3, unpaired_mutants=1)])

    with pytest.raises(RuntimeError, match="report different mutants"):
        validate_generalized_only_kills(df, {1})


def test_mutants_that_share_one_identity_are_rejected():
    df = _frame([_row(1, 100, 3, mutant_pairs=104)])

    with pytest.raises(RuntimeError, match="share one identity"):
        validate_generalized_only_kills(df, {1})
