"""Release maintenance commands.

uv run --directory analysis python -m teralizer.release inventory [--repo PATH]
uv run --directory analysis python -m teralizer.release report-inputs [--json PATH]
"""

from __future__ import annotations

import argparse
import dataclasses
import json
import sys
from pathlib import Path

from teralizer.release import inventory

_REPO_ROOT = Path(__file__).resolve().parents[4]


def _report_problems(problems: list[str], subject: str) -> int:
    for problem in problems:
        print(problem, file=sys.stderr)
    print(f"{len(problems)} {subject} problems", file=sys.stderr)
    return 1


def _inventory(declared: inventory.Inventory, repo: Path) -> int:
    problems = inventory.check(declared, repo)
    if problems:
        return _report_problems(problems, "inventory")
    print(
        f"inventory complete: {len(declared.inputs)} inputs, "
        f"{len(declared.ambient)} ambient rules"
    )
    return 0


def _report_inputs(
    declared: inventory.Inventory, repo: Path, output: Path | None
) -> int:
    import teralizer.eval.reports  # noqa: F401  (registers every report)
    from teralizer.eval.registry import REPORTS
    from teralizer.release import report_inputs

    resolved, problems = report_inputs.resolve(declared, REPORTS, repo)
    if problems:
        return _report_problems(problems, "report-input")
    for item in resolved:
        checksum = item.sha256 or "-"
        print(
            f"{item.report}\t{item.role}\t{item.kind}\t{item.source}\t{item.owner}\t{checksum}\t{item.status}"
        )
    if output is not None:
        output.write_text(
            json.dumps([dataclasses.asdict(item) for item in resolved], indent=2) + "\n"
        )
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m teralizer.release")
    parser.add_argument("--repo", type=Path, default=_REPO_ROOT)
    parser.add_argument("--inventory", type=Path, default=inventory.DEFAULT_INVENTORY)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser(
        "inventory", help="check the release input inventory against a checkout"
    )
    closure = commands.add_parser(
        "report-inputs",
        help="resolve every registered report input to its component and status",
    )
    closure.add_argument("--json", type=Path, dest="output")
    args = parser.parse_args(argv)

    try:
        declared = inventory.load(args.inventory)
    except inventory.InventoryError as error:
        print(f"invalid inventory: {error}", file=sys.stderr)
        return 2
    if args.command == "inventory":
        return _inventory(declared, args.repo)
    return _report_inputs(declared, args.repo, args.output)


if __name__ == "__main__":
    sys.exit(main())
