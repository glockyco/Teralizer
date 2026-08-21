"""Tests for corpus inventory and completeness modes."""

from __future__ import annotations

from teralizer import corpora, corpus_verification


def test_requested_subset_accepts_a_valid_partial_installation():
    registry = corpora.load()

    report = corpus_verification.verify(
        registry,
        ("postgres_dev",),
        required=("controlled",),
        entry_verifier=lambda entry: entry.expected_projects,
    )

    assert report.ok
    assert report.missing == ()
    assert report.verified == ((registry.get("controlled"), 13),)


def test_requested_subset_names_a_missing_corpus_database():
    registry = corpora.load()

    report = corpus_verification.verify(
        registry,
        (),
        required=("controlled",),
        entry_verifier=lambda entry: entry.expected_projects,
    )

    assert not report.ok
    assert report.missing == (registry.get("controlled"),)
    assert report.errors == ("corpus 'controlled' is missing database 'postgres_dev'",)


def test_published_mode_requires_and_verifies_every_published_corpus():
    registry = corpora.load()
    observed = tuple(entry.database for entry in registry.published_entries)

    report = corpus_verification.verify(
        registry,
        observed,
        published=True,
        entry_verifier=lambda entry: entry.expected_projects,
    )

    assert report.ok
    assert report.missing == ()
    assert (
        tuple(entry for entry, _count in report.verified) == registry.published_entries
    )


def test_published_mode_requires_disposition_for_unclassified_database():
    registry = corpora.load()
    observed = tuple(entry.database for entry in registry.published_entries) + (
        "postgres_unknown",
    )

    report = corpus_verification.verify(
        registry,
        observed,
        published=True,
        entry_verifier=lambda entry: entry.expected_projects,
    )

    assert report.errors == (
        "database 'postgres_unknown' is unclassified and requires an explicit "
        "retain, dump, or drop disposition",
    )


def test_verification_preserves_project_count_mismatch_diagnostic():
    registry = corpora.load()

    def mismatch(_entry: corpora.CorpusEntry) -> int:
        raise RuntimeError(
            "corpus 'controlled' expects 13 projects in 'postgres_dev'; observed 12"
        )

    report = corpus_verification.verify(
        registry,
        ("postgres_dev",),
        required=("controlled",),
        entry_verifier=mismatch,
    )

    assert report.errors == (
        "corpus 'controlled' failed verification: corpus 'controlled' expects 13 "
        "projects in 'postgres_dev'; observed 12",
    )


def test_inventory_reports_missing_scratch_and_unclassified_without_failing():
    registry = corpora.load()

    report = corpus_verification.verify(
        registry,
        ("postgres_dev", "scratch_verification", "postgres_test"),
    )

    assert report.ok
    assert tuple(entry.id for entry in report.missing) == (
        "real-world",
        "jarvis-benchmark",
        "jarvis-scenarios",
    )
    assert tuple(
        (item.database, item.kind, item.corpus_id) for item in report.classifications
    ) == (
        ("postgres_dev", corpora.DatabaseKind.CORPUS, "controlled"),
        ("postgres_test", corpora.DatabaseKind.UNCLASSIFIED, None),
        ("scratch_verification", corpora.DatabaseKind.SCRATCH, None),
    )
