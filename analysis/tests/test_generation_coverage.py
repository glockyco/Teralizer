"""Tests for generation-coverage analysis queries."""

import pytest
from sqlalchemy import create_engine, text


_SCHEMA = (
    """
    CREATE TABLE generalization (
        id INTEGER PRIMARY KEY,
        project_id INTEGER NOT NULL,
        test_id INTEGER NOT NULL,
        assertion_id INTEGER NOT NULL,
        variant TEXT NOT NULL,
        file_path TEXT NOT NULL,
        class_qualified_name TEXT NOT NULL,
        method_qualified_name TEXT NOT NULL,
        package_name TEXT NOT NULL,
        class_name TEXT NOT NULL,
        method_name TEXT NOT NULL,
        total_constraint_count INTEGER,
        used_constraint_count INTEGER,
        line_count INTEGER NOT NULL,
        is_included BOOLEAN NOT NULL,
        exclusion_info TEXT
    )
    """,
    """
    CREATE TABLE generation_clause (
        id INTEGER PRIMARY KEY,
        generalization_id INTEGER NOT NULL,
        parameter_name TEXT NOT NULL,
        type_domain TEXT NOT NULL,
        shape TEXT NOT NULL,
        consumed BOOLEAN NOT NULL
    )
    """,
    """
    CREATE TABLE generation_parameter (
        id INTEGER PRIMARY KEY,
        generalization_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        declared_type TEXT NOT NULL,
        type_domain TEXT NOT NULL,
        symbolic_spec_present BOOLEAN NOT NULL,
        representation TEXT NOT NULL
    )
    """,
    "CREATE TABLE project (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
    """
    CREATE TABLE filter_result (
        id INTEGER PRIMARY KEY,
        project_id INTEGER NOT NULL,
        filter_name TEXT NOT NULL,
        decision TEXT NOT NULL,
        reason TEXT NOT NULL
    )
    """,
)


@pytest.fixture
def conn():
    """In-memory sqlite database with generation telemetry rows."""
    engine = create_engine("sqlite:///:memory:")
    with engine.connect() as connection:
        for statement in _SCHEMA:
            connection.execute(text(statement))
        connection.execute(
            text(
                """
                INSERT INTO generalization VALUES
                    (1, 1, 1, 1, 'IMPROVED_100_TRIES', 'f', 'c', 'm', 'p', 'c', 'm', 3, 2, 10, TRUE, NULL),
                    (2, 1, 2, 2, 'IMPROVED_100_TRIES', 'f', 'c', 'm', 'p', 'c', 'm', 0, 0, 10, FALSE, 'NO_SPEC')
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO generation_clause VALUES
                    (1, 1, 'x', 'INTEGER', 'LT(Variable:INTEGER,Constant:INTEGER)', TRUE),
                    (2, 1, 'x', 'INTEGER', 'MOD(Variable:INTEGER,Constant:INTEGER)', FALSE),
                    (3, 1, 'x', 'INTEGER', 'EQ(Variable:INTEGER,Constant:INTEGER)', TRUE)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO generation_parameter VALUES
                    (1, 1, 'x', 'int', 'INTEGER', TRUE, 'encoded'),
                    (2, 1, 's', 'String', 'STRING', TRUE, 'residual'),
                    (3, 2, 'obj', 'Object', 'OBJECT', FALSE, 'none')
                """
            )
        )
        connection.execute(text("INSERT INTO project VALUES (1, 'test-project')"))
        connection.execute(
            text(
                """
                INSERT INTO filter_result VALUES
                    (1, 1, 'ParameterTypeFilter', 'REJECT', 'java.time.Instant'),
                    (2, 1, 'ReturnTypeFilter', 'REJECT', 'void'),
                    (3, 1, 'ParameterTypeFilter', 'ACCEPT', 'int')
                """
            )
        )
        connection.commit()
        yield connection
    engine.dispose()


def test_main_rejects_a_registered_corpus_database(monkeypatch, capsys):
    from teralizer import generation_coverage

    monkeypatch.setattr(
        "sys.argv", ["generation_coverage", "--scratch-db", "postgres_dev"]
    )
    with pytest.raises(SystemExit) as error:
        generation_coverage.main()

    assert error.value.args == (2,)
    assert "must match the reserved scratch_ name pattern" in capsys.readouterr().err


def test_top_residual_shapes(conn):
    from teralizer.generation_coverage import get_top_residual_shapes

    result = get_top_residual_shapes(conn)

    assert len(result) == 1
    assert result.iloc[0]["shape"] == "MOD(Variable:INTEGER,Constant:INTEGER)"
    assert result.iloc[0]["count"] == 1


def test_per_domain_coverage(conn):
    from teralizer.generation_coverage import get_per_domain_coverage

    result = get_per_domain_coverage(conn)

    row = result[result["type_domain"] == "INTEGER"].iloc[0]
    assert row["consumed"] == 2
    assert row["residual"] == 1


def test_parameter_representations(conn):
    from teralizer.generation_coverage import get_parameter_representations

    result = get_parameter_representations(conn)

    encoded = result[result["representation"] == "encoded"].iloc[0]
    residual = result[result["representation"] == "residual"].iloc[0]
    none = result[result["representation"] == "none"].iloc[0]
    assert encoded["count"] == 1
    assert residual["count"] == 1
    assert none["count"] == 1


def test_entry_gap_by_type(conn):
    from teralizer.generation_coverage import get_entry_gap_by_type

    result = get_entry_gap_by_type(conn)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["declared_type"] == "java.time.Instant"
    assert row["type_domain"] == "ENTRY_GAP"
    assert row["count"] == 1


def test_spf_gap_ranking(conn):
    from teralizer.generation_coverage import get_spf_gap_ranking

    result = get_spf_gap_ranking(conn)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["type_domain"] == "OBJECT"
    assert row["count"] == 1
    assert row["exclusion_reason"] == "NO_SPEC"
