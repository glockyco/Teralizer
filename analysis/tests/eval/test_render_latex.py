from decimal import Decimal

import pandas as pd
import pytest
from teralizer.eval.entities import variant_ref
from teralizer.eval.inputs import CorpusInputSnapshot
from teralizer.eval.model import (
    BandSummary,
    BuiltReport,
    ColumnSpec,
    Metric,
    RQReport,
    Section,
    Table,
    ValueKind,
)
from teralizer.eval.render.latex import (
    render_macros,
    render_table,
    write_aggregate_macros,
    write_owned_macros,
)


def _report(rq: str, key: str) -> BuiltReport:
    report = RQReport(rq, "T", [Section("s", [])], metrics=[Metric(key, 1, "count")])
    return BuiltReport(
        report,
        (CorpusInputSnapshot("controlled", "controlled", "db", 1, 1, None, None),),
    )


def test_macros_from_every_report_survive_a_single_report_run(tmp_path):
    write_owned_macros(_report("rq0", "jarvis.probes"), tmp_path)
    write_owned_macros(_report("rq6", "realworld.eligible_projects"), tmp_path)
    write_owned_macros(_report("rq6", "realworld.eligible_projects"), tmp_path)
    write_aggregate_macros(tmp_path)

    aggregate = (tmp_path / "macros.tex").read_text()
    assert "\\TzJarvisProbes" in aggregate
    assert "\\TzRealworldEligibleProjects" in aggregate
    assert aggregate.count("\\newcommand") == 2
    assert "Reports included: rq0, rq6" in aggregate


def test_two_reports_cannot_own_the_same_macro(tmp_path):
    write_owned_macros(_report("rq0", "shared.count"), tmp_path)
    write_owned_macros(_report("rq6", "shared.count"), tmp_path)
    with pytest.raises(RuntimeError, match=r"rq0 and rq6"):
        write_aggregate_macros(tmp_path)


def test_render_table_is_booktabs_with_formatted_cells():
    t = Table(
        key="funnel",
        df=pd.DataFrame({"reason": ["A", "B"], "count": [3598, 12]}),
        columns=[
            ColumnSpec("Reason", "reason", ValueKind.TEXT, align="l"),
            ColumnSpec("Count", "count", ValueKind.COUNT, align="r"),
        ],
        caption="Cap",
        label="tab:funnel",
    )
    tex = render_table(t)
    row_end = chr(92) * 2  # LaTeX terminates a row with two backslashes
    assert (
        tex
        == "\n".join(
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
        )
        + "\n"
    )


def test_full_width_band_distributes_fields_within_table_width():
    table = Table(
        key="funnel",
        df=pd.DataFrame({"stage": ["1"], "count": [1]}),
        columns=[
            ColumnSpec("Stage", "stage", ValueKind.TEXT, align="l"),
            ColumnSpec("Count", "count", ValueKind.COUNT, align="r"),
        ],
        caption="Funnel",
        label="tab:funnel",
        group_by="stage",
        bands={"1": BandSummary("Stage 1 + 2 - Project Analysis", 584, 293, 291)},
        full_width=True,
    )

    tex = render_table(table)

    assert "\\multicolumn{2}{@{}l@{}}" in tex
    assert "\\makebox[\\textwidth][l]" in tex
    assert tex.count("\\hfill{}") == 4
    assert "\\enspace{}" not in tex


def test_render_table_labels_semantic_rows_independently_of_order():
    table = Table(
        key="mechanisms",
        df=pd.DataFrame(
            {
                "row_key": ["test.included", "stage 1:filter rejection"],
                "cause": ["Included", "Filter rejection"],
            }
        ),
        columns=[ColumnSpec("Cause", "cause", ValueKind.TEXT, align="l")],
        caption="Mechanisms",
        label="tab:mechanisms",
        row_key="row_key",
        ordinal_header="#",
    )

    lines = render_table(table).splitlines()

    assert (
        "  \\renewcommand{\\theHreporttablerow}"
        "{mechanisms.\\arabic{reporttablerow}}" in lines
    )
    assert "  \\setcounter{reporttablerow}{0}" in lines
    assert (
        "  \\refstepcounter{reporttablerow}"
        "\\label{tabrow:mechanisms:test.included}"
        "\\thereporttablerow & Included \\\\" in lines
    )
    assert (
        "  \\refstepcounter{reporttablerow}"
        "\\label{tabrow:mechanisms:stage@201@3Afilter@20rejection}"
        "\\thereporttablerow & Filter rejection \\\\" in lines
    )


