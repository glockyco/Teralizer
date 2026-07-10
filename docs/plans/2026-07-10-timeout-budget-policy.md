---
title: Unified Timeout Budget Policy
type: spec
status: draft
created: 2026-07-10
parent: 2026-06-26-teralizer-overview
---

# Unified Timeout Budget Policy

One timeout policy across every timed pipeline stage: a fixed budget for original and initial
(non-generalized) work, and a budget that scales with the number of included generalizations for
generalized work. Replaces three inconsistent per-stage timeout shapes with a single shared
mechanism.

## Problem

Timed stages currently derive their wall budget three different ways:

- **JPF / SPF** (`EXECUTE_JPF`): `jpf.max-execution-time` = 10s, enforced per assertion inside
  `TestGeneralizationListener` (fresh listener per `JpfExecutionTask`, one task per `AssertionRecord`).
  Already a per-entity fixed budget.
- **Test execution** (`TestExecutionTask`): ORIGINAL and INITIAL use a flat `junit.max-execution-time`
  = 60s `ConsoleCommand` wall; GENERALIZED overrides it with `scaledGeneralizedTimeoutSeconds`, a
  bespoke `60 × ⌈gens × tries / 1600⌉` capped at `junit.max-generalized-execution-time` = 3600s.
- **PIT** (`PitDataCollectionTask`): all variants use a flat `pitest.max-execution-time` wall.

The flat PIT wall is the wrong shape. A project's PIT cost scales with its size (more generalized
tests, more mutants), so a flat cap clips big projects for being big. In the paper's data
(`postgres_test`, IMPROVED_200, 300s cap) 40 of 114 reduction-reaching projects (~35%) hit the cap at
`COLLECT_PIT_DATA_INITIAL`. Uncensored calibration on current code confirms the tail is real:
CormenImpl's INITIAL PIT runs 747s, 2.5x the paper's 300s.

Three shapes for one idea (fixed-vs-scaled budget) is also a maintenance hazard: the scaling policy
lives as a private static in one task, PIT has no scaling at all, and each stage reads its own config
keys.

## Principle

Every timed stage's budget is either **fixed** (original / initial / baseline work) or **scaled by
the count of included generalizations** (generalized work). Two things are deliberately *not* inputs:

- **Number of tries.** The execution timeout is part of the tradeoff the analysis measures. If the
  budget grew with tries, a higher-tries variant would buy its way out of the very timeout pressure
  the cross-variant comparison exists to expose. Budget depends only on `#generalizations`, a
  structural property of the generated suite, so the same pressure applies to every variant.
- **Mutant count / covered surface.** Mutant load is the thing the budget bounds, not a scaling
  input. A generalization whose mutant load blows its budget is an honest exclusion, the same stance
  as an over-ceiling test-execution timeout. Folding mutant count into the budget would make it
  impossible to ever time out for being too expensive.

Original and initial stages stay fixed (confirmed): they are the baseline the generalized budget is a
multiple of, so pinning them is the anchor, not an exception. A fixed INITIAL PIT wall set well above
the calibrated tail solves the clipping without scaling, since anything past it is a pathological
mutant load, not a big-but-healthy project.

## Mechanism

A pure util `TimeoutBudget` (lifting today's `scaledGeneralizedTimeoutSeconds` out of
`TestExecutionTask` into a shared home) exposes two functions, in seconds:

- `fixed(base)` -> `base`
- `scaled(generalizationCount, factor, baseline, ceiling)` ->
  `min(baseline + factor * generalizationCount, ceiling)`

`factor` is seconds per included generalization; `baseline` is a flat offset (fixed overhead plus, for
PIT, the original-suite mutation cost); `ceiling` bounds pathological `generalizationCount`. No `tries`
term. `Configuration` stays the raw-config reader; `TimeoutBudget` owns the arithmetic and is
unit-tested in isolation.

| Stage | Budget | Scaling entity |
|---|---|---|
| `EXECUTE_JPF` (SPF) | `fixed(jpf base)` per assertion | assertion (fixed per run) |
| `EXECUTE_TESTS` ORIGINAL / INITIAL | `fixed(junit base)` | — |
| `EXECUTE_TESTS` GENERALIZED | `scaled(#included gens, junit factor, junit baseline, junit ceiling)` | generalization |
| `COLLECT_PIT_DATA` ORIGINAL / INITIAL | `fixed(pit base)`, base set large from calibration | — |
| `COLLECT_PIT_DATA` GENERALIZED | `scaled(#included gens, pit factor, pit baseline, pit ceiling)` | generalization |
| `COLLECT_JACOCO_DATA` / build | unbounded (project cap only) | — |

