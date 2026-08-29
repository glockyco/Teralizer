"""Structured cause taxonomy for the RQ6 project-level funnel.

Replaces the legacy free-text regex classifier and stage-remap hacks with rules
over structured signals: internal stage, task_diagnostic reason_code, whether the
failing task hit its runtime ceiling, and per-project inclusion counts. Every
attribution resolves to a Cause or to UNCODED (which the funnel treats as a
loud defect, never a silent bucket)."""

from __future__ import annotations

from dataclasses import dataclass

_STAGE_1_2 = {
    "SETUP_PROJECT",
    "ADD_DEPENDENCIES",
    "BUILD_PROJECT_ORIGINAL",
    "BUILD_SPOON_MODEL",
    "EXECUTE_TESTS_ORIGINAL",
    "COLLECT_JUNIT_REPORTS_ORIGINAL",
    "FILTER_TESTS_ORIGINAL",
    "ANALYZE_TESTS",
    "FILTER_TESTS",
    "FILTER_ASSERTIONS",
}
_STAGE_3 = {
    "ADD_JPF_INSTRUMENTATION",
    "BUILD_PROJECT_INSTRUMENTED",
    "EXECUTE_JPF",
    "ANALYZE_JPF",
    "CLEANUP_JPF_INSTRUMENTATION",
    "BUILD_PROJECT_INITIAL",
    "EXECUTE_TESTS_INITIAL",
    "COLLECT_JUNIT_REPORTS_INITIAL",
}
_STAGE_4 = {
    "CLEANUP_GENERALIZATION",
    "GENERALIZE_TESTS",
    "BUILD_PROJECT_GENERALIZED",
    "EXECUTE_TESTS_GENERALIZED",
    "COLLECT_JUNIT_REPORTS_GENERALIZED",
    "FILTER_GENERALIZATIONS",
}
_STAGE_5 = {
    "COLLECT_PIT_DATA_ORIGINAL",
    "COLLECT_JACOCO_DATA_ORIGINAL",
    "RESTORE_ORIGINAL_BUILD",
    "COLLECT_JACOCO_DATA_INITIAL",
    "COLLECT_PIT_DATA_INITIAL",
    "RESTORE_GENERALIZED_BUILD",
    "COLLECT_JACOCO_DATA_GENERALIZED",
    "COLLECT_PIT_DATA_GENERALIZED",
}
STAGE_ORDER = {"1 + 2": 0, "3": 1, "4": 2, "5": 3}

FILTER_CLASS_PATTERN = r"filter\.\w+Filter$"
QUARANTINE_PRODUCER = "GeneratedTestValidator"
GATE_CODES = frozenset({"ORACLE_NOT_WIDENABLE", "INPUT_SPEC_NOT_SATISFIED_BY_SEED"})
QUARANTINE_CODES = frozenset(
    {"UNCOMPILABLE_GENERALIZED_TEST", "UNCOMPILABLE_INSTRUMENTED_WRAPPER"}
)
CAPABILITY_CODES = frozenset({"INHERITED_METHOD_NOT_FLATTENABLE"})
KNOWN_TYPED_CODES = GATE_CODES | QUARANTINE_CODES | CAPABILITY_CODES


def paper_stage(internal_stage: str) -> str | None:
    for group, members in (
        ("1 + 2", _STAGE_1_2),
        ("3", _STAGE_3),
        ("4", _STAGE_4),
        ("5", _STAGE_5),
    ):
        if internal_stage in members:
            return group
    return None


@dataclass(frozen=True)
class Attribution:
    """Structured signals for one excluded project's terminal failure."""

    internal_stage: str
    reason_code: str | None
    at_ceiling: bool
    included_tests: int
    included_assertions: int
    included_generalizations: int = 0
    assertion_exclusions_all_filtered: bool = False
    artifact_present: bool = True
    timeout_seconds: float | None = None


@dataclass(frozen=True)
class Cause:
    stage: str
    cause: str


UNCODED = Cause(stage="?", cause="UNCODED")


def _timeout_cause(seconds: float | None, subject: str) -> str:
    if seconds is None:
        return f"timeout exceeded ({subject})"
    numeric = float(seconds)
    rendered = str(int(numeric)) if numeric.is_integer() else f"{numeric:g}"
    return f"timeout exceeded ({rendered} seconds {subject})"


