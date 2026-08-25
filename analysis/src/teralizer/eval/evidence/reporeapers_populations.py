"""Freeze reconstruction populations from the registered version 7 corpus."""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer import corpora
from teralizer.eval.data import Required
from teralizer.eval.inputs import (
    CorpusInputSnapshot,
    CorpusInputSpec,
    FileInputSpec,
    resolve_inputs,
)
from teralizer.eval.evidence import reporeapers_reconstruction
from teralizer.eval.reports import _funnel

CORPUS_ID = "real-world"
SCHEMA_VERSION = 1
_REPO_ROOT = Path(__file__).resolve().parents[5]


class PopulationError(ValueError):
    """A query result cannot identify a reconstruction population."""


@dataclass(frozen=True)
class PopulationQuery:
    """One fixed read-only query and its stable cross-source identity fields."""

    claim: str
    definition: str
    sql: str
    identity_fields: tuple[str, ...]
    fields: tuple[str, ...]

    def __post_init__(self) -> None:
        if not self.claim or not self.definition:
            raise ValueError("population query requires a claim and definition")
        if not self.identity_fields or not self.fields:
            raise ValueError(
                "population query requires identity fields and result fields"
            )
        if not set(self.identity_fields) <= set(self.fields):
            raise ValueError("population identity fields must be query result fields")
        if not self.sql.lstrip().upper().startswith("WITH"):
            raise ValueError(
                "population query must be a fixed SELECT with a WITH clause"
            )


_INPUTS = (
    CorpusInputSpec(
        role="version-seven",
        corpus_id=CORPUS_ID,
        requires=(
            Required("project", "table", ("root_path", "git_version")),
            Required(
                "test",
                "table",
                (
                    "project_id",
                    "test_file_path",
                    "test_class_qualified_name",
                    "test_method_qualified_name",
                ),
            ),
            Required(
                "assertion",
                "table",
                (
                    "project_id",
                    "test_id",
                    "assertion_name",
                    "assertion_arguments",
                    "assertion_source_code",
                    "assertion_relative_path",
                ),
            ),
            Required(
                "filter_result",
                "table",
                ("test_id", "filter_name", "decision", "reason", "reason_code"),
            ),
            Required(
                "mut_resolution_observation",
                "table",
                (
                    "assertion_id",
                    "status",
                    "confidence_tier",
                    "deciding_signal",
                    "corroborating_signals",
                    "no_pick_reason",
                    "candidate_count",
                    "resolved_call_source",
                    "resolved_method_name",
                    "resolved_declaring_type",
                    "candidate_details",
                ),
            ),
            Required(
                "task",
                "table",
                (
                    "project_id",
                    "test_id",
                    "assertion_id",
                    "generalization_id",
                    "stage",
                    "status",
                ),
            ),
        ),
    ),
    FileInputSpec(
        role="inventory",
        path="analysis/data/report-inputs/reporeapers-reconstruction-inventory.json",
        content_addressed=True,
    ),
)

_PROJECT_FIELDS = ("project_root", "project_revision")
_TEST_FIELDS = (
    "test_file_path",
    "test_class_qualified_name",
    "test_method_qualified_name",
)
_ASSERTION_FIELDS = (
    "assertion_relative_path",
    "assertion_name",
    "assertion_arguments",
    "assertion_source_code",
)

NO_ASSERTIONS = PopulationQuery(
    claim="no-assertions",
    definition=(
        "Tests in eligible version 7 projects rejected by the NoAssertionsFilter."
    ),
    sql=_funnel.ELIGIBILITY_CTE
    + """
SELECT
    p.root_path AS project_root,
    p.git_version AS project_revision,
    t.test_file_path,
    t.test_class_qualified_name,
    t.test_method_qualified_name,
    t.test_method_absolute_path,
    t.test_method_relative_path,
    t.exclusion_info AS filter_outcome,
    fr.reason AS filter_reason,
    fr.reason_code AS filter_reason_code,
    fr.detail_json AS filter_detail
FROM eligible_projects ep
JOIN project p ON p.id = ep.id
JOIN test t ON t.project_id = p.id
JOIN filter_result fr ON fr.test_id = t.id
WHERE fr.filter_name LIKE '%%NoAssertionsFilter'
  AND fr.decision = 'REJECT'
ORDER BY
    p.root_path,
    t.test_file_path,
    t.test_class_qualified_name,
    t.test_method_qualified_name
""",
    identity_fields=_PROJECT_FIELDS + _TEST_FIELDS,
    fields=_PROJECT_FIELDS
    + _TEST_FIELDS
    + (
        "test_method_absolute_path",
        "test_method_relative_path",
        "filter_outcome",
        "filter_reason",
        "filter_reason_code",
        "filter_detail",
    ),
)

