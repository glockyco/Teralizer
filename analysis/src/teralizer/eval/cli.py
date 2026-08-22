"""python -m teralizer.eval <rq|all> [--targets md,figures,latex,csv]."""

from __future__ import annotations

import argparse
from pathlib import Path

import teralizer.eval.reports  # noqa: F401
from teralizer.eval import publish, registry, run
from teralizer.eval.artifacts import RenderTarget

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"
_RENDER_TARGETS = frozenset(
    target.value for target in RenderTarget if target is not RenderTarget.MANIFEST
)


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
    paper_root = Path(args.paper_out) if args.paper_out else None
    declaration = publish.read_declaration(paper_root) if paper_root else None
    selected_targets = frozenset(RenderTarget(target) for target in target_names)
    missing_targets = (
        declaration.required_targets - selected_targets
        if declaration is not None
        else frozenset()
    )
    if missing_targets:
        parser.error(
            f"{paper_root} declares render targets not requested by --targets: "
            + ", ".join(sorted(target.value for target in missing_targets))
        )
    report_ids = tuple(sorted(registry.REPORTS)) if args.rq == "all" else (args.rq,)
    if not report_ids:
        print("no reports registered")
        return
    result = run.execute(
        report_ids,
        selected_targets,
        run.RunPaths(_ANALYSIS, REPORTS_DIR, BUILD_DIR),
        repo_url=REPO_URL,
        full=args.rq == "all",
        declaration=declaration,
        publishing=paper_root is not None,
    )
    for path in result.delivered:
        print(f"published {path}")


if __name__ == "__main__":
    main()
