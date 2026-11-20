"""Release input inventory: one disposition for every input and ambient path.

The inventory names every input the release can draw bytes from and assigns each
one payload component or an explicit non-payload disposition. Ambient rules cover
the ignored and untracked paths of a source checkout, so that a check against a
checkout proves no required byte lives only in author state.
"""

from __future__ import annotations

import fnmatch
import subprocess
import tomllib
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from pathlib import Path

COMPONENTS = (
    "core",
    "corpus-controlled",
    "corpus-real-world",
    "corpus-jarvis-scenarios",
    "corpus-jarvis-benchmark",
    "projects-controlled",
    "projects-real-world-sample",
    "projects-real-world-full",
    "projects-jarvis-scoreboard",
    "projects-jarvis-census",
)

INPUT_LOCATIONS = (
    "git-tree",
    "git-lfs",
    "submodule",
    "corpus-package",
    "checkout-ambient",
    "evaluation-host",
    # A published resource outside the release, named by URL or registry reference.
    "external",
)

# payload: bytes enter exactly one component.
# retrieval: the release keeps only how to obtain the bytes again.
# non-payload: the bytes exist but the release does not need them.
# duplicate: another input already ships the same bytes.
INPUT_DISPOSITIONS = ("payload", "retrieval", "non-payload", "duplicate")

AMBIENT_DISPOSITIONS = (
    "input",
    "sensitive",
    "cache",
    "build-output",
    "scratch",
    "unrelated",
)

DEFAULT_INVENTORY = (
    Path(__file__).resolve().parents[4] / "scripts/packaging/release/inventory.toml"
)


class InventoryError(ValueError):
    """The inventory document violates its schema."""


@dataclass(frozen=True)
class Input:
    id: str
    location: str
    disposition: str
    reason: str
    path: str | None = None
    selector: str | None = None
    component: str | None = None
    # Where an input from outside the source tree lands inside its component.
    release_path: str | None = None
    # The collected-source id of the RepoReapers reconstruction inventory this input
    # accounts for.
    reconstruction_source: str | None = None


@dataclass(frozen=True)
class AmbientRule:
    paths: tuple[str, ...]
    disposition: str
    reason: str
    inputs: tuple[str, ...] = ()


REPORT_INPUT_STATUSES = ("regenerable", "frozen")


@dataclass(frozen=True)
class ReportInput:
    """How the release reproduces one registered report file input."""

    path: str
    status: str
    producer: str
    needs: tuple[str, ...]
    reason: str


@dataclass(frozen=True)
class Inventory:
    expand: tuple[str, ...]
    inputs: tuple[Input, ...]
    ambient: tuple[AmbientRule, ...]
    report_inputs: tuple[ReportInput, ...] = ()

    def input(self, input_id: str) -> Input:
        for candidate in self.inputs:
            if candidate.id == input_id:
                return candidate
        raise KeyError(input_id)


def _text(entry: dict[str, object], key: str, where: str) -> str:
    value = entry.get(key)
    if not isinstance(value, str) or not value.strip():
        raise InventoryError(f"{where}: {key!r} must be a non-empty string")
    return value


def _optional_text(entry: dict[str, object], key: str, where: str) -> str | None:
    if key not in entry:
        return None
    return _text(entry, key, where)


def _texts(entry: dict[str, object], key: str, where: str) -> tuple[str, ...]:
    value = entry.get(key, [])
    if not isinstance(value, list) or not all(
        isinstance(item, str) and item for item in value
    ):
        raise InventoryError(f"{where}: {key!r} must be a list of non-empty strings")
    return tuple(value)


def _parse_input(entry: dict[str, object], index: int) -> Input:
    where = f"input {index + 1}"
    parsed = Input(
        id=_text(entry, "id", where),
        location=_text(entry, "location", where),
        disposition=_text(entry, "disposition", where),
        reason=_text(entry, "reason", where),
        path=_optional_text(entry, "path", where),
        selector=_optional_text(entry, "selector", where),
        component=_optional_text(entry, "component", where),
        release_path=_optional_text(entry, "release_path", where),
        reconstruction_source=_optional_text(entry, "reconstruction_source", where),
    )
    where = f"input {parsed.id!r}"
    if parsed.location not in INPUT_LOCATIONS:
        raise InventoryError(f"{where}: unknown location {parsed.location!r}")
    if parsed.disposition not in INPUT_DISPOSITIONS:
        raise InventoryError(f"{where}: unknown disposition {parsed.disposition!r}")
    if parsed.disposition == "payload":
        if parsed.component not in COMPONENTS:
            raise InventoryError(
                f"{where}: payload needs a declared component, got {parsed.component!r}"
            )
    elif parsed.component is not None:
        raise InventoryError(f"{where}: only payload inputs name a component")
    if parsed.release_path is not None and parsed.disposition != "payload":
        raise InventoryError(f"{where}: only payload inputs have a release path")
    if parsed.location != "git-tree" and parsed.path is None:
        raise InventoryError(f"{where}: location {parsed.location!r} needs a path")
    return parsed


