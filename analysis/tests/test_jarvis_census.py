"""Tests for the beyond-JARVIS census report helpers."""

from __future__ import annotations

import sqlite3

from teralizer.jarvis_scoreboard import get_mutation_gain, mutation_gain_keys


def test_mutation_gain_keys_is_set_difference():
    assert mutation_gain_keys({"a", "b", "c"}, {"a"}) == {"b", "c"}


def test_mutation_gain_keys_empty_when_no_new_kills():
    assert mutation_gain_keys({"a"}, {"a", "b"}) == set()


def _pit_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.execute(
        "CREATE TABLE pit_mutation_report ("
        "project_id INTEGER, stage TEXT, variant TEXT, is_detected INTEGER, "
        "mutated_class TEXT, mutated_method TEXT, method_description TEXT, "
        "line_number INTEGER, mutator TEXT, indexes TEXT)"
    )
    rows = [
        # INITIAL seed baseline (variant NULL) kills m1.
        (1, "COLLECT_PIT_DATA_INITIAL", None, 1, "C", "m1", "()V", 1, "MATH", "0"),
        # GENERALIZED (seed + properties) kills m1 (seed) and m2 (added by the property).
        (
            1,
            "COLLECT_PIT_DATA_GENERALIZED",
            "IMPROVED_100_TRIES",
            1,
            "C",
            "m1",
            "()V",
            1,
            "MATH",
            "0",
        ),
        (
            1,
            "COLLECT_PIT_DATA_GENERALIZED",
            "IMPROVED_100_TRIES",
            1,
            "C",
            "m2",
            "()V",
            2,
            "MATH",
            "0",
        ),
        # A covered-but-not-killed mutant must not count toward the gain.
        (
            1,
            "COLLECT_PIT_DATA_GENERALIZED",
            "IMPROVED_100_TRIES",
            0,
            "C",
            "m3",
            "()V",
            3,
            "MATH",
            "0",
        ),
    ]
    conn.executemany(
        "INSERT INTO pit_mutation_report VALUES (?,?,?,?,?,?,?,?,?,?)", rows
    )
    conn.commit()
    return conn


def test_get_mutation_gain_diffs_generalized_against_initial_baseline():
    conn = _pit_conn()
    try:
        gain = get_mutation_gain(conn, variants=["IMPROVED_100_TRIES"])
    finally:
        conn.close()

    assert list(gain["variant"]) == ["IMPROVED_100_TRIES"]
    row = gain.iloc[0]
    assert int(row["initial_killed"]) == 1
    assert int(row["generalized_killed"]) == 2
    # m2 is the only mutant the property kills that the single-value seed misses.
    assert int(row["gain"]) == 1
