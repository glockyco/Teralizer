"""JARVIS Table-2 PVC/IC scoreboard helpers.

PVC is computed from the jqwik value logs generated beside each generalized test.
Each log row is one executed property trial; each tab-separated cell is
``parameter=value``. The score is the sum of distinct values per MUT parameter.

IC uses the available JaCoCo class-level rows for generated variants. The current
Teralizer schema does not persist per-test JaCoCo rows, so this module keeps the
coverage query grouped by project and variant. A later collector can feed the same
shape with a test/generalization key when exact per-test IC is required.
"""

from __future__ import annotations

import base64
from dataclasses import dataclass
import json
from pathlib import Path
from typing import Iterable, cast

import pandas as pd

from teralizer.report_basis import resolve_repo_relative_path

GENERATED_JUNIT_STAGE = "COLLECT_JUNIT_REPORTS_GENERALIZED"
GENERATED_JACOCO_STAGE = "COLLECT_JACOCO_DATA_GENERALIZED"
LIMITED_DIAGNOSTIC_KIND = "LIMITED_TOO_MANY_FILTER_MISSES"

PRECONDITION_REJECTION_FAILURE_TYPES = frozenset(
    {"net.jqwik.api.TooManyFilterMissesException"}
)


@dataclass(frozen=True)
class ProbeSpec:
    """A fixture probe and the method-under-test it must resolve to."""

    generated_method_name: str
    tested_class: str
    tested_method: str


@dataclass(frozen=True)
class JarvisRow:
    """One JARVIS paper Table-2 row: the original Java unit test (CUT), the
    JARVIS-generated Scala property test (PBT), and the Teralizer fixture probes
    that target the same method(s)."""

    table_row: str
    parameter_space: str
    cut_ic: int
    cut_pvc: int
    pbt_ic: int
    pbt_pvc: int
    probes: tuple[ProbeSpec, ...]


_CHARUTILS = "org.apache.commons.lang3.CharUtils"
_FASTMATH = "org.apache.commons.math4.util.FastMath"
_INTERVAL = "org.apache.commons.math4.geometry.euclidean.oned.Interval"
_POLYNOMIAL = "org.apache.commons.math4.analysis.polynomials.PolynomialFunction"
_PRECISION = "org.apache.commons.math4.util.Precision"
_ABS = "org.apache.commons.math4.analysis.function.Abs"

# JARVIS paper (VMCAI 2018) Table 2, verbatim. cut_* = the original Java unit test;
# pbt_* = the JARVIS-generated Scala property test. probes = the pinned
# _Jarvis*ScorecardTest fixture methods that target each row's MUT.
JARVIS_TABLE2: tuple[JarvisRow, ...] = (
    JarvisRow(
        "CharUtilsTest::isAscii",
        "char",
        cut_ic=37,
        cut_pvc=6,
        pbt_ic=37,
        pbt_pvc=59,
        probes=(ProbeSpec("isAscii", _CHARUTILS, "isAscii"),),
    ),
    JarvisRow(
        "CharUtilsTest::isPrintable",
        "char",
        cut_ic=40,
        cut_pvc=195,
        pbt_ic=40,
        pbt_pvc=45,
        probes=(ProbeSpec("isAsciiPrintable", _CHARUTILS, "isAsciiPrintable"),),
    ),
    JarvisRow(
        "FastMathTest::testMinMaxDouble",
        "double^2",
        cut_ic=782,
        cut_pvc=9,
        pbt_ic=770,
        pbt_pvc=400,
        probes=(
            ProbeSpec("minDouble", _FASTMATH, "min"),
            ProbeSpec("maxDouble", _FASTMATH, "max"),
        ),
    ),
    JarvisRow(
        "FastMathTest::toIntExact",
        # Table 2 labels the parameter space "int"; the MUT is toIntExact(long).
        "int",
        cut_ic=738,
        cut_pvc=2001,
        pbt_ic=738,
        pbt_pvc=65,
        probes=(ProbeSpec("toIntExact", _FASTMATH, "toIntExact"),),
    ),
    JarvisRow(
        "IntervalTest",
        "double^2",
        cut_ic=38,
        cut_pvc=2,
        pbt_ic=3869,
        pbt_pvc=2,
        probes=(ProbeSpec("intervalGetSize", _INTERVAL, "getSize"),),
    ),
    JarvisRow(
        "PolynomialFunctionTest::testConstants",
        "double",
        cut_ic=53,
        cut_pvc=5,
        pbt_ic=53,
        pbt_pvc=105,
        probes=(ProbeSpec("polynomialConstant", _POLYNOMIAL, "value"),),
    ),
    JarvisRow(
        "PolynomialFunctionTest::testfirstDerivativeComparison",
        "double",
        cut_ic=117,
        cut_pvc=7,
        pbt_ic=117,
        pbt_pvc=264,
        probes=(ProbeSpec("polynomialDerivative", _POLYNOMIAL, "value"),),
    ),
    JarvisRow(
        "PolynomialFunctionTest::testLinear",
        "double",
        cut_ic=71,
        cut_pvc=5,
        pbt_ic=71,
        pbt_pvc=160,
        probes=(ProbeSpec("polynomialLinear", _POLYNOMIAL, "value"),),
    ),
    JarvisRow(
        "PrecisionTest",
        "double^3",
        cut_ic=871,
        cut_pvc=8,
        pbt_ic=876,
        pbt_pvc=102,
        # The Table-2 PrecisionTest row is the eps overload equals(double, double, double).
        # The scoreboard fixture also has precisionEqualsMaxUlps (equals(double, double, int))
        # as an extra diagnostic for the raw-bits/ULP investigation; it is intentionally not
        # a Table-2 ProbeSpec — the maxUlps overload has parameter space double^2 + int, not
        # double^3, and JARVIS does not report it separately.
        probes=(ProbeSpec("precisionEquals", _PRECISION, "equals"),),
    ),
    JarvisRow(
        "UnivariateFunctionTest::testAbs",
        "double",
        cut_ic=739,
        cut_pvc=5,
        pbt_ic=739,
        pbt_pvc=506,
        probes=(ProbeSpec("absValue", _ABS, "value"),),
    ),
)


