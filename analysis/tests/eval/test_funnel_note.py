"""The funnel note writes each rate next to the count that it describes.

The rate is the share of entering projects that a stage includes. A stage that keeps 182 of 611
projects includes 29.8% and excludes 70.2%, so the two rates are not interchangeable.
"""

from teralizer.eval.reports._funnel import StageBand, _funnel_note


def note_for(stage="1 + 2", entering=611, passing=182):
    band = StageBand(
        stage=stage, entering=entering, exclusions=entering - passing, passing=passing
    )
    return _funnel_note(eligible=entering, stages=[band], success_count=passing)


def test_the_rate_follows_the_included_count():
    assert "182 included (29.8%), 429 excluded." in note_for()


def test_the_rate_is_not_attached_to_the_excluded_count():
    assert "429 excluded (29.8%)" not in note_for()


def test_a_stage_that_keeps_almost_everything_reads_that_way():
    assert "176 included (96.7%), 6 excluded." in note_for(
        stage="3", entering=182, passing=176
    )


def test_the_overall_line_states_the_inclusion_rate():
    assert note_for(entering=611, passing=85).endswith(
        "Overall: 85 of 611 included (13.9%)."
    )


def test_an_empty_stage_does_not_divide_by_zero():
    assert "0 included (0.0%), 0 excluded." in note_for(entering=0, passing=0)
