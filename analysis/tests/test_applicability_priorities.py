"""Offline tests for applicability-gap prioritization logic.

Uses in-memory sqlite via SQLAlchemy to test the shadowing-aware blocker
computation without needing the real PostgreSQL database. Mirrors the query
pattern (pd.read_sql + sqlalchemy text) used by the module under test.
"""

from typing import cast

import pandas as pd
import pytest
from sqlalchemy import create_engine, text

from teralizer.applicability_priorities import (
    compute_blocker_cooccurrence,
    compute_first_reject_blockers,
    compute_multi_blocker_rate,
    compute_projects_closest_to_completion,
    compute_missingvalue_taxonomy,
    get_assertion_filter_chain,
    get_missingvalue_assertions,
    get_project_summary,
)


def _create_schema(conn) -> None:
    """Create minimal project/assertion/filter_result tables for testing."""
    for stmt in (
        "CREATE TABLE project (id INTEGER PRIMARY KEY, root_path TEXT NOT NULL)",
        "CREATE TABLE assertion (id INTEGER PRIMARY KEY, project_id INTEGER NOT NULL, is_included BOOLEAN NOT NULL, assertion_name TEXT NOT NULL, assertion_source_code TEXT NOT NULL, tested_method_call_source_code TEXT)",
        "CREATE TABLE filter_result (id INTEGER PRIMARY KEY, project_id INTEGER NOT NULL, test_id INTEGER, assertion_id INTEGER, generalization_id INTEGER, filter_name TEXT NOT NULL, decision TEXT NOT NULL, reason TEXT NOT NULL)",
    ):
        conn.execute(text(stmt))


def _insert_fixture(conn) -> None:
    """Insert a known fixture with predictable shadowing structure.

    Assertion 1: REJECT by MissingValue (pos 1) then ParameterType (pos 2).
    Assertion 2: REJECT by ReturnType (pos 1) only.
    Assertion 3: REJECT by ParameterType (pos 1) only.
    Assertion 4: ACCEPT (no rejects) — included.
    """
    conn.execute(
        text(
            "INSERT INTO project (id, root_path) VALUES (1, '/data/github_com_example_proj')"
        )
    )
    conn.execute(
        text(
            "INSERT INTO assertion (id, project_id, is_included, assertion_name, assertion_source_code, tested_method_call_source_code) VALUES "
            "(1, 1, false, 'assertEquals', 'assertEquals(result.size(), 3)', 'result.size()'), "
            "(2, 1, false, 'assertEquals', 'assertEquals(obj.getValue(), 5)', 'obj.getValue()'), "
            "(3, 1, false, 'assertEquals', 'assertEquals(Utils.compute(x), 0)', 'Utils.compute(x)'), "
            "(4, 1, true, 'assertEquals', 'assertEquals(1, 1)', null)"
        )
    )
    conn.execute(
        text(
            "INSERT INTO filter_result (id, project_id, assertion_id, filter_name, decision, reason) VALUES "
            "(1, 1, 1, 'teralizer.processing.filter.MissingValueFilter', 'REJECT', 'The test.tested_class_path column is null.'), "
            "(2, 1, 1, 'teralizer.processing.filter.ParameterTypeFilter', 'REJECT', 'no generalizable types'), "
            "(3, 1, 2, 'teralizer.processing.filter.ReturnTypeFilter', 'REJECT', 'unsupported return type'), "
            "(4, 1, 3, 'teralizer.processing.filter.ParameterTypeFilter', 'REJECT', 'no parameters'), "
            "(5, 1, 4, 'teralizer.processing.filter.MissingValueFilter', 'ACCEPT', 'ok')"
        )
    )


@pytest.fixture
def conn():
    """In-memory sqlite SQLAlchemy connection with the fixture loaded."""
    engine = create_engine("sqlite:///:memory:")
    with engine.connect() as connection:
        _create_schema(connection)
        _insert_fixture(connection)
        connection.commit()
        yield connection


# ---------------------------------------------------------------------------
# get_assertion_filter_chain
# ---------------------------------------------------------------------------


def test_filter_chain_returns_only_rejects_in_order(conn):
    chain = get_assertion_filter_chain(conn)

    # Only REJECT rows, never ACCEPT
    assert "ACCEPT" not in chain.values

    # Assertion 4 (included) has no rejects
    assert 4 not in chain["assertion_id"].values

    # Assertion 1 has two rejects in order
    a1 = cast(pd.DataFrame, chain[chain["assertion_id"] == 1]).sort_values("position")
    assert a1["filter_name"].tolist() == ["MissingValue", "ParameterType"]
    assert a1["position"].tolist() == [1, 2]


def test_filter_chain_strips_package_prefix(conn):
    chain = get_assertion_filter_chain(conn)
    assert "teralizer.processing.filter" not in chain["filter_name"].iloc[0]


# ---------------------------------------------------------------------------
# compute_first_reject_blockers
# ---------------------------------------------------------------------------


