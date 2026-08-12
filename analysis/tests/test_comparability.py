from teralizer.comparability import BLOCK, WARN, FUNNEL_TABLES, RunMetadata, compare


def run(name, *, tool="c58fcd3a", extra_columns=(), projects=1161):
    columns = {table: frozenset({"id", "project_id"}) for table in FUNNEL_TABLES}
    if extra_columns:
        table, *names = extra_columns
        columns[table] = columns[table] | frozenset(names)
    return RunMetadata(
        name=name,
        tool_versions=frozenset({tool}),
        columns=columns,
        project_count=projects,
    )


def test_identical_runs_are_comparable():
    report = compare(run("v6"), run("v7"))

    assert report.comparable
    assert report.findings == ()


def test_a_column_present_in_only_one_run_blocks_comparison():
    # The case that produced a day of wrong conclusions: v7 records widening_refusal_code and
    # v6 has no such column, so the two runs classify the same generalization differently.
    report = compare(
        run("v6"),
        run("v7", extra_columns=("generalization", "widening_refusal_code")),
    )

    assert not report.comparable
    assert any(
        f.severity == BLOCK and "widening_refusal_code" in f.message
        for f in report.findings
    )
    assert any("generalization" in f.message for f in report.findings)


def test_a_different_tool_version_blocks_comparison():
    report = compare(run("v6", tool="aaaaaaa"), run("v7", tool="bbbbbbb"))

    assert not report.comparable
    assert any(
        f.severity == BLOCK and "tool version" in f.message for f in report.findings
    )


def test_a_different_project_count_warns_but_does_not_block():
    report = compare(run("v6", projects=1161), run("v7", projects=1048))

    assert report.comparable
    assert [f.severity for f in report.findings] == [WARN]
    assert "1048" in report.findings[0].message


def test_report_renders_the_verdict():
    assert "comparable" in compare(run("v6"), run("v7")).render()
    blocked = compare(run("v6"), run("v7", tool="other")).render()
    assert "NOT comparable" in blocked
