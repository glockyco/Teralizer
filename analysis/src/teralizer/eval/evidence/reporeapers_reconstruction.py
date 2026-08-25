"""Validate read-only RepoReapers evidence inventories and audit records."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
from collections import Counter
from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path, PurePosixPath

from teralizer import corpora
from teralizer.eval.evidence import write_atomic

SCHEMA_VERSION = 1
CANONICAL_CORPUS_ID = "real-world"
CANONICAL_DATA_DIR = "data/reporeapers-rerun-v7"


class ReconstructionError(ValueError):
    """The evidence record cannot support a reproducible reconstruction."""


class ClaimStatus(StrEnum):
    SUPPORTED = "supported"
    PARTIALLY_SUPPORTED = "partially-supported"
    CONTRADICTED = "contradicted"
    EVIDENCE_GAP = "evidence-gap"


class EntityStatus(StrEnum):
    RESOLVED = "resolved"
    UNRESOLVED = "unresolved"
    INCOMPATIBLE = "incompatible"


class ReviewState(StrEnum):
    SINGLE_REVIEWED = "single-reviewed"
    AGREED = "agreed"
    DISPUTED = "disputed"


class ConfidenceTier(StrEnum):
    T1_PROVEN = "T1_PROVEN"
    T2_CORROBORATED = "T2_CORROBORATED"
    T3_SINGLE_WEAK = "T3_SINGLE_WEAK"
    T4_GUESS = "T4_GUESS"
    NONE = "NONE"


class SourceRole(StrEnum):
    DATABASE_EXPORT = "database-export"
    FACTS_RECORD = "facts-record"
    PROJECT_LOGS = "project-logs"
    PROJECT_CHECKOUT = "project-checkout"
    RUN_ROOT = "run-root"
    CONFIGURATION = "configuration"
    GENERATED_ARTIFACTS = "generated-artifacts"
    PRODUCER_REVISION = "producer-revision"


@dataclass(frozen=True)
class EntityIdentity:
    """Stable cross-source identity; database surrogate IDs are forbidden."""

    corpus_id: str
    project_root: str
    project_revision: str | None
    level: str
    local_key: str

    @classmethod
    def parse(cls, raw: object, label: str) -> EntityIdentity:
        item = _mapping(raw, label)
        allowed = {
            "corpus_id",
            "project_root",
            "project_revision",
            "level",
            "local_key",
        }
        _exact_keys(item, allowed, label)
        project_revision = item["project_revision"]
        if project_revision is not None and (
            not isinstance(project_revision, str)
            or len(project_revision) != 40
            or any(
                character not in "0123456789abcdef" for character in project_revision
            )
        ):
            raise ReconstructionError(
                f"{label}.project_revision must be null or a lowercase Git commit"
            )
        identity = cls(
            corpus_id=_string(item, "corpus_id", label),
            project_root=_string(item, "project_root", label),
            project_revision=project_revision,
            level=_string(item, "level", label),
            local_key=_string(item, "local_key", label),
        )
        if identity.corpus_id != CANONICAL_CORPUS_ID:
            raise ReconstructionError(
                f"{label}.corpus_id must be {CANONICAL_CORPUS_ID!r}"
            )
        if not identity.project_root.startswith("/"):
            raise ReconstructionError(f"{label}.project_root must be absolute")
        return identity

    def key(self) -> tuple[str, str, str | None, str, str]:
        return (
            self.corpus_id,
            self.project_root,
            self.project_revision,
            self.level,
            self.local_key,
        )


@dataclass(frozen=True)
class SourceSpec:
    """One collected source to inventory without copying its contents."""

    source_id: str
    role: SourceRole
    path: Path
    configured_path: str
    producer_revisions: tuple[str, ...]
    expected_paths: tuple[str, ...]
    attributes: Mapping[str, str | int | bool | None]

    @classmethod
    def parse(cls, raw: object, index: int) -> SourceSpec:
        label = f"sources[{index}]"
        item = _mapping(raw, label)
        _exact_keys(
            item,
            {
                "source_id",
                "role",
                "path",
                "configured_path",
                "producer_revisions",
                "expected_paths",
                "attributes",
            },
            label,
        )
        source_id = _string(item, "source_id", label)
        try:
            role = SourceRole(_string(item, "role", label))
        except ValueError as error:
            raise ReconstructionError(
                f"{label}.role is not a collected-source role"
            ) from error
        path = Path(_string(item, "path", label))
        if not path.is_absolute():
            raise ReconstructionError(f"{label}.path must be absolute")
        configured_path = _string(item, "configured_path", label)
        if not PurePosixPath(configured_path).is_absolute():
            raise ReconstructionError(f"{label}.configured_path must be absolute")
        producer_revisions = _git_revisions(
            item["producer_revisions"], f"{label}.producer_revisions"
        )
        expected_paths = tuple(
            _expected_path(value, f"{label}.expected_paths[{path_index}]")
            for path_index, value in enumerate(
                _sequence(item["expected_paths"], f"{label}.expected_paths")
            )
        )
        if len(expected_paths) != len(set(expected_paths)):
            raise ReconstructionError(f"{label}.expected_paths contains duplicates")
        attributes = _attributes(item["attributes"], f"{label}.attributes")
        return cls(
            source_id,
            role,
            path,
            configured_path,
            producer_revisions,
            expected_paths,
            attributes,
        )


def _mapping(value: object, label: str) -> Mapping[str, object]:
    if not isinstance(value, Mapping):
        raise ReconstructionError(f"{label} must be an object")
    return value


def _sequence(value: object, label: str) -> Sequence[object]:
    if not isinstance(value, list):
        raise ReconstructionError(f"{label} must be an array")
    return value


def _git_revisions(value: object, label: str) -> tuple[str, ...]:
    raw_revisions = _sequence(value, label)
    if not raw_revisions:
        raise ReconstructionError(f"{label} must contain at least one Git commit")
    revisions: list[str] = []
    for index, revision in enumerate(raw_revisions):
        if (
            not isinstance(revision, str)
            or len(revision) != 40
            or any(character not in "0123456789abcdef" for character in revision)
        ):
            raise ReconstructionError(
                f"{label}[{index}] must be a lowercase Git commit"
            )
        revisions.append(revision)
    if len(revisions) != len(set(revisions)):
        raise ReconstructionError(f"{label} contains duplicates")
    return tuple(revisions)


def _attributes(value: object, label: str) -> dict[str, str | int | bool | None]:
    attributes = _mapping(value, label)
    result: dict[str, str | int | bool | None] = {}
    for key, item in sorted(attributes.items()):
        if not isinstance(key, str) or not key:
            raise ReconstructionError(f"{label} keys must be non-empty strings")
        if item is not None and not isinstance(item, (str, int, bool)):
            raise ReconstructionError(f"{label}.{key} must be a JSON scalar")
        result[key] = item
    return result


def _expected_path(value: object, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise ReconstructionError(f"{label} must be a non-empty string")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts:
        raise ReconstructionError(f"{label} must be source-relative")
    return value


def _string(item: Mapping[str, object], key: str, label: str) -> str:
    value = item.get(key)
    if not isinstance(value, str) or not value:
        raise ReconstructionError(f"{label}.{key} must be a non-empty string")
    return value


def _integer(item: Mapping[str, object], key: str, label: str) -> int:
    value = item.get(key)
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ReconstructionError(f"{label}.{key} must be a non-negative integer")
    return value


def _boolean(item: Mapping[str, object], key: str, label: str) -> bool:
    value = item.get(key)
    if not isinstance(value, bool):
        raise ReconstructionError(f"{label}.{key} must be a boolean")
    return value


def _exact_keys(item: Mapping[str, object], expected: set[str], label: str) -> None:
    actual = set(item)
    if actual != expected:
        raise ReconstructionError(
            f"{label} keys differ: missing={sorted(expected - actual)}, "
            f"unexpected={sorted(actual - expected)}"
        )


def _digest_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def _tree_entries(root: Path) -> Iterable[tuple[str, Path]]:
    for directory, names, files in os.walk(root, followlinks=False):
        names.sort()
        files.sort()
        base = Path(directory)
        for name in [*names, *files]:
            path = base / name
            yield path.relative_to(root).as_posix(), path


def _digest_source(path: Path) -> tuple[str, int, int, str, tuple[str, ...]]:
    if not path.exists():
        raise ReconstructionError(f"collected source is missing: {path}")
    if path.is_file():
        return _digest_file(path), 1, path.stat().st_size, "file", (".",)
    if not path.is_dir():
        raise ReconstructionError(f"collected source has unsupported type: {path}")

    digest = hashlib.sha256()
    file_count = 0
    total_bytes = 0
    observed_paths: list[str] = []
    for relative, member in _tree_entries(path):
        mode = member.lstat().st_mode
        if stat.S_ISLNK(mode):
            kind = "symlink"
            payload = os.readlink(member).encode()
            observed_paths.append(relative)
        elif stat.S_ISREG(mode):
            kind = "file"
            payload = bytes.fromhex(_digest_file(member))
            file_count += 1
            total_bytes += member.stat().st_size
            observed_paths.append(relative)
        elif stat.S_ISDIR(mode):
            kind = "directory"
            payload = b""
        else:
            raise ReconstructionError(f"unsupported member type: {member}")
        digest.update(kind.encode())
        digest.update(b"\0")
        digest.update(relative.encode())
        digest.update(b"\0")
        digest.update(payload)
        digest.update(b"\0")
    return (
        digest.hexdigest(),
        file_count,
        total_bytes,
        "directory",
        tuple(observed_paths),
    )


def validate_canonical_registry(path: Path = corpora.REGISTRY_PATH) -> None:
    """Require version 7 as the registry's only RepoReapers report corpus."""
    registry = corpora.load(path)
    entry = registry.get(CANONICAL_CORPUS_ID)
    if entry.data_dir != CANONICAL_DATA_DIR or not entry.database.endswith("_rq6_v7"):
        raise ReconstructionError(
            "canonical RepoReapers mapping is not the declared version 7 corpus"
        )
    aliases = [
        candidate.id
        for candidate in registry.entries
        if candidate.id != CANONICAL_CORPUS_ID
        and (
            "reporeapers" in candidate.id.lower()
            or "reporeapers" in candidate.database.lower()
        )
    ]
    if aliases:
        raise ReconstructionError(
            f"historical RepoReapers aliases remain registered: {sorted(aliases)}"
        )