`#included generalizations` is the same count `TestExecutionTask` already fetches
(`fetchIncludedGeneralizedClasses`); `PitDataCollectionTask` derives it from the same included set it
uses to build `targetTests` / `targetClasses`.

The generalized PIT `factor` and `baseline` are higher than test execution's, reflecting that PIT
reruns the covering suite per mutant. The PIT `baseline` is set to approximately the fixed INITIAL-PIT
value, because generalized PIT re-mutates the original suite too, so `factor * #gens` adds only the
incremental generalized cost on top.

Orthogonal fine bound: PIT's native per-mutant timeout (`timeoutConstant` / `timeoutFactor`, currently
unset so PIT defaults apply) is set explicitly in `pitest-config-maven.txt`, sized for jqwik-property
baselines. The per-project wall catches total runaway; the per-mutant timeout catches a single
pathological mutation.

## Config schema

The corpus is re-run for every measurement, so config keys are not a compatibility surface. The
`junit`, `pitest`, and `jpf` blocks are restructured onto one consistent shape rather than preserving
legacy keys. Each stage exposes a `timeout` block: fixed stages carry a single `fixed` value; scaled
stages additionally carry `generalized { factor, baseline, ceiling }`.

```hocon
teralizer {
  jpf {
    timeout { fixed = 10 }                 # per assertion
  }
  junit {
    timeout {
      fixed = 60                           # ORIGINAL / INITIAL
      generalized { factor = <s/gen>, baseline = <s>, ceiling = 3600 }
    }
  }
  pitest {
    timeout {
      fixed = <large, from calibration>    # ORIGINAL / INITIAL
      generalized { factor = <s/gen, > junit>, baseline = <~= pitest fixed>, ceiling = <s> }
    }
    per-mutant { timeout-constant-ms = <ms>, timeout-factor = <x> }
  }
}
```

## Constants from calibration

`pit-calibrate` (profile `reporeapers-pit-calibrate.conf`: PIT on, ORIGINAL off, generous 3600s cap,
IMPROVED_200, four reduction-reaching projects spanning fast controls and paper-slow-tail members)
supplies the numbers:

- **`pitest.timeout.fixed`** — from the uncensored INITIAL PIT distribution, set comfortably above the
  observed tail (CormenImpl already 747s) so only pathological mutant loads clip.
- **`pitest.timeout.generalized.factor`** — regress GENERALIZED PIT `runtime` against
  `#included generalizations` across the calibration projects; the slope is the per-generalization
  factor.
- **`pitest.timeout.generalized.baseline`** — approximately `pitest.timeout.fixed` (generalized PIT
  includes original-suite mutation).
- **`pitest.timeout.generalized.ceiling`** — a headroom multiple of the observed max, bounding
  pathological `#gens`.
- **`junit.timeout.*`** — `fixed` (60) and `ceiling` (3600) carry over unchanged. `factor` (seconds
  per generalization) and `baseline` are set fresh from observed generalized test-execution `runtime`
  vs `#included generalizations` (calibration plus existing rerun2 telemetry): dropping `tries` means
  the old `⌈gens x tries / 1600⌉` step form no longer defines them, so they are chosen to preserve the
  intended per-generalization tradeoff without a tries term.

## Out of scope

- Backward-compatible config keys (corpus re-runs; no compatibility surface).
- Scaling any budget by `tries` or by mutant count.
- Scaling ORIGINAL / INITIAL stages.
- Per-invocation timeouts for `COLLECT_JACOCO_DATA` or build stages (cheap report / naturally bounded).

## Acceptance criteria

- `TimeoutBudget` is a standalone pure util with unit tests covering `fixed`, `scaled`, the ceiling
  clamp, and the zero / one generalization edges.
- `TestExecutionTask` and `PitDataCollectionTask` both derive their `ConsoleCommand` wall from
  `TimeoutBudget`; no stage computes a timeout inline, and no stage's budget depends on `tries` or
  mutant count.
- `reference.conf` carries the unified `timeout` schema for `jpf`, `junit`, and `pitest`; every reader
  and profile config is updated; no legacy timeout keys remain.
- Generalized PIT wall scales with `#included generalizations`; INITIAL PIT wall is fixed and set above
  the calibrated tail.
- PIT per-mutant timeout is set explicitly in `pitest-config-maven.txt`.
- `./gradlew build` green and one `scripts/verify-pipeline.sh` golden pass.
