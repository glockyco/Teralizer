import sqlite3

import pandas as pd

from teralizer.eval.evidence.jarvis_values import value_identity
from teralizer.eval.render.csv import render_table
from teralizer.eval.model import ColumnSpec, Table
from teralizer.eval.reports.rq0_jarvis import (
    TABLE1_PROJECTS,
    _build_breadth_table,
    _build_budget_table,
    _census_status_ledger,
    _scenario_number,
    _table2_mut_counts,
)
from teralizer.jarvis_scoreboard import (
    compare_to_jarvis,
    get_census_by_mut,
    get_census_project_pvc,
    suite_union_pvc,
)


def test_table2_schema_keeps_historical_references_and_unavailable_rows():
    scoreboard = pd.DataFrame(
        {
            "variant": ["IMPROVED"],
            "generated_method_name": ["isAscii"],
            "parameter_value_coverage": [198],
        }
    )
    comparison = compare_to_jarvis(scoreboard, variant="IMPROVED")
    assert list(comparison.columns) == [
        "table_row",
        "parameter_space",
        "probe_count",
        "teralizer_pvc",
        "original_cut_pvc",
        "jarvis_pbt_pvc",
        "pvc_delta",
        "pbt_cut_multiplier",
    ]
    row = comparison.set_index("table_row")
    assert row.loc["CharUtilsTest::isAscii", "teralizer_pvc"] == 198
    assert row.loc["CharUtilsTest::isAscii", "pvc_delta"] == 139
    assert pd.isna(row.loc["PrecisionTest", "teralizer_pvc"])
    assert pd.isna(row.loc["PrecisionTest", "pvc_delta"])


def test_table2_sound_mut_count_does_not_merge_min_max_or_polynomial_scenarios():
    comparison = pd.DataFrame(
        {
            "table_row": [
                "CharUtilsTest::isAscii",
                "CharUtilsTest::isPrintable",
                "FastMathTest::testMinMaxDouble",
                "FastMathTest::toIntExact",
                "IntervalTest",
                "PolynomialFunctionTest::testConstants",
                "PrecisionTest",
                "UnivariateFunctionTest::testAbs",
            ],
            "probe_count": [1, 1, 2, 1, 1, 1, 0, 1],
        }
    )
    rows, muts = _table2_mut_counts(comparison)
    assert rows == 7
    assert muts == 8


