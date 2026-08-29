"""Accepted-population detail for RQ6 filter rejections."""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import ColumnSpec, Table, ValueKind, share_value
from teralizer.eval.provenance import capture
from teralizer.eval.reports import _exclusion_evidence as exclusion
from teralizer.eval.reports import _funnel


TEST_TYPE_CATEGORY_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT CASE
           WHEN t.test_annotations_source_code = '@Theory' THEN 'junit_theory'
           WHEN t.test_annotations_source_code = '@Override' THEN 'overridden_declaration'
           WHEN t.test_annotation_name = 'TestNG' THEN 'testng'
           ELSE 'unknown'
       END AS category,
       count(DISTINCT t.id) AS count
FROM filter_result fr
JOIN eligible_projects ep ON ep.id = fr.project_id
JOIN test t ON t.id = fr.test_id
WHERE fr.decision = 'REJECT'
  AND fr.filter_name LIKE '%%TestTypeFilter'
GROUP BY category
ORDER BY category
"""
TEST_TYPE_CATEGORIES = (
    "junit_theory",
    "overridden_declaration",
    "testng",
)
TEST_TYPE_CATEGORY_METRIC_KEYS = frozenset(
    f"realworld.test_type.{category}" for category in TEST_TYPE_CATEGORIES
)

NON_PASSING_OUTCOME_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
rejected_tests AS (
    SELECT DISTINCT
        t.id AS test_id,
        t.project_id,
        t.test_package_name,
        t.test_class_name
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    JOIN test t ON t.id = fr.test_id
    WHERE fr.decision = 'REJECT'
      AND fr.filter_name LIKE '%%NonPassingTestFilter'
),
rejected_classes AS (
    SELECT DISTINCT project_id, test_package_name, test_class_name
    FROM rejected_tests
),
observed_outcomes AS (
    SELECT
        rt.test_id,
        bool_or(jtr.result = 'ERROR') AS has_error,
        bool_or(jtr.result = 'FAILED') AS has_failure,
        bool_or(jtr.result = 'SKIPPED') AS has_skip,
        bool_or(jtr.result = 'PASSED') AS has_pass
    FROM rejected_tests rt
    LEFT JOIN junit_test_report jtr
        ON jtr.test_id = rt.test_id
       AND jtr.stage = 'COLLECT_JUNIT_REPORTS_ORIGINAL'
    GROUP BY rt.test_id
),
classified AS (
    SELECT
        test_id,
        CASE
            WHEN has_error THEN 'error'
            WHEN has_failure THEN 'failed'
            WHEN has_skip THEN 'skipped'
            WHEN has_pass THEN 'passed'
            ELSE 'unobserved'
        END AS outcome
    FROM observed_outcomes
)
SELECT
    outcome,
    count(*)::bigint AS tests,
    (SELECT count(*) FROM rejected_classes)::bigint AS classes
FROM classified
GROUP BY outcome
ORDER BY CASE outcome
    WHEN 'passed' THEN 1
    WHEN 'skipped' THEN 2
    WHEN 'failed' THEN 3
    WHEN 'error' THEN 4
    ELSE 5
END
"""
NON_PASSING_OUTCOMES = ("passed", "skipped", "failed", "error")

NON_PASSING_FAILURE_TYPE_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
rejected_tests AS (
    SELECT DISTINCT t.id AS test_id
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    JOIN test t ON t.id = fr.test_id
    WHERE fr.decision = 'REJECT'
      AND fr.filter_name LIKE '%%NonPassingTestFilter'
)
SELECT
    jtr.result,
    coalesce(jtr.failure_type, '<missing>') AS failure_type,
    count(DISTINCT rt.test_id)::bigint AS tests
FROM rejected_tests rt
JOIN junit_test_report jtr
    ON jtr.test_id = rt.test_id
   AND jtr.stage = 'COLLECT_JUNIT_REPORTS_ORIGINAL'
