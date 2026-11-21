"""Ordered source-to-archive plan for every release component.

The plan lists every archive member with the file that supplies its bytes, before
any archive is written. Building the plan reads directory listings and file
modes only. Validation then proves that each member has exactly one owner, sits
inside its component root, and that every payload input contributes.
"""

from __future__ import annotations

import fnmatch
import json
import os
import stat
from collections.abc import Iterable, Iterator
from dataclasses import dataclass, field
from pathlib import Path

from teralizer.release import realworld
from teralizer.release.archive import Entry, link_stays_inside, safe_name
from teralizer.release.declaration import Declaration
from teralizer.release.inventory import Inventory

# Pipeline output, build output and IDE state at the top of a project root. Deeper
# directories with these names can be project sources, such as test resources
# under `data/`, and stay.
PROJECT_RESIDUE_DIRS = frozenset({"target", ".idea", "data", ".gradle", "build"})
PROJECT_RESIDUE_FILES = (
    ".DS_Store",
    ".jqwik-database",
    "pom.teralizer.xml",
    "pom.teralizer.generalized.xml",
    "build.teralizer.gradle",
    "_*_Generalized_*",
    "_*_Driver_*",
    "_*_Instrumented_*",
)

CONTROLLED_PROJECTS = (
    "commons-utils",
    "commons-utils-es-default-1s",
    "commons-utils-es-default-10s",
    "commons-utils-es-default-60s",
    "eqbench-es-default-1s",
    "eqbench-es-default-10s",
    "eqbench-es-default-60s",
)
SCOREBOARD_FIXTURES = ("commons-lang-2017-02-01", "commons-math-2017-02-01")
CENSUS_FIXTURE_PATTERN = "*-2017-02-01-census"


class PlanError(ValueError):
    """The plan violates a release rule."""


@dataclass(frozen=True)
class Sources:
    """Where the builder finds the bytes of each input."""

    # Output of `python -m teralizer.release source`.
    source: Path
    # Release-relative evidence staged from the evaluation host and the checkout.
    evidence: Path
    # The verified four-corpus package.
    corpus_package: Path
    # A checkout whose ambient project and fixture roots hold the measured inputs.
    checkout: Path
    # Output of `python -m teralizer.release export-real-world`, if built.
    real_world: Path | None = None


@dataclass
class ComponentPlan:
    component: str
    root: str
    entries: list[Entry] = field(default_factory=list)
    # Inventory inputs that contributed at least one entry.
    inputs: set[str] = field(default_factory=set)

    def add(self, input_id: str, relative: str, source: Path) -> None:
        mode = source.stat().st_mode
        self.entries.append(
            Entry(
                name=f"{self.root}/{relative}",
                source=source,
                executable=bool(mode & stat.S_IXUSR),
            )
        )
        self.inputs.add(input_id)

    def add_link(self, input_id: str, relative: str, target: str) -> None:
        self.entries.append(Entry(name=f"{self.root}/{relative}", link=target))
        self.inputs.add(input_id)


def _walk(root: Path) -> Iterator[Path]:
    for directory, names, files in os.walk(root):
        names.sort()
        for name in sorted(files):
            path = Path(directory) / name
            if path.is_file() and not path.is_symlink():
                yield path


def project_files(root: Path) -> Iterator[Path]:
    """Files of a project root without pipeline output, IDE state or build output."""
    for directory, names, files in os.walk(root):
        top = Path(directory) == root
        names[:] = sorted(
            name for name in names if not (top and name in PROJECT_RESIDUE_DIRS)
        )
        for name in sorted(files):
            if any(
                fnmatch.fnmatchcase(name, pattern) for pattern in PROJECT_RESIDUE_FILES
            ):
                continue
            path = Path(directory) / name
            if path.is_file() and not path.is_symlink():
                yield path


def fixture_files(root: Path) -> Iterator[Path]:
    """The measured fixture inputs: its POM and its sources."""
    pom = root / "pom.xml"
    if not pom.is_file():
        raise PlanError(f"fixture {root} has no pom.xml")
    yield pom
    for path in project_files(root / "src"):
        yield path


def corpus_entry(corpus_package: Path, corpus_id: str) -> dict[str, object]:
    manifest = json.loads((corpus_package / "manifest.json").read_text())
    for entry in manifest["corpora"]:
        if entry["corpus_id"] == corpus_id:
            return entry
    raise PlanError(f"corpus package has no corpus {corpus_id}")


def build(
    declaration: Declaration, inventory: Inventory, sources: Sources
) -> list[ComponentPlan]:
    """Assemble the member plan of every component that has inputs available."""
    plans = {
        component.id: ComponentPlan(component.id, component.root)
        for component in declaration.components
    }
    core = plans["core"]
    controlled = plans["projects-controlled"]

    for path in _walk(sources.source):
        relative = path.relative_to(sources.source).as_posix()
        input_id = _source_input(inventory, relative)
        plans[inventory.input(input_id).component or "core"].add(
            input_id, relative, path
        )

    for path in _walk(sources.evidence):
        relative = path.relative_to(sources.evidence).as_posix()
        core.add(_evidence_input(inventory, relative), relative, path)

    core.add(
        "corpus-manifest",
        "replication/corpus-manifest.json",
        sources.corpus_package / "manifest.json",
    )
    for component in declaration.components:
        if component.corpus is None:
            continue
        entry = corpus_entry(sources.corpus_package, component.corpus)
        dump = entry["dump"]
        assert isinstance(dump, dict)
        plans[component.id].add(
            f"corpus-{component.corpus}",
            str(dump["path"]),
            sources.corpus_package / str(dump["path"]),
        )

    projects = sources.checkout / "projects"
    for name in CONTROLLED_PROJECTS:
        for path in project_files(projects / name):
            controlled.add(
                "controlled-projects",
                path.relative_to(sources.checkout).as_posix(),
                path,
            )

    scoreboard = sources.checkout / "data/jarvis-scoreboard/fixtures"
    for name in SCOREBOARD_FIXTURES:
        for path in fixture_files(scoreboard / name):
            plans["projects-jarvis-scoreboard"].add(
                "jarvis-scoreboard-fixtures",
                path.relative_to(sources.checkout).as_posix(),
                path,
            )
    census = sources.checkout / "data/jarvis-census/fixtures"
    for root in sorted(census.glob(CENSUS_FIXTURE_PATTERN)):
        for path in fixture_files(root):
            plans["projects-jarvis-census"].add(
                "jarvis-census-fixtures",
                path.relative_to(sources.checkout).as_posix(),
                path,
            )
    if sources.real_world is not None:
        _plan_real_world(sources, plans)
    return [plans[component.id] for component in declaration.components]


