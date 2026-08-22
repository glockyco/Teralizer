"""Typed denominator funnel for real-world generalization attempts."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import BandSummary, ColumnSpec, Table, ValueKind
from teralizer.eval.provenance import Provenance
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel


class PopulationKey(StrEnum):
    ATTEMPTED = "attempted"
    EMITTED = "emitted"
    FILTER_ADJUDICATED = "filter_adjudicated"
    FILTER_PASSED = "filter_passed"
    VALIDATED = "validated"
    REDUCED = "reduced"
    FINAL_USABLE = "final_usable"


POPULATION_LABELS = {
    PopulationKey.ATTEMPTED: "Attempted",
    PopulationKey.EMITTED: "Emitted",
    PopulationKey.FILTER_ADJUDICATED: "Filter adjudicated",
    PopulationKey.FILTER_PASSED: "Filter passed",
    PopulationKey.VALIDATED: "Validated",
    PopulationKey.REDUCED: "Reduced",
    PopulationKey.FINAL_USABLE: "Final usable",
}


@dataclass(frozen=True)
class FunnelBand:
    population: PopulationKey
    entering_population: PopulationKey
    entering: int
    passing: int
    exclusions: int
    attempt_known_exclusions: int
    attempt_unknown_exclusions: int

    def __post_init__(self) -> None:
        if self.entering != self.passing + self.exclusions:
            raise ValueError(f"funnel band does not conserve {self.population.value}")
        if self.exclusions != (
            self.attempt_known_exclusions + self.attempt_unknown_exclusions
        ):
            raise ValueError(
                f"attempt-state counts do not conserve {self.population.value} exclusions"
            )


@dataclass(frozen=True)
class GeneralizationFunnel:
    counts: dict[PopulationKey, int]
    bands: tuple[FunnelBand, ...]
    band_summaries: dict[str, BandSummary]
    table: Table
    unknown_attempt_state: int


GENERALIZATION_LIFECYCLE_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
filter_adjudicated AS (
    SELECT DISTINCT fr.generalization_id
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    WHERE fr.generalization_id IS NOT NULL
      AND fr.filter_name ~ :filter_class_pattern
),
generalization_lifecycle_observation AS (
    SELECT
        g.project_id,
        g.id AS generalization_id,
        g.variant,
        TRUE AS attempted,
        coalesce(l.generated_source_created, FALSE) AS emitted,
        (fa.generalization_id IS NOT NULL) AS filter_adjudicated,
        coalesce(l.generated_filter_passed, FALSE) AS filter_passed,
        coalesce(
            l.generated_source_created
            AND l.generated_project_compiled
            AND l.generated_tests_executed
            AND l.generated_report_collected
            AND l.generated_filter_passed,
            FALSE
        ) AS validated,
        coalesce(l.generated_pit_collected, FALSE) AS reduced,
        coalesce(l.final_usable, FALSE) AS final_usable,
        l.final_failure_stage,
        CASE
            WHEN l.final_failure_stage IS NULL THEN NULL
            ELSE EXISTS (
                SELECT 1
                FROM task attempted_stage
                WHERE attempted_stage.project_id = g.project_id
                  AND attempted_stage.stage = l.final_failure_stage
                  AND (attempted_stage.variant IS NULL
                       OR attempted_stage.variant = g.variant)
            )
        END AS failure_attempt_observed
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
    LEFT JOIN filter_adjudicated fa ON fa.generalization_id = g.id
    WHERE g.variant = :variant
)
SELECT *
FROM generalization_lifecycle_observation
ORDER BY generalization_id
"""


_ORDER = tuple(PopulationKey)


def _bool_series(frame: pd.DataFrame, key: PopulationKey) -> pd.Series:
    return frame[key.value].astype(bool)


def _failure_attempt_known(
    frame: pd.DataFrame,
    population: PopulationKey,
    excluded: pd.Series,
) -> pd.Series:
    if population in {
        PopulationKey.EMITTED,
        PopulationKey.FILTER_PASSED,
        PopulationKey.VALIDATED,
        PopulationKey.FINAL_USABLE,
    }:
        return excluded
    observed = frame["failure_attempt_observed"].eq(True)
    return excluded & observed


