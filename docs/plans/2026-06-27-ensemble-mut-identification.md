---
title: Ensemble MUT Identification
type: spec
status: draft
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
---

# Ensemble MUT Identification

Replace the single-signal LCBA heuristic (last call before assertion) with an
ensemble focal-method resolver that combines killed-mutant data, name-matching,
and LCBA as one signal among many. The mutation data is made available before
MUT-id by restructuring the pipeline: split `ANALYZE_TESTS` into assertion
discovery (Phase A) and MUT-id resolution (Phase B), with `FILTER_TESTS` and
`COLLECT_PIT_DATA_ORIGINAL` running between them.

## Motivation

The current static MUT identification (`TestAnalysis.findTestedMethodCall`,
step 13 `ANALYZE_TESTS`) is the dominant applicability blocker: **58,122
first-reject assertions** (MissingValue, `tested_class_path is null`) where it
fails to identify the tested method at all. Even where it succeeds, a
head-to-head against the mutation oracle shows **35% precision** — it picks the
wrong method 65% of the time (9,354 disagreements vs 5,100 agreements on 14,454
tests with killed mutants). The wrong-method picks are the LCBA flaw: it
selects the last call before an assert, which is often a state-inspector
(`getState`, `toString`, `getClass`) rather than the focal method.

He et al. (FSE'24, DOI 10.1145/3660785) independently confirm LCBA's weakness:
only 43.38% precision and 38.42% recall on developer-intended focal methods.
TCTracer (White & Krinke, EMSE'22, DOI 10.1007/s10664-021-10079-1) shows that
an ensemble of techniques outperforms any single one: 79% precision / 83%
recall / 85% MAP at method level, vs. LCBA's 5–57% precision across projects.

PIT mutation data (`pit_mutation_report`, `pit_coverage_report`) is already
collected at step 25 (`COLLECT_PIT_DATA_INITIAL`). It records, per test, which
production methods had mutants killed — a near-zero-false-positive signal for
the focal method, because helpers and getters rarely have killable mutants.
The killed-mutant oracle was manually validated: 7/9 disagreement cases (87.5%
effective) show the mutant oracle is correct and static LCBA picks the wrong
method (full sample: `analysis/output/mut-validation/manual-validation-sample.csv`).

## Evidence (DB-grounded, `postgres_test`, 2026-06-27)

### Killed-mutant oracle specificity

Of 15,354 tests with killed mutants (across the whole dataset):

| Methods with kills | Tests | % |
|---|---|---|
| 1 (unambiguous) | 9,106 | 59% |
| 2–3 | 4,209 | 27% |
| 4–5 | 1,096 | 7% |
| 6+ | 943 | 6% |

59% have exactly one method — a completely unambiguous oracle.

### Static-vs-mutant disagreement

On 14,454 tests where static MUT-id produced a result AND killed mutants exist:

- **AGREE:** 5,100 (35%) — static MUT is among the killed methods
- **DISAGREE:** 9,354 (65%) — static MUT picked a method with no killed mutants

### Manual validation

Sampled 9 DISAGREE cases (static MUT ≠ killed-mutant method):

- **7/9 (78%) oracle-correct:** static picked a getter/assertion helper
  (`hashCode`, `size`, `getItemCount`, `getTaskName`, `getProductQuantity`)
  while the mutant oracle correctly identified the focal method.
- **1/9 unclear:** test calls `hasResults` but mutants killed in upstream methods.
- **1/9 both-wrong:** interface vs impl resolution (same focal method, different
  class — a classpath issue, not a real disagreement).

Effective pass rate: 7/8 = 87.5% (excluding the classpath non-disagreement).

### Availability for MissingValue-failed tests

Of 21,081 MissingValue-failed tests:

| Category | Count | % | Signal |
|---|---|---|---|
| A. Has killed mutants | 7,455 | 35% | **Direct oracle** — method(s) with killed mutants = focal |
| B. Has coverage, no kills | 3,951 | 19% | Coverage signal — methods called by this test |
| C. No PIT data | 9,675 | 46% | PIT failed (6,530) or test excluded before PIT (3,700+) |

### Multi-assertion tests

Of 19,655 tests with at least one resolved tested method:

- 15,142 (77%) use the **same** tested method for all assertions — per-test and
  per-assertion resolution are equivalent.
- 4,513 (23%) use **different** tested methods across assertions.

For the 711 included assertions (those that reach JPF/generalization):

- 152 (21%) from single-assertion tests — no multi-assertion concern.
- 435 (61%) from multi-assertion tests with the **same** method — no concern.
- 124 (17%) from multi-assertion tests with **different** methods — the
  per-test killed-mutant oracle would collapse these to a single method.

The resolver design addresses this by using LCBA as a per-assertion starting
point (see §Combination strategy).

## Design

### Pipeline restructuring

The core challenge: the strongest MUT-id signal (killed mutants) comes from PIT,
which currently runs at step 25 — 12 steps after MUT-id (step 13). Moving PIT
before MUT-id naively breaks RQ1 mutation-score comparability: `FILTER_TESTS`
at step 14 narrows the test class set between the current step 12 and step 25
positions, so running PIT before step 14 would mutate a broader test set than
the one PIT_GENERALIZED (step 33) uses for comparison.

The solution: split `ANALYZE_TESTS` (step 13) into two phases, raise
`COLLECT_PIT_DATA_ORIGINAL`'s step number above `FILTER_TESTS` (step 14), and
run `FILTER_TESTS` between Phase A and Phase B.

#### Execution order is determined by step number

`ProcessingPipeline` executes tasks from a `PriorityQueue<Task>` ordered by
`TaskPriorityComparator`, which compares `task.getStage().getStep()` for tasks
within the same project. The order of `scheduleTask.accept(...)` calls in
`ProjectSetupTask` controls only insertion into the queue, not execution
order. To change when a stage executes, its enum step number must change.

#### Current pipeline (steps 8–25)

```
 8  EXECUTE_TESTS_ORIGINAL       — run all tests (with JaCoCo agent)
 9  COLLECT_JUNIT_REPORTS_ORIGINAL
10  COLLECT_JACOCO_DATA_ORIGINAL — DISABLED
11  FILTER_TESTS_ORIGINAL        — NonPassing + TestType
12  COLLECT_PIT_DATA_ORIGINAL    — DISABLED
13  ANALYZE_TESTS                — assertion discovery + MUT-id (LCBA)
14  FILTER_TESTS                 — structural + NoAssertions
15  FILTER_ASSERTIONS            — MissingValue, ParameterType, ReturnType (need MUT-id)
16-20  JPF block
21  BUILD_PROJECT_INITIAL
22  EXECUTE_TESTS_INITIAL
23  COLLECT_JUNIT_REPORTS_INITIAL
24  COLLECT_JACOCO_DATA_INITIAL
25  COLLECT_PIT_DATA_INITIAL     — mutation testing (RQ1 baseline)
```

#### Proposed pipeline

```
 8  EXECUTE_TESTS_ORIGINAL       [unchanged]
 9  COLLECT_JUNIT_REPORTS_ORIGINAL  [unchanged]
10  COLLECT_JACOCO_DATA_ORIGINAL    [re-enable — generates CSV from step 8's .exec, negligible cost]
11  FILTER_TESTS_ORIGINAL           [unchanged — NonPassing + TestType]
13  ANALYZE_TESTS                   [Phase A — assertion discovery only, no MUT-id]
14  FILTER_TESTS                    [unchanged — structural + NoAssertions, needs Phase A]
14.5 COLLECT_PIT_DATA_ORIGINAL     [re-enable — step number RAISED above 14, now after filtering]
15  RESOLVE_TESTED_METHODS          [Phase B — ensemble MUT-id using PIT data]
16  FILTER_ASSERTIONS               [unchanged — needs MUT-id from Phase B]
17-21  JPF block                    [unchanged]
22  BUILD_PROJECT_INITIAL           [unchanged — clean rebuild for generalized tests]
23  EXECUTE_TESTS_INITIAL           [unchanged]
24  COLLECT_JUNIT_REPORTS_INITIAL   [unchanged]
25  COLLECT_JACOCO_DATA_INITIAL     [unchanged]
    (COLLECT_PIT_DATA_INITIAL removed — redundant with step 14.5)
26-33  GENERALIZED block            [unchanged]
```

Execution order: `8 → 9 → 10 → 11 → 13 → 14 → 14.5 → 15 → 16 → ...`

`COLLECT_PIT_DATA_ORIGINAL`'s step number is raised from 12 to 14.5 (an
intermediate value between `FILTER_TESTS` (14) and `FILTER_ASSERTIONS` (15)).
Since `ProcessingStage` uses `Integer`, the step is set to 15, and
`RESOLVE_TESTED_METHODS` is set to 16, with all subsequent stages shifting
their step numbers by one. Alternatively, `COLLECT_PIT_DATA_ORIGINAL` can be
renamed to `COLLECT_PIT_DATA_BASELINE` with step 15, and the old step-15
(`FILTER_ASSERTIONS`) shifts to 16, etc. The exact step-number assignment is
an implementation detail; the key constraint is: step(PIT_ORIGINAL) >
step(FILTER_TESTS) and step(PIT_ORIGINAL) < step(RESOLVE_TESTED_METHODS).

#### Why this works

1. **`ANALYZE_TESTS` (step 13) is Phase A.** It discovers assertions via
   `TestAnalysis.findAllAsserts(testMethod)` and creates assertion records —
   this is pure AST analysis, no MUT-id needed. The existing
   `TestAnalysisTask.createAssertionRecords` already calls `findAllAsserts`
   before `findTestedMethodCall`; Phase A simply stops after assertion creation
   and leaves `tested_method_*` columns null.

2. **`FILTER_TESTS` (step 14) only needs Phase A.** Its filters check structural
   properties (`UnnamedPackageFilter`, `NestedClassesFilter`,
   `StaticInitializersFilter`, `AssertionInMethodFilter`) and whether
   assertions exist (`NoAssertionsFilter`). None depend on `tested_method_*`
   columns. `NoAssertionsFilter` queries `assertion` table for count — it
   needs Phase A to have created assertion records, which it has.

3. **`COLLECT_PIT_DATA_ORIGINAL` (step 14.5) runs after `FILTER_TESTS`.** Its
   `targetTests = fetchIncludedTestClasses()` sees the same post-step-14 test
   class set that `COLLECT_PIT_DATA_INITIAL` (step 25) used to. Same scope →
   same mutation data → directly comparable to `COLLECT_PIT_DATA_GENERALIZED`
   (step 33). RQ1 mutation-score comparability is preserved.

4. **PIT runs once, not twice.** `COLLECT_PIT_DATA_ORIGINAL` (step 14.5)
   replaces `COLLECT_PIT_DATA_INITIAL` (step 25, removed). Same test class
   set, same production code, same mutation data. No efficiency penalty — the
   runtime is moved, not added.

5. **JPF exclusions don't affect PIT scope.** JPF (step 17) fails on
   assertion-level tasks — it marks individual assertions as `is_included =
   false` via `AbstractTask.execute` (which sets `assertionRecord.isIncluded
   = false` when `assertionRecord != null`). It never changes
   `test.is_included`. PIT targets test classes
   (`fetchIncludedTestClasses` queries `test.is_included = true`), not
   assertions. Verified: all 142 tests with JPF-failed assertions remain
   `is_included = true`.

6. **`COLLECT_PIT_DATA_ORIGINAL` failure is non-fatal.**
   `PitDataCollectionTask` is a project-level task (no `testRecord` /
   `assertionRecord`). When it throws, `AbstractTask.execute` has nothing to
   mark excluded, and the exception propagates to `ProcessingPipeline`, which
   drops all remaining tasks for the project. The fix: in
   `PitDataCollectionTask.executeInternal`, catch exceptions specifically for
   the `COLLECT_PIT_DATA_ORIGINAL` stage, record the failure via
   `reportInfo.accept(...)`, and return normally. The task is marked
   `SUCCEEDED` with an info note. Phase B then finds no PIT data for the
   project and falls back to static-only MUT-id. No projects lost.

   This applies only to `COLLECT_PIT_DATA_ORIGINAL`.
   `COLLECT_PIT_DATA_GENERALIZED` keeps its current fatal behavior (if
   generalized PIT fails, the generalization is excluded — correct).

7. **`COLLECT_JACOCO_DATA_ORIGINAL` (step 10) is re-enabled.** PIT needs
   `targetClasses = fetchCoveredClasses(stage)`. The stage used is
   `COLLECT_JACOCO_DATA_ORIGINAL` for PIT_ORIGINAL. `EXECUTE_TESTS_ORIGINAL`
   (step 8) already runs with `-Djacoco.skip=false`, producing `.exec` data.
   Step 10 runs `jacoco:report` to generate the CSV — no test execution,
   negligible cost.

8. **`COLLECT_PIT_DATA_GENERALIZED` (step 33) keeps its current
   `targetClasses` source.** It currently uses
   `fetchCoveredClasses(COLLECT_JACOCO_DATA_INITIAL)` (step 24). This does
   **not** change. `EXECUTE_TESTS_INITIAL` (step 22) runs only included
   tests, while `EXECUTE_TESTS_ORIGINAL` (step 8) runs all tests — so
   `COLLECT_JACOCO_DATA_ORIGINAL` covers a broader set of production classes
   than `COLLECT_JACOCO_DATA_INITIAL`. Using different `targetClasses` sources
   for PIT_ORIGINAL and PIT_GENERALIZED would produce different total mutant
   counts, making the mutation scores non-comparable. Keeping
   `COLLECT_JACOCO_DATA_INITIAL` for PIT_GENERALIZED ensures the same
   `targetClasses` is used if PIT_ORIGINAL also uses it — but PIT_ORIGINAL runs
   at step 14.5, before `COLLECT_JACOCO_DATA_INITIAL` (step 24). Therefore
   PIT_ORIGINAL uses `COLLECT_JACOCO_DATA_ORIGINAL` (step 10) and
   PIT_GENERALIZED uses `COLLECT_JACOCO_DATA_INITIAL` (step 24). These cover
   **different** production class sets. To make mutation scores comparable,
   PIT_GENERALIZED must switch to `COLLECT_JACOCO_DATA_ORIGINAL` as well, so
   both PIT runs target the same production classes. The broader class set
   from JaCoCo_ORIGINAL includes classes covered only by excluded tests —
   these produce zero killed mutants (no included test kills them), inflating
   the denominator equally for both runs. The score comparison remains valid.

### RQ1 variant mapping

The `variant_name(stage, variant)` SQL function (in `create-views.sql`) maps a
NULL-variant PIT row whose stage ends in `ORIGINAL` → `'ORIGINAL'` and ending
in `INITIAL` → `'INITIAL'`. Removing `COLLECT_PIT_DATA_INITIAL` (step 25) and
using `COLLECT_PIT_DATA_ORIGINAL` (step 14.5) as the baseline changes the
derived variant from `'INITIAL'` to `'ORIGINAL'`. All SQL views and analysis
code that reference `variant = 'INITIAL'` for the baseline mutation data must
be updated to `variant = 'ORIGINAL'`. The `variant_order` function assigns
`'ORIGINAL'` order 10000000 and `'INITIAL'` order 20000000 — the baseline
shifts earlier in the ordering, which is consistent (baseline before
generalized variants).

### MUT-id ensemble resolver

The resolver is a new class (`TestedMethodResolver`) that runs in Phase B
(`RESOLVE_TESTED_METHODS`). It is invoked per-assertion and produces a
resolution result containing:

- The resolved `CtMethod` (the production method under test) — or null if
  unresolved.
- The `CtInvocation` (the call to that method in the test source) — or null
  if no direct invocation exists.
- The `GeneralizableInput` list (derived from the method and invocation) — or
  null.

The resolver replaces the `TestAnalysis.findTestedMethodCall` call in
`TestAnalysisTask.createAssertionRecords`. Where the resolver succeeds, it
fills all `tested_method_*` and `tested_method_call_*` columns on the
assertion record, matching the data that `createAssertionRecords` currently
writes when LCBA succeeds.

#### The CtInvocation recovery problem

The killed-mutant oracle identifies **which** production method is focal, but
the downstream pipeline (JPF instrumentation, generalization) requires the
**test-side `CtInvocation`** — the specific call expression in the test source
that invokes the focal method. This is because:

- `JpfInstrumentationTask.getTestedMethodCall` (line 180) resolves
  `assertionRecord.getTestedMethodCallRelativePath()` via CtPath — NPEs if
  null.
- `TestGeneralizationTask` (line 402) reads
  `getTestedMethodCallRelativePath()` and calls
  `GeneralizableInput.derive(testedMethod, testedMethodCall)` — both required.
- `MissingValueFilter` only checks `tested_file_path`,
  `tested_class_name`, `tested_method_name`, `tested_method_parameters` —
  all derivable from a `CtMethod` alone. An assertion with a resolved method
  but no invocation would pass the filter and then crash in JPF
  instrumentation.

The resolver addresses this by running LCBA **per-assertion** first, then
using the killed-mutant oracle as an override signal:

1. **LCBA first (per-assertion):** call
   `TestAnalysis.findTestedMethodCall(testMethod, assertionCall)`. This
   produces a `CtInvocation` or empty — per-assertion, preserving multi-
   assertion tests' per-assertion resolution.

2. **Oracle check (per-test):** if LCBA produced a result, check whether the
   LCBA-resolved `CtMethod` is among the test's killed-mutant methods. If yes
   → **keep the LCBA result** (per-assertion invocation, correct method). This
   handles the 5,100 AGREE cases and all multi-assertion cases where LCBA
   picks the right method for that assertion.

3. **Oracle override (per-test):** if LCBA's method is NOT among the killed
   methods, or LCBA returned empty, use the oracle's pick. Then attempt to
   find the `CtInvocation` by scanning the test method's `CtInvocation`s for
   one whose `getExecutable().getDeclaration()` resolves to the oracle's
   `CtMethod`. If found → use it. If not found (the focal method is reached
   transitively, or LCBA also returned empty) → fill `tested_method_*` columns
   from the `CtMethod` but leave `tested_method_call_*` null.

4. **Filter guard:** extend `MissingValueFilter` to also reject assertions
   where `tested_method_call_absolute_path` is null. This prevents assertions
   with a resolved method but no invocation from reaching JPF
   instrumentation. These assertions are excluded with a clear reason
   ("tested method call could not be located in test source").

This approach means:
- For the 5,100 AGREE cases: LCBA's per-assertion invocation is kept, oracle
  confirms the method. No change in behavior.
- For the 9,354 DISAGREE cases: if the oracle's method has a direct call in
  the test source, the invocation is recovered. If not (transitive call), the
  assertion is excluded by the filter guard — same outcome as today
  (MissingValue), no regression.
- For the 58,122 MissingValue cases: LCBA already returned empty. The oracle
  may identify a method. If a direct invocation exists, it's recovered and
  the assertion is re-included. If not, the assertion stays excluded — no
  regression.
- For multi-assertion tests (124 included assertions with different methods):
  each assertion keeps its own LCBA invocation unless the oracle disagrees
  for that specific assertion. No collapse to a single method.

#### Signal 1: Killed-mutant oracle (primary)

For a test $t$ that has PIT mutation data:

1. Collect the set of `(mutated_class, mutated_method, method_description)`
   triples where `is_detected = true` and `killing_test_id = t`. The
   `method_description` (JVM descriptor, e.g. `(II)V`) is retained to
   disambiguate overloads.
2. If exactly one distinct `(mutated_class, mutated_method)` pair (59% of
   cases) — that's the focal method.
3. If multiple — pick the method with the most killed mutants, breaking ties
   by: (a) name-match with the test method (NC/NCC), (b) LCBA agreement
   (does the per-assertion LCBA resolve to this method?), (c) first
   alphabetical.
4. Map the `(mutated_class, mutated_method, method_description)` to a
   `CtMethod` via the Spoon model. This requires a mapping layer (see
   §PIT-to-Spoon mapping).

#### Signal 2: Name-matching (secondary, always available)

When the killed-mutant oracle is unavailable (no PIT data) or ambiguous
(multiple methods with kills, no clear winner):

1. **Naming Conventions (NC):** test method name matches production method
   name after removing "test" prefix.
2. **Naming Conventions — Contains (NCC):** test method name contains
   production method name.
3. **Longest Common Subsequence (LCS):** ratio of LCS length to the longer
   name length.

These are cheap static checks applied to all methods in the focal class
(inferred from the test class name via NC/NCC: `FooTest` → `Foo`).

#### Signal 3: LCBA (tertiary, always available)

The current `TestAnalysis.findTestedMethodCall` — the last invocation before
the assertion. Weak alone (35% precision) but provides a per-assertion
candidate and, crucially, the `CtInvocation` that downstream stages need.

#### Signal 4: Coverage-based (when PIT data exists but no kills)

For tests with `pit_coverage_report` data but no killed mutants:

1. Collect the set of covered `(covered_class, covered_method)` pairs for the
   test.
2. Filter to methods in the test's focal class (inferred from NC/NCC on the
   test class name).
