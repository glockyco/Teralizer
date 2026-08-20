import json
import os
from contextlib import contextmanager
from pathlib import Path

import pytest

from teralizer.eval import registry, run
from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.inputs import ReportContext
from teralizer.eval.model import RQReport, Section
from teralizer.eval.publish import FigureDeclaration, PublishError


@contextmanager
def resolved(report, _declarations):
    yield ReportContext(report, (), ())


def report(context: ReportContext) -> RQReport:
    return RQReport(context.report, context.report.upper(), [Section("S", [])])


def paths(root: Path) -> run.RunPaths:
    return run.RunPaths(root, root / "reports", root / "build")


def install_reports(monkeypatch, *report_ids: str) -> None:
    monkeypatch.setattr(
        registry,
        "REPORTS",
        {report_id: registry.ReportSpec(report, ()) for report_id in report_ids},
    )
    monkeypatch.setattr(run.inputs, "resolve_inputs", resolved)


def execute(root: Path, report_ids: tuple[str, ...], *, full: bool):
    return run.execute(
        report_ids,
        frozenset({RenderTarget.MARKDOWN}),
        paths(root),
        repo_url="https://example.test/repo",
        full=full,
    )


def test_late_report_build_failure_preserves_final_paths(monkeypatch, tmp_path):
    sentinel = tmp_path / "reports" / "a.md"
    sentinel.parent.mkdir()
    sentinel.write_text("previous")

    def fail(_context):
        raise RuntimeError("late build failed")

    monkeypatch.setattr(
        registry,
        "REPORTS",
        {
            "a": registry.ReportSpec(report, ()),
            "b": registry.ReportSpec(fail, ()),
        },
    )
    monkeypatch.setattr(run.inputs, "resolve_inputs", resolved)
    with pytest.raises(RuntimeError, match="late build failed"):
        execute(tmp_path, ("a", "b"), full=True)
    assert sentinel.read_text() == "previous"


def test_invalid_built_result_preserves_final_paths(monkeypatch, tmp_path):
    sentinel = tmp_path / "reports" / "a.md"
    sentinel.parent.mkdir()
    sentinel.write_text("previous")

    def wrong(context):
        return RQReport("wrong", context.report, [Section("S", [])])

    monkeypatch.setattr(registry, "REPORTS", {"a": registry.ReportSpec(wrong, ())})
    monkeypatch.setattr(run.inputs, "resolve_inputs", resolved)
    with pytest.raises(ValueError, match="built result 'wrong'"):
        execute(tmp_path, ("a",), full=True)
    assert sentinel.read_text() == "previous"


def test_renderer_failure_preserves_final_paths(monkeypatch, tmp_path):
    install_reports(monkeypatch, "a")
    sentinel = tmp_path / "reports" / "a.md"
    sentinel.parent.mkdir()
    sentinel.write_text("previous")
    monkeypatch.setattr(
        run.markdown_renderer,
        "render",
        lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("render failed")),
    )
    with pytest.raises(RuntimeError, match="render failed"):
        execute(tmp_path, ("a",), full=True)
    assert sentinel.read_text() == "previous"


def test_manifest_failure_preserves_final_paths(monkeypatch, tmp_path):
    install_reports(monkeypatch, "a")
    sentinel = tmp_path / "reports" / "a.md"
    sentinel.parent.mkdir()
    sentinel.write_text("previous")
    monkeypatch.setattr(
        run.manifest_renderer,
        "render",
        lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("manifest failed")),
    )
    with pytest.raises(RuntimeError, match="manifest failed"):
        execute(tmp_path, ("a",), full=True)
    assert sentinel.read_text() == "previous"


