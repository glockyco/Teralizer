"""python -m teralizer.eval <rq|all> [--targets md,figures,latex,csv]."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import teralizer.eval.reports  # noqa: F401
from teralizer.eval import inputs, provenance, publish, registry
from teralizer.eval.artifacts import ArtifactSet, RenderTarget
from teralizer.eval.model import BuiltReport
from teralizer.eval.render import csv as csv_renderer
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import latex as latex_renderer
from teralizer.eval.render import manifest as manifest_renderer
from teralizer.eval.render import markdown as markdown_renderer

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"
_RENDER_TARGETS = frozenset(
    target.value for target in RenderTarget if target != "manifest"
)


def _build(rq: str, *, publishing: bool) -> BuiltReport:
    if publishing:
        provenance.require_publishable_tree()
    spec = registry.get(rq)
    with inputs.resolve_inputs(rq, spec.inputs) as context:
        report = spec.build(context)
        built = BuiltReport(report, context.snapshots)
    if publishing:
        provenance.require_publishable_inputs(built.inputs)
    return built


def _render_report(
    built: BuiltReport, targets: set[str], *, staging_root: Path
) -> ArtifactSet:
    rq = built.report.rq
    artifacts = ArtifactSet(staging_root)
    if "figures" in targets:
        artifacts.merge(
            figures_renderer.materialize(
                built.report,
                REPORTS_DIR / "figures" / rq,
                BUILD_DIR / "figures" / rq,
                staging_root=staging_root,
            )
        )
    if "md" in targets:
        artifacts.merge(
            markdown_renderer.render(
                built, REPORTS_DIR, staging_root=staging_root, repo_url=REPO_URL
            )
        )
    if "latex" in targets:
        artifacts.merge(
            latex_renderer.render(built, BUILD_DIR, staging_root=staging_root)
        )
    if "csv" in targets:
        artifacts.merge(
            csv_renderer.render(built.report, BUILD_DIR / rq, staging_root=staging_root)
        )
    return artifacts


def _deliver_legacy_paper_outputs(
    artifacts: ArtifactSet,
    paper_root: Path,
    declaration: publish.FigureDeclaration | None,
) -> None:
    tables_dir = paper_root / "tables"
    data_dir = paper_root / "data"
    for artifact in artifacts:
        if artifact.id.target is RenderTarget.LATEX:
            if artifact.id.key.startswith("macros/"):
                continue
            tables_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(artifact.path, tables_dir / artifact.path.name)
        elif artifact.id.target is RenderTarget.CSV:
            data_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(artifact.path, data_dir / artifact.path.name)
    if declaration is not None:
        for path in publish.deliver(declaration, artifacts):
            print(f"published {path}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.eval")
    parser.add_argument("rq", help="report id or 'all'")
    parser.add_argument("--targets", default="md,figures,latex")
    parser.add_argument("--paper-out", default=None)
    args = parser.parse_args(argv)
    targets = {target.strip() for target in args.targets.split(",") if target.strip()}
    unknown_targets = targets - _RENDER_TARGETS
    if unknown_targets:
        parser.error(f"unknown render targets: {', '.join(sorted(unknown_targets))}")
    if args.paper_out and args.rq != "all":
        parser.error("--paper-out requires 'all'. Publish the whole set or none of it")
    if (
        args.paper_out
        and "figures" not in targets
        and (Path(args.paper_out) / publish.DECLARATION_NAME).is_file()
    ):
        parser.error(
            f"{args.paper_out} declares figures in {publish.DECLARATION_NAME}. "
            "Add 'figures' to --targets or publishing would skip them"
        )
    rqs = sorted(registry.REPORTS) if args.rq == "all" else [args.rq]
    if not rqs:
        print("no reports registered")
        return
    declaration = (
        publish.read_declaration(Path(args.paper_out)) if args.paper_out else None
    )
    built_reports = tuple(
        _build(rq, publishing=args.paper_out is not None) for rq in rqs
    )
    staging_root = REPORTS_DIR.parent.resolve()
    artifacts = ArtifactSet(staging_root)
    for built in built_reports:
        artifacts.merge(_render_report(built, targets, staging_root=staging_root))
    if "latex" in targets:
        artifacts.merge(
            latex_renderer.render_aggregate(BUILD_DIR, staging_root=staging_root)
        )
    if "md" in targets:
        artifacts.merge(
            manifest_renderer.render(
                built_reports,
                artifacts,
                REPORTS_DIR,
                staging_root=staging_root,
                repo_url=REPO_URL,
            )
        )
    if args.paper_out:
        _deliver_legacy_paper_outputs(artifacts, Path(args.paper_out), declaration)


if __name__ == "__main__":
    main()
