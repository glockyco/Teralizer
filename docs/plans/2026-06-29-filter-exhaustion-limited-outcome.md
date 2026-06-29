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
  `maxMisses` (default 10000). This is the path Teralizer's generated tests take
  when construction cannot encode the predicate and the residual filter must reject
  misses at runtime.
- jqwik AUTO generation can exhaust finite delegates, but only if every wrapper in
  the arbitrary chain preserves `exhaustive(long)`. Teralizer's seed wrapper must
  therefore forward the delegate's exhaustive generator; otherwise a finite range
  such as `chars().range(0, 31)` degrades into randomized `tries` sampling.
- `filter(int maxMisses, Predicate)` (since 1.7.0) only moves the threshold; on a
  measure-near-zero predicate it still trips or runs unboundedly. It cannot turn a
  partial run into a pass.
- `@Property(maxDiscardRatio)` (default 5) governs `Assume.that()` runtime assumptions
  and yields an `EXHAUSTED` result -- a **different mechanism Teralizer does not use**.

There is no jqwik 1.8.5 option that accepts a property which tested a few inputs and
then exhausted. So leniency must be **Teralizer intercepting jqwik's failure outcome
and reclassifying it** with our own signal -- an outcome-mapping layer over jqwik, not
a config flag. Exhaustive finite delegates are handled before this layer by preserving
jqwik's native exhaustive generation; `LIMITED` is only for residual/filter paths that
still end in `TooManyFilterMissesException`.

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
  is known and finite and the run covered all of it, the result is a full pass, not
  merely `LIMITED`. Prefer jqwik's native exhaustive generation for this case: a
  constructed finite delegate should complete without `TooManyFilterMissesException`.
  A post-hoc `FULL` upgrade is only for finite domains whose cardinality is known from
  generator metadata; it is **not** inferable from the tuple log alone.

### maxMisses stays an evaluation constant, not the design lever

Do not lower `maxMisses` inside this spec to save runtime. For residual filters, the
tradeoff is governed by the predicate's effective pass rate under jqwik's actual
arbitrary distribution: a lower threshold can save time on hopelessly sparse filters,
but it can also produce zero useful tuples where the default threshold would have
found some. Tuning that threshold would become its own experimental variable and would
complicate the NAIVE/IMPROVED comparison.

The preferred fixes are ordered:

1. **construct by design** when the planner can encode the partition;
2. **preserve jqwik exhaustive generation** through wrappers so finite constructed
   delegates stop at their finite cardinality;
3. **classify residual filter exhaustion** with `LIMITED` only when construction and
   exhaustive generation cannot avoid a `TooManyFilterMissesException`.

`maxMisses` remains the jqwik default unless a separate pre-evaluation study defines a
stable policy for all variants.

### `ch < 32` is a NAIVE-only filter-exhaustion example

The `isAsciiPrintable` / `ch < 32` row does not show an IMPROVED planner failure.
`NumericDomainPlanner.createCharArbitrary` emits `Arbitraries.chars().range(min, max)`,
so IMPROVED constructs the `[0, 31]` partition directly (`p = 1`) before any supplier-level
residual `.filter(...)` that is appended when `applyInputFilter` is true. For this
specific row, the constructed values already satisfy the residual predicate, so the
filter does not behave like a sparse rejection sampler. The recorded sweep reflects
that: IMPROVED completes the probe at 100/200/1000 tries, while NAIVE's full-domain
`chars().filter(ch < 32)` path exhausts at higher budgets after collecting all 32
distinct values.

That split is load-bearing for the spec: `LIMITED` must not blur away IMPROVED's
by-construction advantage. The seed-wrapper `exhaustive(long)` preservation removes a
separate false inefficiency -- finite constructed delegates falling back to randomized
`tries` because the wrapper hid jqwik's exhaustive generator. It does **not** make
arbitrary sparse residual filters exhaustive, and it does not eliminate the need for a
`LIMITED` policy for true filter-exhausted residual paths.

### Persistence model

`generalization.is_included` stays the compatibility gate: `true` for full and
`LIMITED`, `false` for excluded. The `filter_result` row for the generalization filter
is the first-slice source of truth for the distinction:

- full pass -- `NonPassingTestFilter` writes `decision = ACCEPT`, no LIMITED reason,
  `distinct_new_tuples = NULL`;
