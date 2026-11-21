import json
from pathlib import Path

from teralizer.eval.evidence import jarvis_values
from teralizer.release import evidence, jarvis, outputs

_CLAIMS = """
format = "teralizer-claims"
format_version = 1

[[claim]]
id = "rq0-share"
rq = "RQ0"
statement = "Half of the cases pass."
evidence = "deterministic transformation"
reports = ["rq0"]

[[claim.check]]
kind = "cell"
report = "rq0"
table = "cases"
where = {{ row_key = "all" }}
column = "share"
value = "{rq0_value}"

[[claim]]
id = "rq5-share"
rq = "RQ5"
statement = "A quarter of the assertions are excluded."
evidence = "deterministic transformation"
reports = ["rq5"]

[[claim.check]]
kind = "cell"
report = "rq5"
table = "outcomes"
where = {{ row_key = "all" }}
column = "share"
value = "0.25"
"""


def _write_report(root: Path, report: str, table: str, share: str) -> None:
    path = root / f"build/{report}/{table}.csv"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"row_key,share\nall,{share}\n")
    manifest_path = root / "reports/provenance.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(manifest_path.read_text()) if manifest_path.is_file() else {}
    manifest[report] = {
        "artifacts": [
            {"target": "csv", "key": table, "path": f"build/{report}/{table}.csv"}
        ],
        "run": {"source_commit": "a" * 40},
    }
    manifest_path.write_text(json.dumps(manifest))


def _full_run(root: Path) -> Path:
    _write_report(root, "rq0", "cases", "0.5")
    _write_report(root, "rq5", "outcomes", "0.25")
    return root


def _setup(tmp_path: Path, rq0_value: str = "0.5") -> tuple[Path, Path]:
    reference = tmp_path / "reference.json"
    reference.write_text(json.dumps(outputs.reference(_full_run(tmp_path / "ref"))))
    claims_path = tmp_path / "claims.toml"
    claims_path.write_text(_CLAIMS.format(rq0_value=rq0_value))
    return reference, claims_path


def test_run_of_rq0_checks_its_claims_and_the_jarvis_inputs_only(tmp_path: Path):
    reference, claims_path = _setup(tmp_path)
    candidate = tmp_path / "candidate"
    _write_report(candidate, "rq0", "cases", "0.5")
    calls: list[str] = []

    def jarvis_check() -> list[str]:
        calls.append("jarvis")
        return []

    verdict = evidence.verify(candidate, reference, claims_path, ["rq0"], jarvis_check)

    assert verdict.passed
    assert [outcome.claim.id for outcome in verdict.claims] == ["rq0-share"]
    assert calls == ["jarvis"]
    assert verdict.summary()["jarvis_inputs"] == {"passed": True, "problems": []}


def test_run_without_rq0_does_not_check_the_jarvis_inputs(tmp_path: Path):
    reference, claims_path = _setup(tmp_path)
    candidate = tmp_path / "candidate"
    _write_report(candidate, "rq5", "outcomes", "0.25")

    def jarvis_check() -> list[str]:
        raise AssertionError("RQ5 does not read the JARVIS inputs")

    verdict = evidence.verify(candidate, reference, claims_path, ["rq5"], jarvis_check)

    assert verdict.passed
    assert verdict.summary()["jarvis_inputs"] is None


def test_a_changed_claim_expectation_alone_fails_the_verdict(tmp_path: Path):
    reference, claims_path = _setup(tmp_path, rq0_value="0.6")
    candidate = _full_run(tmp_path / "candidate")

    verdict = evidence.verify(candidate, reference, claims_path, None, lambda: [])

    assert all(result.passed for result in verdict.outputs)
    assert not verdict.passed
    assert [o.claim.id for o in verdict.claims if not o.passed] == ["rq0-share"]


def test_a_jarvis_input_problem_alone_fails_the_verdict(tmp_path: Path):
    reference, claims_path = _setup(tmp_path)
    candidate = _full_run(tmp_path / "candidate")

    verdict = evidence.verify(
        candidate, reference, claims_path, None, lambda: ["census logs differ"]
    )

    assert all(result.passed for result in verdict.outputs)
    assert all(outcome.passed for outcome in verdict.claims)
    assert not verdict.passed
    assert verdict.summary()["jarvis_inputs"]["passed"] is False


def test_an_extra_output_alone_fails_the_verdict(tmp_path: Path):
    reference, claims_path = _setup(tmp_path)
    candidate = _full_run(tmp_path / "candidate")
    _write_report(candidate, "rq9", "extra", "1")

    verdict = evidence.verify(candidate, reference, claims_path, None, lambda: [])

    assert not verdict.passed
    statuses = {r.identity: r.status for r in verdict.outputs if not r.passed}
    assert statuses == {"rq9/csv/extra": "extra", "rq9/manifest/provenance": "extra"}


def test_value_logs_keep_their_place_below_data_jarvis():
    nested = "data/jarvis-scoreboard/fixtures/b/data/jarvis-scoreboard/b/2.values.tsv"
    paths = [
        "/home/producer/checkout/data/jarvis-census/fixtures/a/data/jarvis-census/1.tsv",
        nested,
        f"/Users/other/repo/{nested}",
        None,
        "nan",
    ]

    assert jarvis._relative(paths) == [
        "data/jarvis-census/fixtures/a/data/jarvis-census/1.tsv",
        nested,
    ]


def test_a_changed_value_log_differs_from_the_recorded_identity(tmp_path: Path):
    selection = {"census_value_logs": ["data/jarvis-census/fixtures/a/1.values.tsv"]}
    log = tmp_path / selection["census_value_logs"][0]
    log.parent.mkdir(parents=True)
    log.write_text("parameter\tvalue\nx\t1\n")
    facts = tmp_path / "facts.json"
    identity = jarvis_values.file_set_identity([log])
    facts.write_text(json.dumps({"sources": {"census_value_logs": identity}}))

    assert jarvis.verify(selection, tmp_path, facts) == []
    log.write_text("parameter\tvalue\nx\t2\n")
    assert len(jarvis.verify(selection, tmp_path, facts)) == 1
