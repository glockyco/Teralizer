import hashlib
import subprocess
from pathlib import Path

from teralizer.eval.inputs import CorpusInputSpec, FileInputSpec
from teralizer.eval.registry import ReportSpec
from teralizer.release import inventory, report_inputs


def _git(repo: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)


def _inventory(*report_input_paths: str) -> inventory.Inventory:
    return inventory.parse(
        {
            "expand": [],
            "input": [
                {
                    "id": f"input-{component}",
                    "location": "corpus-package",
                    "path": component.removeprefix("corpus-"),
                    "disposition": "payload",
                    "component": component,
                    "reason": "Fixture payload.",
                }
                for component in inventory.COMPONENTS
            ],
            "report_input": [
                {
                    "path": path,
                    "status": "frozen",
                    "producer": "manual",
                    "needs": ["core"],
                    "reason": "Fixture.",
                }
                for path in report_input_paths
            ],
        }
    )


def _spec(*inputs: CorpusInputSpec | FileInputSpec) -> ReportSpec:
    return ReportSpec(lambda context: None, inputs)  # type: ignore[arg-type, return-value]


def _repo(tmp_path: Path) -> Path:
    _git(tmp_path, "init", "-q")
    (tmp_path / "facts.json").write_text("{}\n")
    (tmp_path / "loose.json").write_text("{}\n")
    _git(tmp_path, "add", "facts.json")
    _git(tmp_path, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init")
    return tmp_path


def test_committed_declared_file_resolves_to_core_with_its_checksum(tmp_path: Path):
    repo = _repo(tmp_path)
    reports = {
        "rq": _spec(
            CorpusInputSpec("main", "controlled"), FileInputSpec("facts", "facts.json")
        )
    }

    resolved, problems = report_inputs.resolve(_inventory("facts.json"), reports, repo)

    assert problems == []
    assert [(item.kind, item.source, item.owner) for item in resolved] == [
        ("corpus", "controlled", "corpus-controlled"),
        ("file", "facts.json", "core"),
    ]
    assert resolved[1].sha256 == hashlib.sha256(b"{}\n").hexdigest()


def test_unowned_inputs_and_stale_declarations_are_reported(tmp_path: Path):
    repo = _repo(tmp_path)
    reports = {
        "rq": _spec(
            CorpusInputSpec("other", "unregistered"),
            FileInputSpec("undeclared", "facts.json"),
            FileInputSpec("checkout-only", "loose.json"),
        )
    }

    _, problems = report_inputs.resolve(
        _inventory("loose.json", "retired.json"), reports, repo
    )

    assert problems == [
        "rq other: corpus unregistered has no corpus component",
        "rq undeclared: facts.json has no report-input declaration",
        "rq checkout-only: loose.json is not committed, so no component owns it",
        "report-input declaration retired.json is not read by any report",
    ]
