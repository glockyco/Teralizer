from pathlib import Path
import warnings

import matplotlib

matplotlib.use("Agg")

from teralizer.eval.model import Figure, RQReport, Section
from teralizer.eval.render.figures import materialize


def _report(*keys: str) -> RQReport:
    return RQReport(
        "example",
        "T",
        "db",
        [
            Section(
                "S",
                [
                    Figure(k, lambda ax: ax.bar(["a", "b"], [1, 2]), "cap", f"fig:{k}")
                    for k in (keys or ("bar",))
                ],
            )
        ],
    )


def test_materialize_writes_both_formats(tmp_path: Path):
    png_dir, pdf_dir = tmp_path / "png", tmp_path / "pdf"
    written = materialize(_report(), png_dir, pdf_dir)
    assert written.png == [png_dir / "bar.png"]
    assert written.pdf == {"bar": pdf_dir / "bar.pdf"}
    for path in [png_dir / "bar.png", pdf_dir / "bar.pdf"]:
        assert path.stat().st_size > 0


def test_pdf_is_keyed_by_figure_key_for_publishing(tmp_path: Path):
    """Publishing resolves a consumer's declaration against figure keys, so the
    mapping must be by key rather than by position."""
    written = materialize(_report("first", "second"), tmp_path / "p", tmp_path / "d")
    assert set(written.pdf) == {"first", "second"}


def test_both_formats_carry_provenance(tmp_path: Path):
    written = materialize(_report(), tmp_path / "png", tmp_path / "pdf")
    pdf_bytes = written.pdf["bar"].read_bytes()
    assert b"/Subject" in pdf_bytes
    assert b"teralizer.eval example @ " in pdf_bytes
    assert b"teralizer.eval example @ " in written.png[0].read_bytes()


def test_pdf_metadata_uses_no_unknown_keyword(tmp_path: Path):
    """A key outside the PDF information dictionary makes matplotlib warn, and a
    warned key is dropped, so provenance would be silently lost."""
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        materialize(_report(), tmp_path / "png", tmp_path / "pdf")
    assert not [w for w in caught if "infodict" in str(w.message)]


def test_pdf_is_vector_with_embedded_fonts(tmp_path: Path):
    """A rasterized page, or one relying on a reader's fonts, would defeat the
    reason for publishing PDF at all."""
    written = materialize(_report(), tmp_path / "png", tmp_path / "pdf")
    assert b"/FontFile2" in written.pdf["bar"].read_bytes()


def test_directories_are_created(tmp_path: Path):
    target = tmp_path / "absent" / "deeper"
    materialize(_report(), target / "png", target / "pdf")
    assert (target / "png").is_dir() and (target / "pdf").is_dir()


def test_a_report_without_figures_writes_nothing(tmp_path: Path):
    report = RQReport("example", "T", "db", [Section("S", [])])
    written = materialize(report, tmp_path / "png", tmp_path / "pdf")
    assert written.png == [] and written.pdf == {}
