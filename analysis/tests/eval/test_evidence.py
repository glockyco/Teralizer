"""Tests for compact, versioned report evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import cast

import pytest

from teralizer.eval.evidence import jarvis_values, project_sources

_REPO_ROOT = Path(__file__).resolve().parents[3]
_PROJECT_FACTS = _REPO_ROOT / "analysis/data/report-inputs/project-source-facts.json"
_JARVIS_FACTS = _REPO_ROOT / "analysis/data/report-inputs/jarvis-value-facts.json"


def _project_row(name: str = "fixture") -> dict[str, object]:
    return {
        "project": name,
        "main_files": 1,
        "main_classes": 2,
        "main_sloc": 3,
        "test_files": 4,
        "test_classes": 5,
        "test_sloc": 6,
        "test_methods": 7,
    }


def test_shipped_project_source_facts_have_reconciled_values():
    actual = project_sources.frame(_PROJECT_FACTS).set_index("project")

    assert actual.loc["repo-reapers (total)"].to_dict() == {
        "main_files": 38_916,
        "main_classes": 47_334,
        "main_sloc": 2_552_342,
        "test_files": 20_948,
        "test_classes": 28_990,
        "test_sloc": 1_867_904,
        "test_methods": 85_368,
    }
    assert set(actual.loc[actual.index.str.startswith("eqbench"), "main_classes"]) == {
        652
    }
    assert set(
        actual.loc[actual.index.str.startswith("commons-utils"), "main_classes"]
    ) == {247}


def test_project_source_reader_rejects_reconciliation_drift(tmp_path: Path):
    path = tmp_path / "project-source-facts.json"
    path.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "sources": {"corpus_ids": ["controlled", "real-world"]},
                "reconciliation": {"report_rows": 2},
                "projects": [_project_row()],
            }
        ),
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="report-row total disagrees"):
        project_sources.read(path)


def test_value_identity_is_lossless_for_java_surrogates():
    surrogate = "\ud800"

    assert jarvis_values.value_identity(surrogate).isascii()
    assert jarvis_values.value_identity(surrogate) != jarvis_values.value_identity(
        "\\ud800"
    )


def test_shipped_jarvis_facts_have_reconciled_relations_and_inputs():
    document = jarvis_values.read(_JARVIS_FACTS)
    reconciliation = cast(dict[str, object], document["reconciliation"])
    sources = cast(dict[str, object], document["sources"])
    scoreboard_logs = cast(dict[str, object], sources["scoreboard_value_logs"])
    census_logs = cast(dict[str, object], sources["census_value_logs"])

    assert reconciliation == {
        "scoreboard_rows": 30,
        "census_mut_rows": 342,
        "census_project_rows": 9,
    }
    assert sources["corpus_ids"] == ["jarvis-scenarios", "jarvis-benchmark"]
    assert scoreboard_logs["count"] == 30
    assert census_logs["count"] == 1494
    assert len(jarvis_values.scoreboard_frame(_JARVIS_FACTS)) == 30
    assert len(jarvis_values.census_by_mut_frame(_JARVIS_FACTS)) == 342
    assert len(jarvis_values.census_project_frame(_JARVIS_FACTS)) == 9


def test_jarvis_reader_rejects_reconciliation_drift(tmp_path: Path):
    document = jarvis_values.read(_JARVIS_FACTS)
    reconciliation = cast(dict[str, object], document["reconciliation"])
    reconciliation["scoreboard_rows"] = 31
    path = tmp_path / "jarvis-value-facts.json"
    path.write_text(json.dumps(document), encoding="utf-8")

    with pytest.raises(ValueError, match="reconciliation mismatch"):
        jarvis_values.read(path)
