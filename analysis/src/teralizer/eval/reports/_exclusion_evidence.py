"""Canonical RQ6 exclusion mechanisms and typed evidence relations."""

from __future__ import annotations

import re
from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import share_value
from teralizer.eval.reports import _funnel


class MechanismKey(StrEnum):
    INCLUDED = "included"
    FILTER_REJECTION = "filter_rejection"
    BUILD_QUARANTINE = "build_quarantine"
    GENERATION_GATE = "generation_gate"
    INLINE_CAPABILITY = "inline_capability"
    TASK_EXCEPTION = "task_exception"


class ReaderOutcome(StrEnum):
    INCLUDED = "included"
    FILTERING = "filtering"
    FAILURES = "failures"


@dataclass(frozen=True)
class Mechanism:
    key: MechanismKey
    label: str
    reader_outcome: ReaderOutcome


MECHANISMS = (
    Mechanism(MechanismKey.INCLUDED, "Included", ReaderOutcome.INCLUDED),
    Mechanism(
        MechanismKey.FILTER_REJECTION,
        "Filter rejection",
        ReaderOutcome.FILTERING,
    ),
    Mechanism(
        MechanismKey.GENERATION_GATE,
        "Generation-time gate",
        ReaderOutcome.FILTERING,
    ),
    Mechanism(
        MechanismKey.INLINE_CAPABILITY,
        "Unsupported capability",
        ReaderOutcome.FILTERING,
    ),
    Mechanism(
        MechanismKey.BUILD_QUARANTINE,
        "Build quarantine",
        ReaderOutcome.FAILURES,
    ),
    Mechanism(
        MechanismKey.TASK_EXCEPTION,
        "Task exception",
        ReaderOutcome.FAILURES,
    ),
)
MECHANISM_BY_KEY = {mechanism.key: mechanism for mechanism in MECHANISMS}
MECHANISM_COLUMNS = tuple(mechanism.key.value for mechanism in MECHANISMS)
READER_COLLAPSE = {
    outcome.value: tuple(
        mechanism.key.value
        for mechanism in MECHANISMS
        if mechanism.reader_outcome is outcome
    )
    for outcome in ReaderOutcome
}

FILTER_CLASS_PATTERN = r"filter\.\w+Filter$"
FILTER_CLASS_RE = re.compile(FILTER_CLASS_PATTERN)
QUARANTINE_PRODUCER = "GeneratedTestValidator"
GATE_CODES = frozenset({"ORACLE_NOT_WIDENABLE", "INPUT_SPEC_NOT_SATISFIED_BY_SEED"})
QUARANTINE_CODES = frozenset(
    {"UNCOMPILABLE_GENERALIZED_TEST", "UNCOMPILABLE_INSTRUMENTED_WRAPPER"}
)
CAPABILITY_CODES = frozenset({"INHERITED_METHOD_NOT_FLATTENABLE"})
KNOWN_TYPED_CODES = GATE_CODES | QUARANTINE_CODES | CAPABILITY_CODES


class ExclusionEvidenceError(RuntimeError):
    """Raised when persisted evidence has no unique accepted interpretation."""


def producer_mechanism(
    producer: str, decision: str, reason_code: str | None
) -> MechanismKey | None:
    """Classify one filter-result producer from its semantics, not its table."""
    producer_is_filter = FILTER_CLASS_RE.search(producer) is not None
    producer_is_quarantine = producer == QUARANTINE_PRODUCER
    if not producer_is_filter and not producer_is_quarantine:
        raise ExclusionEvidenceError(f"unknown filter-result producer: {producer}")
    if decision not in {"ACCEPT", "DEFER", "REJECT"}:
        raise ExclusionEvidenceError(
            f"unknown filter-result decision: {producer} decision={decision}"
        )
    if decision != "REJECT":
        return None
    if reason_code is None:
        raise ExclusionEvidenceError(
            f"missing rejection reason: {producer} decision={decision}"
        )
    if producer_is_filter:
        return MechanismKey.FILTER_REJECTION
    if reason_code in QUARANTINE_CODES:
        return MechanismKey.BUILD_QUARANTINE
    raise ExclusionEvidenceError(
        f"unknown quarantine rejection code: {producer} reason_code={reason_code}"
    )


