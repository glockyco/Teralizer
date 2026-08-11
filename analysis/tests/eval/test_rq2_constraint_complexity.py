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
        "avg_model_operation_count",
    ]
    assert table.df.loc[0, "avg_used_constraint_display"] == "47%"
    assert table.key == "tab-mutation-detection-comparison"
    assert table.label == "tab:mutation-detection-comparison"
    assert table.short_caption == (
        "Operations and constraints for \\VariantImprovedC{} detections and misses"
    )
    assert table.body_style == r"\tabstyle"
    assert table.full_width
    assert table.group_by == "project_name"
    assert [column.group_header for column in table.columns[3:]] == [
        "Operations",
        "Operations",
        "Constraints",
        "Constraints",
        "Constraints Used",
        "Constraints Used",
    ]
