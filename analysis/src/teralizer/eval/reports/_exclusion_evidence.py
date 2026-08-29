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
from teralizer.eval.reports._taxonomy import (
    CAPABILITY_CODES,
    FILTER_CLASS_PATTERN,
    GATE_CODES,
    KNOWN_TYPED_CODES,
    QUARANTINE_CODES,
    QUARANTINE_PRODUCER,
)


class MechanismKey(StrEnum):
    INCLUDED = "included"
    FILTER_REJECTION = "filter_rejection"
    BUILD_QUARANTINE = "build_quarantine"
    GENERATION_GATE = "generation_gate"
    INHERITED_TEST_INLINING_LIMIT = "inherited_test_inlining_limit"
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
        MechanismKey.INHERITED_TEST_INLINING_LIMIT,
        "Inherited-test inlining limit",
        ReaderOutcome.FILTERING,
    ),
    Mechanism(
        MechanismKey.BUILD_QUARANTINE,
        "Compilation failure",
        ReaderOutcome.FAILURES,
    ),
    Mechanism(
        MechanismKey.TASK_EXCEPTION,
        "Processing failure",
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

FILTER_CLASS_RE = re.compile(FILTER_CLASS_PATTERN)
GENERALIZATION_FILTERS = (
    "SeedSpecConsistency",
    "WideningLicense",
    "NonPassingTest",
)
FIRST_ROUND_TEST_FILTERS = frozenset({"NonPassingTest", "TestType"})
INHERITED_METHOD_FILTER = "InheritedTestMethod"


class ExclusionEvidenceError(RuntimeError):
    """Raised when persisted evidence has no unique accepted interpretation."""


@dataclass(frozen=True)
class TestFilteringFlow:
    """Persisted populations at the inherited screen and two test-filter rounds."""

    identified: int
    inherited_method_evaluated: int
    inherited_method_rejected: int
    pre_filter_failures: int
    round_one_evaluated: int
    round_one_rejected: int
    round_one_overlap: int
    inter_round_failures: int
    round_two_evaluated: int

    def __post_init__(self) -> None:
        counts = tuple(self.__dict__.values())
        if any(count < 0 for count in counts):
            raise ExclusionEvidenceError(
                "test-filtering flow contains a negative count"
            )
        if self.inherited_method_rejected > self.inherited_method_evaluated:
            raise ExclusionEvidenceError(
                "inherited-method rejections exceed the evaluated population"
            )
        expected_round_one = (
            self.identified - self.inherited_method_rejected - self.pre_filter_failures
        )
        if self.round_one_evaluated != expected_round_one:
            raise ExclusionEvidenceError(
                "first-round test population does not reconcile: "
                f"observed={self.round_one_evaluated}, expected={expected_round_one}"
            )
        if self.round_one_overlap > self.round_one_rejected:
            raise ExclusionEvidenceError(
                "first-round overlap exceeds the rejected population"
            )
        expected_round_two = (
            self.round_one_evaluated
            - self.round_one_rejected
            - self.inter_round_failures
        )
        if self.round_two_evaluated != expected_round_two:
            raise ExclusionEvidenceError(
                "second-round test population does not reconcile: "
                f"observed={self.round_two_evaluated}, expected={expected_round_two}"
            )


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
        return MechanismKey.INHERITED_TEST_INLINING_LIMIT
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
        "first_round_test_filters": sorted(FIRST_ROUND_TEST_FILTERS),
    }


