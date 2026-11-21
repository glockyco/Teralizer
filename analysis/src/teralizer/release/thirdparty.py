"""Third-party records of every shipped project.

Each project component carries `third-party.json`, which lists for every project
root it holds the origin, the revision, the license, and the license files the
release retains. Real-world projects take origin and revision from the checkout
map. The other projects take them from `third-party.toml`, which also names the
upstream repositories whose LICENSE and NOTICE files the release retains next to
projects that do not carry them, such as the JARVIS fixtures, whose payload is
their POM and sources.

licensee, GitHub's license detector, identifies the license files in each
packaged project root. A declared license overrides the detection only where
the declaration states its evidence, such as the Apache license header that
every commons-utils source file carries.
"""

from __future__ import annotations

import csv
import hashlib
import json
import subprocess
import tomllib
import urllib.error
import urllib.request
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

SOURCES = "scripts/packaging/release/third-party.toml"
UPSTREAM_PINS = "scripts/packaging/release/third-party-upstream.tsv"
SOURCES_FORMAT = ("teralizer-third-party-sources", 1)
RECORDS_FORMAT = ("teralizer-third-party-records", 1)
RECORDS = "third-party.json"
UPSTREAM_DIRECTORY = "third-party"
_UPSTREAM_FILES = ("LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt")


class ThirdPartyError(ValueError):
    """A third-party record cannot be established."""


@dataclass(frozen=True)
class Declared:
    component: str
    paths: tuple[str, ...]
    origin: str
    revision: str | None
    license: str | None
    license_evidence: str | None
    upstream: tuple[str, ...]
    note: str | None


def declared(path: Path) -> list[Declared]:
    document = tomllib.loads(path.read_text())
    if (document.get("format"), document.get("format_version")) != SOURCES_FORMAT:
        raise ThirdPartyError(f"{path} is not a {SOURCES_FORMAT[0]} version 1 document")
    entries = []
    for entry in document.get("project", []):
        item = Declared(
            component=entry["component"],
            paths=tuple(entry["paths"]),
            origin=entry["origin"],
            revision=entry.get("revision"),
            license=entry.get("license"),
            license_evidence=entry.get("license_evidence"),
            upstream=tuple(entry.get("upstream", ())),
            note=entry.get("note"),
        )
        if (item.license is None) != (item.license_evidence is None):
            raise ThirdPartyError(
                f"{item.paths[0]}: a declared license needs its evidence"
            )
        entries.append(item)
    return entries


def upstream_pins(path: Path) -> dict[str, str]:
    """Revisions of upstream repositories for projects that record none."""
    with path.open(newline="") as stream:
        return {
            row["repository"]: row["revision"]
            for row in csv.DictReader(stream, delimiter="\t")
        }


def upstream_revisions(
    entries: Iterable[Declared], pins: Mapping[str, str]
) -> set[tuple[str, str]]:
    revisions = set()
    for entry in entries:
        for repository in entry.upstream:
            revision = entry.revision or pins.get(repository)
            if revision is None:
                raise ThirdPartyError(
                    f"{UPSTREAM_PINS} pins no revision of {repository}"
                )
            revisions.add((repository, revision))
    return revisions


def fetch_upstream(revisions: Iterable[tuple[str, str]], out: Path) -> int:
    """Download the LICENSE and NOTICE files of each upstream revision."""
    count = 0
    for repository, revision in sorted(revisions):
        target = out / f"{repository}@{revision}"
        target.mkdir(parents=True, exist_ok=True)
        found = []
        for name in _UPSTREAM_FILES:
            url = f"https://raw.githubusercontent.com/apache/{repository}/{revision}/{name}"
            try:
                with urllib.request.urlopen(url) as response:
                    (target / name).write_bytes(response.read())
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    continue
                raise
            found.append(name)
            count += 1
        if not any(name.startswith("LICENSE") for name in found):
            raise ThirdPartyError(f"{repository}@{revision} has no LICENSE file")
    return count


def detect(root: Path) -> dict[str, Any]:
    """licensee's licenses and matched files for one project root."""
    result = subprocess.run(
        ["licensee", "detect", "--json", str(root)], capture_output=True, text=True
    )
    # licensee exits with status 1 when it finds no license and still reports.
    if result.returncode not in (0, 1) or not result.stdout:
        raise ThirdPartyError(f"licensee failed on {root}: {result.stderr.strip()}")
    report = json.loads(result.stdout)
    return {
        "licenses": sorted(item["spdx_id"] for item in report.get("licenses", [])),
        "files": [
            {
                "filename": item["filename"],
                "license": item.get("matched_license"),
                "matcher": (item.get("matcher") or {}).get("name"),
            }
            for item in report.get("matched_files", [])
        ],
    }