def build_inventory(specification: object) -> dict[str, object]:
    """Hash declared collected sources and retain only provenance metadata."""
    root = _mapping(specification, "inventory specification")
    _exact_keys(
        root, {"schema_version", "population", "sources"}, "inventory specification"
    )
    if root.get("schema_version") != SCHEMA_VERSION:
        raise ReconstructionError(
            f"inventory specification schema_version must be {SCHEMA_VERSION}"
        )
    population = _mapping(root["population"], "population")
    _exact_keys(
        population,
        {"corpus_id", "database", "variant", "producer_revisions"},
        "population",
    )
    if _string(population, "corpus_id", "population") != CANONICAL_CORPUS_ID:
        raise ReconstructionError(
            "inventory population must use the real-world corpus id"
        )
    canonical_database = corpora.resolve(CANONICAL_CORPUS_ID).database
    if _string(population, "database", "population") != canonical_database:
        raise ReconstructionError(
            "inventory population must use the registered real-world database"
        )
    _string(population, "variant", "population")
    _git_revisions(population["producer_revisions"], "population.producer_revisions")

    source_specs = tuple(
        SourceSpec.parse(raw, index)
        for index, raw in enumerate(_sequence(root["sources"], "sources"))
    )
    id_counts = Counter(spec.source_id for spec in source_specs)
    duplicate_ids = sorted(
        source_id for source_id, count in id_counts.items() if count > 1
    )
    if duplicate_ids:
        raise ReconstructionError(f"duplicate collected-source ids: {duplicate_ids}")

    issues: list[str] = []
    location_counts = Counter(str(spec.path) for spec in source_specs)
    for location, count in sorted(location_counts.items()):
        if count > 1:
            issues.append(f"duplicate collected-source location: {location}")

    population_revisions = set(
        _git_revisions(
            population["producer_revisions"], "population.producer_revisions"
        )
    )
    source_records: list[dict[str, object]] = []
    for source in source_specs:
        if not set(source.producer_revisions).issubset(population_revisions):
            issues.append(
                f"{source.source_id}: producer revisions differ from the population"
            )
        try:
            sha256, file_count, total_bytes, kind, observed_paths = _digest_source(
                source.path
            )
            available = True
        except ReconstructionError as error:
            sha256 = None
            file_count = 0
            total_bytes = 0
            kind = "missing"
            observed_paths = ()
            available = False
            issues.append(f"{source.source_id}: {error}")
        if source.expected_paths:
            expected = set(source.expected_paths)
            observed = set(observed_paths)
            missing = sorted(expected - observed)
            unexpected = sorted(observed - expected)
            if missing:
                issues.append(f"{source.source_id}: missing paths: {missing}")
            if unexpected:
                issues.append(f"{source.source_id}: unexpected paths: {unexpected}")
        source_records.append(
            {
                "source_id": source.source_id,
                "role": source.role.value,
                "location": str(source.path),
                "configured_path": source.configured_path,
                "producer_revisions": list(source.producer_revisions),
                "available": available,
                "kind": kind,
                "file_count": file_count,
                "total_bytes": total_bytes,
                "paths": list(observed_paths),
                "sha256": sha256,
                "attributes": dict(source.attributes),
            }
        )
    inventory = {
        "schema_version": SCHEMA_VERSION,
        "population": dict(population),
        "sources": source_records,
        "integrity_issues": issues,
    }
    validate_inventory(inventory)
    return inventory


