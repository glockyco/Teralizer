import pytest
from sqlalchemy import text

from teralizer.eval.inputs import CorpusInputSpec
from teralizer.eval.reports import _funnel

from teralizer.eval.registry import get
from teralizer.eval.reports import rq6_causes  # noqa: F401  (registers "rq6")
from teralizer.eval.reports._causes_common import MECHANISM_COLLAPSE


def test_rq6_has_funnel_and_shared_tables(rq6_report):
    report = rq6_report
    assert report.rq == "rq6"
    input_spec = get("rq6").inputs[0]
    assert isinstance(input_spec, CorpusInputSpec)
    assert (input_spec.role, input_spec.corpus_id) == ("real-world", "real-world")
    labels = {t.label for t in report.tables()}
    assert any("processing-failures" in lbl for lbl in labels)
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)
    assert "tab:jpf-exception-causes" in labels
    assert "tab:mut-choice-sensitivity" in labels
    assert {table.key for table in report.tables()} == {
        "tab-processing-failures",
        "rq6_generalization_funnel",
        "tab-exclusions-breakdown-extended",
        "tab-exclusions-filtering-extended",
        "rq6_jpf_exception_causes",
        "rq6_mut_choice_sensitivity",
        "rq6_widening_refusals",
    }
    choice = next(
        table
        for table in report.tables()
        if table.label == "tab:mut-choice-sensitivity"
    )
    counts = choice.df.set_index("category")["count"]
    total = int(counts.sum())
    assert counts["Candidate detail unavailable"] > 0
    assert float(
        report.metric("realworld.parameter_type_choice_dependent_lower_bound_pct").value
    ) == pytest.approx(counts["Choice-dependent"] / total)


def test_rq6_stage_metrics_chain_the_funnel(rq6_report, funnel_result):
    """Each stage macro must match its band, and the bands must chain.

    A stage's included count is the next stage's entering count. Without this,
    prose can cite two macros that describe incompatible funnels.
    """
    report = rq6_report
    slugs = [rq6_causes._stage_slug(band.stage) for band in funnel_result.stages]
    assert slugs == ["1_2", "3", "4", "5"]
    for band, slug in zip(funnel_result.stages, slugs):
        assert report.metric(f"realworld.stage_{slug}.entering").value == band.entering
        assert report.metric(f"realworld.stage_{slug}.included").value == band.passing
        assert (
            report.metric(f"realworld.stage_{slug}.excluded").value == band.exclusions
        )
        assert float(
            report.metric(f"realworld.stage_{slug}.included_pct").value
        ) == pytest.approx(band.passing / band.entering)
    for earlier, later in zip(slugs, slugs[1:]):
        assert (
            report.metric(f"realworld.stage_{earlier}.included").value
            == report.metric(f"realworld.stage_{later}.entering").value
        )
    assert (
        report.metric("realworld.stage_1_2.entering").value
        == report.metric("realworld.eligible_projects").value
    )
    assert (
        report.metric("realworld.stage_5.included").value
        == report.metric("realworld.applicability_projects").value
    )


def test_rq6_eligibility_partition_is_complete(rq6_report):
    selected = int(rq6_report.metric("realworld.selected_projects").value)
    initial_gate = int(
        rq6_report.metric("realworld.initial_gate_excluded_projects").value
    )
    no_executed_test = int(
        rq6_report.metric("realworld.no_executed_test_excluded_projects").value
    )
    eligible = int(rq6_report.metric("realworld.eligible_projects").value)

    assert selected == initial_gate + no_executed_test + eligible


def test_rq6_funnel_causes_are_typed(rq6_report):
    report = rq6_report
    funnel = next(t for t in report.tables() if "processing-failures" in t.label)
    assert set(funnel.df["type"]) <= {"Internal", "External", "Mixed"}


def test_rq6_breakdown_conservation(rq6_report):
    report = rq6_report
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    # The rendered table is the reader-facing collapse. The per-mechanism
    # partition it folds is asserted in test_rq6_invariants.py.
    reconstructed = breakdown.df[list(MECHANISM_COLLAPSE)].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()


def test_rq6_breakdown_matches_eligible_entity_denominators(rq6_report, rq6_conn):
    report = rq6_report
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    with rq6_conn.begin_nested():
        conn = rq6_conn
        variant = _funnel.resolve_variant(conn)
        counts = conn.execute(
            text(
                """
                WITH eligible AS (
                    SELECT p.id
                    FROM project p
                    WHERE p.use_test_generalization
                      AND NOT EXISTS (
                          SELECT 1
                          FROM task t
                          WHERE t.project_id = p.id
                            AND t.test_id IS NULL
                            AND t.assertion_id IS NULL
                            AND t.generalization_id IS NULL
                            AND t.status <> 'SUCCEEDED'
                            AND t.stage IN (
                                'SETUP_PROJECT',
                                'ADD_DEPENDENCIES',
                                'BUILD_PROJECT_ORIGINAL'
                            )
                      )
                )
                SELECT
                    (SELECT count(*) FROM test t JOIN eligible e ON e.id = t.project_id),
                    (SELECT count(*) FROM assertion a JOIN eligible e ON e.id = a.project_id),
                    (
                        SELECT count(*)
                        FROM generalization g JOIN eligible e ON e.id = g.project_id
                        WHERE g.variant = :variant
                    )
                """
            ),
            {"variant": variant},
        ).one()
    observed = breakdown.df.set_index("level")["total"]
    assert observed["Test"] == counts[0]
    assert observed["Assertion"] == counts[1]
    assert observed["Generalization"] == counts[2]


