"""Corpus-local filtering evidence for generalized tests."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required, read_sql
from teralizer.eval.model import ColumnSpec, Table, ValueKind, share_value
from teralizer.eval.provenance import Provenance
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel

CONTROLLED_VARIANT = "IMPROVED_200_TRIES"
GENERALIZATION_FILTER_PATTERN = r"filter\.NonPassingTestFilter$"
CONTROLLED_REQUIRES: tuple[Required, ...] = (
    Required("project", "table", ("id", "use_test_generalization")),
    Required(
        "generalization",
        "table",
        ("id", "project_id", "variant", "exclusion_info"),
    ),
    Required(
        "filter_result",
        "table",
        ("id", "generalization_id", "filter_name", "decision"),
    ),
)


class FilteringOutcome(StrEnum):
    INCLUDED = "included"
    EXCLUDED = "excluded"
    PRE_FILTER_FAILURE = "pre_filter_failure"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class FilteringSummary:
    total: int
    included: int
    excluded: int

    def __post_init__(self) -> None:
        if self.total <= 0:
            raise exclusion.ExclusionEvidenceError(
                "filtering population must contain at least one generalized test"
            )
        if self.total != self.included + self.excluded:
            raise exclusion.ExclusionEvidenceError(
                "filtering counts do not conserve: "
                f"total={self.total}, included={self.included}, excluded={self.excluded}"
            )

    @property
    def included_share(self):
        return share_value(self.included, self.total)


CONTROLLED_FILTERING_SQL = """
SELECT
    g.id AS generalization_id,
    g.exclusion_info,
    fr.id AS filter_result_id,
    fr.filter_name,
    fr.decision
FROM generalization g
JOIN project p ON p.id = g.project_id
LEFT JOIN filter_result fr
    ON fr.generalization_id = g.id
   AND fr.filter_name ~ :filter_class_pattern
WHERE p.use_test_generalization
  AND g.variant = :variant
ORDER BY g.id, fr.id
"""


REALWORLD_FILTERING_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT
    g.id AS generalization_id,
    g.exclusion_info,
    fr.id AS filter_result_id,
    fr.filter_name,
    fr.decision
FROM generalization g
JOIN eligible_projects ep ON ep.id = g.project_id
LEFT JOIN filter_result fr
    ON fr.generalization_id = g.id
   AND fr.filter_name ~ :filter_class_pattern
WHERE g.variant = :variant
ORDER BY g.id, fr.id
"""


def _classify_rows(frame: pd.DataFrame) -> pd.DataFrame:
    required = {
        "generalization_id",
        "exclusion_info",
        "filter_result_id",
        "filter_name",
        "decision",
    }
    missing = sorted(required - set(frame.columns))
    if missing:
        raise exclusion.ExclusionEvidenceError(
            f"filtering evidence lacks columns: {missing}"
        )

    if frame["generalization_id"].isna().any():
        raise exclusion.ExclusionEvidenceError(
            "filtering evidence contains a missing generalization identity"
        )

    observations: list[dict[str, object]] = []
    for generalization_id, rows in frame.groupby("generalization_id", sort=True):
        evidence = rows.loc[rows["filter_result_id"].notna()]
        if len(evidence) > 1:
            ids = evidence["filter_result_id"].astype(int).tolist()
            raise exclusion.ExclusionEvidenceError(
                f"duplicate filtering evidence for generalization {generalization_id}: {ids}"
            )

        exclusion_values = rows["exclusion_info"].dropna().astype(str).unique().tolist()
        if len(exclusion_values) > 1:
            raise exclusion.ExclusionEvidenceError(
                f"contradictory exclusion evidence for generalization {generalization_id}: "
                f"{exclusion_values}"
            )
        exclusion_info = exclusion_values[0] if exclusion_values else None

        if evidence.empty:
            outcome = (
                FilteringOutcome.PRE_FILTER_FAILURE
                if exclusion_info is not None
                else FilteringOutcome.UNKNOWN
            )
        else:
            row = evidence.iloc[0]
            producer = str(row["filter_name"])
            if not producer.endswith("filter.NonPassingTestFilter"):
                raise exclusion.ExclusionEvidenceError(
                    f"unsupported filtering producer for generalization "
                    f"{generalization_id}: {producer}"
                )
            decision = str(row["decision"])
            if decision == "ACCEPT":
                outcome = FilteringOutcome.INCLUDED
            elif decision == "REJECT":
                outcome = FilteringOutcome.EXCLUDED
            else:
                raise exclusion.ExclusionEvidenceError(
                    f"unsupported filtering decision for generalization "
                    f"{generalization_id}: {decision}"
                )

        observations.append(
            {
                "generalization_id": int(generalization_id),
                "outcome": outcome.value,
            }
        )

    result = pd.DataFrame(observations, columns=["generalization_id", "outcome"])
    if result["generalization_id"].duplicated().any():
        duplicates = result.loc[
            result["generalization_id"].duplicated(keep=False), "generalization_id"
        ].tolist()
        raise exclusion.ExclusionEvidenceError(
            f"duplicate generalized-test identities: {duplicates}"
        )
    return result


