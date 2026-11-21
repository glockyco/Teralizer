"""The two public document formats of a release.

- The release manifest catalogs every archive of one release with its checksum and
  workflow relationships. It lives beside the archives, never inside one, because an
  archive cannot contain its own checksum.
- The component manifest identifies one archive's payload, release and
  dependencies, so an archive opened alone still says what it is.

Each document carries its format name and version. Readers reject other versions.
"""

from __future__ import annotations

import re
from collections.abc import Mapping, Sequence

from teralizer.release.archive import link_stays_inside

RELEASE_MANIFEST = ("teralizer-release-manifest", 1)
COMPONENT_MANIFEST = ("teralizer-component-manifest", 1)

_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_COMMIT = re.compile(r"^[0-9a-f]{40}$")


class DocumentError(ValueError):
    """A release document does not conform to its format."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise DocumentError(message)


def _mapping(value: object, where: str) -> Mapping[str, object]:
    _require(isinstance(value, Mapping), f"{where} must be an object")
    assert isinstance(value, Mapping)
    return value


def _sequence(value: object, where: str) -> Sequence[object]:
    _require(
        isinstance(value, Sequence) and not isinstance(value, str),
        f"{where} must be an array",
    )
    assert isinstance(value, Sequence)
    return value


def _keys(document: Mapping[str, object], expected: set[str], where: str) -> None:
    missing = sorted(expected - set(document))
    extra = sorted(set(document) - expected)
    _require(
        not missing and not extra, f"{where}: missing {missing}, unexpected {extra}"
    )


def _text(document: Mapping[str, object], key: str, where: str) -> str:
    value = document[key]
    _require(isinstance(value, str) and bool(value), f"{where}.{key} must be text")
    assert isinstance(value, str)
    return value


def _count(document: Mapping[str, object], key: str, where: str) -> int:
    value = document[key]
    _require(
        isinstance(value, int) and not isinstance(value, bool) and value >= 0,
        f"{where}.{key} must be a non-negative integer",
    )
    assert isinstance(value, int)
    return value


def _sha256(document: Mapping[str, object], key: str, where: str) -> str:
    value = _text(document, key, where)
    _require(bool(_SHA256.match(value)), f"{where}.{key} must be a SHA-256 digest")
    return value


def _texts(document: Mapping[str, object], key: str, where: str) -> list[str]:
    values = _sequence(document[key], f"{where}.{key}")
    _require(
        all(isinstance(value, str) and value for value in values),
        f"{where}.{key} must contain text",
    )
    return [str(value) for value in values]


def _format(document: Mapping[str, object], expected: tuple[str, int]) -> None:
    name, version = expected
    _require(document.get("format") == name, f"format must be {name}")
    _require(
        document.get("format_version") == version,
        f"{name} format_version must be {version}",
    )


_RELEASE_KEYS = {"name", "version", "version_doi", "concept_doi", "source_revision"}
_PAPER_KEYS = {"title", "authors", "doi"}


def _release_identity(value: object, where: str) -> Mapping[str, object]:
    release = _mapping(value, where)
    _keys(release, _RELEASE_KEYS, where)
    for key in ("name", "version", "version_doi", "concept_doi"):
        _text(release, key, where)
    revision = _text(release, "source_revision", where)
    _require(bool(_COMMIT.match(revision)), f"{where}.source_revision must be a commit")
    return release


def validate_component_manifest(document: object) -> Mapping[str, object]:
    root = _mapping(document, "component manifest")
    _format(root, COMPONENT_MANIFEST)
    _keys(
        root,
        {
            "format",
            "format_version",
            "component",
            "release",
            "root",
            "purpose",
            "requires",
            "incompatible_with",
            "licenses",
            "provenance",
            "payload",
        },
        "component manifest",
    )
    component = _text(root, "component", "component manifest")
    _release_identity(root["release"], "component manifest.release")
    component_root = _text(root, "root", "component manifest")
    _text(root, "purpose", "component manifest")
    _texts(root, "requires", "component manifest")
    _texts(root, "incompatible_with", "component manifest")
    for index, item in enumerate(_sequence(root["licenses"], "licenses")):
        license_entry = _mapping(item, f"licenses[{index}]")
        _keys(license_entry, {"scope", "license", "file"}, f"licenses[{index}]")
        for key in ("scope", "license", "file"):
            _text(license_entry, key, f"licenses[{index}]")
    _mapping(root["provenance"], "provenance")
    seen: set[str] = set()
    for index, item in enumerate(_sequence(root["payload"], "payload")):
        where = f"payload[{index}]"
        entry = _mapping(item, where)
        is_link = "link" in entry
        _keys(
            entry,
            {"path", "link"} if is_link else {"path", "sha256", "bytes", "executable"},
            where,
        )
        path = _text(entry, "path", where)
        _require(
            path.startswith(component_root + "/"),
            f"{where}.path {path} is outside the component root {component_root}",
        )
        _require(".." not in path.split("/"), f"{where}.path {path} escapes its root")
        _require(path not in seen, f"{where}.path {path} is listed twice")
        seen.add(path)
        if is_link:
            target = _text(entry, "link", where)
            _require(
                link_stays_inside(path, target, component_root),
                f"{where}.link {target} leaves the component root {component_root}",
            )
            continue
        _sha256(entry, "sha256", where)
        _count(entry, "bytes", where)
        _require(
            isinstance(entry["executable"], bool), f"{where}.executable must be boolean"
        )
    _require(bool(seen), f"component {component} has no payload")
    return root


def validate_release_manifest(document: object) -> Mapping[str, object]:
    root = _mapping(document, "release manifest")
    _format(root, RELEASE_MANIFEST)
    _keys(
        root,
        {"format", "format_version", "release", "paper", "archives", "workflows"},
        "release manifest",
    )
    _release_identity(root["release"], "release manifest.release")
    paper = _mapping(root["paper"], "paper")
    _keys(paper, _PAPER_KEYS, "paper")
    _text(paper, "title", "paper")
    _texts(paper, "authors", "paper")
    _text(paper, "doi", "paper")

    components: set[str] = set()
    filenames: set[str] = set()
    for index, item in enumerate(_sequence(root["archives"], "archives")):
        where = f"archives[{index}]"
        archive = _mapping(item, where)
        _keys(
            archive,
            {
                "component",
                "filename",
                "sha256",
                "bytes",
                "unpacked_bytes",
                "purpose",
                "requires",
                "component_manifest_sha256",
            },
            where,
        )
        component = _text(archive, "component", where)
        filename = _text(archive, "filename", where)
        _require(component not in components, f"{where}: component {component} repeats")
        _require(filename not in filenames, f"{where}: filename {filename} repeats")
        _require("/" not in filename, f"{where}.filename must not contain a path")
        components.add(component)
        filenames.add(filename)
        _sha256(archive, "sha256", where)
        _sha256(archive, "component_manifest_sha256", where)
        _count(archive, "bytes", where)
        _count(archive, "unpacked_bytes", where)
        _text(archive, "purpose", where)
        _texts(archive, "requires", where)
    for index, item in enumerate(_sequence(root["workflows"], "workflows")):
        where = f"workflows[{index}]"
        workflow = _mapping(item, where)
        _keys(workflow, {"id", "command", "components"}, where)
        _text(workflow, "id", where)
        _text(workflow, "command", where)
        unknown = sorted(set(_texts(workflow, "components", where)) - components)
        _require(not unknown, f"{where}: components {unknown} are not in the release")
    return root
