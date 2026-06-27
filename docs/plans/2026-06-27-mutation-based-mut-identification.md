---
title: Mutation-Based MUT Identification
type: spec
status: draft
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
---

# Mutation-Based MUT Identification

Use existing PIT mutation-testing data as a dynamic focal-method oracle,
falling back to static analysis only when no mutation data exists. No new
pipeline stage, no extra test execution — the data is already collected.

## Motivation

The current static MUT identification (`TestAnalysis.findTestedMethodCall`,
step 13 `ANALYZE_TESTS`) is the dominant applicability blocker: **58,122
first-reject assertions** (MissingValue, `tested_class_path is null`) where it
fails to identify the tested method at all. Even where it succeeds, a
head-to-head against the mutation oracle shows **35% precision** — it picks the
wrong method 65% of the time (9,354 disagreements vs 5,100 agreements on 14,454
tests with killed mutants). The wrong-method picks are the classic LCBA flaw:
it selects the last call before an assert, which is often a state-inspector
(`getState`, `toString`, `getClass`) rather than the focal method.

PIT mutation data (`pit_mutation_report`, `pit_coverage_report`) is already
collected at step 25 (`COLLECT_PIT_DATA_INITIAL`) on the original test suite.
It records, per test, which production methods had mutants killed — a
near-zero-false-positive signal for the focal method, because helpers and
getters rarely have killable mutants.

## Evidence (DB-grounded, `postgres_test`, 2026-06-27)

### Availability for MissingValue-failed tests

Of 21,081 MissingValue-failed tests:

| Category | Count | % | Signal |
|---|---|---|---|
| A. Has killed mutants | 7,455 | 35% | **Direct oracle** — method(s) with killed mutants = focal |
| B. Has coverage, no kills | 3,951 | 19% | TFIDF signal — methods called uniquely by this test |
| C. No PIT data | 9,675 | 46% | 154 projects where PIT failed (6,530) or test excluded before PIT (370) |

### Killed-mutant oracle specificity

Of 15,354 tests with killed mutants (across the whole dataset):

| Methods with kills | Tests | % |
|---|---|---|
| 1 (unambiguous) | 9,106 | 59% |
| 2–3 | 4,209 | 27% |
| 4–5 | 1,096 | 7% |
| 6+ | 943 | 6% |

59% have exactly one method — a completely unambiguous oracle. For the rest, the
method with the most killed mutants is the strongest candidate (TCTracer's
Tarantula signal).

### Static-vs-mutant disagreement

On 14,454 tests where static MUT-id produced a result AND killed mutants exist:

- **AGREE:** 5,100 (35%) — static MUT is among the killed methods
- **DISAGREE:** 9,354 (65%) — static MUT picked a method with no killed mutants

Sample disagreements (static → mutant-killed):
- `BeanKey.isBeanForCreation` → `ProviderBeanKey.isProvider`
- `Board.toString` → `Board.motion`
- `Query.getQuery` → 8 methods including `BaseFunctionHandler.handle`

These are the LCBA flaw: the last call before an assert is often a getter or
`toString`, not the focal method.

### PIT failure causes (category C)

Of 9,675 no-PIT tests across 154 projects:
- 6,530: `COLLECT_PIT_DATA_INITIAL` FAILED (PIT crashed/timed out)
- 3,700+: tests excluded by test-level filters before PIT ran (NoAssertions,
  NonPassingTest, TestType)
- 370: PIT succeeded but didn't produce coverage for these specific tests

## Design

### Pipeline ordering

MUT-id currently runs at step 13 (`ANALYZE_TESTS`), before PIT at step 25.
The mutation-based oracle requires PIT data, so MUT-id must move *after*
`COLLECT_PIT_DATA_INITIAL` (step 25) for the mutant-oracle path.

**Two-phase MUT-id:**
1. **Static MUT-id** (step 13, unchanged) — runs first, produces a candidate
   MUT for all tests. Tests where it succeeds may proceed without waiting for
   PIT; tests where it fails are deferred.
2. **Dynamic MUT-id** (new, after step 25) — for tests where static MUT-id
   failed AND PIT data exists, resolve the focal method from killed mutants
   (primary) or coverage TFIDF (secondary). Feed the resolved MUT back into
   the assertion rows so JPF instrumentation (step 16) can proceed.