WHERE jtr.result <> 'PASSED'
GROUP BY jtr.result, coalesce(jtr.failure_type, '<missing>')
ORDER BY tests DESC, jtr.result, failure_type
LIMIT 10
"""

ASSERTION_TYPE_REJECTION_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
assertion_population AS (
    SELECT count(*)::bigint AS assertions
    FROM assertion a
    JOIN eligible_projects ep ON ep.id = a.project_id
),
counts AS (
    SELECT
        a.assertion_name,
        count(DISTINCT a.id)::bigint AS assertions
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    JOIN assertion a ON a.id = fr.assertion_id
    WHERE fr.decision = 'REJECT'
      AND fr.filter_name LIKE '%%UnsupportedAssertionFilter'
    GROUP BY a.assertion_name
)
SELECT
    assertion_name,
    assertions,
    sum(assertions) OVER ()::bigint AS rejected_assertions,
    (SELECT assertions FROM assertion_population) AS total_assertions
FROM counts
ORDER BY assertions DESC, assertion_name
"""
ASSERTION_TYPE_NAMES = (
    "assertNotNull",
    "fail",
    "assertNull",
    "assertThat",
    "assertSame",
    "assertArrayEquals",
    "assertNotSame",
    "assertNotEquals",
    "getName",
    "assertIterableEquals",
)
ASSERTION_TYPE_METRIC_NAMES = {
    "assertNotNull": "assert_not_null",
    "fail": "fail",
    "assertNull": "assert_null",
    "assertThat": "assert_that",
}

MISSING_VALUE_CAUSE_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
counts AS (
    SELECT
        coalesce(fr.reason_code, '<missing>') AS reason_code,
        count(DISTINCT fr.assertion_id)::bigint AS assertions
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    WHERE fr.decision = 'REJECT'
      AND fr.filter_name LIKE '%%MissingValueFilter'
    GROUP BY coalesce(fr.reason_code, '<missing>')
)
SELECT
    reason_code,
    assertions,
    sum(assertions) OVER ()::bigint AS rejected_assertions
FROM counts
ORDER BY assertions DESC, reason_code
"""
MISSING_VALUE_CAUSES = (
    "UNSUPPORTED_ASSERTION_SHAPE",
    "LIBRARY_DECLARATION",
    "UNRESOLVED_SOURCE_DECLARATION",
    "NO_VISIBLE_CALL",
    "MISSING_TESTED_FILE",
)
MISSING_VALUE_METRIC_NAMES = {
    "UNSUPPORTED_ASSERTION_SHAPE": "unsupported_assertion_shape",
    "LIBRARY_DECLARATION": "library_declaration",
    "UNRESOLVED_SOURCE_DECLARATION": "unresolved_source_declaration",
    "NO_VISIBLE_CALL": "no_visible_call",
    "MISSING_TESTED_FILE": "missing_tested_file",
}

PARAMETER_TYPE_REJECTION_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
categories(category) AS (
    VALUES ('no_arguments'), ('unsupported_types'), ('unknown')
),
rejections AS (
    SELECT DISTINCT
        a.id AS assertion_id,
        CASE
            WHEN a.tested_method_call_arguments::jsonb = '[]'::jsonb
                THEN 'no_arguments'
            WHEN jsonb_array_length(a.tested_method_call_arguments::jsonb) > 0
                THEN 'unsupported_types'
            ELSE 'unknown'
        END AS category
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    JOIN assertion a ON a.id = fr.assertion_id
    WHERE fr.decision = 'REJECT'
      AND fr.filter_name LIKE '%%ParameterTypeFilter'
)
SELECT
    categories.category,
    count(rejections.assertion_id)::bigint AS assertions,
    sum(count(rejections.assertion_id)) OVER ()::bigint AS rejected_assertions
FROM categories
LEFT JOIN rejections USING (category)
GROUP BY categories.category
ORDER BY CASE categories.category
    WHEN 'no_arguments' THEN 1
    WHEN 'unsupported_types' THEN 2
    ELSE 3
END
"""
PARAMETER_TYPE_CATEGORIES = ("no_arguments", "unsupported_types", "unknown")

