"""Tests for MUT-resolution funnel database entry points."""

from __future__ import annotations

import pandas as pd


def test_main_resolves_the_default_real_world_corpus(monkeypatch, capsys):
    from contextlib import contextmanager
    from types import SimpleNamespace

    from teralizer import mut_resolution_funnel

    calls: list[tuple[str, object]] = []

    class FakeConnection:
        pass

    @contextmanager
    def fake_open_corpus(corpus_id: str):
        calls.append(("open_corpus", corpus_id))
        yield SimpleNamespace(database="resolved_real_world"), FakeConnection()

    def fake_print_basis_header(conn: FakeConnection, db_name: str) -> None:
        calls.append(("print_basis_header", db_name))
        print("# Analysis basis")

    monkeypatch.setattr(mut_resolution_funnel, "open_corpus", fake_open_corpus)
    monkeypatch.setattr(
        mut_resolution_funnel, "print_basis_header", fake_print_basis_header
    )
    monkeypatch.setattr("sys.argv", ["mut_resolution_funnel"])
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
    monkeypatch.setattr(
        mut_resolution_funnel,
        "get_library_declaration_observations",
        lambda conn: pd.DataFrame(
            [
                {
                    "resolved_method_name": "get",
                    "resolved_declaring_type": "java.util.List",
                    "resolved_call_source": "producer().get(0)",
                    "receiver_provenance": "NONE",
                    "candidate_details": "[]",
                }
            ]
        ),
    )

    mut_resolution_funnel.main()

    assert calls == [
        ("open_corpus", "real-world"),
        ("print_basis_header", "resolved_real_world"),
    ]
    output = capsys.readouterr().out
    assert "== Tier funnel ==" in output
    assert "T1_PROVEN: 2 (100.0%)" in output
    assert "== MissingValue cross-tab ==" in output
    assert "== T4 guesses: 1 ==" in output
    assert "== Input topology (shape x provenance) ==" in output
    assert "== Lever 4 library-accessor unwrap sizing ==" in output


def test_library_accessor_sizing_marks_inline_and_local_receiver_producers():
    from teralizer.mut_resolution_funnel import summarize_library_accessor_unwrap

    observations = pd.DataFrame(
        [
            {
                "resolved_method_name": "get",
                "resolved_declaring_type": "java.util.List",
                "resolved_call_source": "encoder.topDownCompute(output).get(0)",
                "receiver_provenance": "NONE",
                "candidate_details": "[]",
            },
            {
                "resolved_method_name": "get",
                "resolved_declaring_type": "java.util.ArrayList",
                "resolved_call_source": "bucketInfoList.get(0)",
                "receiver_provenance": "LOCAL_OTHER",
                "candidate_details": None,
            },
            {
                "resolved_method_name": "get",
                "resolved_declaring_type": "java.util.Optional",
                "resolved_call_source": "maybe.get()",
                "receiver_provenance": "PARAM_OR_STATIC",
                "candidate_details": "[]",
            },
            {
                "resolved_method_name": "equals",
                "resolved_declaring_type": "java.lang.String",
                "resolved_call_source": '"yes".equals(actual)',
                "receiver_provenance": "NONE",
                "candidate_details": "[]",
            },
        ]
    )

    summary = summarize_library_accessor_unwrap(observations)

    by_accessor = summary.set_index("accessor")
    assert by_accessor.loc["List.get", "total"] == 2
    assert by_accessor.loc["List.get", "estimated_recoverable"] == 2
    assert by_accessor.loc["Optional.get", "total"] == 1
    assert by_accessor.loc["Optional.get", "estimated_recoverable"] == 0
    assert by_accessor.loc["other", "total"] == 1
    assert by_accessor.loc["TOTAL", "total"] == 4
    assert by_accessor.loc["TOTAL", "estimated_recoverable"] == 2


def test_library_accessor_sizing_uses_candidate_details_for_receiver_producer():
    from teralizer.mut_resolution_funnel import estimate_library_accessor_unwrap

    result = estimate_library_accessor_unwrap(
        resolved_method_name="get",
        resolved_declaring_type="java.util.Map",
        resolved_call_source='byKey.get("name")',
        receiver_provenance="NONE",
        candidate_details=(
            '[{"methodName":"findByName","declaringType":"com.acme.Cut",'
            '"callSource":"byKey"}]'
        ),
    )

    assert result.accessor == "Map.get"
    assert result.estimated_recoverable
    assert result.evidence == "candidate_details_receiver"
