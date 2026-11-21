import json
from pathlib import Path

from teralizer import cut_pvc
from teralizer.eval.evidence import jarvis_values
from teralizer.release import evidence, jarvis, outputs


def _write_report(root: Path, report: str, table: str, share: str) -> None:
    path = root / f"build/{report}/{table}.csv"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"row_key,share\nall,{share}\n")
    manifest_path = root / "reports/provenance.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(manifest_path.read_text()) if manifest_path.is_file() else {}
    manifest[report] = {
        "artifacts": [
            {"target": "csv", "key": table, "path": f"build/{report}/{table}.csv"}
        ],
        "run": {"source_commit": "a" * 40},
    }
    manifest_path.write_text(json.dumps(manifest))


def _full_run(root: Path) -> Path:
    _write_report(root, "rq0", "cases", "0.5")
    _write_report(root, "rq5", "outcomes", "0.25")
    return root


def _reference(tmp_path: Path) -> Path:
    reference = tmp_path / "reference.json"
    reference.write_text(json.dumps(outputs.reference(_full_run(tmp_path / "ref"))))
    return reference


def test_run_of_rq0_checks_the_jarvis_inputs(tmp_path: Path):
    reference = _reference(tmp_path)
    candidate = tmp_path / "candidate"
    _write_report(candidate, "rq0", "cases", "0.5")

    verdict = evidence.verify(candidate, reference, ["rq0"], lambda: [])

    assert verdict.passed
    assert verdict.summary()["jarvis_inputs"] == {"passed": True, "problems": []}


def test_run_without_rq0_does_not_check_the_jarvis_inputs(tmp_path: Path):
    reference = _reference(tmp_path)
    candidate = tmp_path / "candidate"
    _write_report(candidate, "rq5", "outcomes", "0.25")

    def jarvis_check() -> list[str]:
        raise AssertionError("RQ5 does not read the JARVIS inputs")

    verdict = evidence.verify(candidate, reference, ["rq5"], jarvis_check)

    assert verdict.passed
    assert verdict.summary()["jarvis_inputs"] is None


def test_a_jarvis_input_problem_alone_fails_the_verdict(tmp_path: Path):
    reference = _reference(tmp_path)
    candidate = _full_run(tmp_path / "candidate")

    verdict = evidence.verify(candidate, reference, None, lambda: ["logs differ"])

    assert all(result.passed for result in verdict.outputs)
    assert not verdict.passed


def test_value_logs_keep_their_place_below_data_jarvis():
    nested = "data/jarvis-scoreboard/fixtures/b/data/jarvis-scoreboard/b/2.values.tsv"
    paths = [
        "/home/producer/checkout/data/jarvis-census/fixtures/a/data/jarvis-census/1.tsv",
        nested,
        f"/Users/other/repo/{nested}",
        None,
        "nan",
    ]

    assert jarvis._relative(paths) == [
        "data/jarvis-census/fixtures/a/data/jarvis-census/1.tsv",
        nested,
    ]


def test_a_changed_value_log_differs_from_the_recorded_identity(tmp_path: Path):
    selection = {"census_value_logs": ["data/jarvis-census/fixtures/a/1.values.tsv"]}
    log = tmp_path / selection["census_value_logs"][0]
    log.parent.mkdir(parents=True)
    log.write_text("parameter\tvalue\nx\t1\n")
    facts = tmp_path / "facts.json"
    identity = jarvis_values.file_set_identity([log])
    facts.write_text(json.dumps({"sources": {"census_value_logs": identity}}))

    assert jarvis.verify(selection, tmp_path, facts) == []
    log.write_text("parameter\tvalue\nx\t2\n")
    assert len(jarvis.verify(selection, tmp_path, facts)) == 1


def test_cut_values_must_be_the_aggregate_of_the_retained_captures(tmp_path: Path):
    capture = tmp_path / jarvis.CUT_CAPTURE / "isascii/CharUtils.isAscii.tsv"
    capture.parent.mkdir(parents=True)
    capture.write_text("ch=a\nch=b\nch=a\n")
    values = tmp_path / jarvis.CUT_VALUES
    cut_pvc.write_cut_values(
        cut_pvc.load_capture(tmp_path / jarvis.CUT_CAPTURE), values
    )

    assert jarvis.cut_value_problems(tmp_path) == []
    capture.write_text("ch=a\nch=c\n")
    assert len(jarvis.cut_value_problems(tmp_path)) == 1
    capture.unlink()
    assert jarvis.cut_value_problems(tmp_path) == [
        f"{jarvis.CUT_CAPTURE} holds no capture"
    ]
