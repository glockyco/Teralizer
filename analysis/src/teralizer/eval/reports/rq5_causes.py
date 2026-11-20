"""RQ5 controlled-dataset exclusion causes report."""

from __future__ import annotations

from dataclasses import replace

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import Required, read_sql
from teralizer.eval.inputs import CorpusInputSpec, ReportContext
from teralizer.eval.model import Prose, RQReport, Section
from teralizer.eval.provenance import capture
from teralizer.eval.registry import ReportSpec, register
from teralizer.eval.entities import variant_ref
from teralizer.eval.model import ColumnSpec, Table, ValueKind, share_value
from teralizer.eval.reports._causes_common import (
    build_breakdown_table,
    build_filtering_table,
    variant_sort_key,
)

REQUIRES: tuple[Required, ...] = (
    Required("project", "table", ("id", "use_test_generalization")),
    Required("test", "table", ("id", "project_id", "is_included", "exclusion_info")),
    Required("assertion", "table", ("id", "test_id", "is_included", "exclusion_info")),
    Required(
        "generalization",
        "table",
        ("id", "variant", "is_included", "exclusion_info", "project_id"),
    ),
    Required(
        "filter_result",
        "table",
        (
            "project_id",
            "test_id",
            "assertion_id",
            "generalization_id",
            "filter_name",
            "decision",
        ),
    ),
    Required(
        "mv_exclusions_all",
        "view",
        ("level", "is_included", "excluded_by", "count"),
    ),
)

FILTERING_SQL = r"""
WITH
    base_data AS (
        SELECT
            CASE
                WHEN fr.test_id IS NOT NULL THEN 'Test'
                WHEN fr.assertion_id IS NOT NULL THEN 'Assertion'
                WHEN fr.generalization_id IS NOT NULL THEN 'Generalization'
            END AS level,
            substring(fr.filter_name from 'filter\.(\w+)Filter$') AS filter_name,
            fr.decision,
            count(*) AS count
        FROM filter_result fr
        JOIN project p ON fr.project_id = p.id
        LEFT JOIN generalization g ON fr.generalization_id = g.id
        WHERE p.use_test_generalization
        GROUP BY
            g.variant,
            fr.filter_name,
            CASE
                WHEN fr.test_id IS NOT NULL THEN 'Test'
                WHEN fr.assertion_id IS NOT NULL THEN 'Assertion'
                WHEN fr.generalization_id IS NOT NULL THEN 'Generalization'
            END,
            fr.decision
    ),
    pivoted AS (
        SELECT
            level,
            filter_name,
            sum(count)::bigint AS total,
            sum(CASE WHEN decision = 'ACCEPT' THEN count ELSE 0 END)::bigint AS accept,
            sum(CASE WHEN decision = 'DEFER' THEN count ELSE 0 END)::bigint AS defer,
            sum(CASE WHEN decision = 'REJECT' THEN count ELSE 0 END)::bigint AS reject
        FROM base_data
        GROUP BY level, filter_name
    )
SELECT
    level,
    CASE
        WHEN filter_name = 'UnsupportedAssertion' THEN 'AssertionType'
        ELSE filter_name
    END AS filter,
    total,
    accept,
    defer,
    reject
FROM pivoted
WHERE reject > 0
  AND level IN ('Test', 'Assertion')
ORDER BY
    CASE level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    filter
"""

