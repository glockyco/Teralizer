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

import shutil
import subprocess
import tomllib
from dataclasses import dataclass
from pathlib import Path

from teralizer.eval.artifacts import ArtifactId, ArtifactSet, RenderTarget

DECLARATION_NAME = "publish.toml"
_DECLARABLE_TARGETS = frozenset(
    target for target in RenderTarget if target is not RenderTarget.MANIFEST
)


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


def deliver(declaration: ArtifactDeclaration, artifacts: ArtifactSet) -> list[Path]:
    """Copy every declared artifact and no undeclared artifact."""
    validate(declaration, artifacts)
    written: list[Path] = []
    for artifact_id, target in sorted(declaration.targets.items()):
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(artifacts.get(artifact_id).path, target)
        written.append(target)
    return written