def typed_code_mechanism(level: str, code: str) -> MechanismKey:
    """Classify one typed exclusion code at its valid entity level."""
    if code in GATE_CODES and level == "Generalization":
        return MechanismKey.GENERATION_GATE
    if code in QUARANTINE_CODES and level in {"Assertion", "Generalization"}:
        return MechanismKey.BUILD_QUARANTINE
    if code in CAPABILITY_CODES and level == "Test":
        return MechanismKey.INLINE_CAPABILITY
    if code in KNOWN_TYPED_CODES:
        raise ExclusionEvidenceError(
            f"exclusion code {code} is invalid at {level} level"
        )
    raise ExclusionEvidenceError(f"unknown {level} exclusion code: {code}")


def resolve_mechanism_candidates(
    level: str,
    entity_id: int,
    candidates: Sequence[MechanismKey],
) -> MechanismKey | None:
    """Resolve one entity's causal evidence without inventing absent evidence."""
    unique = tuple(dict.fromkeys(candidates))
    if not unique:
        return None
    if len(unique) > 1:
        keys = ",".join(candidate.value for candidate in unique)
        raise ExclusionEvidenceError(
            f"multiply classified entity: {level}:{entity_id}:{keys}"
        )
    return unique[0]


def query_params(variant: str) -> dict[str, object]:
    return {
        **_funnel.base_query_params(variant),
        "filter_class_pattern": FILTER_CLASS_PATTERN,
        "quarantine_producer": QUARANTINE_PRODUCER,
        "gate_codes": sorted(GATE_CODES),
        "quarantine_codes": sorted(QUARANTINE_CODES),
        "capability_codes": sorted(CAPABILITY_CODES),
        "known_typed_codes": sorted(KNOWN_TYPED_CODES),
    }


