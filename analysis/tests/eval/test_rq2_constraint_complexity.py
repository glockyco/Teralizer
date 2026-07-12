import pandas as pd

from teralizer.eval.reports.rq2_constraint_complexity import _table


def test_rq2_complexity_table_preserves_thesis_statistics():
    table = _table(
        pd.DataFrame(
            {
                "project_name": ["eqbench-es-default-1s"],
                "is_detected": ["yes"],
                "count": [11145],
                "mutant_percent": [51.8],
                "avg_model_operation_count": [147.0],
                "median_model_operation_count": [9.0],
                "avg_total_constraint_count": [6.0],
                "median_total_constraint_count": [2.0],
                "avg_used_constraint_pct": [47.0],
                "median_used_constraint_pct": [80.0],
            }
        )
    )
    row = table.df.iloc[0]
    assert row["avg_model_operation_count"] == 147.0
    assert row["median_model_operation_count"] == 9.0
    assert row["avg_total_constraint_count"] == 6.0
    assert row["avg_used_constraint_pct"] == 47.0
    assert [column.source for column in table.columns][:4] == [
        "project_name",
        "is_detected",
        "count",
        "mutant_percent",
    ]
    assert table.label == "tab:mutation-detection-comparison"
