"""Check the paper's RQ claims against the outputs of a registered report run.

`replication/claims.toml` lists every claim of the RQ0 to RQ6 answers with the
reports that carry its evidence and the checks that bind each number of the
claim to their outputs. The run manifest of a report run names the file of each
output, so the checks read the regenerated tables and macros, not the reference
copies.

Evidence classes:

- direct observation: a recorded measurement, reported as recorded.
- deterministic transformation: computed from recorded observations by code
  that yields the same result for the same corpus.
- analytic derivation: an interpretation that the checks reduce to structural
  conditions on deterministic outputs, such as membership in a Pareto frontier.
- external published reference: a value published by others and carried as
  frozen input, such as the JARVIS paper's Table 2.
- stochastic collection: a value that new collection reproduces only within
  the invariant the claim's `collection` text states.

Check kinds:

- macro: a report macro renders `value`.
- cell: the one row that `where` selects holds `value` in `column`.
- extent: the rows that `where` selects range from `min` to `max` in `column`.
  A list of columns sums them per row. `divide_by` names a cell to divide by.
- sum: the rows that `where` selects sum to `value` in `column`.
- count: `value` rows that `where` selects satisfy `condition`.
- wins: in `value` of the `pairs` of row keys, the first row's `column` exceeds
  the second's.
- reduction: the relative reduction from the first to the second row of each
  pair in `column` ranges from `min` to `max` percent.
- top: the row with the largest `by` has a value in `present`.

`where` maps columns to a glob or a list of globs. Row keys come from the
`row_key` column, or from the `key` columns joined with "/". Numbers compare
after rounding half up to the decimals of the expected value, after `scale`.
"""

from __future__ import annotations

import csv
import fnmatch
import json
import re
import tomllib
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation
from pathlib import Path
from typing import Any

CLAIMS_FORMAT = ("teralizer-claims", 1)
DEFAULT_CLAIMS = "replication/claims.toml"
RUN_MANIFEST = "reports/provenance.json"
EVIDENCE_CLASSES = (
    "direct observation",
    "deterministic transformation",
    "analytic derivation",
    "external published reference",
    "stochastic collection",
)
_MACRO = re.compile(r"\\newcommand\{\\(\w+)\}\{(.*)\}")


class ClaimsError(ValueError):
    """The claims declaration or a report run cannot be read."""


@dataclass(frozen=True)
class Claim:
    id: str
    rq: str
    statement: str
    evidence: str
    reports: tuple[str, ...]
    checks: tuple[Mapping[str, Any], ...]
    collection: str | None = None


@dataclass
class Outcome:
    claim: Claim
    failures: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not self.failures


def load(path: Path) -> list[Claim]:
    document = tomllib.loads(path.read_text())
    if (document.get("format"), document.get("format_version")) != CLAIMS_FORMAT:
        raise ClaimsError(f"{path} is not a {CLAIMS_FORMAT[0]} version 1 document")
    claims = []
    for entry in document.get("claim", []):
        claim = Claim(
            id=entry["id"],
            rq=entry["rq"],
            statement=entry["statement"],
            evidence=entry["evidence"],
            reports=tuple(entry["reports"]),
            checks=tuple(entry.get("check", [])),
            collection=entry.get("collection"),
        )
        if claim.evidence not in EVIDENCE_CLASSES:
            raise ClaimsError(f"{claim.id}: unknown evidence class {claim.evidence}")
        if not claim.checks:
            raise ClaimsError(f"{claim.id}: a claim needs at least one check")
        claims.append(claim)
    ids = [claim.id for claim in claims]
    if len(ids) != len(set(ids)):
        raise ClaimsError(f"{path} declares a claim id twice")
    return claims


