"""Tests for RepoReapers rerun report integrity invariants."""

import pytest
from sqlalchemy import create_engine, text


_BASE_TABLES = {
    "assertion",
    "mut_resolution_observation",
    "assertion_semantics",
    "generalization",
    "generalization_lifecycle",
    "jqwik_property_execution",
    "task_diagnostic",
    "jpf_extraction_summary",
    "filter_result",
}

_BUDGET_EXPECTATIONS = {
    "generic budget: task_diagnostic BUILD_% OTHER_COMPILE_FAILURE share": {
        "actual": 0.50,
        "expected": 0.30,
        "holds": False,
    },
    "generic budget: jpf_extraction_summary UNCAUGHT_EXCEPTION_PATH failure_counts share": {
        "actual": 0.25,
        "expected": 0.30,
        "holds": True,
    },
    "generic budget: assertion_semantics UNKNOWN semantic_kind share": {
        "actual": 0.50,
        "expected": 0.30,
        "holds": False,
    },
    "generic budget: filter_result REJECT NULL/<none> reason_code share": {
        "actual": 0.25,
        "expected": 0.30,
        "holds": True,
    },
}


def _create_schema(conn, *, include_task_diagnostic: bool = True) -> None:
    statements = [
        "CREATE TABLE assertion (id INTEGER PRIMARY KEY)",
        "CREATE TABLE mut_resolution_observation (id INTEGER PRIMARY KEY, assertion_id INTEGER NOT NULL)",
        "CREATE TABLE assertion_semantics (id INTEGER PRIMARY KEY, assertion_id INTEGER NOT NULL, semantic_kind TEXT NOT NULL, argument_shape TEXT NOT NULL)",
        "CREATE TABLE generalization (id INTEGER PRIMARY KEY)",
        "CREATE TABLE generalization_lifecycle (id INTEGER PRIMARY KEY, generalization_id INTEGER NOT NULL, generated_source_created BOOLEAN NOT NULL, final_usable BOOLEAN NOT NULL)",
        "CREATE TABLE jqwik_property_execution (id INTEGER PRIMARY KEY, generalization_id INTEGER NOT NULL)",
        "CREATE TABLE jpf_extraction_summary (id INTEGER PRIMARY KEY, failure_counts TEXT NOT NULL)",
        "CREATE TABLE filter_result (id INTEGER PRIMARY KEY, decision TEXT NOT NULL, reason_code TEXT)",
    ]
    if include_task_diagnostic:
        statements.append(
            "CREATE TABLE task_diagnostic (id INTEGER PRIMARY KEY, stage TEXT NOT NULL, reason_code TEXT NOT NULL)"
        )

    for statement in statements:
        conn.execute(text(statement))


