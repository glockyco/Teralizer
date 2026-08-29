import pandas as pd
import pytest

from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _filtering_comparison as filtering


def _row(
    generalization_id: int,
    *,
    decision: str | None = None,
    exclusion_info: str | None = None,
    filter_result_id: int | None = None,
    filter_name: str | None = None,
) -> dict[str, object]:
    return {
        "generalization_id": generalization_id,
        "exclusion_info": exclusion_info,
        "filter_result_id": filter_result_id,
        "filter_name": filter_name,
        "decision": decision,
    }


def _observations() -> pd.DataFrame:
    return filtering._classify_rows(
        pd.DataFrame(
            [
                _row(
                    1,
                    decision="ACCEPT",
                    filter_result_id=11,
                    filter_name="teralizer.processing.filter.NonPassingTestFilter",
                ),
                _row(
                    2,
                    decision="REJECT",
                    filter_result_id=12,
                    filter_name="teralizer.processing.filter.NonPassingTestFilter",
                ),
                _row(3, exclusion_info="GenerateTestsTask: failed"),
                _row(4),
            ]
        )
    )


def test_filtering_summary_uses_only_explicit_filter_results():
    observations = _observations()
    assert observations.to_dict("records") == [
        {"generalization_id": 1, "outcome": "included"},
        {"generalization_id": 2, "outcome": "excluded"},
        {"generalization_id": 3, "outcome": "pre_filter_failure"},
        {"generalization_id": 4, "outcome": "unknown"},
    ]

    summary = filtering.summarize_filtering(observations)
    assert (summary.total, summary.included, summary.excluded) == (2, 1, 1)
    assert float(summary.included_share) == pytest.approx(0.5)


def test_later_exclusion_does_not_override_a_filtering_result():
    observations = filtering._classify_rows(
        pd.DataFrame(
            [
                _row(
                    1,
                    decision="ACCEPT",
                    exclusion_info="GeneratedTestExecutionTask: failed",
                    filter_result_id=11,
                    filter_name="teralizer.processing.filter.NonPassingTestFilter",
                )
            ]
        )
    )
    assert observations.iloc[0].to_dict() == {
        "generalization_id": 1,
        "outcome": "included",
    }


@pytest.mark.parametrize(
    ("rows", "message"),
    [
        (
            [
                _row(
                    1,
                    decision="ACCEPT",
                    filter_result_id=11,
                    filter_name="teralizer.processing.filter.NonPassingTestFilter",
                ),
                _row(
                    1,
                    decision="REJECT",
                    filter_result_id=12,
                    filter_name="teralizer.processing.filter.NonPassingTestFilter",
                ),
            ],
            "duplicate filtering evidence",
        ),
        (
            [
                _row(
                    1,
                    decision="DEFER",
                    filter_result_id=11,
                    filter_name="teralizer.processing.filter.NonPassingTestFilter",
                )
            ],
            "unsupported filtering decision",
        ),
        (
            [
                _row(
                    1,
                    decision="ACCEPT",
                    filter_result_id=11,
                    filter_name="GeneratedTestValidator",
                )
            ],
            "unsupported filtering producer",
        ),
        (
            [
                _row(1, exclusion_info="First failure"),
                _row(1, exclusion_info="Second failure"),
            ],
            "contradictory exclusion evidence",
        ),
        ([_row(1), _row(2)], "must contain at least one"),
    ],
)
def test_invalid_filtering_evidence_fails(rows, message):
    with pytest.raises(exclusion.ExclusionEvidenceError, match=message):
        observations = filtering._classify_rows(pd.DataFrame(rows))
        filtering.summarize_filtering(observations)


def test_filtering_summary_rejects_nonconserving_counts():
    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="filtering counts do not conserve",
    ):
        filtering.FilteringSummary(3, 1, 1)


def test_duplicate_observation_id_fails_before_summary():
    observations = pd.DataFrame(
        [
            {"generalization_id": 1, "outcome": "included"},
            {"generalization_id": 1, "outcome": "excluded"},
        ]
    )
    with pytest.raises(
        exclusion.ExclusionEvidenceError,
        match="duplicate generalized-test identities",
    ):
        filtering.summarize_filtering(observations)


def test_filtering_table_preserves_separate_denominators():
    table = filtering.build_filtering_comparison_table(
        filtering.FilteringSummary(4, 3, 1),
        filtering.FilteringSummary(5, 4, 1),
        None,
    )
    rows = table.df.set_index("dataset_key")
    assert rows.loc["controlled", ["total", "included", "excluded"]].tolist() == [
        4,
        3,
        1,
    ]
    assert rows.loc["realworld", ["total", "included", "excluded"]].tolist() == [
        5,
        4,
        1,
    ]
    assert float(rows.loc["controlled", "included_share"]) == pytest.approx(0.75)
    assert float(rows.loc["realworld", "included_share"]) == pytest.approx(0.8)
    assert table.note is not None
    assert "that dataset" in table.note
