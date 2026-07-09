import pytest
from teralizer.eval import registry
from teralizer.eval.model import RQReport, Section


def test_get_unknown_raises():
    with pytest.raises(KeyError):
        registry.get("nope")


def test_register_and_get(monkeypatch):
    spec = registry.ReportSpec(
        lambda conn: RQReport("t", "T", "db", [Section("s", [])]), "postgres_dev", "old"
    )
    monkeypatch.setitem(registry.REPORTS, "t", spec)
    assert registry.get("t") is spec
