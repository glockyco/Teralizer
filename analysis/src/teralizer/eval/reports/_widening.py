"""Reports why widening refused to emit generalized tests."""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import ColumnSpec, Metric, Table
from teralizer.eval.reports import _funnel


# `WideningLicense` writes the cause it decided on into
# `generalization.widening_refusal_code`, so this reads that column. The license
# picks one code per refusal from an ordered set of checks, and a refusal that
# satisfies several of them carries the first. Re-deriving the cause here would
# have to repeat that order, and a copy that drifts reports a cause the pipeline
# never assigned.
#
# A corpus written before the column existed carries only the ORACLE_NOT_WIDENABLE
# label. `fetch_widening_refusals` rejects such a corpus rather than report a
# partial table.
WIDENING_REFUSAL_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
attempts AS (
    SELECT count(*)::numeric AS n
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    WHERE g.variant = :variant
),
refusals AS (
    SELECT g.widening_refusal_code AS code
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    WHERE g.variant = :variant
      AND g.widening_refusal_code IS NOT NULL
)
SELECT code,
       count(*)::bigint AS refusals,
       count(*) / (SELECT n FROM attempts) AS attempts_pct,
       count(*) / sum(count(*)) OVER () AS refusals_pct
FROM refusals
GROUP BY code
ORDER BY refusals DESC, code COLLATE "C"
"""

# A corpus that predates the typed column. Counted separately so the reader gets
# a refusal rather than a table that silently omits every refusal.
UNTYPED_REFUSAL_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT count(*)::bigint AS n
FROM generalization g
JOIN eligible_projects ep ON ep.id = g.project_id
WHERE g.variant = :variant
  AND g.exclusion_info = 'ORACLE_NOT_WIDENABLE'
  AND g.widening_refusal_code IS NULL
"""

# The metric slug and the table label for each code `WideningLicense` can write.
# Adding a code to the license means adding it here, and an unmapped code raises.
WIDENING_REFUSALS = {
    "NULL_CONCRETE_OUTPUT_NOT_LITERAL": (
        "output_not_literal",
        "Null output model, and the returned value is not a bytecode literal",
    ),
    "NULL_CONCRETE_CONCRETIZATION_EVENTS": (
        "concretization",
        "Null output model, and a native call received a symbolic argument",
    ),
    "NULL_CONCRETE_PARAMETERS_EMPTY": (
        "parameters_empty",
        "Null output model, and no generated parameter reaches the path condition",
    ),
    "NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS": (
        "path_coverage",
        "Null output model, and the path condition does not pin every generated parameter",
    ),
    "EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK": (
        "exception_divergence",
        "Exception oracle, concretized with a risk of divergence",
    ),
    "EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS": (
        "exception_path_coverage",
        "Exception oracle, and the path condition does not pin every generated parameter",
    ),
}


def fetch_widening_refusals(conn: Connection, variant: str) -> pd.DataFrame:
    """Return widening-refusal counts by cause for the eligible corpus."""
    params = _funnel.base_query_params(variant)
    untyped = int(read_sql(conn, UNTYPED_REFUSAL_SQL, params)["n"].iloc[0])
    if untyped:
        raise RuntimeError(
            f"{untyped} refusals carry no widening_refusal_code. This corpus predates the "
            "column, and the cause it recorded cannot be recovered from the database."
        )

    df = read_sql(conn, WIDENING_REFUSAL_SQL, params)
    df["refusals"] = df["refusals"].astype(int)
    for column in ("attempts_pct", "refusals_pct"):
        df[column] = df[column].astype(float)

    unmapped = sorted(set(df["code"]) - set(WIDENING_REFUSALS))
    if unmapped:
        raise RuntimeError(f"unmapped widening refusal codes: {unmapped}")
    df["cause"] = df["code"].map(lambda code: WIDENING_REFUSALS[code][1])

    return pd.DataFrame(
        df, columns=["code", "cause", "refusals", "attempts_pct", "refusals_pct"]
    )


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
        entry = WIDENING_REFUSALS.get(str(row["code"]))
        if entry is None:
            raise RuntimeError(f"unmapped widening refusal code: {row['code']!r}")
        key = entry[0]
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
