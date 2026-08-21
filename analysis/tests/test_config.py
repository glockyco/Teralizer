import pytest

from teralizer.config import DatabaseConfig


def test_database_name_is_required_at_connection_boundary():
    config = DatabaseConfig()

    with pytest.raises(TypeError, match="database"):
        config.get_engine()  # type: ignore[call-arg]


def test_legacy_identity_environment_does_not_create_database_aliases(monkeypatch):
    monkeypatch.setenv("DB_NAME_DEV", "ignored_dev")
    monkeypatch.setenv("DB_NAME_TEST", "ignored_test")
    monkeypatch.setenv("DATASET_VARIANT", "replicate")

    config = DatabaseConfig()

    assert not hasattr(config, "db_name_dev")
    assert not hasattr(config, "db_name_test")
    assert not hasattr(config, "variant")
