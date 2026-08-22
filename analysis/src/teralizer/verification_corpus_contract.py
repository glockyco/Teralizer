"""Validate the hosted verification-corpus scheduling contract."""

from __future__ import annotations

import argparse
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import NoReturn, cast

import yaml

_REPO_ROOT = Path(__file__).resolve().parents[3]
WORKFLOW_PATH = _REPO_ROOT / ".github/workflows/verification-corpus.yml"

# Exact files are listed individually. Recursive roots use one representative file because the
# supported pattern language treats a trailing /** as the complete subtree.
OWNER_PATH_CASES = (
    ".github/workflows/verification-corpus.yml",
    "scripts/verify-pipeline.sh",
    "scripts/run-verification-corpus.sh",
    "scripts/check-verification-corpus.sh",
    "scripts/corpus-registry",
    "scripts/lib/db-guard.sh",
    "scripts/lib/run-supervisor.sh",
    "scripts/lib/db-lifecycle.sh",
    "scripts/lib/psql.sh",
    "analysis/pyproject.toml",
    "analysis/uv.lock",
    "analysis/src/teralizer/__init__.py",
    "analysis/src/teralizer/config.py",
    "analysis/src/teralizer/corpora.py",
    "analysis/src/teralizer/report_basis.py",
    "build.gradle",
    "settings.gradle",
    "build-properties.xml",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.properties",
    ".gitmodules",
    "jpf-symbc",
    "src/main/java/teralizer/TestGeneralizationRunner.java",
    "project-configs/verification.conf",
    "project-configs/verification/fixture-symbolic-int.conf",
    "verification/fixtures/symbolic-int/pom.xml",
    "verification/golden/symbolic-int.tsv",
)

EXCLUDED_PATH_CASES = (
    "openspec/changes/example/proposal.md",
    "docs/architecture/example.md",
    "analysis/src/teralizer/eval/reports/rq0.py",
    "analysis/src/teralizer/eval/render.py",
    "scripts/lib/jarvis-run.sh",
)


class ContractError(ValueError):
    """The workflow does not satisfy the declared scheduling contract."""


def _fail(message: str) -> NoReturn:
    raise ContractError(message)


def _mapping(value: object, location: str) -> Mapping[str, object]:
    if not isinstance(value, Mapping):
        _fail(f"{location} must be a mapping")
    return cast(Mapping[str, object], value)


def _string_list(value: object, location: str) -> tuple[str, ...]:
    if not isinstance(value, Sequence) or isinstance(value, str):
        _fail(f"{location} must be a list")
    items = cast(Sequence[object], value)
    if not all(isinstance(item, str) for item in items):
        _fail(f"{location} must contain only strings")
    return tuple(cast(Sequence[str], items))


def _required(mapping: Mapping[str, object], key: str, location: str) -> object:
    if key not in mapping:
        _fail(f"{location}.{key} is missing")
    return mapping[key]


def _matches(pattern: str, path: str) -> bool:
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        if not prefix or any(char in prefix for char in "*?["):
            _fail(f"unsupported push path pattern: {pattern}")
        return path == prefix or path.startswith(f"{prefix}/")
    if any(char in pattern for char in "*?["):
        _fail(f"unsupported push path pattern: {pattern}")
    return path == pattern


def validate_workflow(path: Path = WORKFLOW_PATH) -> None:
    """Raise ``ContractError`` when *path* violates the corpus workflow contract."""

    try:
        document = yaml.load(path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
    except (OSError, yaml.YAMLError) as error:
        _fail(f"cannot parse {path}: {error}")

    workflow = _mapping(document, "workflow")
    triggers = _mapping(_required(workflow, "on", "workflow"), "workflow.on")
    push = _mapping(_required(triggers, "push", "workflow.on"), "workflow.on.push")
    patterns = _string_list(
        _required(push, "paths", "workflow.on.push"), "workflow.on.push.paths"
    )

    for owner_path in OWNER_PATH_CASES:
        if not any(_matches(pattern, owner_path) for pattern in patterns):
            _fail(f"owner path is not scheduled: {owner_path}")
    for excluded_path in EXCLUDED_PATH_CASES:
        if any(_matches(pattern, excluded_path) for pattern in patterns):
            _fail(f"excluded path is scheduled: {excluded_path}")

    _required(triggers, "workflow_dispatch", "workflow.on")
    schedule = _required(triggers, "schedule", "workflow.on")
    if not isinstance(schedule, Sequence) or isinstance(schedule, str) or not schedule:
        _fail("workflow.on.schedule must contain a periodic trigger")
    schedule_entries = cast(Sequence[object], schedule)
    if not any(
        isinstance(entry, Mapping) and entry.get("cron") == "0 4 * * 1"
        for entry in schedule_entries
    ):
        _fail("workflow.on.schedule must retain the weekly Monday trigger")

    concurrency = _mapping(
        _required(workflow, "concurrency", "workflow"), "workflow.concurrency"
    )
    group_value = _required(concurrency, "group", "workflow.concurrency")
    if not isinstance(group_value, str):
        _fail("workflow.concurrency.group must be a string")
    group = cast(str, group_value)
    if "${{ github.workflow }}" not in group:
        _fail("workflow.concurrency.group must include github.workflow")
    if "${{ github.ref }}" not in group:
        _fail("workflow.concurrency.group must include github.ref")
    if _required(concurrency, "cancel-in-progress", "workflow.concurrency") != "true":
        _fail("workflow.concurrency.cancel-in-progress must be true")

    jobs = _mapping(_required(workflow, "jobs", "workflow"), "workflow.jobs")
    corpus = _mapping(
        _required(jobs, "corpus", "workflow.jobs"), "workflow.jobs.corpus"
    )
    if _required(corpus, "timeout-minutes", "workflow.jobs.corpus") != "35":
        _fail("workflow.jobs.corpus.timeout-minutes must be 35")


def main(argv: Sequence[str] | None = None) -> int:
    """Validate a workflow supplied on the command line or the repository declaration."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workflow", nargs="?", type=Path, default=WORKFLOW_PATH)
    args = parser.parse_args(argv)
    try:
        validate_workflow(args.workflow)
    except ContractError as error:
        parser.exit(1, f"verification-corpus contract: {error}\n")
    print(f"Verification-corpus workflow contract passed: {args.workflow}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