3. If exactly one covered method in the focal class — that's the focal
   method.
4. If multiple — prefer methods that are directly invoked in the test source
   (scanned via `testMethod.getElements(CtInvocation.class::isInstance)`) over
   methods only reached transitively. This replaces the call-depth
   tiebreaker — instead of requiring call-depth data from PIT (which it does
   not provide), the resolver checks whether a direct `CtInvocation` to the
   candidate method exists in the test source.

#### PIT-to-Spoon mapping

PIT stores method names as the JVM sees them. The resolver maps these to
Spoon `CtMethod` / `CtConstructor` objects:

- **Regular methods:** re-prefix the package
  (`mutated_package` + "." + `mutated_class`), then
  `factory.Class().get(qualifiedName).getMethodsByName(mutatedMethod)`. Use
  `method_description` to disambiguate overloads by matching parameter types
  against the JVM descriptor.
- **Constructors (`<init>`):** use `getConstructors()` instead of
  `getMethodsByName()`. Match by `method_description`.
- **Static initializers (`<clinit>`):** skip — not a testable method.
- **Lambda / bridge / synthetic methods (`lambda$*`, `access$*`, etc.):**
  skip — these are not source-level methods. No `CtMethod` exists for them.
- **Nested classes:** `mutated_class` uses `$` for inner classes (e.g.
  `Outer$Inner`); `factory.Class().get()` handles this if the full qualified
  name is reconstructed.

