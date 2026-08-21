"""Reject registered physical database names in live consumers."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

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


@dataclass(frozen=True)
class LiteralOccurrence:
    """One forbidden physical database literal in a live file."""

    path: Path
    line: int
    database: str


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


def main() -> None:
    registry = corpora.load()
    physical_names = tuple(entry.database for entry in registry.entries)
    occurrences = find_occurrences(_REPO_ROOT, physical_names)
    if occurrences:
        details = "\n".join(
            f"{item.path}:{item.line}: registered physical database literal "
            f"{item.database!r}"
            for item in occurrences
        )
        raise SystemExit(
            "registered physical database names escaped the registry boundary:\n"
            f"{details}"
        )


if __name__ == "__main__":
    main()
