"""Typed identities and ownership for rendered report artifacts."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from pathlib import Path
from typing import Self


class RenderTarget(StrEnum):
    MARKDOWN = "md"
    LATEX = "latex"
    CSV = "csv"
    FIGURES = "figures"
    MANIFEST = "manifest"


class RunAggregate(StrEnum):
    RUN = "run"


ArtifactOwner = str | RunAggregate


@dataclass(frozen=True, order=True)
class ArtifactId:
    target: RenderTarget
    key: str

    def __post_init__(self) -> None:
        if not self.key:
            raise ValueError("artifact key must not be empty")


@dataclass(frozen=True)
class RenderedArtifact:
    id: ArtifactId
    path: Path
    owner: ArtifactOwner

    def __post_init__(self) -> None:
        if isinstance(self.owner, str) and not self.owner:
            raise ValueError("artifact owner must not be empty")


@dataclass
class ArtifactSet:
    """Artifacts emitted beneath one staging root, unique by target and key."""

    root: Path
    _artifacts: dict[ArtifactId, RenderedArtifact] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.root = self.root.resolve()

    def add(self, artifact: RenderedArtifact) -> None:
        path = artifact.path.resolve()
        if not path.is_relative_to(self.root):
            raise ValueError(
                f"artifact {artifact.id.target.value}/{artifact.id.key} path "
                f"escapes staging root {self.root}: {path}"
            )
        prior = self._artifacts.get(artifact.id)
        if prior is not None:
            raise ValueError(
                f"artifact {artifact.id.target.value}/{artifact.id.key} is emitted "
                f"by both {prior.owner} and {artifact.owner}"
            )
        self._artifacts[artifact.id] = RenderedArtifact(
            artifact.id, path, artifact.owner
        )

    def merge(self, other: ArtifactSet) -> Self:
        if self.root != other.root:
            raise ValueError(
                f"cannot merge artifact roots {self.root} and {other.root}"
            )
        for artifact in other:
            self.add(artifact)
        return self

    def __iter__(self):
        return iter(self._artifacts.values())

    def __len__(self) -> int:
        return len(self._artifacts)

    def __contains__(self, artifact_id: ArtifactId) -> bool:
        return artifact_id in self._artifacts

    def get(self, artifact_id: ArtifactId) -> RenderedArtifact:
        return self._artifacts[artifact_id]

    def by_target(self, target: RenderTarget) -> tuple[RenderedArtifact, ...]:
        return tuple(artifact for artifact in self if artifact.id.target is target)
