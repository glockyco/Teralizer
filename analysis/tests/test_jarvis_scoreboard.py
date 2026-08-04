import sqlite3
from pathlib import Path
from typing import cast

import pandas as pd
import pytest

from teralizer.jarvis_scoreboard import (
    JARVIS_TABLE2,
    classify_generated_test_outcome,
    compare_to_jarvis,
    compute_parameter_value_coverage,
    get_generated_test_runs,
    get_instruction_coverage_scores,
    get_mutation_scores,
    get_pvc_scores,
    get_scoreboard,
    parse_jqwik_value_log,
    summarize_variants,
)


def test_parse_jqwik_value_log_and_compute_pvc(tmp_path):
    log_path = tmp_path / "7.NAIVE.tsv"
    log_path.write_text("x=1\ty=true\nx=1\ty=false\nx=2\ty=true\n")

    values = parse_jqwik_value_log(log_path)

    assert values.to_dict("records") == [
        {"trial_index": 0, "parameter_name": "x", "value": "1"},
        {"trial_index": 0, "parameter_name": "y", "value": "true"},
        {"trial_index": 1, "parameter_name": "x", "value": "1"},
        {"trial_index": 1, "parameter_name": "y", "value": "false"},
        {"trial_index": 2, "parameter_name": "x", "value": "2"},
        {"trial_index": 2, "parameter_name": "y", "value": "true"},
    ]

    coverage = compute_parameter_value_coverage(values)

    assert coverage.to_dict("records") == [
        {
            "parameter_name": "x",
            "generated_values": 3,
            "distinct_generated_values": 2,
        },
        {
            "parameter_name": "y",
            "generated_values": 3,
            "distinct_generated_values": 2,
        },
    ]


def test_parse_jqwik_value_log_unescapes_recorded_control_characters(tmp_path):
    log_path = tmp_path / "7.NAIVE.tsv"
    log_path.write_text("ch=\\u0000	tab=\\t	line=\\n	backslash=\\\\\n")

    values = parse_jqwik_value_log(log_path)

    assert values.to_dict("records") == [
        {"trial_index": 0, "parameter_name": "ch", "value": "\u0000"},
        {"trial_index": 0, "parameter_name": "tab", "value": "\t"},
        {"trial_index": 0, "parameter_name": "line", "value": "\n"},
        {"trial_index": 0, "parameter_name": "backslash", "value": "\\"},
    ]


