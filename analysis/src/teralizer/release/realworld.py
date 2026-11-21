"""Export the real-world project sources as the measured checkouts held them.

The version-seven run cloned each project with the pipeline's ProjectDownloadTask,
which uses JGit, on a host whose Git configuration set core.autocrlf=true and the
Git LFS smudge filter. The checkout map in the staged evidence records the
revision and origin of every project. Export replays that checkout:
MeasuredCheckout.java converts every file of the revision with the same JGit
version and configuration, and export writes the results without the Git
history and without the pipeline output that the run left in the checkouts.
The real-world project components read the exported trees.

A few measured checkouts deviate from their revision, and the deviation file
declares each case, so that export reproduces the measured tree on every file
system:

- collision: the revision holds paths that differ only in letter case. The
  case-insensitive checkout kept one file, with the declared name and blob.
- directory: the revision holds directories whose names differ only in letter
  case. The checkout kept the declared spelling and put every file into it.
- renamed: a path is not valid UTF-8. JGit wrote it under its Latin-1 reading,
  which the file system stored in decomposed (NFD) form.
- absent: the checkout ended early and never wrote the declared path.
"""

from __future__ import annotations

import csv
import os
import shutil
import struct
import subprocess
import tempfile
import unicodedata
from collections import defaultdict
from collections.abc import Iterator
from dataclasses import dataclass, field
from pathlib import Path
from typing import BinaryIO, Protocol

CHECKOUT_MAP = "data/reporeapers-rerun-v7/checkout-revisions.tsv"
SAMPLE_LIST = "project-configs/replication/real-world-sample.txt"
DEVIATIONS = "scripts/packaging/release/real-world-checkout-deviations.tsv"
DEVIATION_KINDS = ("collision", "directory", "renamed", "absent")
MEASURED_CHECKOUT = "scripts/packaging/release/MeasuredCheckout.java"

# Git records a submodule as a commit, not as files. The run's checkouts held no
# submodule content, so export leaves these paths out.
_SUBMODULE_MODE = "160000"
_LINK_MODE = 0o120000
_EXECUTABLE_MODE = 0o100755


class ExportError(RuntimeError):
    """A project could not be exported exactly as its checkout was measured."""


@dataclass(frozen=True)
class Project:
    name: str
    revision: str
    origin: str


@dataclass(frozen=True)
class TreeEntry:
    mode: str
    blob: str


@dataclass(frozen=True)
class Deviation:
    """One declared difference between a measured checkout and its revision."""

    project: str
    kind: str
    path: str
    blob: str


@dataclass(frozen=True)
class CheckoutFile:
    """One file as the pipeline's checkout writes it."""

    mode: int
    path: str
    blob: str
    content: bytes


@dataclass
class _ProjectPlan:
    """Where each revision path lands in the measured tree.

    A path without a placement is not part of the measured checkout.
    """

    placement: dict[str, str] = field(default_factory=dict)
    used: set[Deviation] = field(default_factory=set)


