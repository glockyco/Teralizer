from decimal import Decimal

import pandas as pd

from teralizer.eval.entities import ref
from teralizer.eval.model import ValueKind
from teralizer.eval.render.latex import render_table
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
                "avg_used_constraint_pct": [47.9],
                "median_used_constraint_pct": [80.0],
            }
        )
    )
    row = table.df.iloc[0]
    assert row["project_name"] == ref("dataset.eqbench_a")
    assert row["project_group"] == "eqbench"
    assert row["avg_model_operation_count"] == Decimal("147")
    assert row["median_model_operation_count"] == Decimal("9")
    assert row["avg_total_constraint_count"] == Decimal("6")
    assert row["avg_used_constraint_pct"] == Decimal("47")
    assert [column.source for column in table.columns][:4] == [
        "project_name",
        "is_detected",
        "count",
        "avg_model_operation_count",
    ]
    assert table.key == "tab-mutation-detection-comparison"
    assert table.label == "tab:mutation-detection-comparison"
    assert table.short_caption == (
        "Operations and constraints for {entity.variant.improved_c} detections and misses"
    )
    assert table.body_style == r"\tabstyle"
    assert table.full_width
    assert table.group_by == "project_group"
    assert table.columns[0].kind is ValueKind.ENTITY
    assert [column.header for column in table.columns[-2:]] == ["Mean", "Median"]
    assert [column.kind for column in table.columns[-2:]] == [
        ValueKind.PERCENT,
        ValueKind.PERCENT,
    ]
    tex = render_table(table)
    assert "Mean \\%" not in tex
    assert "Median \\%" not in tex
    assert "47\\% & 80\\%" in tex
    assert [column.group_header for column in table.columns[3:]] == [
        "Operations",
        "Operations",
        "Constraints",
        "Constraints",
        "Constraints Used",
        "Constraints Used",
    ]


def test_rq2_complexity_table_separates_only_dataset_families():
    projects = [
        "eqbench-es-default-1s",
        "eqbench-es-default-1s",
        "eqbench-es-default-10s",
        "commons-utils-es-default-1s",
        "commons-utils",
    ]
    size = len(projects)
    table = _table(
        pd.DataFrame(
            {
                "project_name": projects,
                "is_detected": ["yes", "no", "yes", "yes", "yes"],
                "count": [1] * size,
                "mutant_percent": [1.0] * size,
                "avg_model_operation_count": [1.0] * size,
                "median_model_operation_count": [1.0] * size,
                "avg_total_constraint_count": [1.0] * size,
                "median_total_constraint_count": [1.0] * size,
                "avg_used_constraint_pct": [1.0] * size,
                "median_used_constraint_pct": [1.0] * size,
            }
        )
    )

    tex = render_table(table)

    assert tex.splitlines().count("  \\midrule") == 3
    assert tex.count(r"\DatasetEqBenchA{}") == 2
    assert r"\DatasetEqBenchB{}" in tex
    assert r"\DatasetCommonsA{}" in tex
    assert r"\DatasetCommonsDev{}" in tex
