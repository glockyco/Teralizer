"""
Shared pytest fixtures for teralizer analysis tests.

Provides database connections and common test data based on actual project mappings.
"""

import pytest
from teralizer.config import db_config


@pytest.fixture(scope="session")
def dev_conn():
    """Database connection to postgres_dev (session-scoped)."""
    return db_config.get_dev_engine()


@pytest.fixture(scope="session")
def test_conn():
    """Database connection to postgres_test (session-scoped)."""
    return db_config.get_test_engine()


@pytest.fixture
def known_projects():
    """Known project names from actual dataset mappings in table order."""
    return [
        "eqbench-es-default-1s",
        "eqbench-es-default-10s",
        "eqbench-es-default-60s",
        "commons-utils-es-default-1s",
        "commons-utils-es-default-10s",
        "commons-utils-es-default-60s",
        "commons-utils",
        "repo-reapers",
    ]


@pytest.fixture
def known_variants():
    """Known variant names from actual variant mappings in order."""
    return [
        "ORIGINAL",
        "INITIAL",
        "SHARED",
        "BASELINE",
        "NAIVE_10_TRIES",
        "NAIVE_50_TRIES",
        "NAIVE_200_TRIES",
        "IMPROVED_10_TRIES",
        "IMPROVED_50_TRIES",
        "IMPROVED_200_TRIES",
    ]


@pytest.fixture
def basic_variants():
    """Basic variants used in most queries."""
    return ["ORIGINAL", "INITIAL"]


@pytest.fixture
def generalization_variants():
    """Variants that involve test generalization."""
    return [
        "BASELINE",
        "NAIVE_10_TRIES",
        "NAIVE_50_TRIES",
        "NAIVE_200_TRIES",
        "IMPROVED_10_TRIES",
        "IMPROVED_50_TRIES",
        "IMPROVED_200_TRIES",
    ]
