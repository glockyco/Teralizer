import pandas as pd
import pytest
from teralizer.eval.model import ColumnSpec, Metric, RQReport, Section, Table
from teralizer.eval.render.latex import render_macros, render_table, write_macros


def _report(rq: str, key: str) -> RQReport:
    return RQReport(
        rq, "T", "db", [Section("s", [])], metrics=[Metric(key, 1, "count")]
    )


def test_macros_from_every_report_survive_a_single_report_run(tmp_path):
    write_macros(_report("rq0", "jarvis.probes"), tmp_path)
    write_macros(_report("rq6", "realworld.eligible_projects"), tmp_path)
    write_macros(_report("rq6", "realworld.eligible_projects"), tmp_path)

    aggregate = (tmp_path / "macros.tex").read_text()
    assert "\\TzJarvisProbes" in aggregate
    assert "\\TzRealworldEligibleProjects" in aggregate
    assert aggregate.count("\\newcommand") == 2
    assert "Reports included: rq0, rq6" in aggregate


def test_two_reports_cannot_own_the_same_macro(tmp_path):
    write_macros(_report("rq0", "shared.count"), tmp_path)
    with pytest.raises(RuntimeError, match=r"rq0 and rq6"):
        write_macros(_report("rq6", "shared.count"), tmp_path)


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


def test_render_table_can_fit_text_width():
    table = Table(
        key="wide",
        df=pd.DataFrame({"value": [1]}),
        columns=[ColumnSpec("Value", "value", "count", align="r")],
        caption="Wide",
        label="tab:wide",
        latex_resize_to_width=True,
    )
    tex = render_table(table)
    assert "\\resizebox{\\textwidth}{!}{%" in tex
    assert "  \\end{tabular}\n  }\n\\end{table}" in tex


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
