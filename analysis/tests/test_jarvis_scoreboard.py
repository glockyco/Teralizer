import sqlite3
from typing import cast

import pandas as pd
import pytest

from teralizer.jarvis_scoreboard import (
    classify_generated_test_outcome,
    compute_parameter_value_coverage,
    get_generated_test_runs,
    get_instruction_coverage_scores,
    get_pvc_scores,
    get_scoreboard,
    parse_jqwik_value_log,
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


def test_generated_test_runs_compute_stable_jqwik_value_paths(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        insert_scoreboard_fixture(conn, tmp_path)

        runs = get_generated_test_runs(conn)

        assert cast(
            pd.DataFrame, runs[["project_id", "generalization_id", "variant"]]
        ).to_dict("records") == [
            {"project_id": 1, "generalization_id": 7, "variant": "NAIVE"}
        ]
        assert runs.loc[0, "jqwik_value_log_path"] == str(
            tmp_path / "project-id-1" / "jqwik-data" / "7.NAIVE.tsv"
        )
    finally:
        conn.close()


def test_generated_test_runs_resolve_relative_data_path_from_project_root(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        project_root = tmp_path / "fixture"
        conn.execute(
            "INSERT INTO project (id, root_path, data_path) VALUES (1, ?, 'data/run')",
            (str(project_root),),
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
            ) VALUES (2, 1, 'assertTrue', '[]', 'smoke.Subject', 'contains')
            """
        )
        conn.execute(
            """
            INSERT INTO generalization (
                id, project_id, assertion_id, variant, class_name, method_name, is_included
            ) VALUES (7, 1, 2, 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 1)
            """
        )
        conn.execute(
            """
            INSERT INTO junit_test_report (
                project_id, generalization_id, stage, variant, test_class_name, test_method_name, result
            ) VALUES (1, 7, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 'PASSED')
            """
        )
        conn.commit()

        runs = get_generated_test_runs(conn)

        assert runs.loc[0, "jqwik_value_log_path"] == str(
            project_root / "data/run" / "project-id-1" / "jqwik-data" / "7.NAIVE.tsv"
        )
    finally:
        conn.close()


def test_generated_test_runs_resolve_relative_data_path_from_working_directory_when_project_root_path_is_missing(
    tmp_path, monkeypatch
):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        workspace = tmp_path / "workspace"
        workspace.mkdir()
        (workspace / "data/run").mkdir(parents=True)
        conn.execute(
            "INSERT INTO project (id, root_path, data_path) VALUES (1, 'fixture', 'data/run')"
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
            ) VALUES (2, 1, 'assertTrue', '[]', 'smoke.Subject', 'contains')
            """
        )
        conn.execute(
            """
            INSERT INTO generalization (
                id, project_id, assertion_id, variant, class_name, method_name, is_included
            ) VALUES (7, 1, 2, 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 1)
            """
        )
        conn.execute(
            """
            INSERT INTO junit_test_report (
                project_id, generalization_id, stage, variant, test_class_name, test_method_name, result
            ) VALUES (1, 7, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 'PASSED')
            """
        )
        conn.commit()
        monkeypatch.chdir(workspace)

        runs = get_generated_test_runs(conn)

        assert runs.loc[0, "jqwik_value_log_path"] == str(
            workspace / "data/run" / "project-id-1" / "jqwik-data" / "7.NAIVE.tsv"
        )
    finally:
        conn.close()


def test_generated_test_runs_prefer_junit_value_log_snapshot(tmp_path):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        insert_scoreboard_fixture(conn, tmp_path)
        value_dir = tmp_path / "project-id-1" / "jqwik-data"
        value_dir.mkdir(parents=True)
        live_path = value_dir / "7.NAIVE.tsv"
        snapshot_path = value_dir / "7.NAIVE.junit.tsv"
        live_path.write_text("lower=999\n")
        snapshot_path.write_text("lower=1\n")

        runs = get_generated_test_runs(conn)

        assert runs.loc[0, "jqwik_value_log_path"] == str(snapshot_path)
    finally:
        conn.close()


def test_generated_test_runs_prefer_workspace_snapshot_over_project_live_log(
    tmp_path, monkeypatch
):
    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        workspace = tmp_path / "workspace"
        project_root = workspace / "fixture"
        workspace_snapshot_dir = workspace / "data/run/project-id-1/jqwik-data"
        project_live_dir = project_root / "data/run/project-id-1/jqwik-data"
        workspace_snapshot_dir.mkdir(parents=True)
        project_live_dir.mkdir(parents=True)
        (workspace_snapshot_dir / "7.NAIVE.junit.tsv").write_text("lower=1\n")
        (project_live_dir / "7.NAIVE.tsv").write_text("lower=999\n")
        conn.execute(
            "INSERT INTO project (id, root_path, data_path) VALUES (1, 'fixture', 'data/run')"
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
            ) VALUES (2, 1, 'assertTrue', '[]', 'smoke.Subject', 'contains')
            """
        )
        conn.execute(
            """
            INSERT INTO generalization (
                id, project_id, assertion_id, variant, class_name, method_name, is_included
            ) VALUES (7, 1, 2, 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 1)
            """
        )
        conn.execute(
            """
            INSERT INTO junit_test_report (
                project_id, generalization_id, stage, variant, test_class_name, test_method_name, result
            ) VALUES (1, 7, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 'PASSED')
            """
        )
        conn.commit()
        monkeypatch.chdir(workspace)

        runs = get_generated_test_runs(conn)

        assert runs.loc[0, "jqwik_value_log_path"] == str(
            workspace_snapshot_dir / "7.NAIVE.junit.tsv"
        )
    finally:
        conn.close()

    conn = sqlite3.connect(":memory:")
    try:
        create_scoreboard_schema(conn)
        insert_scoreboard_fixture(conn, tmp_path)
        value_dir = tmp_path / "project-id-1" / "jqwik-data"
        value_dir.mkdir(parents=True)
        (value_dir / "7.NAIVE.tsv").write_text(
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


def insert_scoreboard_fixture(conn: sqlite3.Connection, tmp_path) -> None:
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
    conn.execute(
        """
        INSERT INTO junit_test_report (
            project_id, generalization_id, stage, variant, test_class_name, test_method_name, result
        ) VALUES (1, 7, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'NAIVE', '_SubjectTest_Generalized', 'valueInsideInterval', 'PASSED')
        """
    )
    conn.execute(
        """
        INSERT INTO jacoco_coverage_report (
            project_id, stage, variant, covered_package, covered_class, instruction_missed, instruction_covered
        ) VALUES (1, 'COLLECT_JACOCO_DATA_GENERALIZED', 'NAIVE', 'smoke', 'Subject', 10, 40)
        """
    )
    conn.commit()
