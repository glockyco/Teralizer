---
title: JARVIS Scoreboard Evaluation Lane
type: spec
status: active
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
---

A clean evaluation lane for turning Phase-1 JARVIS capability work into reproducible Table-2 evidence.

## Goal

Run Teralizer on a pinned JARVIS-era fixture and compare generated jqwik tests with JARVIS Table-2 PVC/IC numbers without mutating the paper's collected evaluation databases or the repo's read-only `projects/` submodules.

## Definitions

- **Table row:** one row in JARVIS paper Table 2. The scoreboard has **10** rows.
- **Probe:** one Teralizer/SPF target method. The scoreboard has **11** probes because `FastMathTest::testMinMaxDouble` is one JARVIS row but covers both `FastMath.min(double,double)` and `FastMath.max(double,double)`.
- **Capability:** a Table row enters the Teralizer pipeline when its fixture test reaches specification collection for every probe needed by that row.
- **PVC:** sum of distinct generated jqwik values per tested method parameter, using the same tries budget as JARVIS's ScalaCheck default unless a row-specific concession is recorded.
- **IC:** generated-test instruction coverage from the fresh JARVIS fixture run. Per-probe IC is valid only when the fixture isolates that probe as its own scratch project; otherwise report the available class/project-level JaCoCo unit with an explicit conflation note.

## Data boundaries

- `postgres_dev`, `postgres_test`, and their `_replication` copies are read-only inputs for other evaluations.
- JARVIS runs use a dedicated scratch DB, `postgres_jarvis_scoreboard`.
- JARVIS runs use a dedicated scratch data root, `data/jarvis-scoreboard/`, or an equivalent ignored local path set via `DATA_DIR`.
- Existing `projects/` submodules are read-only. The JARVIS fixture is materialized into an ignored scratch path, not committed as source and not registered as a submodule.
- External evidence paths under `~/Projects/phd-thesis/`, `~/Projects/test-generalization/`, and `~/Downloads/` are reference inputs only until their exact provenance is recorded in this repo's audit. The run must not depend on mutable sibling worktrees at execution time.

## Fixture corpus

Primary comparison fixture:

| component | source | pin requirement | purpose |
|---|---|---|---|
| Commons Math | `apache/commons-math` | tag `MATH_3_5`, commit `b3c5dae8f253fcb4484e5cd3cc5662587803efc2` | Math rows in JARVIS Table 2 |
| Commons Lang | `apache/commons-lang` | tag `LANG_3_5`, commit `36f98d87b24c2f542b02abbf6ec1ee742f1b158b` | CharUtils rows |

The plan may use Maven artifacts for execution when that is the least invasive path, but the audit must record enough source provenance to connect every executed bytecode artifact to a tag, checksum, and license.

## Evaluation namespace

Create a dedicated namespace instead of extending primary replication commands:

- Configs: `project-configs/jarvis-scoreboard/*.conf`.
- Optional fixture-prep script: `scripts/prepare-jarvis-scoreboard-fixtures.sh`.
- Optional runner script: `scripts/run-jarvis-scoreboard.sh`.
- Analysis module: `analysis/src/teralizer/jarvis_scoreboard.py`.
- Analysis tests: `analysis/tests/test_jarvis_scoreboard.py`.
- Evidence output: generated CSV/table files under `analysis/output/jarvis/...`; `jarvis` is a dedicated analysis output variant, not a primary replication dataset.

Do not add JARVIS configs under `project-configs/primary/`; the primary replication runner must not accidentally include this scratch scorecard.

## Case map

| Table row | Probe(s) | Expected status source |
|---|---|---|
| `CharUtilsTest::isAscii` | `CharUtils.isAscii(char)` | SPF spike FULL; Teralizer requires `char` path support |
| `CharUtilsTest::isPrintable` | `CharUtils.isAsciiPrintable(char)` | SPF spike FULL; Teralizer requires `char` path support |
| `FastMathTest::testMinMaxDouble` | `FastMath.min(double,double)`, `FastMath.max(double,double)` | SPF spike FULL except NaN/signed-zero concession shared with JARVIS |
| `FastMathTest::toIntExact` | `FastMath.toIntExact(long)` | SPF spike PARTIAL; overflow exception path is a concession unless fixed |
| `IntervalTest` | `new Interval(double,double).getSize()` | SPF spike FULL; Teralizer needs object-construction inputs before this row can enter |
| `PolynomialFunctionTest::testConstants` | `new PolynomialFunction(double[]{c0}).value(x)` | SPF spike FULL; Teralizer needs object/array construction inputs |
| `PolynomialFunctionTest::testfirstDerivativeComparison` | `new PolynomialFunction(double[]{c0,c1,c2}).polynomialDerivative().value(x)` | SPF spike FULL; Teralizer needs object/array construction inputs |
| `PolynomialFunctionTest::testLinear` | `new PolynomialFunction(double[]{c0,c1}).value(x)` | SPF spike FULL; Teralizer needs object/array construction inputs |
| `PrecisionTest` | `Precision.equals(double,double,double)` | SPF spike BLOCKED on ulps/raw-bits path; core eps behavior remains supporting evidence only |
| `UnivariateFunctionTest::testAbs` | `new Abs().value(double)` | SPF spike FULL only after the `FastMath.abs(double)` SPF model lands |

## Execution contract

1. Reset or create `postgres_jarvis_scoreboard` before a scorecard run.
2. Materialize fixtures from pinned sources or checksummed artifacts into an ignored scratch path.
3. Run Teralizer only through a preflighted command that fails closed unless the resolved JVM environment has `DB_NAME=postgres_jarvis_scoreboard` and `DATA_DIR=data/jarvis-scoreboard`.
4. Run `BASELINE`, `NAIVE`, and `IMPROVED` variants. Match the JARVIS tries budget for `NAIVE` and `IMPROVED`; record jqwik's fixed `seed=0` as the deterministic run seed, not as cross-framework seed parity.
5. Aggregate PVC from jqwik value logs and IC from the same scratch DB's `jacoco_coverage_report` rows, using one-project-per-probe fixtures where per-probe IC is required.
6. Update `docs/plans/2026-06-26-jarvis-case-coverage.md` with source pins, row/probe results, concessions, and whether each Table row is a win, loss, blocked case, or explicit concession.

## Acceptance criteria

- A fresh checkout can reproduce the scorecard setup without reading mutable sibling project worktrees except as optional source mirrors.
- Documented commands include a preflight that verifies the resolved `DB_NAME` and refuses `postgres_dev`, `postgres_test`, and `_replication` targets before the Java process starts.
- The audit distinguishes 10 JARVIS Table rows from 11 Teralizer probes.
- Every numeric PVC/IC claim in the audit is backed by a generated CSV/table path or a logged SQL/query command.
- Every concession is attached to a concrete cause: Teralizer front-end gap, SPF model gap, JARVIS/shared sampling gap, or intentional paradigm mismatch.
- `omp-plans check`, the focused Java tests touched by implementation, and `uv run --directory analysis pytest tests/test_jarvis_scoreboard.py -q` pass before any claim is updated.
