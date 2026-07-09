import pandas as pd
from teralizer.eval.model import ColumnSpec, Metric, RQReport, Section, Table
from teralizer.eval.render.latex import render_macros, render_table


def test_render_table_is_booktabs_with_formatted_cells():
    t = Table(
        key="funnel",
        df=pd.DataFrame({"reason": ["A", "B"], "count": [3598, 12]}),
        columns=[
            ColumnSpec("Reason", "reason", "str", align="l"),
            ColumnSpec("Count", "count", "count", align="r"),
        ],
        caption="Cap",
        label="tab:funnel",
    )
    tex = render_table(t)
    assert "\\begin{tabular}{lr}" in tex
    assert "\\toprule" in tex and "\\bottomrule" in tex
    assert "Reason & Count \\\\" in tex
    assert "A & 3,598 \\\\" in tex
    assert "\\label{tab:funnel}" in tex


def test_render_macros_one_newcommand_per_metric():
    report = RQReport(
        "rq6",
        "T",
        "db",
        [Section("s", [])],
        metrics=[Metric("realworld.eligible_projects_pct", 0.794, "pct1")],
    )
    tex = render_macros(report)
    assert "\\newcommand{\\TzRealworldEligibleProjectsPct}{79.4\\%}" in tex
