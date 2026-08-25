"""Normalize dynamic jqwik value logs into compact JARVIS report facts."""

from __future__ import annotations

import base64
import hashlib
import json
from collections.abc import Iterable
from pathlib import Path
from typing import Any, cast

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.evidence import write_atomic
from teralizer.jarvis_scoreboard import (
    get_census_by_mut,
    get_census_project_pvc,
    get_generated_test_runs,
    get_scoreboard,
    parse_jqwik_value_log,
)

SCHEMA_VERSION = 1


def _plain(value: object) -> object:
    if isinstance(value, Path):
        return str(value)
    if value is None:
        return None
    try:
        if bool(pd.isna(value)):
            return None
    except (TypeError, ValueError):
        pass
    item = getattr(value, "item", None)
    return item() if callable(item) else value


def _records(
    frame: pd.DataFrame, *, exclude: Iterable[str] = ()
) -> list[dict[str, object]]:
    excluded = set(exclude)
    return [
        {key: _plain(value) for key, value in row.items() if key not in excluded}
        for row in frame.to_dict("records")
    ]


def value_identity(value: object) -> str:
    """Encode a Java value losslessly, including strings with lone surrogates."""
    encoded = str(value).encode("utf-8", errors="surrogatepass")
    return base64.b64encode(encoded).decode("ascii")


def _parameter_values(path: object) -> list[dict[str, str]]:
    if path is None or (isinstance(path, float) and pd.isna(path)):
        return []
    values = parse_jqwik_value_log(cast(str | Path, path))
    unique = {
        (str(row["parameter_name"]), value_identity(row["value"]))
        for row in values.to_dict("records")
    }
    return [
        {"parameter": parameter, "value_base64": value_base64}
        for parameter, value_base64 in sorted(unique)
    ]


def _file_set_identity(paths: Iterable[object]) -> dict[str, object]:
    unique = sorted(
        {
            Path(cast(str | Path, path))
            for path in paths
            if path is not None and not (isinstance(path, float) and pd.isna(path))
        },
        key=str,
    )
    combined = hashlib.sha256()
    total_bytes = 0
    for path in unique:
        content = path.read_bytes()
        total_bytes += len(content)
        combined.update(hashlib.sha256(content).digest())
    return {
        "count": len(unique),
        "bytes": total_bytes,
        "sha256": combined.hexdigest(),
    }


def _scoreboard_facts(
    conn: Connection, variants: Iterable[str]
) -> tuple[list[dict[str, object]], dict[str, object]]:
    scoreboard = get_scoreboard(conn, variants=variants)
    paths = (
        scoreboard["jqwik_value_log_path"].tolist()
        if "jqwik_value_log_path" in scoreboard
        else []
    )
    rows = _records(scoreboard, exclude=("jqwik_value_log_path",))
    for row, path in zip(rows, paths, strict=True):
        row["parameter_values"] = _parameter_values(path)
    return rows, _file_set_identity(paths)


def _census_facts(
    conn: Connection, variants: Iterable[str]
) -> tuple[list[dict[str, object]], list[dict[str, object]], dict[str, object]]:
    variant_tuple = tuple(variants)
    by_mut = get_census_by_mut(conn, variants=variant_tuple)
    by_project = get_census_project_pvc(conn, variants=variant_tuple)
    runs = get_generated_test_runs(conn, variants=variant_tuple)
    paths = (
        runs["jqwik_value_log_path"].tolist() if "jqwik_value_log_path" in runs else []
    )
    return _records(by_mut), _records(by_project), _file_set_identity(paths)


def build_facts(
    scenarios: Connection,
    benchmark: Connection,
    *,
    scoreboard_variants: Iterable[str],
    census_variants: Iterable[str],
) -> dict[str, object]:
    """Build and reconcile the two compact JARVIS relations."""
    scoreboard, scoreboard_files = _scoreboard_facts(scenarios, scoreboard_variants)
    census_muts, census_projects, census_files = _census_facts(
        benchmark, census_variants
    )
    return {
        "schema_version": SCHEMA_VERSION,
        "sources": {
            "corpus_ids": ["jarvis-scenarios", "jarvis-benchmark"],
            "scoreboard_value_logs": scoreboard_files,
            "census_value_logs": census_files,
        },
        "reconciliation": {
            "scoreboard_rows": len(scoreboard),
            "census_mut_rows": len(census_muts),
            "census_project_rows": len(census_projects),
        },
        "scoreboard": scoreboard,
        "census_muts": census_muts,
        "census_projects": census_projects,
    }


def refresh(
    scenarios: Connection,
    benchmark: Connection,
    output: Path,
    *,
    scoreboard_variants: Iterable[str],
    census_variants: Iterable[str],
) -> dict[str, object]:
    """Materialize JARVIS value facts atomically and return the validated document."""
    document = build_facts(
        scenarios,
        benchmark,
        scoreboard_variants=scoreboard_variants,
        census_variants=census_variants,
    )
    write_atomic(output, document)
    return read(output)


def read(path: Path) -> dict[str, object]:
    """Read and validate the versioned JARVIS evidence document."""
    document: Any = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"JARVIS value facts must be a JSON object: {path}")
    if document.get("schema_version") != SCHEMA_VERSION:
        raise ValueError(
            f"unsupported JARVIS value facts schema version "
            f"{document.get('schema_version')!r}: {path}"
        )
    scoreboard = document.get("scoreboard")
    census_muts = document.get("census_muts")
    census_projects = document.get("census_projects")
    reconciliation = document.get("reconciliation")
    if not all(
        isinstance(relation, list)
        for relation in (scoreboard, census_muts, census_projects)
    ):
        raise ValueError(f"JARVIS value facts lack report relations: {path}")
    if not isinstance(reconciliation, dict):
        raise ValueError(f"JARVIS value facts lack reconciliation totals: {path}")
    expected = {
        "scoreboard_rows": len(scoreboard),
        "census_mut_rows": len(census_muts),
        "census_project_rows": len(census_projects),
    }
    observed = {key: reconciliation.get(key) for key in expected}
    if observed != expected:
        raise ValueError(
            f"JARVIS value-fact reconciliation mismatch: expected {expected}, "
            f"recorded {observed}"
        )
    return cast(dict[str, object], document)


def scoreboard_frame(path: Path) -> pd.DataFrame:
    return pd.DataFrame(cast(list[dict[str, object]], read(path)["scoreboard"]))


def census_by_mut_frame(path: Path) -> pd.DataFrame:
    rows = cast(list[dict[str, object]], read(path)["census_muts"])
    columns = [
        "project",
        "variant",
        "mut_key",
        "signature_known",
        "sound_properties",
        "all_property_executions",
        "source_test_classes",
    ]
    return pd.DataFrame(rows).loc[:, columns]


def census_project_frame(path: Path) -> pd.DataFrame:
    rows = cast(list[dict[str, object]], read(path)["census_projects"])
    columns = [
        "project",
        "variant",
        "aggregate_pvc",
        "sound_properties",
        "sound_muts",
        "unresolved_sound_properties",
    ]
    return pd.DataFrame(rows, columns=columns)
