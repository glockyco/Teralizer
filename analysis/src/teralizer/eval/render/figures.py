"""Materialize each Figure once to a committed PNG."""

from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from teralizer.eval.model import RQReport
from teralizer.eval.provenance import git_commit
from teralizer.plotting import setup_paper_style


def materialize(report: RQReport, fig_dir: Path) -> list[Path]:
    setup_paper_style()
    fig_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    commit = git_commit()
    for figure in report.figures():
        fig, ax = plt.subplots()
        try:
            figure.build(ax)
            out = fig_dir / f"{figure.key}.png"
            fig.savefig(
                out,
                dpi=200,
                bbox_inches="tight",
                metadata={"Comment": f"teralizer.eval {report.rq} @ {commit}"},
            )
            written.append(out)
        finally:
            plt.close(fig)
    return written