def classify_generated_test_outcome(
    result: str, failure_type: str | None, diagnostic_kind: str | None = None
) -> str:
    """Classify generated jqwik execution status without mixing it into IC."""
    if diagnostic_kind == LIMITED_DIAGNOSTIC_KIND:
        return "passed"
    normalized_result = result.upper()
    if normalized_result == "PASSED":
        return "passed"
    if failure_type in PRECONDITION_REJECTION_FAILURE_TYPES:
        return "precondition_rejected"
    if normalized_result == "FAILED":
        return "assertion_failed"
    if normalized_result == "ERROR":
        return "execution_error"
    return normalized_result.lower()


def classify_generation_diagnostic(diagnostic_kind: str | None) -> str:
    if diagnostic_kind == LIMITED_DIAGNOSTIC_KIND:
        return "limited_filter_exhausted"
    return "full"


def parse_jqwik_value_log(path: str | Path) -> pd.DataFrame:
    """Read a generated jqwik value TSV into one row per parameter value."""
    value_path = Path(path)
    rows: list[dict[str, object]] = []
    with value_path.open(encoding="utf-8") as value_file:
        for trial_index, line in enumerate(value_file):
            line = line.rstrip("\n")
            if not line:
                continue
            for cell in line.split("\t"):
                parameter_name, separator, value = cell.partition("=")
                if not separator:
                    raise ValueError(
                        f"Malformed jqwik value cell in {value_path}: {cell!r}"
                    )
                rows.append(
                    {
                        "trial_index": trial_index,
                        "parameter_name": parameter_name,
                        "value": _unescape_jqwik_value(value, value_path),
                    }
                )
    return pd.DataFrame(rows, columns=["trial_index", "parameter_name", "value"])


def _unescape_jqwik_value(value: str, value_path: Path) -> str:
    chars: list[str] = []
    index = 0
    while index < len(value):
        char = value[index]
        if char != "\\":
            chars.append(char)
            index += 1
            continue

        index += 1
        if index >= len(value):
            raise ValueError(f"Malformed jqwik value escape in {value_path}: {value!r}")

        escaped = value[index]
        if escaped == "n":
            chars.append("\n")
        elif escaped == "r":
            chars.append("\r")
        elif escaped == "t":
            chars.append("\t")
        elif escaped == "\\":
            chars.append("\\")
        elif escaped == "u":
            hex_digits = value[index + 1 : index + 5]
            if len(hex_digits) != 4 or any(
                digit not in "0123456789abcdefABCDEF" for digit in hex_digits
            ):
                raise ValueError(
                    f"Malformed jqwik unicode escape in {value_path}: {value!r}"
                )
            chars.append(chr(int(hex_digits, 16)))
            index += 4
        else:
            chars.append(escaped)
        index += 1
    return "".join(chars)


def compute_parameter_value_coverage(values: pd.DataFrame) -> pd.DataFrame:
    """Count generated and distinct generated values per parameter."""
    required_columns = {"parameter_name", "value"}
    missing_columns = required_columns.difference(values.columns)
    if missing_columns:
        missing = ", ".join(sorted(missing_columns))
        raise KeyError(f"Missing required value columns: {missing}")

    coverage = (
        values.groupby("parameter_name", sort=True)["value"]
        .agg(generated_values="size", distinct_generated_values="nunique")
        .reset_index()
    )
    return cast(
        pd.DataFrame,
        coverage[["parameter_name", "generated_values", "distinct_generated_values"]],
    )


