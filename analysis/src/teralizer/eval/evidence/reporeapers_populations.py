"""Freeze reconstruction populations from the registered version 7 corpus."""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path, PurePosixPath

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


class NoAssertionsLabel(StrEnum):
    """Closed outcomes for review of a NoAssertionsFilter rejection."""

    GENUINE_ABSENCE = "genuine-absence"
    REACHABLE_HELPER_ASSERTION = "reachable-helper-assertion"
    UNSUPPORTED_ORACLE = "unsupported-oracle"
    INCOMPATIBLE_SOURCE = "incompatible-source"
    UNRESOLVED_EVIDENCE = "unresolved-evidence"


NO_ASSERTIONS_LABEL_STATUSES = {
    NoAssertionsLabel.GENUINE_ABSENCE.value: (
        reporeapers_reconstruction.EntityStatus.RESOLVED
    ),
    NoAssertionsLabel.REACHABLE_HELPER_ASSERTION.value: (
        reporeapers_reconstruction.EntityStatus.RESOLVED
    ),
    NoAssertionsLabel.UNSUPPORTED_ORACLE.value: (
        reporeapers_reconstruction.EntityStatus.RESOLVED
    ),
    NoAssertionsLabel.INCOMPATIBLE_SOURCE.value: (
        reporeapers_reconstruction.EntityStatus.INCOMPATIBLE
    ),
    NoAssertionsLabel.UNRESOLVED_EVIDENCE.value: (
        reporeapers_reconstruction.EntityStatus.UNRESOLVED
    ),
}


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
    (SELECT count(*) FROM assertion a WHERE a.test_id = t.id) AS recorded_assertion_count,
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
        "recorded_assertion_count",
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


NO_ASSERTIONS_REVIEW_SEED = "reporeapers-v7-no-assertions-review"
NO_ASSERTIONS_REVIEW_ALLOCATION = {
    "one-per-project": 4,
    "two-to-five-per-project": 4,
    "six-to-twenty-per-project": 8,
    "more-than-twenty-per-project": 84,
}


def _no_assertions_stratum(project_count: int) -> str:
    if project_count == 1:
        return "one-per-project"
    if project_count <= 5:
        return "two-to-five-per-project"
    if project_count <= 20:
        return "six-to-twenty-per-project"
    return "more-than-twenty-per-project"


def sample_no_assertions(
    population: Mapping[str, object],
) -> dict[str, object]:
    """Select the declared deterministic stratified review sample."""
    if population.get("claim") != NO_ASSERTIONS.claim:
        raise PopulationError("no-assertions sampling requires its frozen population")
    rows = population.get("rows")
    identity_fields = population.get("identity_fields")
    if not isinstance(rows, list) or not isinstance(identity_fields, list):
        raise PopulationError("frozen population lacks rows or identity fields")

    project_counts: dict[str, int] = {}
    for index, row in enumerate(rows):
        if not isinstance(row, Mapping):
            raise PopulationError(f"no-assertions row {index} must be an object")
        project_root = row.get("project_root")
        if not isinstance(project_root, str):
            raise PopulationError(f"no-assertions row {index} lacks project_root")
        project_counts[project_root] = project_counts.get(project_root, 0) + 1

    strata: dict[str, list[tuple[str, Mapping[str, object]]]] = {
        name: [] for name in NO_ASSERTIONS_REVIEW_ALLOCATION
    }
    for row in rows:
        project_root = row["project_root"]
        if not isinstance(project_root, str):
            raise AssertionError("validated project_root changed type")
        identity = [row[field] for field in identity_fields]
        rank = hashlib.sha256(
            (
                NO_ASSERTIONS_REVIEW_SEED
                + json.dumps(identity, ensure_ascii=True, separators=(",", ":"))
            ).encode()
        ).hexdigest()
        stratum = _no_assertions_stratum(project_counts[project_root])
        strata[stratum].append((rank, row))

    selected: list[dict[str, object]] = []
    stratum_sizes: dict[str, int] = {}
    for stratum, requested in NO_ASSERTIONS_REVIEW_ALLOCATION.items():
        ranked = sorted(strata[stratum], key=lambda item: item[0])
        if len(ranked) < requested:
            raise PopulationError(
                f"no-assertions stratum {stratum} has {len(ranked)} rows; "
                f"review requires {requested}"
            )
        stratum_sizes[stratum] = len(ranked)
        selected.extend(
            {
                "stratum": stratum,
                "selection_sha256": rank,
                "identity": {field: row[field] for field in identity_fields},
            }
            for rank, row in ranked[:requested]
        )

    return {
        "method": "stratified simple random sample without replacement",
        "seed": NO_ASSERTIONS_REVIEW_SEED,
        "population_identity_sha256": population.get("identity_sha256"),
        "population_size": len(rows),
        "stratum_sizes": stratum_sizes,
        "allocation": dict(NO_ASSERTIONS_REVIEW_ALLOCATION),
        "sample_size": len(selected),
        "selections": selected,
    }


