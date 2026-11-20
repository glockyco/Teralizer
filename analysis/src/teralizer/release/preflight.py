"""Workflow preflight: verify every component a workflow needs before it runs.

Preflight reads the standalone release manifest and the component manifests of
the extracted components. It reports every missing, mixed-release, changed or
incomplete component at once and never repairs anything. Recovery is to remove
the component root, verify the archive checksum and extract it again.
"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

from teralizer.release.documents import (
    DocumentError,
    validate_component_manifest,
    validate_release_manifest,
)

RECOVERY = (
    "remove {root}/, check {filename} against checksums.sha256, "
    "and extract it again with unzip"
)


@dataclass(frozen=True)
class Result:
    workflow: str
    components: tuple[str, ...]
    problems: tuple[str, ...]

    @property
    def passed(self) -> bool:
        return not self.problems


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _load(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def check_component(
    workspace: Path, archive: Mapping[str, object], release: Mapping[str, object]
) -> list[str]:
    """Problems of one extracted component, compared with its release manifest entry."""
    component = str(archive["component"])
    root = "teralizer" if component == "core" else f"teralizer/components/{component}"
    recovery = RECOVERY.format(root=root, filename=archive["filename"])
    manifest_path = workspace / root / "component-manifest.json"
    if not manifest_path.is_file():
        return [
            f"{component}: not extracted ({manifest_path} is missing); extract {archive['filename']}"
        ]
    if _sha256(manifest_path) != archive["component_manifest_sha256"]:
        return [
            f"{component}: component manifest differs from this release; {recovery}"
        ]
    try:
        manifest = validate_component_manifest(_load(manifest_path))
    except (json.JSONDecodeError, DocumentError) as error:
        return [f"{component}: invalid component manifest ({error}); {recovery}"]
    if manifest["release"] != release:
        return [f"{component}: belongs to another release; {recovery}"]

    problems: list[str] = []
    payload = manifest["payload"]
    assert isinstance(payload, list)
    missing = changed = 0
    for entry in payload:
        assert isinstance(entry, Mapping)
        path = workspace / str(entry["path"])
        if not path.is_file():
            missing += 1
        elif path.stat().st_size != entry["bytes"] or _sha256(path) != entry["sha256"]:
            changed += 1
    if missing:
        problems.append(f"{component}: {missing} payload files are missing; {recovery}")
    if changed:
        problems.append(f"{component}: {changed} payload files changed; {recovery}")
    return problems


def run(workspace: Path, release_manifest: Path, workflow: str) -> Result:
    """Verify the components that ``workflow`` needs inside ``workspace``."""
    try:
        manifest = validate_release_manifest(_load(release_manifest))
    except FileNotFoundError:
        return Result(
            workflow, (), (f"release manifest not found: {release_manifest}",)
        )
    except (json.JSONDecodeError, DocumentError) as error:
        return Result(workflow, (), (f"invalid release manifest: {error}",))
    workflows = {str(item["id"]): item for item in manifest["workflows"]}  # type: ignore[union-attr]
    if workflow not in workflows:
        return Result(
            workflow,
            (),
            (f"unknown workflow {workflow}; this release offers {sorted(workflows)}",),
        )
    components = tuple(str(name) for name in workflows[workflow]["components"])  # type: ignore[index, union-attr]
    archives = {str(item["component"]): item for item in manifest["archives"]}  # type: ignore[union-attr]
    release = manifest["release"]
    assert isinstance(release, Mapping)
    problems: list[str] = []
    for component in components:
        problems.extend(check_component(workspace, archives[component], release))
    return Result(workflow, components, tuple(problems))
