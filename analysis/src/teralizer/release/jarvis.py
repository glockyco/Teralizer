"""JARVIS value-log lineage for the release.

`jarvis-value-facts.json` binds the value logs it was built from by count, bytes
and an aggregate checksum. The release copies exactly those logs into core at
their repository-relative paths, so the facts can be regenerated and checked from
the release alone.
"""

from __future__ import annotations

import json
import shutil
from collections.abc import Iterable
from pathlib import Path

from sqlalchemy.engine import Connection

from teralizer.eval.evidence import jarvis_values
from teralizer.eval.reports.rq0_jarvis import CENSUS_VARIANT
from teralizer.jarvis_scoreboard import (
    SWEEP_VARIANTS,
    get_generated_test_runs,
    get_scoreboard,
)

FACTS = Path("analysis/data/report-inputs/jarvis-value-facts.json")


def _relative(paths: Iterable[object], repo: Path) -> list[str]:
    selected = {
        Path(str(path)).resolve().relative_to(repo.resolve()).as_posix()
        for path in paths
        if path is not None and str(path) != "nan"
    }
    return sorted(selected)


def selected_value_logs(
    scenarios: Connection, benchmark: Connection, repo: Path
) -> dict[str, list[str]]:
    """Repository-relative value logs that the facts bind, per JARVIS corpus."""
    scoreboard = get_scoreboard(scenarios, variants=SWEEP_VARIANTS)
    census = get_generated_test_runs(benchmark, variants=(CENSUS_VARIANT,))
    return {
        "scoreboard_value_logs": _relative(
            scoreboard.get("jqwik_value_log_path", []), repo
        ),
        "census_value_logs": _relative(census.get("jqwik_value_log_path", []), repo),
    }


def stage(selection: dict[str, list[str]], repo: Path, staged: Path) -> int:
    """Copy the selected logs from a checkout into the staged evidence root."""
    copied = 0
    for paths in selection.values():
        for relative in paths:
            target = staged / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(repo / relative, target)
            copied += 1
    return copied


def verify(selection: dict[str, list[str]], staged: Path, facts: Path) -> list[str]:
    """Compare staged logs with the identities recorded in the facts document."""
    sources = json.loads(facts.read_text())["sources"]
    problems: list[str] = []
    for key, paths in selection.items():
        recorded = sources[key]
        missing = [path for path in paths if not (staged / path).is_file()]
        if missing:
            problems.append(f"{key}: {len(missing)} selected logs are not staged")
            continue
        observed = jarvis_values.file_set_identity(staged / path for path in paths)
        if observed != recorded:
            problems.append(
                f"{key}: staged logs {observed} differ from recorded {recorded}"
            )
    return problems
