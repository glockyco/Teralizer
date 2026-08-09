import pytest
import sqlalchemy.exc
from sqlalchemy import text

from teralizer.eval.reports import _funnel

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
from teralizer.eval.reports import rq6_causes  # noqa: F401  (registers "rq6")


def _report() -> RQReport:
    spec = get("rq6")
    try:
        with connect(spec.default_db) as conn:
            return spec.build(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    raise AssertionError("unreachable: pytest.skip should have raised")


def test_rq6_has_funnel_and_shared_tables():
    report = _report()
    assert report.rq == "rq6"
    assert report.db == get("rq6").default_db
    labels = {t.label for t in report.tables()}
    assert any("processing-failures" in lbl for lbl in labels)
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)
    assert "tab:jpf-exception-causes" in labels


@pytest.mark.parametrize(
    ("detail", "expected"),
    [
        (
            {
                "message": "gov.nasa.jpf.vm.NoUncaughtExceptionsProperty\n"
                "java.lang.NullPointerException: application state"
            },
            "Application exception",
        ),
        (
            {
                "message": "Caused by: java.lang.IllegalStateException: peer failed\n"
                "at gov.nasa.jpf.vm.JPF_java_lang_Class"
            },
            "JPF native-peer gap",
        ),
        (
            '{"message":"Caused by: java.lang.NoSuchFieldException: value"}',
            "JPF model/field gap",
        ),
        ({"message": "no exception type retained"}, "Unparsed"),
    ],
)
def test_retained_jpf_details_recover_concrete_causes(detail, expected):
    assert rq6_causes._classify_jpf_exception_detail(detail) == expected


def test_rq6_funnel_causes_are_typed():
    report = _report()
    funnel = next(t for t in report.tables() if "processing-failures" in t.label)
    assert set(funnel.df["type"]) <= {"Internal", "External", "Mixed"}


def test_rq6_breakdown_conservation():
    report = _report()
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    reconstructed = breakdown.df[["included", "filtering", "failures"]].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()


def test_rq6_breakdown_matches_eligible_entity_denominators():
    report = _report()
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    with connect(get("rq6").default_db) as conn:
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


def test_rq6_generalization_inclusion_uses_validated_signal():
    report = _report()
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    row = breakdown.df[breakdown.df["level"].eq("Generalization")].iloc[0]
    with connect(get("rq6").default_db) as conn:
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


def test_rq6_generalization_failures_exclude_reduction_attrition():
    # A reduction-dependent inclusion signal would push PIT collection losses into
    # the failures column. Reconstruct only eligible creation failures: outputs that
    # did not validate and were not rejected by a generation filter.
    report = _report()
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    row = breakdown.df[breakdown.df["level"].eq("Generalization")].iloc[0]
    with connect(get("rq6").default_db) as conn:
        variant = _funnel.resolve_variant(conn)
        creation_failures = conn.execute(
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
                  AND NOT l.generated_filter_passed
                  AND NOT (
                      NOT g.is_included
                      AND (
                          g.exclusion_info IN (
                              'ORACLE_NOT_WIDENABLE',
                              'INPUT_SPEC_NOT_SATISFIED_BY_SEED'
                          )
                          OR EXISTS (
                              SELECT 1 FROM filter_result fr
                              WHERE fr.generalization_id = g.id
                                AND fr.decision = 'REJECT'
                          )
                      )
                  )
                """
            ),
            {"variant": variant},
        ).scalar_one()
    assert row["failures"] == creation_failures


def test_rq6_filtering_table_is_entity_conservative():
    report = _report()
    filtering = next(t for t in report.tables() if "exclusions-filtering" in t.label)
    assert "Generalization" in set(filtering.df["level"])
    reconstructed = filtering.df[["accept", "defer", "reject"]].sum(axis=1)
    assert (reconstructed == filtering.df["total"]).all()


def test_rq6_every_assertion_carries_resolver_telemetry():
    # MethodUnderTestResolver.resolve runs unconditionally before the assertion
    # row is stored and MutResolution is total, so a missing observation row is
    # a persistence defect, never a category.
    report = _report()
    assert int(report.metric("realworld.assertions_without_resolution").value) == 0


def test_rq6_metrics_cover_applicability_and_are_well_formed():
    report = _report()
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


def test_rq6_applicability_is_measured_after_reduction():
    report = _report()
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
