import subprocess
from pathlib import Path

import pytest
from teralizer.release import inventory


def _git(repo: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)


def _payloads() -> list[dict[str, object]]:
    return [
        {
            "id": f"input-{component}",
            "location": "corpus-package",
            "path": f"{component}.dump",
            "disposition": "payload",
            "component": component,
            "reason": "Fixture payload.",
        }
        for component in inventory.COMPONENTS
    ]


def _document(**extra: object) -> dict[str, object]:
    document: dict[str, object] = {"expand": [], "input": _payloads(), "ambient": []}
    document.update(extra)
    return document


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    _git(tmp_path, "init", "-q")
    (tmp_path / ".gitignore").write_text("data/\n*.log\n")
    (tmp_path / "README.md").write_text("fixture\n")
    _git(tmp_path, "add", ".")
    _git(tmp_path, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init")
    return tmp_path


def test_payload_input_needs_a_declared_component():
    entry = _payloads()[0] | {"component": "projects-unknown"}
    with pytest.raises(inventory.InventoryError, match="declared component"):
        inventory.parse(_document(input=[entry]))


def test_non_payload_input_must_not_name_a_component():
    entry = _payloads()[0] | {"disposition": "duplicate"}
    with pytest.raises(inventory.InventoryError, match="only payload"):
        inventory.parse(_document(input=[entry]))


def test_ambient_rule_must_reference_a_checkout_input():
    rule = {
        "paths": ["data/"],
        "disposition": "input",
        "inputs": ["input-core"],
        "reason": "Source.",
    }
    with pytest.raises(inventory.InventoryError, match="not checkout-ambient"):
        inventory.parse(_document(ambient=[rule]))


def test_checkout_input_needs_an_ambient_rule():
    entry = {
        "id": "logs",
        "location": "checkout-ambient",
        "path": "data/logs",
        "disposition": "payload",
        "component": "core",
        "reason": "Logs.",
    }
    with pytest.raises(inventory.InventoryError, match="without an ambient rule"):
        inventory.parse(_document(input=[*_payloads(), entry]))


def test_unowned_ambient_paths_and_undeclared_gitlinks_are_reported(repo: Path):
    (repo / "data").mkdir()
    (repo / "data" / "run").mkdir()
    (repo / "data" / "status.tsv").write_text("x\n")
    (repo / "build.log").write_text("x\n")
    _git(repo, "update-index", "--add", "--cacheinfo", f"160000,{'a' * 40},vendor/lib")
    rules = [
        {"paths": ["*.log"], "disposition": "scratch", "reason": "Logs."},
        {"paths": ["data/run/"], "disposition": "unrelated", "reason": "Old runs."},
    ]
    declared = inventory.parse(_document(expand=["data/"], ambient=rules))

    problems = inventory.check(declared, repo)

    assert problems == [
        "gitlink vendor/lib: needs exactly one submodule input, has []",
        "ambient path data/status.tsv: matches no ambient rule",
    ]


def test_most_specific_ambient_pattern_decides(repo: Path):
    (repo / "data").mkdir()
    (repo / "data" / "run.log").write_text("x\n")
    rules = [
        {"paths": ["*.log"], "disposition": "scratch", "reason": "Logs."},
        {"paths": ["data/"], "disposition": "unrelated", "reason": "Data."},
        {"paths": ["data/run.log"], "disposition": "sensitive", "reason": "Kept."},
    ]
    declared = inventory.parse(_document(expand=["data/"], ambient=rules))

    decided = inventory.ambient_rule(declared, "data/run.log")

    assert isinstance(decided, inventory.AmbientRule)
    assert decided.disposition == "sensitive"
    assert inventory.check(declared, repo) == []


def test_equally_specific_rules_are_ambiguous():
    rules = [
        {"paths": ["data/*.log"], "disposition": "scratch", "reason": "Logs."},
        {"paths": ["data/run.*"], "disposition": "unrelated", "reason": "Runs."},
    ]
    declared = inventory.parse(_document(ambient=rules))

    decided = inventory.ambient_rule(declared, "data/run.log")

    assert isinstance(decided, str)
    assert "equally specific" in decided


def test_lfs_files_need_a_git_lfs_input(repo: Path):
    (repo / ".gitattributes").write_text("*.jar filter=lfs\n")
    (repo / "tool.jar").write_text("pointer\n")
    _git(repo, "add", ".gitattributes", "tool.jar")
    declared = inventory.parse(_document())

    assert inventory.check(declared, repo) == [
        "LFS file tool.jar: needs exactly one git-lfs input, has []"
    ]


def test_repository_inventory_is_well_formed():
    declared = inventory.load()

    assert {item.component for item in declared.inputs if item.component} == set(
        inventory.COMPONENTS
    )
