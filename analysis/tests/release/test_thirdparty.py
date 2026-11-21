import json
import shutil
import subprocess
from pathlib import Path

import pytest
from teralizer.release import thirdparty

_MIT = """MIT License

Copyright (c) 2014 Example

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""


def _licensee() -> bool:
    found = shutil.which("licensee")
    return found is not None and subprocess.run([found, "version"]).returncode == 0


def _declared() -> thirdparty.Declared:
    return thirdparty.Declared(
        component="projects-jarvis-census",
        paths=("data/jarvis-census/fixtures/demo",),
        origin="https://github.com/apache/commons-demo.git",
        revision="0" * 40,
        license="Apache-2.0",
        license_evidence="LICENSE.txt of the upstream revision",
        upstream=(),
        note=None,
    )


def test_declared_projects_need_a_payload(tmp_path: Path):
    with pytest.raises(thirdparty.ThirdPartyError, match="without payload"):
        thirdparty.component_records(
            "projects-jarvis-census", {}, {}, {}, [_declared()], {}, tmp_path
        )


def test_every_project_root_needs_an_origin(tmp_path: Path):
    members = {"projects/github_com_a_b/pom.xml": tmp_path / "pom.xml"}

    with pytest.raises(thirdparty.ThirdPartyError, match="no declared origin"):
        thirdparty.component_records(
            "projects-real-world-full", members, {}, {}, [], {}, tmp_path
        )


def test_a_declared_license_needs_its_evidence(tmp_path: Path):
    path = tmp_path / "third-party.toml"
    path.write_text(
        'format = "teralizer-third-party-sources"\nformat_version = 1\n'
        '[[project]]\ncomponent = "c"\npaths = ["projects/x"]\norigin = "o"\n'
        'license = "MIT"\n'
    )

    with pytest.raises(thirdparty.ThirdPartyError, match="needs its evidence"):
        thirdparty.declared(path)


@pytest.mark.skipif(not _licensee(), reason="needs licensee from nix develop")
def test_records_name_shipped_license_files_or_state_none(tmp_path: Path):
    licensed = tmp_path / "projects/github_com_a_licensed"
    licensed.mkdir(parents=True)
    (licensed / "LICENSE").write_text(_MIT)
    (licensed / "pom.xml").write_text("<project/>")
    unlicensed = tmp_path / "projects/github_com_a_unlicensed"
    unlicensed.mkdir(parents=True)
    (unlicensed / "pom.xml").write_text("<project/>")
    members = {
        str(path.relative_to(tmp_path)): path
        for path in tmp_path.rglob("*")
        if path.is_file()
    }
    roots = ["projects/github_com_a_licensed", "projects/github_com_a_unlicensed"]
    origins = {root: (f"https://github.com/{root}.git", "1" * 40) for root in roots}

    content = thirdparty.component_records(
        "projects-real-world-full",
        members,
        {root: tmp_path / root for root in roots},
        origins,
        [],
        {},
        None,
    )

    records = {item["path"]: item for item in json.loads(content)["projects"]}
    licensed_record = records["projects/github_com_a_licensed"]
    assert licensed_record["licenses"] == ["MIT"]
    assert licensed_record["license_files"][0]["path"].endswith("/LICENSE")
    assert records["projects/github_com_a_unlicensed"]["licenses"] == []
    assert (
        records["projects/github_com_a_unlicensed"]["license_evidence"]
        == "no license file found"
    )
