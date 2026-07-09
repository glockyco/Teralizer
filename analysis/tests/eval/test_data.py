from sqlalchemy import create_engine, text
from teralizer.eval.data import read_sql


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