RETURN_TYPE_REJECTION_SQL = f"""
{_funnel.ELIGIBILITY_CTE},
counts AS (
    SELECT
        coalesce(fr.detail_json->>'return_type', '<missing>') AS return_type,
        count(DISTINCT fr.assertion_id)::bigint AS assertions
    FROM filter_result fr
    JOIN eligible_projects ep ON ep.id = fr.project_id
    WHERE fr.decision = 'REJECT'
      AND fr.filter_name LIKE '%%ReturnTypeFilter'
    GROUP BY coalesce(fr.detail_json->>'return_type', '<missing>')
),
ranked AS (
    SELECT
        return_type,
        assertions,
        sum(assertions) OVER ()::bigint AS rejected_assertions,
        row_number() OVER (ORDER BY assertions DESC, return_type) AS rank
    FROM counts
)
SELECT return_type, assertions, rejected_assertions
FROM ranked
WHERE rank <= 10
ORDER BY rank
"""
RETURN_TYPE_METRIC_NAMES = {
    "void": "void",
    "java.util.List": "list",
    "java.lang.Object": "object",
    "T": "type_variable_t",
}


def _params(variant: str) -> dict[str, object]:
    return exclusion.query_params(variant)


def _integer_columns(frame: pd.DataFrame, *columns: str) -> pd.DataFrame:
    for column in columns:
        frame.isetitem(frame.columns.get_loc(column), frame[column].astype(int))
    return frame


def _require_categories(
    frame: pd.DataFrame, column: str, expected: tuple[str, ...], subject: str
) -> None:
    actual = tuple(frame[column].astype(str))
    if set(actual) != set(expected) or len(actual) != len(expected):
        raise exclusion.ExclusionEvidenceError(
            f"{subject} categories differ: expected={sorted(expected)}, "
            f"actual={sorted(actual)}"
        )


def fetch_test_type_categories(conn: Connection, variant: str) -> dict[str, int]:
    """Return the recorded declaration categories rejected by TestType."""
    frame = read_sql(conn, TEST_TYPE_CATEGORY_SQL, _params(variant))
    categories = {
        str(row["category"]): int(row["count"]) for _, row in frame.iterrows()
    }
    if set(categories) != set(TEST_TYPE_CATEGORIES):
        raise exclusion.ExclusionEvidenceError(
            "TestType declaration categories differ: "
            f"expected={sorted(TEST_TYPE_CATEGORIES)}, "
            f"actual={sorted(categories)}"
        )
    return categories


def fetch_non_passing_outcomes(conn: Connection, variant: str) -> pd.DataFrame:
    """Partition rejected tests by their strongest recorded original outcome."""
    frame = _integer_columns(
        read_sql(conn, NON_PASSING_OUTCOME_SQL, _params(variant)), "tests", "classes"
    )
    _require_categories(
        frame, "outcome", NON_PASSING_OUTCOMES, "NonPassingTest outcome"
    )
    if frame["classes"].nunique() != 1:
        raise exclusion.ExclusionEvidenceError(
            "NonPassingTest outcome rows disagree on the rejected class count"
        )
    return frame


def fetch_non_passing_failure_types(conn: Connection, variant: str) -> pd.DataFrame:
    """Return the ten most frequent non-passing JUnit failure types."""
    return _integer_columns(
        read_sql(conn, NON_PASSING_FAILURE_TYPE_SQL, _params(variant)), "tests"
    )


def fetch_assertion_type_rejections(conn: Connection, variant: str) -> pd.DataFrame:
    """Partition AssertionType rejections by recorded assertion name."""
    frame = _integer_columns(
        read_sql(conn, ASSERTION_TYPE_REJECTION_SQL, _params(variant)),
        "assertions",
        "rejected_assertions",
        "total_assertions",
    )
    _require_categories(
        frame, "assertion_name", ASSERTION_TYPE_NAMES, "AssertionType rejection"
    )
    return frame