def test_rq6_generalization_inclusion_uses_validated_signal(rq6_report, rq6_conn):
    report = rq6_report
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    row = breakdown.df[breakdown.df["level"].eq("Generalization")].iloc[0]
    with rq6_conn.begin_nested():
        conn = rq6_conn
        variant = _funnel.resolve_variant(conn)
        validated = conn.execute(
            text(
                """
                SELECT count(*)
                FROM generalization g
                JOIN generalization_lifecycle l ON l.generalization_id = g.id
                WHERE g.variant = :variant AND l.generated_filter_passed
                """
            ),
            {"variant": variant},
        ).scalar_one()
    assert row["included"] == validated


def test_rq6_generalization_reduction_attrition_stays_included(rq6_report, rq6_conn):
    # A reduction-dependent inclusion signal would push Stage-5 PIT losses into an
    # exclusion column. Inclusion must key on the generation-side filter verdict.
    report = rq6_report
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    row = breakdown.df[breakdown.df["level"].eq("Generalization")].iloc[0]
    with rq6_conn.begin_nested():
        conn = rq6_conn
        variant = _funnel.resolve_variant(conn)
        filter_passed = conn.execute(
            text(
                """
                WITH eligible AS (
                    SELECT p.id
                    FROM project p
                    WHERE p.use_test_generalization
                      AND NOT EXISTS (
                          SELECT 1 FROM task t
                          WHERE t.project_id = p.id
                            AND t.test_id IS NULL
                            AND t.assertion_id IS NULL
                            AND t.generalization_id IS NULL
                            AND t.status <> 'SUCCEEDED'
                            AND t.stage IN (
                                'SETUP_PROJECT',
                                'ADD_DEPENDENCIES',
                                'BUILD_PROJECT_ORIGINAL'
                            )
                      )
                )
                SELECT count(*)
                FROM generalization g
                JOIN eligible e ON e.id = g.project_id
                JOIN generalization_lifecycle l ON l.generalization_id = g.id
                WHERE g.variant = :variant
                  AND l.generated_filter_passed
                """
            ),
            {"variant": variant},
        ).scalar_one()
    # Inclusion keys on the filter verdict, not on final_usable, so a generalized
    # test that validated and then lost PIT collection stays included.
    assert row["included"] == filter_passed


def test_rq6_filtering_table_is_entity_conservative(rq6_report):
    report = rq6_report
    filtering = next(t for t in report.tables() if "exclusions-filtering" in t.label)
    assert "Generalization" in set(filtering.df["level"])
    reconstructed = filtering.df[["accept", "defer", "reject"]].sum(axis=1)
    assert (reconstructed == filtering.df["total"]).all()


def test_rq6_every_assertion_carries_resolver_telemetry(rq6_report):
    # MethodUnderTestResolver.resolve runs unconditionally before the assertion
    # row is stored and MutResolution is total, so a missing observation row is
    # a persistence defect, never a category.
    report = rq6_report
    assert int(report.metric("realworld.assertions_without_resolution").value) == 0


def test_rq6_metrics_cover_applicability_and_are_well_formed(rq6_report):
    report = rq6_report
    eligible = int(report.metric("realworld.eligible_projects").value)
    assert eligible > 0

    for key in (
        "realworld.applicability_pct",
        "realworld.assertions_included_pct",
        "realworld.generalization_validated_pct",
    ):
        metric = report.metric(key)
        assert metric.fmt == "pct1"
        assert 0.0 <= float(metric.value) <= 1.0

    applicable = int(report.metric("realworld.applicability_projects").value)
    assert 0 < applicable <= eligible

    included = int(report.metric("realworld.assertions_included").value)
    assert included < int(report.metric("realworld.assertions_total").value)
    validated = int(report.metric("realworld.generalizations_validated").value)
    assert validated < int(report.metric("realworld.generalization_attempts").value)

    # The headline rate is the share of eligible projects completing every stage.
    eligible_projects = int(report.metric("realworld.eligible_projects").value)
    assert applicable == int(report.metric("realworld.applicability_projects").value)
    assert float(report.metric("realworld.applicability_pct").value) == pytest.approx(
        applicable / eligible_projects
    )


def test_rq6_applicability_is_measured_after_reduction(rq6_report):
    report = rq6_report
    stage4 = int(report.metric("realworld.stage4_projects").value)
    excluded = int(report.metric("realworld.reduction_excluded_projects").value)
    baseline_side = int(
        report.metric("realworld.reduction_excluded_baseline_side").value
    )
    applicability = int(report.metric("realworld.applicability_projects").value)
    # The headline counts projects through all five stages; the Stage-4 figure sits
    # beside it, and the two differ by exactly what reduction excluded.
    assert applicability == stage4 - excluded
    assert 0 < excluded < stage4
    assert 0 < baseline_side <= excluded
    assert not any(
        key.endswith("reduction_included") or key.endswith("reduction_inclusion_pct")
        for key in (metric.key for metric in report.metrics)
    )
