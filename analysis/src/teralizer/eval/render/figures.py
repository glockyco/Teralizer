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

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.model import RQReport
from teralizer.eval.provenance import capture
from teralizer.plotting import setup_paper_style


def materialize(
    report: RQReport, fig_dir: Path, pdf_dir: Path, *, staging_root: Path
) -> ArtifactSet:
    setup_paper_style()
    fig_dir.mkdir(parents=True, exist_ok=True)
    pdf_dir.mkdir(parents=True, exist_ok=True)
    artifacts = ArtifactSet(staging_root)
    for figure in report.figures():
        # Resolved through the figure's own build function, so the image records
        # the commit of the code that drew it and stops changing on unrelated
        # commits.
        provenance = f"teralizer.eval {report.rq} @ {capture(figure.build).commit}"
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
            artifacts.add(
                RenderedArtifact(
                    ArtifactId(RenderTarget.MARKDOWN, figure.key), png, report.rq
                )
            )
            pdf = pdf_dir / f"{figure.key}.pdf"
            # No dpi: the paper style's 300 applies, and for a vector page it
            # only governs any raster element the figure embeds.
            fig.savefig(
                pdf,
                bbox_inches="tight",
                metadata={"Subject": provenance},
            )
            artifacts.add(
                RenderedArtifact(
                    ArtifactId(RenderTarget.FIGURES, figure.key), pdf, report.rq
                )
            )
        finally:
            plt.close(fig)
    return artifacts
