import json
from pathlib import Path

from teralizer.release import outputs


def _run(root: Path, *, extra: bool = False) -> Path:
    files = {
        "build/rq1/table.csv": b"a,b\n1,2\n",
        "build/rq1.tex": b"\\begin{table}\n",
        "build/macros/rq1.tex": b"\\newcommand{\\RqOne}{1}\n",
        "reports/rq1.md": b"# RQ1\n",
        "reports/figures/rq1/plot.png": b"\x89PNG\r\n\x1a\nfont-dependent",
        "build/figures/rq1/plot.pdf": b"%PDF-1.4 font-dependent",
        "build/figures/rq1/plot.source.json": b'{"axes": []}\n',
    }
    for relative, content in files.items():
        (root / relative).parent.mkdir(parents=True, exist_ok=True)
        (root / relative).write_bytes(content)
    artifacts = [
        {"target": "csv", "key": "table", "path": "build/rq1/table.csv"},
        {"target": "latex", "key": "table", "path": "build/rq1.tex"},
        {"target": "latex", "key": "macros/rq1", "path": "build/macros/rq1.tex"},
        {"target": "md", "key": "rq1", "path": "reports/rq1.md"},
        {"target": "md", "key": "plot", "path": "reports/figures/rq1/plot.png"},
        {"target": "figures", "key": "plot", "path": "build/figures/rq1/plot.pdf"},
        {
            "target": "figures",
            "key": "plot.source",
            "path": "build/figures/rq1/plot.source.json",
        },
    ]
    if extra:
        (root / "build/rq1/new.csv").write_text("x\n")
        artifacts.append({"target": "csv", "key": "new", "path": "build/rq1/new.csv"})
    manifest = {"rq1": {"artifacts": artifacts, "run": {"source_commit": "a" * 40}}}
    (root / "reports/provenance.json").write_text(json.dumps(manifest))
    return root


def test_identical_run_passes_and_images_compare_by_presence(tmp_path: Path):
    expected = outputs.reference(_run(tmp_path / "reference"))
    candidate = _run(tmp_path / "candidate")
    (candidate / "reports/figures/rq1/plot.png").write_bytes(
        b"\x89PNG\r\n\x1a\nother font"
    )

    summary = outputs.summary(outputs.compare(expected, candidate))

    assert summary["passed"] is True
    assert summary["targets"]["figures"] == {"passed": 2}


def test_run_manifest_ignores_only_the_checkout_position(tmp_path: Path):
    expected = outputs.reference(_run(tmp_path / "reference"))
    candidate = _run(tmp_path / "candidate")
    manifest = json.loads((candidate / "reports/provenance.json").read_text())
    manifest["rq1"]["run"]["source_commit"] = "b" * 40
    (candidate / "reports/provenance.json").write_text(json.dumps(manifest))

    assert outputs.summary(outputs.compare(expected, candidate))["passed"] is True


def test_each_output_kind_fails_on_its_own_change(tmp_path: Path):
    expected = outputs.reference(_run(tmp_path / "reference"))
    candidate = _run(tmp_path / "candidate", extra=True)
    (candidate / "build/rq1/table.csv").write_text("a,b\n1,3\n")
    (candidate / "build/macros/rq1.tex").write_text("\\newcommand{\\RqOne}{2}\n")
    (candidate / "build/figures/rq1/plot.source.json").write_text('{"axes": [1]}\n')
    (candidate / "build/figures/rq1/plot.pdf").write_text("not a pdf")
    (candidate / "reports/rq1.md").unlink()

    failures = outputs.summary(outputs.compare(expected, candidate))["failures"]

    assert {(item["artifact"], item["status"]) for item in failures} == {
        ("rq1/csv/table", "changed"),
        ("rq1/latex/macros/rq1", "changed"),
        ("rq1/figures/plot.source", "changed"),
        ("rq1/figures/plot", "changed"),
        ("rq1/md/rq1", "missing"),
        ("rq1/csv/new", "extra"),
        # The run manifest lists the extra artifact, so it changes as well.
        ("rq1/manifest/provenance", "changed"),
    }


def _add_report(root: Path, report: str) -> None:
    (root / f"reports/{report}.md").write_text(f"# {report}\n")
    manifest = json.loads((root / "reports/provenance.json").read_text())
    manifest[report] = {
        "artifacts": [{"target": "md", "key": report, "path": f"reports/{report}.md"}],
        "run": {"source_commit": "a" * 40},
    }
    (root / "reports/provenance.json").write_text(json.dumps(manifest))


def test_run_of_one_report_compares_only_that_report(tmp_path: Path):
    reference_run = _run(tmp_path / "reference")
    _add_report(reference_run, "rq0")
    expected = outputs.reference(reference_run)
    candidate = _run(tmp_path / "candidate")

    assert outputs.summary(outputs.compare(expected, candidate, ["rq1"]))["passed"]
    failures = outputs.summary(outputs.compare(expected, candidate))["failures"]
    assert {(item["artifact"], item["status"]) for item in failures} == {
        ("rq0/md/rq0", "missing"),
        ("rq0/manifest/provenance", "missing"),
    }


def test_changed_entry_of_a_selected_report_fails(tmp_path: Path):
    reference_run = _run(tmp_path / "reference")
    _add_report(reference_run, "rq0")
    expected = outputs.reference(reference_run)
    candidate = _run(tmp_path / "candidate")
    _add_report(candidate, "rq0")
    manifest = json.loads((candidate / "reports/provenance.json").read_text())
    manifest["rq0"]["metrics"] = {"value": 1}
    (candidate / "reports/provenance.json").write_text(json.dumps(manifest))

    failures = outputs.summary(outputs.compare(expected, candidate, ["rq0"]))[
        "failures"
    ]
    assert failures == [{"artifact": "rq0/manifest/provenance", "status": "changed"}]
