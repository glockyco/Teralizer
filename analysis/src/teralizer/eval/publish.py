"""Deliver generated figures to a repository that prints them.

Which figures a consumer takes, and the file name each lands under, are the
consumer's choice, not a transform of the figure key. One consumer prefixes a
key it prints (``mutation_detection_comparison`` becomes
``teralizer-mutation-detection-comparison``) while leaving another alone
(``teralizer_efficiency`` becomes ``teralizer-efficiency``), and a third figure
is printed by nobody. No rule derived here gets all three right, so the consumer
declares them.

The declaration lives at the publish destination, in ``publish.toml``:

    [figures]
    teralizer_efficiency = "figures/teralizer-efficiency.pdf"

Values are paths relative to the consuming repository's root. The thesis keeps
figures above its publish destination, so paths relative to the file itself would
all start with ``../..`` and a directory move would silently retarget them out of
the repository. A root gives the containment check a boundary instead.
"""

from __future__ import annotations

import shutil
import subprocess
import tomllib
from dataclasses import dataclass
from pathlib import Path

from teralizer.eval.artifacts import ArtifactSet, RenderTarget

DECLARATION_NAME = "publish.toml"


class PublishError(RuntimeError):
    """Raised with every failure found, not just the first: renaming a figure key
    breaks every consumer that names it, and reporting one at a time turns one
    edit into several publish attempts."""

    def __init__(self, reasons: list[str]) -> None:
        super().__init__("; ".join(reasons))
        self.reasons = reasons


@dataclass(frozen=True)
class FigureDeclaration:
    """A consumer's figure requests, resolved against its repository root.

    Every target is inside ``root``: containment is a static property of the
    declaration, so it is checked here rather than at delivery. That fails a bad
    path before a report run instead of after one.
    """

    root: Path
    targets: dict[str, Path]

    def __post_init__(self) -> None:
        escaping = [
            f"declared path for '{key}' escapes {self.root}: {target}"
            for key, target in sorted(self.targets.items())
            if not target.is_relative_to(self.root)
        ]
        if escaping:
            raise PublishError(escaping)


def consumer_root(destination: Path) -> Path:
    """The consuming repository's root. Publishing already requires git on both
    sides -- for provenance here and for the uncommitted-change guard there -- so
    this adds no dependency."""
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


def read_declaration(destination: Path) -> FigureDeclaration | None:
    """None when the destination declares no figures, either because it has no
    declaration file or because the file carries no ``[figures]`` table."""
    path = destination / DECLARATION_NAME
    if not path.is_file():
        return None
    try:
        document = tomllib.loads(path.read_text())
    except tomllib.TOMLDecodeError as exc:
        raise PublishError([f"{path} is not readable as TOML: {exc}"]) from exc
    figures = document.get("figures") or {}
    if not isinstance(figures, dict):
        raise PublishError([f"{path}: [figures] must be a table of key to path"])
    if not figures:
        return None
    root = consumer_root(destination)
    reasons = [
        f"{path}: figure '{key}' must map to a path string, got {type(value).__name__}"
        for key, value in figures.items()
        if not isinstance(value, str)
    ]
    if reasons:
        raise PublishError(reasons)
    return FigureDeclaration(
        root=root,
        targets={key: (root / value).resolve() for key, value in figures.items()},
    )


def _uncommitted(declaration: FigureDeclaration) -> list[str]:
    """A published figure is generated, so a local edit to one means the consumer
    wants something the generator does not produce. Overwriting it loses that
    work silently.

    This lives here rather than in the publish script because only the parsed
    declaration knows which paths get written, and a guard that has to be kept in
    step with them by hand is a guard that drifts.
    """
    result = subprocess.run(
        ["git", "-C", str(declaration.root), "status", "--porcelain", "--"]
        + [str(target) for target in sorted(declaration.targets.values())],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return [f"cannot check {declaration.root} for uncommitted figures"]
    return [
        f"refusing to overwrite uncommitted change to {line[3:]}"
        for line in result.stdout.splitlines()
        if line.strip()
    ]


def deliver(declaration: FigureDeclaration, artifacts: ArtifactSet) -> list[Path]:
    """Copy every declared figure, or nothing.

    A declared key that nothing emits is an error: the consumer is printing a
    figure the generator stopped producing, which is the state this exists to
    end. An emitted figure that nobody declares is not an error -- a figure with
    no consumer is normal.

    Containment was settled when the declaration was built, so the only failure
    left here is the one that depends on what this run produced.
    """
    emitted = {
        artifact.id.key: artifact.path
        for artifact in artifacts.by_target(RenderTarget.FIGURES)
    }
    reasons = [
        f"declared figure '{key}' is not emitted by any report in this run"
        for key in sorted(declaration.targets)
        if key not in emitted
    ]
    reasons += _uncommitted(declaration)
    if reasons:
        raise PublishError(reasons)
    written: list[Path] = []
    for key, target in sorted(declaration.targets.items()):
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(emitted[key], target)
        written.append(target)
    return written
