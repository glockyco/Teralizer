from pathlib import Path

import pytest

from teralizer.verification_corpus_contract import (
    WORKFLOW_PATH,
    ContractError,
    validate_workflow,
)


def _modified_workflow(tmp_path: Path, old: str, new: str) -> Path:
    source = WORKFLOW_PATH.read_text(encoding="utf-8")
    assert old in source
    path = tmp_path / "verification-corpus.yml"
    path.write_text(source.replace(old, new, 1), encoding="utf-8")
    return path


def test_repository_workflow_satisfies_contract() -> None:
    validate_workflow()


def test_missing_owner_path_names_unscheduled_input(tmp_path: Path) -> None:
    workflow = _modified_workflow(tmp_path, "      - 'src/**'\n", "")

    with pytest.raises(
        ContractError, match="owner path is not scheduled: src/main/java"
    ):
        validate_workflow(workflow)


def test_overbroad_owner_path_names_excluded_input(tmp_path: Path) -> None:
    workflow = _modified_workflow(
        tmp_path,
        "      - 'analysis/src/teralizer/corpora.py'\n",
        "      - 'analysis/**'\n",
    )

    with pytest.raises(
        ContractError,
        match="excluded path is scheduled: analysis/src/teralizer/eval/reports/rq0.py",
    ):
        validate_workflow(workflow)


def test_missing_manual_trigger_is_rejected(tmp_path: Path) -> None:
    workflow = _modified_workflow(tmp_path, "  workflow_dispatch:\n", "")

    with pytest.raises(ContractError, match="workflow.on.workflow_dispatch is missing"):
        validate_workflow(workflow)


def test_missing_periodic_trigger_is_rejected(tmp_path: Path) -> None:
    workflow = _modified_workflow(
        tmp_path,
        "  schedule:\n    - cron: '0 4 * * 1'\n",
        "",
    )

    with pytest.raises(ContractError, match="workflow.on.schedule is missing"):
        validate_workflow(workflow)


def test_nonweekly_schedule_is_rejected(tmp_path: Path) -> None:
    workflow = _modified_workflow(
        tmp_path, "    - cron: '0 4 * * 1'\n", "    - cron: '0 4 * * 2'\n"
    )

    with pytest.raises(ContractError, match="must retain the weekly Monday trigger"):
        validate_workflow(workflow)


def test_global_concurrency_group_is_rejected(tmp_path: Path) -> None:
    workflow = _modified_workflow(
        tmp_path,
        "  group: ${{ github.workflow }}-${{ github.ref }}\n",
        "  group: verification-corpus\n",
    )

    with pytest.raises(ContractError, match="group must include github.workflow"):
        validate_workflow(workflow)


def test_missing_timeout_is_rejected(tmp_path: Path) -> None:
    workflow = _modified_workflow(tmp_path, "    timeout-minutes: 35\n", "")

    with pytest.raises(ContractError, match="timeout-minutes is missing"):
        validate_workflow(workflow)
