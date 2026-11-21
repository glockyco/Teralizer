from pathlib import Path
from typing import Any, cast

import pytest
from teralizer.release import build


def _build(tmp_path: Path, candidate: Path) -> None:
    unused = cast(Any, None)
    build.build_candidate(
        unused,
        unused,
        repo=tmp_path / "repo",
        evidence=tmp_path / "evidence",
        corpus_package=tmp_path / "package",
        candidate=candidate,
        components=["core"],
    )


def test_an_existing_candidate_is_never_reused(tmp_path: Path):
    candidate = tmp_path / "dev-1"
    (candidate / "teralizer-replication-core.zip").parent.mkdir(parents=True)
    (candidate / "teralizer-replication-core.zip").write_bytes(b"old")

    with pytest.raises(build.BuildError, match="already exists"):
        _build(tmp_path, candidate)
    assert (candidate / "teralizer-replication-core.zip").read_bytes() == b"old"


def test_an_interrupted_candidate_must_be_removed_first(tmp_path: Path):
    candidate = tmp_path / "dev-1"
    partial = tmp_path / "dev-1.partial"
    partial.mkdir()

    with pytest.raises(build.BuildError, match="remove the failed candidate"):
        _build(tmp_path, candidate)
    assert not candidate.exists()
