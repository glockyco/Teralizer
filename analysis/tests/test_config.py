import pytest

from teralizer.config import DatabaseConfig


def test_jarvis_dataset_variant_is_valid(monkeypatch):
    monkeypatch.setenv("DATASET_VARIANT", "jarvis")

    config = DatabaseConfig()

    assert config.variant == "jarvis"


def test_invalid_dataset_variant_message_includes_jarvis(monkeypatch):
    monkeypatch.setenv("DATASET_VARIANT", "unknown")

    with pytest.raises(ValueError, match="jarvis"):
        DatabaseConfig()
