"""python -m teralizer.eval <rq|all> [--db NAME] [--targets md,figures,latex] [--paper-out PATH]"""

from __future__ import annotations

import argparse
import os
import shutil
from pathlib import Path

from teralizer.eval import registry
from teralizer.eval.data import connect
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import latex as latex_renderer
from teralizer.eval.render import manifest as manifest_renderer
from teralizer.eval.render import markdown as markdown_renderer

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"


def _build_and_render(
    rq: str, db: str | None, targets: set[str], paper_out: Path | None
) -> None:
    spec = registry.get(rq)
    validate = spec.schema == "old"
    with connect(
        db or spec.default_db, validate_schema=validate, require=spec.requires
    ) as conn:
        report = spec.build(conn)
    if "figures" in targets:
        figures_renderer.materialize(report, REPORTS_DIR / "figures" / rq)
    if "md" in targets:
        markdown_renderer.render(report, REPORTS_DIR, repo_url=REPO_URL)
        manifest_renderer.write_manifest(report, REPORTS_DIR, repo_url=REPO_URL)
    if "latex" in targets:
        written = latex_renderer.render(report, BUILD_DIR)
        if paper_out is not None:
            paper_out.mkdir(parents=True, exist_ok=True)
            for path in written:
                shutil.copy2(path, paper_out / path.name)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.eval")
    parser.add_argument("rq", help="report id or 'all'")
    parser.add_argument("--db", default=None)
    parser.add_argument("--targets", default="md,figures,latex")
    parser.add_argument("--paper-out", default=os.environ.get("PAPER_REPO_PATH"))
    args = parser.parse_args(argv)
    targets = {t.strip() for t in args.targets.split(",") if t.strip()}
    paper_out = Path(args.paper_out) / "tables" if args.paper_out else None
    rqs = sorted(registry.REPORTS) if args.rq == "all" else [args.rq]
    if not rqs or (args.rq == "all" and not registry.REPORTS):
        print("no reports registered")
        return
    for rq in rqs:
        _build_and_render(rq, args.db, targets, paper_out)


if __name__ == "__main__":
    main()
