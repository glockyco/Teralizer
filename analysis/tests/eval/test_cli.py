import inspect
import pandas as pd
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


def test_provenance_module_never_reads_environment():
    src = inspect.getsource(provenance)
    assert "environ" not in src and "getenv" not in src and ".env" not in src


def test_cli_fans_out_to_targets(monkeypatch, tmp_path):
    import contextlib

    @contextlib.contextmanager
    def fake_connect(db, *, validate_schema=False):
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
