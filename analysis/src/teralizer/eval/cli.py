"""python -m teralizer.eval <rq|all> [--db NAME] [--targets md,figures,latex] [--paper-out PATH]"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import teralizer.eval.reports  # noqa: F401
from teralizer.eval import provenance, publish, registry
from teralizer.eval.data import connect
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import latex as latex_renderer
from teralizer.eval.render import manifest as manifest_renderer
from teralizer.eval.render import markdown as markdown_renderer
from teralizer.eval.render import csv as csv_renderer
from teralizer.report_basis import (
    require_complete_corpus,
    resolve_repo_relative_path,
)

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"


def _build_and_render(
    rq: str,
    db: str | None,
    targets: set[str],
    paper_out: Path | None,
    corpus_override: tuple[Path, Path] | None = None,
) -> dict[str, Path]:
    """Returns the PDF emitted per figure key, for the caller to publish once the
    whole run is known."""
    if paper_out is not None:
        provenance.require_publishable_tree()
    spec = registry.get(rq)
    validate = spec.schema == "old"
    corpus = corpus_override
    if corpus is None and spec.corpus is not None:
        corpus = (
            resolve_repo_relative_path(spec.corpus.data_dir),
            resolve_repo_relative_path(spec.corpus.config_dir),
        )
    with connect(
        db or spec.default_db, validate_schema=validate, require=spec.requires
    ) as conn:
        if corpus is not None:
            require_complete_corpus(conn, data_dir=corpus[0], config_dir=corpus[1])
        report = spec.build(conn)
    emitted: dict[str, Path] = {}
    if "figures" in targets:
        emitted = figures_renderer.materialize(
            report, REPORTS_DIR / "figures" / rq, BUILD_DIR / "figures" / rq
        ).pdf
    if "md" in targets:
        markdown_renderer.render(report, REPORTS_DIR, repo_url=REPO_URL)
        manifest_renderer.write_manifest(report, REPORTS_DIR, repo_url=REPO_URL)
    if "latex" in targets:
        written = latex_renderer.render(report, BUILD_DIR)
        if paper_out is not None:
            paper_out.mkdir(parents=True, exist_ok=True)
            for path in written:
                shutil.copy2(path, paper_out / path.name)
    if "csv" in targets:
        csv_paths = csv_renderer.render(report, BUILD_DIR / rq)
        if paper_out is not None:
            data_dir = paper_out.parent / "data"
            data_dir.mkdir(parents=True, exist_ok=True)
            for path in csv_paths:
                shutil.copy2(path, data_dir / path.name)
    return emitted


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.eval")
    parser.add_argument("rq", help="report id or 'all'")
    parser.add_argument("--db", default=None)
    parser.add_argument("--targets", default="md,figures,latex")
    # No environment default. Publishing is an explicit act, and an ambient
    # PAPER_REPO_PATH would make every run copy artifacts into another repo.
    parser.add_argument("--paper-out", default=None)
    parser.add_argument(
        "--corpus-data-dir",
        type=Path,
        help="refuse output unless this corpus data directory is complete",
    )
    parser.add_argument(
        "--corpus-config-dir",
        type=Path,
        help="project configs defining the expected complete corpus",
    )
    args = parser.parse_args(argv)
    if (args.corpus_data_dir is None) != (args.corpus_config_dir is None):
        parser.error("--corpus-data-dir and --corpus-config-dir must be used together")
    if args.corpus_data_dir is not None and args.rq == "all":
        parser.error(
            "--corpus-data-dir overrides one report's corpus and cannot be used "
            "with 'all'. Each report declares its own"
        )
    # macros.tex and the CSV directory are shared across reports, so copying
    # them out after a single-report run puts one fresh report beside stale ones.
    if args.paper_out and args.rq != "all":
        parser.error("--paper-out requires 'all'. Publish the whole set or none of it")
    # Without the figure target every declared key would be reported as missing,
    # blaming the consumer's declaration for a mistake in this invocation.
    if (
        args.paper_out
        and "figures" not in {t.strip() for t in args.targets.split(",")}
        and (Path(args.paper_out) / publish.DECLARATION_NAME).is_file()
    ):
        parser.error(
            f"{args.paper_out} declares figures in {publish.DECLARATION_NAME}; "
            "add 'figures' to --targets or publishing would skip them"
        )
    targets = {t.strip() for t in args.targets.split(",") if t.strip()}
    paper_out = Path(args.paper_out) / "tables" if args.paper_out else None
    rqs = sorted(registry.REPORTS) if args.rq == "all" else [args.rq]
    if not rqs or (args.rq == "all" and not registry.REPORTS):
        print("no reports registered")
        return
    override = (
        (args.corpus_data_dir, args.corpus_config_dir)
        if args.corpus_data_dir is not None
        else None
    )
    # Figures are declared per report but published once: a consumer's
    # declaration is checked against everything this run emitted, so a key that
    # no report produces fails rather than passing because another report ran.
    declaration = (
        publish.read_declaration(Path(args.paper_out)) if args.paper_out else None
    )
    emitted: dict[str, Path] = {}
    for rq in rqs:
        emitted = publish.merge_emitted(
            emitted, _build_and_render(rq, args.db, targets, paper_out, override), rq
        )
    if declaration is not None:
        for path in publish.deliver(declaration, emitted):
            print(f"published {path}")


if __name__ == "__main__":
    main()
