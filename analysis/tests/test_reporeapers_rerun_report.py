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


_FILTER_REASON_SOURCES = [
    "assertThat(missingFileOneAlphaBetaGammaDeltaEpsilonZetaEtaTheta).isTrue();",
    "assertThat(missingFileTwoAlphaBetaGammaDeltaEpsilonZetaEtaTheta).isTrue();",
    "assertThat(missingFileThreeAlphaBetaGammaDeltaEpsilonZetaEtaTheta).isTrue();",
    "assertThat(missingFileFourAlphaBetaGammaDeltaEpsilonZetaEtaTheta).isTrue();",
]

_OTHER_FILTER_REASON_SOURCES = [
    "assertThat(parameterOneAlphaBetaGammaDeltaEpsilonZetaEtaTheta).isTrue();",
    "assertThat(parameterTwoAlphaBetaGammaDeltaEpsilonZetaEtaTheta).isTrue();",
]

_UNSUPPORTED_ASSERT_EQUALS_SOURCES = [
    "assertEquals(alphaExpectedOneAlphaBetaGammaDeltaEpsilonZetaEtaTheta, actual);",
    "assertEquals(alphaExpectedTwoAlphaBetaGammaDeltaEpsilonZetaEtaTheta, actual);",
    "assertEquals(alphaExpectedThreeAlphaBetaGammaDeltaEpsilonZetaEtaTheta, actual);",
    "assertEquals(alphaExpectedFourAlphaBetaGammaDeltaEpsilonZetaEtaTheta, actual);",
]

_UNSUPPORTED_ASSERT_THROWS_SOURCES = [
    "assertThrows(firstUnsupportedAlphaBetaGammaDeltaEpsilonZetaEtaTheta, call);",
    "assertThrows(secondUnsupportedAlphaBetaGammaDeltaEpsilonZetaEtaTheta, call);",
]

_SPF_NO_INPUT_MESSAGES = [
    "NO_INPUT_SPEC first diagnostic AlphaBetaGammaDeltaEpsilonZetaEtaTheta details",
    "NO_INPUT_SPEC second diagnostic AlphaBetaGammaDeltaEpsilonZetaEtaTheta details",
    "NO_INPUT_SPEC third diagnostic AlphaBetaGammaDeltaEpsilonZetaEtaTheta details",
    "NO_INPUT_SPEC fourth diagnostic AlphaBetaGammaDeltaEpsilonZetaEtaTheta details",
]

_SPF_UNCAUGHT_MESSAGES = [
    "UNCAUGHT_EXCEPTION_PATH first diagnostic AlphaBetaGammaDeltaEpsilonZetaEtaTheta details",
    "UNCAUGHT_EXCEPTION_PATH second diagnostic AlphaBetaGammaDeltaEpsilonZetaEtaTheta details",
]

_SNAPSHOT_COLUMNS = {
    "assertion": {"id", "assertion_name", "assertion_source_code"},
    "filter_result": {
        "id",
        "test_id",
        "assertion_id",
        "generalization_id",
        "filter_name",
        "decision",
        "reason_code",
    },
    "jpf_extraction_summary": {"id", "failure_counts"},
    "task_diagnostic": {"id", "stage", "reason_code", "first_error_message"},
}


def _sample_parts(sample: str) -> list[str]:
    return sample.split(" | ")


def _prefixes(values: list[str]) -> set[str]:
    return {value[:60] for value in values}


def _assert_sampled_from_bucket(
    result,
    *,
    bucket_column: str,
    bucket_value: str,
    allowed_values: list[str],
    expected_snippets: int,
) -> None:
    assert "sample" in result.columns
    rows = result[result[bucket_column] == bucket_value]
    assert len(rows) == 1
    parts = _sample_parts(rows.iloc[0]["sample"])
    assert len(parts) == expected_snippets
    assert rows.iloc[0]["sample"].count(" | ") == expected_snippets - 1
    assert set(parts) <= _prefixes(allowed_values)
    assert all(len(part) == 60 for part in parts)


