"""Materialize each Figure twice: a PNG the Markdown reports embed, and a PDF a
print consumer publishes.

Both come from one draw, so the two formats cannot disagree. The PNG keeps the
screen resolution the Markdown reports were built against. The PDF takes the
print settings from the paper style, which sets 300 dpi and TrueType-embedded
fonts. Provenance travels in each format's own metadata vocabulary: PNG text
chunks accept a free-form ``Comment``, while PDF has a fixed information
dictionary and warns on anything outside it, so the same string rides in
``Subject``.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from teralizer.eval.model import RQReport
from teralizer.eval.provenance import git_commit
from teralizer.plotting import setup_paper_style


@dataclass(frozen=True)
class MaterializedFigures:
    """What a figure run produced. ``pdf`` is keyed by figure key, because
    publishing resolves a consumer's declaration against those keys."""

    png: list[Path]
    pdf: dict[str, Path]


def materialize(report: RQReport, fig_dir: Path, pdf_dir: Path) -> MaterializedFigures:
    setup_paper_style()
    fig_dir.mkdir(parents=True, exist_ok=True)
    pdf_dir.mkdir(parents=True, exist_ok=True)
    pngs: list[Path] = []
    pdfs: dict[str, Path] = {}
    provenance = f"teralizer.eval {report.rq} @ {git_commit()}"
    for figure in report.figures():
        fig, ax = plt.subplots()
        try:
            figure.build(ax)
            png = fig_dir / f"{figure.key}.png"
            fig.savefig(
                png,
                dpi=200,
                bbox_inches="tight",
                metadata={"Comment": provenance},
            )
            pngs.append(png)
            pdf = pdf_dir / f"{figure.key}.pdf"
            # No dpi: the paper style's 300 applies, and for a vector page it
            # only governs any raster element the figure embeds.
            fig.savefig(
                pdf,
                bbox_inches="tight",
                metadata={"Subject": provenance},
            )
            pdfs[figure.key] = pdf
        finally:
            plt.close(fig)
    return MaterializedFigures(png=pngs, pdf=pdfs)
