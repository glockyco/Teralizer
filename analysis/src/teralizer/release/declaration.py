"""Typed release declaration: identity, components, workflows and platforms."""

from __future__ import annotations

import re
import tomllib
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from teralizer import corpora
from teralizer.release.inventory import COMPONENTS, Inventory

DEFAULT_DECLARATION = (
    Path(__file__).resolve().parents[4] / "scripts/packaging/release/release.toml"
)

PLATFORM_STATUSES = ("release-tested", "compatibility-tested", "unsupported")
NETWORK_PHASES = ("setup", "collection")
CORE_ROOT = "teralizer"
_DOI = re.compile(r"^10\.\d{4,9}/\S+$")


class DeclarationError(ValueError):
    """The release declaration is invalid or incomplete."""


@dataclass(frozen=True)
class Component:
    id: str
    root: str
    purpose: str
    corpus: str | None = None


@dataclass(frozen=True)
class Workflow:
    id: str
    command: str
    components: tuple[str, ...]
    network: tuple[str, ...]
    purpose: str
    full_command: str | None = None


@dataclass(frozen=True)
class Platform:
    id: str
    status: str
    host: str


@dataclass(frozen=True)
class Declaration:
    name: str
    version: str
    title: str
    concept_doi: str
    version_doi: str
    previous_version_dois: tuple[str, ...]
    code_license: str
    content_license: str
    paper_title: str
    paper_authors: tuple[str, ...]
    paper_doi: str
    components: tuple[Component, ...]
    workflows: tuple[Workflow, ...]
    platforms: tuple[Platform, ...]

    def component(self, component_id: str) -> Component:
        for candidate in self.components:
            if candidate.id == component_id:
                return candidate
        raise KeyError(component_id)


def _table(document: dict[str, object], key: str) -> dict[str, object]:
    value = document.get(key)
    if not isinstance(value, dict):
        raise DeclarationError(f"[{key}] must be a table")
    return value


def _tables(document: dict[str, object], key: str) -> list[dict[str, object]]:
    value = document.get(key, [])
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise DeclarationError(f"[[{key}]] must be an array of tables")
    return value


def _text(
    table: dict[str, object], key: str, where: str, *, empty: bool = False
) -> str:
    value = table.get(key)
    if not isinstance(value, str) or (not empty and not value.strip()):
        raise DeclarationError(f"{where}: {key!r} must be a non-empty string")
    return value


def _texts(table: dict[str, object], key: str, where: str) -> tuple[str, ...]:
    value = table.get(key, [])
    if not isinstance(value, list) or not all(
        isinstance(item, str) and item for item in value
    ):
        raise DeclarationError(f"{where}: {key!r} must be a list of strings")
    return tuple(value)


def _optional(table: dict[str, object], key: str, where: str) -> str | None:
    return _text(table, key, where) if key in table else None


def parse(document: dict[str, object]) -> Declaration:
    release = _table(document, "release")
    paper = _table(document, "paper")
    components = tuple(
        Component(
            id=_text(entry, "id", "component"),
            root=_text(entry, "root", "component"),
            purpose=_text(entry, "purpose", "component"),
            corpus=_optional(entry, "corpus", "component"),
        )
        for entry in _tables(document, "component")
    )
    workflows = tuple(
        Workflow(
            id=_text(entry, "id", "workflow"),
            command=_text(entry, "command", "workflow"),
            components=_texts(entry, "components", "workflow"),
            network=_texts(entry, "network", "workflow"),
            purpose=_text(entry, "purpose", "workflow"),
            full_command=_optional(entry, "full_command", "workflow"),
        )
        for entry in _tables(document, "workflow")
    )
    platforms = tuple(
        Platform(
            id=_text(entry, "id", "platform"),
            status=_text(entry, "status", "platform"),
            host=_text(entry, "host", "platform"),
        )
        for entry in _tables(document, "platform")
    )
    declaration = Declaration(
        name=_text(release, "name", "release"),
        version=_text(release, "version", "release"),
        title=_text(release, "title", "release"),
        concept_doi=_text(release, "concept_doi", "release"),
        version_doi=_text(release, "version_doi", "release", empty=True),
        previous_version_dois=_texts(release, "previous_version_dois", "release"),
        code_license=_text(release, "code_license", "release"),
        content_license=_text(release, "content_license", "release"),
        paper_title=_text(paper, "title", "paper"),
        paper_authors=_texts(paper, "authors", "paper"),
        paper_doi=_text(paper, "doi", "paper"),
        components=components,
        workflows=workflows,
        platforms=platforms,
    )
    _validate(declaration)
    return declaration


