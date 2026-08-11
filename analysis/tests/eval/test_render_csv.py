import pandas as pd
from teralizer.eval.model import ColumnSpec, Table
from teralizer.eval.render.csv import render_table


def _table(columns: list[ColumnSpec]) -> Table:
    return Table(
        key="export",
        df=pd.DataFrame(
            {
                "qualified": ["CharUtilsTest::isAscii", "IntervalTest"],
                "short": ["isAscii", "IntervalTest"],
                "pvc": [6, 2],
            }
        ),
        columns=columns,
        caption="Cap",
        label="tab:export",
    )


def test_export_terminates_rows_with_a_bare_newline(tmp_path):
    path = render_table(
        _table([ColumnSpec("Case", "short"), ColumnSpec("PVC", "pvc", "int")]),
        tmp_path,
    )
    assert b"\r" not in path.read_bytes()


def test_export_defaults_to_the_rendered_source_column(tmp_path):
    path = render_table(
        _table([ColumnSpec("Case", "short"), ColumnSpec("PVC", "pvc", "int")]),
        tmp_path,
    )
    assert path.read_text(encoding="utf-8").splitlines()[:2] == [
        "short,pvc",
        "isAscii,6",
    ]


def test_csv_source_exports_the_qualified_value_latex_grouping_abbreviates(tmp_path):
    path = render_table(
        _table(
            [
                ColumnSpec("Case", "short", csv_source="qualified"),
                ColumnSpec("PVC", "pvc", "int"),
            ]
        ),
        tmp_path,
    )
    # Header and cells both follow csv_source, so the export stays self-describing.
    assert path.read_text(encoding="utf-8").splitlines() == [
        "qualified,pvc",
        "CharUtilsTest::isAscii,6",
        "IntervalTest,2",
    ]