def fetch_missing_value_causes(conn: Connection, variant: str) -> pd.DataFrame:
    """Partition MissingValue rejections by resolver reason code."""
    frame = _integer_columns(
        read_sql(conn, MISSING_VALUE_CAUSE_SQL, _params(variant)),
        "assertions",
        "rejected_assertions",
    )
    _require_categories(frame, "reason_code", MISSING_VALUE_CAUSES, "MissingValue")
    return frame


def fetch_parameter_type_rejections(conn: Connection, variant: str) -> pd.DataFrame:
    """Partition ParameterType rejections by the branch that rejected them."""
    frame = _integer_columns(
        read_sql(conn, PARAMETER_TYPE_REJECTION_SQL, _params(variant)),
        "assertions",
        "rejected_assertions",
    )
    _require_categories(
        frame, "category", PARAMETER_TYPE_CATEGORIES, "ParameterType rejection"
    )
    return frame


def fetch_return_type_rejections(conn: Connection, variant: str) -> pd.DataFrame:
    """Return the ten most frequent encoded types rejected by ReturnType."""
    frame = _integer_columns(
        read_sql(conn, RETURN_TYPE_REJECTION_SQL, _params(variant)),
        "assertions",
        "rejected_assertions",
    )
    missing = set(RETURN_TYPE_METRIC_NAMES) - set(frame["return_type"].astype(str))
    if missing:
        raise exclusion.ExclusionEvidenceError(
            f"required ReturnType detail rows are missing: {sorted(missing)}"
        )
    return frame


def validate_filter_partitions(
    filtering: pd.DataFrame,
    *,
    test_types: dict[str, int],
    non_passing: pd.DataFrame,
    assertion_types: pd.DataFrame,
    missing_values: pd.DataFrame,
    parameter_types: pd.DataFrame,
    return_types: pd.DataFrame,
) -> None:
    """Require every detail population to match its filter-result total."""
    details = {
        ("Test", "TestType"): sum(test_types.values()),
        ("Test", "NonPassingTest"): int(non_passing["tests"].sum()),
        ("Assertion", "AssertionType"): int(
            assertion_types["rejected_assertions"].iloc[0]
        ),
        ("Assertion", "MissingValue"): int(
            missing_values["rejected_assertions"].iloc[0]
        ),
        ("Assertion", "ParameterType"): int(
            parameter_types["rejected_assertions"].iloc[0]
        ),
        ("Assertion", "ReturnType"): int(return_types["rejected_assertions"].iloc[0]),
    }
    for (level, filter_name), detailed in details.items():
        row = filtering.loc[
            filtering["level"].eq(level) & filtering["filter"].eq(filter_name)
        ]
        if len(row) != 1:
            raise exclusion.ExclusionEvidenceError(
                f"expected one {level} {filter_name} filtering row, found {len(row)}"
            )
        rejected = int(row["reject"].iloc[0])
        if detailed != rejected:
            raise exclusion.ExclusionEvidenceError(
                f"{filter_name} detail does not partition its rejections: "
                f"detail={detailed}, rejected={rejected}"
            )


def _with_share(frame: pd.DataFrame, numerator: str, denominator: str) -> pd.DataFrame:
    return frame.assign(
        share=[
            share_value(row[numerator], row[denominator]) for _, row in frame.iterrows()
        ]
    )


