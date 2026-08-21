"""Stage and publish complete, verified corpus dump artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer import corpora
from teralizer.corpus_preparation import require_current_revision
from teralizer.eval.provenance import checkout_snapshot, require_publishable_tree
from teralizer.report_basis import resolve_repo_path

_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUTPUT_DIR = _REPO_ROOT / "replication/datasets"
MANIFEST_NAME = "manifest.json"
CHECKSUMS_NAME = "checksums.sha256"


@dataclass(frozen=True)
class FileFact:
    path: str
    sha256: str
    bytes: int


def _file_fact(path: Path, *, relative_to: Path) -> FileFact:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return FileFact(str(path.relative_to(relative_to)), digest.hexdigest(), size)


def _corpus_input_paths(entry: corpora.CorpusEntry) -> tuple[Path, ...]:
    paths = [corpora.REGISTRY_PATH]
    if entry.data_dir is None:
        return tuple(paths)
    assert entry.config_dir is not None
    data_dir = resolve_repo_path(Path(entry.data_dir))
    config_dir = resolve_repo_path(Path(entry.config_dir))
    paths.append(data_dir / "status.tsv")
    paths.extend(sorted((data_dir / "done").glob("project-*")))
    paths.extend(sorted(config_dir.glob("project-*.conf")))
    missing = [path for path in paths if not path.is_file()]
    if missing:
        raise FileNotFoundError(
            f"corpus {entry.id!r} input is missing: {missing[0].relative_to(_REPO_ROOT)}"
        )
    return tuple(paths)


def producer_provenance(conn: Connection) -> dict[str, object]:
    """Count projects by recorded Teralizer commit and expose missing attribution."""
    rows = conn.execute(
        text(
            "SELECT NULLIF(BTRIM(tool_git_version), '') AS commit, COUNT(*) AS projects "
            "FROM project GROUP BY NULLIF(BTRIM(tool_git_version), '') "
            "ORDER BY projects DESC, commit"
        )
    )
    commits: list[dict[str, object]] = []
    unattributed = 0
    for commit, projects in rows:
        if commit is None:
            unattributed += int(projects)
        else:
            commits.append({"commit": str(commit), "projects": int(projects)})
    return {"commits": commits, "unattributed_projects": unattributed}


def _corpus_facts(entry: corpora.CorpusEntry) -> dict[str, object]:
    from teralizer.corpora import open_corpus

    with open_corpus(entry.id) as (_, conn):
        observed = corpora.validate_project_count(conn, entry)
        database_bytes = int(
            conn.execute(
                text("SELECT pg_database_size(current_database())")
            ).scalar_one()
        )
        revision = (
            require_current_revision(conn, entry.id) if entry.derived_views else None
        )
        provenance = producer_provenance(conn)
    inputs = [
        _file_fact(path, relative_to=_REPO_ROOT).__dict__
        for path in _corpus_input_paths(entry)
    ]
    return {
        "corpus_id": entry.id,
        "database": entry.database,
        "dump_path": f"{entry.database}.dump",
        "expected_projects": entry.expected_projects,
        "observed_projects": observed,
        "database_bytes": database_bytes,
        "derived_view_revision": revision,
        "inputs": inputs,
        "provenance": provenance,
    }


def publication_plan() -> dict[str, object]:
    """Inspect every published corpus without exporting database rows."""
    require_publishable_tree()
    source_commit, dirty = checkout_snapshot()
    return {
        "schema_version": 1,
        "producer": {"source_commit": source_commit, "dirty": dirty},
        "corpora": [_corpus_facts(entry) for entry in corpora.load().published_entries],
    }


def validate_manifest(
    document: dict[str, object], stage: Path, registry: corpora.CorpusRegistry
) -> None:
    """Reject any staged fact that disagrees with the registry or staged bytes."""
    raw_entries = document.get("corpora")
    if not isinstance(raw_entries, list):
        raise ValueError("corpus manifest must contain a corpora list")
    by_id = {
        entry.get("corpus_id"): entry
        for entry in raw_entries
        if isinstance(entry, dict) and isinstance(entry.get("corpus_id"), str)
    }
    expected_ids = {entry.id for entry in registry.published_entries}
    if set(by_id) != expected_ids or len(by_id) != len(raw_entries):
        raise ValueError(
            f"manifest corpus ids {sorted(by_id)} do not match published ids {sorted(expected_ids)}"
        )
    dump_files: set[str] = set()
    for declared in registry.published_entries:
        record = by_id[declared.id]
        if record.get("database") != declared.database:
            raise ValueError(f"manifest database disagrees for corpus {declared.id!r}")
        if record.get("expected_projects") != declared.expected_projects:
            raise ValueError(
                f"manifest expected project count disagrees for {declared.id!r}"
            )
        if record.get("observed_projects") != declared.expected_projects:
            raise ValueError(
                f"manifest observed project count disagrees for {declared.id!r}"
            )
        if (
            not isinstance(record.get("database_bytes"), int)
            or record["database_bytes"] <= 0
        ):
            raise ValueError(f"manifest database size is invalid for {declared.id!r}")
        dump = record.get("dump")
        if not isinstance(dump, dict) or not isinstance(dump.get("path"), str):
            raise ValueError(f"manifest dump fact is missing for {declared.id!r}")
        dump_path = str(dump["path"])
        if dump_path != f"{declared.database}.dump":
            raise ValueError(f"manifest dump filename disagrees for {declared.id!r}")
        if dump_path in dump_files:
            raise ValueError(f"manifest reuses dump file {dump_path!r}")
        dump_files.add(dump_path)
        observed_dump = _file_fact(stage / dump_path, relative_to=stage).__dict__
        if dump != observed_dump:
            raise ValueError(f"manifest dump fact disagrees for {declared.id!r}")
        if declared.derived_views:
            expected_revision = corpora.derived_view_revision()
            if record.get("derived_view_revision") != expected_revision:
                raise ValueError(
                    f"manifest derived-view revision disagrees for {declared.id!r}"
                )
        elif record.get("derived_view_revision") is not None:
            raise ValueError(
                f"manifest records inapplicable derived views for {declared.id!r}"
            )
        raw_inputs = record.get("inputs")
        if not isinstance(raw_inputs, list) or not raw_inputs:
            raise ValueError(f"manifest inputs are missing for {declared.id!r}")
        for fact in raw_inputs:
            if not isinstance(fact, dict) or not isinstance(fact.get("path"), str):
                raise ValueError(
                    f"manifest input fact is malformed for {declared.id!r}"
                )
            path = _REPO_ROOT / str(fact["path"])
            if (
                not path.is_file()
                or fact != _file_fact(path, relative_to=_REPO_ROOT).__dict__
            ):
                raise ValueError(
                    f"manifest input fact disagrees for {declared.id!r}: {fact['path']}"
                )


def _verified_document(input_dir: Path) -> tuple[Path, dict[str, object]]:
    manifest_path = input_dir / MANIFEST_NAME
    try:
        document = json.loads(manifest_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise FileNotFoundError(f"corpus manifest not found: {manifest_path}") from None
    if not isinstance(document, dict) or document.get("schema_version") != 1:
        raise ValueError("unsupported or malformed corpus manifest")
    producer = document.get("producer")
    if (
        not isinstance(producer, dict)
        or not isinstance(producer.get("source_commit"), str)
        or len(producer["source_commit"]) != 40
        or producer.get("dirty") is not False
    ):
        raise ValueError("corpus manifest does not identify a clean producer commit")
    validate_manifest(document, input_dir, corpora.load())
    checksums_path = input_dir / CHECKSUMS_NAME
    expected_checksums = _checksums(document)
    if (
        not checksums_path.is_file()
        or checksums_path.read_text(encoding="utf-8") != expected_checksums
    ):
        raise ValueError(
            "corpus package checksum inventory disagrees with its manifest"
        )
    return manifest_path, document


def verify_package(input_dir: Path) -> Path:
    """Verify a published manifest, every dump, and every declared corpus input."""
    manifest_path, _ = _verified_document(input_dir)
    return manifest_path


def _copy_package_artifacts(
    input_dir: Path, destination_dir: Path, document: dict[str, object]
) -> int:
    """Copy one already verified manifest set without inferring dump filenames."""
    records = document["corpora"]
    assert isinstance(records, list)
    filenames = {MANIFEST_NAME, CHECKSUMS_NAME}
    filenames.update(record["dump"]["path"] for record in records)
    destination_dir.mkdir(parents=True, exist_ok=True)
    for stale in destination_dir.glob("*.dump"):
        if stale.name not in filenames:
            stale.unlink()
    for name in sorted(filenames):
        shutil.copy2(input_dir / name, destination_dir / name)
    return len(filenames)


def copy_package_artifacts(input_dir: Path, destination_dir: Path) -> int:
    """Verify and copy the complete manifest set to a package directory."""
    _, document = _verified_document(input_dir)
    return _copy_package_artifacts(input_dir, destination_dir, document)


def _copy_package_inputs(destination_root: Path, document: dict[str, object]) -> int:
    records = document["corpora"]
    assert isinstance(records, list)
    copied: set[str] = set()
    for record in records:
        for fact in record["inputs"]:
            relative = str(fact["path"])
            if relative in copied:
                continue
            source = _REPO_ROOT / relative
            destination = destination_root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            copied.add(relative)
    return len(copied)


def copy_package_inputs(input_dir: Path, destination_root: Path) -> int:
    """Verify and copy manifest-declared inputs to a package repository tree."""
    _, document = _verified_document(input_dir)
    return _copy_package_inputs(destination_root, document)


def copy_complete_package(input_dir: Path, destination_root: Path) -> tuple[int, int]:
    """Verify once, then copy corpus artifacts and inputs into a package tree."""
    _, document = _verified_document(input_dir)
    artifact_count = _copy_package_artifacts(
        input_dir, destination_root / "replication" / "datasets", document
    )
    input_count = _copy_package_inputs(destination_root, document)
    return artifact_count, input_count


def _required_disk_from_document(document: dict[str, object]) -> int:
    records = document["corpora"]
    assert isinstance(records, list)
    dump_bytes = sum(int(record["dump"]["bytes"]) for record in records)
    database_bytes = sum(int(record["database_bytes"]) for record in records)
    return dump_bytes + 2 * database_bytes


def required_disk_bytes(input_dir: Path) -> int:
    """Return dump storage plus two restored-database footprints for safe import."""
    _, document = _verified_document(input_dir)
    return _required_disk_from_document(document)


def _summary_from_document(document: dict[str, object]) -> tuple[str, ...]:
    records = document["corpora"]
    assert isinstance(records, list)
    lines = []
    for record in records:
        dump = record["dump"]
        lines.append(
            f"{record['corpus_id']}: {dump['path']} "
            f"({int(dump['bytes']):,} dump bytes, "
            f"{int(record['database_bytes']):,} database bytes)"
        )
    lines.append(
        f"required free disk: {_required_disk_from_document(document):,} bytes"
    )
    return tuple(lines)


def package_summary(input_dir: Path) -> tuple[str, ...]:
    """Return human-readable sizes from one verified package manifest."""
    _, document = _verified_document(input_dir)
    return _summary_from_document(document)


def package_preflight(input_dir: Path) -> tuple[str, ...]:
    """Return a machine-readable disk requirement and human-readable inventory."""
    _, document = _verified_document(input_dir)
    return (
        f"required_disk_bytes={_required_disk_from_document(document)}",
        *_summary_from_document(document),
    )


def _checksums(document: dict[str, object]) -> str:
    entries = document["corpora"]
    assert isinstance(entries, list)
    records = []
    for entry in entries:
        assert isinstance(entry, dict)
        dump = entry["dump"]
        assert isinstance(dump, dict)
        records.append(f"{dump['sha256']}  {dump['path']}")
    return "\n".join(sorted(records)) + "\n"


def _promote(stage: Path, output_dir: Path, filenames: set[str]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    backup = stage / ".backup"
    backup.mkdir()
    existing = {
        path.name
        for path in output_dir.iterdir()
        if path.is_file() and path.suffix == ".dump"
    }
    affected = filenames | existing | {MANIFEST_NAME, CHECKSUMS_NAME}
    moved: list[str] = []
    try:
        for name in sorted(affected):
            destination = output_dir / name
            if destination.exists():
                os.replace(destination, backup / name)
                moved.append(name)
        for name in sorted(filenames | {MANIFEST_NAME, CHECKSUMS_NAME}):
            os.replace(stage / name, output_dir / name)
    except BaseException:
        for name in filenames | {MANIFEST_NAME, CHECKSUMS_NAME}:
            (output_dir / name).unlink(missing_ok=True)
        for name in moved:
            os.replace(backup / name, output_dir / name)
        raise


def assemble(dump_dir: Path, output_dir: Path = DEFAULT_OUTPUT_DIR) -> Path:
    """Build and atomically promote a package from explicit completed dumps."""
    registry = corpora.load()
    document = publication_plan()
    records = document["corpora"]
    assert isinstance(records, list)
    with tempfile.TemporaryDirectory(prefix="teralizer-corpora-") as temporary:
        stage = Path(temporary)
        filenames: set[str] = set()
        for record in records:
            assert isinstance(record, dict)
            dump_path = record.pop("dump_path")
            assert isinstance(dump_path, str)
            source = dump_dir / dump_path
            if not source.is_file():
                raise FileNotFoundError(
                    f"published corpus {record['corpus_id']!r} dump not found: {source}"
                )
            destination = stage / dump_path
            shutil.copy2(source, destination)
            record["dump"] = _file_fact(destination, relative_to=stage).__dict__
            filenames.add(dump_path)
        validate_manifest(document, stage, registry)
        manifest_path = stage / MANIFEST_NAME
        manifest_path.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        (stage / CHECKSUMS_NAME).write_text(_checksums(document), encoding="utf-8")
        _promote(stage, output_dir, filenames)
    return output_dir / MANIFEST_NAME


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="publish-corpora")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument(
        "--plan",
        action="store_true",
        help="inspect every published corpus without exporting database rows",
    )
    action.add_argument(
        "--assemble-from",
        type=Path,
        metavar="DUMP_DIR",
        help="assemble and promote a package from explicit completed dumps",
    )
    action.add_argument(
        "--verify-package",
        type=Path,
        help="verify an existing package instead of publishing",
    )
    action.add_argument(
        "--summarize-package",
        type=Path,
        help="verify and summarize an existing package",
    )
    action.add_argument(
        "--preflight-package",
        type=Path,
        help="verify a package and print its inventory with a disk requirement",
    )
    action.add_argument(
        "--required-disk-bytes",
        type=Path,
        help="verify a package and print its required free disk bytes",
    )
    action.add_argument(
        "--copy-complete-to",
        type=Path,
        metavar="REPOSITORY_ROOT",
        help="verify once and copy the corpus package plus declared inputs",
    )
    action.add_argument(
        "--copy-package-to",
        type=Path,
        help="verify and copy the complete manifest set to a package directory",
    )
    action.add_argument(
        "--copy-inputs-to",
        type=Path,
        help="verify the output package and copy its declared inputs to a repository tree",
    )
    args = parser.parse_args(argv)
    if args.plan:
        print(json.dumps(publication_plan(), indent=2, sort_keys=True))
    elif args.assemble_from is not None:
        print(assemble(args.assemble_from, args.output_dir))
    elif args.verify_package is not None:
        print(verify_package(args.verify_package))
    elif args.summarize_package is not None:
        print("\n".join(package_summary(args.summarize_package)))
    elif args.preflight_package is not None:
        print("\n".join(package_preflight(args.preflight_package)))
    elif args.required_disk_bytes is not None:
        print(required_disk_bytes(args.required_disk_bytes))
    elif args.copy_complete_to is not None:
        artifacts, inputs = copy_complete_package(
            args.output_dir, args.copy_complete_to
        )
        print(f"copied {artifacts} package files and {inputs} corpus input files")
    elif args.copy_package_to is not None:
        count = copy_package_artifacts(args.output_dir, args.copy_package_to)
        print(f"copied {count} package files")
    elif args.copy_inputs_to is not None:
        count = copy_package_inputs(args.output_dir, args.copy_inputs_to)
        print(f"copied {count} corpus input files")


if __name__ == "__main__":
    main()