def validate_inventory(document: object) -> dict[str, object]:
    root = _mapping(document, "inventory")
    _exact_keys(
        root,
        {"schema_version", "population", "sources", "integrity_issues"},
        "inventory",
    )
    if root.get("schema_version") != SCHEMA_VERSION:
        raise ReconstructionError(f"inventory schema_version must be {SCHEMA_VERSION}")
    population = _mapping(root["population"], "population")
    _exact_keys(
        population,
        {"corpus_id", "database", "variant", "producer_revisions"},
        "population",
    )
    if _string(population, "corpus_id", "population") != CANONICAL_CORPUS_ID:
        raise ReconstructionError("inventory corpus id is not canonical")
    canonical_database = corpora.resolve(CANONICAL_CORPUS_ID).database
    if _string(population, "database", "population") != canonical_database:
        raise ReconstructionError(
            "inventory database is not the registered real-world corpus"
        )
    _string(population, "variant", "population")
    _git_revisions(population["producer_revisions"], "population.producer_revisions")

    source_ids: set[str] = set()
    for index, raw in enumerate(_sequence(root["sources"], "sources")):
        label = f"sources[{index}]"
        source = _mapping(raw, label)
        _exact_keys(
            source,
            {
                "source_id",
                "role",
                "location",
                "configured_path",
                "producer_revisions",
                "available",
                "kind",
                "file_count",
                "total_bytes",
                "paths",
                "sha256",
                "attributes",
            },
            label,
        )
        source_id = _string(source, "source_id", label)
        if source_id in source_ids:
            raise ReconstructionError(f"duplicate source id: {source_id}")
        source_ids.add(source_id)
        role = _string(source, "role", label)
        try:
            SourceRole(role)
        except ValueError as error:
            raise ReconstructionError(f"{label}.role is unknown: {role!r}") from error
        for key in ("location", "configured_path", "kind"):
            _string(source, key, label)
        _git_revisions(source["producer_revisions"], f"{label}.producer_revisions")
        available = _boolean(source, "available", label)
        _integer(source, "file_count", label)
        _integer(source, "total_bytes", label)
        for path_index, value in enumerate(
            _sequence(source["paths"], f"{label}.paths")
        ):
            _expected_path(value, f"{label}.paths[{path_index}]")
        _attributes(source["attributes"], f"{label}.attributes")
        sha256 = source.get("sha256")
        if available:
            if (
                not isinstance(sha256, str)
                or len(sha256) != 64
                or any(c not in "0123456789abcdef" for c in sha256)
            ):
                raise ReconstructionError(f"{label}.sha256 is not a lowercase SHA-256")
        elif sha256 is not None:
            raise ReconstructionError(f"{label}.sha256 must be null when unavailable")
    for issue_index, issue in enumerate(
        _sequence(root["integrity_issues"], "integrity_issues")
    ):
        if not isinstance(issue, str) or not issue:
            raise ReconstructionError(
                f"integrity_issues[{issue_index}] must be a non-empty string"
            )
    return dict(root)