- `LIMITED` -- `NonPassingTestFilter` writes `decision = ACCEPT`, stable reason
  `LIMITED_TOO_MANY_FILTER_MISSES`, and `distinct_new_tuples = N` where `N >= 1`;
- excluded by filter exhaustion with readable seed-only evidence -- `NonPassingTestFilter`
  writes `decision = REJECT`, stable reason `FILTER_EXHAUSTED_SEED_ONLY`, and
  `distinct_new_tuples = 0`;
- excluded by filter exhaustion with missing/unreadable evidence -- `NonPassingTestFilter`
  writes `decision = REJECT`, stable reason `FILTER_EXHAUSTED_VALUE_LOG_MISSING`, and
  `distinct_new_tuples = NULL`;
- excluded by another failure -- `decision = REJECT`, with the existing rejection reason
  and `distinct_new_tuples = NULL`.

This keeps the inclusion boolean compatible with existing pipeline scheduling while
making LIMITED queryable from the filter ledger. Consumers must not parse prose: the
LIMITED reason must be the stable code `LIMITED_TOO_MANY_FILTER_MISSES`, and
filter-exhaustion rejections must distinguish `FILTER_EXHAUSTED_SEED_ONLY` from
`FILTER_EXHAUSTED_VALUE_LOG_MISSING`. The distinct-new-tuple count is stored in
nullable `filter_result.distinct_new_tuples` (`INTEGER`): `N >= 1` for `LIMITED`, `0`
for seed-only evidence, `NULL` when evidence is absent or the outcome is unrelated to
filter exhaustion. Existing rows get `NULL` for the count and no LIMITED/filter-exhaustion
reason; there is no historical backfill in the schema migration.

### Mechanism: post-process the persisted result

A spike confirmed the needed data already exists for the failing row: the excluded
`isAsciiPrintable` (`ERROR` / `TooManyFilterMisses`) has its value log collected at the
canonical path (`14.NAIVE_1000_TRIES.junit.tsv`, 178 rows / **32 distinct** tuples) and
the DB already stores `failure_type = TooManyFilterMissesException`. Classification is
therefore pure pipeline post-processing -- **no hook, no per-test generated code, no
test-suite growth**:

- after junit collection, for a row with `result = ERROR` and
  `failure_type = TooManyFilterMissesException`, read the collected value log; if
  distinct tuples beyond the seed `>= 1` -> `LIMITED` (`is_included = true`), else
  `EXCLUDED`.

For filter-exhausted randomized runs, use explicit seed metadata if the value log gains
it later; otherwise the seed tuple is the first serialized tuple in the value log. Count
distinct full-tuple rows after removing all rows equal to that seed tuple. An empty,
missing, or unreadable value log yields no evidence and remains `EXCLUDED` with a
distinct reason; failure type alone is never enough to infer `LIMITED`. An
`AroundPropertyHook` was considered and rejected: it would inject code into every
generated test and grow the suite, and it is unnecessary because inclusion is already
Teralizer's decision, not jqwik's.

## Current implementation status

- **Shipped:** generated `FirstValueArbitrary` preserves jqwik exhaustive generation by
  delegating `exhaustive(long)` to the wrapped arbitrary (`0fe290f6`). This covers the
  seed-wrapper-caused fallback from finite exhaustive domains to randomized `tries`.
- **Not implemented:** `LIMITED` classification. `TestFilteringTask` still applies
  `NonPassingTestFilter` for generated tests, so a `TooManyFilterMissesException` row
  is still excluded.
- **Not implemented:** `LIMITED` schema/metadata. There is no stored `LIMITED` outcome
  or distinct-new-tuple gate column yet.
- **Not implemented:** analysis/scorecard handling. `jarvis_scoreboard.py` currently
  classifies `TooManyFilterMissesException` as `precondition_rejected`, not `LIMITED`.

## Scope (subsystems touched)

- **Generated seed wrapper** -- preserve `exhaustive(long)` by delegating to the wrapped
  arbitrary so finite constructed domains use jqwik's native exhaustive mode instead
  of entering randomized `tries` sampling.
- **Filter stage** -- `TestFilteringTask` (`FILTER_GENERALIZATIONS`) reads the
  collected value log and maps the exhausted-with-new-tuple case to `LIMITED` instead
  of exclusion. No generated-test-template hook.
