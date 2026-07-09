import pandas as pd
from teralizer.eval.model import ColumnSpec, Metric, Prose, RQReport, Section, Table
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.markdown import render_str


def _report():
    prov = Provenance(
        "teralizer.eval.reports.example", "build_report", 50, None, "abc1234"
    )
    t = Table(
        key="x",
        df=pd.DataFrame({"reason": ["A"], "count": [3598]}),
        columns=[
            ColumnSpec("Reason", "reason", "str"),
            ColumnSpec("Count", "count", "count", align="r"),
        ],
        caption="Cap",
        label="tab:x",
        provenance=prov,
    )
    return RQReport(
        "example",
        "Example Title",
        "postgres_dev",
        [Section("Overview", [Prose("Total {m.total}."), t])],
        metrics=[Metric("m.total", 3598, "count")],
    )


def test_render_substitutes_metrics_and_renders_table_and_provenance():
    out = render_str(_report(), repo_url="https://github.com/glockyco/Teralizer")
    assert "# Example Title" in out
    assert "Total 3,598." in out
    assert "| Reason | Count |" in out
    assert "| A | 3,598 |" in out
    assert "blob/abc1234/analysis/src/teralizer/eval/reports/example.py#L50" in out