BREAKDOWN_SQL = """
WITH
    test_counts AS (
        SELECT
            'All' AS strategy,
            'Test' AS level,
            CASE
                WHEN t.is_included THEN 'included'
                WHEN t.exclusion_info LIKE '%%TestFilteringTask%%'
                    AND EXISTS (
                        SELECT 1
                        FROM filter_result fr
                        WHERE fr.test_id = t.id
                          AND fr.decision = 'REJECT'
                    ) THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM test t
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY bucket
    ),
    assertion_counts AS (
        SELECT
            'All' AS strategy,
            'Assertion' AS level,
            CASE
                WHEN a.is_included THEN 'included'
                WHEN a.exclusion_info LIKE '%%TestFilteringTask%%'
                    AND EXISTS (
                        SELECT 1
                        FROM filter_result fr
                        WHERE fr.assertion_id = a.id
                          AND fr.decision = 'REJECT'
                    ) THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM assertion a
        JOIN test t ON a.test_id = t.id
        JOIN project p ON t.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY bucket
    ),
    generalization_counts AS (
        SELECT
            g.variant AS strategy,
            'Generalization' AS level,
            CASE
                WHEN g.is_included THEN 'included'
                WHEN g.exclusion_info LIKE '%%TestFilteringTask%%' THEN 'filtering'
                ELSE 'failures'
            END AS bucket,
            count(*) AS item_count
        FROM generalization g
        JOIN project p ON g.project_id = p.id
        WHERE p.use_test_generalization
        GROUP BY g.variant, bucket
    ),
    combined AS (
        SELECT strategy, level, bucket, item_count FROM test_counts
        UNION ALL
        SELECT strategy, level, bucket, item_count FROM assertion_counts
        UNION ALL
        SELECT strategy, level, bucket, item_count FROM generalization_counts
    ),
    report_rows AS (
        SELECT 'All' AS strategy, 'Test' AS level
        UNION ALL
        SELECT 'All' AS strategy, 'Assertion' AS level
        UNION ALL
        SELECT DISTINCT g.variant AS strategy, 'Generalization' AS level
        FROM generalization g
        JOIN project p ON g.project_id = p.id
        WHERE p.use_test_generalization
    )
SELECT
    report_rows.strategy,
    report_rows.level,
    coalesce(sum(item_count), 0)::bigint AS total,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'included'), 0)::bigint AS included,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'filtering'), 0)::bigint AS filtering,
    coalesce(sum(item_count) FILTER (WHERE bucket = 'failures'), 0)::bigint AS failures
FROM report_rows
LEFT JOIN combined
    ON combined.strategy = report_rows.strategy
   AND combined.level = report_rows.level
GROUP BY report_rows.strategy, report_rows.level
ORDER BY
    CASE report_rows.level
        WHEN 'Test' THEN 1
        WHEN 'Assertion' THEN 2
        WHEN 'Generalization' THEN 3
    END,
    report_rows.strategy
"""

# The controlled corpus predates the typed extraction tokens, so its failure
# messages carry the English phrases of that pipeline version. Each cause
# matches both forms, so the classification is the same for old and new runs.
ASSERTION_FAILURE_SQL = r"""
SELECT
    CASE
        WHEN info LIKE '%PATH_CONDITION_TOO_LARGE%' OR info LIKE '%PC size limit exceeded%'
            THEN 'limit|Path condition size limit'
        WHEN info LIKE '%SEARCH_DEPTH_LIMIT%' OR info LIKE '%Depth limit of % exceeded%'
            THEN 'limit|Search depth limit'
        WHEN info LIKE '%EXECUTION_TIMEOUT%' OR info LIKE '%Execution timeout exceeded%'
            THEN 'limit|Execution timeout'
        WHEN info LIKE '%java.lang.OutOfMemoryError%'
            THEN 'limit|Out of memory'
        WHEN info LIKE '%TestGeneralizationListener.writeSpecificationFiles%'
            THEN 'teralizer|NullPointerException while writing the specification'
        WHEN info LIKE '%Failed to collect input/output specification for unknown reason%'
            THEN 'teralizer|Specification not collected, no recorded reason'
        WHEN info LIKE '%java.lang.ArithmeticException: !!!div by 0%'
            THEN 'spf|ArithmeticException (division by zero)'
        WHEN info LIKE '%java.lang.NoSuchMethodException!!%'
            THEN 'spf|NoSuchMethodException'
        ELSE 'spf|Other SPF/JPF exceptions'
    END AS cause,
    count(*) AS assertions
FROM task
WHERE stage = 'EXECUTE_JPF' AND status = 'FAILED'
GROUP BY 1
"""

ASSERTION_TOTAL_SQL = "SELECT count(*) AS assertions FROM assertion"

_CAUSE_GROUPS = {
    "spf": (0, "SPF exception"),
    "limit": (1, "Exceeded analysis limit"),
    "teralizer": (2, "Teralizer error"),
}