def get_generated_test_runs(
    conn,
    *,
    project_ids: Iterable[int] | None = None,
    variants: Iterable[str] | None = None,
    outcomes: Iterable[str] | None = ("passed",),
) -> pd.DataFrame:
    """Return generalized tests with execution outcome and jqwik value-log path."""
    query = """
    SELECT
        p.id AS project_id,
        p.root_path AS root_path,
        g.id AS generalization_id,
        g.variant AS variant,
        g.class_name AS generated_class_name,
        g.method_name AS generated_method_name,
        a.id AS assertion_id,
        a.assertion_name AS assertion_name,
        a.tested_class_qualified_name AS tested_class_qualified_name,
        a.tested_method_name AS tested_method_name,
        a.tested_method_parameters AS tested_method_parameters,
        a.tested_method_call_arguments AS tested_method_call_arguments,
        jtr.result AS test_result,
        jtr.failure_type AS failure_type,
        jtr.failure_message AS failure_message,
        jpe.diagnostic_kind AS diagnostic_kind,
        jpe.distinct_new_tuples AS distinct_new_tuples,
        jpe.selected_value_log_path AS jqwik_value_log_path
    FROM generalization g
    JOIN project p ON p.id = g.project_id
    JOIN assertion a ON a.id = g.assertion_id
    JOIN junit_test_report jtr ON jtr.generalization_id = g.id
    LEFT JOIN jqwik_property_execution jpe
      ON jpe.junit_test_report_id = jtr.id
    WHERE g.is_included = TRUE
      AND jtr.stage = 'COLLECT_JUNIT_REPORTS_GENERALIZED'
    ORDER BY p.id, g.variant, g.id
    """
    runs = pd.read_sql_query(query, conn)
    path_mask = runs["jqwik_value_log_path"].notna()
    runs.loc[path_mask, "jqwik_value_log_path"] = runs.loc[
        path_mask, "jqwik_value_log_path"
    ].map(lambda path: resolve_repo_relative_path(str(path)))
    if runs.empty:
        runs["jqwik_value_log_path"] = pd.Series(dtype="object")
        runs["outcome_class"] = pd.Series(dtype="object")
        runs["outcome"] = pd.Series(dtype="object")
        runs["generation_diagnostic"] = pd.Series(dtype="object")
        return runs

    if project_ids is not None:
        runs = runs[runs["project_id"].isin(list(project_ids))]
    if variants is not None:
        runs = runs[runs["variant"].isin(list(variants))]
    runs = runs.copy()
    runs["outcome_class"] = runs.apply(
        lambda run: classify_generated_test_outcome(
            str(run["test_result"]),
            run["failure_type"],
            run["diagnostic_kind"],
        ),
        axis=1,
    )
    runs["outcome"] = runs["outcome_class"]
    runs["generation_diagnostic"] = runs["diagnostic_kind"].map(
        classify_generation_diagnostic
    )
    if outcomes is not None:
        runs = runs[runs["outcome_class"].isin(set(outcomes))]

    return cast(pd.DataFrame, runs.reset_index(drop=True))


