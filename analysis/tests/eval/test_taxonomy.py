from teralizer.eval.reports import _taxonomy
from teralizer.eval.reports._taxonomy import (
    UNCODED,
    Attribution,
    STAGE_ORDER,
    classify,
    paper_stage,
)


def test_paper_stage_grouping():
    assert paper_stage("SETUP_PROJECT") == "1 + 2"
    assert paper_stage("EXECUTE_JPF") == "3"
    assert paper_stage("FILTER_GENERALIZATIONS") == "4"
    assert paper_stage("COLLECT_PIT_DATA_GENERALIZED") == "5"
    assert STAGE_ORDER["1 + 2"] < STAGE_ORDER["3"] < STAGE_ORDER["4"] < STAGE_ORDER["5"]


def test_execute_tests_original_timeout_vs_error():
    timeout = Attribution(
        "EXECUTE_TESTS_ORIGINAL",
        None,
        at_ceiling=True,
        included_tests=5,
        included_assertions=5,
    )
    err = Attribution(
        "EXECUTE_TESTS_ORIGINAL",
        None,
        at_ceiling=False,
        included_tests=5,
        included_assertions=5,
    )
    assert "timeout" in classify(timeout).cause
    assert "JUnit" in classify(err).cause


def test_timeout_label_uses_observed_budget():
    original = Attribution(
        "EXECUTE_TESTS_ORIGINAL",
        "EXECUTION_TIMEOUT",
        at_ceiling=True,
        timeout_seconds=300.0,
        included_tests=5,
        included_assertions=5,
    )
    generalized = Attribution(
        "EXECUTE_TESTS_GENERALIZED",
        "SUITE_TIMEOUT",
        at_ceiling=True,
        timeout_seconds=1800.0,
        included_tests=5,
        included_assertions=5,
    )
    pit = Attribution(
        "COLLECT_PIT_DATA_INITIAL",
        "EXECUTION_TIMEOUT",
        at_ceiling=True,
        timeout_seconds=3600.0,
        included_tests=5,
        included_assertions=5,
    )
    assert "300 seconds" in classify(original).cause
    assert "1800 seconds" in classify(generalized).cause
    assert "3600 seconds" in classify(pit).cause


def test_no_input_spec_requires_entity_evidence():
    for included_tests in (0, 4):
        attribution = Attribution(
            "ANALYZE_JPF",
            "NO_INPUT_SPEC",
            at_ceiling=False,
            included_tests=included_tests,
            included_assertions=0,
        )
        assert classify(attribution) is UNCODED


def test_spoon_error_names_failed_operation():
    a = Attribution(
        "BUILD_SPOON_MODEL",
        None,
        at_ceiling=False,
        included_tests=0,
        included_assertions=0,
    )
    assert "Spoon" in classify(a).cause


def test_unknown_signal_is_uncoded():
    a = Attribution(
        "SOME_FUTURE_STAGE",
        "NEW_CODE",
        at_ceiling=False,
        included_tests=1,
        included_assertions=1,
    )
    assert classify(a) is UNCODED


def test_stage4_terminal_failures_require_entity_mechanisms():
    cases = [
        Attribution(
            "BUILD_PROJECT_GENERALIZED",
            "OTHER_COMPILE_FAILURE",
            at_ceiling=False,
            included_tests=213,
            included_assertions=5,
            included_generalizations=4,
        ),
        Attribution(
            "EXECUTE_TESTS_GENERALIZED",
            "LISTENER_BUG",
            at_ceiling=False,
            included_tests=70,
            included_assertions=11,
            included_generalizations=1,
        ),
        Attribution(
            "GENERALIZE_TESTS",
            None,
            at_ceiling=False,
            included_tests=0,
            included_assertions=0,
            included_generalizations=0,
        ),
        Attribution(
            "FILTER_GENERALIZATIONS",
            None,
            at_ceiling=False,
            included_tests=5,
            included_assertions=5,
            included_generalizations=0,
        ),
    ]
    for attribution in cases:
        assert classify(attribution) is UNCODED, attribution


