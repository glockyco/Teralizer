import json
from pathlib import Path

import pytest
from teralizer.release import claims

_TABLE = """row_key,strategy,included,total,share,misses
NAIVE_10_TRIES,NAIVE_10_TRIES,10743,13836,0.7765,2233
IMPROVED_10_TRIES,IMPROVED_10_TRIES,11788,13836,0.8520,1189
NAIVE_50_TRIES,NAIVE_50_TRIES,9964,13836,0.7202,2938
IMPROVED_50_TRIES,IMPROVED_50_TRIES,11660,13836,0.8427,1223
"""
_PAIRS = [
    ["NAIVE_10_TRIES", "IMPROVED_10_TRIES"],
    ["NAIVE_50_TRIES", "IMPROVED_50_TRIES"],
]


def _run(tmp_path: Path) -> claims.Run:
    (tmp_path / "build/rq5").mkdir(parents=True)
    (tmp_path / "build/macros").mkdir(parents=True)
    (tmp_path / "reports").mkdir()
    (tmp_path / "build/rq5/outcomes.csv").write_text(_TABLE)
    (tmp_path / "build/macros/rq5.tex").write_text(
        "% rq5\n\\newcommand{\\TzShare}{14.6\\%}\n"
    )
    manifest = {
        "rq5": {
            "artifacts": [
                {"target": "csv", "key": "outcomes", "path": "build/rq5/outcomes.csv"},
                {
                    "target": "latex",
                    "key": "macros/rq5",
                    "path": "build/macros/rq5.tex",
                },
            ]
        }
    }
    (tmp_path / "reports/provenance.json").write_text(json.dumps(manifest))
    return claims.Run(tmp_path)


def _claim(*checks: dict) -> claims.Claim:
    table = [{"report": "rq5", "table": "outcomes", **check} for check in checks]
    return claims.Claim(
        "c", "RQ5", "statement", "deterministic transformation", ("rq5",), tuple(table)
    )


def test_every_check_kind_passes_on_matching_outputs(tmp_path: Path):
    claim = _claim(
        {
            "kind": "cell",
            "where": {"row_key": "NAIVE_10_TRIES"},
            "column": "share",
            "scale": 100,
            "value": "77.7",
        },
        {
            "kind": "extent",
            "where": {"row_key": "NAIVE_*"},
            "column": "share",
            "scale": 100,
            "min": "72.0",
            "max": "77.7",
        },
        {"kind": "sum", "column": "included", "value": "44155"},
        {
            "kind": "count",
            "condition": {"column": "misses", "op": ">", "value": "2000"},
            "value": 2,
        },
        {
            "kind": "wins",
            "column": "included",
            "pairs": [[after, before] for before, after in _PAIRS],
            "value": 2,
        },
        {
            "kind": "reduction",
            "column": "misses",
            "pairs": _PAIRS,
            "min": "46.8",
            "max": "58.4",
        },
        {"kind": "top", "by": "share", "present": "strategy"},
        {
            "kind": "extent",
            "column": "included",
            "scale": 100,
            "min": "72.0",
            "max": "85.2",
            "divide_by": {
                "report": "rq5",
                "table": "outcomes",
                "where": {"row_key": "NAIVE_10_TRIES"},
                "column": "total",
            },
        },
    )
    macro = claims.Claim(
        "m",
        "RQ6",
        "statement",
        "deterministic transformation",
        ("rq5",),
        ({"kind": "macro", "report": "rq5", "macro": "TzShare", "value": "14.6%"},),
    )

    outcomes = claims.verify([claim, macro], _run(tmp_path))

    assert [outcome.failures for outcome in outcomes] == [[], []]


def test_changed_outputs_fail_the_claim_with_the_found_value(tmp_path: Path):
    claim = _claim(
        # 46.75 rounds half up to 46.8, never down to 46.7.
        {
            "kind": "reduction",
            "column": "misses",
            "pairs": _PAIRS,
            "min": "46.7",
            "max": "58.4",
        },
        {
            "kind": "cell",
            "where": {"row_key": "*_TRIES"},
            "column": "share",
            "value": "0.7765",
        },
        {"kind": "wins", "column": "included", "pairs": _PAIRS, "value": 1},
    )

    (outcome,) = claims.verify([claim], _run(tmp_path))

    assert outcome.failures == [
        "rq5/outcomes smallest reduction is 46.8, the claim states 46.7",
        "outcomes: {'row_key': '*_TRIES'} selects 4 rows, not one",
        "rq5/outcomes: the first row wins 0 of 2 pairs, the claim states 1",
    ]


def test_the_repository_claims_declaration_loads():
    root = Path(__file__).resolve().parents[3]

    declared = claims.load(root / claims.DEFAULT_CLAIMS)

    assert {claim.rq for claim in declared} == {f"RQ{n}" for n in range(7)}


def test_unknown_evidence_classes_are_rejected(tmp_path: Path):
    path = tmp_path / "claims.toml"
    path.write_text(
        'format = "teralizer-claims"\nformat_version = 1\n'
        '[[claim]]\nid = "x"\nrq = "RQ1"\nstatement = "s"\n'
        'evidence = "belief"\nreports = ["rq1"]\n'
        '[[claim.check]]\nkind = "macro"\nreport = "rq1"\nmacro = "M"\nvalue = "1"\n'
    )

    with pytest.raises(claims.ClaimsError, match="unknown evidence class"):
        claims.load(path)
