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
    row_end = chr(92) * 2  # LaTeX terminates a row with two backslashes
    assert tex == "\n".join(
        [
            "\\begin{table}",
            "  \\caption{Cap}",
            "  \\label{tab:funnel}",
            "  \\centering",
            "  \\begin{tabular}{lr}",
            "  \\toprule",
            "  Reason & Count " + row_end,
            "  \\midrule",
            "  A & 3,598 " + row_end,
            "  B & 12 " + row_end,
            "  \\bottomrule",
            "  \\end{tabular}",
            "\\end{table}",
        ]
    ) + "\n"


def test_render_table_stacks_headers_only_when_a_group_header_is_set():
    table = Table(
        key="stacked",
        df=pd.DataFrame({"reported": [1], "cut": [2], "jarvis": [3], "tool": [4]}),
        columns=[
            ColumnSpec("Reported case", "reported", "int"),
            ColumnSpec("CUT PVC", "cut", "int", group_header="Original"),
            ColumnSpec("PBT PVC", "jarvis", "int", group_header="JARVIS"),
            ColumnSpec("PBT PVC", "tool", "int", group_header="\\ToolTeralizer{}"),
        ],
        caption="Cap",
        label="tab:stacked",
    )
    lines = render_table(table).splitlines()
    row_end = chr(92) * 2  # LaTeX terminates a row with two backslashes
    assert "   & Original & JARVIS & \\ToolTeralizer{} " + row_end in lines
    assert "  Reported case & CUT PVC & PBT PVC & PBT PVC " + row_end in lines
    # Every group covers one column, so nothing spans and nothing is underlined.
    assert not any("multicolumn" in line for line in lines)
    assert not any("cmidrule" in line for line in lines)


def test_render_table_spans_and_rules_group_headers_shared_by_columns():
    table = Table(
        key="spanned",
        df=pd.DataFrame({"p": ["x"], "a": [1], "b": [2], "c": [3], "d": [4]}),
        columns=[
            ColumnSpec("Benchmark project", "p"),
            ColumnSpec("PBT PVC", "a", "int", "r", group_header="JARVIS"),
            ColumnSpec("MUTs", "b", "int", "r", group_header="JARVIS"),
            ColumnSpec("PBT PVC", "c", "int", "r", group_header="\\ToolTeralizer{}"),
            ColumnSpec("MUTs", "d", "int", "r", group_header="\\ToolTeralizer{}"),
        ],
        caption="Cap",
        label="tab:spanned",
    )
    lines = render_table(table).splitlines()
    row_end = chr(92) * 2
    assert (
        "   & \\multicolumn{2}{c}{JARVIS} & "
        "\\multicolumn{2}{c}{\\ToolTeralizer{}} " + row_end in lines
    )
    # The rule line sits between the two header rows and carries no row terminator.
    rules = "  \\cmidrule(lr){2-3} \\cmidrule(lr){4-5}"
    assert rules in lines
    assert lines.index(rules) == lines.index(
        "  Benchmark project & PBT PVC & MUTs & PBT PVC & MUTs " + row_end
    ) - 1


def test_render_table_label_rows_indent_members_and_flatten_empty_groups():
    table = Table(
        key="labels",
        df=pd.DataFrame(
            {
                "group": [
                    "CharUtilsTest",
                    "CharUtilsTest",
                    "IntervalTest",
                    "",
                    None,
                    float("nan"),
                ],
                "name": [
                    "isAscii",
                    "isPrintable",
                    "contains",
                    "flat-empty",
                    "flat-null",
                    "flat-nan",
                ],
                "count": [6, 195, 2, 0, 1, 2],
            }
        ),
        columns=[
            ColumnSpec("Name", "name"),
            ColumnSpec("Count", "count", "int", align="r"),
        ],
        caption="Cap",
        label="tab:labels",
        group_by="group",
        group_style="label-row",
    )
    row_end = chr(92) * 2  # LaTeX terminates a row with two backslashes
    lines = render_table(table).splitlines()
    assert "  CharUtilsTest " + row_end in lines
    assert "  \\quad isAscii & 6 " + row_end in lines
    assert "  \\quad isPrintable & 195 " + row_end in lines
    assert "  IntervalTest " + row_end in lines
    assert "  \\quad contains & 2 " + row_end in lines
    assert "  flat-empty & 0 " + row_end in lines
    assert "  flat-null & 1 " + row_end in lines
    assert "  flat-nan & 2 " + row_end in lines
    assert all("flat-empty" not in line or "quad" not in line for line in lines)
    assert all("flat-null" not in line or "quad" not in line for line in lines)
    assert all("flat-nan" not in line or "quad" not in line for line in lines)


def test_render_table_midrule_group_style_preserves_existing_group_breaks():
    table = Table(
        key="groups",
        df=pd.DataFrame({"group": ["A", "A", "B"], "value": [1, 2, 3]}),
        columns=[ColumnSpec("Value", "value", "int")],
        caption="Cap",
        label="tab:groups",
        group_by="group",
        group_style="midrule",
    )
    row_end = chr(92) * 2  # LaTeX terminates a row with two backslashes
    lines = render_table(table).splitlines()
    assert lines.count("  \\midrule") == 2
    assert "  1 " + row_end in lines
    assert "  2 " + row_end in lines
    assert "  3 " + row_end in lines
    assert not any("A " + row_end in line or "B " + row_end in line for line in lines)


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