class Run:
    """The outputs of one report run, located through its run manifest."""

    def __init__(self, analysis: Path) -> None:
        self._analysis = analysis
        manifest = json.loads((analysis / RUN_MANIFEST).read_text())
        self._paths = {
            (report, str(item["target"]), str(item["key"])): str(item["path"])
            for report, record in manifest.items()
            for item in record["artifacts"]
        }
        self._tables: dict[tuple[str, str], list[dict[str, str]]] = {}

    def table(self, report: str, key: str) -> list[dict[str, str]]:
        if (report, key) not in self._tables:
            path = self._paths.get((report, "csv", key))
            if path is None:
                raise ClaimsError(f"report {report} emitted no table {key}")
            with (self._analysis / path).open(newline="") as stream:
                self._tables[(report, key)] = list(csv.DictReader(stream))
        return self._tables[(report, key)]

    def macro(self, report: str, name: str) -> str:
        path = self._paths.get((report, "latex", f"macros/{report}"))
        if path is None:
            raise ClaimsError(f"report {report} emitted no macros")
        for line in (self._analysis / path).read_text().splitlines():
            match = _MACRO.fullmatch(line.strip())
            if match and match.group(1) == name:
                return match.group(2).replace("\\%", "%").replace("\\_", "_")
        raise ClaimsError(f"report {report} defines no macro {name}")


def _matches(value: str, patterns: str | Sequence[str]) -> bool:
    if isinstance(patterns, str):
        patterns = [patterns]
    return any(fnmatch.fnmatchcase(value, pattern) for pattern in patterns)


def _select(
    rows: list[dict[str, str]], where: Mapping[str, Any]
) -> list[dict[str, str]]:
    return [
        row
        for row in rows
        if all(
            _matches(row.get(column, ""), pattern) for column, pattern in where.items()
        )
    ]


def _number(text: str) -> Decimal:
    try:
        return Decimal(text)
    except InvalidOperation as error:
        raise ClaimsError(f"{text!r} is not a number") from error


def _row_value(row: Mapping[str, str], column: str | Sequence[str]) -> Decimal:
    columns = [column] if isinstance(column, str) else list(column)
    return sum((_number(row[name]) for name in columns), Decimal(0))


def _rounded(value: Decimal, expected: str) -> Decimal:
    places = Decimal(1).scaleb(-len(expected.split(".")[1]) if "." in expected else 0)
    return value.quantize(places, rounding=ROUND_HALF_UP)


def _key(row: Mapping[str, str], key: Sequence[str] | None) -> str:
    if key is None:
        return row["row_key"]
    return "/".join(row[column] for column in key)


def _keyed(run: Run, check: Mapping[str, Any]) -> dict[str, dict[str, str]]:
    rows = run.table(check["report"], check["table"])
    return {_key(row, check.get("key")): row for row in rows}


def _cell(run: Run, reference: Mapping[str, Any]) -> Decimal:
    rows = _select(
        run.table(reference["report"], reference["table"]), reference["where"]
    )
    if len(rows) != 1:
        raise ClaimsError(
            f"{reference['table']}: {reference['where']} selects {len(rows)} rows, not one"
        )
    return _row_value(rows[0], reference["column"])


def _scaled(run: Run, check: Mapping[str, Any], value: Decimal) -> Decimal:
    if "divide_by" in check:
        value = value / _cell(run, check["divide_by"])
    return value * Decimal(check.get("scale", 1))


def _compare(label: str, actual: Decimal, expected: str) -> list[str]:
    shown = _rounded(actual, expected)
    if shown != _number(expected):
        return [f"{label} is {shown}, the claim states {expected}"]
    return []


def _condition(row: Mapping[str, str], condition: Mapping[str, str]) -> bool:
    value = row.get(condition["column"], "")
    op = condition["op"]
    if op == "missing":
        return value == ""
    if op == "present":
        return value != ""
    if value == "":
        return False
    other = row[condition["other"]] if "other" in condition else condition["value"]
    if other == "":
        return False
    left, right = _number(value), _number(other)
    return {">": left > right, "<": left < right, "=": left == right}[op]