# These relations preserve only evidence that exists at their entity level. The
# final UNION happens after each relation has resolved exactly one mechanism.
_TYPED_RELATIONS_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
filter_adjudication_evidence AS (
    SELECT
        fr.id AS evidence_id,
        fr.project_id,
        CASE
            WHEN fr.test_id IS NOT NULL THEN 'Test'
            WHEN fr.assertion_id IS NOT NULL THEN 'Assertion'
            WHEN fr.generalization_id IS NOT NULL THEN 'Generalization'
        END AS level,
        coalesce(fr.test_id, fr.assertion_id, fr.generalization_id) AS entity_id,
        fr.filter_name AS producer,
        fr.decision,
        fr.reason_code,
        num_nonnulls(fr.test_id, fr.assertion_id, fr.generalization_id) AS identity_count,
        CASE
            WHEN fr.filter_name ~ :filter_class_pattern THEN 'filter_rejection'
            WHEN fr.filter_name = :quarantine_producer THEN 'build_quarantine'
        END AS producer_mechanism
    FROM filter_result fr
),
eligible_filter_evidence AS (
    SELECT fa.*
    FROM filter_adjudication_evidence fa
    JOIN eligible_projects ep ON ep.id = fa.project_id
),
test_lifecycle_evidence AS (
    SELECT
        t.project_id,
        t.id AS entity_id,
        t.is_included,
        split_part(t.exclusion_info, ':', 1) AS exclusion_code,
        EXISTS (
            SELECT 1
            FROM eligible_filter_evidence fa
            WHERE fa.level = 'Test'
              AND fa.entity_id = t.id
              AND fa.decision = 'REJECT'
              AND fa.producer_mechanism = 'filter_rejection'
        ) AS filter_rejected,
        EXISTS (
            SELECT 1
            FROM eligible_filter_evidence fa
            WHERE fa.level = 'Test'
              AND fa.entity_id = t.id
              AND fa.producer_mechanism = 'build_quarantine'
        ) AS quarantined,
        EXISTS (
            SELECT 1
            FROM task failed
            WHERE failed.test_id = t.id
              AND failed.assertion_id IS NULL
              AND failed.generalization_id IS NULL
              AND failed.status <> 'SUCCEEDED'
        ) AS task_failed
    FROM test t
    JOIN eligible_projects ep ON ep.id = t.project_id
),
assertion_evidence AS (
    SELECT
        a.project_id,
        a.id AS entity_id,
        a.is_included,
        split_part(a.exclusion_info, ':', 1) AS exclusion_code,
        EXISTS (
            SELECT 1
            FROM eligible_filter_evidence fa
            WHERE fa.level = 'Assertion'
              AND fa.entity_id = a.id
              AND fa.decision = 'REJECT'
              AND fa.producer_mechanism = 'filter_rejection'
        ) AS filter_rejected,
        EXISTS (
            SELECT 1
            FROM eligible_filter_evidence fa
            WHERE fa.level = 'Assertion'
              AND fa.entity_id = a.id
              AND fa.producer_mechanism = 'build_quarantine'
        ) AS quarantined,
        EXISTS (
            SELECT 1
            FROM task failed
            WHERE failed.assertion_id = a.id
              AND failed.generalization_id IS NULL
              AND failed.status <> 'SUCCEEDED'
        ) AS task_failed
    FROM assertion a
    JOIN eligible_projects ep ON ep.id = a.project_id
),
generated_generalization_evidence AS (
    SELECT
        g.project_id,
        g.id AS entity_id,
        split_part(g.exclusion_info, ':', 1) AS exclusion_code,
        l.generated_filter_passed,
        l.final_failure_stage,
        EXISTS (
            SELECT 1
            FROM eligible_filter_evidence fa
            WHERE fa.level = 'Generalization'
              AND fa.entity_id = g.id
              AND fa.decision = 'REJECT'
              AND fa.producer_mechanism = 'filter_rejection'
        ) AS filter_rejected,
        EXISTS (
            SELECT 1
            FROM eligible_filter_evidence fa
            WHERE fa.level = 'Generalization'
              AND fa.entity_id = g.id
              AND fa.producer_mechanism = 'build_quarantine'
        ) AS quarantined,
        EXISTS (
            SELECT 1
            FROM task failed
            WHERE failed.generalization_id = g.id
              AND failed.status <> 'SUCCEEDED'
        ) AS task_failed
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
    WHERE g.variant = :variant
),
test_mechanism_candidates AS (
    SELECT
        project_id,
        entity_id,
        'Test' AS level,
        ARRAY_REMOVE(ARRAY[
            CASE WHEN is_included AND NOT task_failed THEN 'included' END,
            CASE WHEN filter_rejected THEN 'filter_rejection' END,
            CASE WHEN quarantined OR exclusion_code = ANY(:quarantine_codes)
                 THEN 'build_quarantine' END,
            CASE WHEN exclusion_code = ANY(:capability_codes)
                 THEN 'inline_capability' END,
            CASE WHEN (task_failed OR exclusion_code LIKE 'Excluded by%')
                       AND NOT filter_rejected
                       AND NOT quarantined
                       AND (exclusion_code IS NULL
                            OR exclusion_code <> ALL(:quarantine_codes))
                       AND (exclusion_code IS NULL
                            OR exclusion_code <> ALL(:capability_codes))
                 THEN 'task_exception' END
        ], NULL) AS candidates
    FROM test_lifecycle_evidence
),
assertion_mechanism_candidates AS (
    SELECT
        project_id,
        entity_id,
        'Assertion' AS level,
        ARRAY_REMOVE(ARRAY[
            CASE WHEN is_included AND NOT task_failed THEN 'included' END,
            CASE WHEN filter_rejected THEN 'filter_rejection' END,
            CASE WHEN quarantined OR exclusion_code = ANY(:quarantine_codes)
                 THEN 'build_quarantine' END,
            CASE WHEN (task_failed OR exclusion_code LIKE 'Excluded by%')
                       AND NOT filter_rejected
                       AND NOT quarantined
                       AND (exclusion_code IS NULL
                            OR exclusion_code <> ALL(:quarantine_codes))
                 THEN 'task_exception' END
        ], NULL) AS candidates
    FROM assertion_evidence
),
generalization_mechanism_candidates AS (
    SELECT
        project_id,
        entity_id,
        'Generalization' AS level,
        ARRAY_REMOVE(ARRAY[
            CASE WHEN generated_filter_passed THEN 'included' END,
            CASE WHEN exclusion_code = ANY(:gate_codes)
                 THEN 'generation_gate' END,
            CASE WHEN filter_rejected THEN 'filter_rejection' END,
            CASE WHEN quarantined OR exclusion_code = ANY(:quarantine_codes)
                 THEN 'build_quarantine' END,
            CASE WHEN NOT coalesce(generated_filter_passed, FALSE)
                       AND NOT filter_rejected
                       AND NOT quarantined
                       AND (exclusion_code IS NULL
                            OR exclusion_code <> ALL(:gate_codes))
                       AND (exclusion_code IS NULL
                            OR exclusion_code <> ALL(:quarantine_codes))
                       AND (task_failed OR final_failure_stage IS NOT NULL
                            OR exclusion_code LIKE 'Excluded by%')
                 THEN 'task_exception' END
        ], NULL) AS candidates
    FROM generated_generalization_evidence
),
all_mechanism_candidates AS (
    SELECT * FROM test_mechanism_candidates
    UNION ALL
    SELECT * FROM assertion_mechanism_candidates
    UNION ALL
    SELECT * FROM generalization_mechanism_candidates
),
mechanism_evidence AS (
    SELECT project_id, entity_id, level, candidates[1] AS mechanism
    FROM all_mechanism_candidates
    WHERE cardinality(candidates) = 1
)
"""


FILTER_DECISION_SQL = f"""
{_TYPED_RELATIONS_SQL}
SELECT
    fa.level,
    CASE
        WHEN substring(fa.producer from 'filter\\.(\\w+)Filter$') = 'UnsupportedAssertion'
            THEN 'AssertionType'
        ELSE substring(fa.producer from 'filter\\.(\\w+)Filter$')
    END AS filter,
    count(DISTINCT fa.entity_id)::bigint AS total,
    count(DISTINCT fa.entity_id) FILTER (WHERE fa.decision = 'ACCEPT')::bigint AS accept,
    count(DISTINCT fa.entity_id) FILTER (WHERE fa.decision = 'DEFER')::bigint AS defer,
    count(DISTINCT fa.entity_id) FILTER (WHERE fa.decision = 'REJECT')::bigint AS reject
