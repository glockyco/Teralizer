import pytest

from teralizer.eval.reports import _diagnostics


@pytest.mark.parametrize(
    ("detail", "expected"),
    [
        (
            {
                "message": "gov.nasa.jpf.vm.NoUncaughtExceptionsProperty\n"
                "java.lang.NullPointerException: application state"
            },
            "Application exception",
        ),
        (
            {
                "message": "Caused by: java.lang.IllegalStateException: peer failed\n"
                "at gov.nasa.jpf.vm.JPF_java_lang_Class"
            },
            "JPF native-peer gap",
        ),
        (
            '{"message":"Caused by: java.lang.NoSuchFieldException: value"}',
            "JPF model/field gap",
        ),
        ({"message": "no exception type retained"}, "Unparsed"),
    ],
)
def test_retained_jpf_details_recover_concrete_causes(detail, expected):
    assert _diagnostics._classify_jpf_exception_detail(detail) == expected


@pytest.mark.parametrize(
    ("detail", "expected"),
    [
        (None, "Candidate detail unavailable"),
        ("not JSON", "Candidate detail unavailable"),
        ([], "Candidate detail unavailable"),
        ([{"callSource": "subject.size()"}], "Choice-invariant"),
        (
            [{"callSource": "subject.contains(new Interval(1, 10), 5)"}],
            "Choice-dependent",
        ),
        ([{"callSource": "subject.accept(factory.create())"}], "Choice-dependent"),
    ],
)
def test_mut_candidate_details_preserve_choice_sensitivity(detail, expected):
    assert _diagnostics._classify_mut_candidate_details(detail) == expected