def validate_audit(document: object) -> dict[str, object]:
    """Validate complete entity coverage and claim-summary reconciliation."""
    root = _mapping(document, "audit")
    _exact_keys(
        root,
        {"schema_version", "inventory", "entities", "claims", "integrity_issues"},
        "audit",
    )
    if root.get("schema_version") != SCHEMA_VERSION:
        raise ReconstructionError(f"audit schema_version must be {SCHEMA_VERSION}")
    inventory = validate_inventory(root["inventory"])
    source_ids = {
        _string(_mapping(item, "source"), "source_id", "source")
        for item in _sequence(inventory["sources"], "inventory.sources")
    }
    entity_keys: set[tuple[str, str, str | None, str, str]] = set()
    entity_counts: Counter[tuple[str, str]] = Counter()
    for index, raw in enumerate(_sequence(root["entities"], "entities")):
        label = f"entities[{index}]"
        entity = _mapping(raw, label)
        _exact_keys(
            entity,
            {
                "identity",
                "claim",
                "status",
                "label",
                "confidence",
                "rationale",
                "source_ids",
                "filter_outcome",
                "reviewer",
                "review_state",
            },
            label,
        )
        identity = EntityIdentity.parse(entity["identity"], f"{label}.identity")
        if identity.key() in entity_keys:
            raise ReconstructionError(f"duplicate entity identity: {identity.key()}")
        entity_keys.add(identity.key())
        claim_name = _string(entity, "claim", label)
        try:
            entity_status = EntityStatus(_string(entity, "status", label))
            confidence = ConfidenceTier(_string(entity, "confidence", label))
            ReviewState(_string(entity, "review_state", label))
        except ValueError as error:
            raise ReconstructionError(
                f"{label} has an unknown status, confidence, or review state"
            ) from error
        _string(entity, "label", label)
        _string(entity, "rationale", label)
        _string(entity, "filter_outcome", label)
        _string(entity, "reviewer", label)
        if (
            entity_status is not EntityStatus.RESOLVED
            and confidence is not ConfidenceTier.NONE
        ):
            raise ReconstructionError(
                f"{label}.confidence must be NONE unless the entity is resolved"
            )
        references = _sequence(entity["source_ids"], f"{label}.source_ids")
        invalid_references = [
            reference
            for reference in references
            if not isinstance(reference, str) or reference not in source_ids
        ]
        if invalid_references:
            raise ReconstructionError(
                f"{label} cites unknown source ids: {invalid_references}"
            )
        if len(references) != len(set(references)):
            raise ReconstructionError(f"{label}.source_ids contains duplicates")
        if entity_status is not EntityStatus.UNRESOLVED and not references:
            raise ReconstructionError(f"{label} requires at least one evidence source")
        entity_counts[(claim_name, entity_status.value)] += 1
    seen_claims: set[str] = set()
    for index, raw in enumerate(_sequence(root["claims"], "claims")):
        label = f"claims[{index}]"
        claim = _mapping(raw, label)
        _exact_keys(
            claim,
            {
                "claim",
                "status",
                "population_definition",
                "population_sha256",
                "resolved",
                "unresolved",
                "incompatible",
                "total",
                "method",
                "reason",
            },
            label,
        )
        name = _string(claim, "claim", label)
        if name in seen_claims:
            raise ReconstructionError(f"duplicate claim summary: {name}")
        seen_claims.add(name)
        try:
            claim_status = ClaimStatus(_string(claim, "status", label))
        except ValueError as error:
            raise ReconstructionError(f"{label}.status is unknown") from error
        _string(claim, "population_definition", label)
        population_sha256 = _string(claim, "population_sha256", label)
        if len(population_sha256) != 64 or any(
            character not in "0123456789abcdef" for character in population_sha256
        ):
            raise ReconstructionError(
                f"{label}.population_sha256 is not a lowercase SHA-256"
            )
        resolved = _integer(claim, "resolved", label)
        unresolved = _integer(claim, "unresolved", label)
        incompatible = _integer(claim, "incompatible", label)
        total = _integer(claim, "total", label)
        _string(claim, "method", label)
        _string(claim, "reason", label)
        actual = {
            EntityStatus.RESOLVED.value: entity_counts[
                (name, EntityStatus.RESOLVED.value)
            ],
            EntityStatus.UNRESOLVED.value: entity_counts[
                (name, EntityStatus.UNRESOLVED.value)
            ],
            EntityStatus.INCOMPATIBLE.value: entity_counts[
                (name, EntityStatus.INCOMPATIBLE.value)
            ],
        }
        expected = {
            EntityStatus.RESOLVED.value: resolved,
            EntityStatus.UNRESOLVED.value: unresolved,
            EntityStatus.INCOMPATIBLE.value: incompatible,
        }
        if expected != actual or total != sum(actual.values()):
            raise ReconstructionError(
                f"{label} does not reconcile: expected={expected}, actual={actual}, total={total}"
            )
        if claim_status in {ClaimStatus.SUPPORTED, ClaimStatus.CONTRADICTED}:
            valid_status = resolved == total
        elif claim_status is ClaimStatus.PARTIALLY_SUPPORTED:
            valid_status = 0 < resolved < total
        else:
            valid_status = resolved == 0
        if not valid_status:
            raise ReconstructionError(
                f"{label}.status does not match its resolved population"
            )
    entity_claims = {claim for claim, _ in entity_counts}
    if entity_claims != seen_claims:
        raise ReconstructionError(
            f"claim coverage differs: missing={sorted(entity_claims - seen_claims)}, extra={sorted(seen_claims - entity_claims)}"
        )
    for issue_index, issue in enumerate(
        _sequence(root["integrity_issues"], "integrity_issues")
    ):
        if not isinstance(issue, str) or not issue:
            raise ReconstructionError(
                f"integrity_issues[{issue_index}] must be a non-empty string"
            )
    return dict(root)


