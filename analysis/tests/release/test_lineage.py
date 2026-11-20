import hashlib
from pathlib import Path

from teralizer.release import inventory, lineage


def _inventory(*extra: dict[str, object]) -> inventory.Inventory:
    payloads = [
        {
            "id": f"input-{component}",
            "location": "corpus-package",
            "path": component.removeprefix("corpus-"),
            "disposition": "payload",
            "component": component,
            "reason": "Fixture payload.",
        }
        for component in inventory.COMPONENTS
    ]
    return inventory.parse({"expand": [], "input": [*payloads, *extra]})


def _staged_input(
    input_id: str, release_path: str, source_id: str
) -> dict[str, object]:
    return {
        "id": input_id,
        "location": "evaluation-host",
        "path": f"host/{release_path}",
        "disposition": "payload",
        "component": "core",
        "release_path": release_path,
        "reconstruction_source": source_id,
        "reason": "Fixture evidence.",
    }


def _sources(status_sha: str) -> dict[str, dict[str, object]]:
    return {
        "run-status": {"kind": "file", "sha256": status_sha, "paths": ["."]},
        "checkouts": {
            "kind": "virtual-revision-map",
            "sha256": "0" * 64,
            "paths": ["github_com_a_one", "github_com_b_two"],
        },
    }


def test_each_collected_source_needs_exactly_one_input():
    declared = _inventory(_staged_input("status", "status.tsv", "run-status"))

    problems = lineage.closure(declared, _sources("0" * 64))

    assert problems == [
        "reconstruction source checkouts: needs exactly one inventory input, has []"
    ]


def test_staged_evidence_must_reproduce_recorded_identities(tmp_path: Path):
    (tmp_path / "status.tsv").write_text("project\texit\n")
    (tmp_path / "checkouts.tsv").write_text(
        "project\trevision\torigin\n"
        "github_com_a_one\t" + "a" * 40 + "\thttps://github.com/a/one\n"
    )
    declared = _inventory(
        _staged_input("status", "status.tsv", "run-status"),
        _staged_input("map", "checkouts.tsv", "checkouts"),
    )
    recorded = hashlib.sha256(b"project\texit\n").hexdigest()

    assert lineage.verify_staged(declared, _sources(recorded), tmp_path) == [
        "checkouts: staged map does not list each of the 2 recorded projects once"
    ]
    changed = lineage.verify_staged(declared, _sources("f" * 64), tmp_path)
    assert changed[0] == (
        f"run-status: staged status.tsv has digest {recorded}, recorded {'f' * 64}"
    )


def test_corpus_revisions_must_match_the_staged_checkout_map():
    rows = [
        {"project": "github_com_a_one", "revision": "a" * 40, "origin": "x"},
        {"project": "github_com_b_two", "revision": "b" * 40, "origin": "y"},
    ]
    corpus = [
        ("projects/github_com_a_one", "a" * 40),
        ("projects/github_com_b_two", "c" * 40),
        ("projects/github_com_c_three", None),
    ]

    assert lineage.corpus_revision_mismatches(rows, corpus) == [
        f"github_com_b_two: corpus revision {'c' * 40}, staged {'b' * 40}"
    ]