def _plan_real_world(sources: Sources, plans: dict[str, ComponentPlan]) -> None:
    """Every exported project into the full component, the sample into its own.

    Both components read the same exported files, so a sample project's members
    equal its members in the full component by construction.
    """
    assert sources.real_world is not None
    exported = sources.real_world / "projects"
    mapped = [
        project.name
        for project in realworld.checkout_map(sources.evidence / realworld.CHECKOUT_MAP)
    ]
    present = sorted(path.name for path in exported.iterdir())
    if present != sorted(mapped):
        missing = sorted(set(mapped) - set(present))
        extra = sorted(set(present) - set(mapped))
        raise PlanError(
            f"exported projects differ from the checkout map: "
            f"missing {missing[:5]}, extra {extra[:5]}"
        )
    sample = set(realworld.sample(sources.source / realworld.SAMPLE_LIST))
    unknown = sorted(sample - set(present))
    if unknown:
        raise PlanError(f"sample projects without an export: {unknown[:5]}")

    targets = [("projects-real-world-full", "reporeapers-project-checkouts", None)]
    targets.append(("projects-real-world-sample", "reporeapers-project-sample", sample))
    for name in present:
        project_root = f"projects/{name}"
        for path in realworld.tree_files(exported / name):
            relative = f"{project_root}/{path.relative_to(exported / name).as_posix()}"
            target = os.readlink(path) if path.is_symlink() else None
            if target is not None and not link_stays_inside(
                relative, target, project_root
            ):
                raise PlanError(f"link {relative} -> {target} leaves {project_root}")
            for component, input_id, members in targets:
                if members is not None and name not in members:
                    continue
                if target is None:
                    plans[component].add(input_id, relative, path)
                else:
                    plans[component].add_link(input_id, relative, target)


def _source_input(inventory: Inventory, relative: str) -> str:
    """The inventory input that owns one file of the materialized source tree.

    A file inside a nested source belongs to its innermost submodule input. An LFS
    file belongs to its git-lfs input. Everything else is the committed tree.
    """
    nested = [
        item
        for item in inventory.inputs
        if item.location == "submodule"
        and item.path
        and relative.startswith(item.path + "/")
    ]
    if nested:
        return max(nested, key=lambda item: len(item.path or "")).id
    for item in inventory.inputs:
        if (
            item.location == "git-lfs"
            and item.path
            and fnmatch.fnmatchcase(relative, item.path)
        ):
            return item.id
    return "source-tree"


def _evidence_input(inventory: Inventory, relative: str) -> str:
    """The inventory input that owns one staged evidence path."""
    for item in inventory.inputs:
        if item.release_path and (
            relative == item.release_path
            or relative.startswith(item.release_path + "/")
        ):
            return item.id
    if relative.endswith(".values.tsv"):
        return "jarvis-value-logs"
    if relative.startswith("data/cut-pvc-capture/raw/"):
        return "cut-pvc-raw-captures"
    if relative.startswith(("data/jarvis-scoreboard/", "data/jarvis-census/")):
        return "jarvis-run-ledgers"
    raise PlanError(f"staged evidence {relative} belongs to no inventory input")


def validate(
    plans: Iterable[ComponentPlan],
    inventory: Inventory,
    *,
    required: Iterable[str],
) -> list[str]:
    """Release rules over a plan, checked before any archive is written."""
    plans = list(plans)
    problems: list[str] = []
    owners: dict[str, str] = {}
    for plan in plans:
        for entry in plan.entries:
            if not safe_name(entry.name):
                problems.append(f"{plan.component}: unsafe member {entry.name}")
            if not entry.name.startswith(plan.root + "/"):
                problems.append(
                    f"{plan.component}: member {entry.name} is outside {plan.root}"
                )
            previous = owners.setdefault(entry.name, plan.component)
            if previous != plan.component:
                problems.append(
                    f"member {entry.name} is owned by {previous} and {plan.component}"
                )
        names = [entry.name for entry in plan.entries]
        if len(names) != len(set(names)):
            problems.append(f"{plan.component}: repeated members")

    contributed = {input_id for plan in plans for input_id in plan.inputs}
    by_component = {plan.component: plan for plan in plans}
    for component in required:
        if not by_component.get(component) or not by_component[component].entries:
            problems.append(f"component {component} has no members")
    for item in inventory.inputs:
        if item.disposition != "payload" or item.component not in set(required):
            continue
        if item.id not in contributed:
            problems.append(
                f"payload input {item.id} contributed no member to {item.component}"
            )
    return problems
