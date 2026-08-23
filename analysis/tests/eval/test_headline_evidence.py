from pathlib import Path

import pytest
import sqlalchemy.exc

from teralizer.eval.artifacts import ArtifactSet
from teralizer.eval.model import BuiltReport, RQReport
from teralizer.eval.render.latex import macro_name, render_macros
from teralizer.eval.render.manifest import build_manifest
from teralizer.eval.reports.rq1_mutation_score import EFFECTIVENESS_METRIC_KEYS
from teralizer.eval.reports.rq6_causes import FILTERING_METRIC_KEYS


HEADLINE_KEYS_BY_DIMENSION = {
    "effectiveness": EFFECTIVENESS_METRIC_KEYS,
    "applicability": frozenset(
        {
            "rq0.breadth.published_projects",
            "rq0.census.intended_projects",
            "rq0.census.populated_projects",
            "realworld.eligible_projects",
            "realworld.applicability_projects",
            "realworld.applicability_pct",
        }
    ),
    "demonstrated_output": frozenset(
        {
            "realworld.generalizations_final_usable",
            "realworld.final_usable_projects",
        }
    ),
    "mechanism_insight": frozenset(
        {
            "realworld.assertions_total",
            "realworld.assertions_included",
            "realworld.assertions_included_pct",
            "realworld.generalization_attempts",
            "realworld.widening_refusals",
            "realworld.widening_refusals_pct",
        }
    ),
}
HEADLINE_METRIC_KEYS = frozenset().union(*HEADLINE_KEYS_BY_DIMENSION.values())


def _reports(build_report) -> tuple[RQReport, ...]:
    try:
        return tuple(build_report(report_id) for report_id in ("rq0", "rq1", "rq6"))
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_headline_metrics_are_unique_in_macros_and_provenance(
    build_report, tmp_path: Path
):
    reports = _reports(build_report)
    metrics = {
        metric.key: metric
        for report in reports
        for metric in report.metrics
        if metric.key in HEADLINE_METRIC_KEYS
    }
    assert set(metrics) == HEADLINE_METRIC_KEYS
    assert all(metric.kind is not None for metric in metrics.values())
    assert all(metric.population is not None for metric in metrics.values())
    assert all(metric.provenance is not None for metric in metrics.values())

    macros = "".join(render_macros(report) for report in reports)
    for key in HEADLINE_METRIC_KEYS:
        assert macros.count(f"\\newcommand{{\\{macro_name(key)}}}") == 1

    manifest_keys: list[str] = []
    for report in reports:
        manifest = build_manifest(
            BuiltReport(report, ()),
            ArtifactSet(tmp_path),
            repo_url="https://example.invalid",
        )
        manifest_keys.extend(
            key for key in manifest["metrics"] if key in HEADLINE_METRIC_KEYS
        )
    assert len(manifest_keys) == len(HEADLINE_METRIC_KEYS)
    assert set(manifest_keys) == HEADLINE_METRIC_KEYS


def test_filtering_metrics_publish_once_without_becoming_headlines(
    build_report, tmp_path: Path
):
    report = build_report("rq6")
    macros = render_macros(report)
    manifest = build_manifest(
        BuiltReport(report, ()),
        ArtifactSet(tmp_path),
        repo_url="https://example.invalid",
    )
    for key in FILTERING_METRIC_KEYS:
        assert macros.count(f"\\newcommand{{\\{macro_name(key)}}}") == 1
        assert list(manifest["metrics"]).count(key) == 1
    assert HEADLINE_METRIC_KEYS.isdisjoint(FILTERING_METRIC_KEYS)


def test_headline_selection_excludes_incomparable_or_composite_rates():
    assert HEADLINE_METRIC_KEYS.isdisjoint(FILTERING_METRIC_KEYS)
    forbidden_fragments = (
        "controlled_comparison",
        "cross_corpus",
        "applicability_score",
        "composite",
    )
    assert not any(
        fragment in key
        for key in HEADLINE_METRIC_KEYS
        for fragment in forbidden_fragments
    )
    assert set(HEADLINE_KEYS_BY_DIMENSION) == {
        "effectiveness",
        "applicability",
        "demonstrated_output",
        "mechanism_insight",
    }
