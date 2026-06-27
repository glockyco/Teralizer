---
title: JARVIS Scoreboard Evidence Run
type: plan
status: active
created: 2026-06-27
parent: 2026-06-27-jarvis-scoreboard-evaluation-lane
---

The ordered work to produce a cited JARVIS Table-2 head-to-head result from the clean evaluation lane.

Design spec: `2026-06-27-jarvis-scoreboard-evaluation-lane`.

## Goal

Produce evidence for the "beat JARVIS" claim by running Teralizer on pinned JARVIS-era fixtures, collecting PVC/IC from the scratch run, and updating the case-coverage audit with row-level wins, losses, blockers, and concessions.

Acceptance criteria:

- The run distinguishes **10 JARVIS Table-2 rows** from **11 Teralizer probes** (`FastMathTest::testMinMaxDouble` splits into `min` and `max` probes).
- The primary comparison fixture is pinned to JARVIS-era or checksummed source artifacts, not modern HEAD snapshots or mutable sibling worktrees.
- Teralizer runs use scratch database `postgres_jarvis_scoreboard`, `DATASET_VARIANT=jarvis`, and scratch data path `data/jarvis-scoreboard`; no command writes to `postgres_dev`, `postgres_test`, or their replication copies.
- `docs/plans/2026-06-26-jarvis-case-coverage.md` contains per-row PVC numbers, IC numbers at the available project/probe isolation unit, provenance, and an explicit win/loss/blocker/concession note for each Table-2 row.
- `docs/plans/2026-06-26-teralizer-overview.md` points at this spec/plan pair and states the same 10-row / 11-probe denominator.

## Decisions

- Keep the JARVIS scorecard outside the primary replication namespace: configs live under `project-configs/jarvis-scoreboard/`, not `project-configs/primary/`; analysis output uses `DATASET_VARIANT=jarvis`, not `original` or `replicate`.
- Use Commons Math tag `MATH_3_5` (`b3c5dae8f253fcb4484e5cd3cc5662587803efc2`) and Commons Lang tag `LANG_3_5` (`36f98d87b24c2f542b02abbf6ec1ee742f1b158b`) as the primary source pins. `commons-lang3-3.17.0` remains a spike harness dependency, not the JARVIS-era scorecard source.
- Prefer a small fixture-prep script over committed fixture source. The script materializes ignored scratch copies from pinned tags or checksummed Maven artifacts and records provenance in the audit.
- Run Teralizer directly with `./gradlew run -Dteralizer.config=...`; do not add a `--dataset jarvis` mode to `replication/scripts/run.sh` unless the implementation proves a wrapper is necessary.
- IC data must come from the fresh JARVIS scratch run. Existing `postgres_dev` JaCoCo rows describe the modern `commons-utils` corpus and are not valid scorecard evidence.
- jqwik uses a deterministic fixed seed (`0`) in generated tests; match the JARVIS tries budget for `NAIVE` and `IMPROVED`, and do not claim cross-framework seed parity.

## Files in scope

- `docs/plans/2026-06-27-jarvis-scoreboard-evaluation-lane.md` — design contract for the clean lane.
- `docs/plans/2026-06-26-jarvis-case-coverage.md` — audit updated with final per-case evidence and provenance.
- `docs/plans/2026-06-26-teralizer-overview.md` — current-focus pointers and denominator summary.
- `project-configs/jarvis-scoreboard/*.conf` — scratch scorecard configs using `DATASET_VARIANT=jarvis`.
- `scripts/prepare-jarvis-scoreboard-fixtures.sh` — optional fixture materializer if direct config-only setup is not reproducible enough.
- `scripts/run-jarvis-scoreboard.sh` — optional thin runner if repeated direct Gradle commands become error-prone.
- `analysis/src/teralizer/jarvis_scoreboard.py` — PVC/IC aggregation for the pinned scratch runs.
- `analysis/tests/test_jarvis_scoreboard.py` — offline regression tests for scoreboard-analysis logic.
- `.gitignore` — ignores generated fixture/data byproducts if new local paths are introduced.

## Tasks

- [ ] Pin the primary comparison fixture: record Commons Math `MATH_3_5` / `b3c5dae8f253fcb4484e5cd3cc5662587803efc2`, Commons Lang `LANG_3_5` / `36f98d87b24c2f542b02abbf6ec1ee742f1b158b`, classify every external path as reference-only or execution input, and update the audit before running anything.
- [ ] Add the scratch evaluation namespace: create `project-configs/jarvis-scoreboard/` configs and any fixture-prep/runner scripts needed to materialize ignored scratch projects from the recorded pins without touching committed `projects/` submodules.
- [ ] Prove DB/data isolation: create/reset `postgres_jarvis_scoreboard`, run a preflighted dry or minimal command with `DB_NAME=postgres_jarvis_scoreboard DATA_DIR=data/jarvis-scoreboard`, and verify no command path targets `postgres_dev`, `postgres_test`, or `_replication` databases.
- [ ] Execute the JARVIS scorecard variants for `BASELINE`, `NAIVE`, and `IMPROVED` on the pinned fixture; use the JARVIS tries budget for `NAIVE` and `IMPROVED`, and record jqwik's fixed `seed=0`.
- [ ] Aggregate PVC and IC results from the scratch jqwik value logs and scratch JaCoCo rows; preserve per-probe IC by fixture isolation where needed, or record class-level conflation explicitly.
- [ ] Update `docs/plans/2026-06-26-jarvis-case-coverage.md` with the chosen corpus, numeric PVC/IC results or blockers, exact provenance, and a win/loss/blocker/concession summary for each of the 10 Table-2 rows.
- [ ] Decide whether the evidence supports the overview's "beat JARVIS" claim; if yes, archive this plan and refresh the overview focus, otherwise record the precise blocking gap and spin the next plan from that gap.

## Validation strategy

- Re-run `omp-plans index && omp-plans check` after editing plan/audit/overview docs.
- Re-run `uv run --directory analysis pytest tests/test_config.py tests/test_jarvis_scoreboard.py -q` before and after scoreboard-analysis or analysis-config changes.
- Re-run `./gradlew run -Dteralizer.config=project-configs/jarvis-scoreboard/<case>.conf --no-daemon` after any pipeline-side or fixture-config change.
- Verify scratch DB contents with a read-only `psql` query against `postgres_jarvis_scoreboard` before using any number in the audit.
