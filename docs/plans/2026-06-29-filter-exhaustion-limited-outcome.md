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

### Mechanism

A jqwik `AroundPropertyHook` in the generated-test template wraps
`property.execute()`, which returns a `PropertyExecutionResult` (it does not
necessarily throw):
1. run the property;
2. inspect the result -- if it is erroneous with a `TooManyFilterMissesException`
   throwable/cause (and catch a propagated one too, in case a future jqwik throws),
   consult the recorder for distinct-tuples-beyond-seed;
3. `>= 1` -> return a **satisfied** result and emit an explicit "filter-exhausted"
   marker so the outcome is recorded as `LIMITED`, not inferred; `0` -> return the
   original erroneous result unchanged (-> `EXCLUDED`).

Teralizer already injects the `JqwikValueRecorder` and a `@BeforeProperty` reset, so
the hook is additive to the template.

## Scope (subsystems touched)

- **Generated-test template** -- add the around-hook + the exhausted marker
  (`ModelToJavaTransformer` / supplier rendering / `JqwikValueRecorder`).
- **Filter stage** -- `TestFilteringTask` (`FILTER_GENERALIZATIONS`) maps the
  exhausted-with-new-tuple case to `LIMITED` instead of exclusion.
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

- Compute distinct-tuples in-JVM (recorder counter) vs post-hoc from the value log?
  In-JVM is needed for the live gate; the value log is the cross-check.
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