def extract_population(
    connection: Connection, query: PopulationQuery, variant: str
) -> dict[str, object]:
    """Execute one fixed population query on an existing read-only connection."""
    result = connection.execute(text(query.sql), _funnel.base_query_params(variant))
    rows = [dict(row) for row in result.mappings()]
    return freeze_population(query, rows)


def _identity(query: PopulationQuery, row: Mapping[str, object]) -> dict[str, object]:
    return {field: row[field] for field in query.identity_fields}


def audit_identity(
    query: PopulationQuery, row: Mapping[str, object]
) -> dict[str, object]:
    """Map a population row to the report audit's stable entity identity."""
    normalized = freeze_population(query, [row])
    records = normalized["rows"]
    if not isinstance(records, list) or len(records) != 1:
        raise PopulationError("audit identity requires exactly one population row")
    record = records[0]
    if not isinstance(record, Mapping):
        raise PopulationError("normalized population row must be an object")
    level = "Assertion" if query.claim == ASSERTION_TO_MUT.claim else "Test"
    local_fields = [
        field
        for field in query.identity_fields
        if field not in {"project_root", "project_revision"}
    ]
    return {
        "corpus_id": CORPUS_ID,
        "project_root": record["project_root"],
        "project_revision": record["project_revision"],
        "level": level,
        "local_key": json.dumps(
            [record[field] for field in local_fields],
            ensure_ascii=True,
            separators=(",", ":"),
        ),
    }


def build_source_review_packet(
    query: PopulationQuery,
    row: Mapping[str, object],
    checkout_root: Path,
) -> dict[str, object]:
    """Read one collected test source without invoking project tooling."""
    normalized = freeze_population(query, [row])
    record = normalized["rows"]
    if not isinstance(record, list) or len(record) != 1:
        raise PopulationError("source review requires exactly one population row")
    evidence = record[0]
    if not isinstance(evidence, Mapping):
        raise PopulationError("normalized population row must be an object")
    project_value = evidence.get("project_root")
    source_value = evidence.get("test_file_path")
    if not isinstance(project_value, str) or not isinstance(source_value, str):
        raise PopulationError("source review row lacks project and test source paths")
    project_path = PurePosixPath(project_value)
    source_path = PurePosixPath(source_value)
    if (
        project_path.is_absolute()
        or source_path.is_absolute()
        or ".." in project_path.parts
        or ".." in source_path.parts
        or source_path.parts[: len(project_path.parts)] != project_path.parts
    ):
        raise PopulationError("test source is outside its recorded project root")
    resolved_project = (checkout_root / project_path).resolve(strict=True)
    resolved_source = (checkout_root / source_path).resolve(strict=True)
    if (
        not resolved_source.is_relative_to(resolved_project)
        or not resolved_source.is_file()
    ):
        raise PopulationError("test source is outside the collected project checkout")
    content = resolved_source.read_bytes()
    try:
        text_content = content.decode("utf-8")
    except UnicodeDecodeError as error:
        raise PopulationError(f"test source is not UTF-8: {source_value}") from error
    return {
        "schema_version": SCHEMA_VERSION,
        "claim": query.claim,
        "identity": _identity(query, evidence),
        "evidence": dict(evidence),
        "sources": [
            {
                "role": "test-source",
                "path": source_value,
                "sha256": hashlib.sha256(content).hexdigest(),
                "content": text_content,
            }
        ],
    }


