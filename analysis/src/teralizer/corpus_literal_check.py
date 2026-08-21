"""Reject registered physical database names in live consumers."""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path
import re
import subprocess

from teralizer import corpora

_REPO_ROOT = Path(__file__).resolve().parents[3]
_SCAN_PATHS = (
    Path("analysis/src"),
    Path("scripts"),
    Path("replication"),
    Path("project-configs"),
    Path("src/main/java"),
    Path("src/main/resources"),
    Path(".env.example"),
    Path("build.gradle"),
    Path("dev-run.sh"),
    Path("docker-compose.yml"),
)
_EXCLUDED_PATHS = (Path("replication/datasets"),)
_ALLOWED_PATHS = (Path("src/main/resources/db/corpora.toml"),)
_TEXT_SUFFIXES = frozenset(
    {".conf", ".gradle", ".java", ".json", ".py", ".sh", ".toml", ".yaml", ".yml"}
)
_SYNTHETIC_FIXTURE_ROOT = Path("verification/fixtures/corpus-package")
_PAYLOAD_SUFFIXES = frozenset({".backup", ".bin", ".dump", ".json", ".sha256"})
_CORPUS_MANIFEST_MARKERS = (b'"corpora"', b'"producer"', b'"schema_version"')
_DUMP_CHECKSUM = re.compile(rb"(?m)^[0-9a-f]{64}\s+\*?\S+\.dump$")


@dataclass(frozen=True)
class LiteralOccurrence:
    """One forbidden physical database literal in a live file."""

    path: Path
    line: int
    database: str


@dataclass(frozen=True)
class PayloadOccurrence:
    """One generated corpus payload tracked outside the fixture boundary."""

    path: Path
    kind: str


def _candidate_files(repo_root: Path) -> tuple[Path, ...]:
    files: set[Path] = set()
    for relative in _SCAN_PATHS:
        path = repo_root / relative
        if path.is_file():
            files.add(path)
        elif path.is_dir():
            files.update(
                candidate
                for candidate in path.rglob("*")
                if candidate.is_file() and candidate.suffix in _TEXT_SUFFIXES
            )
    return tuple(sorted(files))


def find_occurrences(
    repo_root: Path, physical_names: tuple[str, ...]
) -> tuple[LiteralOccurrence, ...]:
    """Return registered physical names found outside declared boundaries."""
    occurrences: list[LiteralOccurrence] = []
    for path in _candidate_files(repo_root):
        relative = path.relative_to(repo_root)
        if relative in _ALLOWED_PATHS or any(
            relative.is_relative_to(excluded) for excluded in _EXCLUDED_PATHS
        ):
            continue
        for line_number, line in enumerate(path.read_bytes().splitlines(), start=1):
            for database in physical_names:
                if database.encode() in line:
                    occurrences.append(
                        LiteralOccurrence(relative, line_number, database)
                    )
    return tuple(occurrences)


def find_payload_occurrences(
    files: Iterable[tuple[Path, bytes]],
) -> tuple[PayloadOccurrence, ...]:
    """Return generated corpus payloads outside the synthetic fixture boundary."""
    occurrences: list[PayloadOccurrence] = []
    for path, content in files:
        if path.is_relative_to(_SYNTHETIC_FIXTURE_ROOT):
            continue
        if content.startswith(b"PGDMP"):
            occurrences.append(PayloadOccurrence(path, "PostgreSQL custom dump"))
        elif all(marker in content for marker in _CORPUS_MANIFEST_MARKERS):
            occurrences.append(PayloadOccurrence(path, "generated corpus manifest"))
        elif _DUMP_CHECKSUM.search(content):
            occurrences.append(PayloadOccurrence(path, "generated dump checksums"))
    return tuple(occurrences)


def _indexed_payload_candidates(repo_root: Path) -> tuple[tuple[Path, bytes], ...]:
    tracked = subprocess.run(
        ["git", "ls-files", "--cached", "-z"],
        cwd=repo_root,
        check=True,
        capture_output=True,
    ).stdout
    candidates: list[tuple[Path, bytes]] = []
    for raw_path in tracked.split(b"\0"):
        if not raw_path:
            continue
        path = Path(raw_path.decode())
        if path.suffix not in _PAYLOAD_SUFFIXES:
            continue
        content = subprocess.run(
            ["git", "show", f":{path.as_posix()}"],
            cwd=repo_root,
            check=True,
            capture_output=True,
        ).stdout
        candidates.append((path, content))
    return tuple(candidates)


def find_indexed_payload_occurrences(
    repo_root: Path,
) -> tuple[PayloadOccurrence, ...]:
    """Return generated payloads from the repository index."""
    return find_payload_occurrences(_indexed_payload_candidates(repo_root))


def main() -> None:
    registry = corpora.load()
    physical_names = tuple(entry.database for entry in registry.entries)
    literals = find_occurrences(_REPO_ROOT, physical_names)
    payloads = find_indexed_payload_occurrences(_REPO_ROOT)
    details = [
        f"{item.path}:{item.line}: registered physical database literal "
        f"{item.database!r}"
        for item in literals
    ]
    details.extend(f"{item.path}: {item.kind}" for item in payloads)
    if details:
        raise SystemExit(
            "production corpus data escaped declared source boundaries:\n"
            + "\n".join(details)
        )


if __name__ == "__main__":
    main()
