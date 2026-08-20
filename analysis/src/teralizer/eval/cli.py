"""python -m teralizer.eval <rq|all> [--targets md,figures,latex] [--paper-out PATH]"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import teralizer.eval.reports  # noqa: F401
from teralizer.eval import inputs, provenance, publish, registry
from teralizer.eval.model import BuiltReport
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import latex as latex_renderer
from teralizer.eval.render import manifest as manifest_renderer
from teralizer.eval.render import markdown as markdown_renderer
from teralizer.eval.render import csv as csv_renderer

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"
_RENDER_TARGETS = frozenset({"md", "figures", "latex", "csv"})


def _build_and_render(
    rq: str,
    targets: set[str],
    paper_out: Path | None,
) -> dict[str, Path]:
    """Returns the PDF emitted per figure key, for the caller to publish once the
    whole run is known."""
    if paper_out is not None:
        provenance.require_publishable_tree()
    spec = registry.get(rq)
    with inputs.resolve_inputs(rq, spec.inputs) as context:
        report = spec.build(context)
        built = BuiltReport(report, context.snapshots)
    if paper_out is not None:
        provenance.require_publishable_inputs(built.inputs)
    emitted: dict[str, Path] = {}
    if "figures" in targets:
        emitted = figures_renderer.materialize(
            report, REPORTS_DIR / "figures" / rq, BUILD_DIR / "figures" / rq
        ).pdf
    if "md" in targets:
        markdown_renderer.render(built, REPORTS_DIR, repo_url=REPO_URL)
        manifest_renderer.write_manifest(built, REPORTS_DIR, repo_url=REPO_URL)
    if "latex" in targets:
        written = latex_renderer.render(built, BUILD_DIR)
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
    parser.add_argument("--targets", default="md,figures,latex")
    # No environment default. Publishing is an explicit act, and an ambient
    # PAPER_REPO_PATH would make every run copy artifacts into another repo.
    parser.add_argument("--paper-out", default=None)
    args = parser.parse_args(argv)
    targets = {target.strip() for target in args.targets.split(",") if target.strip()}
    unknown_targets = targets - _RENDER_TARGETS
    if unknown_targets:
        parser.error(f"unknown render targets: {', '.join(sorted(unknown_targets))}")
    # macros.tex and the CSV directory are shared across reports, so copying
    # them out after a single-report run puts one fresh report beside stale ones.
    if args.paper_out and args.rq != "all":
        parser.error("--paper-out requires 'all'. Publish the whole set or none of it")
    # Without the figure target every declared key would be reported as missing,
    # blaming the consumer's declaration for a mistake in this invocation.
    if (
        args.paper_out
        and "figures" not in targets
        and (Path(args.paper_out) / publish.DECLARATION_NAME).is_file()
    ):
        parser.error(
            f"{args.paper_out} declares figures in {publish.DECLARATION_NAME}; "
            "add 'figures' to --targets or publishing would skip them"
        )
    paper_out = Path(args.paper_out) / "tables" if args.paper_out else None
    rqs = sorted(registry.REPORTS) if args.rq == "all" else [args.rq]
    if not rqs or (args.rq == "all" and not registry.REPORTS):
        print("no reports registered")
        return
    # Figures are declared per report but published once: a consumer's
    # declaration is checked against everything this run emitted, so a key that
    # no report produces fails rather than passing because another report ran.
    declaration = (
        publish.read_declaration(Path(args.paper_out)) if args.paper_out else None
    )
    emitted: dict[str, Path] = {}
    for rq in rqs:
        emitted = publish.merge_emitted(
            emitted, _build_and_render(rq, targets, paper_out), rq
        )
    if declaration is not None:
        for path in publish.deliver(declaration, emitted):
            print(f"published {path}")


if __name__ == "__main__":
    main()