This does not add a new pipeline stage — it adds a *refinement pass* after PIT
that backfills MUT-id for previously-failed assertions. The assertion rows are
updated in-place; no schema change is needed beyond using the existing
`tested_method_*` columns.

### Oracle scoring (per test)

For a test $t$ that failed static MUT-id and has PIT data:

1. **Killed-mutant oracle (primary):** collect the set of
   `(mutated_class, mutated_method)` pairs where `is_detected=true` and
   `killing_test_id = t`. Score each method by the number of killed mutants.
   If exactly one method (59% of cases) — that's the focal method. If
   multiple — pick the method with the most kills, breaking ties by:
   (a) name-match with the test method, (b) call depth (shallowest wins), (c)
   first alphabetical.

2. **Coverage TFIDF (secondary):** for tests with coverage but no kills,
   compute the TFIDF score (TCTracer §4.1.5) for each covered method: methods
   called *uniquely* by this test (not by other tests in the same class) get
   higher scores. Pick the top-scoring method.

3. **Static fallback (tertiary):** if no PIT data exists (category C), fall
   back to the current static analysis + Ghafari mutator/inspector for
   stateful objects + name-matching.

### Validation

Before relying on the oracle, validate against the 5,100 AGREE cases: confirm
that the mutant-oracle picks the same method as static MUT-id where they agree,
and that the 9,354 DISAGREE cases are genuine LCBA flaws (not oracle errors).
A manual sample of ~50 disagreements should confirm the mutant oracle is
correct.

## Acceptance criteria

- [ ] The mutation-based oracle resolves a focal method for ≥35% of the
  MissingValue-failed tests (the killed-mutant bucket), with ≤5% false
  positives verified by manual sampling.
- [ ] The coverage-TFIDF fallback resolves a focal method for ≥15% of the
  MissingValue-failed tests (the coverage-but-no-kills bucket).
- [ ] Static fallback (Ghafari + name-matching) is designed but may be a
  follow-up spec; the mutation-based path must work independently.
- [ ] The pipeline does not add a new execution stage — only a refinement pass
  after step 25 that backfills `tested_method_*` columns on assertion rows.
- [ ] Re-run `applicability_priorities.py` after implementation to confirm
  MissingValue first-reject count drops by ≥35%.
- [ ] Re-run against the controlled `postgres_dev` dataset to confirm no
  regressions on tests that currently succeed.

## Research context

- **Ghafari et al. (SCAM'15):** static mutator/inspector classification, 85%+
  accuracy on 4 small projects. Only works on stateful objects. No public
  implementation. Numbers unverified (paper paywalled).
- **TCTracer (White & Krinke, EMSE'22):** dynamic+static ensemble, 79%
  precision / 83% recall / 85% MAP at method level on 5 large projects. Open
  source (`github.com/RRGWhite/tctracer`). Requires test execution with a
  `-javaagent` tracing agent. TCTracer explicitly states Ghafari's results
  "cannot be directly compared" due to different subject sizes.
- **Methods2Test (Tufano et al., MSR'22):** heuristic dataset (name-strip +
  unique-call), open source (`github.com/microsoft/methods2test`). Subset of
  what Teralizer already does.
- **TestLinker (Sun et al., TSE'24):** LLM-based (CodeT5 fine-tuned). Out of
  scope (ML dependency).
- **He et al. (FSE'24):** empirical study confirming MUT-id quality drives
  downstream assertion-generation accuracy. Not a technique, but motivation.

Our approach (mutation-oracle) is distinct from all of the above: it reuses
PIT data that is already collected, requires no extra execution, and provides
a stronger signal than any purely-static approach (the killed-mutant set is a
ground-truth trace of which methods the test actually exercises meaningfully).

## Out of scope

- Ghafari mutator/inspector reimplementation (static fallback — separate spec
  when scheduled).
- TCTracer integration (requires tracing agent — separate pipeline stage).
- PIT failure reduction (category C — fixing why PIT crashes on 104 projects
  is infrastructure work, not MUT-id).
