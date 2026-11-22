"""Build a release candidate: component archives plus the standalone release files.

A candidate is built into a fresh directory that must not exist. Every archive is
written from the member plan and verified through an independent reader before
the release files are written: `release-manifest.json` and `checksums.sha256` over
the archives and the manifest. These are the exact upload set of the Zenodo record.
`zenodo.json` holds the structured deposit metadata and is not uploaded. The record
description is written on Zenodo, so changing it needs no rebuild. A failed build
leaves only a `.partial` directory, which is never a candidate.
"""

from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path

from teralizer.release import (
    archive,
    documents,
    plan,
    provenance,
    realworld,
    source,
    thirdparty,
)
from teralizer.release.declaration import Declaration
from teralizer.release.inventory import Inventory

ALTERNATIVE_SCOPES = ("projects-real-world-sample", "projects-real-world-full")
# Inputs whose files come from repositories, so their commits are recorded.
_ATTRIBUTED_INPUTS = frozenset(
    {"source-tree", "evosuite-jar", "reference-figures", "jpf-symbc", "jpf-core"}
)


class BuildError(RuntimeError):
    """The candidate could not be built completely."""


@dataclass(frozen=True)
class BuiltArchive:
    component: str
    path: Path
    sha256: str
    bytes: int
    unpacked_bytes: int
    manifest_sha256: str


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _third_party(
    component_id: str, root: str, entries: list[archive.Entry], sources: plan.Sources
) -> bytes:
    """The third-party records of a project component."""
    prefix = root + "/"
    members = {
        entry.name.removeprefix(prefix): entry.source
        for entry in entries
        if entry.source is not None
    }
    real_world = component_id in ALTERNATIVE_SCOPES
    origins: dict[str, tuple[str, str]] = {}
    if real_world:
        assert sources.real_world is not None
        mapped = realworld.checkout_map(sources.evidence / realworld.CHECKOUT_MAP)
        origins = {
            f"projects/{project.name}": (project.origin, project.revision)
            for project in mapped
        }
    base = sources.real_world if real_world else sources.checkout
    assert base is not None
    roots = {
        thirdparty.project_root(path)
        for path in members
        if not path.startswith(thirdparty.UPSTREAM_DIRECTORY + "/")
    }
    try:
        return thirdparty.component_records(
            component_id,
            members,
            {project: base / project for project in roots},
            origins,
            thirdparty.declared(sources.source / thirdparty.SOURCES),
            thirdparty.upstream_pins(sources.source / thirdparty.UPSTREAM_PINS),
            sources.upstream,
        )
    except thirdparty.ThirdPartyError as error:
        raise BuildError(str(error)) from error


def _canonical(document: object) -> bytes:
    return (json.dumps(document, indent=2, sort_keys=True) + "\n").encode()


def _release_identity(declaration: Declaration, revision: str) -> dict[str, str]:
    return {
        "name": declaration.name,
        "version": declaration.version,
        "version_doi": declaration.version_doi or "unreserved",
        "concept_doi": declaration.concept_doi,
        "source_revision": revision,
    }


def _readme(declaration: Declaration, component_id: str) -> bytes:
    component = declaration.component(component_id)
    version_doi = declaration.version_doi or "reserved before publication"
    lines = [
        f"# {component.id}",
        "",
        component.purpose,
        "",
        f"Release: {declaration.title}, version {declaration.version}, "
        f"version DOI {version_doi}, concept DOI {declaration.concept_doi}.",
        f"Extract this archive into the same directory as the core archive. It writes only {component.root}/.",
        "",
        "Workflows that use it, with every component they need:",
        "",
    ]
    for workflow in declaration.workflows:
        if component_id in workflow.components:
            lines.append(f"- `./{workflow.command}`: {', '.join(workflow.components)}")
    lines += [
        "",
        "The standalone release-manifest.json lists every archive of this release and its checksum.",
        "The core archive holds the complete instructions, licenses and citation, starting at teralizer/README.md.",
        "",
    ]
    return "\n".join(lines).encode()