def _load(path: Path) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ReconstructionError(f"invalid JSON in {path}: {error}") from error


def parser() -> argparse.ArgumentParser:
    """Expose inventory and validation only; execution commands do not exist."""
    command = argparse.ArgumentParser(prog="reporeapers-evidence-reconstruction")
    subcommands = command.add_subparsers(dest="command", required=True)
    inventory = subcommands.add_parser("inventory")
    inventory.add_argument("specification", type=Path)
    inventory.add_argument("output", type=Path)
    validate_inventory_command = subcommands.add_parser("validate-inventory")
    validate_inventory_command.add_argument("inventory", type=Path)
    validate = subcommands.add_parser("validate")
    validate.add_argument("audit", type=Path)
    validate_registry = subcommands.add_parser("validate-registry")
    validate_registry.add_argument(
        "registry", nargs="?", type=Path, default=corpora.REGISTRY_PATH
    )
    return command


def main(argv: list[str] | None = None) -> None:
    args = parser().parse_args(argv)
    if args.command == "inventory":
        inventory = build_inventory(_load(args.specification))
        write_atomic(args.output, inventory)
        print(args.output)
    elif args.command == "validate-inventory":
        validate_inventory(_load(args.inventory))
        print(args.inventory)
    elif args.command == "validate":
        validate_audit(_load(args.audit))
        print(args.audit)
    else:
        validate_canonical_registry(args.registry)
        print(args.registry)


if __name__ == "__main__":
    main()
