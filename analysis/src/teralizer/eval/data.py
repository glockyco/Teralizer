"""Connection resolution and a read_sql helper for eval reports."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer.report_basis import open_report_connection


@contextmanager
def connect(db: str, *, validate_schema: bool = False) -> Iterator[Connection]:
    """Open a read-only connection to `db`.

    validate_schema is reserved for the old-schema RQs ported in Plan 4; the
    new-schema RQ0/RQ6 path uses the report_basis open connection.
    """
    if validate_schema:
        raise NotImplementedError(
            "schema-validated connect arrives with RQ1-5 (Plan 4)"
        )
    with open_report_connection(db) as conn:
        yield conn


def read_sql(conn: Connection, sql: str, params: dict | None = None) -> pd.DataFrame:
    return pd.read_sql_query(text(sql), conn, params=params or {})
