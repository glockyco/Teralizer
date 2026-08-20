"""Repository-state guard for knowledge and planning authority."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

import pytest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
_PLANNING_DIRECTORY_NAMES = {"planning", "plans", "roadmap", "roadmaps"}
_PLANNING_RECORD_TYPES = {"audit", "note", "overview", "plan", "spec"}
_RETIRED_PLANNING_REFERENCES = (
    "docs" + "/plans",
    "planning" + "-files",
    "omp" + "-plans",
)
_RETIRED_TECHNICAL_REFERENCES = tuple(
    "docs" + f"/{name}.md"
    for name in (
        "architecture",
        "artifacts",
        "database",
        "exclusion-model",
        "local-state",
        "rq6-analysis",
    )
)
_RETIRED_SPIKE_REFERENCES = (
    "project-configs" + "/spikes/r1-viability.conf",
    "verification" + "/spikes/r1-viability",
)
_REQUIRED_PROMOTED_PATHS = (
    Path("project-configs/verification/fixture-expression-slice.conf"),
    Path("verification/golden/expression-slice.tsv"),
)
_PLANNING_GUIDANCE = re.compile(
    r"\b(?:current\s+(?:planning|plans|work)|planning\s+(?:home|roadmap|state))\b",
    re.IGNORECASE,
)
_PLANNING_PATH_REFERENCE = re.compile(
    r"(?:docs|\.omp|planning|plans|roadmaps?)/[^`\s]*",
    re.IGNORECASE,
)
_FRONTMATTER_FIELD = re.compile(r"^([A-Za-z][A-Za-z0-9_-]*):\s*(.*?)\s*$")
_OPEN_SPEC_CONFIG = Path("openspec/config.yaml")
_EXPECTED_OPEN_SPEC_CONFIG = ["schema: spec-driven"]


def _tracked_paths(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=root,
        check=True,
        capture_output=True,
    )
    return [Path(item.decode()) for item in result.stdout.split(b"\0") if item]


def _read_text(path: Path) -> str | None:
    try:
        return path.read_text()
    except (OSError, UnicodeDecodeError):
        return None


def _frontmatter(text: str) -> dict[str, str]:
    if not text.startswith("---\n"):
        return {}
    end = text.find("\n---", 4)
    if end < 0:
        return {}
    fields: dict[str, str] = {}
    for line in text[4:end].splitlines():
        match = _FRONTMATTER_FIELD.fullmatch(line)
        if match:
            fields[match.group(1)] = match.group(2)
    return fields


def _config_lines(text: str) -> list[str]:
    return [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def repository_state_violations(
    root: Path, paths: list[Path] | None = None
) -> list[str]:
    """Return tracked repository paths that conflict with declared authorities."""
    violations: set[str] = set()
    scan_paths = paths if paths is not None else _tracked_paths(root)

    if paths is None:
        for required in _REQUIRED_PROMOTED_PATHS:
            if required not in scan_paths or not (root / required).is_file():
                violations.add(f"{required.as_posix()}: promoted fixture owner missing")

    for relative in scan_paths:
        if not relative.parts:
            continue

        path = root / relative
        if not path.exists():
            continue

        rendered = relative.as_posix()
        if relative == _OPEN_SPEC_CONFIG:
            text = _read_text(path)
            if text is None or _config_lines(text) != _EXPECTED_OPEN_SPEC_CONFIG:
                violations.add(
                    f"{rendered}: project-specific OpenSpec configuration is not allowed"
                )
            continue

        if relative.parts[0] == "openspec":
            continue

        if relative.parts[0] == "docs":
            violations.add(f"{rendered}: retired technical-document tree")

        if any(
            rendered == retired or rendered.startswith(f"{retired}/")
            for retired in _RETIRED_SPIKE_REFERENCES
        ):
            violations.add(f"{rendered}: superseded R1 spike path")

        has_planning_directory = any(
            part.lower() in _PLANNING_DIRECTORY_NAMES for part in relative.parts[:-1]
        )
        is_documentation_tree = relative.parts[0] in {".omp", "docs"}
        is_root_planning_tree = relative.parts[0].lower() in _PLANNING_DIRECTORY_NAMES
        if has_planning_directory and (is_documentation_tree or is_root_planning_tree):
            violations.add(f"{rendered}: planning directory outside openspec")

        text = _read_text(path)
        if text is None:
            continue

        for retired in _RETIRED_PLANNING_REFERENCES:
            if retired in text:
                violations.add(f"{rendered}: retired planning reference {retired}")

        for retired in _RETIRED_TECHNICAL_REFERENCES:
            if retired in text:
                violations.add(f"{rendered}: retired technical reference {retired}")

        for retired in _RETIRED_SPIKE_REFERENCES:
            if retired in text:
                violations.add(f"{rendered}: superseded R1 spike reference {retired}")

        metadata = _frontmatter(text)
        if (
            metadata.get("type", "").lower() in _PLANNING_RECORD_TYPES
            and "status" in metadata
        ):
            violations.add(f"{rendered}: planning metadata outside openspec")

        for line in text.splitlines():
            if (
                _PLANNING_GUIDANCE.search(line)
                and _PLANNING_PATH_REFERENCE.search(line)
                and "openspec/" not in line.lower()
            ):
                violations.add(f"{rendered}: planning guidance points outside openspec")

    return sorted(violations)


def _write(root: Path, relative: str, content: str) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    return Path(relative)


def test_detects_second_planning_directory(tmp_path):
    relative = _write(tmp_path, "docs/roadmaps/item.md", "# Proposed work\n")

    assert repository_state_violations(tmp_path, [relative]) == [
        "docs/roadmaps/item.md: planning directory outside openspec",
        "docs/roadmaps/item.md: retired technical-document tree",
    ]


def test_detects_current_planning_metadata_outside_openspec(tmp_path):
    relative = _write(
        tmp_path,
        "outside/work-item.md",
        "---\ntype: plan\nstatus: active\n---\n# Work item\n",
    )

    assert repository_state_violations(tmp_path, [relative]) == [
        "outside/work-item.md: planning metadata outside openspec"
    ]


def test_detects_retired_path_reference(tmp_path):
    retired = "docs" + "/plans/INDEX.md"
    relative = _write(tmp_path, "AGENTS.md", f"Read `{retired}` first.\n")

    assert repository_state_violations(tmp_path, [relative]) == [
        "AGENTS.md: retired planning reference docs/plans"
    ]


def test_detects_duplicate_planning_guidance(tmp_path):
    destination = "docs" + "/roadmaps/"
    relative = _write(
        tmp_path,
        "AGENTS.md",
        f"Current planning lives in `{destination}`.\n",
    )

    assert repository_state_violations(tmp_path, [relative]) == [
        "AGENTS.md: planning guidance points outside openspec"
    ]


def test_positive_control_reports_injected_path(tmp_path):
    relative = _write(
        tmp_path,
        "docs/roadmap/known-conflict.md",
        "---\ntype: plan\nstatus: active\n---\n# Known conflict\n",
    )

    violations = repository_state_violations(tmp_path, [relative])

    assert violations
    assert all(
        item.startswith("docs/roadmap/known-conflict.md:") for item in violations
    )


def test_detects_tracked_technical_document(tmp_path):
    retired = "docs" + "/architecture.md"
    relative = _write(tmp_path, retired, "# Snapshot\n")

    assert repository_state_violations(tmp_path, [relative]) == [
        f"{retired}: retired technical-document tree"
    ]


@pytest.mark.parametrize(
    "name",
    (
        "architecture",
        "artifacts",
        "database",
        "exclusion-model",
        "local-state",
        "rq6-analysis",
    ),
)
def test_detects_operative_retired_technical_reference(tmp_path, name):
    retired = "docs" + f"/{name}.md"
    relative = _write(tmp_path, "AGENTS.md", f"Read `{retired}` first.\n")

    assert repository_state_violations(tmp_path, [relative]) == [
        f"AGENTS.md: retired technical reference {retired}"
    ]


def test_allows_retired_reference_in_openspec_migration_record(tmp_path):
    retired = "docs" + "/database.md"
    relative = _write(
        tmp_path,
        "openspec/changes/remove-snapshot/tasks.md",
        f"- [ ] Remove `{retired}`.\n",
    )

    assert repository_state_violations(tmp_path, [relative]) == []


def test_rejects_project_specific_openspec_configuration(tmp_path):
    relative = _write(
        tmp_path,
        "openspec/config.yaml",
        "schema: spec-driven\ncontext: project snapshot\n",
    )

    assert repository_state_violations(tmp_path, [relative]) == [
        "openspec/config.yaml: project-specific OpenSpec configuration is not allowed"
    ]


def test_accepts_minimal_openspec_configuration(tmp_path):
    relative = _write(tmp_path, "openspec/config.yaml", "schema: spec-driven\n")

    assert repository_state_violations(tmp_path, [relative]) == []


@pytest.mark.parametrize("retired", _RETIRED_SPIKE_REFERENCES)
def test_detects_superseded_r1_spike_path(tmp_path, retired):
    relative = _write(tmp_path, retired, "retired spike\n")

    assert repository_state_violations(tmp_path, [relative]) == [
        f"{retired}: superseded R1 spike path"
    ]


def test_detects_superseded_r1_spike_reference(tmp_path):
    retired = "project-configs" + "/spikes/r1-viability.conf"
    relative = _write(tmp_path, "AGENTS.md", f"Run `{retired}`.\n")

    assert repository_state_violations(tmp_path, [relative]) == [
        f"AGENTS.md: superseded R1 spike reference {retired}"
    ]


def test_repository_knowledge_has_one_authority():
    assert repository_state_violations(REPOSITORY_ROOT) == []
