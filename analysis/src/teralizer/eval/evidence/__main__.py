"""Refresh compact report evidence from frozen corpora and external inputs."""

from __future__ import annotations

import argparse
from contextlib import ExitStack
from pathlib import Path

from teralizer import corpora
from teralizer.dataset_characteristics import get_projects_path
from teralizer.eval.evidence import jarvis_values, project_sources
from teralizer.eval.reports.rq0_jarvis import CENSUS_VARIANT
from teralizer.jarvis_scoreboard import SWEEP_VARIANTS

_REPO_ROOT = Path(__file__).resolve().parents[5]
_DEFAULT_OUTPUT_DIR = _REPO_ROOT / "analysis/data/report-inputs"


def _refresh_project_sources(projects_root: Path, output_dir: Path) -> None:
    with ExitStack() as stack:
        _, controlled = stack.enter_context(corpora.open_corpus("controlled"))
        _, real_world = stack.enter_context(corpora.open_corpus("real-world"))
        project_sources.refresh(
            controlled,
            real_world,
            projects_root,
            output_dir / "project-source-facts.json",
        )


def _refresh_jarvis_values(output_dir: Path) -> None:
    with ExitStack() as stack:
        _, scenarios = stack.enter_context(corpora.open_corpus("jarvis-scenarios"))
        _, benchmark = stack.enter_context(corpora.open_corpus("jarvis-benchmark"))
        jarvis_values.refresh(
            scenarios,
            benchmark,
            output_dir / "jarvis-value-facts.json",
            scoreboard_variants=SWEEP_VARIANTS,
            census_variants=(CENSUS_VARIANT,),
        )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m teralizer.eval.evidence")
    parser.add_argument(
        "extractor", choices=("project-sources", "jarvis-values", "all")
    )
    parser.add_argument("--projects-root", type=Path)
    parser.add_argument("--output-dir", type=Path, default=_DEFAULT_OUTPUT_DIR)
    args = parser.parse_args(argv)

    projects_root = args.projects_root
    if args.extractor in {"project-sources", "all"}:
        if projects_root is None:
            projects_root = get_projects_path()
        assert projects_root is not None
        _refresh_project_sources(projects_root.resolve(), args.output_dir.resolve())
        print(args.output_dir / "project-source-facts.json")
    if args.extractor in {"jarvis-values", "all"}:
        _refresh_jarvis_values(args.output_dir.resolve())
        print(args.output_dir / "jarvis-value-facts.json")


if __name__ == "__main__":
    main()
