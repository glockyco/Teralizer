"""Repository guard for the single OpenSpec planning home."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
_PLANNING_DIRECTORY_NAMES = {"planning", "plans", "roadmap", "roadmaps"}
_PLANNING_RECORD_TYPES = {"audit", "note", "overview", "plan", "spec"}
_RETIRED_REFERENCES = (
    "docs" + "/plans",
    "planning" + "-files",
    "omp" + "-plans",
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


def planning_home_violations(root: Path, paths: list[Path] | None = None) -> list[str]:
    """Return tracked paths that declare or point to a second planning home."""
    violations: set[str] = set()
    for relative in paths if paths is not None else _tracked_paths(root):
        if not relative.parts or relative.parts[0] == "openspec":
            continue

        path = root / relative
        if not path.exists():
            continue

        rendered = relative.as_posix()
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

        for retired in _RETIRED_REFERENCES:
            if retired in text:
                violations.add(f"{rendered}: retired planning reference {retired}")

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

    assert planning_home_violations(tmp_path, [relative]) == [
        "docs/roadmaps/item.md: planning directory outside openspec"
    ]


def test_detects_current_planning_metadata_outside_openspec(tmp_path):
    relative = _write(
        tmp_path,
        "docs/work-item.md",
        "---\ntype: plan\nstatus: active\n---\n# Work item\n",
    )

    assert planning_home_violations(tmp_path, [relative]) == [
        "docs/work-item.md: planning metadata outside openspec"
    ]


def test_detects_retired_path_reference(tmp_path):
    retired = "docs" + "/plans/INDEX.md"
    relative = _write(tmp_path, "AGENTS.md", f"Read `{retired}` first.\n")

    assert planning_home_violations(tmp_path, [relative]) == [
        "AGENTS.md: retired planning reference docs/plans"
    ]


def test_detects_duplicate_planning_guidance(tmp_path):
    destination = "docs" + "/roadmaps/"
    relative = _write(
        tmp_path,
        "AGENTS.md",
        f"Current planning lives in `{destination}`.\n",
    )

    assert planning_home_violations(tmp_path, [relative]) == [
        "AGENTS.md: planning guidance points outside openspec"
    ]


def test_positive_control_reports_injected_path(tmp_path):
    relative = _write(
        tmp_path,
        "docs/roadmap/known-conflict.md",
        "---\ntype: plan\nstatus: active\n---\n# Known conflict\n",
    )

    violations = planning_home_violations(tmp_path, [relative])

    assert violations
    assert all(
        item.startswith("docs/roadmap/known-conflict.md:") for item in violations
    )


def test_repository_has_one_planning_home():
    assert planning_home_violations(REPOSITORY_ROOT) == []
