import pytest
import sqlalchemy.exc
from sqlalchemy import text

from teralizer.eval.reports import _funnel

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
import teralizer.eval.reports.rq6_causes  # noqa: F401  (registers "rq6")


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


def test_rq6_generalization_inclusion_uses_final_usable():
    report = _report()
    breakdown = next(t for t in report.tables() if "exclusions-breakdown" in t.label)
    row = breakdown.df[breakdown.df["level"].eq("Generalization")].iloc[0]
    with connect(get("rq6").default_db) as conn:
        variant = _funnel.resolve_variant(conn)
        final_usable = conn.execute(
            text(
                """
                SELECT count(*)
                FROM generalization g
                JOIN generalization_lifecycle l ON l.generalization_id = g.id
                WHERE g.variant = :variant AND l.final_usable
                """
            ),
            {"variant": variant},
        ).scalar_one()
    assert row["included"] == final_usable


def test_rq6_filtering_table_is_entity_conservative():
    report = _report()
    filtering = next(t for t in report.tables() if "exclusions-filtering" in t.label)
    assert "Generalization" in set(filtering.df["level"])
    reconstructed = filtering.df[["accept", "defer", "reject"]].sum(axis=1)
    assert (reconstructed == filtering.df["total"]).all()


def test_rq6_overall_inclusion_metric_is_a_fraction():
    report = _report()
    pct = report.metric("realworld.overall_inclusion_pct")
    assert pct.fmt == "pct1"
    assert 0.0 <= float(pct.value) <= 1.0
    eligible = report.metric("realworld.eligible_projects")
    assert int(eligible.value) > 0
