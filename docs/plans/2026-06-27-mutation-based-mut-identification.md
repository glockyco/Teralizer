---
title: Mutation-Based MUT Identification
type: spec
status: active
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

### Pipeline ordering (corrected)

MUT-id runs at step 13 (`ANALYZE_TESTS`), before PIT at step 25
(`COLLECT_PIT_DATA_INITIAL`). The mutation-based oracle requires PIT data.

**Key insight:** MissingValue is an **assertion-level** filter (step 15), not a
test-level exclusion. Tests with MissingValue-failed assertions remain
`is_included = true` at the test level, so PIT runs on them at step 25 regardless.
All 21,081 MissingValue-failed tests have `is_included = true` — PIT data
exists for 11,406 (54%) of them.

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

**Important:** this only helps the 54% of MissingValue-failed tests that have
PIT data. The 46% with no PIT data (projects where PIT failed or tests excluded
before PIT) need the static fallback. Reducing PIT failures and pre-PIT test
exclusions is a separate concern (see §Pipeline failure landscape below).

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

### Validation (manual — completed 2026-06-27)

A manual validation study confirmed the approach is sound. Sampled 9 DISAGREE
cases (static MUT ≠ killed-mutant method) from the 9,354 disagreements.

**Results:**
- **7/9 (78%) oracle-correct:** static picked a getter/assertion helper
  (`hashCode`, `size`, `getItemCount`, `getTaskName`, `getProductQuantity`)
  while the mutant oracle correctly identified the focal method.
- **1/9 unclear:** test calls `hasResults` but mutants killed in upstream
  methods.
- **1/9 both-wrong:** interface vs impl resolution (same focal method,
  different class — a classpath issue, not a real disagreement).

**Effective pass rate: 7/8 = 87.5%** (excluding the classpath-resolution
non-disagreement). The disagreements are overwhelmingly the LCBA flaw: static
picks the last call before assert (a getter/state-inspector) while the mutant
oracle identifies the method that does the work being tested.

Full sample: `analysis/output/mut-validation/manual-validation-sample.csv`.

## Pipeline failure landscape

The MUT-id spec only addresses the 54% of MissingValue-failed tests that have
PIT data. The other 46% fail because the pipeline never reaches PIT, or PIT
itself fails. This is a separate, larger problem — the pre-analysis funnel.

Re-run with:
```python
from teralizer.applicability_priorities import (
    get_pipeline_failure_funnel, compute_stage_failure_summary
)
with db_config.get_test_engine().connect() as conn:
    funnel = get_pipeline_failure_funnel(conn)
    print(compute_stage_failure_summary(funnel))
```

The funnel (1161 projects, first-failure stage):

| Stage | Step | Projects killed | % | Root cause |
|---|---|---|---|---|
| SETUP_PROJECT | 2 | 355 | 30.9% | Maven setup failures (dependency resolution, non-standard layouts) |
| BUILD_PROJECT_ORIGINAL | 4 | 189 | 16.5% | Compilation errors (`mvn compile test-compile` exit 1) |
| COLLECT_JUNIT_REPORTS_ORIGINAL | 9 | 88 | 7.7% | Cannot identify test report path (non-standard report locations) |
| EXECUTE_TESTS_ORIGINAL | 8 | 61 | 5.3% | Test execution timeout (48 of 61) |
| EXECUTE_TESTS_INITIAL | 22 | 116 | 10.1% | "All tests excluded" — test-level filters reject everything (NoAssertions, NonPassingTest) |
| COLLECT_PIT_DATA_GENERALIZED | 33 | 219 | 19.1% | Mutation testing on generalized tests fails (compilation or runtime) |
| COLLECT_PIT_DATA_INITIAL | 25 | 40 | 3.5% | PIT timeout (40 of 40) |
| EXECUTE_JPF | 18 | 29 | 2.5% | JPF execution failures |
| COLLECT_JACOCO_DATA_INITIAL | 24 | 34 | 3.0% | JaCoCo collection failures |

**Key observations:**

- **47.4% of projects (549/1161) fail before analysis** (SETUP + BUILD +
  EXECUTE_TESTS + COLLECT_JUNIT). These never reach MUT identification at all.
  The biggest single killer is SETUP_PROJECT (30.9%) — Maven dependency
  resolution and non-standard project layouts.

- **10.1% (116 projects) reach EXECUTE_TESTS_INITIAL but fail with "all tests
  excluded"** — test-level filters (NoAssertions, NonPassingTest, TestType)
  reject every test in the project. This is the same filter-based exclusion
  that blocks assertions, but at the test level. Fixing NoAssertions
  (interprocedural assertions, #4) and TestType (@ParameterizedTest, #9) would
  unblock projects here.

- **19.1% (219 projects) fail at COLLECT_PIT_DATA_GENERALIZED** — mutation
  testing on the *generalized* tests fails. This is downstream of the
  generalization step and doesn't affect the original-suite PIT data used by
  the mutation-based MUT oracle.

- **Only 13 projects (1.1%) complete the pipeline with no failures at all.**

**What this means for the MUT-id spec:** the mutation-based oracle can only
help projects that reach step 25 (`COLLECT_PIT_DATA_INITIAL`) with data. The
pre-analysis failures (SETUP, BUILD, EXECUTE_TESTS) are infrastructure
problems that need separate fixes — the applicability-barriers audit (#14:
Maven/structure-only, project-setup detection) covers these. The test-level
exclusion failures (EXECUTE_TESTS_INITIAL "all tests excluded") need the same
filter fixes as the assertion-level analysis (#4 interprocedural assertions,
#9 @ParameterizedTest).

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