ASSERTION_TO_MUT = PopulationQuery(
    claim="assertion-to-mut",
    definition=(
        "Assertions in eligible version 7 projects with one persisted "
        "method-under-test resolution observation."
    ),
    sql=_funnel.ELIGIBILITY_CTE
    + """
SELECT
    p.root_path AS project_root,
    p.git_version AS project_revision,
    t.test_file_path,
    t.test_class_qualified_name,
    t.test_method_qualified_name,
    a.assertion_relative_path,
    a.assertion_name,
    a.assertion_arguments,
    a.assertion_source_code,
    a.tested_method_qualified_name,
    a.tested_method_call_source_code,
    m.status AS resolution_status,
    m.confidence_tier,
    m.deciding_signal,
    m.corroborating_signals,
    m.no_pick_reason,
    m.candidate_count,
    m.resolved_call_source,
    m.resolved_method_name,
    m.resolved_declaring_type,
    m.candidate_details
FROM eligible_projects ep
JOIN project p ON p.id = ep.id
JOIN assertion a ON a.project_id = p.id
JOIN test t ON t.id = a.test_id
JOIN mut_resolution_observation m ON m.assertion_id = a.id
ORDER BY
    p.root_path,
    t.test_file_path,
    t.test_class_qualified_name,
    t.test_method_qualified_name,
    a.assertion_relative_path,
    a.assertion_name,
    a.assertion_arguments,
    a.assertion_source_code
""",
    identity_fields=_PROJECT_FIELDS + _TEST_FIELDS + _ASSERTION_FIELDS,
    fields=_PROJECT_FIELDS
    + _TEST_FIELDS
    + _ASSERTION_FIELDS
    + (
        "tested_method_qualified_name",
        "tested_method_call_source_code",
        "resolution_status",
        "confidence_tier",
        "deciding_signal",
        "corroborating_signals",
        "no_pick_reason",
        "candidate_count",
        "resolved_call_source",
        "resolved_method_name",
        "resolved_declaring_type",
        "candidate_details",
    ),
)

REGISTERED_QUERIES = (NO_ASSERTIONS, ASSERTION_TO_MUT)


def _json_value(value: object, label: str) -> object:
    if value is None or isinstance(value, str | int | float | bool):
        return value
    if isinstance(value, Mapping):
        return {
            str(key): _json_value(item, f"{label}.{key}")
            for key, item in sorted(value.items(), key=lambda pair: str(pair[0]))
        }
    if isinstance(value, Sequence) and not isinstance(value, str | bytes | bytearray):
        return [_json_value(item, f"{label}[]") for item in value]
    raise PopulationError(f"{label} has a non-JSON value: {type(value).__name__}")


def freeze_population(
    query: PopulationQuery, rows: Sequence[Mapping[str, object]]
) -> dict[str, object]:
    """Normalize a complete query result and digest its stable identities."""
    expected = set(query.fields)
    normalized: list[dict[str, object]] = []
    identities: set[str] = set()
    for index, row in enumerate(rows):
        actual = set(row)
        if actual != expected:
            raise PopulationError(
                f"{query.claim} row {index} fields differ: "
                f"missing={sorted(expected - actual)}, "
                f"unexpected={sorted(actual - expected)}"
            )
        record = {
            field: _json_value(row[field], f"{query.claim}[{index}].{field}")
            for field in query.fields
        }
        identity = json.dumps(
            [record[field] for field in query.identity_fields],
            ensure_ascii=True,
            separators=(",", ":"),
        )
        if identity in identities:
            raise PopulationError(f"{query.claim} has duplicate identity: {identity}")
        identities.add(identity)
        normalized.append(record)
    normalized.sort(
        key=lambda record: json.dumps(
            [record[field] for field in query.identity_fields],
            ensure_ascii=True,
            separators=(",", ":"),
        )
    )
    identity_payload = json.dumps(
        [[record[field] for field in query.identity_fields] for record in normalized],
        ensure_ascii=True,
        separators=(",", ":"),
    ).encode()
    return {
        "claim": query.claim,
        "definition": query.definition,
        "identity_fields": list(query.identity_fields),
        "identity_sha256": hashlib.sha256(identity_payload).hexdigest(),
        "row_count": len(normalized),
        "rows": normalized,
    }