# These relations preserve only evidence that exists at their entity level. The
# final UNION happens after each relation has resolved exactly one mechanism.
_TYPED_RELATIONS_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
filter_result_evidence AS (
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
    FROM filter_result_evidence fa
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
                 THEN 'inherited_test_inlining_limit' END,
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


_PROACTIVE_FILTER_RELATIONS_SQL = f"""
{_TYPED_RELATIONS_SQL},
resolved_test_method_evidence AS (
    SELECT
        t.id AS entity_id,
        t.test_class_qualified_name,
        rtrim(
            regexp_replace(
                regexp_replace(
                    substring(t.test_method_absolute_path from '^(.*)#method'),
                    '#subPackage\\[name=([^]]+)\\]',
                    '\\1.',
                    'g'
                ),
                '#containedType\\[name=([^]]+)\\]',
                '\\1.',
                'g'
            ),
            '.'
        ) AS declaring_type,
        split_part(t.exclusion_info, ':', 1) AS exclusion_code
    FROM test t
    JOIN eligible_projects ep ON ep.id = t.project_id
    WHERE t.test_method_absolute_path IS NOT NULL
),
generalization_proactive_filter_evidence AS (
    SELECT
        g.id AS entity_id,
        split_part(g.exclusion_info, ':', 1) AS exclusion_code,
        coalesce(l.generated_source_created, FALSE) AS generated_source_created
    FROM generalization g
    JOIN eligible_projects ep ON ep.id = g.project_id
    LEFT JOIN generalization_lifecycle l ON l.generalization_id = g.id
    WHERE g.variant = :variant
),
recorded_filter_evidence AS (
    SELECT
        fa.level,
        CASE
            WHEN substring(fa.producer from 'filter\\.(\\w+)Filter$') = 'UnsupportedAssertion'
                THEN 'AssertionType'
            ELSE substring(fa.producer from 'filter\\.(\\w+)Filter$')
        END AS filter,
        fa.entity_id,
        fa.decision
    FROM eligible_filter_evidence fa
    LEFT JOIN generalization g
        ON fa.level = 'Generalization' AND g.id = fa.entity_id
    WHERE fa.producer_mechanism = 'filter_rejection'
      AND (fa.level <> 'Generalization' OR g.variant = :variant)
),
proactive_filter_evidence AS (
    SELECT
        'Test' AS level,
        'InheritedTestMethod' AS filter,
        entity_id,
        CASE
            WHEN exclusion_code = 'INHERITED_METHOD_NOT_FLATTENABLE' THEN 'REJECT'
            ELSE 'ACCEPT'
        END AS decision
    FROM resolved_test_method_evidence
    WHERE declaring_type <> test_class_qualified_name

    UNION ALL

    SELECT
        'Generalization',
        'SeedSpecConsistency',
        entity_id,
        CASE
            WHEN exclusion_code = 'INPUT_SPEC_NOT_SATISFIED_BY_SEED' THEN 'REJECT'
            ELSE 'ACCEPT'
        END
    FROM generalization_proactive_filter_evidence

    UNION ALL

    SELECT
        'Generalization',
        'WideningLicense',
        entity_id,
        CASE
            WHEN exclusion_code = 'ORACLE_NOT_WIDENABLE' THEN 'REJECT'
            WHEN generated_source_created THEN 'ACCEPT'
        END
    FROM generalization_proactive_filter_evidence
    WHERE exclusion_code IS DISTINCT FROM 'INPUT_SPEC_NOT_SATISFIED_BY_SEED'
)
"""


FILTER_DECISION_SQL = f"""
{_PROACTIVE_FILTER_RELATIONS_SQL},
all_filter_evidence AS (
    SELECT * FROM recorded_filter_evidence
    UNION ALL
    SELECT * FROM proactive_filter_evidence
)
SELECT
    level,
    filter,
    count(DISTINCT entity_id)::bigint AS total,
    count(DISTINCT entity_id) FILTER (WHERE decision = 'ACCEPT')::bigint AS accept,
    count(DISTINCT entity_id) FILTER (WHERE decision = 'DEFER')::bigint AS defer,
    count(DISTINCT entity_id) FILTER (WHERE decision = 'REJECT')::bigint AS reject
FROM all_filter_evidence
GROUP BY level, filter
HAVING count(DISTINCT entity_id) FILTER (WHERE decision = 'REJECT') > 0
ORDER BY
    CASE level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    filter
"""


TEST_FILTERING_FLOW_SQL = f"""
{_PROACTIVE_FILTER_RELATIONS_SQL},
identified_test_evidence AS (
    SELECT t.id AS entity_id
    FROM test t
    JOIN eligible_projects ep ON ep.id = t.project_id
),
inherited_method_evidence AS (
    SELECT entity_id, decision
    FROM proactive_filter_evidence
    WHERE level = 'Test'
      AND filter = 'InheritedTestMethod'
),
pre_filter_failure_evidence AS (
    SELECT DISTINCT failed.test_id AS entity_id
    FROM task failed
    JOIN identified_test_evidence identified
        ON identified.entity_id = failed.test_id
    WHERE failed.status <> 'SUCCEEDED'
      AND failed.assertion_id IS NULL
      AND failed.generalization_id IS NULL
      AND failed.stage = 'COLLECT_JUNIT_REPORTS_ORIGINAL'
),
first_round_filter_evidence AS (
    SELECT entity_id, filter, decision
    FROM recorded_filter_evidence
    WHERE level = 'Test'
      AND filter = ANY(:first_round_test_filters)
),
first_round_rejected_tests AS (
    SELECT DISTINCT entity_id
    FROM first_round_filter_evidence
    WHERE decision = 'REJECT'
),
first_round_overlap AS (
    SELECT entity_id
    FROM first_round_filter_evidence
    WHERE decision = 'REJECT'
    GROUP BY entity_id
    HAVING count(DISTINCT filter) > 1
),
inter_round_failure_evidence AS (
    SELECT DISTINCT failed.test_id AS entity_id
    FROM task failed
    JOIN identified_test_evidence identified
        ON identified.entity_id = failed.test_id
    WHERE failed.status <> 'SUCCEEDED'
      AND failed.assertion_id IS NULL
      AND failed.generalization_id IS NULL
      AND failed.stage = 'FILTER_TESTS'
),
second_round_filter_evidence AS (
    SELECT entity_id, filter, decision
    FROM recorded_filter_evidence
    WHERE level = 'Test'
      AND filter <> ALL(:first_round_test_filters)
)
SELECT
    (SELECT count(*) FROM identified_test_evidence)::bigint AS identified,
    (SELECT count(DISTINCT entity_id) FROM inherited_method_evidence)::bigint
        AS inherited_method_evaluated,
    (SELECT count(DISTINCT entity_id) FROM inherited_method_evidence
        WHERE decision = 'REJECT')::bigint AS inherited_method_rejected,
    (SELECT count(*) FROM pre_filter_failure_evidence)::bigint
        AS pre_filter_failures,
    (SELECT count(DISTINCT entity_id) FROM first_round_filter_evidence)::bigint
        AS round_one_evaluated,
    (SELECT count(*) FROM first_round_rejected_tests)::bigint
        AS round_one_rejected,
    (SELECT count(*) FROM first_round_overlap)::bigint AS round_one_overlap,
    (SELECT count(*) FROM inter_round_failure_evidence)::bigint
        AS inter_round_failures,
    (SELECT count(DISTINCT entity_id) FROM second_round_filter_evidence)::bigint
        AS round_two_evaluated
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
{_PROACTIVE_FILTER_RELATIONS_SQL},
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
    FROM filter_result_evidence
    WHERE identity_count <> 1

    UNION ALL

    SELECT
        'invalid filter-result decision',
        'filter_result:' || evidence_id::text || ':' || decision
    FROM filter_result_evidence
    WHERE decision NOT IN ('ACCEPT', 'DEFER', 'REJECT')

    UNION ALL

    SELECT
        'missing filter-result rejection reason',
        'filter_result:' || evidence_id::text || ':' || producer
    FROM filter_result_evidence
    WHERE decision = 'REJECT' AND reason_code IS NULL

    UNION ALL

    SELECT
        'unknown filter-result producer',
        'filter_result:' || evidence_id::text || ':' || producer
    FROM filter_result_evidence
    WHERE producer_mechanism IS NULL

    UNION ALL

    SELECT
        'invalid build-quarantine verdict',
        'filter_result:' || evidence_id::text || ':' || decision || ':'
            || coalesce(reason_code, '<null>')
    FROM filter_result_evidence
    WHERE producer = :quarantine_producer
      AND (decision <> 'REJECT' OR reason_code IS NULL
           OR reason_code <> ALL(:quarantine_codes))

    UNION ALL

    SELECT
        'unparsed test method path',
        'Test:' || entity_id::text
    FROM resolved_test_method_evidence
    WHERE declaring_type IS NULL

    UNION ALL

    SELECT
        'inherited-method exclusion without inherited path',
        'Test:' || typed.entity_id::text
    FROM typed_exclusion_evidence typed
    WHERE typed.level = 'Test'
      AND typed.code = 'INHERITED_METHOD_NOT_FLATTENABLE'
      AND NOT EXISTS (
          SELECT 1
          FROM resolved_test_method_evidence resolved
          WHERE resolved.entity_id = typed.entity_id
            AND resolved.declaring_type <> resolved.test_class_qualified_name
      )

    UNION ALL

    SELECT
        'incomplete proactive filter decision',
        level || ':' || filter || ':' || entity_id::text
    FROM proactive_filter_evidence
    WHERE decision IS NULL

    UNION ALL

    SELECT
        'pre-emission rejection created source',
        'Generalization:' || entity_id::text || ':' || exclusion_code
    FROM generalization_proactive_filter_evidence
    WHERE exclusion_code = ANY(:gate_codes)
      AND generated_source_created

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
    """Read filter decisions after report-level evidence validation."""
    frame = read_sql(conn, FILTER_DECISION_SQL, query_params(variant))
    for column in ("total", "accept", "defer", "reject"):
        frame.isetitem(frame.columns.get_loc(column), frame[column].astype(int))
    return pd.DataFrame(
        frame, columns=["level", "filter", "total", "accept", "defer", "reject"]
    )


def fetch_test_filtering_flow(conn: Connection, variant: str) -> TestFilteringFlow:
    """Read and reconcile persisted populations across test filtering."""
    frame = read_sql(conn, TEST_FILTERING_FLOW_SQL, query_params(variant))
    if len(frame) != 1:
        raise ExclusionEvidenceError(
            f"test-filtering flow query returned {len(frame)} rows"
        )
    row = frame.iloc[0]
    return TestFilteringFlow(
        identified=int(row["identified"]),
        inherited_method_evaluated=int(row["inherited_method_evaluated"]),
        inherited_method_rejected=int(row["inherited_method_rejected"]),
        pre_filter_failures=int(row["pre_filter_failures"]),
        round_one_evaluated=int(row["round_one_evaluated"]),
        round_one_rejected=int(row["round_one_rejected"]),
        round_one_overlap=int(row["round_one_overlap"]),
        inter_round_failures=int(row["inter_round_failures"]),
        round_two_evaluated=int(row["round_two_evaluated"]),
    )


def validate_filtering_reconciliation(
    decisions: pd.DataFrame, partition: pd.DataFrame
) -> None:
    """Require complete generalization filter rows and outcome reconciliation."""
    reconstructed = decisions[["accept", "defer", "reject"]].sum(axis=1)
    invalid_totals = decisions.loc[reconstructed != decisions["total"], "filter"]
    if not invalid_totals.empty:
        raise ExclusionEvidenceError(
            "filter decision totals do not reconcile: "
            + ",".join(sorted(invalid_totals.astype(str)))
        )

    generalization = decisions.loc[decisions["level"] == "Generalization"]
    observed_filters = frozenset(generalization["filter"].astype(str))
    expected_filters = frozenset(GENERALIZATION_FILTERS)
    if observed_filters != expected_filters:
        raise ExclusionEvidenceError(
            "generalization filter set disagrees with the semantic registry: "
            f"observed={sorted(observed_filters)}, expected={sorted(expected_filters)}"
        )

    expected_rejections = int(
        partition.loc[
            (partition["level"] == "Generalization")
            & (partition["reader_outcome"] == ReaderOutcome.FILTERING.value),
            "entity_count",
        ].sum()
    )
    observed_rejections = int(generalization["reject"].sum())
    if observed_rejections != expected_rejections:
        raise ExclusionEvidenceError(
            "generalization filter rejections disagree with the mechanism partition: "
            f"observed={observed_rejections}, expected={expected_rejections}"
        )


def fetch_mechanism_partition(conn: Connection, variant: str) -> pd.DataFrame:
    """Read the mechanism partition after report-level evidence validation."""
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
