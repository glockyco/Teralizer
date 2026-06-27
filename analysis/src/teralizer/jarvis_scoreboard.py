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

import json
from pathlib import Path
from typing import Iterable

import pandas as pd

GENERATED_JUNIT_STAGE = "COLLECT_JUNIT_REPORTS_GENERALIZED"
GENERATED_JACOCO_STAGE = "COLLECT_JACOCO_DATA_GENERALIZED"

PRECONDITION_REJECTION_FAILURE_TYPES = frozenset(
    {"net.jqwik.api.TooManyFilterMissesException"}
)


def classify_generated_test_outcome(result: str, failure_type: str | None) -> str:
    """Classify generated jqwik execution status without mixing it into IC."""
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
    return coverage[["parameter_name", "generated_values", "distinct_generated_values"]]


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
        p.root_path AS project_root_path,
        p.data_path AS data_path,
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
        jtr.failure_message AS failure_message
    FROM generalization g
    JOIN project p ON p.id = g.project_id
    JOIN assertion a ON a.id = g.assertion_id
    JOIN junit_test_report jtr ON jtr.generalization_id = g.id
    WHERE g.is_included = TRUE
      AND jtr.stage = 'COLLECT_JUNIT_REPORTS_GENERALIZED'
    ORDER BY p.id, g.variant, g.id
    """
    runs = pd.read_sql_query(query, conn)
    if runs.empty:
        runs["jqwik_value_log_path"] = pd.Series(dtype="object")
        runs["outcome_class"] = pd.Series(dtype="object")
        return runs

    if project_ids is not None:
        runs = runs[runs["project_id"].isin(list(project_ids))]
    if variants is not None:
        runs = runs[runs["variant"].isin(list(variants))]
    runs = runs.copy()
    runs["outcome_class"] = runs.apply(
        lambda run: classify_generated_test_outcome(
            str(run["test_result"]), run["failure_type"]
        ),
        axis=1,
    )
    if outcomes is not None:
        runs = runs[runs["outcome_class"].isin(set(outcomes))]

    runs = runs.copy()
    runs["jqwik_value_log_path"] = runs.apply(_jqwik_value_log_path, axis=1)
    return runs.reset_index(drop=True)


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
    return coverage.reset_index(drop=True)


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


def _jqwik_value_log_path(run: pd.Series) -> str:
    data_path = Path(str(run["data_path"]))
    project_id = int(run["project_id"])
    generalization_id = int(run["generalization_id"])
    variant = run["variant"]

    def value_log_path(base: Path) -> Path:
        return (
            base
            / f"project-id-{project_id}"
            / "jqwik-data"
            / f"{generalization_id}.{variant}.tsv"
        )

    def junit_snapshot_path(path: Path) -> Path:
        return path.with_name(f"{generalization_id}.{variant}.junit.tsv")

    candidate_bases: list[Path]
    if data_path.is_absolute():
        candidate_bases = [data_path]
        fallback_base = data_path
    else:
        project_root_path = Path(str(run["project_root_path"]))
        workspace_data_path = Path.cwd() / data_path
        if project_root_path.is_absolute():
            project_data_path = project_root_path / data_path
            fallback_base = project_data_path
        else:
            project_data_path = Path.cwd() / project_root_path / data_path
            fallback_base = workspace_data_path
        candidate_bases = [workspace_data_path, project_data_path]

    candidate_paths: list[Path] = []
    for base in candidate_bases:
        live_path = value_log_path(base)
        candidate_paths.append(junit_snapshot_path(live_path))
        candidate_paths.append(live_path)

    for candidate in candidate_paths:
        if candidate.exists():
            return str(candidate)
    return str(value_log_path(fallback_base))


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
