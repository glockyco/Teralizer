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

import json
from datetime import UTC, datetime
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.figure import Figure
from matplotlib.patches import Patch

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.model import RQReport
from teralizer.eval.provenance import capture
from teralizer.plotting import setup_paper_style

# Matplotlib stamps a PDF with the time it was written unless SOURCE_DATE_EPOCH is
# set, which only the Nix development shell does. A figure published from any other
# shell would then differ from the last one in every run. The date is the one that
# shell's SOURCE_DATE_EPOCH yields, so published figures keep their bytes.
_PDF_CREATION_DATE = datetime(1980, 1, 1, tzinfo=UTC)


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
                metadata={"Subject": provenance, "CreationDate": _PDF_CREATION_DATE},
            )
            artifacts.add(
                RenderedArtifact(
                    ArtifactId(RenderTarget.FIGURES, figure.key), pdf, report.rq
                )
            )
            source = pdf_dir / f"{figure.key}.source.json"
            source.write_text(
                json.dumps(figure_source(fig), indent=2, sort_keys=True) + "\n"
            )
            artifacts.add(
                RenderedArtifact(
                    ArtifactId(RenderTarget.FIGURES, f"{figure.key}.source"),
                    source,
                    report.rq,
                )
            )
        finally:
            plt.close(fig)
    return artifacts


def _number(value: object) -> object:
    """A JSON number that does not depend on the platform's float formatting."""
    number = float(value)  # type: ignore[arg-type]
    if number != number or number in (float("inf"), float("-inf")):
        return str(number)
    rounded = round(number, 9)
    return int(rounded) if rounded.is_integer() else rounded


def _points(values: object) -> list[list[object]]:
    return [[_number(item) for item in row] for row in values]  # type: ignore[union-attr]


def _vertices(patch: Patch) -> list[list[object]]:
    """Outline of a patch in data coordinates, without display transforms."""
    path = patch.get_path()
    return _points(patch.get_patch_transform().transform(path.vertices))


def figure_source(fig: Figure) -> dict[str, object]:
    """The data and labels a figure shows, independent of fonts and rasterizer.

    PNG and PDF bytes depend on the fonts installed where the figure is drawn,
    so reproduction compares this canonical source instead: every plotted line,
    bar, marker and text with its coordinates and every axis label and tick.
    """
    axes = []
    for ax in fig.axes:
        legend = ax.get_legend()
        axes.append(
            {
                "title": ax.get_title(),
                "xlabel": ax.get_xlabel(),
                "ylabel": ax.get_ylabel(),
                "xscale": ax.get_xscale(),
                "yscale": ax.get_yscale(),
                "xlim": [_number(value) for value in ax.get_xlim()],
                "ylim": [_number(value) for value in ax.get_ylim()],
                "xticks": [_number(value) for value in ax.get_xticks()],
                "yticks": [_number(value) for value in ax.get_yticks()],
                "xticklabels": [label.get_text() for label in ax.get_xticklabels()],
                "yticklabels": [label.get_text() for label in ax.get_yticklabels()],
                "lines": [
                    {"label": line.get_label(), "xy": _points(line.get_xydata())}
                    for line in ax.get_lines()
                ],
                "patches": [
                    {
                        "type": type(patch).__name__,
                        "vertices": _vertices(patch),
                        "label": patch.get_label(),
                    }
                    for patch in ax.patches
                ],
                "collections": [
                    {
                        "type": type(collection).__name__,
                        "label": collection.get_label(),
                        "offsets": _points(collection.get_offsets()),
                    }
                    for collection in ax.collections
                ],
                "texts": [
                    {
                        "text": text.get_text(),
                        "xy": [_number(value) for value in text.get_position()],
                    }
                    for text in ax.texts
                ],
                "legend": [text.get_text() for text in legend.get_texts()]
                if legend is not None
                else [],
            }
        )
    return {"axes": axes}