def non_passing_outcome_table(frame: pd.DataFrame) -> Table:
    data = _with_share(
        frame.assign(rejected_tests=int(frame["tests"].sum())),
        "tests",
        "rejected_tests",
    )
    return Table(
        key="rq6_non_passing_outcomes",
        df=data,
        columns=[
            ColumnSpec("Recorded outcome", "outcome", kind=ValueKind.TEXT),
            ColumnSpec("Rejected tests", "tests", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Share", "share", kind=ValueKind.SHARE, align="r"),
        ],
        caption="Recorded outcomes of tests rejected by NonPassingTest.",
        label="tab:rq6-non-passing-outcomes",
        row_key="outcome",
        note=(
            "Each test appears once. When repeated executions disagree, the table "
            "uses error, failed, skipped, then passed precedence."
        ),
        provenance=capture(fetch_non_passing_outcomes, query=NON_PASSING_OUTCOME_SQL),
    )


def non_passing_failure_type_table(frame: pd.DataFrame) -> Table:
    return Table(
        key="rq6_non_passing_failure_types",
        df=frame,
        columns=[
            ColumnSpec("Result", "result", kind=ValueKind.TEXT),
            ColumnSpec("Failure type", "failure_type", kind=ValueKind.TEXT),
            ColumnSpec("Rejected tests", "tests", kind=ValueKind.COUNT, align="r"),
        ],
        caption="Most frequent failure types among NonPassingTest rejections.",
        label="tab:rq6-non-passing-failure-types",
        row_key="failure_type",
        note=(
            "Rows count distinct tests. A test can appear under more than one type "
            "when repeated executions recorded different failures."
        ),
        provenance=capture(
            fetch_non_passing_failure_types, query=NON_PASSING_FAILURE_TYPE_SQL
        ),
    )


def assertion_type_rejection_table(frame: pd.DataFrame) -> Table:
    data = _with_share(frame, "assertions", "total_assertions")
    return Table(
        key="rq6_assertion_type_rejections",
        df=data,
        columns=[
            ColumnSpec("Assertion", "assertion_name", kind=ValueKind.TEXT),
            ColumnSpec("Rejections", "assertions", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("All assertions", "share", kind=ValueKind.SHARE, align="r"),
        ],
        caption="Assertion names rejected by AssertionType.",
        label="tab:rq6-assertion-type-rejections",
        row_key="assertion_name",
        provenance=capture(
            fetch_assertion_type_rejections, query=ASSERTION_TYPE_REJECTION_SQL
        ),
    )


def missing_value_cause_table(frame: pd.DataFrame) -> Table:
    data = _with_share(frame, "assertions", "rejected_assertions")
    return Table(
        key="rq6_missing_value_causes",
        df=data,
        columns=[
            ColumnSpec("Resolver cause", "reason_code", kind=ValueKind.TEXT),
            ColumnSpec("Rejections", "assertions", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Share", "share", kind=ValueKind.SHARE, align="r"),
        ],
        caption="Recorded resolver causes for MissingValue rejections.",
        label="tab:rq6-missing-value-causes",
        row_key="reason_code",
        note=(
            "The resolver reason is the primary cause. detail_json can record "
            "additional missing fields for the same assertion."
        ),
        provenance=capture(fetch_missing_value_causes, query=MISSING_VALUE_CAUSE_SQL),
    )


def parameter_type_rejection_table(frame: pd.DataFrame) -> Table:
    data = _with_share(frame, "assertions", "rejected_assertions")
    return Table(
        key="rq6_parameter_type_rejections",
        df=data,
        columns=[
            ColumnSpec("Rejection branch", "category", kind=ValueKind.TEXT),
            ColumnSpec("Rejections", "assertions", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Share", "share", kind=ValueKind.SHARE, align="r"),
        ],
        caption="Observed branches of ParameterType rejections.",
        label="tab:rq6-parameter-type-rejections",
        row_key="category",
        provenance=capture(
            fetch_parameter_type_rejections, query=PARAMETER_TYPE_REJECTION_SQL
        ),
    )


def return_type_rejection_table(frame: pd.DataFrame) -> Table:
    data = _with_share(frame, "assertions", "rejected_assertions")
    return Table(
        key="rq6_return_type_rejections",
        df=data,
        columns=[
            ColumnSpec("Return type", "return_type", kind=ValueKind.TEXT),
            ColumnSpec("Rejections", "assertions", kind=ValueKind.COUNT, align="r"),
            ColumnSpec("Share", "share", kind=ValueKind.SHARE, align="r"),
        ],
        caption="Most frequent encoded types rejected by ReturnType.",
        label="tab:rq6-return-type-rejections",
        row_key="return_type",
        note="The table shows the ten most frequent of all recorded rejected types.",
        provenance=capture(
            fetch_return_type_rejections, query=RETURN_TYPE_REJECTION_SQL
        ),
    )