def _create_sampling_schema(conn) -> None:
    statements = [
        "CREATE TABLE assertion (id INTEGER PRIMARY KEY, assertion_name TEXT NOT NULL, assertion_source_code TEXT NOT NULL)",
        "CREATE TABLE filter_result (id INTEGER PRIMARY KEY, test_id INTEGER, assertion_id INTEGER, generalization_id INTEGER, filter_name TEXT NOT NULL, decision TEXT NOT NULL, reason_code TEXT)",
        "CREATE TABLE jpf_extraction_summary (id INTEGER PRIMARY KEY, failure_counts TEXT NOT NULL)",
        "CREATE TABLE task_diagnostic (id INTEGER PRIMARY KEY, stage TEXT NOT NULL, reason_code TEXT NOT NULL, first_error_message TEXT NOT NULL)",
    ]
    for statement in statements:
        conn.execute(text(statement))


def _insert_sampling_fixture(conn) -> None:
    assertion_rows = []
    assertion_id = 1
    for source in _FILTER_REASON_SOURCES:
        assertion_rows.append((assertion_id, "assertTrue", source))
        assertion_id += 1
    for source in _OTHER_FILTER_REASON_SOURCES:
        assertion_rows.append((assertion_id, "assertTrue", source))
        assertion_id += 1
    for source in _UNSUPPORTED_ASSERT_EQUALS_SOURCES:
        assertion_rows.append((assertion_id, "assertEquals", source))
        assertion_id += 1
    for source in _UNSUPPORTED_ASSERT_THROWS_SOURCES:
        assertion_rows.append((assertion_id, "assertThrows", source))
        assertion_id += 1

    for row in assertion_rows:
        conn.execute(
            text(
                "INSERT INTO assertion (id, assertion_name, assertion_source_code) "
                "VALUES (:id, :assertion_name, :assertion_source_code)"
            ),
            {"id": row[0], "assertion_name": row[1], "assertion_source_code": row[2]},
        )

    filter_rows = []
    filter_id = 1
    for assertion_id in range(1, 5):
        filter_rows.append(
            (
                filter_id,
                assertion_id,
                "com.example.MissingValueFilter",
                "MISSING_TESTED_FILE",
            )
        )
        filter_id += 1
    for assertion_id in range(5, 7):
        filter_rows.append(
            (
                filter_id,
                assertion_id,
                "com.example.ParameterTypeFilter",
                "MISSING_TESTED_PARAMS",
            )
        )
        filter_id += 1
    for assertion_id in range(7, 13):
        filter_rows.append(
            (
                filter_id,
                assertion_id,
                "com.example.UnsupportedAssertionFilter",
                "UNSUPPORTED_ASSERTION_SHAPE",
            )
        )
        filter_id += 1

    for row in filter_rows:
        conn.execute(
            text(
                "INSERT INTO filter_result "
                "(id, test_id, assertion_id, generalization_id, filter_name, decision, reason_code) "
                "VALUES (:id, NULL, :assertion_id, NULL, :filter_name, 'REJECT', :reason_code)"
            ),
            {
                "id": row[0],
                "assertion_id": row[1],
                "filter_name": row[2],
                "reason_code": row[3],
            },
        )
    conn.execute(
        text(
            "INSERT INTO filter_result "
            "(id, test_id, assertion_id, generalization_id, filter_name, decision, reason_code) "
            "VALUES (99, NULL, 1, NULL, 'com.example.MissingValueFilter', 'ACCEPT', NULL)"
        )
    )

    conn.execute(
        text(
            "INSERT INTO jpf_extraction_summary (id, failure_counts) VALUES "
            '(1, \'{"NO_INPUT_SPEC": 4, "UNCAUGHT_EXCEPTION_PATH": 2}\')'
        )
    )
    diagnostic_id = 1
    for reason_code, messages in (
        ("NO_INPUT_SPEC", _SPF_NO_INPUT_MESSAGES),
        ("UNCAUGHT_EXCEPTION_PATH", _SPF_UNCAUGHT_MESSAGES),
    ):
        for message in messages:
            conn.execute(
                text(
                    "INSERT INTO task_diagnostic "
                    "(id, stage, reason_code, first_error_message) "
                    "VALUES (:id, 'ANALYZE_JPF', :reason_code, :first_error_message)"
                ),
                {
                    "id": diagnostic_id,
                    "reason_code": reason_code,
                    "first_error_message": message,
                },
            )
            diagnostic_id += 1


