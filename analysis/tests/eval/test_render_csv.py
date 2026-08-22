import csv
from decimal import Decimal

import pandas as pd

from teralizer.eval.entities import ref_for_csv
from teralizer.eval.model import ColumnSpec, Table, ValueKind
from teralizer.eval.render.csv import render_table


def _table(columns: list[ColumnSpec]) -> Table:
    return Table(
        key="export",
        df=pd.DataFrame(
            {
                "case": [
                    ref_for_csv("scenario", "CharUtilsTest::isAscii"),
                    ref_for_csv("scenario", "IntervalTest"),
                ],
                "pvc": [6, 2],
                "share": [Decimal("0.12"), None],
            }
        ),
        columns=columns,
        caption="Cap",
        label="tab:export",
    )


def test_export_terminates_rows_with_a_bare_newline(tmp_path):
    path = render_table(
        _table(
            [
                ColumnSpec("Case", "case", ValueKind.ENTITY),
                ColumnSpec("PVC", "pvc", ValueKind.COUNT),
            ]
        ),
        tmp_path,
    )
    assert b"\r" not in path.read_bytes()


def test_export_uses_stable_semantic_source_names(tmp_path):
    path = render_table(
        _table(
            [
                ColumnSpec("Case", "case", ValueKind.ENTITY),
                ColumnSpec("PVC", "pvc", ValueKind.COUNT),
            ]
        ),
        tmp_path,
    )
    assert path.read_text(encoding="utf-8").splitlines()[:2] == [
        "case,pvc",
        "CharUtilsTest::isAscii,6",
    ]


def test_export_keeps_every_numeric_kind_machine_readable(tmp_path):
    numeric_sources = [
        "count",
        "share",
        "percent",
        "percent_delta",
        "decimal",
        "delta",
        "runtime",
    ]
    table = Table(
        key="numeric",
        df=pd.DataFrame(
            {
                "count": [3598, None],
                "share": [Decimal("0.12"), None],
                "percent": [Decimal("47"), None],
                "percent_delta": [Decimal("574.5"), None],
                "decimal": [Decimal("59.10"), None],
                "delta": [Decimal("-3.99"), None],
                "runtime": [Decimal("3601"), None],
            }
        ),
        columns=[
            ColumnSpec(source.title(), source, ValueKind(source))
            for source in numeric_sources
        ],
        caption="Numeric",
        label="tab:numeric",
    )

    path = render_table(table, tmp_path)
    rows = list(csv.DictReader(path.open(encoding="utf-8", newline="")))

    for source in numeric_sources:
        assert Decimal(rows[0][source]).is_finite()
        assert rows[1][source] == ""
    assert not any(
        token in path.read_text(encoding="utf-8") for token in ("%", "—", "--")
    )
    assert rows[0]["count"] == "3598"


def test_export_keeps_numeric_values_bare_and_absence_empty(tmp_path):
    path = render_table(
        _table(
            [
                ColumnSpec("Case", "case", ValueKind.ENTITY),
                ColumnSpec("Share", "share", ValueKind.SHARE),
            ]
        ),
        tmp_path,
    )
    assert path.read_text(encoding="utf-8").splitlines() == [
        "case,share",
        "CharUtilsTest::isAscii,0.12",
        "IntervalTest,",
    ]
