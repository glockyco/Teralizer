"""Reports why widening refused to emit generalized tests."""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import (
    ColumnSpec,
    Metric,
    MetricPopulation,
    Table,
    ValueKind,
    share_value,
)
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
       sum(count(*)) OVER ()::bigint AS refusal_total,
       (SELECT n FROM attempts)::bigint AS attempts,
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
    for column in ("refusals", "refusal_total", "attempts"):
        df.isetitem(df.columns.get_loc(column), df[column].astype(int))
    attempts_pct = pd.Series(
        (
            share_value(refusals, attempts)
            for refusals, attempts in zip(df["refusals"], df["attempts"])
        ),
        index=df.index,
        dtype=object,
    )
    refusals_pct = pd.Series(
        (
            share_value(refusals, total)
            for refusals, total in zip(df["refusals"], df["refusal_total"])
        ),
        index=df.index,
        dtype=object,
    )
    df = df.assign(attempts_pct=attempts_pct, refusals_pct=refusals_pct)

    unmapped = sorted(set(df["code"]) - set(WIDENING_REFUSALS))
    if unmapped:
        raise RuntimeError(f"unmapped widening refusal codes: {unmapped}")
    df.loc[:, "cause"] = df["code"].map(lambda code: WIDENING_REFUSALS[code][1])

    return pd.DataFrame(
        df,
        columns=[
            "code",
            "cause",
            "refusals",
            "refusal_total",
            "attempts",
            "attempts_pct",
            "refusals_pct",
        ],
    )


def widening_refusal_table(df: pd.DataFrame, provenance) -> Table:
    return Table(
        key="rq6_widening_refusals",
        df=df,
        columns=[
            ColumnSpec("Refusal cause", "cause"),
            ColumnSpec("Generalizations", "refusals", kind=ValueKind.COUNT, align="r"),
            ColumnSpec(
                "All refusals", "refusal_total", kind=ValueKind.COUNT, align="r"
            ),
            ColumnSpec("Refusals", "refusals_pct", kind=ValueKind.SHARE, align="r"),
            ColumnSpec("All attempts", "attempts", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Attempts", "attempts_pct", kind=ValueKind.SHARE, align="r"),
        ],
        caption=(
            "Causes for generalization attempts that produce no generalized test "
            "in the real-world dataset."
        ),
        label="tab:widening-refusals",
        row_key="code",
        note=(
            "Refusals are decided before a generalized test is written, so they "
            "carry no filter decision and no lifecycle record."
        ),
        provenance=provenance,
    )


def widening_refusal_metrics(df: pd.DataFrame, provenance) -> list[Metric]:
    total_key = "realworld.widening_refusals"
    total_population = MetricPopulation(total_key, "Generalization", "real-world")
    refusals = int(df["refusals"].sum())
    refusal_totals = df["refusal_total"].astype(int).unique().tolist()
    if refusal_totals != [refusals]:
        raise RuntimeError(
            "widening refusal branches do not conserve their total: "
            f"rows={refusals}, declared={refusal_totals}"
        )
    attempts_values = df["attempts"].astype(int).unique().tolist()
    if len(attempts_values) != 1:
        raise RuntimeError(
            f"widening refusal rows disagree on attempt count: {attempts_values}"
        )
    attempts = attempts_values[0]
    if refusals > attempts:
        raise RuntimeError(
            f"widening refusals exceed generalization attempts: {refusals} > {attempts}"
        )
    metrics = [
        Metric(
            total_key,
            refusals,
            fmt="count",
            provenance=provenance,
            kind=ValueKind.COUNT,
            population=total_population,
        ),
        Metric(
            "realworld.widening_refusals_pct",
            float(share_value(refusals, attempts)),
            fmt="pct1",
            provenance=provenance,
            kind=ValueKind.SHARE,
            population=total_population,
            numerator_key=total_key,
            denominator_key="realworld.generalization_attempts",
        ),
    ]
    for row in df.to_dict("records"):
        entry = WIDENING_REFUSALS.get(str(row["code"]))
        if entry is None:
            raise RuntimeError(f"unmapped widening refusal code: {row['code']!r}")
        slug = entry[0]
        count_key = f"realworld.widening_refusal_{slug}"
        count_population = MetricPopulation(count_key, "Generalization", "real-world")
        metrics.append(
            Metric(
                count_key,
                int(row["refusals"]),
                fmt="count",
                provenance=provenance,
                kind=ValueKind.COUNT,
                population=count_population,
            )
        )
        metrics.append(
            Metric(
                f"realworld.widening_refusal_{slug}_pct",
                float(row["refusals_pct"]),
                fmt="pct1",
                provenance=provenance,
                kind=ValueKind.SHARE,
                population=count_population,
                numerator_key=count_key,
                denominator_key=total_key,
            )
        )
    return metrics
