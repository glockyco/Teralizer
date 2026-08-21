"""Stage and publish complete, verified corpus dump artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer import corpora
from teralizer.config import db_config
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


def _dump(entry: corpora.CorpusEntry, destination: Path) -> None:
    env = os.environ.copy()
    env["PGPASSWORD"] = db_config.password
    subprocess.run(
        [
            "pg_dump",
            "--host",
            db_config.host,
            "--port",
            str(db_config.port),
            "--username",
            db_config.user,
            "--format=custom",
            "--no-owner",
            "--no-privileges",
            "--file",
            str(destination),
            entry.database,
        ],
        env=env,
        check=True,
    )


def _corpus_entry(entry: corpora.CorpusEntry, stage: Path) -> dict[str, object]:
    from teralizer.corpora import open_corpus

    with open_corpus(entry.id) as (_, conn):
        observed = corpora.validate_project_count(conn, entry)
        revision = (
            require_current_revision(conn, entry.id) if entry.derived_views else None
        )
        provenance = producer_provenance(conn)
    dump_path = stage / f"{entry.database}.dump"
    _dump(entry, dump_path)
    dump = _file_fact(dump_path, relative_to=stage)
    inputs = [
        _file_fact(path, relative_to=_REPO_ROOT).__dict__
        for path in _corpus_input_paths(entry)
    ]
    return {
        "corpus_id": entry.id,
        "database": entry.database,
        "dump": dump.__dict__,
        "expected_projects": entry.expected_projects,
        "observed_projects": observed,
        "derived_view_revision": revision,
        "inputs": inputs,
        "provenance": provenance,
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
        dump = record.get("dump")
        if not isinstance(dump, dict) or not isinstance(dump.get("path"), str):
            raise ValueError(f"manifest dump fact is missing for {declared.id!r}")
        dump_path = str(dump["path"])
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


def publish(output_dir: Path = DEFAULT_OUTPUT_DIR) -> Path:
    """Dump every published corpus and atomically promote one verified manifest set."""
    require_publishable_tree()
    registry = corpora.load()
    source_commit, dirty = checkout_snapshot()
    with tempfile.TemporaryDirectory(prefix="teralizer-corpora-") as temporary:
        stage = Path(temporary)
        entries = [_corpus_entry(entry, stage) for entry in registry.published_entries]
        document: dict[str, object] = {
            "schema_version": 1,
            "producer": {"source_commit": source_commit, "dirty": dirty},
            "corpora": entries,
        }
        validate_manifest(document, stage, registry)
        manifest_path = stage / MANIFEST_NAME
        manifest_path.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        (stage / CHECKSUMS_NAME).write_text(_checksums(document), encoding="utf-8")
        filenames = {
            str(record["dump"]["path"])
            for record in entries
            if isinstance(record.get("dump"), dict)
        }
        _promote(stage, output_dir, filenames)
    return output_dir / MANIFEST_NAME


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="publish-corpora")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args(argv)
    print(publish(args.output_dir))


if __name__ == "__main__":
    main()
