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

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Iterable, cast

import pandas as pd

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
_FASTMATH = "org.apache.commons.math3.util.FastMath"
_INTERVAL = "org.apache.commons.math3.geometry.euclidean.oned.Interval"
_POLYNOMIAL = "org.apache.commons.math3.analysis.polynomials.PolynomialFunction"
_PRECISION = "org.apache.commons.math3.util.Precision"
_ABS = "org.apache.commons.math3.analysis.function.Abs"

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
    """Return generated tests with execution outcome and jqwik value-log path."""
    query = """
    SELECT
        p.id AS project_id,
        g.id AS generalization_id,
        g.variant AS variant,
        g.class_name AS generated_class_name,
        g.method_name AS generated_method_name,
        a.id AS assertion_id,
        a.assertion_name AS assertion_name,
        a.tested_class_qualified_name AS tested_class_qualified_name,
        a.tested_method_name AS tested_method_name,
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
        scores[column] = scores[column].astype(int)
    return cast(pd.DataFrame, scores[columns].reset_index(drop=True))


CENSUS_VARIANTS = ("NAIVE_100_TRIES", "IMPROVED_100_TRIES")

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
    (seed + generated properties) runs per variant over the same mutated classes. The
    gain is the killed mutant-key set difference ``GENERALIZED \\ INITIAL`` -- the
    mutants the added properties kill that the single-value seed tests miss.
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
    """Trailing path component of a project root_path (e.g. ``commons-math-3.5-census``)."""
    return str(root_path).rstrip("/").rsplit("/", 1)[-1]


def get_census(
    conn,
    *,
    variants: Iterable[str] | None = None,
) -> pd.DataFrame:
    """Per (project, variant, upstream test class): the sound-generalization funnel.

    ``full_sound`` counts generated properties whose diagnostic is FULL; ``executions``
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
    """Aggregate Teralizer probes into JARVIS Table-2 rows and score PVC head-to-head.

    Folds the per-assertion probes of ``variant`` into their Table-2 row, summing PVC
    (the eps precedent), joins JARVIS's published Scala-PBT PVC, and flags each row
    ``win``/``trail``/``absent``. Probes are keyed by fixture method name because the
    three ``PolynomialFunction`` rows share one MUT (``value``) with an identical
    single-double argument signature, so the MUT alone cannot separate them; when the
    scoreboard carries ``tested_class_qualified_name``/``tested_method_name`` the
    matched MUT is validated against the reference and a mismatch raises. Non-Table-2
    probes (e.g. the raw-bits ``precisionEqualsMaxUlps``) have no spec and are dropped.
    IC is not compared per row: Teralizer IC is project-level until one-project-per-probe
    fixtures land, so only JARVIS's reference IC is carried for context.
    """
    selected = scoreboard[scoreboard["variant"] == variant]
    has_mut = {"tested_class_qualified_name", "tested_method_name"}.issubset(
        selected.columns
    )
    result = []
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
        if probe_count == 0:
            verdict = "absent"
        elif teralizer_pvc >= row.pbt_pvc:
            verdict = "win"
        else:
            verdict = "trail"
        result.append(
            {
                "table_row": row.table_row,
                "parameter_space": row.parameter_space,
                "probe_count": probe_count,
                "teralizer_pvc": teralizer_pvc,
                "jarvis_cut_pvc": row.cut_pvc,
                "jarvis_pbt_pvc": row.pbt_pvc,
                "pvc_delta": teralizer_pvc - row.pbt_pvc,
                "verdict": verdict,
            }
        )
    return pd.DataFrame(result)


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
        summary[column] = summary[column].fillna(0).astype(int)
    summary["covered_mutation_score"] = [
        round(killed / covered, 4) if covered else 0.0
        for killed, covered in zip(
            summary["killed_mutants"], summary["covered_mutants"]
        )
    ]
    return cast(pd.DataFrame, summary)


SWEEP_VARIANTS = (
    "NAIVE_100_TRIES",
    "NAIVE_200_TRIES",
    "NAIVE_1000_TRIES",
    "IMPROVED_100_TRIES",
    "IMPROVED_200_TRIES",
    "IMPROVED_1000_TRIES",
)


def main() -> None:
    """Print a JARVIS scratch-scorecard table from ``postgres_jarvis_scoreboard``.

    ``uv run --directory analysis python -m teralizer.jarvis_scoreboard`` scores the
    IMPROVED_100_TRIES variant against :data:`JARVIS_TABLE2`. ``--sweep`` instead
    prints the per-variant tries-sweep summary (PVC versus covered mutation score)
    for the six canonical :data:`SWEEP_VARIANTS`; its ``probes`` column shows the
    passing probe count, so an excluded probe (13 of 14) stays visible. The working
    directory moves to the repo root so the repo-relative jqwik value-log paths
    resolve.
    """
    import argparse
    import os

    from teralizer.config import find_project_root
    from teralizer.report_basis import open_report_connection, print_basis_header

    parser = argparse.ArgumentParser(description="JARVIS scratch-scorecard tables.")
    parser.add_argument(
        "--sweep",
        action="store_true",
        help="print the tries-sweep summary (PVC vs covered mutation score)",
    )
    parser.add_argument(
        "--census",
        action="store_true",
        help="print the beyond-JARVIS census from postgres_jarvis_census",
    )
    parser.add_argument(
        "--db",
        help=(
            "database override (defaults: postgres_jarvis_scoreboard, or "
            "postgres_jarvis_census with --census)"
        ),
    )
    args = parser.parse_args()

    os.chdir(Path(find_project_root()).parent)

    if args.census:
        db_name = args.db or "postgres_jarvis_census"
        with open_report_connection(db_name) as conn:
            print_basis_header(conn, db_name)
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

    db_name = args.db or "postgres_jarvis_scoreboard"
    with open_report_connection(db_name) as conn:
        print_basis_header(conn, db_name)
        if args.sweep:
            summary = summarize_variants(
                get_scoreboard(conn, variants=SWEEP_VARIANTS),
                get_mutation_scores(conn, variants=SWEEP_VARIANTS),
            )
            table = summary.set_index("variant").reindex(SWEEP_VARIANTS).reset_index()
        else:
            scoreboard = get_scoreboard(
                conn, variants=["NAIVE_100_TRIES", "IMPROVED_100_TRIES"]
            )
            table = compare_to_jarvis(scoreboard, variant="IMPROVED_100_TRIES")
    print(table.to_string(index=False))


if __name__ == "__main__":
    main()