@pytest.fixture
def sampling_conn():
    """In-memory sqlite snapshot with assertion/SPF exemplar telemetry rows."""
    engine = create_engine("sqlite:///:memory:")
    with engine.connect() as connection:
        _create_sampling_schema(connection)
        _insert_sampling_fixture(connection)
        connection.commit()
        yield connection
    engine.dispose()


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
        if table == "filter_result" and column == "reason_code":
            return True
        return column in _SNAPSHOT_COLUMNS.get(table, set())

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


def test_assertion_filter_summary_samples_sources_by_reason_code(
    monkeypatch, sampling_conn
):
    from teralizer import reporeapers_rerun_report as report

    _patch_snapshot_catalog(monkeypatch, report)

    result = report.get_filter_summary(sampling_conn, report._ASSERTION_SCOPE)

    _assert_sampled_from_bucket(
        result,
        bucket_column="reason_code",
        bucket_value="MISSING_TESTED_FILE",
        allowed_values=_FILTER_REASON_SOURCES,
        expected_snippets=3,
    )
    _assert_sampled_from_bucket(
        result,
        bucket_column="reason_code",
        bucket_value="MISSING_TESTED_PARAMS",
        allowed_values=_OTHER_FILTER_REASON_SOURCES,
        expected_snippets=2,
    )


def test_unsupported_assertion_counts_samples_sources_by_assertion_name(
    monkeypatch, sampling_conn
):
    from teralizer import reporeapers_rerun_report as report

    _patch_snapshot_catalog(monkeypatch, report)

    result = report.get_unsupported_assertion_counts(sampling_conn, top=2)

    _assert_sampled_from_bucket(
        result,
        bucket_column="assertion_name",
        bucket_value="assertEquals",
        allowed_values=_UNSUPPORTED_ASSERT_EQUALS_SOURCES,
        expected_snippets=3,
    )
    _assert_sampled_from_bucket(
        result,
        bucket_column="assertion_name",
        bucket_value="assertThrows",
        allowed_values=_UNSUPPORTED_ASSERT_THROWS_SOURCES,
        expected_snippets=2,
    )


def test_spf_failure_causes_samples_task_diagnostic_messages_by_cause(
    monkeypatch, sampling_conn
):
    from teralizer import reporeapers_rerun_report as report

    _patch_snapshot_catalog(monkeypatch, report)
    original_read_sql = report._read_sql

    def sqlite_read_sql(conn, sql: str, **params):
        if "jsonb_each_text(failure_counts)" in sql:
            sql = sql.replace(
                "jsonb_each_text(failure_counts)", "json_each(failure_counts)"
            )
            sql = sql.replace("value::int", "CAST(value AS INTEGER)")
        return original_read_sql(conn, sql, **params)

    monkeypatch.setattr(report, "_read_sql", sqlite_read_sql)

    result = report.get_spf_failure_causes(sampling_conn)

    _assert_sampled_from_bucket(
        result,
        bucket_column="cause",
        bucket_value="NO_INPUT_SPEC",
        allowed_values=_SPF_NO_INPUT_MESSAGES,
        expected_snippets=3,
    )
    _assert_sampled_from_bucket(
        result,
        bucket_column="cause",
        bucket_value="UNCAUGHT_EXCEPTION_PATH",
        allowed_values=_SPF_UNCAUGHT_MESSAGES,
        expected_snippets=2,
    )
