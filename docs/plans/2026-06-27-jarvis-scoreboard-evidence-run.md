---
title: JARVIS Scoreboard Evidence Run
type: plan
status: active
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
---

The ordered work to turn Phase 1 capability into a cited JARVIS Table-2 head-to-head result.

## Goal

Produce the evidence needed to claim "beat JARVIS" on Table 2 using the new PVC/IC harness.

Acceptance criteria:

- The primary comparison corpus is pinned to JARVIS-era sources rather than modern HEAD snapshots.
- Teralizer runs use the matched tries budget and seed, and write only to scratch databases / scratch data paths.
- `docs/plans/2026-06-26-jarvis-case-coverage.md` contains per-case PVC/IC numbers, provenance, and an explicit win/loss/concession note for each Table-2 row.
- `docs/plans/2026-06-26-teralizer-overview.md` points at the live evidence run, not the archived Phase-1 implementation plan.

## Decision

Use a pinned JARVIS-era monolithic comparison fixture as the **primary** scorecard corpus:

- Commons Math: tag `MATH_3_5` with resolved commit SHA.
- Commons Lang: pinned `commons-lang3` release tag + resolved commit SHA.
- Modern modular Apache Commons remains **supporting applicability evidence**, not the primary beat-JARVIS claim.

This keeps the scoreboard apples-to-apples with the VMCAI 2018 paper and avoids treating modern helper/lambda refactors as if they were inherent Teralizer failures.

## Files in scope

- `docs/plans/2026-06-26-jarvis-case-coverage.md` — audit updated with the final per-case evidence.
- `docs/plans/2026-06-26-teralizer-overview.md` — current-focus pointers after the evidence run lands.
- `analysis/src/teralizer/jarvis_scoreboard.py` — PVC/IC aggregation for the pinned comparison runs.
- `analysis/tests/test_jarvis_scoreboard.py` — regression tests for any scoreboard-analysis changes.
- `project-configs/jarvis-scoreboard.conf` — dedicated scratch scoreboard config for the pinned JARVIS cases.

## Tasks

- [ ] Pin the primary comparison fixture for the JARVIS Table-2 cases, recording exact release tags, commit SHAs, upstream paths, and license/provenance notes for every vendored source used by the run.
- [ ] Create `project-configs/jarvis-scoreboard.conf`, targeting only the pinned JARVIS cases and writing to scratch databases / scratch data directories.
- [ ] Execute the Teralizer pipeline for `BASELINE`, `NAIVE`, and `IMPROVED` on the pinned cases with the matched jqwik tries budget and seed.
- [ ] Run the PVC/IC analysis helpers on the resulting jqwik value logs and JaCoCo rows; extend `analysis/src/teralizer/jarvis_scoreboard.py` only if the pinned runs expose a missing aggregation step.
- [ ] Update `docs/plans/2026-06-26-jarvis-case-coverage.md` with the chosen comparison corpus, numeric PVC/IC results, exact provenance, and a win/loss/concession summary for each Table-2 case.
- [ ] Decide whether the resulting evidence supports the overview's "beat JARVIS" claim; if yes, archive this plan and refresh the overview focus, otherwise record the blocking gap precisely and spin the next plan from that gap.

## Validation strategy

- Use scratch databases only; never mutate `postgres_dev` or `postgres_test`.
- Re-run `uv run --directory analysis pytest tests/test_jarvis_scoreboard.py -q` before and after scoreboard-analysis changes.
- Re-run `./gradlew run -Dteralizer.config=project-configs/jarvis-scoreboard.conf` after any pipeline-side change.
- Re-run `omp-plans index && omp-plans check` after editing plan/audit/overview docs.
