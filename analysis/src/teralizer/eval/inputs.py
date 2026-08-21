"""Declared corpus and repository-file inputs for one report construction."""

from __future__ import annotations

import hashlib
import subprocess
from collections.abc import Iterator
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from sqlalchemy.engine import Connection

from teralizer import corpora
from teralizer.eval.data import Required, validate_required
from teralizer.report_basis import require_complete_corpus

_REPO_ROOT = Path(__file__).resolve().parents[4]


@dataclass(frozen=True)
class CorpusInputSpec:
    """One semantic corpus role and the schema objects read through it."""

    role: str
    corpus_id: str
    requires: tuple[Required, ...] = ()

    def __post_init__(self) -> None:
        if not self.role:
            raise ValueError("report input role must not be empty")
        if not self.corpus_id:
            raise ValueError(f"corpus input {self.role!r} must name a corpus id")
        if not isinstance(self.requires, tuple):
            raise TypeError(f"corpus input {self.role!r} requires must be a tuple")


@dataclass(frozen=True)
class FileInputSpec:
    """One repository-relative file role."""

    role: str
    path: str
    required: bool = True

    def __post_init__(self) -> None:
        if not self.role:
            raise ValueError("report input role must not be empty")
        relative = PurePosixPath(self.path)
        if not self.path or relative.is_absolute() or ".." in relative.parts:
            raise ValueError(
                f"file input {self.role!r} path must be repository-relative: "
                f"{self.path!r}"
            )


ReportInputSpec = CorpusInputSpec | FileInputSpec


@dataclass(frozen=True)
class CorpusInputSnapshot:
    role: str
    corpus_id: str
    database: str
    expected_projects: int
    observed_projects: int
    data_dir: str | None
    config_dir: str | None


@dataclass(frozen=True)
class FileInputSnapshot:
    role: str
    path: str
    present: bool
    sha256: str | None
    commit: str | None
    dirty: bool


InputSnapshot = CorpusInputSnapshot | FileInputSnapshot


@dataclass(frozen=True)
class ResolvedCorpusInput:
    spec: CorpusInputSpec
    entry: corpora.CorpusEntry
    connection: Connection


@dataclass(frozen=True)
class ResolvedFileInput:
    spec: FileInputSpec
    path: Path | None


ResolvedInput = ResolvedCorpusInput | ResolvedFileInput


@dataclass(frozen=True)
class ReportContext:
    """Resolved handles and frozen identities for one report construction."""

    report: str
    inputs: tuple[ResolvedInput, ...]
    snapshots: tuple[InputSnapshot, ...]

    def _input(self, role: str) -> ResolvedInput:
        for resolved in self.inputs:
            if resolved.spec.role == role:
                return resolved
        raise KeyError(f"report {self.report!r} has no input role {role!r}")

    def corpus(self, role: str) -> Connection:
        resolved = self._input(role)
        if not isinstance(resolved, ResolvedCorpusInput):
            raise TypeError(
                f"report {self.report!r} input role {role!r} is not a corpus"
            )
        return resolved.connection

    def file(self, role: str) -> Path | None:
        resolved = self._input(role)
        if not isinstance(resolved, ResolvedFileInput):
            raise TypeError(f"report {self.report!r} input role {role!r} is not a file")
        return resolved.path


def validate_declarations(declarations: tuple[ReportInputSpec, ...]) -> None:
    """Reject mutable declaration containers and duplicate semantic roles."""
    if not isinstance(declarations, tuple):
        raise TypeError("report input declarations must be a tuple")
    roles: set[str] = set()
    for declaration in declarations:
        if declaration.role in roles:
            raise ValueError(f"duplicate report input role {declaration.role!r}")
        roles.add(declaration.role)


def _git(repo_root: Path, args: list[str]) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()


def _snapshot_file(role: str, relative: str, repo_root: Path) -> FileInputSnapshot:
    path = repo_root / relative
    if not path.is_file():
        return FileInputSnapshot(role, relative, False, None, None, False)
    if (repo_root / ".git").exists():
        commit: str | None = _git(
            repo_root, ["log", "-1", "--format=%H", "--", relative]
        )
        dirty = bool(_git(repo_root, ["status", "--porcelain", "--", relative]))
    else:
        from teralizer.eval.provenance import checkout_snapshot

        commit, dirty = checkout_snapshot()
    if not commit:
        commit = None
        dirty = True
    return FileInputSnapshot(
        role=role,
        path=relative,
        present=True,
        sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        commit=commit,
        dirty=dirty,
    )


def _resolve_file(
    report: str, declaration: FileInputSpec, repo_root: Path
) -> ResolvedFileInput:
    path = repo_root / declaration.path
    if not path.is_file():
        if declaration.required:
            raise FileNotFoundError(
                f"report {report!r} required input {declaration.role!r} is missing: "
                f"{path}"
            )
        return ResolvedFileInput(declaration, None)
    return ResolvedFileInput(declaration, path)


def _file_snapshots(
    declarations: tuple[ReportInputSpec, ...], repo_root: Path
) -> tuple[FileInputSnapshot, ...]:
    return tuple(
        _snapshot_file(declaration.role, declaration.path, repo_root)
        for declaration in declarations
        if isinstance(declaration, FileInputSpec)
    )


@contextmanager
def resolve_inputs(
    report: str,
    declarations: tuple[ReportInputSpec, ...],
    *,
    corpus_registry: corpora.CorpusRegistry | None = None,
    repo_root: Path = _REPO_ROOT,
) -> Iterator[ReportContext]:
    """Resolve all declarations and reject changed files before rendering."""
    validate_declarations(declarations)
    resolved: list[ResolvedInput] = []
    snapshots: list[InputSnapshot] = []
    with ExitStack() as stack:
        for declaration in declarations:
            if isinstance(declaration, CorpusInputSpec):
                entry, conn = stack.enter_context(
                    corpora.open_corpus(declaration.corpus_id, corpus_registry)
                )
                if entry.data_dir is not None and entry.config_dir is not None:
                    require_complete_corpus(
                        conn,
                        data_dir=repo_root / entry.data_dir,
                        config_dir=repo_root / entry.config_dir,
                    )
                validate_required(conn, declaration.requires)
                observed_projects = entry.expected_projects
                resolved.append(ResolvedCorpusInput(declaration, entry, conn))
                snapshots.append(
                    CorpusInputSnapshot(
                        role=declaration.role,
                        corpus_id=entry.id,
                        database=entry.database,
                        expected_projects=entry.expected_projects,
                        observed_projects=observed_projects,
                        data_dir=entry.data_dir,
                        config_dir=entry.config_dir,
                    )
                )
            else:
                file_input = _resolve_file(report, declaration, repo_root)
                resolved.append(file_input)
                snapshots.append(
                    _snapshot_file(declaration.role, declaration.path, repo_root)
                )

        context = ReportContext(report, tuple(resolved), tuple(snapshots))
        yield context

        before = tuple(
            snapshot
            for snapshot in context.snapshots
            if isinstance(snapshot, FileInputSnapshot)
        )
        after = _file_snapshots(declarations, repo_root)
        if after != before:
            changed_roles = sorted(
                first.role
                for first, second in zip(before, after, strict=True)
                if first != second
            )
            raise RuntimeError(
                f"report {report!r} input changed during construction: "
                + ", ".join(changed_roles)
            )
