import hashlib
import json
from pathlib import Path

from teralizer.release import preflight

_RELEASE = {
    "name": "teralizer-replication",
    "version": "2.0.0",
    "version_doi": "10.5281/zenodo.1",
    "concept_doi": "10.5281/zenodo.0",
    "source_revision": "a" * 40,
}


def _component(
    workspace: Path, component: str, files: dict[str, bytes], release=None
) -> str:
    root = "teralizer" if component == "core" else f"teralizer/components/{component}"
    payload = []
    for name, content in files.items():
        path = workspace / root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        payload.append(
            {
                "path": f"{root}/{name}",
                "sha256": hashlib.sha256(content).hexdigest(),
                "bytes": len(content),
                "executable": False,
            }
        )
    manifest = {
        "format": "teralizer-component-manifest",
        "format_version": 1,
        "component": component,
        "release": release or _RELEASE,
        "root": root,
        "purpose": "Fixture.",
        "requires": [] if component == "core" else ["core"],
        "incompatible_with": [],
        "licenses": [],
        "provenance": {},
        "payload": payload,
    }
    content = json.dumps(manifest).encode()
    (workspace / root / "component-manifest.json").write_bytes(content)
    return hashlib.sha256(content).hexdigest()


def _workspace(tmp_path: Path) -> tuple[Path, Path]:
    workspace = tmp_path / "release"
    digests = {
        "core": _component(workspace, "core", {"README.md": b"core\n"}),
        "corpus-controlled": _component(
            workspace, "corpus-controlled", {"controlled.dump": b"dump"}
        ),
    }
    manifest = {
        "format": "teralizer-release-manifest",
        "format_version": 1,
        "release": _RELEASE,
        "paper": {"title": "T", "authors": ["A"], "doi": "10.1/x"},
        "archives": [
            {
                "component": component,
                "filename": f"teralizer-replication-{component}.zip",
                "sha256": "0" * 64,
                "bytes": 1,
                "unpacked_bytes": 1,
                "purpose": "Fixture.",
                "requires": [],
                "component_manifest_sha256": digest,
            }
            for component, digest in digests.items()
        ],
        "workflows": [
            {"id": "inspect", "command": "artifact preflight", "components": ["core"]},
            {
                "id": "reproduce",
                "command": "artifact reproduce-results",
                "components": ["core", "corpus-controlled"],
            },
        ],
    }
    release_manifest = workspace / "release-manifest.json"
    release_manifest.write_text(json.dumps(manifest))
    return workspace, release_manifest


def test_complete_workflow_components_pass(tmp_path: Path):
    workspace, manifest = _workspace(tmp_path)

    result = preflight.run(workspace, manifest, "reproduce")

    assert result.passed
    assert result.components == ("core", "corpus-controlled")


def test_every_problem_is_reported_together(tmp_path: Path):
    workspace, manifest = _workspace(tmp_path)
    (workspace / "teralizer/README.md").write_text("edited\n")
    dump = workspace / "teralizer/components/corpus-controlled/controlled.dump"
    dump.unlink()

    problems = preflight.run(workspace, manifest, "reproduce").problems

    assert len(problems) == 2
    assert problems[0].startswith("core: 1 payload files changed")
    assert problems[1].startswith("corpus-controlled: 1 payload files are missing")
    assert "extract it again with unzip" in problems[1]


def test_component_from_another_release_is_rejected(tmp_path: Path):
    workspace, manifest = _workspace(tmp_path)
    other = dict(_RELEASE, version_doi="10.5281/zenodo.2")
    _component(workspace, "corpus-controlled", {"controlled.dump": b"dump"}, other)

    problems = list(preflight.run(workspace, manifest, "reproduce").problems)

    assert problems == [
        "corpus-controlled: component manifest differs from this release; "
        "remove teralizer/components/corpus-controlled/, check "
        "teralizer-replication-corpus-controlled.zip against checksums.sha256, "
        "and extract it again with unzip"
    ]


def test_unextracted_component_and_unknown_workflow(tmp_path: Path):
    workspace, manifest = _workspace(tmp_path)
    (
        workspace / "teralizer/components/corpus-controlled/component-manifest.json"
    ).unlink()

    assert preflight.run(workspace, manifest, "inspect").passed
    assert (
        "not extracted" in preflight.run(workspace, manifest, "reproduce").problems[0]
    )
    assert (
        "unknown workflow" in preflight.run(workspace, manifest, "collect").problems[0]
    )
