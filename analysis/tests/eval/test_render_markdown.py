from decimal import Decimal

import pandas as pd
import pytest
from teralizer.eval.inputs import CorpusInputSnapshot
from teralizer.eval.model import (
    BuiltReport,
    ColumnSpec,
    Metric,
    Prose,
    RQReport,
    Section,
    Table,
    ValueKind,
)
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.markdown import render_str


def _report():
    prov = Provenance(
        "teralizer.eval.reports.example",
        "build_report",
        50,
        None,
        "abc1234",
        "analysis/src/teralizer/eval/reports/example.py",
    )
    t = Table(
        key="x",
        df=pd.DataFrame({"reason": ["A"], "count": [3598], "delta": [Decimal("0.00")]}),
        columns=[
            ColumnSpec("Reason", "reason", ValueKind.TEXT),
            ColumnSpec("Count", "count", ValueKind.COUNT, align="r"),
            ColumnSpec(
                "Delta",
                "delta",
                ValueKind.DELTA,
                align="r",
                zero_is_absent=True,
            ),
        ],
        caption="Cap",
        label="tab:x",
        provenance=prov,
    )
    report = RQReport(
        "example",
        "Example Title",
        [Section("Overview", [Prose("Total {m.total}."), t])],
        metrics=[Metric("m.total", 3598, "count")],
    )
    return BuiltReport(
        report,
        (
            CorpusInputSnapshot(
                "controlled", "controlled", "postgres_dev", 13, 13, None, None
            ),
        ),
    )


def test_render_parenthesizes_delta_paired_by_a_merged_header():
    table = Table(
        key="paired",
        df=pd.DataFrame(
            {
                "absolute": [Decimal("54.98")],
                "delta": [Decimal("3.99")],
                "percent_delta": [Decimal("574.5")],
            }
        ),
        columns=[
            ColumnSpec("Detected", "absolute", ValueKind.DECIMAL, align="r"),
            ColumnSpec("Detected", "delta", ValueKind.DELTA, align="r"),
            ColumnSpec("Delta %", "percent_delta", ValueKind.PERCENT_DELTA, align="r"),
        ],
        caption="Paired values",
        label="tab:paired",
        merge_equal_headers=True,
    )
    report = RQReport("example", "Example", [Section("Data", [table])])
    built = BuiltReport(report, ())

    out = render_str(built, repo_url="https://example.invalid")

    assert "| 54.98 | (+3.99) | +574.5% |" in out


def test_render_rejects_any_backslash_in_output():
    report = RQReport(
        "example",
        "Example",
        [Section("Data", [Prose(r"Leaked \ToolPit{} macro.")])],
    )

    with pytest.raises(ValueError, match="rendered markdown contains a backslash"):
        render_str(BuiltReport(report, ()), repo_url="https://example.invalid")


def test_render_substitutes_metrics_and_renders_table_and_provenance():
    out = render_str(_report(), repo_url="https://github.com/glockyco/Teralizer")
    assert "# Example Title" in out
    assert "Total 3,598." in out
    assert "| Reason | Count | Delta |" in out
    assert "| A | 3,598 | — |" in out
    assert "blob/abc1234/analysis/src/teralizer/eval/reports/example.py#L50" in out
    assert "\\" not in out
