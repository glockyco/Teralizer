"""Deterministic ZIP archives over Python's maintained Zip64 implementation.

Writing streams each declared file into the archive in path order with fixed
timestamps and normalized permissions, so the same plan always yields the same
bytes. Verification reads the finished archive through Info-ZIP `unzip`, an
implementation independent of the writer, before trusting it.
"""

from __future__ import annotations

import hashlib
import shutil
import stat
import subprocess
import zipfile
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

# ZIP cannot represent dates before 1980.
_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
_CHUNK = 1024 * 1024


class ArchiveError(RuntimeError):
    """An archive could not be written or failed verification."""


@dataclass(frozen=True)
class Entry:
    """One archive member: where its bytes come from and where they land."""

    name: str
    source: Path | None = None
    content: bytes | None = None
    executable: bool = False


@dataclass(frozen=True)
class Member:
    name: str
    sha256: str
    bytes: int
    executable: bool


def safe_name(name: str) -> bool:
    path = PurePosixPath(name)
    return (
        bool(name)
        and not path.is_absolute()
        and "\\" not in name
        and all(part not in ("", ".", "..") for part in name.split("/"))
    )


def _info(name: str, executable: bool) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3  # Unix, so external_attr carries the mode.
    mode = 0o755 if executable else 0o644
    info.external_attr = (stat.S_IFREG | mode) << 16
    return info


def write(path: Path, entries: Iterable[Entry]) -> list[Member]:
    """Write ``entries`` sorted by name to a new archive at ``path``."""
    ordered = sorted(entries, key=lambda entry: entry.name)
    names = [entry.name for entry in ordered]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise ArchiveError(f"duplicate members: {duplicates}")
    unsafe = [name for name in names if not safe_name(name)]
    if unsafe:
        raise ArchiveError(f"unsafe member names: {unsafe}")
    if path.exists():
        raise ArchiveError(f"archive already exists: {path}")

    members: list[Member] = []
    partial = path.with_name(path.name + ".partial")
    with zipfile.ZipFile(partial, "w", compresslevel=9) as archive:
        for entry in ordered:
            if (entry.source is None) == (entry.content is None):
                raise ArchiveError(
                    f"{entry.name}: give exactly one of source or content"
                )
            digest = hashlib.sha256()
            size = 0
            with archive.open(
                _info(entry.name, entry.executable), "w", force_zip64=True
            ) as out:
                if entry.content is not None:
                    chunks: Iterable[bytes] = [entry.content]
                else:
                    assert entry.source is not None
                    chunks = _read_chunks(entry.source)
                for chunk in chunks:
                    digest.update(chunk)
                    size += len(chunk)
                    out.write(chunk)
            members.append(
                Member(entry.name, digest.hexdigest(), size, entry.executable)
            )
    partial.rename(path)
    return members


def _read_chunks(source: Path) -> Iterable[bytes]:
    with source.open("rb") as stream:
        while chunk := stream.read(_CHUNK):
            yield chunk


def verify(path: Path, root: str, expected: Iterable[Member]) -> list[str]:
    """Check an archive against its planned members through an independent reader."""
    unzip = shutil.which("unzip")
    if unzip is None:
        raise ArchiveError("Info-ZIP unzip is required to verify archives")
    problems: list[str] = []
    tested = subprocess.run([unzip, "-tqq", str(path)], capture_output=True, text=True)
    if tested.returncode != 0:
        problems.append(f"{path.name}: integrity test failed: {tested.stdout.strip()}")
    listing = subprocess.run(
        [unzip, "-Z1", str(path)], capture_output=True, text=True, check=True
    ).stdout.splitlines()

    planned = {member.name: member for member in expected}
    seen: set[str] = set()
    for name in listing:
        if name in seen:
            problems.append(f"{path.name}: duplicate member {name}")
        seen.add(name)
        if not safe_name(name):
            problems.append(f"{path.name}: unsafe member {name}")
        if not (name == root or name.startswith(root + "/")):
            problems.append(f"{path.name}: member {name} is outside {root}/")
        if name not in planned:
            problems.append(f"{path.name}: unexpected member {name}")
    for name in sorted(set(planned) - seen):
        problems.append(f"{path.name}: missing member {name}")

    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            member = planned.get(info.filename)
            if member is None:
                continue
            digest = hashlib.sha256()
            with archive.open(info) as stream:
                while chunk := stream.read(_CHUNK):
                    digest.update(chunk)
            if digest.hexdigest() != member.sha256 or info.file_size != member.bytes:
                problems.append(
                    f"{path.name}: member {info.filename} differs from its plan"
                )
            mode = (info.external_attr >> 16) & 0o777
            if bool(mode & 0o111) != member.executable:
                problems.append(
                    f"{path.name}: member {info.filename} has mode {oct(mode)}"
                )
    return problems