def test_render_table_formats_delta_sign_grouping_and_absent_zero():
    table = Table(
        key="deltas",
        df=pd.DataFrame({"delta": [Decimal("10653"), Decimal("0.00")]}),
        columns=[
            ColumnSpec(
                "Delta",
                "delta",
                ValueKind.DELTA,
                zero_is_absent=True,
            )
        ],
        caption="Deltas",
        label="tab:deltas",
    )

    tex = render_table(table)

    assert "+10,653" in tex
    assert "  -- \\" in tex


def test_render_table_stacks_headers_only_when_a_group_header_is_set():
    table = Table(
        key="stacked",
        df=pd.DataFrame({"reported": [1], "cut": [2], "jarvis": [3], "tool": [4]}),
        columns=[
            ColumnSpec("Reported case", "reported", ValueKind.COUNT),
            ColumnSpec("CUT PVC", "cut", ValueKind.COUNT, group_header="Original"),
            ColumnSpec("PBT PVC", "jarvis", ValueKind.COUNT, group_header="JARVIS"),
            ColumnSpec(
                "PBT PVC",
                "tool",
                ValueKind.COUNT,
                group_header="{entity.tool.teralizer}",
            ),
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


def test_render_table_emits_the_consuming_documents_house_style():
    table = Table(
        key="styled",
        df=pd.DataFrame({"p": ["x"], "a": [1], "b": [2]}),
        columns=[
            ColumnSpec("Project", "p"),
            ColumnSpec("Files", "a", ValueKind.COUNT, "r", group_header="Impl"),
            ColumnSpec("SLOC", "b", ValueKind.COUNT, "r", group_header="Impl"),
        ],
        caption="Long caption.",
        label="tab:styled",
        short_caption="Short caption",
        body_style="\\tabstyle",
        float_spec="H",
        full_width=True,
        group_header_align="r",
    )
    lines = render_table(table).splitlines()
    assert lines[0] == "\\begin{table}[H]"
    # The trailing comment keeps a stray space out of the float.
    assert lines[1] == "  \\caption[Short caption]{Long caption.}%"
    assert lines[3] == "  \\tabstyle"
    assert lines[4] == (
        "  \\begin{tabular*}{\\textwidth}"
        "{@{\\hspace{\\tabcolsep}\\extracolsep{\\fill}}lrr}"
    )
    assert "  \\end{tabular*}" in lines
    assert any("\\multicolumn{2}{r}{Impl}" in line for line in lines)


def test_equal_leaf_headers_span_only_when_the_table_asks():
    columns = [
        ColumnSpec("Mutator", "m"),
        ColumnSpec("Naive", "a", ValueKind.COUNT, "r"),
        ColumnSpec("Naive", "b", ValueKind.COUNT, "r"),
    ]
    df = pd.DataFrame({"m": ["Math"], "a": [1], "b": [2]})

    merged = render_table(
        Table(
            key="m",
            df=df,
            columns=columns,
            caption="Cap",
            label="tab:m",
            merge_equal_headers=True,
        )
    )
    assert "  Mutator & \\multicolumn{2}{c}{Naive} \\\\" in merged.splitlines()
    # The pair is already ruled by the group row above, so no rule is added here.
    assert "cmidrule" not in merged

    # Default: two columns may share a header without one label covering both.
    plain = render_table(
        Table(key="m", df=df, columns=columns, caption="Cap", label="tab:m")
    )
    assert "  Mutator & Naive & Naive \\\\" in plain.splitlines()


def test_equal_leaf_headers_parenthesize_their_paired_delta():
    table = Table(
        key="deltas",
        df=pd.DataFrame(
            {
                "absolute": [Decimal("54.98"), Decimal("50.00")],
                "delta": [Decimal("3.99"), Decimal("0.00")],
            }
        ),
        columns=[
            ColumnSpec("Detected", "absolute", ValueKind.DECIMAL, "r"),
            ColumnSpec(
                "Detected",
                "delta",
                ValueKind.DELTA,
                "r",
                zero_is_absent=True,
            ),
        ],
        caption="Cap",
        label="tab:deltas",
        merge_equal_headers=True,
    )

    tex = render_table(table)

    assert "  54.98 & (+3.99) \\\\" in tex.splitlines()
    assert "  50.00 & -- \\\\" in tex.splitlines()


def test_entity_cells_render_latex_while_plain_text_stays_escaped():
    table = Table(
        key="entity",
        df=pd.DataFrame({"v": [variant_ref("IMPROVED_50_TRIES")], "p": ["a_b 50%"]}),
        columns=[
            ColumnSpec("Variant", "v", ValueKind.ENTITY),
            ColumnSpec("Plain", "p"),
        ],
        caption="Cap",
        label="tab:entity",
    )
    body = [
        line for line in render_table(table).splitlines() if "VariantImprovedB" in line
    ]
    assert body == [r"  \VariantImprovedB{} & a\_b 50\% " + "\\\\"]


def test_render_table_captions_itself_when_the_document_owns_the_float():
    table = Table(
        key="sidebyside",
        df=pd.DataFrame({"a": [1]}),
        columns=[ColumnSpec("A", "a", ValueKind.COUNT)],
        caption="Pareto points.",
        label="tab:pareto",
        short_caption="Pareto",
        body_style="\\tabstyle[\\footnotesize]\n\\setlength{\\tabcolsep}{3pt}",
        floating=False,
    )
    lines = render_table(table).splitlines()
    # Nothing may open or close a float the surrounding document already opened.
    assert not any("begin{table}" in line or "end{table}" in line for line in lines)
    assert lines[0] == "\\captionof{table}[Pareto]{Pareto points.}%"
    assert lines[1] == "\\label{tab:pareto}"
    assert lines[2:4] == ["\\tabstyle[\\footnotesize]", "\\setlength{\\tabcolsep}{3pt}"]


def test_render_table_defaults_stay_on_the_plain_centred_float():
    table = Table(
        key="plain",
        df=pd.DataFrame({"a": [1]}),
        columns=[ColumnSpec("A", "a", ValueKind.COUNT)],
        caption="Cap",
        label="tab:plain",
    )
    lines = render_table(table).splitlines()
    assert lines[0] == "\\begin{table}"
    assert lines[1] == "  \\caption{Cap}"
    assert lines[3] == "  \\centering"
    assert lines[4] == "  \\begin{tabular}{l}"
    assert not any("tabular*" in line for line in lines)


def test_render_table_spans_and_rules_group_headers_shared_by_columns():
    table = Table(
        key="spanned",
        df=pd.DataFrame({"p": ["x"], "a": [1], "b": [2], "c": [3], "d": [4]}),
        columns=[
            ColumnSpec("Benchmark project", "p"),
            ColumnSpec("PBT PVC", "a", ValueKind.COUNT, "r", group_header="JARVIS"),
            ColumnSpec("MUTs", "b", ValueKind.COUNT, "r", group_header="JARVIS"),
            ColumnSpec(
                "PBT PVC",
                "c",
                ValueKind.COUNT,
                "r",
                group_header="{entity.tool.teralizer}",
            ),
            ColumnSpec(
                "MUTs",
                "d",
                ValueKind.COUNT,
                "r",
                group_header="{entity.tool.teralizer}",
            ),
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
    assert (
        lines.index(rules)
        == lines.index(
            "  Benchmark project & PBT PVC & MUTs & PBT PVC & MUTs " + row_end
        )
        - 1
    )


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
            ColumnSpec("Count", "count", ValueKind.COUNT, align="r"),
        ],
        caption="Cap",
        label="tab:labels",
        group_by="group",
        group_style="label-row",
    )
    row_end = chr(92) * 2  # LaTeX terminates a row with two backslashes
    lines = render_table(table).splitlines()
    assert "  CharUtilsTest " + row_end in lines
    assert "  \\qquad isAscii & 6 " + row_end in lines
    assert "  \\qquad isPrintable & 195 " + row_end in lines
    assert "  IntervalTest " + row_end in lines
    # Groups are separated by one \\addlinespace, and the first label row,
    # which follows the header midrule directly, gets none.
    assert lines.count("  \\addlinespace") == 1
    assert lines.index("  \\addlinespace") + 1 == lines.index(
        "  IntervalTest " + row_end
    )
    assert "  \\qquad contains & 2 " + row_end in lines
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
        columns=[ColumnSpec("Value", "value", ValueKind.COUNT)],
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
        columns=[ColumnSpec("Value", "value", ValueKind.COUNT, align="r")],
        caption="Wide",
        label="tab:wide",
        latex_resize_to_width=True,
    )
    tex = render_table(table)
    assert "\\noindent\\resizebox{\\linewidth}{!}{%" in tex
    assert "\\begin{tabular}{r}" in tex
    assert "tabular*" not in tex
    assert "  \\end{tabular}\n  }\n\\end{table}" in tex


def test_render_macros_one_newcommand_per_metric():
    report = RQReport(
        "rq6",
        "T",
        [Section("s", [])],
        metrics=[Metric("realworld.eligible_projects_pct", 0.794, "pct1")],
    )
    tex = render_macros(report)
    assert "\\newcommand{\\TzRealworldEligibleProjectsPct}{79.4\\%}" in tex
