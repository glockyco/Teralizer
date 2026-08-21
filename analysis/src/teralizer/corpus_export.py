"""Export immutable corpora beside PostgreSQL and transfer verified archives."""

from __future__ import annotations

import argparse
import hashlib
import os
import shlex
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from teralizer import corpora

_REPO_ROOT = Path(__file__).resolve().parents[3]
_REMOTE_SCRIPT = _REPO_ROOT / "scripts/packaging/export-corpus-remote.sh"
DEFAULT_OUTPUT_DIR = _REPO_ROOT / "analysis/build/corpus-exports"
_SSH_OPTIONS = (
    "-T",
    "-o",
    "BatchMode=yes",
    "-o",
    "ControlMaster=no",
    "-o",
    "ControlPath=none",
    "-o",
    "ControlPersist=no",
)


class Runner(Protocol):
    """Subprocess boundary used by export and transfer."""

    def __call__(
        self, args: list[str], **kwargs: object
    ) -> subprocess.CompletedProcess[str]: ...


@dataclass(frozen=True)
class ExportEndpoint:
    """Deployment-specific route to PostgreSQL on the data host."""

    ssh_host: str
    remote_spool: str
    docker: str
    container: str
    database_user: str


@dataclass(frozen=True)
class ExportFact:
    """Verified identity and bytes of one completed remote export."""

    corpus_id: str
    database: str
    projects: int
    database_bytes: int
    sha256: str
    bytes: int


def _digest(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def _parse_fact(raw: str, entry: corpora.CorpusEntry) -> ExportFact:
    lines = [line for line in raw.splitlines() if line]
    if not lines:
        raise RuntimeError(f"corpus {entry.id!r} export returned no completion fact")
    fields = lines[-1].split("\t")
    if len(fields) != 6:
        raise RuntimeError(f"corpus {entry.id!r} export returned a malformed fact")
    fact = ExportFact(
        fields[0],
        fields[1],
        int(fields[2]),
        int(fields[3]),
        fields[4],
        int(fields[5]),
    )
    if (
        fact.corpus_id != entry.id
        or fact.database != entry.database
        or fact.projects != entry.expected_projects
    ):
        raise RuntimeError(
            f"corpus {entry.id!r} export identity disagrees with registry"
        )
    return fact


def export_corpus(
    entry: corpora.CorpusEntry,
    endpoint: ExportEndpoint,
    *,
    replace: bool = False,
    runner: Runner = subprocess.run,
) -> ExportFact:
    """Create or verify one durable database-local export."""
    command = shlex.join(
        [
            "sh",
            "-s",
            "--",
            endpoint.remote_spool,
            entry.id,
            entry.database,
            str(entry.expected_projects),
            endpoint.docker,
            endpoint.container,
            endpoint.database_user,
            str(replace).lower(),
        ]
    )
    try:
        result = runner(
            ["ssh", *_SSH_OPTIONS, endpoint.ssh_host, command],
            input=_REMOTE_SCRIPT.read_text(encoding="utf-8"),
            text=True,
            capture_output=True,
            check=True,
        )
    except subprocess.CalledProcessError as error:
        diagnostic = (error.stderr or error.stdout or str(error)).strip()
        raise RuntimeError(
            f"corpus {entry.id!r} database-local export failed: {diagnostic}"
        ) from None
    return _parse_fact(result.stdout, entry)


def transfer_corpus(
    entry: corpora.CorpusEntry,
    fact: ExportFact,
    endpoint: ExportEndpoint,
    output_dir: Path,
    *,
    replace: bool = False,
    runner: Runner = subprocess.run,
) -> Path:
    """Resume transfer of one completed archive and verify its checksum."""
    output_dir.mkdir(parents=True, exist_ok=True)
    destination = output_dir / f"{entry.database}.dump"
    partial = output_dir / f".{entry.database}.dump.partial"
    if destination.exists():
        if _digest(destination) == (fact.sha256, fact.bytes):
            return destination
        if not replace:
            raise RuntimeError(
                f"local export for corpus {entry.id!r} failed verification; "
                "rerun with --replace"
            )
        destination.unlink()
    source = (
        f"{endpoint.ssh_host}:{endpoint.remote_spool}/"
        f"{entry.id}.complete/{entry.database}.dump"
    )
    ssh_command = shlex.join(["ssh", *_SSH_OPTIONS])
    try:
        runner(
            ["rsync", "--partial", "-e", ssh_command, source, str(partial)],
            check=True,
        )
    except subprocess.CalledProcessError as error:
        raise RuntimeError(
            f"corpus {entry.id!r} archive transfer failed: {error}"
        ) from None
    observed = _digest(partial)
    if observed != (fact.sha256, fact.bytes):
        raise RuntimeError(
            f"corpus {entry.id!r} transferred archive disagrees with remote checksum"
        )
    os.replace(partial, destination)
    return destination


def export_and_transfer(
    entries: tuple[corpora.CorpusEntry, ...],
    endpoint: ExportEndpoint,
    output_dir: Path,
    *,
    replace: bool = False,
) -> tuple[Path, ...]:
    """Export and transfer corpora sequentially while retaining each checkpoint."""
    completed = []
    for entry in entries:
        fact = export_corpus(entry, endpoint, replace=replace)
        completed.append(
            transfer_corpus(entry, fact, endpoint, output_dir, replace=replace)
        )
    return tuple(completed)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="export-corpora")
    parser.add_argument("--ssh-host", required=True)
    parser.add_argument("--remote-spool", required=True)
    parser.add_argument("--docker", required=True, help="remote Docker executable")
    parser.add_argument("--postgres-container", required=True)
    parser.add_argument("--database-user", default="postgres")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--corpus", action="append", default=[])
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args(argv)

    if any(character.isspace() for character in args.remote_spool):
        parser.error("--remote-spool must not contain whitespace")
    registry = corpora.load()
    entries = (
        tuple(registry.get(corpus_id) for corpus_id in args.corpus)
        if args.corpus
        else registry.published_entries
    )
    unpublished = [entry.id for entry in entries if not entry.published]
    if unpublished:
        parser.error(f"corpora are not published: {', '.join(unpublished)}")
    endpoint = ExportEndpoint(
        args.ssh_host,
        args.remote_spool,
        args.docker,
        args.postgres_container,
        args.database_user,
    )
    for path in export_and_transfer(
        entries, endpoint, args.output_dir, replace=args.replace
    ):
        print(path)


if __name__ == "__main__":
    main()
