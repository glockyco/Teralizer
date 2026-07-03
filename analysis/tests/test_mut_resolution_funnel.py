"""Tests for MUT-resolution funnel database entry points."""

from __future__ import annotations

import pandas as pd


def test_main_uses_repo_reapers_engine_without_schema_validation(monkeypatch, capsys):
    from teralizer import mut_resolution_funnel

    calls: list[tuple[str, object]] = []

    class FakeConnection:
        pass

    class FakeConnectionContext:
        def __enter__(self) -> FakeConnection:
            return FakeConnection()

        def __exit__(self, exc_type, exc, tb) -> None:
            return None

    class FakeEngine:
        def connect(self) -> FakeConnectionContext:
            calls.append(("connect", None))
            return FakeConnectionContext()

    class FakeDbConfig:
        def get_test_engine(self, *, validate: bool = True) -> FakeEngine:
            calls.append(("get_test_engine", validate))
            return FakeEngine()

    monkeypatch.setattr(mut_resolution_funnel, "db_config", FakeDbConfig())
    monkeypatch.setattr(
        mut_resolution_funnel,
        "get_tier_funnel",
        lambda conn: pd.DataFrame(
            [
                {
                    "status": "RESOLVED",
                    "confidence_tier": "T1_PROVEN",
                    "deciding_signal": "DIRECT_ASSERT_ARG",
                    "assertions": 2,
                    "shallow_picks": 0,
                    "inspector_unwraps": 0,
                }
            ]
        ),
    )
    monkeypatch.setattr(
        mut_resolution_funnel,
        "get_missing_value_cross_tab",
        lambda conn: pd.DataFrame(
            [
                {
                    "status": "NONE",
                    "confidence_tier": "T5_NONE",
                    "no_pick_reason": "NO_CANDIDATES",
                    "mv_rejects": 1,
                }
            ]
        ),
    )
    monkeypatch.setattr(
        mut_resolution_funnel,
        "get_guess_provenance",
        lambda conn: pd.DataFrame(
            [
                {
                    "project_id": 1,
                    "assertion_id": 7,
                    "resolved_method_name": "compute",
                    "candidate_count": 3,
                    "candidate_param_supported": True,
                    "candidate_return_supported": True,
                    "focal_agreement": False,
                    "candidate_details": "[]",
                }
            ]
        ),
    )
    monkeypatch.setattr(
        mut_resolution_funnel,
        "get_topology_cross_tab",
        lambda conn: pd.DataFrame(
            [
                {
                    "actual_shape": "RETURN_VALUE",
                    "receiver_provenance": "TEST_LITERAL",
                    "assertions": 2,
                    "resolved": 2,
                }
            ]
        ),
    )

    mut_resolution_funnel.main()

    assert calls == [("get_test_engine", False), ("connect", None)]
    output = capsys.readouterr().out
    assert "== Tier funnel ==" in output
    assert "T1_PROVEN: 2 (100.0%)" in output
    assert "== MissingValue cross-tab ==" in output
    assert "== T4 guesses: 1 ==" in output
    assert "== Input topology (shape x provenance) ==" in output
