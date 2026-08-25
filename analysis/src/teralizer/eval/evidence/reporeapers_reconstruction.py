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
from typing import cast

from teralizer import corpora
from teralizer.eval.evidence.project_sources import write_atomic

SCHEMA_VERSION = 1
CANONICAL_CORPUS_ID = "real-world"
CANONICAL_DATA_DIR = "data/reporeapers-rerun-v7"


class ReconstructionError(ValueError):
    """The evidence record cannot support a reproducible reconstruction."""


class ReconstructionStatus(StrEnum):
    RECONSTRUCTED = "reconstructed"
    REPLICATED = "replicated"
    ESTIMATED = "estimated"
    EVIDENCE_GAP = "evidence-gap"
    CONTRADICTED = "contradicted"


class ConfidenceTier(StrEnum):
    T1_PROVEN = "T1_PROVEN"
    T2_CORROBORATED = "T2_CORROBORATED"
    T3_SINGLE_WEAK = "T3_SINGLE_WEAK"
    T4_GUESS = "T4_GUESS"
    NONE = "NONE"


class SourceRole(StrEnum):
    DATABASE_EXPORT = "database-export"
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
    level: str
    local_key: str

    @classmethod
    def parse(cls, raw: object, label: str) -> EntityIdentity:
        item = _mapping(raw, label)
        allowed = {"corpus_id", "project_root", "level", "local_key"}
        _exact_keys(item, allowed, label)
        identity = cls(
            corpus_id=_string(item, "corpus_id", label),
            project_root=_string(item, "project_root", label),
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

    def key(self) -> tuple[str, str, str, str]:
        return (self.corpus_id, self.project_root, self.level, self.local_key)


@dataclass(frozen=True)
class SourceSpec:
    """One collected source to inventory without copying its contents."""

    role: SourceRole
    path: Path
    configured_path: str
    producer_revision: str

    @classmethod
    def parse(cls, raw: object, index: int) -> SourceSpec:
        label = f"sources[{index}]"
        item = _mapping(raw, label)
        _exact_keys(
            item,
            {"role", "path", "configured_path", "producer_revision"},
            label,
        )
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
        producer_revision = _string(item, "producer_revision", label)
        if len(producer_revision) != 40 or any(
            character not in "0123456789abcdef" for character in producer_revision
        ):
            raise ReconstructionError(
                f"{label}.producer_revision must be a lowercase Git commit"
            )
        return cls(role, path, configured_path, producer_revision)


def _mapping(value: object, label: str) -> Mapping[str, object]:
    if not isinstance(value, Mapping):
        raise ReconstructionError(f"{label} must be an object")
    return cast(Mapping[str, object], value)


def _sequence(value: object, label: str) -> Sequence[object]:
    if not isinstance(value, list):
        raise ReconstructionError(f"{label} must be an array")
    return cast(Sequence[object], value)


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


def _digest_source(path: Path) -> tuple[str, int, int, str]:
    if not path.exists():
        raise ReconstructionError(f"collected source is missing: {path}")
    if path.is_file():
        return _digest_file(path), 1, path.stat().st_size, "file"
    if not path.is_dir():
        raise ReconstructionError(f"collected source has unsupported type: {path}")

    digest = hashlib.sha256()
    file_count = 0
    total_bytes = 0
    for relative, member in _tree_entries(path):
        mode = member.lstat().st_mode
        if stat.S_ISLNK(mode):
            kind = "symlink"
            payload = os.readlink(member).encode()
        elif stat.S_ISREG(mode):
            kind = "file"
            payload = bytes.fromhex(_digest_file(member))
            file_count += 1
            total_bytes += member.stat().st_size
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
    return digest.hexdigest(), file_count, total_bytes, "directory"


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
        {"corpus_id", "database", "variant", "producer_revision"},
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
    _string(population, "producer_revision", "population")

    source_specs = tuple(
        SourceSpec.parse(raw, index)
        for index, raw in enumerate(_sequence(root["sources"], "sources"))
    )
    role_counts = Counter(spec.role for spec in source_specs)
    duplicates = sorted(role.value for role, count in role_counts.items() if count > 1)
    if duplicates:
        raise ReconstructionError(f"duplicate collected-source roles: {duplicates}")

    source_records: list[dict[str, object]] = []
    for source in source_specs:
        sha256, file_count, total_bytes, kind = _digest_source(source.path)
        source_records.append(
            {
                "role": source.role.value,
                "location": str(source.path),
                "configured_path": source.configured_path,
                "producer_revision": source.producer_revision,
                "kind": kind,
                "file_count": file_count,
                "total_bytes": total_bytes,
                "sha256": sha256,
            }
        )
    inventory = {
        "schema_version": SCHEMA_VERSION,
        "population": dict(population),
        "sources": source_records,
        "integrity_issues": [],
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
        {"corpus_id", "database", "variant", "producer_revision"},
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
    _string(population, "producer_revision", "population")

    roles: set[str] = set()
    for index, raw in enumerate(_sequence(root["sources"], "sources")):
        label = f"sources[{index}]"
        source = _mapping(raw, label)
        _exact_keys(
            source,
            {
                "role",
                "location",
                "configured_path",
                "producer_revision",
                "kind",
                "file_count",
                "total_bytes",
                "sha256",
            },
            label,
        )
        role = _string(source, "role", label)
        try:
            SourceRole(role)
        except ValueError as error:
            raise ReconstructionError(f"{label}.role is unknown: {role!r}") from error
        if role in roles:
            raise ReconstructionError(f"duplicate source role: {role}")
        roles.add(role)
        for key in ("location", "configured_path", "producer_revision", "kind"):
            _string(source, key, label)
        _integer(source, "file_count", label)
        _integer(source, "total_bytes", label)
        sha256 = _string(source, "sha256", label)
        if len(sha256) != 64 or any(c not in "0123456789abcdef" for c in sha256):
            raise ReconstructionError(f"{label}.sha256 is not a lowercase SHA-256")
    _sequence(root["integrity_issues"], "integrity_issues")
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
    source_roles = {
        cast(str, _mapping(item, "source")["role"])
        for item in _sequence(inventory["sources"], "inventory.sources")
    }

    entity_keys: set[tuple[str, str, str, str]] = set()
    claim_counts: Counter[tuple[str, str]] = Counter()
    for index, raw in enumerate(_sequence(root["entities"], "entities")):
        label = f"entities[{index}]"
        entity = _mapping(raw, label)
        _exact_keys(
            entity,
            {
                "identity",
                "claim",
                "status",
                "confidence",
                "reason",
                "source_roles",
                "filter_outcome",
                "reviewer",
            },
            label,
        )
        identity = EntityIdentity.parse(entity["identity"], f"{label}.identity")
        if identity.key() in entity_keys:
            raise ReconstructionError(f"duplicate entity identity: {identity.key()}")
        entity_keys.add(identity.key())
        claim = _string(entity, "claim", label)
        try:
            status = ReconstructionStatus(_string(entity, "status", label))
            ConfidenceTier(_string(entity, "confidence", label))
        except ValueError as error:
            raise ReconstructionError(
                f"{label} has an unknown status or confidence"
            ) from error
        _string(entity, "reason", label)
        _string(entity, "filter_outcome", label)
        _string(entity, "reviewer", label)
        roles = _sequence(entity["source_roles"], f"{label}.source_roles")
        unknown_roles = sorted(
            role
            for role in roles
            if not isinstance(role, str) or role not in source_roles
        )
        if unknown_roles:
            raise ReconstructionError(
                f"{label} cites unknown source roles: {unknown_roles}"
            )
        if status is not ReconstructionStatus.EVIDENCE_GAP and not roles:
            raise ReconstructionError(f"{label} requires at least one evidence source")
        claim_counts[(claim, status.value)] += 1

    seen_claims: set[str] = set()
    for index, raw in enumerate(_sequence(root["claims"], "claims")):
        label = f"claims[{index}]"
        claim = _mapping(raw, label)
        _exact_keys(
            claim,
            {"claim", "status", "numerator", "denominator", "method", "reason"},
            label,
        )
        name = _string(claim, "claim", label)
        if name in seen_claims:
            raise ReconstructionError(f"duplicate claim summary: {name}")
        seen_claims.add(name)
        try:
            status = ReconstructionStatus(_string(claim, "status", label))
        except ValueError as error:
            raise ReconstructionError(f"{label}.status is unknown") from error
        numerator = _integer(claim, "numerator", label)
        denominator = _integer(claim, "denominator", label)
        if numerator > denominator:
            raise ReconstructionError(f"{label}.numerator exceeds denominator")
        _string(claim, "method", label)
        _string(claim, "reason", label)
        matching_entities = sum(
            count
            for (entity_claim, _), count in claim_counts.items()
            if entity_claim == name
        )
        matching_status = claim_counts[(name, status.value)]
        if denominator != matching_entities or numerator != matching_status:
            raise ReconstructionError(
                f"{label} does not reconcile: expected numerator={matching_status}, "
                f"denominator={matching_entities}"
            )
    entity_claims = {claim for claim, _ in claim_counts}
    if entity_claims != seen_claims:
        raise ReconstructionError(
            f"claim coverage differs: missing={sorted(entity_claims - seen_claims)}, "
            f"extra={sorted(seen_claims - entity_claims)}"
        )
    _sequence(root["integrity_issues"], "integrity_issues")
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
    elif args.command == "validate":
        validate_audit(_load(args.audit))
        print(args.audit)
    else:
        validate_canonical_registry(args.registry)
        print(args.registry)


if __name__ == "__main__":
    main()