def _create_census_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE project (id INTEGER PRIMARY KEY, root_path TEXT NOT NULL);
        CREATE TABLE test (id INTEGER PRIMARY KEY, project_id INTEGER, test_class_name TEXT NOT NULL);
        CREATE TABLE assertion (
            id INTEGER PRIMARY KEY, project_id INTEGER, test_id INTEGER,
            tested_class_qualified_name TEXT, tested_method_name TEXT,
            tested_method_parameters TEXT, tested_method_call_arguments TEXT,
            assertion_name TEXT
        );
        CREATE TABLE generalization (
            id INTEGER PRIMARY KEY, project_id INTEGER, assertion_id INTEGER,
            variant TEXT, class_name TEXT, method_name TEXT, is_included BOOLEAN
        );
        CREATE TABLE junit_test_report (
            id INTEGER PRIMARY KEY, project_id INTEGER, generalization_id INTEGER,
            stage TEXT, variant TEXT, result TEXT, failure_type TEXT,
            failure_message TEXT
        );
        CREATE TABLE jqwik_property_execution (
            id INTEGER PRIMARY KEY, project_id INTEGER, generalization_id INTEGER,
            junit_test_report_id INTEGER, diagnostic_kind TEXT,
            distinct_new_tuples INTEGER, selected_value_log_path TEXT
        );
        """
    )


def test_census_status_ledger_keeps_failed_and_unreached_projects():
    conn = sqlite3.connect(":memory:")
    try:
        conn.executescript(
            """
            CREATE TABLE project (id INTEGER PRIMARY KEY, root_path TEXT NOT NULL);
            CREATE TABLE task (
                project_id INTEGER, stage TEXT, variant TEXT, status TEXT
            );
            """
        )
        conn.executemany(
            "INSERT INTO project VALUES (?, ?)",
            [
                (index + 1, f"data/{project}")
                for index, project in enumerate(TABLE1_PROJECTS)
            ],
        )
        required = [
            "EXECUTE_TESTS_ORIGINAL",
            "COLLECT_JUNIT_REPORTS_ORIGINAL",
            "GENERALIZE_TESTS",
            "EXECUTE_TESTS_GENERALIZED",
            "COLLECT_JUNIT_REPORTS_GENERALIZED",
        ]
        conn.executemany(
            "INSERT INTO task VALUES (1, ?, ?, ?)",
            [
                (
                    stage,
                    "IMPROVED_100_TRIES" if stage == "GENERALIZE_TESTS" else None,
                    "SUCCEEDED",
                )
                for stage in required
            ],
        )
        conn.execute(
            "INSERT INTO task VALUES (2, 'EXECUTE_TESTS_ORIGINAL', NULL, 'FAILED')"
        )
        conn.executemany(
            "INSERT INTO task VALUES (3, ?, NULL, 'SUCCEEDED')",
            [(stage,) for stage in required[:2]],
        )
        conn.commit()
        ledger, status, marker_present = _census_status_ledger(
            conn, marker_present=True
        )
        by_project = ledger.set_index("project")
        assert marker_present is True
        assert status == "partial"
        assert by_project.loc[TABLE1_PROJECTS[0], "generalization_status"] == "complete"
        assert by_project.loc[TABLE1_PROJECTS[1], "generalization_status"] == "failed"
        assert (
            by_project.loc[TABLE1_PROJECTS[2], "generalization_status"] == "not_reached"
        )
        assert (
            by_project.loc[TABLE1_PROJECTS[1], "first_failed_stage"]
            == "EXECUTE_TESTS_ORIGINAL"
        )
        assert by_project.loc[TABLE1_PROJECTS[1], "failed_task_count"] == 1
    finally:
        conn.close()


def test_census_project_pvc_unions_duplicate_inputs_by_mut(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        _create_census_schema(conn)
        conn.execute(
            "INSERT INTO project VALUES (1, ?)",
            ("data/fixtures/commons-lang-2017-02-01-census",),
        )
        conn.executemany(
            "INSERT INTO test VALUES (?, 1, ?)",
            [(1, "FirstTest"), (2, "SecondTest")],
        )
        params = '[{"type":"int","name":"x"}]'
        conn.executemany(
            "INSERT INTO assertion (id, project_id, test_id, tested_class_qualified_name, tested_method_name, tested_method_parameters, tested_method_call_arguments, assertion_name) VALUES (?, 1, ?, 'example.Subject', 'value', ?, NULL, 'assertTrue')",
            [(1, 1, params), (2, 2, params)],
        )
        conn.executemany(
            "INSERT INTO generalization VALUES (?, 1, ?, 'IMPROVED_100_TRIES', 'Generated', 'value', 1)",
            [(1, 1), (2, 2)],
        )
        conn.executemany(
            "INSERT INTO junit_test_report VALUES (?, 1, ?, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'IMPROVED_100_TRIES', 'PASSED', NULL, NULL)",
            [(1, 1), (2, 2)],
        )
        log1 = tmp_path / "one.tsv"
        log2 = tmp_path / "two.tsv"
        log1.write_text("x=1\nx=2\n", encoding="utf-8")
        log2.write_text("x=2\nx=3\n", encoding="utf-8")
        conn.executemany(
            "INSERT INTO jqwik_property_execution VALUES (?, 1, ?, ?, 'FULL', 0, ?)",
            [(1, 1, 1, str(log1)), (2, 2, 2, str(log2))],
        )
        conn.commit()

        by_mut = get_census_by_mut(conn, variants=["IMPROVED_100_TRIES"])
        project_pvc = get_census_project_pvc(
            conn, variants=["IMPROVED_100_TRIES"]
        ).set_index("project")

        assert by_mut.loc[0, "sound_properties"] == 2
        assert by_mut.loc[0, "all_property_executions"] == 2
        assert by_mut.loc[0, "source_test_classes"] == 2
        assert project_pvc.loc["commons-lang-2017-02-01-census", "aggregate_pvc"] == 3
        assert project_pvc.loc["commons-lang-2017-02-01-census", "sound_muts"] == 1
    finally:
        conn.close()


def test_breadth_keeps_available_pvc_when_task_status_failed():
    ledger = pd.DataFrame(
        {
            "project": TABLE1_PROJECTS,
            "generalization_status": [
                "complete",
                "failed",
                *(["not_reached"] * 10),
            ],
        }
    )
    project_pvc = pd.DataFrame(
        {
            "project": [
                "commons-math-2017-02-01-census",
                "commons-lang-2017-02-01-census",
            ],
            "variant": ["IMPROVED_100_TRIES", "IMPROVED_100_TRIES"],
            "aggregate_pvc": [42, 84],
            "sound_muts": [3, 5],
            "sound_properties": [4, 6],
        }
    )
    breadth = _build_breadth_table(ledger, project_pvc).set_index("project")
    assert breadth.loc["commons-lang-2017-02-01-census", "aggregate_pvc"] == 84
    assert breadth.loc["commons-lang-2017-02-01-census", "sound_muts"] == 5
    assert breadth.loc["commons-lang-2017-02-01-census", "sound_properties"] == 6
    assert pd.isna(breadth.loc["commons-cli-2017-02-01-census", "aggregate_pvc"])


def test_breadth_leaves_jarvis_unreported_projects_blank_not_zero():
    ledger = pd.DataFrame(
        {
            "project": TABLE1_PROJECTS,
            "generalization_status": ["complete"] * 12,
        }
    )
    project_pvc = pd.DataFrame(
        {
            "project": ["commons-math-2017-02-01-census"],
            "variant": ["IMPROVED_100_TRIES"],
            "aggregate_pvc": [42],
            "sound_muts": [3],
            "sound_properties": [4],
        }
    )
    breadth = _build_breadth_table(ledger, project_pvc).set_index("project")

    # JARVIS reports cases for two projects only. Zero would claim it measured
    # nothing there, when it published nothing at all.
    assert breadth.loc["commons-math-2017-02-01-census", "jarvis_successful_muts"] == 7
    assert pd.isna(
        breadth.loc["commons-cli-2017-02-01-census", "jarvis_successful_muts"]
    )
    assert pd.isna(
        breadth.loc["commons-pool-2017-02-01-census", "jarvis_successful_pbt_pvc"]
    )

    # The total covers what JARVIS did report, so it is unchanged by the blanks.
    total = breadth.loc["all 12 projects"]
    assert total["jarvis_successful_pbt_pvc"] == 1708
    assert total["jarvis_successful_muts"] == 9


def test_budget_table_marks_missing_pit_variants_unavailable():
    scoreboard = pd.DataFrame(
        {
            "variant": ["IMPROVED_100_TRIES", "IMPROVED_200_TRIES"],
            "parameter_value_coverage": [100, 200],
        }
    )
    mutation = pd.DataFrame(
        {
            "variant": ["IMPROVED_100_TRIES"],
            "killed_mutants": [5],
            "covered_mutants": [10],
            "total_mutants": [20],
        }
    )
    budget = _build_budget_table(scoreboard, mutation).set_index("variant")
    assert budget.loc["IMPROVED_100_TRIES", "killed_mutants"] == 5
    assert pd.isna(budget.loc["IMPROVED_200_TRIES", "killed_mutants"])
    assert pd.isna(budget.loc["IMPROVED_200_TRIES", "covered_mutation_score"])
    # PVC comes from the scoreboard, so it survives a variant that PIT never ran.
    assert budget.loc["IMPROVED_200_TRIES", "total_pvc"] == 200


def test_suite_union_joins_captured_and_generated_values():
    scoreboard = pd.DataFrame(
        {
            "variant": ["IMPROVED_100_TRIES", "IMPROVED_100_TRIES"],
            "generated_method_name": ["isAscii", "absValue"],
            "tested_method_parameters": [
                '[{"type":"char","name":"ch"}]',
                '[{"type":"double","name":"x"}]',
            ],
            "parameter_values": [
                [
                    {"parameter": "ch", "value_base64": value_identity("a")},
                    {"parameter": "ch", "value_base64": value_identity("b")},
                ],
                [
                    {"parameter": "x", "value_base64": value_identity("1.0")},
                    {"parameter": "x", "value_base64": value_identity("2.0")},
                ],
            ],
        }
    )
    cut_values = pd.DataFrame(
        {
            "table_row": [
                "CharUtilsTest::isAscii",
                "CharUtilsTest::isAscii",
                "PrecisionTest",
            ],
            "mut_class": [
                "org.apache.commons.lang3.CharUtils",
                "org.apache.commons.lang3.CharUtils",
                "org.apache.commons.math4.util.Precision",
            ],
            "mut_method": ["isAscii", "isAscii", "equals"],
            "parameter": ["p0", "p0", "p0"],
            "value": ["a", "z", "1.0"],
        }
    )
    suite = suite_union_pvc(
        scoreboard, cut_values, variant="IMPROVED_100_TRIES"
    ).set_index("table_row")
    # Captured {a, z} unions with generated {a, b}: the seed overlap counts once.
    assert suite.loc["CharUtilsTest::isAscii", "suite_pvc"] == 3
    assert suite.loc["CharUtilsTest::isAscii", "measured_cut_pvc"] == 2
    # Captured values without a generalized test stay a dash, never a number.
    assert suite.loc["PrecisionTest", "measured_cut_pvc"] == 1
    assert pd.isna(suite.loc["PrecisionTest", "suite_pvc"])
    # A generalized test without captured original values keeps its log values
    # (the log already contains the seed via FirstValueArbitrary).
    assert suite.loc["UnivariateFunctionTest::testAbs", "suite_pvc"] == 2
    assert pd.isna(suite.loc["UnivariateFunctionTest::testAbs", "measured_cut_pvc"])
    # Invariant: coverage after generalization never falls below the captured
    # original coverage.
    both = suite.dropna(subset=["suite_pvc", "measured_cut_pvc"])
    assert (both["suite_pvc"] >= both["measured_cut_pvc"]).all()


def test_suite_union_without_capture_dataset_keeps_generated_rows():
    scoreboard = pd.DataFrame(
        {
            "variant": ["IMPROVED_100_TRIES"],
            "generated_method_name": ["isAscii"],
            "tested_method_parameters": ['[{"type":"char","name":"ch"}]'],
            "parameter_values": [
                [{"parameter": "ch", "value_base64": value_identity("a")}]
            ],
        }
    )
    empty = pd.DataFrame(
        columns=["table_row", "mut_class", "mut_method", "parameter", "value"]
    )
    suite = suite_union_pvc(scoreboard, empty).set_index("table_row")
    assert suite.loc["CharUtilsTest::isAscii", "suite_pvc"] == 1
    assert pd.isna(suite.loc["PrecisionTest", "suite_pvc"])


def test_csv_headers_follow_table_source_order(tmp_path):
    table = Table(
        key="rq0-table2-comparison",
        df=pd.DataFrame(
            {
                "table_row": ["case"],
                "original_cut_pvc": [6],
                "jarvis_pbt_pvc": [59],
                "suite_pvc": [227],
            }
        ),
        columns=[
            ColumnSpec("Reported case", "table_row"),
            ColumnSpec("Original CUT PVC", "original_cut_pvc", "count"),
            ColumnSpec("JARVIS PBT PVC", "jarvis_pbt_pvc", "count"),
            ColumnSpec("Teralizer PVC", "suite_pvc", "pvc"),
        ],
        caption="caption",
        label="tab:label",
    )
    path = render_table(table, tmp_path)
    assert path.read_text(encoding="utf-8").splitlines()[0] == (
        "table_row,original_cut_pvc,jarvis_pbt_pvc,suite_pvc"
    )


def test_scenario_numbers_follow_stable_table_order():
    assert _scenario_number("CharUtilsTest::isAscii") == 1
    assert _scenario_number("UnivariateFunctionTest::testAbs") == 10