def _zenodo(declaration: Declaration) -> dict[str, object]:
    """Structured metadata for the Zenodo deposit. The record description lives on Zenodo."""
    return {
        "metadata": {
            "upload_type": "software",
            "title": declaration.title,
            "version": declaration.version,
            "creators": [
                {
                    "name": creator.name,
                    "affiliation": creator.affiliation,
                    "orcid": creator.orcid,
                }
                for creator in declaration.creators
            ],
            "keywords": list(declaration.keywords),
            "license": declaration.code_license.lower(),
            "related_identifiers": [
                {
                    "identifier": declaration.paper_doi,
                    "relation": "isSupplementTo",
                    "scheme": "doi",
                },
                {
                    "identifier": declaration.repository,
                    "relation": "isDerivedFrom",
                    "scheme": "url",
                },
            ],
        }
    }


def _members_with_hashes(entries: Iterable[archive.Entry]) -> list[archive.Member]:
    members = []
    for entry in entries:
        if entry.link is not None:
            target = entry.link.encode()
            digest, size = hashlib.sha256(target).hexdigest(), len(target)
        elif entry.source is not None:
            digest, size = _sha256(entry.source), entry.source.stat().st_size
        else:
            assert entry.content is not None
            digest, size = hashlib.sha256(entry.content).hexdigest(), len(entry.content)
        members.append(
            archive.Member(entry.name, digest, size, entry.executable, entry.link)
        )
    return members


def _payload_entry(member: archive.Member) -> dict[str, object]:
    if member.link is not None:
        return {"path": member.name, "link": member.link}
    return {
        "path": member.name,
        "sha256": member.sha256,
        "bytes": member.bytes,
        "executable": member.executable,
    }


