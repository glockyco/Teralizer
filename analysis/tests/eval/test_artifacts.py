from pathlib import Path

import pytest

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
    RunAggregate,
)


def artifact(root: Path, target: RenderTarget, key: str, owner: str | RunAggregate):
    path = root / target.value / key
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(key)
    return RenderedArtifact(ArtifactId(target, key), path, owner)


def test_same_key_in_different_targets_coexists(tmp_path):
    artifacts = ArtifactSet(tmp_path)
    artifacts.add(artifact(tmp_path, RenderTarget.LATEX, "results", "rq1"))
    artifacts.add(artifact(tmp_path, RenderTarget.CSV, "results", "rq1"))
    assert len(artifacts) == 2


def test_duplicate_identity_names_both_owners(tmp_path):
    artifacts = ArtifactSet(tmp_path)
    artifacts.add(artifact(tmp_path, RenderTarget.LATEX, "results", "rq1"))
    with pytest.raises(ValueError, match="both rq1 and rq2"):
        artifacts.add(artifact(tmp_path, RenderTarget.LATEX, "results", "rq2"))


def test_merge_preserves_identity_invariant(tmp_path):
    left = ArtifactSet(tmp_path)
    right = ArtifactSet(tmp_path)
    left.add(artifact(tmp_path, RenderTarget.CSV, "results", "rq1"))
    right.add(artifact(tmp_path, RenderTarget.CSV, "results", "rq2"))
    with pytest.raises(ValueError, match="both rq1 and rq2"):
        left.merge(right)


def test_artifact_path_must_stay_beneath_root(tmp_path):
    artifacts = ArtifactSet(tmp_path / "stage")
    with pytest.raises(ValueError, match="escapes staging root"):
        artifacts.add(
            RenderedArtifact(
                ArtifactId(RenderTarget.MANIFEST, "provenance"),
                tmp_path / "provenance.json",
                RunAggregate.RUN,
            )
        )


def test_artifact_requires_key_and_owner(tmp_path):
    with pytest.raises(ValueError, match="key must not be empty"):
        ArtifactId(RenderTarget.CSV, "")
    with pytest.raises(ValueError, match="owner must not be empty"):
        RenderedArtifact(
            ArtifactId(RenderTarget.CSV, "results"), tmp_path / "results.csv", ""
        )
