"""Counts the mutants that only a generalized test kills in the real-world dataset."""

from __future__ import annotations

from collections.abc import Collection

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import Metric, MetricPopulation, ValueKind


# PIT runs the Initial suite alone and then the Initial suite together with the
# generalized tests, so the generalized tests have no mutation score of their
# own. A mutant counts here when the Initial run does not detect it and the
# combined run credits the kill to a generalized test. The difference between
# the two scores is not used, because it also contains the mutants whose verdict
# changes between the runs through a nondeterministic developer-written test.
#
# `is_detected` and the covered denominator follow the RQ1 mutation score.
# PIT identifies a mutant by its location, its mutator, and its description.
# `fetch_generalized_only_kills` rejects a corpus in which that identity does
# not pair every mutant of the two runs one to one.
GENERALIZED_ONLY_KILLS_SQL = """
WITH reports AS (
    SELECT m.project_id, m.stage, m.status, m.is_detected,
           m.killing_generalization_id,
           row(m.mutated_package, m.mutated_class, m.mutated_method,
               m.method_description, m.line_number, m.mutator, m.indexes,
               m.blocks, m.description) AS mutant
    FROM pit_mutation_report m
    WHERE m.project_id = ANY(:project_ids)
      AND ((m.stage = 'COLLECT_PIT_DATA_INITIAL' AND m.variant IS NULL)
           OR (m.stage = 'COLLECT_PIT_DATA_GENERALIZED' AND m.variant = :variant))
),
initial AS (SELECT * FROM reports WHERE stage = 'COLLECT_PIT_DATA_INITIAL'),
combined AS (SELECT * FROM reports WHERE stage = 'COLLECT_PIT_DATA_GENERALIZED')
SELECT coalesce(i.project_id, c.project_id) AS project_id,
       count(*)::bigint AS mutant_pairs,
       count(DISTINCT coalesce(i.mutant, c.mutant))::bigint AS mutants,
       count(*) FILTER (
           WHERE i.project_id IS NULL OR c.project_id IS NULL
       )::bigint AS unpaired_mutants,
       count(*) FILTER (WHERE i.status <> 'NO_COVERAGE')::bigint AS covered_mutants,
       count(*) FILTER (
           WHERE NOT i.is_detected AND c.killing_generalization_id IS NOT NULL
       )::bigint AS generalized_only_kills
FROM initial i
FULL JOIN combined c ON i.project_id = c.project_id AND i.mutant = c.mutant
GROUP BY 1
ORDER BY 1
"""


GENERALIZED_ONLY_KILL_METRIC_KEYS = frozenset(
    {
        "realworld.mutation.covered_mutants",
        "realworld.mutation.generalized_only_kills",
        "realworld.mutation.generalized_only_kill_projects",
        "realworld.mutation.generalized_only_kills_pp",
    }
)


def fetch_generalized_only_kills(
    conn: Connection, variant: str, project_ids: Collection[int]
) -> pd.DataFrame:
    """Return covered mutants and generalized-only kills for each given project."""
    df = read_sql(
        conn,
        GENERALIZED_ONLY_KILLS_SQL,
        {"variant": variant, "project_ids": sorted(project_ids)},
    )
    validate_generalized_only_kills(df, project_ids)
    return df


def validate_generalized_only_kills(
    df: pd.DataFrame, project_ids: Collection[int]
) -> None:
    missing = sorted(set(project_ids) - set(df["project_id"].astype(int)))
    if missing:
        raise RuntimeError(f"projects without PIT mutation reports: {missing}")
    unpaired = df.loc[df["unpaired_mutants"] > 0, "project_id"].astype(int).tolist()
    if unpaired:
        raise RuntimeError(
            f"projects whose two PIT runs report different mutants: {unpaired}"
        )
    ambiguous = df.loc[df["mutant_pairs"] != df["mutants"], "project_id"]
    if not ambiguous.empty:
        raise RuntimeError(
            "projects with mutants that share one identity: "
            f"{ambiguous.astype(int).tolist()}"
        )


def generalized_only_kill_metrics(df: pd.DataFrame, provenance) -> list[Metric]:
    covered_key = "realworld.mutation.covered_mutants"
    kills_key = "realworld.mutation.generalized_only_kills"
    projects_key = "realworld.mutation.generalized_only_kill_projects"
    gain_key = "realworld.mutation.generalized_only_kills_pp"
    covered = int(df["covered_mutants"].sum())
    kills = int(df["generalized_only_kills"].sum())
    if covered == 0:
        raise RuntimeError("the Initial suites cover no mutant")
    return [
        Metric(
            covered_key,
            covered,
            fmt="count",
            provenance=provenance,
            kind=ValueKind.COUNT,
            population=MetricPopulation(covered_key, "Mutant", "real-world"),
        ),
        Metric(
            kills_key,
            kills,
            fmt="count",
            provenance=provenance,
            kind=ValueKind.COUNT,
            population=MetricPopulation(kills_key, "Mutant", "real-world"),
        ),
        Metric(
            projects_key,
            int((df["generalized_only_kills"] > 0).sum()),
            fmt="count",
            provenance=provenance,
            kind=ValueKind.COUNT,
            population=MetricPopulation(projects_key, "Project", "real-world"),
        ),
        Metric(
            gain_key,
            round(100 * kills / covered, 2),
            fmt="decimal2",
            provenance=provenance,
            kind=ValueKind.PERCENT_DELTA,
            population=MetricPopulation(gain_key, "Mutant", "real-world"),
        ),
    ]