def licensee_version() -> str:
    return subprocess.run(
        ["licensee", "version"], capture_output=True, text=True, check=True
    ).stdout.strip()


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record(
    project: str,
    source: Path,
    members: Mapping[str, Path],
    origin: str,
    revision: str | None,
    declaration: Declared | None,
    upstream: Mapping[str, Path],
) -> dict[str, Any]:
    """The third-party record of one packaged project root.

    ``members`` maps the packaged paths of the project, relative to the component
    root, to their source files. ``upstream`` maps the retained upstream files to
    their staged copies.
    """
    detected = detect(source)
    license_files = []
    for item in detected["files"]:
        path = f"{project}/{item['filename']}"
        if path not in members:
            # licensee reads the project root; a file outside the payload, such
            # as pipeline output, is not shipped and is no record of the project.
            continue
        license_files.append(
            {
                "path": path,
                "sha256": _sha256(members[path]),
                "license": item["license"],
                "matcher": item["matcher"],
            }
        )
    if declaration is not None and declaration.license is not None:
        licenses = [declaration.license]
        evidence = declaration.license_evidence
    elif license_files:
        licenses = sorted({str(item["license"]) for item in license_files})
        evidence = "license files that licensee identifies"
    else:
        licenses = []
        evidence = "no license file found"
    return {
        "path": project,
        "origin": origin,
        "revision": revision,
        "licenses": licenses,
        "license_evidence": evidence,
        "license_files": license_files,
        "upstream_files": [
            {"path": path, "sha256": _sha256(file)}
            for path, file in sorted(upstream.items())
        ],
        "note": declaration.note if declaration is not None else None,
    }


def upstream_members(
    entry: Declared, pins: Mapping[str, str], staged: Path
) -> dict[str, Path]:
    """Component-relative paths of the upstream files retained for an entry."""
    members = {}
    for repository in entry.upstream:
        revision = entry.revision or pins[repository]
        directory = staged / f"{repository}@{revision}"
        files = sorted(path for path in directory.iterdir() if path.is_file())
        if not files:
            raise ThirdPartyError(f"no staged upstream files in {directory}")
        for path in files:
            relative = PurePosixPath(UPSTREAM_DIRECTORY, directory.name, path.name)
            members[relative.as_posix()] = path
    return members


def document(records: list[dict[str, Any]], detector: str) -> bytes:
    content = {
        "format": RECORDS_FORMAT[0],
        "format_version": RECORDS_FORMAT[1],
        "detector": detector,
        "notice": (
            "The release licenses do not cover these projects. Each project remains "
            "under its authors' terms and is included only to reproduce the study."
        ),
        "projects": sorted(records, key=lambda item: item["path"]),
    }
    return (json.dumps(content, indent=2, sort_keys=True) + "\n").encode()


def project_root(path: str) -> str:
    parts = PurePosixPath(path).parts
    # Real-world and controlled projects sit at projects/<name>, the JARVIS
    # fixtures at data/<family>/fixtures/<name>.
    depth = 2 if parts[0] == "projects" else 4
    return PurePosixPath(*parts[:depth]).as_posix()


def component_records(
    component: str,
    members: Mapping[str, Path],
    project_sources: Mapping[str, Path],
    origins: Mapping[str, tuple[str, str]],
    entries: Iterable[Declared],
    pins: Mapping[str, str],
    staged: Path | None,
) -> bytes:
    """The records file of one project component.

    ``members`` maps component-relative payload paths to their sources, the
    retained upstream files included. ``project_sources`` maps each project root
    to the directory licensee reads. ``origins`` gives origin and revision of the
    real-world projects.
    """
    projects = {
        path: source
        for path, source in members.items()
        if not path.startswith(UPSTREAM_DIRECTORY + "/")
    }
    roots = sorted({project_root(path) for path in projects})
    grouped: dict[str, dict[str, Path]] = {root: {} for root in roots}
    for path, source in projects.items():
        grouped[project_root(path)][path] = source
    by_path = {
        path: entry
        for entry in entries
        if entry.component == component
        for path in entry.paths
    }
    unused = sorted(set(by_path) - set(roots))
    if unused:
        raise ThirdPartyError(
            f"{component}: declared projects without payload: {unused}"
        )
    records = []
    for root in roots:
        entry = by_path.get(root)
        upstream: dict[str, Path] = {}
        if entry is not None:
            origin, revision = entry.origin, entry.revision
            if entry.upstream:
                if staged is None:
                    raise ThirdPartyError(f"{root} needs the staged upstream files")
                upstream = upstream_members(entry, pins, staged)
                missing = sorted(set(upstream) - set(members))
                if missing:
                    raise ThirdPartyError(
                        f"{root}: upstream files not packaged: {missing}"
                    )
        elif root in origins:
            origin, revision = origins[root]
        else:
            raise ThirdPartyError(f"{component}: {root} has no declared origin")
        records.append(
            record(
                root,
                project_sources[root],
                grouped[root],
                origin,
                revision,
                entry,
                upstream,
            )
        )
    return document(records, f"licensee {licensee_version()}")


def planned_upstream(
    component: str, entries: Iterable[Declared], pins: Mapping[str, str], staged: Path
) -> dict[str, Path]:
    """The retained upstream files of one component, component-relative."""
    files: dict[str, Path] = {}
    for entry in entries:
        if entry.component == component and entry.upstream:
            files.update(upstream_members(entry, pins, staged))
    return files