def checkout_map(path: Path) -> list[Project]:
    with path.open(newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    return [Project(row["project"], row["revision"], row["origin"]) for row in rows]


def deviations(path: Path) -> list[Deviation]:
    with path.open(newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    declared = [Deviation(r["project"], r["kind"], r["path"], r["blob"]) for r in rows]
    unknown = sorted({d.kind for d in declared} - set(DEVIATION_KINDS))
    if unknown:
        raise ExportError(f"{path} uses unknown deviation kinds {unknown}")
    if len(declared) != len(set(declared)):
        raise ExportError(f"{path} declares a deviation twice")
    return declared


def sample(path: Path) -> list[str]:
    """Project names of the reduced real-world collection, one per line."""
    names = [line.strip() for line in path.read_text().splitlines() if line.strip()]
    if len(names) != len(set(names)):
        raise ExportError(f"{path} lists a project twice")
    return names


def _git(repository: Path, *arguments: str) -> bytes:
    return subprocess.run(
        ["git", "-C", str(repository), *arguments],
        capture_output=True,
        check=True,
        env={**os.environ, "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_NOSYSTEM": "1"},
    ).stdout


def _tree(repository: Path, revision: str) -> dict[str, TreeEntry]:
    """Every file and link that the revision records, with mode and blob.

    Paths decode as the file system decodes them, so a path that is not valid
    UTF-8 keeps its bytes as surrogate escapes.
    """
    listing = _git(repository, "ls-tree", "-r", "-z", revision)
    entries = {}
    for record in filter(None, listing.split(b"\0")):
        meta, path = record.split(b"\t", 1)
        mode, _, blob = meta.decode().split(" ")
        if mode != _SUBMODULE_MODE:
            entries[os.fsdecode(path)] = TreeEntry(mode, blob)
    return entries


def tree_files(root: Path) -> list[Path]:
    """Every file and link below ``root`` in path order, links not followed."""
    paths = []
    for directory, names, files in os.walk(root):
        names.sort()
        base = Path(directory)
        # os.walk lists a link to a directory among the directories.
        paths.extend(base / name for name in names if (base / name).is_symlink())
        paths.extend(base / name for name in files)
    return sorted(paths)


def _exported(root: Path) -> set[str]:
    return {path.relative_to(root).as_posix() for path in tree_files(root)}


def _utf8(path: str) -> bool:
    try:
        os.fsencode(path).decode("utf-8")
    except UnicodeDecodeError:
        return False
    return True


def _plan_project(
    project: Project, tree: dict[str, TreeEntry], declared: list[Deviation]
) -> _ProjectPlan:
    """Apply the project's declared deviations to its revision tree."""
    mine = [item for item in declared if item.project == project.name]
    plan = _ProjectPlan()

    def declared_for(kind: str, folded: str, spellings: list[str]) -> Deviation:
        matches = [
            item
            for item in mine
            if item.kind == kind and item.path.casefold() == folded
        ]
        if len(matches) != 1 or matches[0].path not in spellings:
            raise ExportError(
                f"{project.name}: {kind} paths {sorted(spellings)} differ only in "
                f"case and {DEVIATIONS} declares none of them as the measured one"
            )
        plan.used.add(matches[0])
        return matches[0]

    # A case-insensitive checkout keeps one spelling of a directory and puts
    # every file under the variants into it.
    spellings: dict[str, set[str]] = defaultdict(set)
    for path in tree:
        parts = path.split("/")
        for end in range(1, len(parts)):
            spellings["/".join(parts[:end]).casefold()].add("/".join(parts[:end]))
    merged: dict[str, str] = {}
    for folded, variants in spellings.items():
        if len(variants) > 1:
            kept = declared_for("directory", folded, sorted(variants)).path
            merged |= {variant: kept for variant in variants if variant != kept}

    def measured_path(path: str) -> str:
        parts = path.split("/")
        for end in range(len(parts) - 1, 0, -1):
            directory = "/".join(parts[:end])
            if directory in merged:
                return "/".join([merged[directory], *parts[end:]])
        return path

    absent = set()
    for item in mine:
        if item.kind != "absent":
            continue
        entry = tree.get(item.path)
        if entry is None or entry.blob != item.blob:
            raise ExportError(
                f"{project.name}: declared absent path {item.path} with blob "
                f"{item.blob} is not in revision {project.revision}"
            )
        absent.add(item.path)
        plan.used.add(item)

    groups: dict[str, list[str]] = defaultdict(list)
    for path in tree:
        if path not in absent:
            groups[measured_path(path).casefold()].append(path)
    for folded, paths in groups.items():
        if len(paths) == 1:
            (path,) = paths
            if _utf8(path):
                plan.placement[path] = measured_path(path)
                continue
            name = unicodedata.normalize("NFD", os.fsencode(path).decode("latin-1"))
            matches = [
                item
                for item in mine
                if item.kind == "renamed"
                and item.path == name
                and item.blob == tree[path].blob
            ]
            if len(matches) != 1:
                raise ExportError(
                    f"{project.name}: path {os.fsencode(path)!r} is not valid UTF-8 "
                    f"and {DEVIATIONS} declares no measured name for it"
                )
            plan.used.add(matches[0])
            plan.placement[path] = name
            continue
        collision = declared_for(
            "collision", folded, [measured_path(path) for path in paths]
        )
        sources = sorted(path for path in paths if tree[path].blob == collision.blob)
        if not sources:
            raise ExportError(
                f"{project.name}: declared file {collision.path} has blob "
                f"{collision.blob}, which is not one of {sorted(paths)}"
            )
        plan.placement[sources[0]] = collision.path
    return plan


class Checkout(Protocol):
    """Produces every file of a revision as the measured checkout wrote it."""

    def files(self, repository: Path, revision: str) -> Iterator[CheckoutFile]: ...


class MeasuredCheckoutRunner:
    """MeasuredCheckout.java in one Java process, with an isolated configuration.

    The process reads no user or system Git configuration, so the exporting
    host's settings cannot change the converted bytes.
    """

    def __init__(self, repo: Path) -> None:
        self._work = Path(tempfile.mkdtemp(prefix="measured-checkout-"))
        classpath = (
            subprocess.run(
                ["./gradlew", "-q", "measuredCheckoutClasspath"],
                cwd=repo,
                capture_output=True,
                text=True,
                check=True,
            )
            .stdout.strip()
            .splitlines()[-1]
        )
        classes = self._work / "classes"
        classes.mkdir()
        subprocess.run(
            [
                "javac",
                "-d",
                str(classes),
                "-cp",
                classpath,
                str(repo / MEASURED_CHECKOUT),
            ],
            check=True,
        )
        home = self._work / "home"
        home.mkdir()
        self._process = subprocess.Popen(
            [
                "java",
                f"-Duser.home={home}",
                "-cp",
                f"{classes}{os.pathsep}{classpath}",
                "MeasuredCheckout",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            env={
                **os.environ,
                "HOME": str(home),
                "XDG_CONFIG_HOME": str(home / ".config"),
                "GIT_CONFIG_NOSYSTEM": "1",
            },
        )

    def files(self, repository: Path, revision: str) -> Iterator[CheckoutFile]:
        assert self._process.stdin is not None and self._process.stdout is not None
        self._process.stdin.write(f"{repository}\t{revision}\n".encode())
        self._process.stdin.flush()
        stream = self._process.stdout
        while True:
            kind = _read(stream, 1)
            if kind == b"P":
                return
            if kind != b"E":
                raise ExportError(f"MeasuredCheckout failed on {repository}")
            (mode,) = struct.unpack(">i", _read(stream, 4))
            (length,) = struct.unpack(">i", _read(stream, 4))
            path = os.fsdecode(_read(stream, length))
            blob = _read(stream, 20).hex()
            (size,) = struct.unpack(">q", _read(stream, 8))
            yield CheckoutFile(mode, path, blob, _read(stream, size))

    def close(self) -> None:
        if self._process.stdin is not None:
            self._process.stdin.close()
        if self._process.wait() != 0:
            raise ExportError("MeasuredCheckout exited with an error")
        shutil.rmtree(self._work)


def _read(stream: BinaryIO, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise ExportError("MeasuredCheckout output ended early")
    return data


def _write(destination: Path, relative: str, file: CheckoutFile) -> None:
    if any(part in ("", ".", "..") for part in relative.split("/")):
        raise ExportError(f"unsafe path {relative!r}")
    target = destination / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    if file.mode == _LINK_MODE:
        target.symlink_to(os.fsdecode(file.content))
        return
    target.write_bytes(file.content)
    target.chmod(0o755 if file.mode == _EXECUTABLE_MODE else 0o644)


def export_project(
    checkouts: Path,
    project: Project,
    destination: Path,
    declared: list[Deviation],
    checkout: Checkout,
) -> set[Deviation]:
    """Write the measured tree of ``project`` to ``destination``.

    Returns the declared deviations that this project used.
    """
    repository = checkouts / project.name
    if not (repository / ".git").exists():
        raise ExportError(f"{project.name}: no Git checkout at {repository}")
    origin = _git(repository, "config", "--get", "remote.origin.url").decode().strip()
    if origin != project.origin:
        raise ExportError(
            f"{project.name}: checkout origin {origin} is not {project.origin}"
        )
    tree = _tree(repository, project.revision)
    plan = _plan_project(project, tree, declared)

    destination.mkdir(parents=True)
    streamed = set()
    for file in checkout.files(repository.resolve(), project.revision):
        streamed.add(file.path)
        if tree.get(file.path) != TreeEntry(f"{file.mode:06o}", file.blob):
            raise ExportError(f"{project.name}: {file.path} differs from the revision")
        measured = plan.placement.get(file.path)
        if measured is not None:
            _write(destination, measured, file)
    if streamed != set(tree):
        raise ExportError(f"{project.name}: the checkout did not stream every file")

    expected = set(plan.placement.values())
    actual = _exported(destination)
    if expected != actual:
        missing, extra = sorted(expected - actual), sorted(actual - expected)
        raise ExportError(
            f"{project.name}: export differs from the measured tree: "
            f"missing {missing[:5]}, extra {extra[:5]}"
        )
    return plan.used


def export(
    checkouts: Path,
    map_path: Path,
    deviations_path: Path,
    out: Path,
    checkout: Checkout,
) -> int:
    """Export every mapped project below ``out/projects`` and return their number."""
    if out.exists():
        raise ExportError(f"export directory already exists: {out}")
    partial = out.with_name(out.name + ".partial")
    if partial.exists():
        shutil.rmtree(partial)
    declared = deviations(deviations_path)
    used: set[Deviation] = set()
    projects = checkout_map(map_path)
    for project in projects:
        used |= export_project(
            checkouts, project, partial / "projects" / project.name, declared, checkout
        )
    unused = [item for item in declared if item not in used]
    if unused:
        raise ExportError(
            f"{deviations_path} declares deviations that no revision has: {unused[:5]}"
        )
    partial.rename(out)
    return len(projects)
