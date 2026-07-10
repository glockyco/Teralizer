---
title: Unified Timeout Budget Policy
type: spec
status: implemented
created: 2026-07-10
parent: 2026-06-26-teralizer-overview
archived: 2026-07-10
---

# Unified Timeout Budget Policy

One timeout policy across every timed pipeline stage: a fixed budget keyed on the processing stage,
read from a uniform config schema, with data-driven defaults that cover every corpus so no profile
config needs a timeout override. Replaces three inconsistent per-stage timeout shapes with one
mechanism.

## Problem

Timed stages derived their wall budget three different ways: JPF a per-assertion `jpf.max-execution-time`
(10s), test execution a flat `junit.max-execution-time` (60s) for ORIGINAL and INITIAL but a bespoke
`60 x ceil(gens x tries / 1600)` (capped 3600s) for GENERALIZED, and PIT a flat `pitest.max-execution-time`.
On top of that, the JARVIS census and per-corpus benchmark configs carried their own overrides
(`jpf` 30, `junit` 1200, `pitest` 3600) that predated any measurement.

Two problems with the shapes. First, the PIT cap was set far too low: at 300s it excludes
large-but-healthy projects, since PIT cost scales with the mutation surface. The paper's 300s hit 40
of 114 reduction-reaching projects (~35%) at `COLLECT_PIT_DATA_INITIAL`. A flat cap is right, it just
has to be sized to the real runtime tail. Second, the scaled generalized budget scaled by `tries`,
which over-budgets and lets a higher-tries variant buy its way out of the timeout pressure the
cross-variant comparison exists to measure.

### What the runtimes actually show

RepoReapers calibration (`pit-calibrate`: PIT on, ORIGINAL off, 3600s cap, IMPROVED_200):

| Project | included gens | `EXECUTE_TESTS` INITIAL -> GENERALIZED | `COLLECT_PIT` INITIAL -> GENERALIZED |
|---|---|---|---|
| JadConfig | 79 | 21.3s -> 22.5s (+6%) | 74s -> 64s (-14%) |
| byteseek | 143 | 48.8s -> 52.7s (+8%) | 1021s -> 1044s (+2%) |

JARVIS census (`postgres_jarvis_census`, the demanding corpus), over all tasks per stage:

| Stage | succeeded max | failed (timeouts) |
|---|---|---|
| `EXECUTE_JPF` (per assertion) | 0.5s | none (non-timeout failures <= 1.3s) |
| `EXECUTE_TESTS_ORIGINAL` | 70s (commons-math) | commons-pool at the 1200s cap |
| `EXECUTE_TESTS_INITIAL` | 54.5s | none |
| `EXECUTE_TESTS_GENERALIZED` | 1531s | none |
| `COLLECT_PIT_DATA_INITIAL` | 1375s | 2, at the 3600s cap |
| `COLLECT_PIT_DATA_GENERALIZED` | 1467s | none |

Findings:

- **PIT is mutation-surface-bound.** GENERALIZED PIT approximately equals INITIAL PIT; the surface is
  shared and generalizations add only cheap covering tests. Neither scales with the generalization count.
- **Test execution barely grows on RepoReapers (+6-8%) but genuinely grows on big-commons projects**
  (census generalized execution reaches 1531s), because those projects generalize far more tests.
- **ORIGINAL and INITIAL execution are different magnitudes on real projects.** ORIGINAL runs the full
  upstream suite (census commons-math 70s, commons-pool past 1200s); INITIAL runs the filtered included
  subset (census max 54.5s). Filtering removes most tests, so a single ORIGINAL/INITIAL cap is wrong.
- **Instrumenting ORIGINAL execution is wasted when ORIGINAL PIT is off.** ORIGINAL coverage is
  consumed only by ORIGINAL PIT; with it off, `COLLECT_JACOCO_DATA_ORIGINAL` is skipped, so the JaCoCo
  agent on the ORIGINAL suite is pure overhead. Removing it drops commons-math ORIGINAL from 70s
  instrumented to 61s native.
- **The census overrides are mostly not data-justified.** JPF (0.5s) and INITIAL execution (54.5s) sit
  under the 10s / 60s baselines; census PIT that completed finished by 1467s. The two INITIAL-PIT
  timeouts exceed both 1800s and 3600s, so they are excluded at either cap.
- **A slow ORIGINAL suite is only worth accommodating when the project yields.** commons-pool spends
  1200s+ on ORIGINAL and yields 0 generalizations, so excluding it is correct. commons-math spends 70s
  and yields 273, so it must be kept.

## Principle

Every timed stage has a fixed budget keyed on its processing stage, with one default set that covers
every corpus, so no profile config overrides it (the RQ5 methodology configs excepted). No budget
scales with the generalization count, `tries`, or mutant count:

- **Generalization count** does not drive PIT runtime (surface-bound) and drives execution only weakly;
  a fixed budget sized to the demanding corpus covers realistic projects, and one that blows it is an
  honest exclusion.
- **`tries`** is the tradeoff the analysis measures; a budget that grew with `tries` would let a
  higher-tries variant escape the timeout pressure the comparison exposes.
- **Mutant count / covered surface** is what the fixed PIT budget bounds, not a scaling input.

ORIGINAL and INITIAL execution get separate caps because ORIGINAL runs the full upstream suite while
INITIAL runs the filtered subset. The ORIGINAL cap is set above the slowest *valuable* project and
below the worthless-slow one, so a project that burns a large budget without yielding is excluded.

## Mechanism

