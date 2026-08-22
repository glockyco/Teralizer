import pandas as pd
import pytest

from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel
from teralizer.eval.reports import _generalization_funnel as funnel


def _observation(
    generalization_id: int,
    *,
    emitted: bool,
    adjudicated: bool,
    passed: bool,
    validated: bool,
    reduced: bool,
    usable: bool,
    failure_stage: str | None = None,
    attempt_observed: bool | None = None,
) -> dict[str, object]:
    return {
        "project_id": 1,
        "generalization_id": generalization_id,
        "variant": "IMPROVED_200_TRIES",
        "attempted": True,
        "emitted": emitted,
        "filter_adjudicated": adjudicated,
        "filter_passed": passed,
        "validated": validated,
        "reduced": reduced,
        "final_usable": usable,
        "final_failure_stage": failure_stage,
        "failure_attempt_observed": attempt_observed,
    }


def test_first_failing_gate_owns_each_exclusion_once():
    observations = pd.DataFrame(
        [
            _observation(
                1,
                emitted=True,
                adjudicated=True,
                passed=True,
                validated=True,
                reduced=True,
                usable=True,
            ),
            _observation(
                2,
                emitted=False,
                adjudicated=False,
                passed=False,
                validated=False,
                reduced=False,
                usable=False,
            ),
            _observation(
                3,
                emitted=True,
                adjudicated=False,
                passed=False,
                validated=False,
                reduced=False,
                usable=False,
                failure_stage="EXECUTE_TESTS_GENERALIZED",
                attempt_observed=False,
            ),
            _observation(
                4,
                emitted=True,
                adjudicated=True,
                passed=False,
                validated=False,
                reduced=False,
                usable=False,
                failure_stage="FILTER_GENERALIZATIONS",
                attempt_observed=True,
            ),
            _observation(
                5,
                emitted=True,
                adjudicated=True,
                passed=True,
                validated=True,
                reduced=False,
                usable=False,
                failure_stage="COLLECT_PIT_DATA_GENERALIZED",
                attempt_observed=False,
            ),
        ]
    )

    result = funnel.build_generalization_funnel_from_observations(
        observations, provenance=None
    )
    exclusions = {band.population: band.exclusions for band in result.bands}
    assert exclusions == {
        funnel.PopulationKey.EMITTED: 1,
        funnel.PopulationKey.FILTER_ADJUDICATED: 1,
        funnel.PopulationKey.FILTER_PASSED: 1,
        funnel.PopulationKey.VALIDATED: 0,
        funnel.PopulationKey.REDUCED: 1,
        funnel.PopulationKey.FINAL_USABLE: 0,
    }
    assert sum(exclusions.values()) == (
        result.counts[funnel.PopulationKey.ATTEMPTED]
        - result.counts[funnel.PopulationKey.FINAL_USABLE]
    )
    assert result.unknown_attempt_state == 2
    assert sum(band.attempt_unknown_exclusions for band in result.bands) == 2


def test_later_population_cannot_exist_without_earlier_population():
    observations = pd.DataFrame(
        [
            _observation(
                7,
                emitted=False,
                adjudicated=True,
                passed=False,
                validated=False,
                reduced=False,
                usable=False,
            )
        ]
    )

    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match=r"filter_adjudicated generalizations outside emitted: \[7\]",
    ):
        funnel.build_generalization_funnel_from_observations(
            observations, provenance=None
        )


def test_report_metrics_share_the_funnel_table_values(rq6_report):
    table = next(
        table
        for table in rq6_report.tables()
        if table.key == "rq6_generalization_funnel"
    )
    counts = table.df.set_index("population_key")["count"]
    metric_keys = {
        funnel.PopulationKey.ATTEMPTED: "realworld.generalization_attempts",
        funnel.PopulationKey.EMITTED: "realworld.generalizations_emitted",
        funnel.PopulationKey.FILTER_ADJUDICATED: (
            "realworld.generalizations_filter_adjudicated"
        ),
        funnel.PopulationKey.FILTER_PASSED: "realworld.generalizations_filter_passed",
        funnel.PopulationKey.VALIDATED: "realworld.generalizations_validated",
        funnel.PopulationKey.REDUCED: "realworld.generalizations_reduced",
        funnel.PopulationKey.FINAL_USABLE: "realworld.generalizations_final_usable",
    }
    for population, metric_key in metric_keys.items():
        assert int(rq6_report.metric(metric_key).value) == int(counts[population.value])


def test_corpus_funnel_reconciles_mechanisms_and_attempt_state(rq6_conn):
    variant = _funnel.resolve_variant(rq6_conn)
    result = funnel.build_generalization_funnel(
        rq6_conn,
        variant,
        provenance=None,
    )
    mechanism_counts = exclusion.pivot_mechanism_partition(
        exclusion.fetch_mechanism_partition(rq6_conn, variant)
    ).set_index("level")
    generalizations = mechanism_counts.loc["Generalization"]

    assert result.counts[funnel.PopulationKey.ATTEMPTED] == int(
        generalizations["total"]
    )
    assert result.counts[funnel.PopulationKey.VALIDATED] == int(
        generalizations["included"]
    )
    assert (
        result.counts[funnel.PopulationKey.FILTER_PASSED]
        == result.counts[funnel.PopulationKey.VALIDATED]
    )
    assert (
        result.counts[funnel.PopulationKey.REDUCED]
        == result.counts[funnel.PopulationKey.FINAL_USABLE]
    )
    assert result.unknown_attempt_state > 0
    assert result.unknown_attempt_state == sum(
        band.attempt_unknown_exclusions for band in result.bands
    )
    assert tuple(result.table.df["population_key"]) == tuple(
        population.value for population in funnel.PopulationKey
    )