FROM eligible_filter_evidence fa
LEFT JOIN generalization g
    ON fa.level = 'Generalization' AND g.id = fa.entity_id
WHERE fa.producer_mechanism = 'filter_rejection'
  AND (fa.level <> 'Generalization' OR g.variant = :variant)
GROUP BY fa.level, filter
HAVING count(DISTINCT fa.entity_id) FILTER (WHERE fa.decision = 'REJECT') > 0
ORDER BY
    CASE fa.level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    filter
"""


MECHANISM_COUNTS_SQL = f"""
{_TYPED_RELATIONS_SQL}
SELECT
    CASE WHEN level = 'Generalization' THEN :variant ELSE 'All' END AS strategy,
    level,
    mechanism,
    count(*)::bigint AS entity_count
FROM mechanism_evidence
GROUP BY level, mechanism
ORDER BY
    CASE level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    mechanism
"""


EVIDENCE_ISSUES_SQL = f"""
{_TYPED_RELATIONS_SQL},
typed_exclusion_evidence AS (
    SELECT 'Test' AS level, id AS entity_id,
           split_part(exclusion_info, ':', 1) AS code
    FROM test
    WHERE exclusion_info IS NOT NULL
      AND exclusion_info NOT LIKE 'Excluded by%'
    UNION ALL
    SELECT 'Assertion', id, split_part(exclusion_info, ':', 1)
    FROM assertion
    WHERE exclusion_info IS NOT NULL
      AND exclusion_info NOT LIKE 'Excluded by%'
    UNION ALL
    SELECT 'Generalization', id, split_part(exclusion_info, ':', 1)
    FROM generalization
    WHERE exclusion_info IS NOT NULL
      AND exclusion_info NOT LIKE 'Excluded by%'
)
SELECT issue, evidence
FROM (
    SELECT
        'invalid filter-result identity' AS issue,
        'filter_result:' || evidence_id::text AS evidence
    FROM filter_adjudication_evidence
    WHERE identity_count <> 1

    UNION ALL

    SELECT
        'invalid filter-result decision',
        'filter_result:' || evidence_id::text || ':' || decision
    FROM filter_adjudication_evidence
    WHERE decision NOT IN ('ACCEPT', 'DEFER', 'REJECT')

    UNION ALL

    SELECT
        'missing filter-result rejection reason',
        'filter_result:' || evidence_id::text || ':' || producer
    FROM filter_adjudication_evidence
    WHERE decision = 'REJECT' AND reason_code IS NULL

    UNION ALL

    SELECT
        'unknown filter-result producer',
        'filter_result:' || evidence_id::text || ':' || producer
    FROM filter_adjudication_evidence
    WHERE producer_mechanism IS NULL

    UNION ALL

    SELECT
        'invalid build-quarantine verdict',
        'filter_result:' || evidence_id::text || ':' || decision || ':'
            || coalesce(reason_code, '<null>')
    FROM filter_adjudication_evidence
    WHERE producer = :quarantine_producer
      AND (decision <> 'REJECT' OR reason_code IS NULL
           OR reason_code <> ALL(:quarantine_codes))

    UNION ALL

    SELECT
        'unknown typed exclusion code',
        level || ':' || entity_id::text || ':' || code
    FROM typed_exclusion_evidence
    WHERE code <> ALL(:known_typed_codes)

    UNION ALL

    SELECT
        'typed exclusion code at invalid level',
        level || ':' || entity_id::text || ':' || code
    FROM typed_exclusion_evidence
    WHERE (code = ANY(:gate_codes) AND level <> 'Generalization')
       OR (code = ANY(:quarantine_codes) AND level NOT IN ('Assertion', 'Generalization'))
       OR (code = ANY(:capability_codes) AND level <> 'Test')

    UNION ALL

    SELECT
        CASE
            WHEN cardinality(candidates) = 0 THEN 'unclassified entity'
            ELSE 'multiply classified entity'
        END,
        level || ':' || entity_id::text || ':' || array_to_string(candidates, ',')
    FROM all_mechanism_candidates
    WHERE cardinality(candidates) <> 1
) issues
ORDER BY issue, evidence
"""


def validate_evidence(conn: Connection, variant: str) -> None:
    issues = read_sql(conn, EVIDENCE_ISSUES_SQL, query_params(variant))
    if issues.empty:
        return
    details = "; ".join(
        f"{row['issue']}={row['evidence']}" for row in issues.to_dict("records")
    )
    raise ExclusionEvidenceError(details)


def fetch_filter_decisions(conn: Connection, variant: str) -> pd.DataFrame:
    validate_evidence(conn, variant)
    frame = read_sql(conn, FILTER_DECISION_SQL, query_params(variant))
    for column in ("total", "accept", "defer", "reject"):
        frame.isetitem(frame.columns.get_loc(column), frame[column].astype(int))
    return pd.DataFrame(
        frame, columns=["level", "filter", "total", "accept", "defer", "reject"]
    )


def fetch_mechanism_partition(conn: Connection, variant: str) -> pd.DataFrame:
    """Return one semantic row per entity level and exclusion mechanism."""
    validate_evidence(conn, variant)
    counts = read_sql(conn, MECHANISM_COUNTS_SQL, query_params(variant))
    counts.isetitem(
        counts.columns.get_loc("entity_count"), counts["entity_count"].astype(int)
    )
    counts.loc[:, "level_total"] = counts.groupby("level")["entity_count"].transform(
        "sum"
    )
    counts.loc[:, "share"] = counts.apply(
        lambda row: share_value(row["entity_count"], row["level_total"]), axis=1
    )
    counts.loc[:, "reader_outcome"] = counts["mechanism"].map(
        lambda key: MECHANISM_BY_KEY[MechanismKey(key)].reader_outcome.value
    )
    counts.loc[:, "mechanism_label"] = counts["mechanism"].map(
        lambda key: MECHANISM_BY_KEY[MechanismKey(key)].label
    )
    counts.loc[:, "row_key"] = counts["level"].str.lower() + "." + counts["mechanism"]
    return pd.DataFrame(
        counts,
        columns=[
            "row_key",
            "strategy",
            "level",
            "mechanism",
            "mechanism_label",
            "reader_outcome",
            "entity_count",
            "level_total",
            "share",
        ],
    )


def pivot_mechanism_partition(partition: pd.DataFrame) -> pd.DataFrame:
    """Pivot the normalized partition for the existing reader-facing collapse."""
    wide = partition.pivot(
        index=["strategy", "level"], columns="mechanism", values="entity_count"
    ).fillna(0)
    wide = wide.reindex(columns=MECHANISM_COLUMNS, fill_value=0).astype(int)
    wide.loc[:, "total"] = wide.sum(axis=1)
    wide = wide.reset_index()
    return pd.DataFrame(
        wide, columns=["strategy", "level", "total", *MECHANISM_COLUMNS]
    )