- **Schema** -- keep `generalization.is_included` as the compatibility boolean. Store
  LIMITED on the accepting `NonPassingTestFilter` `filter_result` row with stable reason
  `LIMITED_TOO_MANY_FILTER_MISSES` plus nullable `filter_result.distinct_new_tuples`
  (`INTEGER`); use `FILTER_EXHAUSTED_SEED_ONLY` with count `0` for readable seed-only
  filter exhaustion and `FILTER_EXHAUSTED_VALUE_LOG_MISSING` with count `NULL` for
  missing evidence. Existing rows keep `NULL` and are not backfilled.
- **Analysis** -- `classify_generated_test_outcome` and the scorecard recognize
  `LIMITED`; PVC reported alongside.

## Presentation semantics

`LIMITED` is included and counts as passing in high-level scorecards. The stable reason
and distinct-new-tuple count are diagnostic metadata, not a separate high-level result.
Tables whose purpose is inclusion, capability, or effectiveness should collapse
`LIMITED` into `passed` by default.

Generation-specific analyses may split the same rows out as diagnostics:

- `full` -- normal pass;
- `limited_filter_exhausted` -- accepted after useful tuple generation and filter
  exhaustion;
- `filter_exhausted_seed_only` -- rejected after readable evidence showed no new tuple;
- `filter_exhausted_value_log_missing` -- rejected because the evidence needed for the
  gate was unavailable;
- other excluded / failed categories as needed.

This preserves the NAIVE-vs-IMPROVED generation-quality signal without polluting
high-level scorecards. A high-level table can report a row as passed while a generator
diagnostic table still records that the pass was limited by residual-filter exhaustion.

## Non-goals

- Not a jqwik config change; not tuning `maxMisses` as the fix.
- Not a replacement for by-construction generation -- `LIMITED` is a safety net for
  shapes the planner cannot construct, not a reason to stop constructing.
- Not retroactively re-scoring archived runs.

## First-slice decisions

- (resolved by spike) Compute distinct-tuples post-hoc from the collected value log --
  verified present for `ERROR` rows, so no in-JVM counter and no hook are needed.
- (resolved) First-slice schema location: keep `generalization.is_included` as the
  compatibility gate; represent LIMITED on the accepting `NonPassingTestFilter`
  `filter_result` row with stable reason `LIMITED_TOO_MANY_FILTER_MISSES` and nullable
  `filter_result.distinct_new_tuples INTEGER`. Seed-only filter exhaustion writes
  `FILTER_EXHAUSTED_SEED_ONLY` with count `0`; missing/unreadable value-log evidence
  writes `FILTER_EXHAUSTED_VALUE_LOG_MISSING` with count `NULL`. Existing rows remain
  `NULL` and are not backfilled.
- (resolved) Seed identification for filter-exhausted randomized runs: use explicit
  seed metadata if present; otherwise the first serialized tuple in the value log is the
  seed, and all rows equal to it are excluded from the distinct-new-tuple count.
- (resolved) Scorecard semantics: `LIMITED` is included and counts as `passed` in
  high-level scorecards by default. The stable reason and tuple count remain queryable
  for generation-specific diagnostics, where LIMITED rows may be split out.
- (resolved) Backfill policy: forward-only. Archived runs keep their historical
  classification unless a paper-specific table explicitly asks for a regenerated run.
- (resolved) Missing or unreadable value log: conservative `EXCLUDED`, with a distinct
  reason. Never infer `LIMITED` from `TooManyFilterMissesException` alone.

## Acceptance criteria

- A finite constructed delegate can preserve jqwik AUTO exhaustive behavior through the
  seed wrapper and complete without randomized `tries` sampling.
- A probe that exhausts the residual filter after `>= 1` new tuple is kept as
  `LIMITED`, sound, with PVC telemetry; a probe that only ran the seed is `EXCLUDED`.
- Missing or unreadable value logs stay excluded with a distinct reason; they never
  become `LIMITED` by failure type alone.
- The gate is the distinct-tuple count, not PVC; for filter-exhausted randomized runs,
  explicit seed metadata wins if present, otherwise the seed is the first serialized
  value-log row and rows equal to it do not count as new tuples.
- High-level scorecards count `LIMITED` rows as passed while retaining the stable reason
  and tuple count for generation-diagnostic views.
- No generated test becomes unsound.
