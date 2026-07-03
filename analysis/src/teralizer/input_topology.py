"""Input-topology spike: classify asserted 'actual' expressions by shape.

Answers: where would generated inputs enter, and where is the oracle value
read, for every supported assertion in the corpus? The shape taxonomy sizes
the recipe increments in ``docs/plans/2026-07-02-input-topology-spike.md``
(T0 direct call, T1 inline ctor receiver, T2 expression slice, T3 statement
slice, T4 fixture, T5 environment).

Textual heuristic over Spoon-printed expression source (the DB stores no
AST); expect a few percent noise. The AST-exact version is the
``actual_shape`` column of ``mut_resolution_observation``
(``2026-07-02-mut-id-confidence-fusion``).

Run:  uv run --directory analysis python -m teralizer.input_topology
"""

from __future__ import annotations

import json
import re

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.config import db_config

_DOUBLEISH = {
    "double",
    "float",
    "int",
    "long",
    "short",
    "byte",
    "java.lang.Double",
    "java.lang.Float",
    "java.lang.Integer",
    "java.lang.Long",
}

_STR = re.compile(r'"(\\.|[^"\\])*"|\'(\\.|[^\'\\])*\'')
_LITERAL = re.compile(
    r"^(-?\d[\d_]*(\.\d+)?[dDfFlL]?|0[xX][0-9a-fA-F_]+[lL]?|true|false|null|\"\")$"
)
_IDENT = re.compile(r"^[A-Za-z_$][\w$]*$")
_QUALNAME = re.compile(r"^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)+$")
_CAST = re.compile(r"^\(\s*[A-Za-z_$][\w.$<>\[\], ]*\s*\)\s*(?=[\w(\"])")
_BINOP = re.compile(
    r" (\+|-|\*|/|%|&&|\|\||==|!=|<=|>=|<|>|\^|&|\||<<|>>|>>>|instanceof) "
)


def actual_expression(
    assertion_name: str, arguments_json: str, framework: str
) -> str | None:
    """Mirror TestAnalysis.getActualParameterIndex textually; None = not extractable."""
    try:
        args = json.loads(arguments_json)
    except (ValueError, TypeError):
        return None
    n = len(args)
    if assertion_name == "assertEquals":
        if framework == "JUNIT_4":
            if n == 4:
                i = 2
            elif n == 3:
                i = 1 if all(a["type"] in _DOUBLEISH for a in args) else 2
            elif n == 2:
                i = 1
            else:
                return None
        else:
            i = 1 if n in (2, 3, 4) else -1
    elif assertion_name in ("assertTrue", "assertFalse"):
        if framework == "JUNIT_4":
            i = 1 if n == 2 else (0 if n == 1 else -1)
        else:
            i = 0 if n in (1, 2) else -1
    else:
        return None
    if i < 0 or i >= n:
        return None
    return args[i]["value"]


def _strip_strings(e: str) -> str:
    return _STR.sub('""', e)


def _strip_outer_parens(e: str) -> str:
    while e.startswith("(") and e.endswith(")"):
        depth = 0
        k = 0
        for k, c in enumerate(e):
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    break
        if k == len(e) - 1:
            e = e[1:-1].strip()
        else:
            break
    return e


def _top_level_binop_or_ternary(e: str) -> bool:
    for m in _BINOP.finditer(e):
        head = e[: m.start()]
        if head.count("(") == head.count(")") and head.count("[") == head.count("]"):
            return True
    depth = 0
    for c in e:
        if c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
        elif c == "?" and depth == 0:
            return True
    return False


def _segments(e: str) -> list[str]:
    """Split on '.' at nesting depth 0."""
    segs: list[str] = []
    depth = 0
    cur: list[str] = []
    for c in e:
        if c in "([<":
            depth += 1
        elif c in ")]>":
            depth -= 1
        if c == "." and depth == 0:
            segs.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    segs.append("".join(cur))
    return segs


