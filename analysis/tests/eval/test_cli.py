from pathlib import Path

import inspect
import pandas as pd
import pytest
from teralizer.eval import cli, provenance, registry
from teralizer.eval.model import ColumnSpec, Metric, RQReport, Section, Table


def _fixture_report(_conn):
    t = Table(
        key="k",
        df=pd.DataFrame({"a": [1]}),
        columns=[ColumnSpec("A", "a", "int")],
        caption="c",
        label="tab:k",
    )
    return RQReport(
        "smoke",
        "Smoke",
        "sqlite",
        [Section("s", [t])],
        metrics=[Metric("smoke.n", 1, "int")],
    )


def test_provenance_documents_dirty_publish_opt_out():
    src = inspect.getsource(provenance)
    assert provenance.DIRTY_PROVENANCE_ENV in src


def test_cli_fans_out_to_targets(monkeypatch, tmp_path):
    import contextlib

    @contextlib.contextmanager
    def fake_connect(db, *, validate_schema=False, require=None):
        yield None

    monkeypatch.setitem(
        registry.REPORTS, "smoke", registry.ReportSpec(_fixture_report, "sqlite", "new")
    )
    monkeypatch.setattr(cli, "connect", fake_connect)
    monkeypatch.setattr(cli, "REPORTS_DIR", tmp_path / "reports")
    monkeypatch.setattr(cli, "BUILD_DIR", tmp_path / "build")
    cli.main(["smoke", "--targets", "md,figures,latex"])
    assert (tmp_path / "reports" / "smoke.md").exists()
    assert (tmp_path / "reports" / "provenance.json").exists()
    assert (tmp_path / "build" / "k.tex").exists()
    assert (tmp_path / "build" / "macros.tex").exists()


def test_cli_checks_corpus_completion_before_build(monkeypatch, tmp_path):
    import contextlib

    connection = object()
    calls = []

    @contextlib.contextmanager
    def fake_connect(db, *, validate_schema=False, require=None):
        yield connection

    def fake_require_complete(conn, *, data_dir, config_dir):
        calls.append((conn, data_dir, config_dir))

    monkeypatch.setitem(
        registry.REPORTS,
        "complete",
        registry.ReportSpec(_fixture_report, "sqlite", "new"),
    )
    monkeypatch.setattr(cli, "connect", fake_connect)
    monkeypatch.setattr(cli, "require_complete_corpus", fake_require_complete)
    monkeypatch.setattr(cli, "REPORTS_DIR", tmp_path / "reports")
    cli.main(
        [
            "complete",
            "--targets",
            "md",
            "--corpus-data-dir",
            "data/run",
            "--corpus-config-dir",
            "project-configs/run",
        ]
    )

    assert calls == [(connection, Path("data/run"), Path("project-configs/run"))]


def test_cli_fans_out_csv_to_build_and_paper_data(monkeypatch, tmp_path):
    import contextlib

    monkeypatch.setenv(provenance.DIRTY_PROVENANCE_ENV, "1")

    @contextlib.contextmanager
    def fake_connect(db, *, validate_schema=False, require=None):
        yield None

    monkeypatch.setitem(
        registry.REPORTS,
        "smoke_csv",
        registry.ReportSpec(_fixture_report, "sqlite", "new"),
    )
    monkeypatch.setattr(cli, "connect", fake_connect)
    monkeypatch.setattr(cli, "REPORTS_DIR", tmp_path / "reports")
    monkeypatch.setattr(cli, "BUILD_DIR", tmp_path / "build")
    cli._build_and_render(
        "smoke_csv",
        None,
        {"md", "latex", "csv"},
        tmp_path / "paper" / "tables",
    )
    assert (tmp_path / "build" / "smoke_csv" / "k.csv").exists()
    assert (tmp_path / "paper" / "tables" / "k.tex").exists()
    csv_path = tmp_path / "paper" / "data" / "k.csv"
    assert csv_path.exists()
    assert csv_path.read_text(encoding="utf-8").splitlines()[0] == "a"


def test_publishing_dirty_tree_is_refused(monkeypatch, tmp_path):
    monkeypatch.delenv(provenance.DIRTY_PROVENANCE_ENV, raising=False)
    monkeypatch.setattr(
        provenance, "_git_snapshot", lambda: ("a" * 40, True)
    )
    with pytest.raises(RuntimeError, match=provenance.DIRTY_PROVENANCE_ENV):
        cli.main(["all", "--paper-out", str(tmp_path)])


def test_publishing_one_report_is_refused(tmp_path):
    with pytest.raises(SystemExit):
        cli.main(["rq6", "--paper-out", str(tmp_path)])


def test_overriding_the_corpus_across_all_reports_is_refused(tmp_path):
    with pytest.raises(SystemExit):
        cli.main(
            [
                "all",
                "--corpus-data-dir",
                str(tmp_path),
                "--corpus-config-dir",
                str(tmp_path),
            ]
        )