def test_first_reject_blockers_counts_position_1_only():
    chain = pd.DataFrame(
        [
            {"assertion_id": 1, "filter_name": "MissingValue", "position": 1},
            {"assertion_id": 1, "filter_name": "ParameterType", "position": 2},
            {"assertion_id": 2, "filter_name": "ReturnType", "position": 1},
            {"assertion_id": 3, "filter_name": "ParameterType", "position": 1},
        ]
    )
    blockers = compute_first_reject_blockers(chain)

    # MissingValue: 1 first-reject, 1 total, 0 shadowed
    mv = blockers[blockers["filter_name"] == "MissingValue"].iloc[0]
    assert mv["first_reject_count"] == 1
    assert mv["total_reject_count"] == 1
    assert mv["shadowed_count"] == 0
    assert mv["net_reach"] == 1

    # ParameterType: 1 first-reject, 2 total, 1 shadowed
    pt = blockers[blockers["filter_name"] == "ParameterType"].iloc[0]
    assert pt["first_reject_count"] == 1
    assert pt["total_reject_count"] == 2
    assert pt["shadowed_count"] == 1
    assert pt["net_reach"] == 1

    # ReturnType: 1 first-reject
    rt = blockers[blockers["filter_name"] == "ReturnType"].iloc[0]
    assert rt["first_reject_count"] == 1
    assert rt["net_reach"] == 1

    # Sorted by net_reach descending
    assert blockers.iloc[0]["net_reach"] >= blockers.iloc[-1]["net_reach"]


def test_first_reject_blockers_zero_net_reach_when_fully_shadowed():
    """A filter that is never the first reject has zero net reach."""
    chain = pd.DataFrame(
        [
            {"assertion_id": 1, "filter_name": "MissingValue", "position": 1},
            {"assertion_id": 1, "filter_name": "UnsupportedAssertion", "position": 2},
        ]
    )
    blockers = compute_first_reject_blockers(chain)
    ua = blockers[blockers["filter_name"] == "UnsupportedAssertion"].iloc[0]
    assert ua["first_reject_count"] == 0
    assert ua["total_reject_count"] == 1
    assert ua["shadowed_count"] == 1
    assert ua["net_reach"] == 0


# ---------------------------------------------------------------------------
# compute_blocker_cooccurrence
# ---------------------------------------------------------------------------


def test_cooccurrence_pairs_first_and_second_blocker():
    chain = pd.DataFrame(
        [
            {"assertion_id": 1, "filter_name": "ReturnType", "position": 1},
            {"assertion_id": 1, "filter_name": "ParameterType", "position": 2},
            {"assertion_id": 2, "filter_name": "ReturnType", "position": 1},
            {"assertion_id": 3, "filter_name": "MissingValue", "position": 1},
        ]
    )
    cooc = compute_blocker_cooccurrence(chain)

    pair = cooc[
        (cooc["first_blocker"] == "ReturnType")
        & (cooc["second_blocker"] == "ParameterType")
    ]
    assert pair["count"].iloc[0] == 1

    solo = cooc[
        (cooc["first_blocker"] == "ReturnType") & (cooc["second_blocker"].isna())
    ]
    assert solo["count"].iloc[0] == 1

    mv = cooc[cooc["first_blocker"] == "MissingValue"]
    assert mv["count"].iloc[0] == 1


# ---------------------------------------------------------------------------
# compute_multi_blocker_rate
# ---------------------------------------------------------------------------


def test_multi_blocker_rate_counts_assertions_with_two_or_more():
    chain = pd.DataFrame(
        [
            {"assertion_id": 1, "filter_name": "A", "position": 1},
            {"assertion_id": 1, "filter_name": "B", "position": 2},
            {"assertion_id": 2, "filter_name": "A", "position": 1},
        ]
    )
    # 1 of 2 assertions has >= 2 blockers
    assert compute_multi_blocker_rate(chain) == 0.5


def test_multi_blocker_rate_zero_when_all_single():
    chain = pd.DataFrame(
        [
            {"assertion_id": 1, "filter_name": "A", "position": 1},
            {"assertion_id": 2, "filter_name": "B", "position": 1},
        ]
    )
    assert compute_multi_blocker_rate(chain) == 0.0


def test_multi_blocker_rate_empty_chain():
    chain = pd.DataFrame(columns=["assertion_id", "filter_name", "position"])
    assert compute_multi_blocker_rate(chain) == 0.0


# ---------------------------------------------------------------------------
# get_project_summary + compute_projects_closest_to_completion
# ---------------------------------------------------------------------------


def test_project_summary_counts_included_and_total(conn):
    summary = get_project_summary(conn)
    assert len(summary) == 1
    row = summary.iloc[0]
    assert row["project_name"] == "github_com_example_proj"
    assert row["total_assertions"] == 4
    assert row["included_assertions"] == 1
    assert row["pct_included"] == 25.0


def test_projects_closest_filters_by_min_included(conn):
    summary = get_project_summary(conn)
    closest = compute_projects_closest_to_completion(summary, min_included=1)
    assert len(closest) == 1

    empty = compute_projects_closest_to_completion(summary, min_included=5)
    assert len(empty) == 0


# ---------------------------------------------------------------------------
# get_missingvalue_assertions + compute_missingvalue_taxonomy
# ---------------------------------------------------------------------------


def test_missingvalue_taxonomy_classifies_extracted_vs_in_source(conn):
    """The taxonomy should classify assertions by call-extraction state."""
    mv_df = get_missingvalue_assertions(conn)
    taxonomy = compute_missingvalue_taxonomy(mv_df)

    # Assert 1 is first-reject MissingValue; asserts 2,3 reject on ReturnType
    # and ParameterType respectively, so only assert 1 enters the taxonomy.
    assert len(taxonomy) >= 1
    assert "count" in taxonomy.columns
    assert "pct" in taxonomy.columns

    # Assert 1's call 'result.size()' starts lowercase -> instance_call_extracted
    categories = set(taxonomy["category"])
    assert "instance_call_extracted" in categories