def classify(a: Attribution) -> Cause:
    stage = paper_stage(a.internal_stage)
    if stage is None:
        return UNCODED

    if a.reason_code == "NO_INPUT_SPEC":
        if a.included_tests == 0:
            return UNCODED
        if a.included_assertions == 0:
            if a.assertion_exclusions_all_filtered:
                return Cause(
                    "1 + 2", "all assertions excluded due to filter rejections"
                )
            return Cause(
                "3",
                "all assertions excluded due to earlier filter rejections and new failures",
            )
        return UNCODED

    if a.internal_stage == "EXECUTE_TESTS_ORIGINAL":
        if a.at_ceiling:
            return Cause(
                "1 + 2", _timeout_cause(a.timeout_seconds, "per original test suite")
            )
        return Cause("1 + 2", "JUnit execution error during test execution")
    if a.internal_stage == "BUILD_SPOON_MODEL":
        return Cause("1 + 2", "Spoon execution error during test analysis")
    if a.internal_stage == "COLLECT_JUNIT_REPORTS_ORIGINAL":
        if a.reason_code == "MISSING_REPORT_FILE":
            return Cause("1 + 2", "JUnit report directory not found")
        if a.reason_code == "UNSUPPORTED_REPORT_LAYOUT":
            return Cause("1 + 2", "unsupported JUnit report layout")
        return Cause("1 + 2", "JUnit report collection error")
    if a.internal_stage == "COLLECT_JACOCO_DATA_ORIGINAL":
        if a.artifact_present:
            return Cause("5", "JaCoCo execution error during coverage collection")
        return Cause("5", "JaCoCo outputs not found")

    if a.internal_stage in {"ADD_JPF_INSTRUMENTATION", "BUILD_PROJECT_INSTRUMENTED"}:
        return Cause("3", "Spoon execution error during test instrumentation")
    if a.internal_stage == "EXECUTE_TESTS_INITIAL":
        if a.at_ceiling:
            return Cause(
                "3", _timeout_cause(a.timeout_seconds, "per initial test suite")
            )
        return Cause("3", "JUnit execution error during initial test execution")
    if a.internal_stage == "COLLECT_JUNIT_REPORTS_INITIAL":
        return Cause("3", "JUnit reports not found")

    if a.internal_stage == "EXECUTE_TESTS_GENERALIZED" and a.at_ceiling:
        return Cause(
            "4", _timeout_cause(a.timeout_seconds, "per generalized test suite")
        )

    if (
        a.internal_stage
        in {
            "FILTER_GENERALIZATIONS",
            "GENERALIZE_TESTS",
            "COLLECT_JUNIT_REPORTS_GENERALIZED",
        }
        or (
            a.internal_stage == "BUILD_PROJECT_GENERALIZED"
            and a.reason_code == "OTHER_COMPILE_FAILURE"
        )
        or (
            a.internal_stage == "EXECUTE_TESTS_GENERALIZED"
            and a.reason_code == "LISTENER_BUG"
        )
    ):
        return UNCODED

    if a.internal_stage in {"RESTORE_ORIGINAL_BUILD", "RESTORE_GENERALIZED_BUILD"}:
        return Cause("5", "build restore failed")

    if a.internal_stage in {
        "COLLECT_JACOCO_DATA_ORIGINAL",
        "COLLECT_JACOCO_DATA_INITIAL",
        "COLLECT_JACOCO_DATA_GENERALIZED",
    }:
        if a.at_ceiling:
            return Cause(
                "5",
                _timeout_cause(a.timeout_seconds, "during JaCoCo coverage collection"),
            )
        if not a.artifact_present:
            return Cause("5", "JaCoCo outputs not found")
        return Cause("5", "JaCoCo execution error during coverage collection")
    if a.internal_stage in {
        "COLLECT_PIT_DATA_ORIGINAL",
        "COLLECT_PIT_DATA_INITIAL",
        "COLLECT_PIT_DATA_GENERALIZED",
    }:
        if a.at_ceiling:
            return Cause(
                "5", _timeout_cause(a.timeout_seconds, "during PIT mutation testing")
            )
        if a.reason_code == "PIT_MAPPING_FAILURE":
            return Cause("5", "failed to import PIT reports")
        if a.reason_code == "PIT_REPORT_PERSISTENCE_FAILURE":
            return Cause("5", "failed to persist PIT coverage reports")
        # Diagnosed command failures come from the captured Maven output. Each cause
        # names the failed operation instead of using the former catch-all.
        if a.reason_code == "MINION_DIED":
            return Cause("5", "PIT coverage minion exited abnormally")
        if a.reason_code == "PLUGIN_UNUSABLE":
            return Cause("5", "PIT plugin version cannot run")
        if a.reason_code == "SUITE_NOT_GREEN":
            return Cause("5", "unmutated test suite has failing tests")
        if a.reason_code == "NO_TESTS_FOUND":
            return Cause("5", "PIT found no tests to mutate")
        if a.reason_code == "LISTENER_BUG":
            return Cause("5", "PIT execution error during mutation testing")
        if not a.artifact_present:
            return Cause("5", "PIT reports not found")
        return Cause("5", "PIT execution error during mutation testing")

    return UNCODED