GENERALIZATION_FAILURE_SQL = r"""
WITH
    totals AS (
        SELECT variant, count(*) AS generalizations
        FROM generalization
        GROUP BY variant
    ),
    failures AS (
        SELECT variant, failure_type, count(*) AS failures
        FROM junit_test_report
        WHERE generalization_id IS NOT NULL AND failure_type IS NOT NULL
        GROUP BY variant, failure_type
    )
SELECT t.variant, t.generalizations, f.failure_type, coalesce(f.failures, 0) AS failures
FROM totals t
LEFT JOIN failures f ON f.variant = t.variant
"""

_FAILURE_TYPES = (
    (
        "net.jqwik.api.TooManyFilterMissesException",
        "too_many_filter_misses",
        "TooManyFilterMisses",
    ),
    ("org.opentest4j.AssertionFailedError", "assertion_failed", "AssertionFailedError"),
    ("java.lang.ArithmeticException", "arithmetic", "ArithmeticException"),
    ("java.lang.NumberFormatException", "number_format", "NumberFormatException"),
)


def _fetch_assertion_failures(conn: Connection) -> pd.DataFrame:
    """Assertion-level SPF failures by cause, with shares of failures and assertions."""
    df = read_sql(conn, ASSERTION_FAILURE_SQL)
    assertions = int(read_sql(conn, ASSERTION_TOTAL_SQL)["assertions"].iloc[0])
    failures = int(df["assertions"].sum())
    parts = df["cause"].str.split("|", n=1, expand=True)
    out = pd.DataFrame(
        {
            "group": parts[0].map(lambda key: _CAUSE_GROUPS[key][1]),
            "cause": parts[1],
            "assertions": df["assertions"].astype(int),
            "_order": parts[0].map(lambda key: _CAUSE_GROUPS[key][0]),
        }
    )
    out = out.sort_values(["_order", "assertions"], ascending=[True, False])
    out.loc[:, "of_failures"] = out["assertions"].map(
        lambda count: share_value(count, failures)
    )
    out.loc[:, "of_assertions"] = out["assertions"].map(
        lambda count: share_value(count, assertions)
    )
    out.loc[:, "row_key"] = out["group"] + ":" + out["cause"]
    return out.drop(columns="_order").reset_index(drop=True)


def _fetch_generalization_failures(conn: Connection) -> pd.DataFrame:
    """Failing generalized tests by failure type and variant."""
    df = read_sql(conn, GENERALIZATION_FAILURE_SQL)
    df = df[df["variant"].str.match(r"^(BASELINE|NAIVE_\d+_TRIES|IMPROVED_\d+_TRIES)$")]
    rows = []
    for variant, group in df.groupby("variant"):
        total = int(group["generalizations"].iloc[0])
        row: dict[str, object] = {"variant": variant, "generalizations": total}
        counts = {
            failure_type: int(count)
            for failure_type, count in zip(
                group["failure_type"], group["failures"], strict=True
            )
            if isinstance(failure_type, str)
        }
        for failure_type, column, _ in _FAILURE_TYPES:
            count = counts.pop(failure_type, 0)
            row[column] = count
            row[f"{column}_pct"] = share_value(count, total)
        if counts:
            raise ValueError(f"unclassified generalized-test failures: {counts}")
        rows.append(row)
    out = pd.DataFrame(rows)
    out.loc[:, "_order"] = out["variant"].map(variant_sort_key)
    out = out.sort_values("_order").drop(columns="_order").reset_index(drop=True)
    out.loc[:, "row_key"] = out["variant"]
    out.loc[:, "variant"] = out["variant"].map(variant_ref)
    return out


def _assertion_failure_table(conn: Connection) -> Table:
    return Table(
        key="tab-exclusions-spf-causes",
        df=_fetch_assertion_failures(conn),
        columns=[
            ColumnSpec("Group", "group"),
            ColumnSpec("Cause", "cause"),
            ColumnSpec(
                "Assertions",
                "assertions",
                kind=ValueKind.COUNT,
                align="r",
                share_source="of_failures",
            ),
            ColumnSpec(
                "Share of assertions", "of_assertions", kind=ValueKind.SHARE, align="r"
            ),
        ],
        caption=(
            "Causes of assertion-level failures during specification extraction in the "
            "{entity.dataset.commons} and {entity.dataset.eqbench_es} projects, with "
            "shares of all failures and of all assertions."
        ),
        group_by="group",
        row_key="row_key",
        provenance=capture(_fetch_assertion_failures, query=ASSERTION_FAILURE_SQL),
    )


