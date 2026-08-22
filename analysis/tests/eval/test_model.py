import pandas as pd
import pytest

from teralizer.eval.model import (
    ColumnSpec,
    Figure,
    Metric,
    MetricPopulation,
    Prose,
    RQReport,
    Section,
    Table,
    ValueKind,
)


def test_rqreport_collects_metrics_by_key():
    m = Metric(key="realworld.eligible_projects", value=632, fmt="int")
    report = RQReport(
        rq="rq6",
        title="RQ6",
        sections=[
            Section(
                title="Overview",
                blocks=[Prose("Eligible: {realworld.eligible_projects}.")],
            )
        ],
        metrics=[m],
    )
    assert report.metric("realworld.eligible_projects").value == 632
    assert report.metric_map()["realworld.eligible_projects"] is m


def test_metric_rate_resolves_compatible_operands():
    attempted = MetricPopulation("attempted", "Generalization", "real-world")
    validated = MetricPopulation("validated", "Generalization", "real-world")
    report = RQReport(
        rq="rq6",
        title="RQ6",
        sections=[],
        metrics=[
            Metric("attempts", 10, kind=ValueKind.COUNT, population=attempted),
            Metric("validated", 4, kind=ValueKind.COUNT, population=validated),
            Metric(
                "validated_pct",
                0.4,
                kind=ValueKind.SHARE,
                population=validated,
                numerator_key="validated",
                denominator_key="attempts",
            ),
        ],
    )
    report.validate_metric_relations()


def test_metric_rate_rejects_incompatible_population():
    projects = MetricPopulation("projects", "Project", "real-world")
    assertions = MetricPopulation("assertions", "Assertion", "real-world")
    report = RQReport(
        rq="rq6",
        title="RQ6",
        sections=[],
        metrics=[
            Metric("projects", 10, kind=ValueKind.COUNT, population=projects),
            Metric("assertions", 4, kind=ValueKind.COUNT, population=assertions),
            Metric(
                "bad_rate",
                0.4,
                kind=ValueKind.SHARE,
                population=assertions,
                numerator_key="assertions",
                denominator_key="projects",
            ),
        ],
    )
    with pytest.raises(ValueError, match="incompatible denominator projects"):
        report.validate_metric_relations()


def test_table_and_figure_are_frozen_and_carry_keys():
    t = Table(
        key="funnel",
        df=pd.DataFrame({"a": [1]}),
        columns=[ColumnSpec(header="A", source="a", kind=ValueKind.COUNT)],
        caption="Funnel",
        label="tab:rq6-funnel",
    )
    f = Figure(key="bar", build=lambda ax: None, caption="Bar", label="fig:rq6-bar")
    assert t.key == "funnel" and f.label == "fig:rq6-bar"
    # frozen: mutation raises
    import dataclasses

    with pytest.raises(dataclasses.FrozenInstanceError):
        t.caption = "x"  # type: ignore[misc]
