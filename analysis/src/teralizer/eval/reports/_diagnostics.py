"""Reports explaining telemetry columns behind the JPF and MUT tables."""

from __future__ import annotations

import json
import re

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql
from teralizer.eval.model import (
    ColumnSpec,
    Table,
    ValueKind,
    decimal_value,
    share_value,
)
from teralizer.eval.provenance import capture
from teralizer.eval.reports import _funnel


JPF_EXCEPTION_DETAIL_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT td.detail_json
FROM task_diagnostic td
JOIN eligible_projects ep ON ep.id = td.project_id
WHERE td.reason_code = 'UNCAUGHT_EXCEPTION_PATH'
"""

_JPF_EXCEPTION_CATEGORIES = (
    "Application exception",
    "JPF native-peer gap",
    "JPF model/field gap",
    "Unparsed",
)
_CAUSED_BY = re.compile(r"Caused by:\s*([\w.$]+)(?::\s*([^\r\n]*))?")
_THROWN = re.compile(r"^([\w.$]+(?:Exception|Error))(?::\s*([^\r\n]*))?", re.MULTILINE)
_JPF_PEER = re.compile(r"gov\.nasa\.jpf\.vm\.JPF_[\w_]+")

MUT_CHOICE_SENSITIVITY_SQL = f"""
{_funnel.ELIGIBILITY_CTE}
SELECT DISTINCT o.assertion_id, o.candidate_details
FROM mut_resolution_observation o
JOIN eligible_projects ep ON ep.id = o.project_id
JOIN filter_result fr ON fr.assertion_id = o.assertion_id
WHERE fr.decision = 'REJECT'
  AND fr.filter_name LIKE '%ParameterTypeFilter'
  AND fr.reason_code = 'NO_GENERALIZABLE_PARAMETERS'
"""

_MUT_CHOICE_CATEGORIES = (
    "Candidate detail unavailable",
    "Choice-invariant",
    "Choice-dependent",
)


def _detail_message(detail: object) -> str:
    if isinstance(detail, dict):
        return str(detail.get("message") or detail.get("detail") or "")
    if not isinstance(detail, str):
        return ""
    try:
        decoded = json.loads(detail)
    except json.JSONDecodeError:
        return detail
    if isinstance(decoded, dict):
        return str(decoded.get("message") or decoded.get("detail") or "")
    return detail


def _classify_jpf_exception_detail(detail: object) -> str:
    """Recover a concrete JPF failure family from retained diagnostic detail."""
    message = _detail_message(detail)
    caused_by = _CAUSED_BY.findall(message)
    exception_type = caused_by[-1][0] if caused_by else ""
    if not exception_type:
        after_marker = message.split("gov.nasa.jpf.vm.NoUncaughtExceptionsProperty", 1)[
            -1
        ]
        thrown = _THROWN.search(after_marker)
        exception_type = thrown.group(1) if thrown else ""
    if not exception_type:
        return "Unparsed"
    if exception_type.endswith(("NoSuchFieldException", "NoSuchMethodException")):
        return "JPF model/field gap"
    if _JPF_PEER.search(message):
        return "JPF native-peer gap"
    if exception_type.startswith("gov.nasa.jpf"):
        return "Unparsed"
    return "Application exception"


def fetch_jpf_exception_causes(conn: Connection, variant: str) -> pd.DataFrame:
    details = read_sql(
        conn, JPF_EXCEPTION_DETAIL_SQL, params=_funnel.base_query_params(variant)
    )
    classified = details["detail_json"].map(_classify_jpf_exception_detail)
    counts = classified.value_counts()
    total = int(len(classified))
    return pd.DataFrame(
        [
            {
                "category": category,
                "count": int(counts.get(category, 0)),
                "share": (
                    share_value(counts.get(category, 0), total)
                    if total
                    else decimal_value(0, 0)
                ),
            }
            for category in _JPF_EXCEPTION_CATEGORIES
        ]
    )


def jpf_exception_table(df: pd.DataFrame) -> Table:
    return Table(
        key="rq6_jpf_exception_causes",
        df=df,
        columns=[
            ColumnSpec("Recovered cause", "category"),
            ColumnSpec("Diagnostics", "count", ValueKind.COUNT, "r"),
            ColumnSpec("Share", "share", ValueKind.SHARE, "r"),
        ],
        caption=(
            "Retrospective classification of generic JPF uncaught-exception "
            "diagnostics from retained detail."
        ),
        label="tab:jpf-exception-causes",
        note=(
            "This recovery changes cause attribution only; it does not change "
            "project eligibility or funnel outcomes."
        ),
        provenance=capture(fetch_jpf_exception_causes, query=JPF_EXCEPTION_DETAIL_SQL),
    )


def _call_has_arguments(source: str) -> bool:
    """Whether the final Java call in source passes at least one argument."""
    source = source.strip()
    if not source.endswith(")"):
        return False
    depth = 0
    for index in range(len(source) - 1, -1, -1):
        value = source[index]
        if value == ")":
            depth += 1
        elif value == "(":
            depth -= 1
            if depth == 0:
                return bool(source[index + 1 : -1].strip())
    return False


def _classify_mut_candidate_details(detail: object) -> str:
    if isinstance(detail, str):
        try:
            detail = json.loads(detail)
        except json.JSONDecodeError:
            return "Candidate detail unavailable"
    if not isinstance(detail, list) or not detail:
        return "Candidate detail unavailable"

    sources: list[str] = []
    for candidate in detail:
        if not isinstance(candidate, dict):
            return "Candidate detail unavailable"
        source = candidate.get("callSource")
        if not isinstance(source, str) or not source.strip():
            return "Candidate detail unavailable"
        sources.append(source)
    if any(_call_has_arguments(source) for source in sources):
        return "Choice-dependent"
    return "Choice-invariant"


def fetch_mut_choice_sensitivity(conn: Connection, variant: str) -> pd.DataFrame:
    details = read_sql(
        conn, MUT_CHOICE_SENSITIVITY_SQL, params=_funnel.base_query_params(variant)
    )
    classified = details["candidate_details"].map(_classify_mut_candidate_details)
    counts = classified.value_counts()
    total = int(len(classified))
    return pd.DataFrame(
        [
            {
                "category": category,
                "count": int(counts.get(category, 0)),
                "share": (
                    share_value(counts.get(category, 0), total)
                    if total
                    else decimal_value(0, 0)
                ),
            }
            for category in _MUT_CHOICE_CATEGORIES
        ]
    )


def mut_choice_table(df: pd.DataFrame) -> Table:
    return Table(
        key="rq6_mut_choice_sensitivity",
        df=df,
        columns=[
            ColumnSpec("Candidate evidence", "category"),
            ColumnSpec("Rejections", "count", ValueKind.COUNT, "r"),
            ColumnSpec("Share of all", "share", ValueKind.SHARE, "r"),
        ],
        caption=(
            "Choice sensitivity of ParameterType rejections classified from "
            "retained MUT candidate details."
        ),
        label="tab:mut-choice-sensitivity",
        note=(
            "Choice-dependent rows divided by all ParameterType rejections are "
            "a lower bound; rows without candidate detail remain unscored."
        ),
        provenance=capture(
            fetch_mut_choice_sensitivity, query=MUT_CHOICE_SENSITIVITY_SQL
        ),
    )