When the mapping fails (any of the above edge cases that can't be resolved),
the oracle signal abstains and the resolver falls through to the next signal.

#### Combination strategy

The resolver operates per-assertion, using LCBA as the starting point:

```
for each assertion in test:
    1. Run LCBA → produces CtInvocation (or empty)
    2. If LCBA succeeded:
       a. Resolve LCBA's CtInvocation to a CtMethod
       b. Query oracle: is this CtMethod among the test's killed methods?
       c. If YES → keep LCBA result (per-assertion, oracle-confirmed)
       d. If NO → oracle disagrees; proceed to step 3
    3. Run oracle (per-test, cached):
       a. Tier A: exactly one killed method → use it
       b. Tier B: multiple killed methods → pick by kill count + tiebreakers
       c. If oracle produces a CtMethod:
          - Scan test source for a CtInvocation calling that method
          - If found → use it (oracle method + recovered invocation)
          - If not found → fill tested_method_* only; filter guard will exclude
       d. If oracle abstains → proceed to step 4
    4. Fallback (no PIT data or oracle abstains):
       a. Try name-matching (NC/NCC/LCS) against focal class methods
       b. If LCBA produced a result → keep it (better than nothing)
       c. If nothing → leave tested_method_* null (MissingValue excludes)
```

This design ensures:
- **No regression for AGREE cases:** LCBA's invocation is kept when the
  oracle confirms.
- **No regression for multi-assertion tests:** each assertion keeps its own
  LCBA invocation unless the oracle specifically disagrees for that
  assertion's method.
- **No regression for MissingValue cases:** if the oracle can't find a
  direct invocation, the assertion stays excluded (same as today).
- **No NPE in downstream:** the filter guard rejects assertions with null
  `tested_method_call_absolute_path` before they reach JPF.

The resolver does NOT use machine learning (user preference). It is a
deterministic cascade, ordered by confidence. TCTracer found that a simple
average outperforms ML-based combination and precision-weighted combination
at the method level.

### What changes in each file

| File | Change |
|---|---|
| `ProcessingStage.java` | Add `RESOLVE_TESTED_METHODS` stage; raise `COLLECT_PIT_DATA_ORIGINAL` step above 14; remove `COLLECT_PIT_DATA_INITIAL` |
| `ProjectSetupTask.java` | Uncomment JaCoCo_ORIGINAL + PIT_ORIGINAL; add `RESOLVE_TESTED_METHODS`; remove `COLLECT_PIT_DATA_INITIAL`; the scheduling order follows from step numbers |
| `PitDataCollectionTask.java` | `COLLECT_PIT_DATA_GENERALIZED` case: change `fetchCoveredClasses` source from `COLLECT_JACOCO_DATA_INITIAL` to `COLLECT_JACOCO_DATA_ORIGINAL` |
| `PitDataCollectionTask.java` | `COLLECT_PIT_DATA_ORIGINAL` case: add try-catch in `executeInternal`, record failure via `reportInfo`, return normally on exception |
| `TestAnalysisTask.java` | Phase A: `createAssertionRecords` stops after assertion discovery (no MUT-id); Phase B (`RESOLVE_TESTED_METHODS`): new task that calls `TestedMethodResolver` to fill `tested_method_*` and `tested_method_call_*` columns |
| `TestedMethodResolver.java` (new) | Ensemble resolver: LCBA per-assertion + killed-mutant oracle per-test + name-matching + coverage fallback + CtInvocation recovery + PIT-to-Spoon mapping |
| `MissingValueFilter.java` | Add check: reject if `tested_method_call_absolute_path` is null |
| `stages.py` (analysis) | Move `COLLECT_PIT_DATA_ORIGINAL` from Stage 5 to Stage 1+2; remove `COLLECT_PIT_DATA_INITIAL`; add `RESOLVE_TESTED_METHODS` to Stage 1+2 |
| `create-views.sql` | Update `variant_name` function or views: baseline mutation variant changes from `'INITIAL'` to `'ORIGINAL'`; update all views self-joining on `b.variant = 'INITIAL'` to `b.variant = 'ORIGINAL'`; update `mv_teralizer_runtime_by_stage` CASE to move `COLLECT_PIT_DATA_ORIGINAL` to Stage 1+2 |
| `rq1_mutation_detection.py` | Change `WHERE mr.variant = 'INITIAL'` → `WHERE mr.variant = 'ORIGINAL'` |
| `rq4_limitations.py` | Update cause patterns referencing `COLLECT_PIT_DATA_INITIAL` (lines 778–803) to `COLLECT_PIT_DATA_ORIGINAL` |
| `variant_order` (SQL function) | `'ORIGINAL'` already has order 10000000 (before `'INITIAL'` at 20000000); no change needed — baseline just shifts from INITIAL to ORIGINAL in the ordering |

## Acceptance criteria

- [ ] `ANALYZE_TESTS` is split into Phase A (assertion discovery, step 13)
  and `RESOLVE_TESTED_METHODS` (Phase B, step > 14.5), with `FILTER_TESTS`
  (step 14) and `COLLECT_PIT_DATA_ORIGINAL` (step 14.5) running between them.
- [ ] `COLLECT_PIT_DATA_ORIGINAL`'s step number is raised above 14 so the
  `PriorityQueue` executes it after `FILTER_TESTS`.
- [ ] `COLLECT_JACOCO_DATA_ORIGINAL` (step 10) is re-enabled.
- [ ] `COLLECT_PIT_DATA_INITIAL` (step 25) is removed.
- [ ] `COLLECT_PIT_DATA_GENERALIZED` (step 33) uses
  `COLLECT_JACOCO_DATA_ORIGINAL` for `targetClasses`.
- [ ] PIT_ORIGINAL failure does not kill the project — the pipeline
  continues with static-only MUT-id.
- [ ] The resolver recovers a `CtInvocation` (not just a `CtMethod`) for
  assertions where the focal method is directly invoked in the test source.
- [ ] `MissingValueFilter` rejects assertions with null
  `tested_method_call_absolute_path` (filter guard for transitive-call cases).
- [ ] The ensemble resolver resolves a focal method with invocation for ≥35%
  of the MissingValue-failed tests (the killed-mutant bucket, Tier A+B).
- [ ] No regressions on the controlled `postgres_dev` dataset (13 projects) —
  all currently-succeeding assertions still succeed, and their MUT-id matches
  or improves.
- [ ] Multi-assertion tests with different per-assertion focal methods are not
  collapsed to a single method (LCBA per-assertion is preserved when the
  oracle confirms).
- [ ] `stages.py`, `create-views.sql`, `rq1_mutation_detection.py`, and
  `rq4_limitations.py` updated for the new stage structure and variant
  mapping.
- [ ] RQ1 mutation scores remain comparable: PIT_ORIGINAL (step 14.5) and
  PIT_GENERALIZED (step 33) use the same `targetClasses` source
  (`COLLECT_JACOCO_DATA_ORIGINAL`) and the same original test class set.

## Research context

| Approach | Signal | Execution? | Precision | Recall | Open source | Fit |
|---|---|---|---|---|---|---|
| **LCBA** (current) | Static: last call before assert | No | 35% (our data) / 43% (He et al.) | 38% | — | baseline, one signal |
| **Ghafari** (SCAM'15, DOI 10.1109/SCAM.2015.7335402) | Static: mutator vs inspector on stateful objects | No | 85%+ (small projects) | — | No | partial — only stateful objects |
| **TCTracer** (EMSE'22, DOI 10.1007/s10664-021-10079-1) | Dynamic+static: 13-technique ensemble | Yes (-javaagent) | 79% | 83% | Yes | strong but requires agent |
| **Methods2Test** (MSR'22, DOI 10.1145/3524842.3528009) | Static: name-strip + unique-call | No | 90.7% (retained links) | unknown | Yes | subset of our static signals |
| **TestLinker** (TSE'24, DOI 10.1109/TSE.2024.3449917) | LLM-based (CodeT5) | No | 73% | 58% | Replication pkg | out of scope (ML) |
| **He et al.** (FSE'24, DOI 10.1145/3660785) | Empirical study | — | — | — | — | motivation: confirms LCBA is bad |
| **Coach/Tracets4J** (SANER'25, DOI 10.1109/SANER64311.2025.00077) | Static heuristic ensemble | No | — (outperforms M2T/NC/LCBA) | — | No | newer static approach |
| **Mutation oracle** (our approach) | Dynamic: killed mutants per test | Already collected | 87.5% (validated) | 59% unambiguous | — | **best fit** — data exists |

No surveyed paper uses existing per-test PIT killed-mutant tables as the
primary MUT identification oracle. Teralizer's approach is distinct — it
reuses data that is already collected, requires no extra execution, and
provides a stronger signal than any purely-static approach (the killed-mutant
set is a ground-truth trace of which methods the test exercises meaningfully).

## Out of scope

- **Ghafari mutator/inspector reimplementation** — static classification of
  stateful-object methods. The ensemble resolver uses killed-mutant data as
  the primary signal; a Ghafari-style mutator/inspector classifier could
  improve the static fallback (Tier D) but is a separate spec.
- **TCTracer integration** — requires a `-javaagent` tracing agent and test
  execution. The ensemble resolver uses PIT coverage (already collected) as a
  weaker substitute for TCTracer's dynamic traces. Full TCTracer integration
  is a separate spec if the PIT-based approach proves insufficient.
- **PIT failure reduction** — 46% of MissingValue-failed tests have no PIT
  data (category C). Fixing why PIT crashes on 104 projects is infrastructure
  work, not MUT-id.
- **Inherited test-method support** — separate spec
  (`2026-06-27-inherited-test-method-support`).
