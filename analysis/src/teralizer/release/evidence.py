"""One verdict on a report run: its outputs, the paper's claims and the JARVIS inputs.

The verifier compares every output of the run with the reference manifest, checks
every claim whose reports the run built, and, when the run includes RQ0, checks that
the JARVIS value logs in the release are the logs the compact JARVIS facts were built
from. RQ0 reads only those facts, so a changed log would otherwise pass unnoticed.

A run of selected reports is checked for those reports only. The verdict passes when
all three parts pass.
"""

from __future__ import annotations

import json
from collections.abc import Callable, Collection
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from teralizer.release import claims, outputs

JarvisCheck = Callable[[], list[str]]


@dataclass(frozen=True)
class Verdict:
    outputs: list[outputs.Comparison]
    claims: list[claims.Outcome]
    jarvis_problems: list[str] | None

    @property
    def passed(self) -> bool:
        return (
            all(result.passed for result in self.outputs)
            and all(outcome.passed for outcome in self.claims)
            and not self.jarvis_problems
        )

    def summary(self) -> dict[str, Any]:
        return {
            "passed": self.passed,
            "outputs": outputs.summary(self.outputs),
            "claims": claims.summary(self.claims),
            "jarvis_inputs": None
            if self.jarvis_problems is None
            else {"passed": not self.jarvis_problems, "problems": self.jarvis_problems},
        }


def selected_claims(
    declared: list[claims.Claim], reports: Collection[str] | None
) -> list[claims.Claim]:
    """The claims that a run of ``reports`` can establish, or all of them."""
    if reports is None:
        return declared
    return [claim for claim in declared if set(claim.reports) <= set(reports)]


def verify(
    analysis: Path,
    reference: Path,
    claims_path: Path,
    reports: Collection[str] | None,
    jarvis_check: JarvisCheck,
) -> Verdict:
    """Verify the run in ``analysis`` for ``reports``, or for every report."""
    compared = outputs.compare(json.loads(reference.read_text()), analysis, reports)
    checked = claims.verify(
        selected_claims(claims.load(claims_path), reports), claims.Run(analysis)
    )
    includes_rq0 = reports is None or "rq0" in reports
    return Verdict(compared, checked, jarvis_check() if includes_rq0 else None)


def release_jarvis_check(repo: Path) -> list[str]:
    """Compare the value logs in ``repo`` with the identities the facts record."""
    from contextlib import ExitStack

    from teralizer import corpora
    from teralizer.release import jarvis

    with ExitStack() as stack:
        _, scenarios = stack.enter_context(corpora.open_corpus("jarvis-scenarios"))
        _, benchmark = stack.enter_context(corpora.open_corpus("jarvis-benchmark"))
        selection = jarvis.selected_value_logs(scenarios, benchmark)
    return jarvis.verify(selection, repo, repo / jarvis.FACTS)