def test_pvc_reads_selected_value_log_path(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        value_log_path = insert_scoreboard_fixture(conn, tmp_path)
        Path(value_log_path).parent.mkdir(parents=True, exist_ok=True)
        Path(value_log_path).write_text(
            "lower=1\tupper=10\tvalue=5\n"
            "lower=1\tupper=10\tvalue=6\n"
            "lower=0\tupper=10\tvalue=7\n"
        )

        pvc = get_pvc_scores(conn)
        ic = get_instruction_coverage_scores(conn)
        scoreboard = get_scoreboard(conn)

        assert cast(
            pd.DataFrame,
            pvc[
                [
                    "project_id",
                    "generalization_id",
                    "parameter_value_coverage",
                    "jqwik_trials",
                ]
            ],
        ).to_dict("records") == [
            {
                "project_id": 1,
                "generalization_id": 7,
                "parameter_value_coverage": 6,
                "jqwik_trials": 3,
            }
        ]
        assert pvc.loc[0, "original_parameter_value_count"] == 3
        assert cast(
            pd.DataFrame,
            ic[["project_id", "variant", "instruction_covered", "instruction_total"]],
        ).to_dict("records") == [
            {
                "project_id": 1,
                "variant": "NAIVE",
                "instruction_covered": 40,
                "instruction_total": 50,
            }
        ]
        assert scoreboard.loc[0, "instruction_coverage"] == pytest.approx(0.8)
        assert scoreboard.loc[0, "parameter_value_coverage"] == 6
    finally:
        conn.close()


def test_generated_runs_separate_precondition_rejections(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        insert_scoreboard_fixture(conn, tmp_path)
        conn.execute(
            """
            INSERT INTO assertion (
                id,
                project_id,
                assertion_name,
                tested_method_call_arguments,
                tested_class_qualified_name,
                tested_method_name
            ) VALUES (3, 1, 'assertTrue', ?, 'smoke.Subject', 'contains')
            """,
            (
                '[{"type":"int","value":"2"},{"type":"int","value":"8"},{"type":"int","value":"5"}]',
            ),
        )
        conn.execute(
            """
            INSERT INTO generalization (
                id, project_id, assertion_id, variant, class_name, method_name, is_included
            ) VALUES (8, 1, 3, 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 1)
            """
        )
        conn.execute(
            """
            INSERT INTO junit_test_report (
                project_id,
                generalization_id,
                stage,
                variant,
                test_class_name,
                test_method_name,
                result,
                failure_type,
                failure_message
            ) VALUES (
                1,
                8,
                'COLLECT_JUNIT_REPORTS_GENERALIZED',
                'NAIVE',
                '_SubjectTest_Generalized',
                'valueInsideInterval',
                'ERROR',
                'net.jqwik.api.TooManyFilterMissesException',
                'Filtering missed more than 10000 times.'
            )
            """
        )
        conn.commit()

        runs = get_generated_test_runs(conn, outcomes=None)

        assert cast(
            pd.DataFrame, runs[["generalization_id", "test_result", "outcome_class"]]
        ).to_dict("records") == [
            {
                "generalization_id": 7,
                "test_result": "PASSED",
                "outcome_class": "passed",
            },
            {
                "generalization_id": 8,
                "test_result": "ERROR",
                "outcome_class": "precondition_rejected",
            },
        ]
        assert (
            classify_generated_test_outcome(
                "FAILED", "org.opentest4j.AssertionFailedError"
            )
            == "assertion_failed"
        )
    finally:
        conn.close()


def test_generated_runs_count_limited_filter_exhaustion_as_passed(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        insert_scoreboard_fixture(conn, tmp_path)
        # gen 8 exhausted Arbitrary.filter(...) after validating a distinct new tuple. The
        # generated test's lifecycle hook remaps it to a passing JUnit result, and collection
        # records the LIMITED diagnostic in jqwik_property_execution.
        conn.execute(
            """
            INSERT INTO assertion (
                id,
                project_id,
                assertion_name,
                tested_method_call_arguments,
                tested_class_qualified_name,
                tested_method_name
            ) VALUES (3, 1, 'assertTrue', ?, 'smoke.Subject', 'contains')
            """,
            (
                '[{"type":"int","value":"2"},{"type":"int","value":"8"},{"type":"int","value":"5"}]',
            ),
        )
        conn.execute(
            """
            INSERT INTO generalization (
                id, project_id, assertion_id, variant, class_name, method_name, is_included
            ) VALUES (8, 1, 3, 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 1)
            """
        )
        report_cursor = conn.execute(
            """
            INSERT INTO junit_test_report (
                project_id,
                generalization_id,
                stage,
                variant,
                test_class_name,
                test_method_name,
                result,
                failure_type,
                failure_message
            ) VALUES (
                1,
                8,
                'COLLECT_JUNIT_REPORTS_GENERALIZED',
                'NAIVE',
                '_SubjectTest_Generalized',
                'valueInsideInterval',
                'PASSED',
                NULL,
                NULL
            )
            """
        )
        report_id = report_cursor.lastrowid
        conn.execute(
            """
            INSERT INTO jqwik_property_execution (
                jqwik_execution_run_id,
                project_id,
                generalization_id,
                junit_test_report_id,
                test_case_name,
                diagnostic_kind,
                raw_status,
                final_status,
                distinct_new_tuples
            ) VALUES (
                1, 1, 8, ?, 'valueInsideInterval',
                'LIMITED_TOO_MANY_FILTER_MISSES', 'FAILED', 'SUCCESSFUL', 1
            )
            """,
            (report_id,),
        )
        conn.commit()

        runs = get_generated_test_runs(conn, outcomes=None)
        limited = runs.set_index("generalization_id").loc[8]
        passed_runs = get_generated_test_runs(conn)

        assert set(runs["generalization_id"]) == {7, 8}
        assert set(passed_runs["generalization_id"]) == {7, 8}
        assert limited["outcome_class"] == "passed"
        assert limited["outcome"] == "passed"
        assert limited["diagnostic_kind"] == "LIMITED_TOO_MANY_FILTER_MISSES"
        assert limited["generation_diagnostic"] == "limited_filter_exhausted"
        assert limited["distinct_new_tuples"] == 1
        assert (
            runs.set_index("generalization_id").loc[7, "generation_diagnostic"]
            == "full"
        )
    finally:
        conn.close()


def create_scoreboard_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE project (
            id INTEGER PRIMARY KEY,
            root_path TEXT NOT NULL,
            data_path TEXT NOT NULL
        );
        CREATE TABLE assertion (
            id INTEGER PRIMARY KEY,
            project_id INTEGER NOT NULL,
            assertion_name TEXT NOT NULL,
            tested_method_call_arguments TEXT,
            tested_method_parameters TEXT,
            tested_class_qualified_name TEXT,
            tested_method_name TEXT
        );
        CREATE TABLE generalization (
            id INTEGER PRIMARY KEY,
            project_id INTEGER NOT NULL,
            assertion_id INTEGER NOT NULL,
            variant TEXT NOT NULL,
            class_name TEXT NOT NULL,
            method_name TEXT NOT NULL,
            is_included BOOLEAN NOT NULL
        );
        CREATE TABLE junit_test_report (
            id INTEGER PRIMARY KEY,
            project_id INTEGER NOT NULL,
            generalization_id INTEGER,
            stage TEXT NOT NULL,
            variant TEXT,
            test_class_name TEXT NOT NULL,
            test_method_name TEXT NOT NULL,
            result TEXT NOT NULL,
            failure_type TEXT,
            failure_message TEXT
        );
        CREATE TABLE jqwik_property_execution (
            id INTEGER PRIMARY KEY,
            jqwik_execution_run_id INTEGER,
            project_id INTEGER NOT NULL,
            generalization_id INTEGER NOT NULL,
            junit_test_report_id INTEGER,
            test_case_name TEXT NOT NULL,
            diagnostic_kind TEXT NOT NULL,
            raw_status TEXT NOT NULL,
            final_status TEXT NOT NULL,
            distinct_new_tuples INTEGER,
            selected_value_log_path TEXT
        );
        CREATE TABLE jacoco_coverage_report (
            id INTEGER PRIMARY KEY,
            project_id INTEGER NOT NULL,
            stage TEXT NOT NULL,
            variant TEXT,
            covered_package TEXT NOT NULL,
            covered_class TEXT NOT NULL,
            instruction_missed INTEGER NOT NULL,
            instruction_covered INTEGER NOT NULL
        );
        """
    )


def insert_scoreboard_fixture(conn: sqlite3.Connection, tmp_path) -> str:
    conn.execute(
        "INSERT INTO project (id, root_path, data_path) VALUES (1, '/repo', ?)",
        (str(tmp_path),),
    )
    conn.execute(
        """
        INSERT INTO assertion (
            id,
            project_id,
            assertion_name,
            tested_method_call_arguments,
            tested_class_qualified_name,
            tested_method_name
        ) VALUES (2, 1, 'assertTrue', ?, 'smoke.Subject', 'contains')
        """,
        (
            '[{"type":"int","value":"1"},{"type":"int","value":"10"},{"type":"int","value":"5"}]',
        ),
    )
    conn.execute(
        """
        INSERT INTO generalization (
            id, project_id, assertion_id, variant, class_name, method_name, is_included
        ) VALUES (7, 1, 2, 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 1)
        """
    )
    report_cursor = conn.execute(
        """
        INSERT INTO junit_test_report (
            project_id, generalization_id, stage, variant, test_class_name, test_method_name, result
        ) VALUES (1, 7, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 'PASSED')
        """
    )
    value_log_path = str(
        tmp_path
        / "project-id-1"
        / "jqwik-data"
        / "executions"
        / "exec-fixture"
        / "7.NAIVE.values.tsv"
    )
    conn.execute(
        """
        INSERT INTO jqwik_property_execution (
            jqwik_execution_run_id,
            project_id,
            generalization_id,
            junit_test_report_id,
            test_case_name,
            diagnostic_kind,
            raw_status,
            final_status,
            distinct_new_tuples,
            selected_value_log_path
        ) VALUES (1, 1, 7, ?, 'valueInsideInterval', 'FULL', 'SUCCESSFUL', 'SUCCESSFUL', 0, ?)
        """,
        (report_cursor.lastrowid, value_log_path),
    )
    conn.execute(
        """
        INSERT INTO jacoco_coverage_report (
            project_id, stage, variant, covered_package, covered_class, instruction_missed, instruction_covered
        ) VALUES (1, 'COLLECT_JACOCO_DATA_GENERALIZED', 'NAIVE', 'smoke', 'Subject', 10, 40)
        """
    )
    conn.commit()
    return value_log_path


def _scoreboard_df(rows):
    return pd.DataFrame(
        rows,
        columns=[
            "variant",
            "generated_method_name",
            "assertion_name",
            "parameter_value_coverage",
        ],
    )


def test_jarvis_table2_reference_is_complete_and_corrects_polynomial_pvc():
    by_row = {row.table_row: row for row in JARVIS_TABLE2}
    assert len(JARVIS_TABLE2) == 10
    # JARVIS Scala-PBT PVC, verbatim from the paper's Table 2.
    assert by_row["CharUtilsTest::isAscii"].pbt_pvc == 59
    assert by_row["CharUtilsTest::isPrintable"].pbt_pvc == 45
    assert by_row["FastMathTest::testMinMaxDouble"].pbt_pvc == 400
    assert by_row["FastMathTest::toIntExact"].pbt_pvc == 65
    assert by_row["IntervalTest"].pbt_pvc == 2
    assert by_row["PolynomialFunctionTest::testConstants"].pbt_pvc == 105
    # testLinear vs testfirstDerivativeComparison were transposed in the prose
    # audit; the paper's Table 2 has linear=160, derivative=264.
    assert by_row["PolynomialFunctionTest::testLinear"].pbt_pvc == 160
    assert (
        by_row["PolynomialFunctionTest::testfirstDerivativeComparison"].pbt_pvc == 264
    )
    assert by_row["PrecisionTest"].pbt_pvc == 102
    assert by_row["UnivariateFunctionTest::testAbs"].pbt_pvc == 506
    # Every fixture probe maps to exactly one Table-2 row (guards name drift).
    probe_names = [
        spec.generated_method_name for row in JARVIS_TABLE2 for spec in row.probes
    ]
    assert len(probe_names) == len(set(probe_names))


def test_compare_to_jarvis_aggregates_probes_and_exposes_pvc_delta():
    scoreboard = _scoreboard_df(
        [
            ("IMPROVED", "isAscii", "assertTrue", 60),
            ("IMPROVED", "isAscii", "assertFalse", 88),
            ("IMPROVED", "minDouble", "assertEquals", 152),
            ("IMPROVED", "maxDouble", "assertEquals", 152),
            ("IMPROVED", "absValue", "assertEquals", 94),
            ("IMPROVED", "precisionEquals", "assertTrue", 176),
            ("IMPROVED", "precisionEquals", "assertFalse", 30),
        ]
    )
    by_row = compare_to_jarvis(scoreboard, variant="IMPROVED").set_index("table_row")
    # Probes fold into their Table-2 row (sum PVC, the eps precedent).
    assert by_row.loc["CharUtilsTest::isAscii", "teralizer_pvc"] == 148
    assert by_row.loc["CharUtilsTest::isAscii", "probe_count"] == 2
    assert by_row.loc["CharUtilsTest::isAscii", "pvc_delta"] == 89
    assert by_row.loc["CharUtilsTest::isAscii", "original_cut_pvc"] == 6
    assert by_row.loc["FastMathTest::testMinMaxDouble", "teralizer_pvc"] == 304
    assert by_row.loc["FastMathTest::testMinMaxDouble", "pvc_delta"] == -96
    assert by_row.loc["PrecisionTest", "teralizer_pvc"] == 206
    assert by_row.loc["PrecisionTest", "pvc_delta"] == 104
    assert by_row.loc["UnivariateFunctionTest::testAbs", "pvc_delta"] == -412


def test_compare_to_jarvis_excludes_non_table2_and_other_variants():
    scoreboard = _scoreboard_df(
        [
            ("IMPROVED", "absValue", "assertEquals", 94),
            ("IMPROVED", "precisionEqualsMaxUlps", "assertFalse", 15),
            ("NAIVE", "absValue", "assertEquals", 91),
        ]
    )
    by_row = compare_to_jarvis(scoreboard, variant="IMPROVED").set_index("table_row")
    # maxUlps is a non-Table-2 raw-bits probe; it must not fold into PrecisionTest.
    assert by_row.loc["PrecisionTest", "probe_count"] == 0
    assert pd.isna(by_row.loc["PrecisionTest", "teralizer_pvc"])
    assert pd.isna(by_row.loc["PrecisionTest", "pvc_delta"])
    # Only the IMPROVED variant's probe counts toward the comparison.
    assert by_row.loc["UnivariateFunctionTest::testAbs", "teralizer_pvc"] == 94


def test_compare_to_jarvis_validates_mut_when_columns_present():
    correct = pd.DataFrame(
        [
            {
                "variant": "IMPROVED",
                "generated_method_name": "absValue",
                "assertion_name": "assertEquals",
                "parameter_value_coverage": 94,
                "tested_class_qualified_name": (
                    "org.apache.commons.math4.analysis.function.Abs"
                ),
                "tested_method_name": "value",
            }
        ]
    )
    by_row = compare_to_jarvis(correct, variant="IMPROVED").set_index("table_row")
    assert by_row.loc["UnivariateFunctionTest::testAbs", "teralizer_pvc"] == 94

    wrong = correct.assign(
        tested_class_qualified_name="org.apache.commons.math4.analysis.function.Sin"
    )
    with pytest.raises(ValueError, match="unexpected MUT"):
        compare_to_jarvis(wrong, variant="IMPROVED")


def test_get_mutation_scores_counts_distinct_mutants():
    conn = sqlite3.connect(":memory:")
    try:
        conn.executescript(
            """
            CREATE TABLE pit_mutation_report (
                project_id INTEGER, variant TEXT, stage TEXT, is_detected INTEGER,
                status TEXT, mutated_class TEXT, mutated_method TEXT,
                method_description TEXT, line_number INTEGER, mutator TEXT,
                indexes INTEGER
            );
            """
        )
        g = "COLLECT_PIT_DATA_GENERALIZED"
        rows = [
            # IMPROVED: A killed (twice -> one kill), B survived (covered, not killed),
            # C killed with a NULL description, D NO_COVERAGE (reached by no test).
            (1, "IMPROVED", g, 1, "KILLED", "C", "m", "desc", 10, "MUT_A", 0),
            (1, "IMPROVED", g, 1, "KILLED", "C", "m", "desc", 10, "MUT_A", 0),
            (1, "IMPROVED", g, 0, "SURVIVED", "C", "m", "desc", 11, "MUT_B", 0),
            (1, "IMPROVED", g, 1, "KILLED", "C", "m", None, 12, "MUT_C", 0),
            (1, "IMPROVED", g, 0, "NO_COVERAGE", "C", "m", "desc", 13, "MUT_D", 0),
            (1, "NAIVE", g, 1, "KILLED", "C", "m", "desc", 10, "MUT_A", 0),
            # wrong stage -> excluded.
            (1, "IMPROVED", "X", 1, "KILLED", "C", "m", "d", 99, "MUT_Z", 0),
        ]
        conn.executemany(
            "INSERT INTO pit_mutation_report VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows
        )
        conn.commit()

        scores = get_mutation_scores(conn).set_index("variant")
        assert scores.loc["IMPROVED", "killed_mutants"] == 2  # A once + C
        assert scores.loc["IMPROVED", "covered_mutants"] == 3  # A, B, C (not D)
        assert scores.loc["IMPROVED", "total_mutants"] == 4  # A, B, C, D
        assert scores.loc["NAIVE", "killed_mutants"] == 1
        assert scores.loc["NAIVE", "covered_mutants"] == 1
        assert set(get_mutation_scores(conn, variants=["IMPROVED"])["variant"]) == {
            "IMPROVED"
        }
    finally:
        conn.close()


def test_summarize_variants_pairs_flat_kills_with_rising_pvc():
    scoreboard = pd.DataFrame(
        {
            "variant": [
                "IMPROVED",
                "IMPROVED",
                "IMPROVED_1000_TRIES",
                "IMPROVED_1000_TRIES",
            ],
            "parameter_value_coverage": [60, 88, 190, 290],
        }
    )
    mutation = pd.DataFrame(
        {
            "variant": [
                "IMPROVED",
                "IMPROVED",
                "IMPROVED_1000_TRIES",
                "IMPROVED_1000_TRIES",
            ],
            "killed_mutants": [44, 10, 44, 10],
            "covered_mutants": [80, 7, 80, 7],
            "total_mutants": [2000, 953, 2000, 953],
        }
    )
    summary = summarize_variants(scoreboard, mutation).set_index("variant")
    # PVC rises with the tries budget...
    assert summary.loc["IMPROVED", "total_pvc"] == 148
    assert summary.loc["IMPROVED_1000_TRIES", "total_pvc"] == 480
    assert summary.loc["IMPROVED", "probes"] == 2
    # ...while kills, covered mutants, and the covered score stay flat.
    assert summary.loc["IMPROVED", "killed_mutants"] == 54
    assert summary.loc["IMPROVED", "covered_mutants"] == 87
    assert summary.loc["IMPROVED", "total_mutants"] == 2953
    assert summary.loc["IMPROVED", "covered_mutation_score"] == round(54 / 87, 4)
    assert (
        summary.loc["IMPROVED", "covered_mutation_score"]
        == summary.loc["IMPROVED_1000_TRIES", "covered_mutation_score"]
    )