def _validate_nested_populations(frame: pd.DataFrame) -> None:
    for entering, population in zip(_ORDER, _ORDER[1:]):
        outside = _bool_series(frame, population) & ~_bool_series(frame, entering)
        if outside.any():
            ids = frame.loc[outside, "generalization_id"].astype(int).tolist()
            raise exclusion.ExclusionEvidenceError(
                f"{population.value} generalizations outside {entering.value}: {ids}"
            )


def _build_table(
    counts: dict[PopulationKey, int],
    bands: tuple[FunnelBand, ...],
    provenance: Provenance | None,
) -> Table:
    rows = [
        {
            "population_key": PopulationKey.ATTEMPTED.value,
            "population": POPULATION_LABELS[PopulationKey.ATTEMPTED],
            "count": counts[PopulationKey.ATTEMPTED],
            "entering": counts[PopulationKey.ATTEMPTED],
            "excluded": 0,
            "attempt_known_exclusions": 0,
            "attempt_unknown_exclusions": 0,
        }
    ]
    rows.extend(
        {
            "population_key": band.population.value,
            "population": POPULATION_LABELS[band.population],
            "count": band.passing,
            "entering": band.entering,
            "excluded": band.exclusions,
            "attempt_known_exclusions": band.attempt_known_exclusions,
            "attempt_unknown_exclusions": band.attempt_unknown_exclusions,
        }
        for band in bands
    )
    return Table(
        key="rq6_generalization_funnel",
        df=pd.DataFrame(rows),
        columns=[
            ColumnSpec("Population", "population", kind=ValueKind.ENTITY),
            ColumnSpec("Count", "count", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Entering", "entering", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Excluded", "excluded", kind=ValueKind.COUNT, align="r"),
            ColumnSpec(
                "Attempt known",
                "attempt_known_exclusions",
                kind=ValueKind.COUNT,
                align="r",
            ),
            ColumnSpec(
                "Attempt unknown",
                "attempt_unknown_exclusions",
                kind=ValueKind.COUNT,
                align="r",
            ),
        ],
        caption=(
            "Observed generalized-test populations for {entity.variant.improved_c}. "
            "Unknown means that no independent task record proves the reported "
            "failure stage ran."
        ),
        label="tab:rq6-generalization-funnel",
        row_key="population_key",
        provenance=provenance,
        note=(
            "A lifecycle failure stage without a matching task record remains unknown; "
            "later failure labels are not treated as attempt evidence."
        ),
    )


def build_generalization_funnel_from_observations(
    frame: pd.DataFrame, provenance: Provenance | None
) -> GeneralizationFunnel:
    """Aggregate one typed observation relation into conserved funnel bands."""
    _validate_nested_populations(frame)
    counts = {
        population: int(_bool_series(frame, population).sum()) for population in _ORDER
    }
    bands: list[FunnelBand] = []
    for entering_population, population in zip(_ORDER, _ORDER[1:]):
        entering = _bool_series(frame, entering_population)
        passing = _bool_series(frame, population)
        excluded = entering & ~passing
        attempt_known = _failure_attempt_known(frame, population, excluded)
        band = FunnelBand(
            population=population,
            entering_population=entering_population,
            entering=int(entering.sum()),
            passing=int(passing.sum()),
            exclusions=int(excluded.sum()),
            attempt_known_exclusions=int(attempt_known.sum()),
            attempt_unknown_exclusions=int((excluded & ~attempt_known).sum()),
        )
        bands.append(band)

    unknown_attempt_state = int(
        (
            frame["final_failure_stage"].notna()
            & ~frame["failure_attempt_observed"].eq(True)
        ).sum()
    )
    result_bands = tuple(bands)
    summaries = {
        band.population.value: BandSummary(
            title=POPULATION_LABELS[band.population],
            entering=band.entering,
            inclusions=band.passing,
            exclusions=band.exclusions,
        )
        for band in result_bands
    }
    return GeneralizationFunnel(
        counts=counts,
        bands=result_bands,
        band_summaries=summaries,
        table=_build_table(counts, result_bands, provenance),
        unknown_attempt_state=unknown_attempt_state,
    )


def build_generalization_funnel(
    conn: Connection, variant: str, provenance: Provenance | None
) -> GeneralizationFunnel:
    """Read and aggregate observed lifecycle and independent task evidence."""
    exclusion.validate_evidence(conn, variant)
    frame = read_sql(
        conn, GENERALIZATION_LIFECYCLE_SQL, exclusion.query_params(variant)
    )
    return build_generalization_funnel_from_observations(frame, provenance)
