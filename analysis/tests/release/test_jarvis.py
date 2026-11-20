import json
from pathlib import Path

from teralizer.eval.evidence.jarvis_values import file_set_identity
from teralizer.release import jarvis

_SELECTION = {
    "scoreboard_value_logs": ["data/s/1.values.tsv"],
    "census_value_logs": ["data/c/1.values.tsv", "data/c/2.values.tsv"],
}


def _stage(root: Path) -> None:
    for paths in _SELECTION.values():
        for relative in paths:
            (root / relative).parent.mkdir(parents=True, exist_ok=True)
            (root / relative).write_text(f"value\t{relative}\n")


def _facts(root: Path, recorded_from: Path) -> Path:
    sources = {
        key: file_set_identity(recorded_from / path for path in paths)
        for key, paths in _SELECTION.items()
    }
    facts = root / "facts.json"
    facts.write_text(json.dumps({"sources": sources}))
    return facts


def test_staged_logs_reproduce_the_recorded_identity(tmp_path: Path):
    checkout, staged = tmp_path / "checkout", tmp_path / "staged"
    _stage(checkout)
    facts = _facts(tmp_path, checkout)

    copied = jarvis.stage(_SELECTION, checkout, staged)

    assert copied == 3
    assert jarvis.verify(_SELECTION, staged, facts) == []


def test_changed_or_missing_logs_fail(tmp_path: Path):
    staged = tmp_path / "staged"
    _stage(staged)
    facts = _facts(tmp_path, staged)
    (staged / "data/s/1.values.tsv").write_text("changed\n")
    (staged / "data/c/2.values.tsv").unlink()

    problems = jarvis.verify(_SELECTION, staged, facts)

    assert problems[0].startswith("scoreboard_value_logs: staged logs")
    assert problems[1] == "census_value_logs: 1 selected logs are not staged"