def build_candidate(
    declaration: Declaration,
    inventory: Inventory,
    *,
    repo: Path,
    evidence: Path,
    corpus_package: Path,
    candidate: Path,
    components: Iterable[str],
    real_world: Path | None = None,
    upstream: Path | None = None,
) -> list[BuiltArchive]:
    """Build the selected components and the release files into ``candidate``."""
    selected = list(components)
    if candidate.exists():
        raise BuildError(f"candidate directory already exists: {candidate}")
    partial = candidate.with_name(candidate.name + ".partial")
    if partial.exists():
        raise BuildError(f"remove the failed candidate first: {partial}")
    provenance.require_clean(repo)
    work = partial / "work"
    work.mkdir(parents=True)

    revision = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    nested, _ = source.materialize(repo, revision, work / "source")
    sources = plan.Sources(
        work / "source",
        evidence,
        corpus_package,
        repo,
        real_world=real_world,
        upstream=upstream,
    )
    try:
        plans = {
            item.component: item for item in plan.build(declaration, inventory, sources)
        }
    except plan.PlanError as error:
        raise BuildError(str(error)) from error
    problems = plan.validate(plans.values(), inventory, required=selected)
    if problems:
        raise BuildError("invalid plan:\n" + "\n".join(problems))

    release = _release_identity(declaration, revision)
    built: list[BuiltArchive] = []
    records: dict[str, bytes] = {}
    for component_id in selected:
        component = declaration.component(component_id)
        component_plan = plans[component_id]
        entries = list(component_plan.entries)
        licenses: list[dict[str, str]] = []
        provenance_section: dict[str, object] = {
            "inputs": sorted(component_plan.inputs)
        }
        if component_id == "core":
            prefix = component.root + "/"
            attributed = [
                entry.name.removeprefix(prefix)
                for entry in entries
                if entry.source is not None
                and entry.source.is_relative_to(work / "source")
            ]
            provenance_section["files"] = provenance.file_revisions(
                repo, nested, attributed
            )
            licenses = [
                {
                    "scope": "source code",
                    "license": declaration.code_license,
                    "file": f"{prefix}LICENSE-MIT",
                },
                {
                    "scope": "data and documentation",
                    "license": declaration.content_license,
                    "file": f"{prefix}LICENSE-CC-BY-4.0",
                },
            ]
        else:
            entries.append(
                archive.Entry(
                    f"{component.root}/README.md",
                    content=_readme(declaration, component_id),
                )
            )
            if component.corpus is not None:
                license_text = work / "source" / "LICENSE-CC-BY-4.0"
                entries.append(
                    archive.Entry(
                        f"{component.root}/LICENSE-CC-BY-4.0", source=license_text
                    )
                )
                licenses = [
                    {
                        "scope": "database dump",
                        "license": declaration.content_license,
                        "file": f"{component.root}/LICENSE-CC-BY-4.0",
                    }
                ]
                # The corpus package entry carries the corpus identity, its
                # database facts and producer provenance, so an archive opened
                # alone still identifies its corpus.
                provenance_section["corpus_package_entry"] = plan.corpus_entry(
                    corpus_package, component.corpus
                )
            else:
                records[component_id] = _third_party(
                    component_id, component.root, entries, sources
                )
                entries.append(
                    archive.Entry(
                        f"{component.root}/{thirdparty.RECORDS}",
                        content=records[component_id],
                    )
                )
                licenses = [
                    {
                        "scope": "analyzed projects, each under its authors' terms",
                        "license": "per project in third-party.json",
                        "file": f"{component.root}/{thirdparty.RECORDS}",
                    }
                ]
        requires = [] if component_id == "core" else ["core"]
        incompatible = [
            scope
            for scope in ALTERNATIVE_SCOPES
            if component_id in ALTERNATIVE_SCOPES and scope != component_id
        ]

        payload = _members_with_hashes(entries)
        manifest = {
            "format": documents.COMPONENT_MANIFEST[0],
            "format_version": documents.COMPONENT_MANIFEST[1],
            "component": component_id,
            "release": release,
            "root": component.root,
            "purpose": component.purpose,
            "requires": requires,
            "incompatible_with": incompatible,
            "licenses": licenses,
            "provenance": provenance_section,
            "payload": [
                _payload_entry(member)
                for member in sorted(payload, key=lambda item: item.name)
            ],
        }
        documents.validate_component_manifest(manifest)
        manifest_bytes = _canonical(manifest)
        manifest_entry = archive.Entry(
            f"{component.root}/component-manifest.json", content=manifest_bytes
        )
        path = partial / f"{declaration.name}-{component_id}.zip"
        members = archive.write(path, [*entries, manifest_entry])
        failures = archive.verify(path, component.root, members)
        if failures:
            raise BuildError("archive verification failed:\n" + "\n".join(failures))
        built.append(
            BuiltArchive(
                component=component_id,
                path=path,
                sha256=_sha256(path),
                bytes=path.stat().st_size,
                unpacked_bytes=sum(member.bytes for member in members),
                manifest_sha256=hashlib.sha256(manifest_bytes).hexdigest(),
            )
        )

    if set(ALTERNATIVE_SCOPES) <= set(records):
        sample, full = (
            {item["path"]: item for item in json.loads(records[scope])["projects"]}
            for scope in ALTERNATIVE_SCOPES
        )
        differing = sorted(
            path for path, item in sample.items() if full.get(path) != item
        )
        if differing:
            raise BuildError(
                f"sample records differ from the full component: {differing[:5]}"
            )
    included = {item.component for item in built}
    release_manifest = {
        "format": documents.RELEASE_MANIFEST[0],
        "format_version": documents.RELEASE_MANIFEST[1],
        "release": release,
        "paper": {
            "title": declaration.paper_title,
            "authors": list(declaration.paper_authors),
            "doi": declaration.paper_doi,
        },
        "archives": [
            {
                "component": item.component,
                "filename": item.path.name,
                "sha256": item.sha256,
                "bytes": item.bytes,
                "unpacked_bytes": item.unpacked_bytes,
                "purpose": declaration.component(item.component).purpose,
                "requires": [] if item.component == "core" else ["core"],
                "component_manifest_sha256": item.manifest_sha256,
            }
            for item in built
        ],
        "workflows": [
            {
                "id": workflow.id,
                "command": workflow.command,
                "components": list(workflow.components),
            }
            for workflow in declaration.workflows
            if set(workflow.components) <= included
        ],
    }
    documents.validate_release_manifest(release_manifest)
    manifest_bytes = _canonical(release_manifest)
    (partial / "release-manifest.json").write_bytes(manifest_bytes)
    lines = [f"{item.sha256}  {item.path.name}" for item in built]
    lines.append(f"{hashlib.sha256(manifest_bytes).hexdigest()}  release-manifest.json")
    (partial / "checksums.sha256").write_text(
        "\n".join(sorted(lines, key=lambda line: line[66:])) + "\n"
    )
    (partial / "zenodo.json").write_bytes(_canonical(_zenodo(declaration)))

    shutil.rmtree(work)
    partial.rename(candidate)
    return [
        BuiltArchive(
            item.component,
            candidate / item.path.name,
            item.sha256,
            item.bytes,
            item.unpacked_bytes,
            item.manifest_sha256,
        )
        for item in built
    ]
