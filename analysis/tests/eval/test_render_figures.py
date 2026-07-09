from pathlib import Path
import matplotlib

matplotlib.use("Agg")
from teralizer.eval.model import Figure, RQReport, Section
from teralizer.eval.render.figures import materialize


def test_materialize_writes_png_per_figure(tmp_path: Path):
    report = RQReport(
        "example",
        "T",
        "db",
        [
            Section(
                "S",
                [
                    Figure(
                        "bar", lambda ax: ax.bar(["a", "b"], [1, 2]), "cap", "fig:bar"
                    )
                ],
            )
        ],
    )
    written = materialize(report, tmp_path)
    assert (tmp_path / "bar.png").exists() and (tmp_path / "bar.png").stat().st_size > 0
    assert written == [tmp_path / "bar.png"]
