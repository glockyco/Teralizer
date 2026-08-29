from copy import deepcopy
from dataclasses import replace
from pathlib import Path

import pytest
from sqlalchemy import text

from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec
from teralizer.eval.macros import macro_name
from teralizer.eval.model import BuiltReport, ValueKind
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel

from teralizer.eval.registry import get
from teralizer.eval.render.latex import render_macros
from teralizer.eval.render.markdown import render_str
from teralizer.eval.reports import rq6_causes  # noqa: F401  (registers "rq6")
from teralizer.eval.reports._causes_common import MECHANISM_COLLAPSE


def test_pit_coverage_persistence_diagnostic_names_failed_operation():
    message = (
        'SQL [insert into "public"."pit_coverage_report" '
        '("project_id", "covered_method_name") values (?, ?)]'
    )

    assert (
        _funnel._normalize_task_reason("LISTENER_BUG", message)
        == "PIT_REPORT_PERSISTENCE_FAILURE"
    )
    assert _funnel._normalize_task_reason("LISTENER_BUG", "listener failed") == (
        "LISTENER_BUG"
    )


def test_rq6_has_funnel_and_shared_tables(rq6_report):
    report = rq6_report
    assert report.rq == "rq6"
    input_specs = get("rq6").inputs
    corpus_inputs = [
        input_spec
        for input_spec in input_specs
        if isinstance(input_spec, CorpusInputSpec)
    ]
    file_inputs = [
        input_spec
        for input_spec in input_specs
        if isinstance(input_spec, FileInputSpec)
    ]
    assert [
        (input_spec.role, input_spec.corpus_id) for input_spec in corpus_inputs
    ] == [
        ("real-world", "real-world"),
        ("controlled", "controlled"),
    ]
    assert [(input_spec.role, input_spec.path) for input_spec in file_inputs] == [
        (
            "reconstruction-audit",
            "analysis/data/report-inputs/reporeapers-reconstruction-audit.json",
        ),
        (
            "reconstruction-inventory",
            "analysis/data/report-inputs/reporeapers-reconstruction-inventory.json",
        ),
        (
            "output-directory-population",
            "analysis/data/report-inputs/reporeapers-output-directories-population.json",
        ),
    ]
    labels = {t.label for t in report.tables()}
    assert any("processing-failures" in lbl for lbl in labels)
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)
    assert "tab:jpf-exception-causes" in labels
    assert "tab:mut-choice-sensitivity" in labels
    assert {table.key for table in report.tables()} == {
        "tab-processing-failures",
        "rq6_generalization_funnel",
        "rq6_filtering_comparison",
        "rq6_exclusion_mechanisms",
        "tab-exclusions-breakdown-extended",
        "tab-exclusions-filtering-extended",
        "rq6_jpf_exception_causes",
        "rq6_mut_choice_sensitivity",
        "rq6_widening_refusals",
        "rq6_reconstruction_summary",
        "rq6_reconstruction_outcomes",
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


def test_rq6_publishes_reconstructed_evidence_without_exact_rates(rq6_report):
    summary = next(
        table
        for table in rq6_report.tables()
        if table.key == "rq6_reconstruction_summary"
    )
    outcomes = next(
        table
        for table in rq6_report.tables()
        if table.key == "rq6_reconstruction_outcomes"
    )

    claims = summary.df.set_index("claim_key")
    assert claims.loc["no-assertions", "resolved"] == 100
    assert claims.loc["no-assertions", "unresolved"] == 24_166
    assert claims.loc["no-assertions", "unreviewed_population"] == 24_166
    assert claims.loc["assertion-to-mut", "unreviewed_population"] == 180_448
    assert claims.loc["output-directories", "unreviewed_population"] == 0
    assert "10.53% genuine absences" in claims.loc["no-assertions", "finding"]
    assert "95% normal CI" in claims.loc["no-assertions", "finding"]
    assert claims.loc["output-directories", "incompatible"] == 3
    assert {
        "supported-mapping",
        "contradicted-mapping",
        "insufficient-specification-evidence",
        "incompatible-evidence",
    } <= set(outcomes.df["outcome"])

    reconstruction_metrics = [
        metric
        for metric in rq6_report.metrics
        if metric.key.startswith("rq6.reconstruction.")
    ]
    assert len(reconstruction_metrics) == 22

    estimate_values = {
        "rq6.reconstruction.no_assertions.genuine_absence_estimate_pct": (
            10.532765813817491
        ),
        "rq6.reconstruction.no_assertions.genuine_absence_ci_lower_pct": (
            4.378705783971964
        ),
        "rq6.reconstruction.no_assertions.genuine_absence_ci_upper_pct": (
            16.686825843663016
        ),
    }
    for key, value in estimate_values.items():
        metric = rq6_report.metric(key)
        assert metric.value == pytest.approx(value)
        assert metric.kind is ValueKind.PERCENT
        assert metric.fmt == "percent2"
        assert metric.population.entity_level == "Test"
        assert metric.population.input_role == "reconstruction-audit"

    outcome_values = {
        "rq6.reconstruction.assertion_to_mut.reviewed_supported_mapping": 36,
        "rq6.reconstruction.assertion_to_mut.reviewed_contradicted_mapping": 34,
        "rq6.reconstruction.assertion_to_mut.reviewed_insufficient_specification_evidence": 30,
        "rq6.reconstruction.output_directories.default_directory_mismatch": 1,
        "rq6.reconstruction.output_directories.absent_artifact": 32,
        "rq6.reconstruction.output_directories.earlier_build_failure": 7,
        "rq6.reconstruction.output_directories.incompatible_evidence": 3,
    }
    for key, value in outcome_values.items():
        metric = rq6_report.metric(key)
        assert metric.value == value
        assert metric.kind is ValueKind.COUNT
        assert metric.population.input_role == "reconstruction-audit"

    tex = render_macros(rq6_report)
    for key in estimate_values | outcome_values:
        assert tex.count(f"\\newcommand{{\\{macro_name(key)}}}") == 1


def test_reconstruction_metrics_do_not_parse_claim_reason(rq6_report):
    audit = rq6_causes._load_reconstruction_audit(
        Path("data/report-inputs/reporeapers-reconstruction-audit.json")
    )
    claims = rq6_causes._reconstruction_claims(audit)
    outcome_counts = rq6_causes._reconstruction_outcome_counts(audit)
    provenance = rq6_report.metric(
        "rq6.reconstruction.no_assertions.genuine_absence_estimate_pct"
    ).provenance
    assert provenance is not None
    expected = [
        (metric.key, metric.value)
        for metric in rq6_causes._reconstruction_metrics(
            claims, outcome_counts, provenance
        )
    ]

    changed_claims = deepcopy(claims)
    for claim in changed_claims:
        claim["reason"] = "Narrative text changed without changing evidence."
    actual = [
        (metric.key, metric.value)
        for metric in rq6_causes._reconstruction_metrics(
            changed_claims, outcome_counts, provenance
        )
    ]

    assert actual == expected


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


def test_rq6_funnel_causes_use_reader_facing_categories(rq6_report):
    report = rq6_report
    funnel = next(t for t in report.tables() if "processing-failures" in t.label)
    assert list(funnel.df.columns) == ["stage", "cause", "count", "row_key"]
    assert [column.source for column in funnel.columns] == ["cause", "count"]
    assert list(
        funnel.df[["stage", "cause", "count"]].itertuples(index=False, name=None)
    ) == [
        (
            "1 + 2",
            "all tests or assertions excluded due to filter rejections",
            228,
        ),
        (
            "1 + 2",
            "JUnit execution of the {entity.variant.original} test suite exceeds 300 seconds",
            26,
        ),
        ("1 + 2", "JUnit test execution or Spoon test analysis fails", 21),
        ("1 + 2", "no test or assertion records collected", 11),
        ("1 + 2", "JUnit report collection fails", 5),
        ("3", "processing failures prevent specification extraction", 112),
        (
            "3",
            "no generalization attempt recorded despite retained assertions",
            2,
        ),
        (
            "4",
            "widening refusals contribute to exclusion of all generalization attempts",
            74,
        ),
        (
            "4",
            "filter rejections alone exclude all generalization attempts",
            4,
        ),
        (
            "4",
            "processing failures alone exclude all generalization attempts",
            3,
        ),
        (
            "5",
            "PIT mutation testing of the {entity.variant.initial} test suite exceeds 3,600 seconds",
            4,
        ),
        (
            "5",
            "{entity.variant.improved_c} test suite has failing tests before mutation",
            3,
        ),
        (
            "5",
            "{entity.variant.initial} test suite has failing tests before mutation",
            3,
        ),
        (
            "5",
            "required PIT reports or JaCoCo outputs unavailable for the {entity.variant.initial} test suite",
            3,
        ),
    ]


def test_rq6_breakdown_conservation(rq6_report):
    report = rq6_report
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    # The rendered table is the reader-facing collapse. The per-mechanism
    # partition it folds is asserted in test_rq6_invariants.py.
    reconstructed = breakdown.df[list(MECHANISM_COLLAPSE)].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()


def test_rq6_mechanism_rows_have_stable_keys_and_denominators(rq6_report):
    table = next(
        table
        for table in rq6_report.tables()
        if table.key == "rq6_exclusion_mechanisms"
    )
    assert table.row_key == "row_key"
    assert table.df["row_key"].is_unique
    assert set(table.df["mechanism"]) <= {
        mechanism.key.value for mechanism in exclusion.MECHANISMS
    }
    for _, rows in table.df.groupby("level"):
        assert int(rows["entity_count"].sum()) == int(rows["level_total"].iloc[0])
        assert float(rows["share"].sum()) == pytest.approx(1.0)


def test_rq6_mechanism_table_renders_text_identities(rq6_report):
    markdown = render_str(
        BuiltReport(rq6_report, ()), repo_url="https://example.invalid"
    )
    assert "| Test | Included | included |" in markdown
    assert "| Generalization | Generation-time gate | filtering |" in markdown


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
    breakdown = next(
        table
        for table in report.tables()
        if table.key == "tab-exclusions-breakdown-extended"
    )
    filtering = next(t for t in report.tables() if "exclusions-filtering" in t.label)
    assert breakdown.float_spec == "!t"
    assert filtering.float_spec == "!t"
    assert filtering.latex_resize_to_width
    assert not filtering.full_width
    assert filtering.body_style == "\\tabstyle\n\\renewcommand{\\arraystretch}{1.1}"
    assert [column.header for column in filtering.columns] == [
        "Level",
        "Filter Name",
        "Evaluated",
        "Accept",
        "Defer",
        "Reject",
    ]
    assert filtering.df.loc[
        filtering.df["level"] == "Generalization", "filter"
    ].tolist() == ["SeedSpecConsistency", "WideningLicense", "NonPassingTest"]
    inherited = filtering.df.loc[filtering.df["filter"] == "InheritedTestMethod"]
    assert inherited["_filter_group"].tolist() == [0]
    reconstructed = filtering.df[["accept", "defer", "reject"]].sum(axis=1)
    assert (reconstructed == filtering.df["total"]).all()


def test_rq6_test_filtering_flow_reconciles_persisted_populations(rq6_report):
    report = rq6_report
    values = {
        suffix: int(report.metric(f"realworld.test_filtering.{suffix}").value)
        for suffix in (
            "identified",
            "inherited_method_evaluated",
            "inherited_method_rejected",
            "pre_filter_failures",
            "round_one_evaluated",
            "round_one_rejected",
            "round_one_overlap",
            "inter_round_failures",
            "round_two_evaluated",
        )
    }
    assert values == {
        "identified": 85_368,
        "inherited_method_evaluated": 6_259,
        "inherited_method_rejected": 2_835,
        "pre_filter_failures": 88,
        "round_one_evaluated": 82_445,
        "round_one_rejected": 8_782,
        "round_one_overlap": 82,
        "inter_round_failures": 1,
        "round_two_evaluated": 73_662,
    }
    assert values["round_one_evaluated"] == (
        values["identified"]
        - values["inherited_method_rejected"]
        - values["pre_filter_failures"]
    )
    assert values["round_two_evaluated"] == (
        values["round_one_evaluated"]
        - values["round_one_rejected"]
        - values["inter_round_failures"]
    )

    filtering = next(
        table
        for table in report.tables()
        if table.key == "tab-exclusions-filtering-extended"
    )
    first_round = filtering.df.loc[
        (filtering.df["level"] == "Test")
        & filtering.df["filter"].isin(exclusion.FIRST_ROUND_TEST_FILTERS)
    ]
    assert values["round_one_rejected"] == (
        int(first_round["reject"].sum()) - values["round_one_overlap"]
    )


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


def test_rq6_final_usable_output_matches_applicable_projects(rq6_report):
    final_usable = rq6_report.metric("realworld.generalizations_final_usable")
    final_usable_projects = rq6_report.metric("realworld.final_usable_projects")
    applicable_projects = rq6_report.metric("realworld.applicability_projects")

    assert int(final_usable.value) > int(final_usable_projects.value)
    assert final_usable.kind is ValueKind.COUNT
    assert final_usable.population.entity_level == "Generalization"
    assert final_usable_projects.kind is ValueKind.COUNT
    assert final_usable_projects.population.entity_level == "Project"
    assert final_usable_projects.value == applicable_projects.value
    tex = render_macros(rq6_report)
    assert "\\TzRealworldGeneralizationsFinalUsable" in tex
    assert "\\TzRealworldFinalUsableProjects" in tex


def test_rq6_rejects_disagreeing_final_usable_projects():
    with pytest.raises(
        ValueError,
        match=r"only_final_usable=\[3\], only_applicable=\[2\]",
    ):
        rq6_causes._validate_final_usable_projects(frozenset({1, 3}), frozenset({1, 2}))


def test_rq6_widening_headline_uses_all_attempts(rq6_report):
    refusals = rq6_report.metric("realworld.widening_refusals")
    attempts = rq6_report.metric("realworld.generalization_attempts")
    rate = rq6_report.metric("realworld.widening_refusals_pct")

    assert rate.kind is ValueKind.SHARE
    assert rate.population == refusals.population
    assert rate.numerator_key == refusals.key
    assert rate.denominator_key == attempts.key
    assert float(rate.value) == pytest.approx(int(refusals.value) / int(attempts.value))
    assert int(refusals.value) + (int(attempts.value) - int(refusals.value)) == int(
        attempts.value
    )
    tex = render_macros(rq6_report)
    assert "\\TzRealworldAssertionsIncluded" in tex
    assert "\\TzRealworldAssertionsIncludedPct" in tex
    assert "\\TzRealworldWideningRefusals" in tex
    assert "\\TzRealworldWideningRefusalsPct" in tex


def test_rq6_filtering_comparison_preserves_corpus_local_denominators(rq6_report):
    report = rq6_report
    expected = {
        "controlled": (13_804, 11_597, 2_207),
        "realworld": (2_035, 1_615, 420),
    }
    for dataset, (total, included, excluded) in expected.items():
        prefix = f"rq6.filtering.{dataset}"
        total_metric = report.metric(f"{prefix}.total")
        included_metric = report.metric(f"{prefix}.included")
        excluded_metric = report.metric(f"{prefix}.excluded")
        rate = report.metric(f"{prefix}.included_pct")
        assert (total_metric.value, included_metric.value, excluded_metric.value) == (
            total,
            included,
            excluded,
        )
        assert included + excluded == total
        assert rate.numerator_key == included_metric.key
        assert rate.denominator_key == total_metric.key
        assert rate.population == included_metric.population
        assert float(rate.value) == pytest.approx(included / total)

    table = next(
        table for table in report.tables() if table.key == "rq6_filtering_comparison"
    )
    assert table.df["dataset_key"].tolist() == ["controlled", "realworld"]
    assert table.df["dataset_key"].is_unique
    tex = render_macros(report)
    for key in rq6_causes.FILTERING_METRIC_KEYS:
        assert tex.count(f"\\newcommand{{\\{macro_name(key)}}}") == 1


def test_rq6_metrics_have_population_denominator_and_provenance(rq6_report):
    rq6_report.validate_metric_relations(require_metadata=True)
    for metric in rq6_report.metrics:
        assert metric.kind is not None
        assert metric.population is not None
        assert metric.population.input_role in {
            "real-world",
            "controlled",
            "reconstruction-audit",
        }
        assert metric.provenance is not None
        if metric.fmt == "pct1":
            assert metric.numerator_key is not None
            assert metric.denominator_key is not None


def test_rq6_retained_consumer_validation_names_missing_metric(rq6_report):
    incomplete = replace(
        rq6_report,
        metrics=[
            metric
            for metric in rq6_report.metrics
            if metric.key != "realworld.generalizations_validated"
        ],
    )
    with pytest.raises(
        ValueError,
        match="RQ6 retained metrics are missing:.*generalizations_validated",
    ):
        rq6_causes.validate_retained_consumers(incomplete)


def test_removed_call_shape_taxonomy_has_no_replacement_output(rq6_report):
    assert not any("call_shape" in metric.key for metric in rq6_report.metrics)
    assert not any("call_shape" in table.key for table in rq6_report.tables())


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
