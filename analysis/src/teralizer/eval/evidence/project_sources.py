"""Normalize ignored project source trees into report-ready source facts."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Any

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.dataset_characteristics import (
    _should_exclude_file,
    compute_project_aggregates,
    compute_project_statistics,
    get_source_directories,
)
from teralizer.eval.data import read_sql
from teralizer.eval.reports import _funnel

SCHEMA_VERSION = 1
_COLUMNS = (
    "project",
    "main_files",
    "main_classes",
    "main_sloc",
    "test_files",
    "test_classes",
    "test_sloc",
    "test_methods",
)

_INELIGIBLE_PROJECTS_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT p.root_path
FROM project p
WHERE p.use_test_generalization
  AND NOT EXISTS (
      SELECT 1 FROM eligible_projects e WHERE e.id = p.id
  )
"""


def _ineligible_project_names(conn: Connection) -> set[str]:
    rows = read_sql(
        conn,
        _INELIGIBLE_PROJECTS_SQL,
        _funnel.base_query_params(""),
    )
    return {str(path).rsplit("/", maxsplit=1)[-1] for path in rows["root_path"]}


def _git(path: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(path), *args],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()


def _source_revisions(
    projects_root: Path, excluded_projects: set[str]
) -> list[dict[str, object]]:
    source_paths: dict[str, list[str]] = {}
    for directory in get_source_directories(projects_root, excluded_projects):
        relative = Path(directory).resolve().relative_to(projects_root.resolve())
        source_paths.setdefault(relative.parts[0], []).append(
            Path(*relative.parts[1:]).as_posix()
        )
    revisions: list[dict[str, object]] = []
    for project, paths in sorted(source_paths.items()):
        project_path = projects_root / project
        revision = _git(project_path, "rev-parse", "HEAD")
        files = sorted(
            file
            for relative in paths
            for file in (project_path / relative).rglob("*.java")
            if not _should_exclude_file(str(file))
        )
        content_identity = hashlib.sha256()
        total_bytes = 0
        for file in files:
            content = file.read_bytes()
            relative = file.relative_to(project_path).as_posix()
            content_identity.update(relative.encode())
            content_identity.update(b"\0")
            content_identity.update(hashlib.sha256(content).digest())
            total_bytes += len(content)
        revisions.append(
            {
                "project": project,
                "revision": revision,
                "included_files": len(files),
                "included_bytes": total_bytes,
                "included_sha256": content_identity.hexdigest(),
            }
        )
    return revisions


def _normalized_rows(frame: pd.DataFrame) -> list[dict[str, object]]:
    missing = set(_COLUMNS).difference(frame.columns)
    if missing:
        raise ValueError(f"project source facts are missing columns {sorted(missing)}")
    normalized = frame.loc[:, list(_COLUMNS)].copy()
    normalized["project"] = normalized["project"].astype(str)
    for column in _COLUMNS[1:]:
        normalized[column] = normalized[column].astype(int)
    return list(normalized.to_dict("records"))


def build_facts(
    controlled: Connection,
    real_world: Connection,
    projects_root: Path,
) -> dict[str, object]:
    """Build one shared relation for dataset and RQ1 source measurements."""
    excluded = _ineligible_project_names(real_world)
    detailed = compute_project_statistics(projects_root, excluded)
    statistics = compute_project_aggregates(
        detailed,
        controlled,
        real_world,
        excluded_test_projects=excluded,
    )
    rows = _normalized_rows(statistics)
    revisions = _source_revisions(projects_root, excluded)
    return {
        "schema_version": SCHEMA_VERSION,
        "sources": {
            "corpus_ids": ["controlled", "real-world"],
            "project_revisions": revisions,
        },
        "reconciliation": {
            "excluded_real_world_projects": len(excluded),
            "selected_source_projects": len(revisions),
            "report_rows": len(rows),
        },
        "projects": rows,
    }


def write_atomic(path: Path, document: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(document, indent=2, sort_keys=True) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", dir=path.parent, text=True
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(content)
        temporary.replace(path)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise


def refresh(
    controlled: Connection,
    real_world: Connection,
    projects_root: Path,
    output: Path,
) -> dict[str, object]:
    """Materialize project facts atomically and return the validated document."""
    document = build_facts(controlled, real_world, projects_root)
    write_atomic(output, document)
    return read(output)


def read(path: Path) -> dict[str, object]:
    """Read and validate the versioned project-source evidence document."""
    document: Any = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"project source facts must be a JSON object: {path}")
    if document.get("schema_version") != SCHEMA_VERSION:
        raise ValueError(
            f"unsupported project source facts schema version "
            f"{document.get('schema_version')!r}: {path}"
        )
    projects = document.get("projects")
    if not isinstance(projects, list):
        raise ValueError(f"project source facts must contain a projects list: {path}")
    rows = _normalized_rows(pd.DataFrame(projects))
    reconciliation = document.get("reconciliation")
    if not isinstance(reconciliation, dict):
        raise ValueError(f"project source facts lack reconciliation totals: {path}")
    if reconciliation.get("report_rows") != len(rows):
        raise ValueError(
            "project source facts report-row total disagrees with the projects relation"
        )
    document["projects"] = rows
    return document


def frame(path: Path) -> pd.DataFrame:
    """Return the normalized project relation consumed by reports."""
    return pd.DataFrame(read(path)["projects"], columns=list(_COLUMNS))
