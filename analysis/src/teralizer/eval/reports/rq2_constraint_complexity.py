"""RQ2 constraint-complexity report."""

from __future__ import annotations

from typing import cast

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required
from teralizer.eval.model import ColumnSpec, Metric, Prose, RQReport, Section, Table
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
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
    columns = [
        ColumnSpec("Project", "project_name"),
        ColumnSpec("Detected", "is_detected"),
        ColumnSpec("Mutants", "count", "count"),
        ColumnSpec("Share", "mutant_percent", "float2"),
        ColumnSpec("Operations mean", "avg_model_operation_count", "float2"),
        ColumnSpec("Operations median", "median_model_operation_count", "float2"),
        ColumnSpec("Constraints mean", "avg_total_constraint_count", "float2"),
        ColumnSpec("Constraints median", "median_total_constraint_count", "float2"),
        ColumnSpec("Used constraints mean", "avg_used_constraint_pct", "float2"),
        ColumnSpec("Used constraints median", "median_used_constraint_pct", "float2"),
    ]
    return Table(
        "mutation_detection_comparison",
        result,
        columns,
        "Model properties of mutants that are (not) detected by the improved variant.",
        "tab:mutation-detection-comparison",
        provenance=capture(compute_mutation_model_complexity),
    )


def build(conn: Connection) -> RQReport:
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
            Prose(
                "The comparison contains {rq2.mutant_groups} project and detection groups."
            ),
            table,
        ],
    )
    return RQReport(
        "rq2", "RQ2 - Constraint complexity", "postgres_dev", [section], metrics
    )


register("rq2", ReportSpec(build, "postgres_dev", "old", REQUIRES))
