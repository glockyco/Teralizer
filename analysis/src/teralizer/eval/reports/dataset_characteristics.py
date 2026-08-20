"""Dataset-characteristics report for the controlled and real-world corpora."""

from __future__ import annotations

import pandas as pd

from teralizer.eval.data import Required
from teralizer.eval.evidence import project_sources
from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec, ReportContext
from teralizer.eval.model import ColumnSpec, Metric, RQReport, Section, Table
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.formatting import (
    replace_project_names_with_macros,
    sort_dataframe_by_project,
)

REQUIRES = (Required("project", "table", ("id",)),)


def _table(df: pd.DataFrame) -> Table:
    result = df.copy()
    result["project"] = result["project"].astype(str)
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
    result = sort_dataframe_by_project(result, "project")
    result = replace_project_names_with_macros(result, "project")
    columns = [
        ColumnSpec("Project", "project"),
        ColumnSpec("Files", "main_files", "count", "r", group_header="Implementation"),
        ColumnSpec(
            "Classes", "main_classes", "count", "r", group_header="Implementation"
        ),
        ColumnSpec("SLOC", "main_sloc", "count", "r", group_header="Implementation"),
        ColumnSpec("Files", "test_files", "count", "r", group_header="Test"),
        ColumnSpec("Classes", "test_classes", "count", "r", group_header="Test"),
        ColumnSpec("SLOC", "test_sloc", "count", "r", group_header="Test"),
        ColumnSpec("Methods", "test_methods", "count", "r", group_header="Test"),
    ]
    return Table(
        "tab-dataset-statistics",
        result,
        columns,
        "Number of files, classes, source lines of code (SLOC), and test methods per project.",
        "tab:dataset-statistics",
        short_caption="Implementation and test-suite size per evaluation project",
        body_style="\\tabstyle",
        full_width=True,
        group_header_align="r",
        provenance=capture(project_sources.frame),
    )


def build(context: ReportContext) -> RQReport:
    context.corpus("controlled")
    context.corpus("real-world")
    facts_path = context.file("project-source-facts")
    if facts_path is None:
        raise AssertionError("required project-source facts resolved as absent")
    stats = project_sources.frame(facts_path)
    table = _table(stats)
    metrics = [
        Metric(
            "dataset.projects",
            int(len(stats)),
            "count",
            capture(project_sources.frame),
        )
    ]
    section = Section(
        "Dataset characteristics",
        [
            table,
        ],
    )
    return RQReport("dataset", "Dataset characteristics", [section], metrics)


register(
    "dataset",
    ReportSpec(
        build,
        (
            CorpusInputSpec("controlled", "controlled", REQUIRES),
            CorpusInputSpec("real-world", "real-world", REQUIRES),
            FileInputSpec(
                "project-source-facts",
                "analysis/data/report-inputs/project-source-facts.json",
            ),
        ),
    ),
)