def adjudicate_decisions(
    query: PopulationQuery,
    row: Mapping[str, object],
    decisions: Sequence[Mapping[str, object]],
    label_statuses: Mapping[str, reporeapers_reconstruction.EntityStatus],
) -> dict[str, object]:
    """Validate reviewer decisions and preserve agreement or disagreement."""
    normalized = freeze_population(query, [row])
    records = normalized["rows"]
    if not isinstance(records, list) or len(records) != 1:
        raise PopulationError("adjudication requires exactly one population row")
    record = records[0]
    if not isinstance(record, Mapping):
        raise PopulationError("normalized population row must be an object")
    expected_identity = _identity(query, record)
    reviewers: set[str] = set()
    reviews: list[dict[str, object]] = []
    for index, raw in enumerate(decisions):
        expected_fields = {
            "identity",
            "reviewer",
            "label",
            "rationale",
            "confidence",
            "source_ids",
        }
        if set(raw) != expected_fields:
            raise PopulationError(f"decision {index} fields differ")
        identity = raw["identity"]
        if not isinstance(identity, Mapping) or dict(identity) != expected_identity:
            raise PopulationError(
                f"decision {index} does not join to its population row"
            )
        reviewer = raw["reviewer"]
        if not isinstance(reviewer, str) or not reviewer:
            raise PopulationError(f"decision {index} reviewer must be non-empty")
        if reviewer in reviewers:
            raise PopulationError(f"duplicate reviewer decision: {reviewer}")
        reviewers.add(reviewer)
        decision_label = raw["label"]
        if not isinstance(decision_label, str) or decision_label not in label_statuses:
            raise PopulationError(f"decision {index} has an unknown label")
        rationale = raw["rationale"]
        if not isinstance(rationale, str) or not rationale:
            raise PopulationError(f"decision {index} rationale must be non-empty")
        confidence = raw["confidence"]
        try:
            tier = reporeapers_reconstruction.ConfidenceTier(confidence)
        except (TypeError, ValueError) as error:
            raise PopulationError(f"decision {index} has unknown confidence") from error
        decision_status = label_statuses[decision_label]
        if (
            decision_status is not reporeapers_reconstruction.EntityStatus.RESOLVED
            and tier is not reporeapers_reconstruction.ConfidenceTier.NONE
        ):
            raise PopulationError(
                f"decision {index} confidence must be NONE for {decision_status.value}"
            )
        source_ids = raw["source_ids"]
        if (
            not isinstance(source_ids, list)
            or any(
                not isinstance(source_id, str) or not source_id
                for source_id in source_ids
            )
            or len(source_ids) != len(set(source_ids))
        ):
            raise PopulationError(f"decision {index} source_ids are invalid")
        reviews.append(
            {
                "reviewer": reviewer,
                "label": decision_label,
                "rationale": rationale,
                "confidence": tier.value,
                "source_ids": source_ids,
            }
        )
    if not reviews:
        return {
            "status": reporeapers_reconstruction.EntityStatus.UNRESOLVED.value,
            "label": "unresolved",
            "confidence": reporeapers_reconstruction.ConfidenceTier.NONE.value,
            "rationale": "No reviewer decision is available.",
            "source_ids": [],
            "reviews": [],
            "review_state": reporeapers_reconstruction.ReviewState.UNREVIEWED.value,
        }
    labels = {str(review["label"]) for review in reviews}
    reviewed_source_ids: set[str] = set()
    for review in reviews:
        review_source_ids = review["source_ids"]
        if not isinstance(review_source_ids, list):
            raise AssertionError("validated review source ids changed type")
        reviewed_source_ids.update(str(source_id) for source_id in review_source_ids)
    source_ids = sorted(reviewed_source_ids)
    if len(labels) > 1:
        return {
            "status": reporeapers_reconstruction.EntityStatus.UNRESOLVED.value,
            "label": "unresolved",
            "confidence": reporeapers_reconstruction.ConfidenceTier.NONE.value,
            "rationale": "Reviewer decisions disagree; individual decisions are preserved.",
            "source_ids": source_ids,
            "reviews": reviews,
            "review_state": reporeapers_reconstruction.ReviewState.DISPUTED.value,
        }
    accepted_label = labels.pop()
    status = label_statuses[accepted_label]
    confidence_order = tuple(reporeapers_reconstruction.ConfidenceTier)
    confidence = max(
        (str(review["confidence"]) for review in reviews),
        key=lambda value: confidence_order.index(
            reporeapers_reconstruction.ConfidenceTier(value)
        ),
    )
    review_state = (
        reporeapers_reconstruction.ReviewState.SINGLE_REVIEWED
        if len(reviews) == 1
        else reporeapers_reconstruction.ReviewState.AGREED
    )
    return {
        "status": status.value,
        "label": accepted_label,
        "confidence": confidence,
        "rationale": "Reviewer decisions agree.",
        "source_ids": source_ids,
        "reviews": reviews,
        "review_state": review_state.value,
    }


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
