"""python -m teralizer.eval <rq|all> [--targets md,figures,latex,csv]."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import teralizer.eval.reports  # noqa: F401
from teralizer.eval import publish, registry, run
from teralizer.eval.artifacts import ArtifactSet, RenderTarget

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"
_RENDER_TARGETS = frozenset(
    target.value for target in RenderTarget if target is not RenderTarget.MANIFEST
)


def _deliver_legacy_paper_outputs(artifacts: ArtifactSet, paper_root: Path) -> None:
    """Keep the existing table and data copies after generator promotion."""
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


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.eval")
    parser.add_argument("rq", help="report id or 'all'")
    parser.add_argument("--targets", default="md,figures,latex")
    parser.add_argument("--paper-out", default=None)
    args = parser.parse_args(argv)
    target_names = {
        target.strip() for target in args.targets.split(",") if target.strip()
    }
    unknown_targets = target_names - _RENDER_TARGETS
    if unknown_targets:
        parser.error(f"unknown render targets: {', '.join(sorted(unknown_targets))}")
    if args.paper_out and args.rq != "all":
        parser.error("--paper-out requires 'all'. Publish the whole set or none of it")
    if (
        args.paper_out
        and RenderTarget.FIGURES.value not in target_names
        and (Path(args.paper_out) / publish.DECLARATION_NAME).is_file()
    ):
        parser.error(
            f"{args.paper_out} declares figures in {publish.DECLARATION_NAME}. "
            "Add 'figures' to --targets or publishing would skip them"
        )
    report_ids = tuple(sorted(registry.REPORTS)) if args.rq == "all" else (args.rq,)
    if not report_ids:
        print("no reports registered")
        return
    paper_root = Path(args.paper_out) if args.paper_out else None
    declaration = publish.read_declaration(paper_root) if paper_root else None
    result = run.execute(
        report_ids,
        frozenset(RenderTarget(target) for target in target_names),
        run.RunPaths(_ANALYSIS, REPORTS_DIR, BUILD_DIR),
        repo_url=REPO_URL,
        full=args.rq == "all",
        declaration=declaration,
        publishing=paper_root is not None,
    )
    if paper_root is not None:
        _deliver_legacy_paper_outputs(result.artifacts, paper_root)
        for path in result.delivered:
            print(f"published {path}")


if __name__ == "__main__":
    main()