A pure util `TimeoutBudget.forStage(ProcessingStage)` returns the fixed budget in seconds, a switch
mapping each timed stage to its configured value (mirroring the existing `mavenBuildFileFor(stage)`
resolver, throwing for an unmapped stage). `TestExecutionTask` and `PitDataCollectionTask` set their
`ConsoleCommand` wall from it; SPF's per-assertion budget stays enforced in `TestGeneralizationListener`.
`TestExecutionTask.jacocoSkipped(stage, pitestOriginalEnabled)` gates the JaCoCo agent: ORIGINAL
execution runs un-instrumented when ORIGINAL PIT is off, INITIAL and GENERALIZED always instrument.
`Configuration` remains the raw-config reader; both helpers are unit-tested in isolation.

| Tier | Stage | Fixed budget | Basis |
|---|---|---|---|
| Fast | `EXECUTE_JPF` (SPF) | 10s / assertion | census 0.5s |
| Fast | `EXECUTE_TESTS` INITIAL | 60s | census subset 54.5s, RepoReapers 49s |
| Fast | `EXECUTE_TESTS` ORIGINAL | 120s | full suite 61s native; keeps commons-math (273 gens), excludes commons-pool (1200s+, 0 gens) |
| Heavy | `EXECUTE_TESTS` GENERALIZED | 1800s | census 1531s |
| Heavy | `COLLECT_PIT_DATA` INITIAL / GENERALIZED | 1800s | census <= 1467s, RepoReapers 1021s |
| — | `COLLECT_JACOCO_DATA` / build | unbounded (project cap) | cheap report / naturally bounded |

ORIGINAL PIT and ORIGINAL JaCoCo collection are also skipped when ORIGINAL PIT is off, so the whole
ORIGINAL-coverage path stays inert unless something consumes it.

## Project timeout

With both PIT stages at 1800s a project can legitimately spend ~3600s in PIT plus build, JPF, JaCoCo,
and execution. `REPOREAPERS_PROJECT_TIMEOUT` must exceed the sum of a project's stage budgets or it
silently becomes the real cap. Phase 2 sets it to 14400s.

## Config schema

The corpus is re-run for every measurement, so config keys are not a compatibility surface. The
`junit`, `pitest`, and `jpf` blocks are restructured onto one consistent shape; no legacy keys are
preserved. `junit` splits execution into `original` and `initial`.

```hocon
teralizer {
  jpf {
    timeout { per-assertion = 10 }
  }
  junit {
    timeout { original = 120, initial = 60, generalized = 1800 }
  }
  pitest {
    timeout { original-initial = 1800, generalized = 1800 }
  }
}
```

**No per-config timeout overrides.** Sized to the demanding corpus, the defaults let the JARVIS census
and per-corpus benchmark configs drop their `jpf` / `junit` / `pitest` timeout keys and inherit the
reference values. A strip, not a value translation.

**Exception - experiment-methodology caps.** The plain `commons-lang-3.5.conf` / `commons-math-3.5.conf`
(the RQ5 head-to-head and tries sweep) replicate the paper's caps (`junit` flat 120, `pitest` 300), not
a runtime fit. They migrate to the split keys preserving those values (`original = 120, initial = 120`),
and whether they later adopt the unified defaults is an RQ5 decision.

## Constants and evidence

- **Fast tier.** `jpf.timeout.per-assertion` = 10s (census 0.5s). `junit.timeout.initial` = 60s (census
  subset 54.5s, RepoReapers 49s, the paper value). `junit.timeout.original` = 120s: above the slowest
  valuable ORIGINAL suite (commons-math 61s native, 273 gens) and well below the worthless one
  (commons-pool 1200s+, 0 gens), so the wasteful case is bounded and excluded.
- **Heavy tier = 1800s.** `junit.timeout.generalized` (census generalized execution 1531s).
  `pitest.timeout.*` (census completed <= 1467s, RepoReapers 1021s).

The PIT cap sits in a clean gap: every PIT task that completed finished by 1467s, and the only timeouts
were two census INITIAL-PIT tasks at the old 3600s cap. 1800s accommodates everything that completed and
excludes those two exactly as 3600s did. Residual uncertainty is small-N: with nine census projects a
larger rerun could surface an INITIAL-PIT project in the 1800-3600s band; a scoped ~20-project spike
would settle it, and is optional.

## Out of scope

- Scaling any budget by generalization count, `tries`, or mutant count.
- Backward-compatible config keys (corpus re-runs; no compatibility surface).
- Per-invocation timeouts for `COLLECT_JACOCO_DATA` or build stages.
- The RQ5 methodology caps, whose values are decided with the RQ5 work.

## Acceptance criteria

- `TimeoutBudget.forStage` and `TestExecutionTask.jacocoSkipped` are pure, unit-tested (per stage, the
  unmapped-stage error, and the ORIGINAL-PIT-off agent skip).
- `TestExecutionTask` and `PitDataCollectionTask` derive their `ConsoleCommand` wall from `TimeoutBudget`;
  no inline timeout arithmetic; no timeout depends on generalization count, `tries`, or mutant count.
- ORIGINAL execution skips the JaCoCo agent when ORIGINAL PIT is off.
- `reference.conf` carries the unified `timeout` schema with `junit` split into `original` / `initial`;
  no legacy timeout keys remain anywhere.
- No profile config sets a timeout override except the RQ5 methodology configs (migrated to the split
  keys preserving their flat 120).
- `./gradlew build` green and one `scripts/verify-pipeline.sh` golden pass; a census fixture smoke
  clears `EXECUTE_TESTS_ORIGINAL` under the 120s cap.