def check(run: Run, spec: Mapping[str, Any]) -> list[str]:
    """Failures of one check; an empty list means it passed."""
    kind = spec["kind"]
    where = spec.get("where", {})
    label = f"{spec['report']}/{spec.get('table', spec.get('macro'))}"
    if kind == "macro":
        actual = run.macro(spec["report"], spec["macro"])
        if actual != spec["value"]:
            return [f"{label} is {actual}, the claim states {spec['value']}"]
        return []
    if kind == "cell":
        actual = _scaled(run, spec, _cell(run, spec))
        return _compare(f"{label} {spec['column']}", actual, spec["value"])
    if kind in ("extent", "sum"):
        rows = _select(run.table(spec["report"], spec["table"]), where)
        if not rows:
            return [f"{label}: {where} selects no rows"]
        values = [_scaled(run, spec, _row_value(row, spec["column"])) for row in rows]
        if kind == "sum":
            return _compare(
                f"{label} sum of {spec['column']}",
                sum(values, Decimal(0)),
                spec["value"],
            )
        return _compare(
            f"{label} minimum of {spec['column']}", min(values), spec["min"]
        ) + (_compare(f"{label} maximum of {spec['column']}", max(values), spec["max"]))
    if kind == "count":
        rows = _select(run.table(spec["report"], spec["table"]), where)
        if "condition" in spec:
            rows = [row for row in rows if _condition(row, spec["condition"])]
        if len(rows) != spec["value"]:
            return [
                f"{label}: {len(rows)} rows qualify, the claim states {spec['value']}"
            ]
        return []
    if kind in ("wins", "reduction"):
        rows = _keyed(run, spec)
        missing = sorted({key for pair in spec["pairs"] for key in pair} - set(rows))
        if missing:
            return [f"{label}: no rows {missing}"]
        values = [
            (
                _row_value(rows[first], spec["column"]),
                _row_value(rows[second], spec["column"]),
            )
            for first, second in spec["pairs"]
        ]
        if kind == "wins":
            wins = sum(1 for first, second in values if first > second)
            if wins != spec["value"]:
                return [
                    f"{label}: the first row wins {wins} of {len(values)} pairs, "
                    f"the claim states {spec['value']}"
                ]
            return []
        reductions = [(1 - after / before) * 100 for before, after in values]
        return _compare(f"{label} smallest reduction", min(reductions), spec["min"]) + (
            _compare(f"{label} largest reduction", max(reductions), spec["max"])
        )
    if kind == "top":
        rows = run.table(spec["report"], spec["table"])
        top = max(rows, key=lambda row: _number(row[spec["by"]]))
        if top[spec["present"]] == "":
            return [
                f"{label}: the row with the largest {spec['by']} has no {spec['present']}"
            ]
        return []
    raise ClaimsError(f"unknown check kind {kind}")


def verify(claims: Sequence[Claim], run: Run) -> list[Outcome]:
    outcomes = []
    for claim in claims:
        outcome = Outcome(claim)
        for spec in claim.checks:
            try:
                outcome.failures.extend(check(run, spec))
            except ClaimsError as error:
                outcome.failures.append(str(error))
        outcomes.append(outcome)
    return outcomes


def summary(outcomes: Sequence[Outcome]) -> dict[str, Any]:
    return {
        "passed": all(outcome.passed for outcome in outcomes),
        "claims": [
            {
                "id": outcome.claim.id,
                "rq": outcome.claim.rq,
                "status": "passed" if outcome.passed else "failed",
                "failures": outcome.failures,
            }
            for outcome in outcomes
        ],
    }


DEFAULT_MATRIX = "replication/CLAIMS.md"

_MATRIX_INTRO = """\
# Claims and their evidence

Generated from `replication/claims.toml` by
`uv run --directory analysis python -m teralizer.release claims-matrix`. Edit the
claims there.

Each row is a claim of the RQ0 to RQ6 answers of the paper, or an observation an
answer rests on. The command of a row regenerates its outputs from the published
corpora and checks it: every number must equal the expected value after rounding
half up to the decimals shown, with no tolerance. `output/evidence.json` records
the result of every claim.

Mode "inspection" marks a value that others published and the release carries as
frozen input. Reproduction reads it and regenerates everything computed from it.
"New collection" states what a rerun of the collection can establish, because
collected values depend on jqwik's random inputs and on the host. [RQ0.md](RQ0.md)
describes the inputs of the JARVIS comparison and what each command establishes.

The answers also explain some results, for example by the design of a generation
strategy. Those explanations come from the design and from inspecting generated
tests, and the artifact does not check them.

"""


