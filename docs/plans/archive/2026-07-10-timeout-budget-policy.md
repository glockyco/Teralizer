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

Timed stages currently derive their wall budget three different ways:

- **JPF / SPF** (`EXECUTE_JPF`): `jpf.max-execution-time` = 10s, enforced per assertion inside
  `TestGeneralizationListener` (fresh listener per `JpfExecutionTask`, one task per `AssertionRecord`).
- **Test execution** (`TestExecutionTask`): ORIGINAL and INITIAL use a flat `junit.max-execution-time`
  = 60s `ConsoleCommand` wall; GENERALIZED overrides it with `scaledGeneralizedTimeoutSeconds`, a
  bespoke `60 x ceil(gens x tries / 1600)` capped at `junit.max-generalized-execution-time` = 3600s.
- **PIT** (`PitDataCollectionTask`): all variants use a flat `pitest.max-execution-time` wall.

On top of the shapes, profile configs (the JARVIS census set, per-corpus benchmarks) carry their own
timeout overrides (`jpf` 30, `junit` 1200, `pitest` 3600) that predate any measurement.

Two problems with the shapes. First, the PIT cap was set far too low: at 300s the wall excludes
large-but-healthy projects, because PIT cost scales with the mutation surface. The paper's 300s cap
hit 40 of 114 reduction-reaching projects (~35%) at `COLLECT_PIT_DATA_INITIAL`. A flat cap is right;
it just has to be sized to the real runtime tail rather than to a number copied from the paper.
Second, the one scaled budget scales by the wrong thing: it multiplies by `tries`, which massively
over-budgets (byteseek's 143 generalizations x 200 tries gave a ~1080s wall for a run that finished in
53s), and it lets a higher-tries variant buy its way out of the timeout pressure the cross-variant
comparison exists to measure.

### What the runtimes actually show

RepoReapers calibration (`pit-calibrate`: PIT on, ORIGINAL off, 3600s cap, IMPROVED_200):

| Project | included gens | `EXECUTE_TESTS` INITIAL -> GENERALIZED | `COLLECT_PIT` INITIAL -> GENERALIZED |
|---|---|---|---|
| JadConfig | 79 | 21.3s -> 22.5s (+6%) | 74s -> 64s (-14%) |
| byteseek | 143 | 48.8s -> 52.7s (+8%) | 1021s -> 1044s (+2%) |

(CormenImpl and webbit produced 0 included generalizations, so they are INITIAL-only points; webbit
exited non-zero.)

JARVIS census (`postgres_jarvis_census`, the demanding corpus), over all tasks per stage:

| Stage | succeeded max | failed (timeouts) |
|---|---|---|
| `EXECUTE_JPF` (per assertion) | 0.5s | none (non-timeout failures <= 1.3s) |
| `EXECUTE_TESTS_INITIAL` | 54.5s | none |
| `EXECUTE_TESTS_GENERALIZED` | 1531s | none |
| `COLLECT_PIT_DATA_INITIAL` | 1375s | 2, at the 3600s cap |
| `COLLECT_PIT_DATA_GENERALIZED` | 1467s | none |

Findings:

- **PIT is mutation-surface-bound.** GENERALIZED PIT approximately equals INITIAL PIT (byteseek +2%,
  JadConfig -14%); the surface is shared and generalizations only add cheap covering tests. Neither
  scales with the generalization count.
- **Test execution barely grows on RepoReapers (+6-8%) but genuinely grows on big-commons projects**
  (census generalized execution reaches 1531s), because those projects generalize far more tests. This
  is the paper's NAIVE_200-style increase in absolute terms.
- **The census overrides are mostly not data-justified.** JPF (max 0.5s) and INITIAL execution (max
  54.5s) sit under the 10s / 60s baselines; census PIT that completed finished by 1467s. Only two
  INITIAL-PIT tasks exceed 1800s, and they exceed 3600s too, so they are excluded at either cap.
  Nothing that completed lands in the 1467-3600s band.

## Principle

Every timed stage has a fixed budget keyed on its processing stage, with one default set that covers
every corpus, so no profile config overrides it. Original and initial (baseline) work gets the
reference budget; the generalized variant gets its own, larger, fixed budget. No budget scales with
the generalization count, `tries`, or mutant count:

- **Generalization count** does not drive PIT runtime (surface-bound) and drives execution only
  weakly; a fixed budget sized to the demanding corpus covers realistic projects, and one that blows
  it is an honest exclusion.
- **`tries`** is the tradeoff the analysis measures; a budget that grew with `tries` would let a
  higher-tries variant escape the timeout pressure the comparison exposes.
- **Mutant count / covered surface** is what the fixed PIT budget bounds, not a scaling input.

## Mechanism

A pure util `TimeoutBudget.forStage(ProcessingStage)` returns the fixed budget in seconds, a switch
mapping each timed stage to its configured value (mirroring the existing `mavenBuildFileFor(stage)`
resolver, throwing for an unmapped stage). `TestExecutionTask` and `PitDataCollectionTask` set their
`ConsoleCommand` wall from it; SPF's per-assertion budget stays enforced in `TestGeneralizationListener`.
`Configuration` remains the raw-config reader; `TimeoutBudget` owns the stage-to-budget mapping and is
unit-tested in isolation. No arithmetic, no per-run inputs.

