import pandas as pd
from teralizer.eval.model import (
    ColumnSpec,
    Figure,
    Metric,
    Prose,
    RQReport,
    Section,
    Table,
)


def test_rqreport_collects_metrics_by_key():
    m = Metric(key="realworld.eligible_projects", value=632, fmt="int")
    report = RQReport(
        rq="rq6",
        title="RQ6",
        db="postgres_reporeapers_rq6",
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


def test_table_and_figure_are_frozen_and_carry_keys():
    t = Table(
        key="funnel",
        df=pd.DataFrame({"a": [1]}),
        columns=[ColumnSpec(header="A", source="a", fmt="int")],
        caption="Funnel",
        label="tab:rq6-funnel",
    )
    f = Figure(key="bar", build=lambda ax: None, caption="Bar", label="fig:rq6-bar")
    assert t.key == "funnel" and f.label == "fig:rq6-bar"
    # frozen: mutation raises
    import dataclasses
    import pytest

    with pytest.raises(dataclasses.FrozenInstanceError):
        t.caption = "x"  # type: ignore[misc]
