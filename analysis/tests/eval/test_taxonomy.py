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
    assert classify(timeout).type == "Internal"
    assert "timeout" in classify(timeout).cause
    assert classify(err).type == "External"
    assert "JUnit" in classify(err).cause


def test_no_input_spec_reattributes_upstream():
    all_tests = Attribution(
        "ANALYZE_JPF",
        "NO_INPUT_SPEC",
        at_ceiling=False,
        included_tests=0,
        included_assertions=0,
    )
    all_asserts = Attribution(
        "ANALYZE_JPF",
        "NO_INPUT_SPEC",
        at_ceiling=False,
        included_tests=4,
        included_assertions=0,
        assertion_exclusions_all_filtered=True,
    )
    assert classify(all_tests).stage == "1 + 2"
    assert "all tests excluded" in classify(all_tests).cause
    assert classify(all_asserts).stage == "1 + 2"
    assert "all assertions excluded" in classify(all_asserts).cause
    assert classify(all_asserts).type == "Mixed"


def test_spoon_error_external():
    a = Attribution(
        "BUILD_SPOON_MODEL",
        None,
        at_ceiling=False,
        included_tests=0,
        included_assertions=0,
    )
    assert classify(a).type == "External"
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


def test_no_input_spec_stage3_new_failures():
    a = Attribution(
        "ANALYZE_JPF",
        "NO_INPUT_SPEC",
        at_ceiling=False,
        included_tests=4,
        included_assertions=0,
        assertion_exclusions_all_filtered=False,
    )
    c = classify(a)
    assert c.stage == "3"
    assert "new failures" in c.cause
    assert c.type == "Mixed"


def test_stage4_terminal_failures_map_to_single_cause():
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
    for a in cases:
        c = classify(a)
        assert c.stage == "4", a
        assert c.type == "Internal", a
        assert "all generalizations excluded" in c.cause, a


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