def _parse_ambient(entry: dict[str, object], index: int) -> AmbientRule:
    where = f"ambient rule {index + 1}"
    rule = AmbientRule(
        paths=_texts(entry, "paths", where),
        disposition=_text(entry, "disposition", where),
        reason=_text(entry, "reason", where),
        inputs=_texts(entry, "inputs", where),
    )
    if not rule.paths:
        raise InventoryError(f"{where}: paths must not be empty")
    if rule.disposition not in AMBIENT_DISPOSITIONS:
        raise InventoryError(f"{where}: unknown disposition {rule.disposition!r}")
    if (rule.disposition == "input") != bool(rule.inputs):
        raise InventoryError(
            f"{where}: exactly the 'input' disposition names source inputs"
        )
    return rule


def _parse_report_input(entry: dict[str, object], index: int) -> ReportInput:
    where = f"report input {index + 1}"
    parsed = ReportInput(
        path=_text(entry, "path", where),
        status=_text(entry, "status", where),
        producer=_text(entry, "producer", where),
        needs=_texts(entry, "needs", where),
        reason=_text(entry, "reason", where),
    )
    where = f"report input {parsed.path!r}"
    if parsed.status not in REPORT_INPUT_STATUSES:
        raise InventoryError(f"{where}: unknown status {parsed.status!r}")
    unknown = sorted(set(parsed.needs) - set(COMPONENTS))
    if not parsed.needs or unknown:
        raise InventoryError(f"{where}: needs must name declared components {unknown}")
    return parsed


def _tables(document: dict[str, object], key: str) -> list[dict[str, object]]:
    value = document.get(key, [])
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise InventoryError(f"{key!r} must be an array of tables")
    return value


def parse(document: dict[str, object]) -> Inventory:
    """Validate an inventory document and its internal references."""
    inventory = Inventory(
        expand=_texts(document, "expand", "inventory"),
        inputs=tuple(
            _parse_input(entry, i) for i, entry in enumerate(_tables(document, "input"))
        ),
        ambient=tuple(
            _parse_ambient(entry, i)
            for i, entry in enumerate(_tables(document, "ambient"))
        ),
        report_inputs=tuple(
            _parse_report_input(entry, i)
            for i, entry in enumerate(_tables(document, "report_input"))
        ),
    )
    paths = [item.path for item in inventory.report_inputs]
    repeated = sorted({path for path in paths if paths.count(path) > 1})
    if repeated:
        raise InventoryError(f"duplicate report inputs: {', '.join(repeated)}")
    ids = [item.id for item in inventory.inputs]
    duplicates = sorted({item for item in ids if ids.count(item) > 1})
    if duplicates:
        raise InventoryError(f"duplicate input ids: {', '.join(duplicates)}")
    ambient_inputs = {
        item.id for item in inventory.inputs if item.location == "checkout-ambient"
    }
    referenced = {name for rule in inventory.ambient for name in rule.inputs}
    unknown = sorted(referenced - ambient_inputs)
    if unknown:
        raise InventoryError(
            f"ambient rules name inputs that are not checkout-ambient inputs: {', '.join(unknown)}"
        )
    unreferenced = sorted(ambient_inputs - referenced)
    if unreferenced:
        raise InventoryError(
            f"checkout-ambient inputs without an ambient rule: {', '.join(unreferenced)}"
        )
    return inventory


def load(path: Path = DEFAULT_INVENTORY) -> Inventory:
    with path.open("rb") as handle:
        return parse(tomllib.load(handle))


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        capture_output=True,
        text=True,
    ).stdout


def _nul_fields(output: str) -> list[str]:
    return [field for field in output.split("\0") if field]