def get_pvc_scores(
    conn,
    *,
    project_ids: Iterable[int] | None = None,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Compute parameter-value coverage for passing generated jqwik tests."""
    runs = get_generated_test_runs(conn, project_ids=project_ids, variants=variants)
    rows: list[dict[str, object]] = []
    for run in runs.to_dict("records"):
        values = parse_jqwik_value_log(run["jqwik_value_log_path"])
        per_parameter = compute_parameter_value_coverage(values)
        rows.append(
            {
                **run,
                "jqwik_trials": int(values["trial_index"].nunique()),
                "parameter_count": int(per_parameter.shape[0]),
                "parameter_value_coverage": int(
                    per_parameter["distinct_generated_values"].sum()
                ),
                "original_parameter_value_count": _count_original_argument_values(
                    run.get("tested_method_call_arguments")
                ),
            }
        )
    return pd.DataFrame(rows)


def get_instruction_coverage_scores(
    conn,
    *,
    project_ids: Iterable[int] | None = None,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Return generated-variant instruction coverage from JaCoCo rows."""
    query = """
    SELECT
        project_id,
        variant,
        SUM(instruction_covered) AS instruction_covered,
        SUM(instruction_missed) AS instruction_missed
    FROM jacoco_coverage_report
    WHERE stage = 'COLLECT_JACOCO_DATA_GENERALIZED'
      AND variant IS NOT NULL
    GROUP BY project_id, variant
    ORDER BY project_id, variant
    """
    coverage = pd.read_sql_query(query, conn)
    if project_ids is not None and not coverage.empty:
        coverage = coverage[coverage["project_id"].isin(list(project_ids))]
    if variants is not None and not coverage.empty:
        coverage = coverage[coverage["variant"].isin(list(variants))]
    if coverage.empty:
        return pd.DataFrame(
            columns=[
                "project_id",
                "variant",
                "instruction_covered",
                "instruction_missed",
                "instruction_total",
                "instruction_coverage",
            ]
        )

    coverage = coverage.copy()
    coverage["instruction_covered"] = coverage["instruction_covered"].astype(int)
    coverage["instruction_missed"] = coverage["instruction_missed"].astype(int)
    coverage["instruction_total"] = (
        coverage["instruction_covered"] + coverage["instruction_missed"]
    )
    coverage["instruction_coverage"] = (
        coverage["instruction_covered"] / coverage["instruction_total"]
    )
    return cast(pd.DataFrame, coverage.reset_index(drop=True))


def get_mutation_scores(
    conn,
    *,
    project_ids: Iterable[int] | None = None,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Return distinct killed/covered/total PIT mutants per project and variant.

    Each mutant is counted once by its stable PIT identity
    (class/method/description/line/mutator/index); every key component is
    null-guarded so a nullable field never silently drops a mutant.
    ``covered_mutants`` excludes ``NO_COVERAGE``/``NON_VIABLE`` -- the mutants the
    generated tests actually reach, which is the meaningful denominator. The
    project-wide ``total_mutants`` is kept for context but is dominated by mutants
    in code the probes never touch.
    """
    mutant_key = (
        "COALESCE(mutated_class, '<null>') || '|' || "
        "COALESCE(mutated_method, '<null>') || '|' || "
        "COALESCE(method_description, '<null>') || '|' || "
        "COALESCE(CAST(line_number AS TEXT), '<null>') || '|' || "
        "COALESCE(mutator, '<null>') || '|' || "
        "COALESCE(CAST(indexes AS TEXT), '<null>')"
    )
    query = f"""
    SELECT
        project_id,
        variant,
        COUNT(DISTINCT {mutant_key}) FILTER (WHERE is_detected) AS killed_mutants,
        COUNT(DISTINCT {mutant_key}) FILTER (
            WHERE status NOT IN ('NO_COVERAGE', 'NON_VIABLE')
        ) AS covered_mutants,
        COUNT(DISTINCT {mutant_key}) AS total_mutants
    FROM pit_mutation_report
    WHERE stage = 'COLLECT_PIT_DATA_GENERALIZED'
      AND variant IS NOT NULL
    GROUP BY project_id, variant
    ORDER BY project_id, variant
    """
    scores = pd.read_sql_query(query, conn)
    columns = [
        "project_id",
        "variant",
        "killed_mutants",
        "covered_mutants",
        "total_mutants",
    ]
    if project_ids is not None and not scores.empty:
        scores = scores[scores["project_id"].isin(list(project_ids))]
    if variants is not None and not scores.empty:
        scores = scores[scores["variant"].isin(list(variants))]
    if scores.empty:
        return pd.DataFrame(columns=columns)
    scores = scores.copy()
    for column in ("killed_mutants", "covered_mutants", "total_mutants"):
        scores.loc[:, column] = scores[column].astype(int)
    return cast(pd.DataFrame, scores[columns].reset_index(drop=True))


CENSUS_VARIANTS = ("IMPROVED_100_TRIES",)

_MUTANT_KEY_SQL = (
    "COALESCE(mutated_class, '<null>') || '|' || "
    "COALESCE(mutated_method, '<null>') || '|' || "
    "COALESCE(method_description, '<null>') || '|' || "
    "COALESCE(CAST(line_number AS TEXT), '<null>') || '|' || "
    "COALESCE(mutator, '<null>') || '|' || "
    "COALESCE(CAST(indexes AS TEXT), '<null>')"
)


def mutation_gain_keys(generalized_keys: set[str], initial_keys: set[str]) -> set[str]:
    """Mutant keys killed by the GENERALIZED (seed+properties) suite but not by INITIAL (seed)."""
    return generalized_keys - initial_keys


def get_mutation_gain(
    conn,
    *,
    project_ids: Iterable[int] | None = None,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Per (project, variant): the net fault-detection gain of generalization.

    INITIAL (the seed suite) runs once per project (``variant IS NULL``); GENERALIZED
    (seed + generalized tests) runs per variant over the same mutated classes. The
    gain is the killed mutant-key set difference ``GENERALIZED \\ INITIAL`` -- the
    mutants the added generalized tests kill that the single-value seed tests miss.
    """
    initial = pd.read_sql_query(
        f"SELECT project_id, {_MUTANT_KEY_SQL} AS k FROM pit_mutation_report "
        "WHERE stage = 'COLLECT_PIT_DATA_INITIAL' AND is_detected",
        conn,
    )
    generalized = pd.read_sql_query(
        f"SELECT project_id, variant, {_MUTANT_KEY_SQL} AS k FROM pit_mutation_report "
        "WHERE stage = 'COLLECT_PIT_DATA_GENERALIZED' AND variant IS NOT NULL "
        "AND is_detected",
        conn,
    )
    columns = ["project_id", "variant", "initial_killed", "generalized_killed", "gain"]
    if generalized.empty:
        return pd.DataFrame(columns=columns)
    initial_by_project = {
        int(pid): set(grp["k"]) for pid, grp in initial.groupby("project_id")
    }
    rows = []
    for _, grp in generalized.groupby(["project_id", "variant"]):
        pid = int(grp["project_id"].iloc[0])
        variant = str(grp["variant"].iloc[0])
        gen_keys = set(grp["k"])
        init_keys = initial_by_project.get(pid, set())
        rows.append(
            {
                "project_id": pid,
                "variant": variant,
                "initial_killed": len(init_keys),
                "generalized_killed": len(gen_keys),
                "gain": len(mutation_gain_keys(gen_keys, init_keys)),
            }
        )
    result = pd.DataFrame(rows, columns=columns)
    if project_ids is not None:
        result = result[result["project_id"].isin(list(project_ids))]
    if variants is not None:
        result = result[result["variant"].isin(list(variants))]
    return cast(pd.DataFrame, result.reset_index(drop=True))


def _project_label(root_path: object) -> str:
    """Trailing path component of a project root_path (e.g. ``commons-math-2017-02-01-census``)."""
    return str(root_path).rstrip("/").rsplit("/", 1)[-1]


def get_census(
    conn,
    *,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Per (project, variant, upstream test class): the sound-generalization funnel.

    ``full_sound`` counts generalized tests whose diagnostic is FULL; ``executions``
    is all property executions. Grouped by the upstream test class (e.g. ``FastMathTest``)
    the generalized assertions came from.
    """
    query = """
    SELECT
        p.root_path AS root_path,
        g.variant AS variant,
        t.test_class_name AS test_class,
        COUNT(*) FILTER (WHERE jpe.diagnostic_kind = 'FULL') AS full_sound,
        COUNT(*) AS executions
    FROM jqwik_property_execution jpe
    JOIN generalization g ON g.id = jpe.generalization_id
    JOIN assertion a ON a.id = g.assertion_id
    JOIN test t ON t.id = a.test_id
    JOIN project p ON p.id = jpe.project_id
    GROUP BY p.root_path, g.variant, t.test_class_name
    ORDER BY p.root_path, g.variant, t.test_class_name
    """
    census = pd.read_sql_query(query, conn)
    columns = ["project", "variant", "test_class", "full_sound", "executions"]
    if census.empty:
        return pd.DataFrame(columns=columns)
    census["project"] = census["root_path"].map(_project_label)
    if variants is not None:
        census = census[census["variant"].isin(list(variants))]
    for column in ("full_sound", "executions"):
        census[column] = census[column].astype(int)
    return cast(pd.DataFrame, census[columns].reset_index(drop=True))


def _method_signature(parameters_json: object) -> tuple[str, bool]:
    """Return an ordered parameter-type signature and whether it is complete."""
    if parameters_json is None or (
        isinstance(parameters_json, float) and pd.isna(parameters_json)
    ):
        return "?", False
    try:
        parsed = json.loads(str(parameters_json))
    except (TypeError, ValueError):
        return "?", False
    if not isinstance(parsed, list):
        return "?", False
    types: list[str] = []
    known = True
    for parameter in parsed:
        if isinstance(parameter, dict) and parameter.get("type"):
            types.append(str(parameter["type"]))
        else:
            types.append("?")
            known = False
    return ",".join(types), known


def _mut_identity(
    tested_class: object, tested_method: object, parameters_json: object
) -> tuple[str | None, bool]:
    """Build a stable MUT identity without merging unresolved overloads."""
    if tested_class is None or tested_method is None:
        return None, False
    if isinstance(tested_class, float) and pd.isna(tested_class):
        return None, False
    if isinstance(tested_method, float) and pd.isna(tested_method):
        return None, False
    signature, signature_known = _method_signature(parameters_json)
    return f"{tested_class}::{tested_method}({signature})", signature_known


def get_census_by_mut(
    conn,
    *,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Aggregate census property executions by production MUT identity.

    ``diagnostic_kind = 'FULL'`` is an internal soundness marker. Unresolved MUTs
    remain as rows with ``signature_known = False`` so callers can report them as
    diagnostics without counting them as supported MUTs.
    """
    query = """
    SELECT
        p.root_path AS root_path,
        g.variant AS variant,
        a.tested_class_qualified_name AS tested_class,
        a.tested_method_name AS tested_method,
        a.tested_method_parameters AS tested_method_parameters,
        t.test_class_name AS test_class,
        jpe.diagnostic_kind AS diagnostic_kind
    FROM jqwik_property_execution jpe
    JOIN generalization g ON g.id = jpe.generalization_id
    JOIN assertion a ON a.id = g.assertion_id
    JOIN test t ON t.id = a.test_id
    JOIN project p ON p.id = jpe.project_id
    WHERE g.is_included = TRUE
    ORDER BY p.root_path, g.variant, a.id, jpe.id
    """
    raw = pd.read_sql_query(query, conn)
    columns = [
        "project",
        "variant",
        "mut_key",
        "signature_known",
        "sound_properties",
        "all_property_executions",
        "source_test_classes",
    ]
    if raw.empty:
        return pd.DataFrame(columns=columns)
    if variants is not None:
        raw = raw[raw["variant"].isin(list(variants))]
    if raw.empty:
        return pd.DataFrame(columns=columns)
    identities = raw.apply(
        lambda row: _mut_identity(
            row["tested_class"],
            row["tested_method"],
            row["tested_method_parameters"],
        ),
        axis=1,
    )
    raw = raw.copy()
    raw["mut_key"] = [identity[0] for identity in identities]
    raw["signature_known"] = [identity[1] for identity in identities]
    raw["project"] = raw["root_path"].map(_project_label)
    grouped = (
        raw.groupby(
            ["project", "variant", "mut_key", "signature_known"],
            dropna=False,
            sort=True,
        )
        .agg(
            sound_properties=(
                "diagnostic_kind",
                lambda values: int((values == "FULL").sum()),
            ),
            all_property_executions=("diagnostic_kind", "size"),
            source_test_classes=("test_class", "nunique"),
        )
        .reset_index()
    )
    for column in (
        "sound_properties",
        "all_property_executions",
        "source_test_classes",
    ):
        grouped[column] = grouped[column].astype(int)
    return cast(pd.DataFrame, grouped[columns].reset_index(drop=True))


def get_census_project_pvc(
    conn,
    *,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Union sound jqwik values by project, variant, MUT, and parameter.

    A value emitted by multiple assertions targeting one MUT contributes once. The
    result therefore differs intentionally from ``get_pvc_scores``, which sums
    per-property PVC for the scoreboard's row-level diagnostic.
    """
    runs = get_generated_test_runs(conn, variants=variants)
    columns = [
        "project",
        "variant",
        "aggregate_pvc",
        "sound_properties",
        "sound_muts",
        "unresolved_sound_properties",
    ]
    if runs.empty:
        return pd.DataFrame(columns=columns)

    values_by_key: dict[tuple[str, str, str], dict[str, set[str]]] = {}
    sound_properties: dict[tuple[str, str], int] = {}
    sound_muts: dict[tuple[str, str], set[str]] = {}
    unresolved: dict[tuple[str, str], int] = {}
    for run in runs.to_dict("records"):
        if run.get("diagnostic_kind") != "FULL":
            continue
        project = _project_label(run.get("root_path", ""))
        variant = str(run["variant"])
        project_variant = (project, variant)
        sound_properties[project_variant] = sound_properties.get(project_variant, 0) + 1
        mut_key, _ = _mut_identity(
            run.get("tested_class_qualified_name"),
            run.get("tested_method_name"),
            run.get("tested_method_parameters"),
        )
        if mut_key is None:
            unresolved[project_variant] = unresolved.get(project_variant, 0) + 1
            continue
        sound_muts.setdefault(project_variant, set()).add(mut_key)
        path = run.get("jqwik_value_log_path")
        if path is None or (isinstance(path, float) and pd.isna(path)):
            continue
        values = parse_jqwik_value_log(path)
        key = (project, variant, mut_key)
        per_parameter = values_by_key.setdefault(key, {})
        for value in values.to_dict("records"):
            per_parameter.setdefault(str(value["parameter_name"]), set()).add(
                str(value["value"])
            )

    rows: list[dict[str, object]] = []
    project_variants = set(sound_properties) | {
        (key[0], key[1]) for key in values_by_key
    }
    for project, variant in sorted(project_variants):
        aggregate = sum(
            len(values)
            for key, parameters in values_by_key.items()
            if key[0] == project and key[1] == variant
            for values in parameters.values()
        )
        rows.append(
            {
                "project": project,
                "variant": variant,
                "aggregate_pvc": int(aggregate),
                "sound_properties": int(sound_properties.get((project, variant), 0)),
                "sound_muts": int(len(sound_muts.get((project, variant), set()))),
                "unresolved_sound_properties": int(
                    unresolved.get((project, variant), 0)
                ),
            }
        )
    return pd.DataFrame(rows, columns=columns)


def get_census_filter_tally(conn) -> pd.DataFrame:
    """Per (project, filter, decision): filter-result counts.

    ``REJECT`` rows are the actual exclusions (e.g. the type-ceiling ``ParameterTypeFilter``);
    ``DEFER`` rows are informational annotations (loop/nested/static-init) that never exclude.
    """
    tally = pd.read_sql_query(
        "SELECT p.root_path AS root_path, fr.filter_name AS filter_name, "
        "fr.decision AS decision, COUNT(*) AS count "
        "FROM filter_result fr JOIN project p ON p.id = fr.project_id "
        "WHERE fr.decision IN ('REJECT', 'DEFER') "
        "GROUP BY p.root_path, fr.filter_name, fr.decision",
        conn,
    )
    columns = ["project", "filter_name", "decision", "count"]
    if tally.empty:
        return pd.DataFrame(columns=columns)
    tally["project"] = tally["root_path"].map(_project_label)
    tally["count"] = tally["count"].astype(int)
    tally = tally.sort_values(
        ["project", "decision", "count"], ascending=[True, True, False]
    )
    return cast(pd.DataFrame, tally[columns].reset_index(drop=True))


def get_scoreboard(
    conn,
    *,
    project_ids: Iterable[int] | None = None,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Combine PVC and generated-variant IC for JARVIS scoreboard rows."""
    pvc = get_pvc_scores(conn, project_ids=project_ids, variants=variants)
    coverage = get_instruction_coverage_scores(
        conn, project_ids=project_ids, variants=variants
    )
    if pvc.empty:
        return pvc
    return pvc.merge(coverage, on=["project_id", "variant"], how="left")


def compare_to_jarvis(
    scoreboard: pd.DataFrame, *, variant: str = "IMPROVED_100_TRIES"
) -> pd.DataFrame:
    """Aggregate Teralizer probes into the published JARVIS Table-2 rows.

    The returned ``pvc_delta`` is PVC of Teralizer's generalized tests minus JARVIS
    published PBT PVC. Original CUT PVC is carried as a published reference. The database
    snapshot stores only a single assertion's seed-call values for the original
    suite, so a complete Teralizer CUT value set is unavailable. A row with no
    matching fixture probe has ``probe_count = 0`` and ``None`` PVC/delta values so
    callers cannot mistake an unavailable result for observed numeric zero.
    """
    selected = scoreboard[scoreboard["variant"] == variant]
    has_mut = {"tested_class_qualified_name", "tested_method_name"}.issubset(
        selected.columns
    )
    result: list[dict[str, object]] = []
    for row in JARVIS_TABLE2:
        teralizer_pvc = 0
        probe_count = 0
        for spec in row.probes:
            matched = selected[
                selected["generated_method_name"] == spec.generated_method_name
            ]
            if matched.empty:
                continue
            if has_mut:
                wrong = matched[
                    (matched["tested_class_qualified_name"] != spec.tested_class)
                    | (matched["tested_method_name"] != spec.tested_method)
                ]
                if not wrong.empty:
                    raise ValueError(
                        f"fixture probe {spec.generated_method_name!r} resolved to an "
                        f"unexpected MUT; expected {spec.tested_class}.{spec.tested_method}"
                    )
            teralizer_pvc += int(matched["parameter_value_coverage"].sum())
            probe_count += int(matched.shape[0])
        observed_pvc: int | None = teralizer_pvc if probe_count else None
        result.append(
            {
                "table_row": row.table_row,
                "parameter_space": row.parameter_space,
                "probe_count": probe_count,
                "teralizer_pvc": observed_pvc,
                "original_cut_pvc": row.cut_pvc,
                "jarvis_pbt_pvc": row.pbt_pvc,
                "pvc_delta": (
                    observed_pvc - row.pbt_pvc if observed_pvc is not None else None
                ),
                "pbt_cut_multiplier": round(row.pbt_pvc / row.cut_pvc, 4),
            }
        )
    return pd.DataFrame(result)


def _value_identity(value: object) -> str:
    encoded = str(value).encode("utf-8", errors="surrogatepass")
    return base64.b64encode(encoded).decode("ascii")


def suite_union_pvc(
    scoreboard: pd.DataFrame,
    cut_values: pd.DataFrame,
    *,
    variant: str = "IMPROVED_100_TRIES",
) -> pd.DataFrame:
    """Measured PVC of the suite after generalization, per JARVIS Table-2 row.

    Union of the captured original-suite values (``teralizer.cut_pvc``) and
    the values in the generalized tests' jqwik logs, per MUT and parameter
    position, summed with the same per-parameter construct as
    ``get_pvc_scores``. Generalized tests always exercise the original inputs
    as their first samples (FirstValueArbitrary), so the logs already contain
    the seed values. Rows without a generalized test yield ``None`` (a dash,
    never zero).
    """
    if scoreboard.empty:
        selected = scoreboard
    else:
        selected = scoreboard[scoreboard["variant"] == variant]
    rows: list[dict[str, object]] = []
    for row in JARVIS_TABLE2:
        if cut_values.empty:
            cut_rows = cut_values
        else:
            cut_rows = cut_values[cut_values["table_row"] == row.table_row]
        union: dict[tuple[str, str, int], set[str]] = {}
        for cut in cut_rows.to_dict("records"):
            parameter = str(cut["parameter"])
            if not parameter.startswith("p") or not parameter[1:].isdigit():
                raise ValueError(f"Unexpected capture parameter name: {parameter!r}")
            key = (str(cut["mut_class"]), str(cut["mut_method"]), int(parameter[1:]))
            union.setdefault(key, set()).add(_value_identity(cut["value"]))
        measured_cut = (
            sum(len(values) for values in union.values())
            if not cut_rows.empty
            else None
        )
        matched_runs: list[dict[str, object]] = []
        for spec in row.probes:
            if selected.empty:
                continue
            matched = cast(
                pd.DataFrame,
                selected[
                    selected["generated_method_name"] == spec.generated_method_name
                ],
            )
            for run in matched.to_dict("records"):
                matched_runs.append({"spec": spec, "run": run})
        if not matched_runs:
            rows.append(
                {
                    "table_row": row.table_row,
                    "measured_cut_pvc": measured_cut,
                    "suite_pvc": None,
                }
            )
            continue
        for entry in matched_runs:
            spec = entry["spec"]
            run = entry["run"]
            parameters = json.loads(str(run["tested_method_parameters"]))
            names = [str(parameter["name"]) for parameter in parameters]
            index_of = {name: index for index, name in enumerate(names)}
            values = run["parameter_values"]
            if not isinstance(values, list):
                raise ValueError(
                    f"Normalized parameter values for {spec.generated_method_name!r} "
                    "must be a list"
                )
            unknown = {
                str(record["parameter"])
                for record in values
                if str(record["parameter"]) not in index_of
            }
            if unknown:
                raise ValueError(
                    f"Value-log parameters {sorted(unknown)} not in "
                    f"tested_method_parameters of probe "
                    f"{spec.generated_method_name!r}"
                )
            for record in values:
                key = (
                    spec.tested_class,
                    spec.tested_method,
                    index_of[str(record["parameter"])],
                )
                union.setdefault(key, set()).add(str(record["value_base64"]))
        rows.append(
            {
                "table_row": row.table_row,
                "measured_cut_pvc": measured_cut,
                "suite_pvc": sum(len(values) for values in union.values()),
            }
        )
    return pd.DataFrame(rows, columns=["table_row", "measured_cut_pvc", "suite_pvc"])


def _count_original_argument_values(arguments_json: object) -> int:
    if arguments_json is None or pd.isna(arguments_json):
        return 0
    arguments = json.loads(str(arguments_json))
    if not isinstance(arguments, list):
        raise ValueError("tested_method_call_arguments must be a JSON list")
    values = []
    for index, argument in enumerate(arguments):
        if isinstance(argument, dict):
            value = argument.get("value")
        else:
            value = argument
        values.append((index, str(value)))
    return len(set(values))


def summarize_variants(
    scoreboard: pd.DataFrame, mutation: pd.DataFrame
) -> pd.DataFrame:
    """Per-variant tries-sweep totals: probe count, summed PVC, and PIT kills.

    PVC sums a variant's per-probe distinct-value counts. ``killed_mutants``,
    ``covered_mutants`` and ``total_mutants`` sum the distinct PIT kills, covered
    mutants (reached by the tests) and all project mutants across fixtures.
    ``covered_mutation_score`` is killed/covered -- the meaningful score, since
    ``total_mutants`` is dominated by code the probes never touch. The point is that
    PVC rises with the tries budget while kills and the covered score stay flat.
    """
    pvc = scoreboard.groupby("variant").agg(
        probes=("parameter_value_coverage", "size"),
        total_pvc=("parameter_value_coverage", "sum"),
    )
    muts = mutation.groupby("variant").agg(
        killed_mutants=("killed_mutants", "sum"),
        covered_mutants=("covered_mutants", "sum"),
        total_mutants=("total_mutants", "sum"),
    )
    summary = pvc.join(muts, how="outer").reset_index()
    int_columns = (
        "probes",
        "total_pvc",
        "killed_mutants",
        "covered_mutants",
        "total_mutants",
    )
    for column in int_columns:
        summary.loc[:, column] = summary[column].fillna(0).astype(int)
    summary.loc[:, "covered_mutation_score"] = [
        round(killed / covered, 4) if covered else 0.0
        for killed, covered in zip(
            summary["killed_mutants"], summary["covered_mutants"]
        )
    ]
    return cast(pd.DataFrame, summary)


SWEEP_VARIANTS = (
    "IMPROVED_100_TRIES",
    "IMPROVED_200_TRIES",
    "IMPROVED_1000_TRIES",
)


def main() -> None:
    """Print JARVIS scorecard tables from registered corpora.

    ``uv run --directory analysis python -m teralizer.jarvis_scoreboard`` scores the
    IMPROVED_100_TRIES variant against :data:`JARVIS_TABLE2`. ``--sweep`` instead
    prints the per-variant tries-sweep summary (PVC versus covered mutation score)
    for the IMPROVED tries ladder in :data:`SWEEP_VARIANTS`; its ``probes`` column shows the
    passing probe count, so an excluded probe (13 of 14) stays visible.
    """
    import argparse

    from teralizer.corpora import open_corpus
    from teralizer.report_basis import print_basis_header

    parser = argparse.ArgumentParser(description="JARVIS scratch-scorecard tables.")
    parser.add_argument(
        "--sweep",
        action="store_true",
        help="print the tries-sweep summary (PVC vs covered mutation score)",
    )
    parser.add_argument(
        "--census",
        action="store_true",
        help="print the beyond-JARVIS census",
    )
    parser.add_argument(
        "--corpus",
        help=(
            "registered corpus override (defaults: jarvis-scenarios, or "
            "jarvis-benchmark with --census)"
        ),
    )
    args = parser.parse_args()

    if args.census:
        corpus_id = args.corpus or "jarvis-benchmark"
        with open_corpus(corpus_id) as (entry, conn):
            print_basis_header(conn, entry.database)
            census = get_census(conn, variants=CENSUS_VARIANTS)
            gain = get_mutation_gain(conn, variants=CENSUS_VARIANTS)
            scores = get_mutation_scores(conn, variants=CENSUS_VARIANTS)
            tally = get_census_filter_tally(conn)
        mutation = gain.merge(
            scores[["project_id", "variant", "covered_mutants", "total_mutants"]],
            on=["project_id", "variant"],
            how="left",
        )
        print("=== sound generalizations per upstream test class (FULL) ===")
        print(census.to_string(index=False))
        print(
            "\n=== mutation: augmented score (generalized_killed / covered_mutants) "
            "+ gain (GENERALIZED \\ INITIAL killed mutant-keys) ==="
        )
        print(mutation.to_string(index=False))
        print("\n=== filter decisions (REJECT excludes; DEFER is informational) ===")
        print(tally.to_string(index=False))
        return

    corpus_id = args.corpus or "jarvis-scenarios"
    with open_corpus(corpus_id) as (entry, conn):
        print_basis_header(conn, entry.database)
        if args.sweep:
            summary = summarize_variants(
                get_scoreboard(conn, variants=SWEEP_VARIANTS),
                get_mutation_scores(conn, variants=SWEEP_VARIANTS),
            )
            table = summary.set_index("variant").reindex(SWEEP_VARIANTS).reset_index()
        else:
            scoreboard = get_scoreboard(conn, variants=["IMPROVED_100_TRIES"])
            table = compare_to_jarvis(scoreboard, variant="IMPROVED_100_TRIES")
    print(table.to_string(index=False))


if __name__ == "__main__":
    main()