def _generalization_failure_table(conn: Connection) -> Table:
    return Table(
        key="tab-exclusions-test-failures",
        df=_fetch_generalization_failures(conn),
        columns=[
            ColumnSpec("Strategy", "variant", kind=ValueKind.ENTITY),
            ColumnSpec(
                "Generalized", "generalizations", kind=ValueKind.COUNT, align="r"
            ),
            *[
                ColumnSpec(
                    header,
                    column,
                    kind=ValueKind.COUNT,
                    align="r",
                    group_header="Failing generalized tests",
                    share_source=f"{column}_pct",
                )
                for _, column, header in _FAILURE_TYPES
            ],
        ],
        caption=(
            "Generalized tests that fail on the original program, by failure type and "
            "strategy, in the {entity.dataset.commons} and {entity.dataset.eqbench_es} "
            "projects, with shares of all generalized tests of the strategy."
        ),
        row_key="row_key",
        provenance=capture(
            _fetch_generalization_failures, query=GENERALIZATION_FAILURE_SQL
        ),
        full_width=True,
    )


def _fetch_filtering(conn: Connection) -> pd.DataFrame:
    """Return filter rejection counts normalized for the shared table builder."""
    df = read_sql(conn, FILTERING_SQL)
    for column in ("total", "accept", "defer", "reject"):
        df.isetitem(df.columns.get_loc(column), df[column].astype(int))
    return pd.DataFrame(
        df, columns=["level", "filter", "total", "accept", "defer", "reject"]
    )


def _fetch_breakdown(conn: Connection) -> pd.DataFrame:
    """Return inclusion, filtering, and failure counts by level and strategy."""
    df = read_sql(conn, BREAKDOWN_SQL)
    for column in ("total", "included", "filtering", "failures"):
        df.isetitem(df.columns.get_loc(column), df[column].astype(int))
    return pd.DataFrame(
        df, columns=["strategy", "level", "total", "included", "filtering", "failures"]
    )


def build(context: ReportContext) -> RQReport:
    conn = context.corpus("controlled")
    filtering = build_filtering_table(
        _fetch_filtering(conn),
        key="tab-exclusions-filtering",
        caption=(
            "Filtering results for tests and assertions in the "
            "{entity.dataset.commons} and {entity.dataset.eqbench_es} projects."
        ),
        full_width=True,
    )
    filtering = replace(
        filtering,
        df=filtering.df.assign(
            row_key=filtering.df["level"] + ":" + filtering.df["filter"]
        ),
        row_key="row_key",
        provenance=capture(_fetch_filtering, query=FILTERING_SQL),
    )

    breakdown = build_breakdown_table(
        _fetch_breakdown(conn),
        key="tab-exclusions-breakdown",
        caption=(
            "Exclusion results for tests, assertions, and generalizations in the "
            "{entity.dataset.commons} and {entity.dataset.eqbench_es} projects."
        ),
        full_width=True,
        include_strategy=True,
    )
    breakdown = replace(
        breakdown, provenance=capture(_fetch_breakdown, query=BREAKDOWN_SQL)
    )

    section = Section(
        title="Exclusion breakdown",
        blocks=[
            Prose(
                "Controlled-dataset exclusions separate successful inclusions from "
                "proactive filter rejections and task failures."
            ),
            breakdown,
            filtering,
            _assertion_failure_table(conn),
            _generalization_failure_table(conn),
        ],
    )
    return RQReport(
        rq="rq5",
        title="RQ5 - Causes of Unsuccessful Generalization (Controlled)",
        sections=[section],
    )


register(
    "rq5",
    ReportSpec(build, (CorpusInputSpec("controlled", "controlled", REQUIRES),)),
)
