"""RepoReapers reconstruction lineage over the release inventory.

The reconstruction inventory names every collected source behind the RQ6 audit by
content identity. The release accounts for each one with exactly one inventory
input, and the staged evidence must reproduce the recorded identities.
"""

from __future__ import annotations

import csv
import json
from pathlib import Path

from teralizer.eval.evidence.reporeapers_reconstruction import _digest_source
from teralizer.release.inventory import Inventory

RECONSTRUCTION_INVENTORY = Path(
    "analysis/data/report-inputs/reporeapers-reconstruction-inventory.json"
)

# The checkout-revision map has one row per corpus project with these columns.
CHECKOUT_COLUMNS = ("project", "revision", "origin")


def load_sources(repo: Path) -> dict[str, dict[str, object]]:
    document = json.loads((repo / RECONSTRUCTION_INVENTORY).read_text())
    return {source["source_id"]: source for source in document["sources"]}


def closure(inventory: Inventory, sources: dict[str, dict[str, object]]) -> list[str]:
    """Every collected source has exactly one inventory input, and no input names an unknown one."""
    problems: list[str] = []
    claimed: dict[str, list[str]] = {}
    for item in inventory.inputs:
        if item.reconstruction_source is not None:
            claimed.setdefault(item.reconstruction_source, []).append(item.id)
    for source_id in sorted(sources):
        owners = claimed.get(source_id, [])
        if len(owners) != 1:
            problems.append(
                f"reconstruction source {source_id}: needs exactly one inventory input, has {owners}"
            )
    for source_id in sorted(set(claimed) - set(sources)):
        problems.append(
            f"inputs {claimed[source_id]} name unknown reconstruction source {source_id}"
        )
    return problems


def read_checkout_map(path: Path) -> list[dict[str, str]]:
    with path.open(newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if tuple(reader.fieldnames or ()) != CHECKOUT_COLUMNS:
            raise ValueError(f"{path}: columns must be {list(CHECKOUT_COLUMNS)}")
        return list(reader)


def corpus_revision_mismatches(
    checkout_rows: list[dict[str, str]], corpus_rows: list[tuple[str, str | None]]
) -> list[str]:
    """Projects whose corpus git_version differs from the staged checkout revision.

    ``corpus_rows`` are ``(root_path, git_version)`` pairs of the real-world corpus.
    Projects that failed before recording a revision have no git_version and are
    not compared.
    """
    revisions = {row["project"]: row["revision"] for row in checkout_rows}
    problems: list[str] = []
    for root_path, git_version in corpus_rows:
        project = root_path.rstrip("/").split("/")[-1]
        if git_version is not None and revisions.get(project) != git_version:
            problems.append(
                f"{project}: corpus revision {git_version}, staged {revisions.get(project)}"
            )
    return problems


def verify_staged(
    inventory: Inventory,
    sources: dict[str, dict[str, object]],
    staged: Path,
    dump_sha256: str | None = None,
) -> list[str]:
    """Compare staged evidence with the identities the reconstruction recorded."""
    problems: list[str] = []
    for item in inventory.inputs:
        source_id = item.reconstruction_source
        if source_id is None or source_id not in sources:
            continue
        source = sources[source_id]
        expected = source["sha256"]
        if item.location == "corpus-package":
            if item.disposition == "payload" and dump_sha256 is not None:
                if dump_sha256 != expected:
                    problems.append(
                        f"{source_id}: corpus dump {dump_sha256} differs from {expected}"
                    )
            continue
        if item.release_path is None:
            continue
        path = staged / item.release_path
        if not path.exists():
            problems.append(
                f"{source_id}: staged evidence {item.release_path} is missing"
            )
            continue
        if str(source["kind"]).startswith("virtual"):
            rows = read_checkout_map(path)
            projects = [row["project"] for row in rows]
            listed = [str(name) for name in source["paths"]]  # type: ignore[union-attr]
            if sorted(projects) != sorted(listed) or len(projects) != len(
                set(projects)
            ):
                problems.append(
                    f"{source_id}: staged map does not list each of the {len(listed)} recorded projects once"
                )
            continue
        observed = _digest_source(path)[0]
        if observed != expected:
            problems.append(
                f"{source_id}: staged {item.release_path} has digest {observed}, recorded {expected}"
            )
    return problems
