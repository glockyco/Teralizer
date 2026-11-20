"""Release maintenance commands.

uv run --directory analysis python -m teralizer.release inventory [--repo PATH]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from teralizer.release import inventory

_REPO_ROOT = Path(__file__).resolve().parents[4]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m teralizer.release")
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser(
        "inventory", help="check the release input inventory against a checkout"
    )
    check.add_argument("--repo", type=Path, default=_REPO_ROOT)
    check.add_argument("--inventory", type=Path, default=inventory.DEFAULT_INVENTORY)
    args = parser.parse_args(argv)

    try:
        declared = inventory.load(args.inventory)
    except inventory.InventoryError as error:
        print(f"invalid inventory: {error}", file=sys.stderr)
        return 2
    problems = inventory.check(declared, args.repo)
    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        print(f"{len(problems)} inventory problems", file=sys.stderr)
        return 1
    print(
        f"inventory complete: {len(declared.inputs)} inputs, "
        f"{len(declared.ambient)} ambient rules"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
