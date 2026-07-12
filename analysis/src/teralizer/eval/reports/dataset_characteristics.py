"""Dataset-characteristics report for the controlled and real-world corpora."""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required
from teralizer.eval.model import ColumnSpec, Metric, Prose, RQReport, Section, Table
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.report_basis import open_report_connection
from teralizer.dataset_characteristics import get_dataset_statistics

REQUIRES = (Required("project", "table", ("id",)),)


def _table(df: pd.DataFrame) -> Table:
    result = df.copy()
    result["project"] = (
        result["project"]
        .astype(str)
        .str.replace("-default", "", regex=False)
        .replace({"commons-utils": "commons-utils-dev"})
    )
    wanted = [
        "project",
        "main_files",
        "main_classes",
        "main_sloc",
        "test_files",
        "test_classes",
        "test_sloc",
        "test_methods",
    ]
    for col in wanted:
        if col not in result:
            result[col] = 0
    result = result[wanted]
    columns = [
        ColumnSpec("Project", "project"),
        ColumnSpec("Implementation files", "main_files", "count"),
        ColumnSpec("Implementation classes", "main_classes", "count"),
        ColumnSpec("Implementation SLOC", "main_sloc", "count"),
        ColumnSpec("Test files", "test_files", "count"),
        ColumnSpec("Test classes", "test_classes", "count"),
        ColumnSpec("Test SLOC", "test_sloc", "count"),
        ColumnSpec("Test methods", "test_methods", "count"),
    ]
    return Table(
        "dataset_statistics",
        result,
        columns,
        "Dataset files, classes, source lines, and test methods per project.",
        "tab:dataset-statistics",
        provenance=capture(get_dataset_statistics),
    )


def build(conn: Connection) -> RQReport:
    with open_report_connection("postgres_test") as test_conn:
        stats = get_dataset_statistics(db_conn_dev=conn, db_conn_test=test_conn)
    table = _table(stats)
    metrics = [
        Metric(
            "dataset.projects",
            int(len(stats)),
            "count",
            capture(get_dataset_statistics),
        )
    ]
    section = Section(
        "Dataset characteristics",
        [
            Prose("The dataset summary contains {dataset.projects} aggregate rows."),
            table,
        ],
    )
    return RQReport(
        "dataset", "Dataset characteristics", "postgres_dev", [section], metrics
    )


register("dataset", ReportSpec(build, "postgres_dev", "old", REQUIRES))
