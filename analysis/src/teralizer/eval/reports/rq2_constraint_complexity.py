"""RQ2 constraint-complexity report."""

from __future__ import annotations

from typing import cast

import pandas as pd

from teralizer.eval.data import Required
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import ColumnSpec, Metric, RQReport, Section, Table
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
    result["avg_used_constraint_display"] = result["avg_used_constraint_pct"].map(
        lambda value: f"{float(value):.0f}%"
    )
    result["median_used_constraint_display"] = result["median_used_constraint_pct"].map(
        lambda value: f"{float(value):.0f}%"
    )
    columns = [
        ColumnSpec("Project", "project_name"),
        ColumnSpec("Detected", "is_detected", align="c"),
        ColumnSpec("Mutants", "count", "count", "r"),
        ColumnSpec(
            "Mean",
            "avg_model_operation_count",
            "count",
            "r",
            group_header="Operations",
        ),
        ColumnSpec(
            "Median",
            "median_model_operation_count",
            "count",
            "r",
            group_header="Operations",
        ),
        ColumnSpec(
            "Mean",
            "avg_total_constraint_count",
            "count",
            "r",
            group_header="Constraints",
        ),
        ColumnSpec(
            "Median",
            "median_total_constraint_count",
            "count",
            "r",
            group_header="Constraints",
        ),
        ColumnSpec(
            "Mean",
            "avg_used_constraint_display",
            align="r",
            group_header="Constraints Used",
        ),
        ColumnSpec(
            "Median",
            "median_used_constraint_display",
            align="r",
            group_header="Constraints Used",
        ),
    ]
    return Table(
        "tab-mutation-detection-comparison",
        result,
        columns,
        "Model properties of mutants that are (not) detected by the \\VariantImprovedC{} variant.",
        "tab:mutation-detection-comparison",
        short_caption="Operations and constraints for \\VariantImprovedC{} detections and misses",
        group_by="project_name",
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
