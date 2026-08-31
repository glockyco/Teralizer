"""Deliver the generated artifacts a consuming repository declares.

The declaration lives at the publish destination in ``publish.toml``. Each
section is an invocable render target, and each entry maps the generated key to
a path relative to the consuming repository root::

    [figures]
    teralizer_efficiency = "figures/teralizer-efficiency.pdf"

    [latex]
    rq4 = "chapters/05-teralizer/tables/tab-rq4.tex"

Consumer paths are explicit because generated names do not determine where a
repository retains an artifact.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import tomllib
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from teralizer.eval.artifacts import ArtifactId, ArtifactSet, RenderTarget

DECLARATION_NAME = "publish.toml"
_DECLARABLE_TARGETS = frozenset(
    target for target in RenderTarget if target is not RenderTarget.MANIFEST
)


def _copy2(source: Path, target: Path) -> object:
    return shutil.copy2(source, target)


class PublishError(RuntimeError):
    """Raised with every publication failure found, not only the first."""

    def __init__(self, reasons: list[str]) -> None:
        super().__init__("; ".join(reasons))
        self.reasons = reasons


@dataclass(frozen=True)
class ArtifactDeclaration:
    """A consumer's artifact requests, resolved inside its repository root."""

    root: Path
    targets: dict[ArtifactId, Path]

    def __post_init__(self) -> None:
        root = self.root.resolve()
        escaping = [
            f"declared path for '{artifact_id.target.value}/{artifact_id.key}' "
            f"escapes {root}: {path.resolve()}"
            for artifact_id, path in sorted(self.targets.items())
            if not path.resolve().is_relative_to(root)
        ]
        if escaping:
            raise PublishError(escaping)

    @property
    def required_targets(self) -> frozenset[RenderTarget]:
        """Render targets the consuming repository requires from an invocation."""
        return frozenset(artifact_id.target for artifact_id in self.targets)


def consumer_root(destination: Path) -> Path:
    """Resolve the consuming Git repository root."""
    try:
        top = subprocess.run(
            ["git", "-C", str(destination), "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        raise PublishError(
            [f"cannot resolve the consuming repository root for {destination}"]
        ) from exc
    return Path(top).resolve()


def read_declaration(destination: Path) -> ArtifactDeclaration | None:
    """Read all declared render targets, or return ``None`` when none exist."""
    path = destination / DECLARATION_NAME
    if not path.is_file():
        return None
    try:
        document = tomllib.loads(path.read_text())
    except tomllib.TOMLDecodeError as exc:
        raise PublishError([f"{path} is not readable as TOML: {exc}"]) from exc

    known_sections = {target.value: target for target in _DECLARABLE_TARGETS}
    reasons = [
        f"{path}: unknown render target section [{section}]"
        for section in sorted(document)
        if section not in known_sections
    ]
    entries: dict[ArtifactId, str] = {}
    for section, value in document.items():
        target = known_sections.get(section)
        if target is None:
            continue
        if not isinstance(value, dict):
            reasons.append(
                f"{path}: [{section}] must be a table of artifact key to path"
            )
            continue
        for key, declared_path in value.items():
            if not isinstance(declared_path, str):
                reasons.append(
                    f"{path}: artifact '{section}/{key}' must map to a path string, "
                    f"got {type(declared_path).__name__}"
                )
                continue
            entries[ArtifactId(target, key)] = declared_path
    if reasons:
        raise PublishError(reasons)
    if not entries:
        return None

    root = consumer_root(destination)
    return ArtifactDeclaration(
        root=root,
        targets={
            artifact_id: (root / declared_path).resolve()
            for artifact_id, declared_path in entries.items()
        },
    )


def require_clean_generator_worktree(path: Path) -> None:
    """Reject publication when the generator Git worktree has any changes."""
    try:
        root = Path(
            subprocess.run(
                ["git", "-C", str(path), "rev-parse", "--show-toplevel"],
                capture_output=True,
                text=True,
                check=True,
            ).stdout.strip()
        ).resolve()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        raise PublishError(
            [f"cannot resolve the generator repository root for {path}"]
        ) from exc
    result = subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain", "--untracked-files=all"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise PublishError([f"cannot check generator worktree {root}"])
    reasons = [
        f"generator worktree has uncommitted change: {line[3:]}"
        for line in result.stdout.splitlines()
        if line.strip()
    ]
    if reasons:
        raise PublishError(reasons)


def _uncommitted(declaration: ArtifactDeclaration) -> list[str]:
    """Report consumer edits to any path that delivery would overwrite."""
    paths = sorted(set(declaration.targets.values()))
    result = subprocess.run(
        ["git", "-C", str(declaration.root), "status", "--porcelain", "--"]
        + [str(path) for path in paths],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return [f"cannot check {declaration.root} for uncommitted artifacts"]
    return [
        f"refusing to overwrite uncommitted change to {line[3:]}"
        for line in result.stdout.splitlines()
        if line.strip()
    ]


def validate(declaration: ArtifactDeclaration, artifacts: ArtifactSet) -> None:
    """Validate a declaration against one complete artifact set without copying."""
    reasons = [
        f"declared artifact '{artifact_id.target.value}/{artifact_id.key}' "
        "is not emitted by any report in this run"
        for artifact_id in sorted(declaration.targets)
        if artifact_id not in artifacts
    ]
    reasons += _uncommitted(declaration)
    if reasons:
        raise PublishError(reasons)


def deliver(
    declaration: ArtifactDeclaration,
    artifacts: ArtifactSet,
    *,
    copy: Callable[[Path, Path], object] = _copy2,
    replace: Callable[[Path, Path], object] = os.replace,
) -> list[Path]:
    """Atomically replace every declared artifact and no undeclared artifact."""
    validate(declaration, artifacts)
    declared = sorted(declaration.targets.items())
    with tempfile.TemporaryDirectory(
        prefix=".publish-stage-", dir=declaration.root
    ) as stage_name:
        stage_root = Path(stage_name)
        staged: list[tuple[Path, Path]] = []
        for index, (artifact_id, target) in enumerate(declared):
            staged_path = stage_root / "artifacts" / f"{index:06d}"
            staged_path.parent.mkdir(parents=True, exist_ok=True)
            copy(artifacts.get(artifact_id).path, staged_path)
            staged.append((staged_path, target))

        backup_root = stage_root / "backups"
        backup_root.mkdir()
        backups: list[tuple[Path, Path]] = []
        installed: list[Path] = []
        created_dirs: list[Path] = []

        def ensure_parent(path: Path) -> None:
            missing: list[Path] = []
            parent = path.parent
            while parent != declaration.root and not parent.exists():
                missing.append(parent)
                parent = parent.parent
            for directory in reversed(missing):
                directory.mkdir()
                created_dirs.append(directory)

        try:
            for index, (staged_path, target) in enumerate(staged):
                ensure_parent(target)
                if target.exists():
                    backup = backup_root / f"{index:06d}"
                    replace(target, backup)
                    backups.append((target, backup))
                replace(staged_path, target)
                installed.append(target)
        except Exception:
            for target in reversed(installed):
                if target.exists():
                    target.unlink()
            for target, backup in reversed(backups):
                if backup.exists():
                    replace(backup, target)
            for directory in reversed(created_dirs):
                try:
                    directory.rmdir()
                except OSError:
                    pass
            raise
    return [target for _, target in declared]