def gitlinks(repo: Path, prefix: str = "") -> list[str]:
    """Repository-relative paths of every gitlink, including nested ones that are checked out."""
    found: list[str] = []
    for record in _nul_fields(_git(repo, "ls-files", "-s", "-z")):
        metadata, path = record.split("\t", 1)
        if metadata.split(" ", 1)[0] != "160000":
            continue
        found.append(prefix + path)
        nested = repo / path
        if (nested / ".git").exists():
            found.extend(gitlinks(nested, prefix + path + "/"))
    return found


def lfs_paths(repo: Path) -> list[str]:
    """Tracked paths whose Git attributes select the LFS filter."""
    tracked = _nul_fields(_git(repo, "ls-files", "-z"))
    if not tracked:
        return []
    attributes = subprocess.run(
        ["git", "-C", str(repo), "check-attr", "-z", "--stdin", "filter"],
        input="\0".join(tracked) + "\0",
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    fields = _nul_fields(attributes)
    return [
        fields[i]
        for i in range(0, len(fields), 3)
        if fields[i + 1] == "filter" and fields[i + 2] == "lfs"
    ]


def ambient_entries(repo: Path, expand: Sequence[str]) -> list[str]:
    """Ignored and untracked entries, with each expanded root replaced by its children."""
    records = _nul_fields(_git(repo, "status", "--porcelain=v1", "--ignored", "-z"))
    entries = [record[3:] for record in records if record[:2] in ("!!", "??")]
    pending = list(entries)
    result: list[str] = []
    while pending:
        entry = pending.pop()
        if entry in expand and (repo / entry).is_dir():
            for child in sorted((repo / entry).iterdir()):
                suffix = "/" if child.is_dir() else ""
                pending.append(f"{entry}{child.name}{suffix}")
        else:
            result.append(entry)
    return sorted(result)


def _matches(path: str, patterns: Iterable[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def _specificity(pattern: str) -> int:
    """Literal characters of a pattern. More literal text names a narrower path set."""
    return sum(1 for character in pattern if character not in "*?[]")


def ambient_rule(inventory: Inventory, entry: str) -> AmbientRule | str:
    """The rule whose matching pattern is most specific, or why none decides.

    Generic cache patterns such as ``*__pycache__/`` and path-scoped rules may both
    match one entry. The most specific matching pattern decides. Two rules that
    match with equally specific patterns are ambiguous.
    """
    best: list[tuple[int, AmbientRule]] = []
    for rule in inventory.ambient:
        scores = [
            _specificity(pattern)
            for pattern in rule.paths
            if fnmatch.fnmatchcase(entry, pattern)
        ]
        if scores:
            best.append((max(scores), rule))
    if not best:
        return "matches no ambient rule"
    top = max(score for score, _ in best)
    winners = [rule for score, rule in best if score == top]
    if len(winners) > 1:
        reasons = [rule.reason.split(".")[0] for rule in winners]
        return f"matches equally specific rules {reasons}"
    return winners[0]


def _brace_alternatives(pattern: str) -> list[str]:
    start = pattern.find("{")
    if start < 0:
        return [pattern]
    end = pattern.index("}", start)
    head, tail = pattern[:start], pattern[end + 1 :]
    return [
        expanded
        for option in pattern[start + 1 : end].split(",")
        for expanded in _brace_alternatives(head + option + tail)
    ]


def _input_matches(item: Input, path: str) -> bool:
    if item.path is None:
        return False
    return _matches(path, _brace_alternatives(item.path))


def check(inventory: Inventory, repo: Path) -> list[str]:
    """Return every disagreement between the inventory and a source checkout."""
    problems: list[str] = []

    submodules = [item for item in inventory.inputs if item.location == "submodule"]
    for path in gitlinks(repo):
        owners = [item.id for item in submodules if _input_matches(item, path)]
        if len(owners) != 1:
            problems.append(
                f"gitlink {path}: needs exactly one submodule input, has {owners}"
            )

    lfs_inputs = [item for item in inventory.inputs if item.location == "git-lfs"]
    for path in lfs_paths(repo):
        owners = [item.id for item in lfs_inputs if _input_matches(item, path)]
        if len(owners) != 1:
            problems.append(
                f"LFS file {path}: needs exactly one git-lfs input, has {owners}"
            )

    for entry in ambient_entries(repo, inventory.expand):
        decided = ambient_rule(inventory, entry)
        if isinstance(decided, str):
            problems.append(f"ambient path {entry}: {decided}")

    owned = {
        item.component for item in inventory.inputs if item.disposition == "payload"
    }
    for component in COMPONENTS:
        if component not in owned:
            problems.append(f"component {component}: no payload input")
    return problems