def _validate(declaration: Declaration) -> None:
    for label, doi in [
        ("concept_doi", declaration.concept_doi),
        ("paper doi", declaration.paper_doi),
        *[("previous version doi", doi) for doi in declaration.previous_version_dois],
    ]:
        if not _DOI.match(doi):
            raise DeclarationError(f"{label} is not a DOI: {doi!r}")
    if declaration.version_doi and not _DOI.match(declaration.version_doi):
        raise DeclarationError(f"version_doi is not a DOI: {declaration.version_doi!r}")
    if declaration.version_doi in (
        declaration.concept_doi,
        *declaration.previous_version_dois,
    ):
        raise DeclarationError("version_doi must be a new version identifier")

    ids = [component.id for component in declaration.components]
    if sorted(ids) != sorted(COMPONENTS):
        raise DeclarationError(
            f"components must be exactly {sorted(COMPONENTS)}, got {sorted(ids)}"
        )
    roots = [PurePosixPath(component.root) for component in declaration.components]
    for component, root in zip(declaration.components, roots, strict=True):
        expected = (
            PurePosixPath(CORE_ROOT)
            if component.id == "core"
            else PurePosixPath(CORE_ROOT, "components", component.id)
        )
        if root != expected:
            raise DeclarationError(f"component {component.id}: root must be {expected}")
    registered = {entry.id for entry in corpora.load().entries}
    for component in declaration.components:
        if component.id.startswith("corpus-"):
            if component.corpus != component.id.removeprefix("corpus-"):
                raise DeclarationError(
                    f"component {component.id}: must name corpus {component.id.removeprefix('corpus-')}"
                )
            if component.corpus not in registered:
                raise DeclarationError(
                    f"component {component.id}: corpus {component.corpus} is not registered"
                )
        elif component.corpus is not None:
            raise DeclarationError(
                f"component {component.id}: only corpus components name a corpus"
            )

    workflow_ids = [workflow.id for workflow in declaration.workflows]
    if len(workflow_ids) != len(set(workflow_ids)):
        raise DeclarationError("workflow ids must be unique")
    for workflow in declaration.workflows:
        if "core" not in workflow.components:
            raise DeclarationError(f"workflow {workflow.id}: every workflow needs core")
        unknown = sorted(set(workflow.components) - set(ids))
        if unknown:
            raise DeclarationError(
                f"workflow {workflow.id}: unknown components {unknown}"
            )
        if {"projects-real-world-sample", "projects-real-world-full"} <= set(
            workflow.components
        ):
            raise DeclarationError(
                f"workflow {workflow.id}: the real-world sample and full scopes are alternatives"
            )
        unknown_phases = sorted(set(workflow.network) - set(NETWORK_PHASES))
        if unknown_phases:
            raise DeclarationError(
                f"workflow {workflow.id}: unknown network phases {unknown_phases}"
            )
    used = {
        component
        for workflow in declaration.workflows
        for component in workflow.components
    }
    unused = sorted(set(ids) - used)
    if unused:
        raise DeclarationError(f"components no workflow needs: {unused}")

    statuses = {platform.status for platform in declaration.platforms}
    if not statuses <= set(PLATFORM_STATUSES):
        raise DeclarationError(f"platform statuses must be among {PLATFORM_STATUSES}")
    if "release-tested" not in statuses:
        raise DeclarationError("at least one platform must be release-tested")


def load(path: Path = DEFAULT_DECLARATION) -> Declaration:
    with path.open("rb") as handle:
        return parse(tomllib.load(handle))


def production_problems(declaration: Declaration, inventory: Inventory) -> list[str]:
    """Completeness rules that only a publishable release candidate must satisfy."""
    problems: list[str] = []
    if not declaration.version_doi:
        problems.append("version_doi is not reserved")
    for component in declaration.components:
        if not any(
            item.component == component.id and item.disposition == "payload"
            for item in inventory.inputs
        ):
            problems.append(f"component {component.id} has no payload input")
    return problems