| Tier | Stage | Fixed budget | Basis (succeeded max) |
|---|---|---|---|
| Fast | `EXECUTE_JPF` (SPF) | 10s / assertion | census 0.5s, RepoReapers headroom |
| Fast | `EXECUTE_TESTS` ORIGINAL / INITIAL | 60s | census 54.5s, RepoReapers 49s |
| Heavy | `EXECUTE_TESTS` GENERALIZED | 1800s | census 1531s, RepoReapers 53s |
| Heavy | `COLLECT_PIT_DATA` ORIGINAL / INITIAL / GENERALIZED | 1800s | census <= 1467s, RepoReapers 1021s; outliers > 3600s excluded |
| — | `COLLECT_JACOCO_DATA` / build | unbounded (project cap only) | cheap report / naturally bounded |

## Project timeout

With both PIT stages at 1800s, a single project can legitimately spend ~3600s in PIT plus build, JPF
(per assertion), JaCoCo, and generalized execution. `REPOREAPERS_PROJECT_TIMEOUT` must exceed the sum
of a project's stage budgets, or it silently becomes the real cap and turns healthy long projects into
timeouts. Phase 2 sets it to 14400s, comfortably above the summed per-stage budgets, so the per-stage
budgets remain the binding bounds.

## Config schema

The corpus is re-run for every measurement, so config keys are not a compatibility surface. The
`junit`, `pitest`, and `jpf` blocks are restructured onto one consistent shape; no legacy keys are
preserved. Each stage exposes a `timeout` block: `jpf` a single per-assertion value; `junit` and
`pitest` an `original-initial` and a `generalized` value.

```hocon
teralizer {
  jpf {
    timeout { per-assertion = 10 }
  }
  junit {
    timeout { original-initial = 60, generalized = 1800 }
  }
  pitest {
    timeout { original-initial = 1800, generalized = 1800 }
  }
}
```

**No per-config timeout overrides.** Because the defaults are sized to the demanding corpus, the
JARVIS census and per-corpus benchmark configs drop their `jpf` / `junit` / `pitest` timeout keys and
inherit the reference defaults. This is a strip, not a value translation.

**Exception - experiment-methodology caps.** The plain `commons-lang-3.5.conf` / `commons-math-3.5.conf`
(the RQ5 head-to-head and tries sweep) set `junit` 120 / `pitest` 300 as a deliberate replication of
the paper's caps, not a runtime fit. They migrate to the new key names preserving those values, and
whether they later adopt the unified defaults is an RQ5 decision, not folded in silently here.

## Constants and evidence

Two tiers, both from measured runtimes across the RepoReapers calibration and the JARVIS census:

- **Fast tier.** `jpf.timeout.per-assertion` = 10s (census max 0.5s). `junit.timeout.original-initial`
  = 60s (census 54.5s, RepoReapers 49s; unchanged paper value).
- **Heavy tier = 1800s.** `junit.timeout.generalized` (census generalized execution max 1531s; the
  300s a RepoReapers-only view suggested would clip the census). `pitest.timeout.original-initial` /
  `generalized` (census completed <= 1467s, RepoReapers 1021s).

The PIT cap sits in a clean gap: every PIT task that completed finished by 1467s, and the only
timeouts were two census INITIAL-PIT tasks at the old 3600s cap (true duration unknown, over an hour of
baseline mutation). 1800s accommodates everything that completed and excludes those two exactly as
3600s did. The only residual uncertainty is small-N: with nine census projects, a larger rerun could
surface an INITIAL-PIT project in the 1800-3600s band. A scoped ~20-project uncensored spike would
settle that, and is optional.

## Out of scope

- Scaling any budget by generalization count, `tries`, or mutant count.
- Backward-compatible config keys (corpus re-runs; no compatibility surface).
- Per-invocation timeouts for `COLLECT_JACOCO_DATA` or build stages.
- The RQ5 methodology caps (`commons-lang` / `commons-math` plain configs), whose values are decided
  with the RQ5 work.

## Acceptance criteria

- `TimeoutBudget.forStage(ProcessingStage)` is a standalone pure util, unit-tested per stage including
  the unmapped-stage error.
- `TestExecutionTask` and `PitDataCollectionTask` derive their `ConsoleCommand` wall from
  `TimeoutBudget`; no stage computes a timeout inline, and no timeout depends on generalization count,
  `tries`, or mutant count.
- `reference.conf` carries the unified `timeout` schema for `jpf`, `junit`, and `pitest`; every reader
  is updated; no legacy timeout keys remain anywhere.
- No profile config sets a `jpf` / `junit` / `pitest` timeout override, except the RQ5 methodology
  configs, which migrate to the new key names preserving their paper caps.
- Heavy-tier stages (generalized execution, both PIT variants) use 1800s; fast-tier stages use 10s / 60s.
- The Phase 2 profile sets `REPOREAPERS_PROJECT_TIMEOUT` = 14400s.
- `./gradlew build` green and one `scripts/verify-pipeline.sh` golden pass.
