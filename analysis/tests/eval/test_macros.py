import pytest
from teralizer.eval.macros import macro_name


@pytest.mark.parametrize(
    "key,expected",
    [
        ("realworld.eligible_projects_pct", "TzRealworldEligibleProjectsPct"),
        ("rq6.foo", "TzRqsixFoo"),  # digits spelled out (LaTeX-legal)
        ("controlled.mutation_score", "TzControlledMutationScore"),
    ],
)
def test_macro_name(key, expected):
    assert macro_name(key) == expected


def test_macro_name_is_letters_only():
    name = macro_name("a1.b2c3")
    assert name.isalpha(), f"{name} must be letters-only for \\newcommand"


def test_macro_name_rejects_empty():
    with pytest.raises(ValueError):
        macro_name("")