def _insert_budget_fixture(conn, *, include_task_diagnostic: bool = True) -> None:
    conn.execute(text("INSERT INTO assertion (id) VALUES (1), (2), (3), (4)"))
    conn.execute(
        text(
            "INSERT INTO mut_resolution_observation (id, assertion_id) VALUES "
            "(1, 1), (2, 2), (3, 3), (4, 4)"
        )
    )
    conn.execute(
        text(
            "INSERT INTO assertion_semantics (id, assertion_id, semantic_kind, argument_shape) VALUES "
            "(1, 1, 'UNKNOWN', 'scalar'), "
            "(2, 2, 'UNKNOWN', 'scalar'), "
            "(3, 3, 'VALUE_EQUALITY', 'scalar'), "
            "(4, 4, 'PREDICATE', 'boolean')"
        )
    )
    conn.execute(text("INSERT INTO generalization (id) VALUES (1), (2)"))
    conn.execute(
        text(
            "INSERT INTO generalization_lifecycle "
            "(id, generalization_id, generated_source_created, final_usable) VALUES "
            "(1, 1, 1, 1), (2, 2, 1, 0)"
        )
    )
    conn.execute(
        text(
            "INSERT INTO jqwik_property_execution (id, generalization_id) VALUES (1, 1)"
        )
    )
    conn.execute(
        text(
            "INSERT INTO jpf_extraction_summary (id, failure_counts) VALUES "
            '(1, \'{"UNCAUGHT_EXCEPTION_PATH": 1, "NO_INPUT_SPEC": 2}\'), '
            "(2, '{\"UNSUPPORTED_BYTECODE\": 1}')"
        )
    )
    conn.execute(
        text(
            "INSERT INTO filter_result (id, decision, reason_code) VALUES "
            "(1, 'REJECT', NULL), "
            "(2, 'REJECT', 'MISSING_TESTED_FILE'), "
            "(3, 'REJECT', 'MISSING_TESTED_PARAMS'), "
            "(4, 'REJECT', 'UNSUPPORTED_ASSERTION_SHAPE'), "
            "(5, 'ACCEPT', NULL)"
        )
    )
    if include_task_diagnostic:
        conn.execute(
            text(
                "INSERT INTO task_diagnostic (id, stage, reason_code) VALUES "
                "(1, 'BUILD_GENERATED_PROJECT', 'OTHER_COMPILE_FAILURE'), "
                "(2, 'BUILD_GENERATED_TEST', 'OTHER_COMPILE_FAILURE'), "
                "(3, 'BUILD_GENERATED_PROJECT', 'GENERATED_SOURCE_LEVEL_TOO_NEW'), "
                "(4, 'BUILD_GENERATED_TEST', 'TEST_COMPILE_OUTPUT_MISSING'), "
                "(5, 'JPF_EXECUTION', 'OTHER_COMPILE_FAILURE')"
            )
        )


def _patch_snapshot_catalog(
    monkeypatch, report, *, missing_tables: set[str] | None = None
) -> None:
    missing = missing_tables or set()

    def table_exists(_conn, table: str) -> bool:
        return table in _BASE_TABLES and table not in missing

    def column_exists(_conn, table: str, column: str) -> bool:
        return table == "filter_result" and column == "reason_code"

    monkeypatch.setattr(report, "_table_exists", table_exists)
    monkeypatch.setattr(report, "_column_exists", column_exists)


@pytest.fixture
def conn():
    """In-memory sqlite snapshot with report-integrity telemetry rows."""
    engine = create_engine("sqlite:///:memory:")
    with engine.connect() as connection:
        _create_schema(connection)
        _insert_budget_fixture(connection)
        connection.commit()
        yield connection
    engine.dispose()


def test_telemetry_integrity_reports_generic_budget_rows(monkeypatch, conn):
    from teralizer import reporeapers_rerun_report as report

    _patch_snapshot_catalog(monkeypatch, report)

    result = report.get_telemetry_integrity(conn)

    missing = set(_BUDGET_EXPECTATIONS) - set(result["invariant"])
    assert missing == set()
    for invariant, expectation in _BUDGET_EXPECTATIONS.items():
        rows = result[result["invariant"] == invariant]
        assert len(rows) == 1
        row = rows.iloc[0]
        assert row["actual"] == pytest.approx(expectation["actual"])
        assert row["expected"] == pytest.approx(expectation["expected"])
        assert bool(row["holds"]) is expectation["holds"]


def test_telemetry_integrity_skips_missing_budget_table(monkeypatch):
    from teralizer import reporeapers_rerun_report as report

    engine = create_engine("sqlite:///:memory:")
    try:
        with engine.connect() as connection:
            _create_schema(connection, include_task_diagnostic=False)
            _insert_budget_fixture(connection, include_task_diagnostic=False)
            connection.commit()
            _patch_snapshot_catalog(
                monkeypatch, report, missing_tables={"task_diagnostic"}
            )

            result = report.get_telemetry_integrity(connection)
    finally:
        engine.dispose()

    assert report._SKIP_NOTE.format(table="task_diagnostic") in set(result["invariant"])
