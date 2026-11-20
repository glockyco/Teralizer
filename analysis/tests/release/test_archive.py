import os
import subprocess
import zipfile
from pathlib import Path

import pytest
from teralizer.release import archive

_ROOT = "teralizer/components/demo"


def _entries(tmp_path: Path) -> list[archive.Entry]:
    script = tmp_path / "run.sh"
    script.write_text("#!/bin/sh\necho demo\n")
    data = tmp_path / "data.bin"
    data.write_bytes(os.urandom(4096))
    return [
        archive.Entry(f"{_ROOT}/run.sh", source=script, executable=True),
        archive.Entry(f"{_ROOT}/data.bin", source=data),
        archive.Entry(f"{_ROOT}/component-manifest.json", content=b"{}\n"),
    ]


def test_same_plan_yields_identical_bytes_and_verifies(tmp_path: Path):
    entries = _entries(tmp_path)
    first = archive.write(tmp_path / "a.zip", entries)
    # A later rebuild must not depend on file timestamps or plan order.
    os.utime(tmp_path / "data.bin", (0, 0))
    archive.write(tmp_path / "b.zip", list(reversed(entries)))

    assert (tmp_path / "a.zip").read_bytes() == (tmp_path / "b.zip").read_bytes()
    assert archive.verify(tmp_path / "a.zip", _ROOT, first) == []
    with zipfile.ZipFile(tmp_path / "a.zip") as written:
        modes = {
            info.filename: (info.external_attr >> 16) & 0o777
            for info in written.infolist()
        }
    assert modes[f"{_ROOT}/run.sh"] == 0o755
    assert modes[f"{_ROOT}/data.bin"] == 0o644


def test_verification_reports_missing_extra_and_changed_members(tmp_path: Path):
    members = archive.write(tmp_path / "a.zip", _entries(tmp_path))
    planned = [
        archive.Member(
            members[0].name, "0" * 64, members[0].bytes, members[0].executable
        ),
        *members[1:2],
        archive.Member(f"{_ROOT}/absent.txt", "0" * 64, 1, False),
    ]

    problems = archive.verify(tmp_path / "a.zip", _ROOT, planned)

    assert f"a.zip: member {members[0].name} differs from its plan" in problems
    assert f"a.zip: unexpected member {members[2].name}" in problems
    assert f"a.zip: missing member {_ROOT}/absent.txt" in problems


def test_members_outside_the_root_are_reported(tmp_path: Path):
    escaping = [archive.Entry("teralizer/other/file.txt", content=b"x")]
    members = archive.write(tmp_path / "a.zip", escaping)

    assert archive.verify(tmp_path / "a.zip", _ROOT, members) == [
        f"a.zip: member teralizer/other/file.txt is outside {_ROOT}/"
    ]


@pytest.mark.parametrize("name", ["/abs/file", "a/../b", "a\\b", "a//b", ""])
def test_unsafe_names_are_refused(tmp_path: Path, name: str):
    with pytest.raises(archive.ArchiveError, match="unsafe"):
        archive.write(tmp_path / "a.zip", [archive.Entry(name, content=b"x")])


def test_duplicate_names_are_refused(tmp_path: Path):
    entries = [archive.Entry("a/x", content=b"1"), archive.Entry("a/x", content=b"2")]

    with pytest.raises(archive.ArchiveError, match="duplicate"):
        archive.write(tmp_path / "a.zip", entries)


def test_link_members_round_trip_through_unzip(tmp_path: Path):
    entries = [
        archive.Entry(f"{_ROOT}/p/target.txt", content=b"target\n"),
        archive.Entry(f"{_ROOT}/p/alias.txt", link="target.txt"),
        archive.Entry(f"{_ROOT}/p/docs/api", link="../build/api/"),
    ]
    members = archive.write(tmp_path / "a.zip", entries)

    assert archive.verify(tmp_path / "a.zip", _ROOT, members) == []
    out = tmp_path / "out"
    subprocess.run(["unzip", "-q", str(tmp_path / "a.zip"), "-d", str(out)], check=True)
    alias = out / _ROOT / "p/alias.txt"
    assert alias.is_symlink() and alias.read_text() == "target\n"
    assert os.readlink(out / _ROOT / "p/docs/api") == "../build/api/"


def test_a_planned_file_stored_as_link_fails_verification(tmp_path: Path):
    members = archive.write(
        tmp_path / "a.zip", [archive.Entry(f"{_ROOT}/alias", link="target")]
    )
    as_file = [archive.Member(m.name, m.sha256, m.bytes, False) for m in members]

    assert archive.verify(tmp_path / "a.zip", _ROOT, as_file) == [
        f"a.zip: member {_ROOT}/alias is a link against its plan"
    ]


@pytest.mark.parametrize("target", ["/etc/passwd", "../../../../outside", ""])
def test_links_that_leave_the_archive_root_are_refused(tmp_path: Path, target: str):
    with pytest.raises(archive.ArchiveError, match="leave the archive root"):
        archive.write(
            tmp_path / "a.zip", [archive.Entry(f"{_ROOT}/alias", link=target)]
        )
