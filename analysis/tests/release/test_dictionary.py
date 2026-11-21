from pathlib import Path

import pytest
import sqlalchemy.exc
from teralizer.release import dictionary
from teralizer.release.dictionary import Column, Entry, Relation

_REPO = Path(__file__).resolve().parents[3]


def _relations() -> dict[str, Relation]:
    return {
        "project": Relation(
            "project",
            "table",
            ["controlled", "real-world"],
            {
                "id": Column("id", ["bigint"], False, ["controlled", "real-world"]),
                "runtime": Column("runtime", ["real"], True, ["real-world"]),
            },
        )
    }


def test_described_columns_must_be_exactly_the_corpus_columns():
    entries = [
        Entry("project", "One project.", {"id": "Primary key.", "renamed": "Old."}),
        Entry("v_gone", "A dropped view.", {}),
    ]

    assert dictionary.problems(entries, _relations()) == [
        "project.runtime is not described",
        "project.renamed is in no corpus",
        "v_gone is in no corpus",
    ]


def test_every_table_needs_a_description():
    assert dictionary.problems([], _relations()) == ["table project is not described"]


def test_a_column_missing_from_some_corpora_names_the_others():
    entries = [
        Entry("project", "One project.", {"id": "Primary key.", "runtime": "Seconds."})
    ]

    rendered = dictionary.render(
        entries, _relations(), ["controlled", "real-world"], [], {}
    )

    assert "| `runtime` | real | yes | Seconds. Only in real-world. |" in rendered
    assert "| `id` | bigint | no | Primary key. |" in rendered


def test_described_result_columns_must_be_exactly_the_csv_header():
    files = {
        "tab-runtime": dictionary.ResultFile(
            "tab-runtime", "rq3", "build/rq3/tab-runtime.csv", ("project", "hours")
        )
    }
    entries = [Entry("tab-runtime", "Runtimes.", {"project": "Project.", "min": "x"})]

    assert dictionary.result_problems(entries, files) == [
        "result tab-runtime.csv column hours is not described",
        "result tab-runtime.csv has no column min",
    ]
    assert dictionary.result_problems([], files) == [
        "result tab-runtime.csv is not described"
    ]


@pytest.mark.db
def test_committed_dictionary_matches_the_corpora():
    try:
        rendered, problems = dictionary.current(_REPO)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    assert problems == []
    assert (_REPO / dictionary.DEFAULT_RENDERED).read_text() == rendered