def test_consumer_preflight_failure_precedes_promotion(monkeypatch, tmp_path):
    install_reports(monkeypatch, "a")
    sentinel = tmp_path / "reports" / "a.md"
    sentinel.parent.mkdir()
    sentinel.write_text("previous")
    declaration = FigureDeclaration(
        root=tmp_path, targets={"missing": tmp_path / "consumer" / "missing.pdf"}
    )
    with pytest.raises(PublishError):
        run.execute(
            ("a",),
            frozenset({RenderTarget.MARKDOWN}),
            paths(tmp_path),
            repo_url="https://example.test/repo",
            full=True,
            declaration=declaration,
        )
    assert sentinel.read_text() == "previous"
    assert not declaration.targets["missing"].exists()


def test_partial_run_preserves_registered_unselected_manifest_entry(
    monkeypatch, tmp_path
):
    install_reports(monkeypatch, "a", "b")
    reports = tmp_path / "reports"
    reports.mkdir()
    (reports / "b.md").write_text("B")
    (tmp_path / "build").mkdir()
    (tmp_path / "build" / "a.tex").write_text("A table")
    (reports / "provenance.json").write_text(
        json.dumps(
            {
                "a": {
                    "artifacts": [
                        {"target": "latex", "key": "a", "path": "build/a.tex"}
                    ]
                },
                "b": {
                    "artifacts": [{"target": "md", "key": "b", "path": "reports/b.md"}]
                },
            }
        )
    )
    execute(tmp_path, ("a",), full=False)
    manifest = json.loads((reports / "provenance.json").read_text())
    assert set(manifest) == {"a", "b"}
    assert {record["target"] for record in manifest["a"]["artifacts"]} == {
        "latex",
        "md",
    }
    assert (tmp_path / "build" / "a.tex").read_text() == "A table"
    assert (reports / "b.md").read_text() == "B"
    assert (reports / "a.md").is_file()


def test_partial_run_rejects_unregistered_preserved_entry(monkeypatch, tmp_path):
    install_reports(monkeypatch, "a")
    reports = tmp_path / "reports"
    reports.mkdir()
    manifest = reports / "provenance.json"
    manifest.write_text(json.dumps({"removed": {"artifacts": []}}))
    with pytest.raises(ValueError, match="unregistered manifest reports: removed"):
        execute(tmp_path, ("a",), full=False)
    assert json.loads(manifest.read_text()) == {"removed": {"artifacts": []}}


def test_full_run_removes_artifacts_owned_by_removed_report(monkeypatch, tmp_path):
    install_reports(monkeypatch, "a")
    reports = tmp_path / "reports"
    reports.mkdir()
    stale = reports / "removed.md"
    stale.write_text("stale")
    (reports / "provenance.json").write_text(
        json.dumps(
            {
                "removed": {
                    "artifacts": [
                        {
                            "target": "md",
                            "key": "removed",
                            "path": "reports/removed.md",
                        }
                    ]
                }
            }
        )
    )
    execute(tmp_path, ("a",), full=True)
    assert not stale.exists()
    assert set(json.loads((reports / "provenance.json").read_text())) == {"a"}


def test_promotion_failure_restores_every_replaced_file(tmp_path):
    stage = tmp_path / "stage"
    final = tmp_path / "final"
    (stage / "reports").mkdir(parents=True)
    (final / "reports").mkdir(parents=True)
    artifacts = ArtifactSet(stage)
    for key, content in (("a", "new a"), ("b", "new b")):
        staged = stage / "reports" / f"{key}.md"
        staged.write_text(content)
        (final / "reports" / f"{key}.md").write_text(f"old {key}")
        artifacts.add(
            RenderedArtifact(ArtifactId(RenderTarget.MARKDOWN, key), staged, key)
        )
    calls = 0

    def fail_once(source: Path, destination: Path):
        nonlocal calls
        calls += 1
        if calls == 4:
            raise OSError("injected promotion failure")
        return os.replace(source, destination)

    with pytest.raises(OSError, match="injected promotion failure"):
        run.promote(artifacts, final, replace=fail_once)
    assert (final / "reports" / "a.md").read_text() == "old a"
    assert (final / "reports" / "b.md").read_text() == "old b"
