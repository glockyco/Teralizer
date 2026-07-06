"""Generation-coverage analysis for clause and parameter telemetry."""

from __future__ import annotations
import argparse


import pandas as pd
from sqlalchemy import Connection, text
from teralizer.report_basis import open_report_connection, print_basis_header

_DEFAULT_DB = "postgres_dev"


def get_top_residual_shapes(conn: Connection) -> pd.DataFrame:
    """Return residual clause shapes ranked by frequency.

    Columns: shape, count.
    """
    sql = text(
        """
        SELECT shape, count(*) AS count
        FROM generation_clause
        WHERE consumed = false
        GROUP BY shape
        ORDER BY count DESC, shape
        """
    )
    return pd.read_sql(sql, conn)


def get_per_domain_coverage(conn: Connection) -> pd.DataFrame:
    """Return consumed and residual clause counts per type domain.

    Columns: type_domain, consumed, residual.
    """
    sql = text(
        """
        SELECT
            type_domain,
            sum(CASE WHEN consumed THEN 1 ELSE 0 END) AS consumed,
            sum(CASE WHEN NOT consumed THEN 1 ELSE 0 END) AS residual
        FROM generation_clause
        GROUP BY type_domain
        ORDER BY type_domain
        """
    )
    return pd.read_sql(sql, conn)


def get_parameter_representations(conn: Connection) -> pd.DataFrame:
    """Return the encoded, residual, and none parameter representation counts.

    Columns: representation, count.
    """
    sql = text(
        """
        SELECT representation, count(*) AS count
        FROM generation_parameter
        GROUP BY representation
        ORDER BY count DESC, representation
        """
    )
    return pd.read_sql(sql, conn)


def get_entry_gap_by_type(conn: Connection) -> pd.DataFrame:
    """Return parameter types rejected before symbolic generation.

    Columns: declared_type, type_domain, count.
    """
    sql = text(
        """
        SELECT fr.reason AS declared_type, 'ENTRY_GAP' AS type_domain, count(*) AS count
        FROM filter_result fr
        WHERE fr.filter_name LIKE '%%ParameterTypeFilter'
          AND fr.decision = 'REJECT'
        GROUP BY fr.reason
        ORDER BY count DESC, declared_type
        """
    )
    return pd.read_sql(sql, conn)


def get_spf_gap_ranking(conn: Connection) -> pd.DataFrame:
    """Return admitted parameter domains lacking symbolic specifications.

    Columns: type_domain, count, exclusion_reason.
    """
    sql = text(
        """
        SELECT
            gp.type_domain,
            count(*) AS count,
            g.exclusion_info AS exclusion_reason
        FROM generation_parameter gp
        JOIN generalization g ON g.id = gp.generalization_id
        WHERE gp.symbolic_spec_present = false
        GROUP BY gp.type_domain, g.exclusion_info
        ORDER BY count DESC, gp.type_domain, g.exclusion_info
        """
    )
    return pd.read_sql(sql, conn)


def generate_report(conn: Connection) -> dict[str, pd.DataFrame]:
    """Run all generation-coverage queries and return their DataFrames."""
    return {
        "top_residual_shapes": get_top_residual_shapes(conn),
        "per_domain_coverage": get_per_domain_coverage(conn),
        "parameter_representations": get_parameter_representations(conn),
        "entry_gap_by_type": get_entry_gap_by_type(conn),
        "spf_gap_ranking": get_spf_gap_ranking(conn),
    }


def print_report(report: dict[str, pd.DataFrame]) -> None:
    """Print a compact generation-coverage report."""
    for title, frame in report.items():
        print(f"=== {title.replace('_', ' ').title()} ===")
        print(frame.to_string(index=False))
        print()


def main() -> None:
    """Print a generation-coverage summary from the dev analysis database."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--db",
        default=_DEFAULT_DB,
        help=f"database to inspect (default: {_DEFAULT_DB})",
    )
    args = parser.parse_args()
    with open_report_connection(args.db) as conn:
        print_basis_header(conn, args.db)
        print_report(generate_report(conn))


if __name__ == "__main__":
    main()