def extract_population(
    connection: Connection, query: PopulationQuery, variant: str
) -> dict[str, object]:
    """Execute one fixed population query on an existing read-only connection."""
    result = connection.execute(text(query.sql), _funnel.base_query_params(variant))
    rows = [dict(row) for row in result.mappings()]
    return freeze_population(query, rows)


_SCHEMA_OBJECTS = (
    "project",
    "test",
    "assertion",
    "filter_result",
    "mut_resolution_observation",
    "task",
)
_SCHEMA_IDENTITY_SQL = """
SELECT
    table_name,
    ordinal_position,
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = ANY(:table_names)
ORDER BY table_name, ordinal_position
"""


def _schema_identity(connection: Connection) -> dict[str, object]:
    rows = [
        dict(row)
        for row in connection.execute(
            text(_SCHEMA_IDENTITY_SQL), {"table_names": list(_SCHEMA_OBJECTS)}
        ).mappings()
    ]
    present = {str(row["table_name"]) for row in rows}
    missing = set(_SCHEMA_OBJECTS) - present
    if missing:
        raise PopulationError(f"schema identity is missing tables: {sorted(missing)}")
    payload = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode()
    return {"sha256": hashlib.sha256(payload).hexdigest(), "columns": rows}


def _dump_identity(path: Path) -> dict[str, object]:
    inventory = reporeapers_reconstruction.validate_inventory(
        json.loads(path.read_text(encoding="utf-8"))
    )
    sources = inventory["sources"]
    if not isinstance(sources, list):
        raise PopulationError("inventory sources must be an array")
    database_sources = [
        source
        for source in sources
        if isinstance(source, Mapping) and source.get("role") == "database-export"
    ]
    if len(database_sources) != 1:
        raise PopulationError(
            "inventory must contain exactly one version 7 database export"
        )
    source = database_sources[0]
    source_id = source.get("source_id")
    sha256 = source.get("sha256")
    if not isinstance(source_id, str) or not isinstance(sha256, str):
        raise PopulationError("database export inventory identity is incomplete")
    return {"source_id": source_id, "sha256": sha256}


def extract_registered_populations(
    *,
    queries: tuple[PopulationQuery, ...] = REGISTERED_QUERIES,
    corpus_registry: corpora.CorpusRegistry | None = None,
    repo_root: Path = _REPO_ROOT,
) -> dict[str, object]:
    """Read populations through the canonical registered-corpus transaction."""
    with resolve_inputs(
        "reporeapers-reconstruction",
        _INPUTS,
        corpus_registry=corpus_registry,
        repo_root=repo_root,
    ) as context:
        connection = context.corpus("version-seven")
        variant = _funnel.resolve_variant(connection)
        populations = [
            extract_population(connection, query, variant) for query in queries
        ]
        corpus_snapshot = next(
            snapshot
            for snapshot in context.snapshots
            if isinstance(snapshot, CorpusInputSnapshot)
            and snapshot.role == "version-seven"
        )
        inventory_path = context.file("inventory")
        if inventory_path is None:
            raise PopulationError("the version 7 evidence inventory is missing")
        return {
            "schema_version": SCHEMA_VERSION,
            "corpus": {
                "corpus_id": corpus_snapshot.corpus_id,
                "database": corpus_snapshot.database,
                "expected_projects": corpus_snapshot.expected_projects,
                "observed_projects": corpus_snapshot.observed_projects,
                "data_dir": corpus_snapshot.data_dir,
                "config_dir": corpus_snapshot.config_dir,
                "variant": variant,
                "dump": _dump_identity(inventory_path),
                "schema": _schema_identity(connection),
                "inspection_method": "registered read-only repeatable-read transaction",
            },
            "populations": populations,
        }
