"""Reports why widening refused to emit generalized tests."""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import ColumnSpec, Metric, Table
from teralizer.eval.reports import _funnel


# Reconstructs why WideningLicense refused. Corpora from before
# `generalization.widening_refusal_code` existed record only the single
# ORACLE_NOT_WIDENABLE label, so the cause has to be re-derived here. Read the
# column instead once the corpus has it.
#
# The CASE order must match `WideningLicense.evaluate` branch for branch. It
# decides attribution, not just naming: a non-boolean oracle that also
# concretized belongs to the first branch that catches it.
WIDENING_REFUSAL_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
attempts AS (
    SELECT count(*)::numeric AS n
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    WHERE g.variant = :variant
),
refusals AS (
    SELECT
        CASE
            WHEN a.output_spec_class = 'EXCEPTION'
                 AND coalesce(a.concretization_events, 0) > 0
                 AND a.post_concretization_divergence_risk IS DISTINCT FROM false
                THEN 'EXCEPTION_DIVERGENCE'
            WHEN a.output_spec_class = 'EXCEPTION'
                THEN 'PATH_COVERAGE'
            WHEN coalesce(
                     a.generalization_recipe::jsonb ->> 'oracleExpressionType', ''
                 ) NOT IN ('boolean', 'java.lang.Boolean')
                THEN 'NON_BOOLEAN_ORACLE'
            WHEN coalesce(a.concretization_events, 0) > 0
                THEN 'CONCRETIZATION'
            ELSE 'PATH_COVERAGE'
        END AS code,
        CASE
            WHEN a.output_spec_class = 'EXCEPTION'
                 AND coalesce(a.concretization_events, 0) > 0
                 AND a.post_concretization_divergence_risk IS DISTINCT FROM false
                THEN 'Exception oracle concretized with divergence risk'
            WHEN a.output_spec_class = 'EXCEPTION'
                THEN 'Path condition does not pin every generated parameter'
            WHEN coalesce(
                     a.generalization_recipe::jsonb ->> 'oracleExpressionType', ''
                 ) NOT IN ('boolean', 'java.lang.Boolean')
                THEN 'Null output model, oracle expression is not boolean'
            WHEN coalesce(a.concretization_events, 0) > 0
                THEN 'Null output model, concretization weakened the path condition'
            ELSE 'Path condition does not pin every generated parameter'
        END AS cause
    FROM generalization g
    JOIN assertion a ON a.id = g.assertion_id
    JOIN eligible_projects ep ON ep.id = g.project_id
    WHERE g.variant = :variant
      AND g.exclusion_info = 'ORACLE_NOT_WIDENABLE'
)
SELECT code,
       cause,
       count(*)::bigint AS refusals,
       count(*) / (SELECT n FROM attempts) AS attempts_pct,
       count(*) / sum(count(*)) OVER () AS refusals_pct
FROM refusals
GROUP BY code, cause
ORDER BY refusals DESC, code COLLATE "C"
"""


def fetch_widening_refusals(conn: Connection, variant: str) -> pd.DataFrame:
    """Return widening-refusal counts by cause for the eligible corpus."""
    df = read_sql(conn, WIDENING_REFUSAL_SQL, _funnel.base_query_params(variant))
    df["refusals"] = df["refusals"].astype(int)
    for column in ("attempts_pct", "refusals_pct"):
        df[column] = df[column].astype(float)
    return pd.DataFrame(
        df, columns=["code", "cause", "refusals", "attempts_pct", "refusals_pct"]
    )


# Macro names are built from these slugs, so renaming one breaks the chapter.
# Rename the label in the SQL instead, the code is what this keys on.
_WIDENING_METRIC_KEYS = {
    "NON_BOOLEAN_ORACLE": "non_boolean_oracle",
    "CONCRETIZATION": "concretization",
    "PATH_COVERAGE": "path_coverage",
    "EXCEPTION_DIVERGENCE": "exception_divergence",
}


def widening_refusal_table(df: pd.DataFrame, provenance) -> Table:
    return Table(
        key="rq6_widening_refusals",
        df=df,
        columns=[
            ColumnSpec("Refusal cause", "cause"),
            ColumnSpec("Generalizations", "refusals", fmt="count", align="r"),
            ColumnSpec("Refusals", "refusals_pct", fmt="pct1", align="r"),
            ColumnSpec("Attempts", "attempts_pct", fmt="pct1", align="r"),
        ],
        caption=(
            "Why the widening license refused to emit a generalized test, by "
            "cause, for the real-world dataset."
        ),
        label="tab:widening-refusals",
        note=(
            "Refusals are decided before a generalized test is written, so they "
            "carry no filter decision and no lifecycle record."
        ),
        provenance=provenance,
    )


def widening_refusal_metrics(df: pd.DataFrame, provenance) -> list[Metric]:
    metrics = [
        Metric(
            "realworld.widening_refusals",
            int(df["refusals"].sum()),
            fmt="count",
            provenance=provenance,
        )
    ]
    for row in df.to_dict("records"):
        key = _WIDENING_METRIC_KEYS.get(str(row["code"]))
        if key is None:
            raise RuntimeError(f"unmapped widening refusal code: {row['code']!r}")
        metrics.append(
            Metric(
                f"realworld.widening_refusal_{key}",
                int(row["refusals"]),
                fmt="count",
                provenance=provenance,
            )
        )
        metrics.append(
            Metric(
                f"realworld.widening_refusal_{key}_pct",
                float(row["refusals_pct"]),
                fmt="pct1",
                provenance=provenance,
            )
        )
    return metrics