def fetch_controlled_filtering(conn: Connection) -> pd.DataFrame:
    """Read controlled filtering outcomes without using the inclusion flag."""
    frame = read_sql(
        conn,
        CONTROLLED_FILTERING_SQL,
        {
            "variant": CONTROLLED_VARIANT,
            "filter_class_pattern": GENERALIZATION_FILTER_PATTERN,
        },
    )
    return _classify_rows(frame)


def fetch_realworld_filtering(conn: Connection, variant: str) -> pd.DataFrame:
    """Read RepoReapers filtering outcomes inside the accepted eligible cohort."""
    frame = read_sql(
        conn,
        REALWORLD_FILTERING_SQL,
        {
            **exclusion.query_params(variant),
            "filter_class_pattern": GENERALIZATION_FILTER_PATTERN,
        },
    )
    return _classify_rows(frame)


def summarize_filtering(observations: pd.DataFrame) -> FilteringSummary:
    """Summarize only generalized tests with included or excluded filter results."""
    if observations["generalization_id"].duplicated().any():
        duplicates = (
            observations.loc[
                observations["generalization_id"].duplicated(keep=False),
                "generalization_id",
            ]
            .astype(int)
            .tolist()
        )
        raise exclusion.ExclusionEvidenceError(
            f"duplicate generalized-test identities: {duplicates}"
        )
    outcomes = observations["outcome"]
    known = outcomes.isin(
        {FilteringOutcome.INCLUDED.value, FilteringOutcome.EXCLUDED.value}
    )
    included = int(outcomes.eq(FilteringOutcome.INCLUDED.value).sum())
    excluded = int(outcomes.eq(FilteringOutcome.EXCLUDED.value).sum())
    return FilteringSummary(int(known.sum()), included, excluded)


def build_filtering_comparison_table(
    controlled: FilteringSummary,
    realworld: FilteringSummary,
    provenance: Provenance | None,
) -> Table:
    """Render the two corpus-local filtering summaries without combining them."""
    frame = pd.DataFrame(
        [
            {
                "dataset_key": "controlled",
                "dataset": "Controlled",
                "total": controlled.total,
                "included": controlled.included,
                "excluded": controlled.excluded,
                "included_share": controlled.included_share,
            },
            {
                "dataset_key": "realworld",
                "dataset": "RepoReapers",
                "total": realworld.total,
                "included": realworld.included,
                "excluded": realworld.excluded,
                "included_share": realworld.included_share,
            },
        ]
    )
    return Table(
        key="rq6_filtering_comparison",
        df=frame,
        columns=[
            ColumnSpec("Dataset", "dataset", kind=ValueKind.TEXT),
            ColumnSpec("Total", "total", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Included", "included", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Excluded", "excluded", kind=ValueKind.COUNT, align="r"),
            ColumnSpec(
                "Included share",
                "included_share",
                kind=ValueKind.SHARE,
                align="r",
            ),
        ],
        caption="Filtering results for controlled and RepoReapers generalized tests.",
        label="tab:rq6-filtering-comparison",
        row_key="dataset_key",
        provenance=provenance,
        note=(
            "Each share uses the generalized tests with a filtering result in that "
            "dataset as its denominator."
        ),
    )
