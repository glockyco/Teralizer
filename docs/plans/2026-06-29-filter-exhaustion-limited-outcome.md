---
title: Filter-Exhaustion LIMITED Outcome
type: spec
status: draft
created: 2026-06-29
parent: 2026-06-26-teralizer-overview
---

# Filter-Exhaustion LIMITED Outcome

## Problem

When a generated property uses an `Arbitrary.filter(...)` whose predicate is sparse,
jqwik throws `TooManyFilterMissesException` after `maxMisses` (default 10000)
consecutive misses. The generated test then reports as an error, and
`FILTER_GENERALIZATIONS` marks the generalization `is_included = false` -- it is
discarded entirely. This happens even when the test **soundly validated the oracle on
the seed input plus one or more new tuples** before generation stalled.

Observed in `2026-06-29-pvc-budget-elasticity`: `isAsciiPrintable` passes at 100 tries
but is excluded at 200/1000 tries (NAIVE, filter-based) -- the same sound generalization
is kept or thrown away purely on the sampling budget. Discarding a generalization that
did real, sound work is questionable now that we can *measure* how much it explored.

## Why this is Teralizer post-processing, not a jqwik setting

Grounded in this repo's jqwik **1.8.5**:

- `Arbitrary.filter(predicate)` -> throws `TooManyFilterMissesException` after
  `maxMisses` (default 10000). This is the path Teralizer's generated tests take.
- `filter(int maxMisses, Predicate)` (since 1.7.0) only moves the threshold; on a
  measure-near-zero predicate it still trips or runs unboundedly. It cannot turn a
  partial run into a pass.
- `@Property(maxDiscardRatio)` (default 5) governs `Assume.that()` runtime assumptions
  and yields an `EXHAUSTED` result -- a **different mechanism Teralizer does not use**.

There is no jqwik 1.8.5 option that accepts a property which tested a few inputs and
then exhausted. So leniency must be **Teralizer intercepting jqwik's failure outcome
and reclassifying it** with our own signal -- an outcome-mapping layer over jqwik, not
a config flag.

## Design

### Acceptance gate (the precise signal)

Keep the generalization iff the recorder logged **at least one distinct serialized
input tuple beyond the original concrete tuple** (the `firstValue` seed). This is
distinct full-tuple rows in the value log, deduped so jqwik re-sampling the seed does
not count.

**Not PVC.** PVC is per-parameter distinct-value counts; for a multi-parameter probe
it conflates "parameter `a` got a new value" with "parameter `b` got a new value" and
is not a tuple count. PVC is reported as telemetry (how *much* a kept generalization
explored), never as the gate.

The signal needs no new recording: the value log the recorder already writes contains
the raw tuples, so distinct-tuples-beyond-seed is a different aggregation of the same
data than PVC.

### Outcomes

- **`LIMITED`** (new): filter-exhausted, but `>= 1` distinct new tuple was soundly
  validated. Kept (`is_included = true`), flagged distinctly from a full pass.
- **`EXCLUDED`** (unchanged): exhausted with no new tuple beyond the seed -- the
  generalization is no better than the source test.
- A `LIMITED` generalization is **sound**: every tuple it tested passed the oracle; it
  never weakens the path predicate. It is *narrow*, not *wrong*.
- **`FULL` upgrade (optional, finite domains only):** when the partition's cardinality
  is known and finite (`ch < 32` has exactly 32 values) and the run covered all of it,
  the exclusion is plainly wrong -- it is a full pass, not merely `LIMITED`. This needs
  domain-cardinality reasoning and is **not** inferable from the tuple log alone, so
  `LIMITED` is the default and the upgrade is a separate, optional enhancement for
  finite constructed domains.

### maxMisses stays at the default

Do not lower `maxMisses` to save runtime. Spike (`chars()` filtered to `ch < 32`,
~0.05% density): at `maxMisses` 10/100/1000 the generator finds **zero** valid inputs
(it trips before the first hit); only the default **10000** finds all 32 distinct, at
~50 ms. For the sparse filters that actually cause exclusions, the high default is what
lets them find anything -- lowering it turns a 32-distinct `LIMITED` into a 0-distinct
`EXCLUDED` and saves time negligible beside PIT/JaCoCo. `maxMisses` is a coverage knob,
not a runtime knob; the efficiency intuition holds only for moderately dense filters.

### Mechanism: post-process the persisted result

A 2026-06-29 spike confirmed the needed data already exists for the failing row: the
excluded `isAsciiPrintable` (`ERROR` / `TooManyFilterMisses`) has its value log
collected at the canonical path (`14.NAIVE_1000_TRIES.junit.tsv`, 178 rows /
**32 distinct** tuples) and the DB already stores
`failure_type = TooManyFilterMissesException`. Classification is therefore pure
pipeline post-processing -- **no hook, no per-test generated code, no test-suite
growth**:

- after junit collection, for a row with `result = ERROR` and
  `failure_type = TooManyFilterMissesException`, read the collected value log; if
  distinct tuples beyond the seed `>= 1` -> `LIMITED` (`is_included = true`), else
  `EXCLUDED`.

The seed (`firstValue`) is always recorded first, so the gate is distinct value-log
rows minus the seed tuple. An `AroundPropertyHook` was considered and rejected: it
would inject code into every generated test and grow the suite, and it is unnecessary
because inclusion is already Teralizer's decision, not jqwik's.

## Scope (subsystems touched)

- **Filter stage** -- `TestFilteringTask` (`FILTER_GENERALIZATIONS`) reads the
  collected value log and maps the exhausted-with-new-tuple case to `LIMITED` instead
  of exclusion. No generated-test-template change and no hook.
- **Schema** -- a `LIMITED` outcome (`filter_result` / `generalization`) plus the
  distinct-tuple count recorded for the gate. Exact columns: implementation detail.
- **Analysis** -- `classify_generated_test_outcome` and the scorecard recognize
  `LIMITED`; PVC reported alongside.

## NAIVE-vs-IMPROVED honesty

`LIMITED` must stay visibly distinct from a full pass. A `LIMITED` NAIVE row where
IMPROVED is full still shows NAIVE's filter-exhaustion -- the by-construction advantage
the comparison demonstrates is preserved, not erased. `LIMITED` is a third state, not a
silent upgrade of NAIVE to "passing."

## Non-goals

- Not a jqwik config change; not raising `maxMisses` as the fix.
- Not a replacement for by-construction generation -- `LIMITED` is a safety net for
  shapes the planner cannot construct, not a reason to stop constructing.
- Not retroactively re-scoring archived runs.

## Open questions

- (resolved by spike) Compute distinct-tuples post-hoc from the collected value log --
  verified present for `ERROR` rows, so no in-JVM counter and no hook are needed.
- How does `LIMITED` interact with the scorecard's "exclusion-free" claim -- is a
  `LIMITED` row exclusion-free, or a documented third category?
- Backfill policy for existing runs (default: none; forward-only).

## Acceptance criteria

- A probe that exhausts the filter after `>= 1` new tuple is kept as `LIMITED`, sound,
  with PVC telemetry; a probe that only ran the seed is `EXCLUDED`.
- The gate is the distinct-tuple count, not PVC.
- `LIMITED` is distinguishable from a full pass in the scorecard and the NAIVE/IMPROVED
  comparison.
- No generated test becomes unsound.
