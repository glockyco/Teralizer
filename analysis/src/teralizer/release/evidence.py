"""One verdict on a report run: its outputs and, for RQ0, the JARVIS inputs.

The verifier compares every output of the run with the reference manifest. When the
run includes RQ0, it also checks RQ0's file inputs against the raw evidence in the
release: the JARVIS value logs must be the logs the compact JARVIS facts were built
from, and the CUT-PVC captures must aggregate to `cut_values.tsv`. RQ0 reads only
the compact files, so a changed raw file would otherwise pass unnoticed.
"""

from __future__ import annotations

import json
from collections.abc import Callable, Collection
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from teralizer.release import outputs

JarvisCheck = Callable[[], list[str]]


@dataclass(frozen=True)
class Verdict:
    outputs: list[outputs.Comparison]
    jarvis_problems: list[str] | None

    @property
    def passed(self) -> bool:
        return (
            all(result.passed for result in self.outputs) and not self.jarvis_problems
        )

    def summary(self) -> dict[str, Any]:
        return {
            "passed": self.passed,
            "outputs": outputs.summary(self.outputs),
            "jarvis_inputs": None
            if self.jarvis_problems is None
            else {"passed": not self.jarvis_problems, "problems": self.jarvis_problems},
        }


def verify(
    analysis: Path,
    reference: Path,
    reports: Collection[str] | None,
    jarvis_check: JarvisCheck,
) -> Verdict:
    """Verify the run in ``analysis`` for ``reports``, or for every report."""
    compared = outputs.compare(json.loads(reference.read_text()), analysis, reports)
    includes_rq0 = reports is None or "rq0" in reports
    return Verdict(compared, jarvis_check() if includes_rq0 else None)


def release_jarvis_check(repo: Path) -> list[str]:
    """Compare the value logs and CUT-PVC captures in ``repo`` with RQ0's inputs."""
    from contextlib import ExitStack

    from teralizer import corpora
    from teralizer.release import jarvis

    with ExitStack() as stack:
        _, scenarios = stack.enter_context(corpora.open_corpus("jarvis-scenarios"))
        _, benchmark = stack.enter_context(corpora.open_corpus("jarvis-benchmark"))
        selection = jarvis.selected_value_logs(scenarios, benchmark)
    return jarvis.verify(
        selection, repo, repo / jarvis.FACTS
    ) + jarvis.cut_value_problems(repo)