def test_unenumerated_stage4_signal_is_uncoded():
    a = Attribution(
        "BUILD_PROJECT_GENERALIZED",
        "SOME_NEW_CODE",
        at_ceiling=False,
        included_tests=5,
        included_assertions=5,
        included_generalizations=1,
    )
    assert classify(a) is UNCODED


def test_collect_jacoco_original_not_found_vs_error():
    not_found = Attribution(
        "COLLECT_JACOCO_DATA_ORIGINAL",
        None,
        at_ceiling=False,
        included_tests=1,
        included_assertions=1,
        artifact_present=False,
    )
    err = Attribution(
        "COLLECT_JACOCO_DATA_ORIGINAL",
        None,
        at_ceiling=False,
        included_tests=1,
        included_assertions=1,
        artifact_present=True,
    )
    nf = classify(not_found)
    assert nf.stage == "5"
    assert "JaCoCo" in nf.cause and "not found" in nf.cause
    e = classify(err)
    assert e.stage == "5"
    assert "JaCoCo" in e.cause


def test_pit_report_persistence_failure_names_failed_operation():
    cause = classify(
        Attribution(
            "COLLECT_PIT_DATA_INITIAL",
            "PIT_REPORT_PERSISTENCE_FAILURE",
            at_ceiling=False,
            included_tests=1,
            included_assertions=1,
            artifact_present=True,
        )
    )
    assert cause.cause == "failed to persist PIT reports for the initial test suite"


def test_pit_listener_failure_is_execution_error():
    cause = classify(
        Attribution(
            "COLLECT_PIT_DATA_GENERALIZED",
            "LISTENER_BUG",
            at_ceiling=False,
            included_tests=1,
            included_assertions=1,
            artifact_present=False,
        )
    )
    assert "PIT execution error" in cause.cause


def test_restore_original_build_is_stage5():
    a = Attribution(
        "RESTORE_ORIGINAL_BUILD",
        None,
        at_ceiling=False,
        included_tests=1,
        included_assertions=1,
    )
    c = classify(a)
    assert c.stage == "5"
    assert "build" in c.cause.lower()


def test_build_project_instrumented_names_compilation():
    attribution = Attribution(
        "BUILD_PROJECT_INSTRUMENTED",
        "OTHER_COMPILE_FAILURE",
        at_ceiling=False,
        included_tests=1,
        included_assertions=1,
    )
    cause = classify(attribution)
    assert cause.stage == "3"
    assert cause.cause == "instrumented project compilation failed"


def test_collect_junit_reports_preserves_diagnosed_cause():
    expected = {
        "MISSING_REPORT_FILE": "JUnit report directory not found",
        "UNSUPPORTED_REPORT_LAYOUT": "unsupported JUnit report layout",
        None: "JUnit report collection error",
    }
    for reason_code, description in expected.items():
        attribution = Attribution(
            "COLLECT_JUNIT_REPORTS_ORIGINAL",
            reason_code,
            at_ceiling=False,
            included_tests=1,
            included_assertions=1,
        )
        cause = classify(attribution)
        assert cause.stage == "1 + 2"
        assert cause.cause == description


def test_diagnosed_reduction_command_failures_get_named_causes():
    cases = {
        "MINION_DIED": "PIT coverage minion exited for the generalized test suite",
        "PLUGIN_UNUSABLE": "PIT plugin cannot run for the generalized test suite",
        "SUITE_NOT_GREEN": "generalized test suite has failing tests before mutation",
        "NO_TESTS_FOUND": "PIT found no tests in the generalized test suite",
    }
    for reason_code, expected in cases.items():
        cause = _taxonomy.classify(
            _taxonomy.Attribution(
                internal_stage="COLLECT_PIT_DATA_GENERALIZED",
                reason_code=reason_code,
                at_ceiling=False,
                included_tests=5,
                included_assertions=5,
                included_generalizations=1,
            )
        )
        assert cause.stage == "5"
        assert cause.cause == expected
