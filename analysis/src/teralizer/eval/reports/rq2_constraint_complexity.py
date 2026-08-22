"""RQ2 constraint-complexity report."""

from __future__ import annotations

from typing import cast

import pandas as pd

from teralizer.eval.data import Required
from teralizer.eval.entities import ref_for_csv
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import (
    ColumnSpec,
    Metric,
    RQReport,
    Section,
    Table,
    ValueKind,
    decimal_value,
)
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.exports import get_project_type
from teralizer.rq1_mutation_detection import (
    compute_mutation_model_complexity,
    get_mutation_detection_comparison,
)

REQUIRES = (
    Required(
        "mv_mutation_detection_comparison",
        "view",
        (
            "project_name",
            "is_detected",
            "count",
            "avg_model_operation_count",
            "median_model_operation_count",
            "avg_total_constraint_count",
            "median_total_constraint_count",
            "avg_used_constraint_pct",
            "median_used_constraint_pct",
        ),
    ),
)


def _table(df: pd.DataFrame) -> Table:
    result = cast(
        pd.DataFrame,
        df[
            [
                "project_name",
                "is_detected",
                "count",
                "mutant_percent",
                "avg_model_operation_count",
                "median_model_operation_count",
                "avg_total_constraint_count",
                "median_total_constraint_count",
                "avg_used_constraint_pct",
                "median_used_constraint_pct",
            ]
        ],
    ).copy()
    result.loc[:, "project_group"] = result["project_name"].map(get_project_type)
    result.loc[:, "project_name"] = result["project_name"].map(
        lambda value: ref_for_csv("dataset", value)
    )
    for column in (
        "avg_model_operation_count",
        "median_model_operation_count",
        "avg_total_constraint_count",
        "median_total_constraint_count",
        "avg_used_constraint_pct",
        "median_used_constraint_pct",
    ):
        result = result.assign(
            **{column: result[column].map(lambda value: decimal_value(int(value), 0))}
        )
    columns = [
        ColumnSpec("Project", "project_name", ValueKind.ENTITY),
        ColumnSpec("Detected", "is_detected", align="c"),
        ColumnSpec("Mutants", "count", ValueKind.COUNT, "r"),
        ColumnSpec(
            "Mean",
            "avg_model_operation_count",
            ValueKind.DECIMAL,
            "r",
            group_header="Operations",
        ),
        ColumnSpec(
            "Median",
            "median_model_operation_count",
            ValueKind.DECIMAL,
            "r",
            group_header="Operations",
        ),
        ColumnSpec(
            "Mean",
            "avg_total_constraint_count",
            ValueKind.DECIMAL,
            "r",
            group_header="Constraints",
        ),
        ColumnSpec(
            "Median",
            "median_total_constraint_count",
            ValueKind.DECIMAL,
            "r",
            group_header="Constraints",
        ),
        ColumnSpec(
            "Mean",
            "avg_used_constraint_pct",
            kind=ValueKind.PERCENT,
            align="r",
            group_header="Constraints Used",
        ),
        ColumnSpec(
            "Median",
            "median_used_constraint_pct",
            kind=ValueKind.PERCENT,
            align="r",
            group_header="Constraints Used",
        ),
    ]
    return Table(
        "tab-mutation-detection-comparison",
        result,
        columns,
        "Model properties of mutants that are (not) detected by the {entity.variant.improved_c} variant.",
        "tab:mutation-detection-comparison",
        short_caption="Operations and constraints for {entity.variant.improved_c} detections and misses",
        group_by="project_group",
        body_style="\\tabstyle",
        full_width=True,
        provenance=capture(compute_mutation_model_complexity),
    )


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("controlled")
    data = compute_mutation_model_complexity(get_mutation_detection_comparison(conn))
    table = _table(data)
    metrics = [
        Metric(
            "rq2.mutant_groups",
            len(data),
            "count",
            capture(compute_mutation_model_complexity),
        )
    ]
    section = Section(
        "Constraint complexity",
        [
            table,
        ],
    )
    return RQReport("rq2", "RQ2 - Constraint complexity", [section], metrics)


register(
    "rq2",
    ReportSpec(build, (CorpusInputSpec("controlled", "controlled", REQUIRES),)),
)