def classify(expr: str | None) -> str:
    """Shape of the asserted actual expression (see module docstring taxonomy)."""
    if expr is None:
        return "UNEXTRACTABLE"
    e = _strip_outer_parens(_strip_strings(expr.strip()))
    if "->" in e or "::" in e:
        return "LAMBDA_OR_METHODREF"
    if _LITERAL.match(e):
        return "LITERAL"
    if _IDENT.match(e):
        return "VARIABLE"
    m = _CAST.match(e)
    if m:
        return classify(e[m.end() :])
    if _top_level_binop_or_ternary(e) or e.startswith(("!", "~")):
        return "OPERATOR_COMPOSITE"
    if _QUALNAME.match(e):
        return "FIELD_OR_QUALIFIED_NAME"
    if e.endswith("]"):
        return "ARRAY_INDEX"
    segs = _segments(e)
    is_ctor = segs[0].lstrip().startswith("new ")
    n_calls = sum(1 for s in segs if "(" in s) - (
        1 if is_ctor and "(" in segs[0] else 0
    )
    if is_ctor:
        if n_calls == 0:
            return "CTOR_ONLY"
        if n_calls == 1:
            zero_arg = bool(re.search(r"\(\s*\)$", segs[-1]))
            return "CTOR_RECEIVER_CALL_0ARG" if zero_arg else "CTOR_RECEIVER_CALL_NARG"
        return "CHAINED_CALLS"
    if n_calls == 0:
        return "FIELD_OR_QUALIFIED_NAME"
    if n_calls == 1:
        return "SINGLE_CALL"
    zero_arg = bool(re.search(r"\(\s*\)$", segs[-1]))
    return "CHAINED_CALLS_END0ARG" if zero_arg else "CHAINED_CALLS_ENDNARG"


def has_input_sites(expr: str | None) -> bool:
    """At least one non-empty argument list anywhere in the expression."""
    if expr is None:
        return False
    return bool(re.search(r"\((?!\s*\))", _strip_strings(expr)))


def load_supported_assertions(conn: Connection) -> pd.DataFrame:
    """Supported-shape assertions with first-reject filter and tested_* state."""
    sql = text(
        """
        WITH first_reject AS (
            SELECT DISTINCT ON (fr.assertion_id) fr.assertion_id, fr.filter_name
            FROM filter_result fr
            WHERE fr.decision = 'REJECT' AND fr.assertion_id IS NOT NULL
            ORDER BY fr.assertion_id, fr.id
        )
        SELECT a.id, a.project_id, a.assertion_name, a.assertion_arguments,
               a.tested_method_name, a.tested_method_parameters,
               p.test_framework,
               fr.filter_name AS first_reject_filter
        FROM assertion a
        JOIN project p ON p.id = a.project_id
        LEFT JOIN first_reject fr ON fr.assertion_id = a.id
        WHERE a.assertion_name IN ('assertEquals', 'assertTrue', 'assertFalse')
        """
    )
    return pd.read_sql(sql, conn)


def shape_cross_tab(df: pd.DataFrame) -> pd.DataFrame:
    """Shape x first-reject-filter cross-tab, TOTAL-sorted."""
    df = df.copy()
    df["actual"] = [
        actual_expression(n, a, f)
        for n, a, f in zip(df.assertion_name, df.assertion_arguments, df.test_framework)
    ]
    df["shape"] = df["actual"].map(classify)
    df["fr"] = df.first_reject_filter.str.replace(
        "teralizer.processing.filter.", "", regex=False
    ).fillna("NONE")
    ct = pd.crosstab(df["shape"], df["fr"])
    ct["TOTAL"] = ct.sum(axis=1)
    return ct.sort_values("TOTAL", ascending=False)


def main() -> None:
    engine = db_config.get_test_engine(validate=False)
    with engine.connect() as conn:
        df = load_supported_assertions(conn)
    ct = shape_cross_tab(df)
    print(f"== Actual-expression shapes x first reject ({len(df)} assertions) ==")
    print(ct.to_string())


if __name__ == "__main__":
    main()
