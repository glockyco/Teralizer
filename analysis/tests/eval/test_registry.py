from pathlib import Path

import pytest
from teralizer.eval import registry, reports
from teralizer.eval.model import RQReport, Section


def test_get_unknown_raises():
    with pytest.raises(KeyError):
        registry.get("nope")


def test_register_and_get(monkeypatch):
    spec = registry.ReportSpec(
        lambda context: RQReport("t", "T", [Section("s", [])]), ()
    )
    monkeypatch.setitem(registry.REPORTS, "t", spec)
    assert registry.get("t") is spec


def test_registered_report_modules_have_no_hidden_input_accessors():
    report_dir = Path(reports.__file__).parent
    forbidden = {
        "open_report_connection(",
        "resolve_repo_relative_path(",
        "get_total_classes_from_filesystem(",
        "get_dataset_statistics(",
        "get_scoreboard(",
        "get_census_by_mut(",
        "get_census_project_pvc(",
    }
    violations = {
        path.name: sorted(token for token in forbidden if token in path.read_text())
        for path in report_dir.glob("*.py")
        if not path.name.startswith("__")
    }
    assert not {path: tokens for path, tokens in violations.items() if tokens}