def _selection(spec: Mapping[str, Any]) -> str:
    parts = [
        f"{column}={' or '.join(pattern) if isinstance(pattern, list) else pattern}"
        for column, pattern in spec.get("where", {}).items()
    ]
    condition = spec.get("condition")
    if condition is not None:
        other = condition.get("other", condition.get("value", ""))
        parts.append(f"{condition['column']} {condition['op']} {other}".rstrip())
    return f" where {', '.join(parts)}" if parts else ""


def _column(spec: Mapping[str, Any]) -> str:
    column = spec["column"]
    text = "+".join(column) if isinstance(column, list) else column
    if "divide_by" in spec:
        text += f" / {spec['divide_by']['column']}"
    if "scale" in spec:
        text += f" * {spec['scale']}"
    return text


def describe(spec: Mapping[str, Any]) -> str:
    """One check as the expected value or invariant it states."""
    kind = spec["kind"]
    if kind == "macro":
        return f"`\\{spec['macro']}` = {spec['value']}"
    output = f"`{spec['table']}`"
    where = _selection(spec)
    if kind == "cell":
        return f"{output} {_column(spec)}{where} = {spec['value']}"
    if kind == "extent":
        return f"{output} {_column(spec)}{where} from {spec['min']} to {spec['max']}"
    if kind == "sum":
        return f"{output} sum of {_column(spec)}{where} = {spec['value']}"
    if kind == "count":
        return f"{output} {spec['value']} rows{where}"
    if kind == "wins":
        return (
            f"{output} {_column(spec)}: first row larger in {spec['value']} "
            f"of {len(spec['pairs'])} pairs"
        )
    if kind == "reduction":
        return (
            f"{output} {_column(spec)}: reduction from {spec['min']} "
            f"to {spec['max']}% per pair"
        )
    if kind == "top":
        return f"{output} row with largest {spec['by']} has {spec['present']}"
    raise ClaimsError(f"unknown check kind {kind}")


def _outputs(claim: Claim) -> list[str]:
    outputs: set[str] = set()
    for spec in claim.checks:
        if spec["kind"] == "macro":
            outputs.add(f"`build/macros/{spec['report']}.tex`")
        else:
            outputs.add(f"`build/{spec['report']}/{spec['table']}.csv`")
    return sorted(outputs)


def matrix(claims: Sequence[Claim]) -> str:
    """The claims-to-evidence matrix in Markdown, with each report's inputs."""
    import teralizer.eval.reports  # noqa: F401  (registers every report)
    from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec
    from teralizer.eval.registry import get

    lines = [
        "| Claim | RQ | Statement | Evidence | Mode | Inputs | Outputs "
        "| Expected | Command | New collection |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for claim in claims:
        corpora: set[str] = set()
        files: set[str] = set()
        for report in claim.reports:
            for spec in get(report).inputs:
                if isinstance(spec, CorpusInputSpec):
                    corpora.add(f"corpus {spec.corpus_id}")
                elif isinstance(spec, FileInputSpec):
                    files.add(f"`{spec.path}`")
        # RQ0 alone needs only the two JARVIS corpora, every other report all four.
        command = (
            "`./artifact reproduce-results rq0`"
            if set(claim.reports) == {"rq0"}
            else "`./artifact reproduce-results`"
        )
        mode = (
            "inspection"
            if claim.evidence == "external published reference"
            else "regeneration"
        )
        lines.append(
            f"| `{claim.id}` | {claim.rq} | {claim.statement} | {claim.evidence} | "
            f"{mode} | {'<br>'.join(sorted(corpora) + sorted(files))} | "
            f"{'<br>'.join(_outputs(claim))} | "
            f"{'<br>'.join(describe(spec) for spec in claim.checks)} | {command} | "
            f"{claim.collection or 'Checked by report reproduction only.'} |"
        )
    return _MATRIX_INTRO + "\n".join(lines) + "\n"
