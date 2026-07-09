import pytest
import sqlalchemy.exc
from sqlalchemy import create_engine, text
from teralizer.eval.data import Required, connect, read_sql


def test_read_sql_returns_dataframe():
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as conn:
        conn.execute(text("CREATE TABLE t (a INTEGER, b TEXT)"))
        conn.execute(text("INSERT INTO t VALUES (1, 'x'), (2, 'y')"))
    with engine.connect() as conn:
        df = read_sql(conn, "SELECT a, b FROM t ORDER BY a")
    assert list(df.columns) == ["a", "b"]
    assert df["a"].tolist() == [1, 2]
    engine.dispose()


def test_validated_connect_accepts_present_objects():
    require = (Required("project", "table", ["id", "use_test_generalization"]),)
    try:
        with connect("postgres_dev", validate_schema=True, require=require) as conn:
            assert conn is not None
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")


def test_validated_connect_rejects_missing_column():
    require = (Required("project", "table", ["column_that_does_not_exist"]),)
    try:
        cm = connect("postgres_dev", validate_schema=True, require=require)
        with pytest.raises(RuntimeError, match="column_that_does_not_exist"):
            cm.__enter__()
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")


def test_validated_connect_requires_objects():
    with pytest.raises(ValueError, match="require"):
        connect("postgres_dev", validate_schema=True).__enter__()
