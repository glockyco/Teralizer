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
    type: str  # "Internal" | "External" | "Mixed"


UNCODED = Cause(stage="?", cause="UNCODED", type="?")


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
            return Cause(
                "1 + 2",
                "all tests excluded due to filter rejections and failures",
                "Mixed",
            )
        if a.included_assertions == 0:
            if a.assertion_exclusions_all_filtered:
                return Cause(
                    "1 + 2", "all assertions excluded due to filter rejections", "Mixed"
                )
            return Cause(
                "3",
                "all assertions excluded due to earlier filter rejections and new failures",
                "Mixed",
            )
        return UNCODED

    if a.internal_stage == "EXECUTE_TESTS_ORIGINAL":
        if a.at_ceiling:
            return Cause(
                "1 + 2",
                _timeout_cause(a.timeout_seconds, "per original test suite"),
                "Internal",
            )
        return Cause("1 + 2", "JUnit execution error during test execution", "External")
    if a.internal_stage == "BUILD_SPOON_MODEL":
        return Cause("1 + 2", "Spoon execution error during test analysis", "External")
    if a.internal_stage == "COLLECT_JUNIT_REPORTS_ORIGINAL":
        return Cause("1 + 2", "JUnit reports not found", "Internal")
    if a.internal_stage == "COLLECT_JACOCO_DATA_ORIGINAL":
        if a.artifact_present:
            return Cause(
                "5",
                "JaCoCo execution error during coverage collection",
                "External",
            )
        return Cause("5", "JaCoCo outputs not found", "Internal")

    if a.internal_stage in {"ADD_JPF_INSTRUMENTATION", "BUILD_PROJECT_INSTRUMENTED"}:
        return Cause(
            "3", "Spoon execution error during test instrumentation", "External"
        )
    if a.internal_stage == "EXECUTE_TESTS_INITIAL":
        if a.at_ceiling:
            return Cause(
                "3",
                _timeout_cause(a.timeout_seconds, "per initial test suite"),
                "Internal",
            )
        return Cause(
            "3", "JUnit execution error during initial test execution", "External"
        )
    if a.internal_stage == "COLLECT_JUNIT_REPORTS_INITIAL":
        return Cause("3", "JUnit reports not found", "Internal")

    if a.internal_stage == "EXECUTE_TESTS_GENERALIZED" and a.at_ceiling:
        return Cause(
            "4",
            _timeout_cause(a.timeout_seconds, "per generalized test suite"),
            "Internal",
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
        return Cause(
            "4",
            "all generalizations excluded due to filter rejections and failures",
            "Internal",
        )

    if a.internal_stage in {"RESTORE_ORIGINAL_BUILD", "RESTORE_GENERALIZED_BUILD"}:
        return Cause("5", "build restore failed", "Internal")

    if a.internal_stage in {
        "COLLECT_JACOCO_DATA_ORIGINAL",
        "COLLECT_JACOCO_DATA_INITIAL",
        "COLLECT_JACOCO_DATA_GENERALIZED",
    }:
        if a.at_ceiling:
            return Cause(
                "5",
                _timeout_cause(a.timeout_seconds, "during JaCoCo coverage collection"),
                "Internal",
            )
        if not a.artifact_present:
            return Cause("5", "JaCoCo outputs not found", "Internal")
        return Cause(
            "5", "JaCoCo execution error during coverage collection", "External"
        )
    if a.internal_stage in {
        "COLLECT_PIT_DATA_ORIGINAL",
        "COLLECT_PIT_DATA_INITIAL",
        "COLLECT_PIT_DATA_GENERALIZED",
    }:
        if a.at_ceiling:
            return Cause(
                "5",
                _timeout_cause(a.timeout_seconds, "during PIT mutation testing"),
                "Internal",
            )
        if a.reason_code in {"PIT_MAPPING_FAILURE"}:
            return Cause("5", "failed to process PIT reports", "Internal")
        if a.reason_code == "LISTENER_BUG":
            return Cause("5", "PIT execution error during mutation testing", "External")
        if not a.artifact_present:
            return Cause("5", "PIT reports not found", "Internal")
        return Cause("5", "PIT execution error during mutation testing", "External")

    return UNCODED
