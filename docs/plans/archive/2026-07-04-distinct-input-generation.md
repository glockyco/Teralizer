---
title: Distinct Input Generation — No Wasted Tries
type: spec
status: implemented
created: 2026-07-04
parent: 2026-06-26-teralizer-overview
archived: 2026-07-04
---

# Distinct Input Generation — No Wasted Tries

**One concern:** generated property tests re-execute inputs they have already tried — on the spike corpus, 18.8% of the try budget of `FULL` properties re-tests seen tuples (avg 99.2 tries → 80.6 distinct) — although under Teralizer's configuration (fixed `seed = "0"`, `shrinking = OFF`, deterministic path-partition oracle) a repeated input carries zero information; every try should test a previously unseen input while the domain has any left.

## Why repeats happen today

Three stacked mechanisms (verified on the `symbolic-int` fixture: 100 tries, 89 distinct, seed `x=2` executed 7×):

1. **Our own double seed-emission.** `FirstValueArbitrary` both prepends the captured original tuple to the delegate's edge cases *and* force-emits it as the first random-phase value. Under `EdgeCasesMode.FIRST` (all generated tests) the seed therefore runs at least twice. Redundant by construction.
2. **jqwik edge-case emission** — boundary-adjacent values (e.g. `2` for `between(1, MAX)`) run once as edge cases and can recur in the random phase.
3. **jqwik's biased random distribution** — deliberate oversampling of small magnitudes and boundaries. Sound PBT wisdom for shrink-target diversity and flaky-state probing; both rationales are void here (shrinking off, oracle deterministic, tests side-effect-free by selection).

jqwik already handles *small* domains correctly: when the domain fits the try budget it switches to exhaustive generation (each value exactly once, early stop). The waste is confined to the random phase over wider domains.

## Design

Both changes live in the one seam every variant shares — the `FirstValueArbitrary` wrapper template (`first-value-arbitrary.vm` / its factory) — so BASELINE-style seeds, NAIVE, and IMPROVED all inherit them identically.

1. **Seen-set dedup in the random phase.** The wrapper's `RandomGenerator.next(...)` keeps a set of serialized emitted values; on a duplicate draw it retries the delegate a bounded number of times (bound: a small multiple of the remaining budget — NEVER unbounded) and falls back to returning the duplicate when the bound is exhausted (graceful degradation in near-exhausted random domains). Deliberately an internal retry, NOT a jqwik `filter(...)`: it must stay invisible to jqwik's discard-ratio accounting, so it cannot create `TOO_MANY_FILTER_MISSES`/`SEED_ONLY` interactions at all.
   Statefulness is sound precisely because of the generated-test configuration — fixed seed, shrinking off, single-threaded execution, and the harness is already stateful (the value recorder). The javadoc must state these preconditions; if any is ever relaxed, the dedup must be revisited.
2. **Exactly-once seed guarantee.** The seed must run first and exactly once. Which of the two current mechanisms (edge-case prepend vs forced first random emission) provides "first" under `EdgeCasesMode.FIRST` is to be established by a characterization test, and the redundant mechanism removed — not guessed from reading jqwik source. The seen-set additionally makes any residual overlap harmless (edge-case values never repeat in the random phase).
3. **Exhaustive mode untouched.** The dedup lives in the random-phase generator only; exhaustive generation is already duplicate-free.

## Measurement

`jqwik_property_execution.distinct_tuples / tries` is the direct metric: ≈100% for `FULL` properties after the change (exactly 100% while the domain exceeds the budget). The `symbolic-int` fixture's golden entry gains a distinct-tuples assertion, pinning the behavior in the verification corpus.

## Acceptance

- Characterization test establishes the seed-emission mechanism under `EdgeCasesMode.FIRST`; the redundant emission is removed; a rendered-harness test asserts the seed appears exactly once in a full property run.
- Unit/harness tests: duplicate draws are retried and distinct values emitted (wide domain); bounded fallback returns a duplicate rather than looping (near-exhausted domain); exhaustive mode behavior unchanged; recorder `tries`/`distinct_tuples` semantics unchanged.
- Fixture corpus: `symbolic-int` golden updated — `distinct_tuples = tries` (or domain size, whichever is smaller); full corpus green.
- No interaction with the residual filter's discard accounting (code-inspection: no `filter(...)` involved).
