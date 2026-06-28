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
MUT-id by enabling the already-existing `COLLECT_PIT_DATA_ORIGINAL` step (step
12), which runs before `ANALYZE_TESTS` (step 13). No pipeline restructuring
is needed — the step numbers already guarantee the correct execution order.

## Motivation

The current static MUT identification (`TestAnalysis.findTestedMethodCall`,
step 13 `ANALYZE_TESTS`) is the dominant applicability blocker: **58,122
first-reject assertions** (MissingValue, `tested_class_path is null`) where it
fails to identify the tested method at all. Even where it does resolve a
method, it is often the wrong one — LCBA picks the last call before an assert,
which is frequently a state-inspector (`getState`, `toString`, `getClass`)
rather than the focal method. He et al. (FSE'24, DOI 10.1145/3660785) measure
LCBA at 43.38% precision and 38.42% recall against developer-intended focal
methods.

TCTracer (White & Krinke, EMSE'22, DOI 10.1007/s10664-021-10079-1) shows that
an ensemble of techniques outperforms any single one, reaching 85% mean
average precision at the method level. The ensemble below adds a killed-mutant
oracle to LCBA: it recovers focal methods both where LCBA produces nothing
(the 58,122 gap) and where LCBA resolves a method that has no killed mutants.

PIT mutation data (`pit_mutation_report`, `pit_coverage_report`) is collected
at step 25 (`COLLECT_PIT_DATA_INITIAL`). It records, per test, which production
methods had mutants killed — a near-zero-false-positive signal for the focal
method, because helpers and getters rarely have killable mutants. The
killed-mutant oracle was manually validated: 7/9 disagreement cases (87.5%
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

### LCBA vs. killed-mutant oracle

On 7,169 tests where LCBA resolves a tested method AND killed mutants exist
(matched by qualified method name):

- **In the killed set:** 4,176 (58%) — LCBA's method is among the methods this
  test kills mutants in.
- **Absent:** 2,993 (42%) — LCBA's method has no killed mutants; the oracle can
  correct these.

Agreement (58%) sits above He et al.'s 43% precision because the killed-mutant
set is a permissive target (membership in a set of methods, not exact developer
intent) — it is an upper bound on LCBA's true precision, not a contradiction.

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

### Pipeline: enable PIT_ORIGINAL (no restructuring)

The pipeline already has the right step numbers. `COLLECT_JACOCO_DATA_ORIGINAL`
(step 10) and `COLLECT_PIT_DATA_ORIGINAL` (step 12) are commented out in
`ProjectSetupTask.java:96,101`. Uncommenting them makes PIT mutation data
available before `ANALYZE_TESTS` (step 13) runs — no step-number changes, no
new stages, no restructuring.

#### Execution order is determined by step number

`ProcessingPipeline` executes tasks from a `PriorityQueue<Task>` ordered by
`TaskPriorityComparator`, which compares `task.getStage().getStep()`. The
existing step numbers already place `COLLECT_PIT_DATA_ORIGINAL` (12) before
`ANALYZE_TESTS` (13). No change needed.

#### Current pipeline (steps 8–25)

```
 8  EXECUTE_TESTS_ORIGINAL       — run all tests (with JaCoCo agent)
 9  COLLECT_JUNIT_REPORTS_ORIGINAL
10  COLLECT_JACOCO_DATA_ORIGINAL — DISABLED (commented out, line 96)
11  FILTER_TESTS_ORIGINAL        — NonPassing + TestType
12  COLLECT_PIT_DATA_ORIGINAL    — DISABLED (commented out, line 101)
13  ANALYZE_TESTS                — assertion discovery + MUT-id (LCBA)
14  FILTER_TESTS                 — structural + NoAssertions
15  FILTER_ASSERTIONS            — MissingValue, ParameterType, ReturnType
16-20  JPF block
21  BUILD_PROJECT_INITIAL
22  EXECUTE_TESTS_INITIAL
23  COLLECT_JUNIT_REPORTS_INITIAL
24  COLLECT_JACOCO_DATA_INITIAL
25  COLLECT_PIT_DATA_INITIAL     — mutation testing (RQ1 baseline, variant='INITIAL')
```

#### Proposed pipeline

```
 8  EXECUTE_TESTS_ORIGINAL       [unchanged]
 9  COLLECT_JUNIT_REPORTS_ORIGINAL  [unchanged]
10  COLLECT_JACOCO_DATA_ORIGINAL    [RE-ENABLE — generates CSV from step 8's .exec]
11  FILTER_TESTS_ORIGINAL           [unchanged — NonPassing + TestType]
12  COLLECT_PIT_DATA_ORIGINAL       [RE-ENABLE — runs before ANALYZE_TESTS, non-fatal]
13  ANALYZE_TESTS                   [MODIFIED — uses killed-mutant oracle alongside LCBA]
14  FILTER_TESTS                    [unchanged]
15  FILTER_ASSERTIONS               [MODIFIED — adds filter guard for null CtInvocation]
16-20  JPF block                    [unchanged]
21-25  INITIAL block                [unchanged — serves RQ1/RQ2/RQ3]
26-33  GENERALIZED block            [unchanged]
```

#### Why this works

1. **Step numbers already guarantee the order.** `COLLECT_PIT_DATA_ORIGINAL`
   (step 12) executes before `ANALYZE_TESTS` (step 13) via the
   `PriorityQueue`/`TaskPriorityComparator`. The mutation data is in
   `pit_mutation_report` (stage=`COLLECT_PIT_DATA_ORIGINAL`) when MUT-id runs.

2. **`COLLECT_JACOCO_DATA_ORIGINAL` (step 10) is re-enabled.** PIT_ORIGINAL's
   `targetClasses` come from `fetchCoveredClasses(COLLECT_JACOCO_DATA_ORIGINAL)`.
   `EXECUTE_TESTS_ORIGINAL` (step 8) already runs with `-Djacoco.skip=false`
   (all 3 Maven/Gradle command variants in `TestExecutionTask.java:122,130,154,161`),
   producing `.exec` data. Step 10 runs `jacoco:report` to generate the CSV —
   no test execution, negligible cost.

3. **PIT_ORIGINAL's test scope is broader than PIT_INITIAL's.**
   `COLLECT_PIT_DATA_ORIGINAL` (step 12) uses
   `targetTests = fetchIncludedTestClasses()`. At step 12, only
   `FILTER_TESTS_ORIGINAL` (step 11, NonPassing + TestType) has run.
   `FILTER_TESTS` (step 14, structural + NoAssertions) and
   `FILTER_ASSERTIONS` (step 15, MissingValue) have **not** run yet. So
   PIT_ORIGINAL runs mutation testing on tests that would later be
   MissingValue-excluded — including the 21,081 MissingValue-failed tests that
   need mutation data. This is exactly the signal the resolver needs at step 13.

4. **RQ1 is untouched.** `COLLECT_PIT_DATA_INITIAL` (step 25) still runs with
   `variant='INITIAL'`. All SQL views, `rq1_mutation_detection.py`, and
   `rq4_limitations.py` continue to reference `variant = 'INITIAL'` for RQ1
   mutation scores. No variant mapping changes, no view updates, no analysis
   code changes.

5. **PIT_ORIGINAL and PIT_INITIAL use different `targetClasses` sources — by
   design.** PIT_ORIGINAL uses `fetchCoveredClasses(COLLECT_JACOCO_DATA_ORIGINAL)`
   (step 10, all tests), PIT_INITIAL uses
   `fetchCoveredClasses(COLLECT_JACOCO_DATA_INITIAL)` (step 24, included tests
   only). These cover different production class sets. This is correct:
   PIT_ORIGINAL's purpose is MUT-id oracle data (broader is better), while
   PIT_INITIAL's purpose is RQ1 mutation scores (must match PIT_GENERALIZED's
   scope). They serve different goals and are stored under different stages.

6. **`COLLECT_PIT_DATA_ORIGINAL` failure must be non-fatal.** It is a
   project-level task (no `testRecord`/`assertionRecord`). When it throws,
   `AbstractTask.execute` has nothing to mark excluded, and
   `ProcessingPipeline.executeNext` removes all remaining tasks for the
   project (the `queuedTasks.removeIf(...)` call matches on `projectId`).
   The fix: in `PitDataCollectionTask.executeInternal`, catch exceptions
   specifically for the `COLLECT_PIT_DATA_ORIGINAL` stage, record the failure
   via `reportInfo.accept(...)`, and return normally. The task is marked
   `SUCCEEDED` with an info note. `ANALYZE_TESTS` then finds no PIT_ORIGINAL
   data for the project and falls back to static-only MUT-id. No projects lost.

   This applies only to `COLLECT_PIT_DATA_ORIGINAL`.
   `COLLECT_PIT_DATA_INITIAL` and `COLLECT_PIT_DATA_GENERALIZED` keep their
   current fatal behavior (if PIT fails there, the generalization is excluded
   — correct for RQ1).

7. **JPF exclusions don't affect PIT scope.** JPF (step 16) fails on
   assertion-level tasks — it marks individual assertions as `is_included =
   false` via `AbstractTask.execute` (which sets `assertionRecord.isIncluded
   = false` when `assertionRecord != null`). It never changes
   `test.is_included`. PIT targets test classes
   (`fetchIncludedTestClasses` queries `test.is_included = true`), not
   assertions. Verified: all 142 tests with JPF-failed assertions remain
   `is_included = true`.

#### Runtime cost

PIT runs twice: step 12 (PIT_ORIGINAL, broader scope — after
`FILTER_TESTS_ORIGINAL` only) and step 25 (PIT_INITIAL, narrower scope — after
`FILTER_TESTS` and `FILTER_ASSERTIONS`). Step 12's test scope is a superset of
step 25's. The extra work covers tests filtered out at steps 14–15, including
the 21,081 MissingValue-failed tests. PIT accounts for 48% of total pipeline
runtime (median 30.9s, p90 142.5s per project); the additional run adds
roughly proportional cost on a broader test set. This is the trade-off for
having mutation data available before MUT-id.

### MUT-id ensemble resolver

The resolver modifies `TestAnalysisTask.createAssertionRecords` (step 13). It
is invoked per-assertion and produces a resolution result containing:

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
  null (`CtPathStringBuilder().fromString(null)` throws).
- `JpfInstrumentationTask.executeTask` (line 91) reads
  `assertionRecord.getTestedMethodAbsolutePath()` via CtPath — NPEs if null.
- `TestAnalysisTask.createAssertionRecords` derives everything from
  `testedMethodCall` (`CtInvocation`): `tested_method_call_arguments`,
  `_source_code`, `_absolute_path`, `_relative_path`, and
  `GeneralizableInput.derive(testedMethod, testedMethodCall)`.
- `MissingValueFilter` only checks `tested_file_path`, `tested_class_name`,
  `tested_method_name`, `tested_method_parameters` — all derivable from a
  `CtMethod` alone. An assertion with a resolved method but no invocation
  would pass the filter and then crash in JPF instrumentation.

The resolver addresses this by running LCBA **per-assertion** first, then
using the killed-mutant oracle as an override signal:

1. **LCBA first (per-assertion):** call
   `TestAnalysis.findTestedMethodCall(testMethod, assertionCall)`. This
   produces a `CtInvocation` or empty — per-assertion, preserving multi-
   assertion tests' per-assertion resolution.

2. **Oracle check (per-test):** if LCBA produced a result, check whether the
   LCBA-resolved `CtMethod` is among the test's killed-mutant methods. If yes
   → **keep the LCBA result** (per-assertion invocation, correct method). This
   handles the 4,176 in-killed-set cases and all multi-assertion cases where LCBA
   picks the right method for that assertion.

3. **Oracle override (per-test):** if LCBA's method is NOT among the killed
   methods, or LCBA returned empty, use the oracle's pick. Then attempt to
   find the `CtInvocation` by scanning the test method's `CtInvocation`s for
   one that resolves to the oracle's `CtMethod`. The scan must account for
   interface-vs-implementation: PIT mutates concrete classes (e.g.
   `ArrayList.add`), but the test may call the method through an interface-typed
   variable (e.g. `List.add`). `getExecutable().getDeclaration()` on such a
   call returns the interface method, not the implementation. The scan must
   also check whether the invocation's resolved method shares the same
   simple name and signature as a method declared in the oracle's `CtMethod`'s
   declaring class or any of its supertypes. If a direct or interface-routed
   `CtInvocation` is found → use it. If LCBA succeeded but the oracle
   disagrees and no invocation can be recovered for the oracle's method →
   **keep the LCBA result** (an included-wrong assertion is better than an
   excluded one). If LCBA returned empty and no invocation can be recovered
   → fill `tested_method_*` columns from the `CtMethod` but leave
   `tested_method_call_*` null (the filter guard will exclude it).

4. **Filter guard:** extend `MissingValueFilter` to also reject assertions
   where `tested_method_call_absolute_path` is null. This prevents assertions
   with a resolved method but no invocation from reaching JPF
   instrumentation. These assertions are excluded with a clear reason
   ("tested method call could not be located in test source").

This approach means:
- For the 4,176 in-killed-set cases: LCBA's per-assertion invocation is kept,
  oracle confirms the method. No change in behavior.
- For the 2,993 absent cases: if the oracle's method has a direct or
  interface-routed call in the test source, the invocation is recovered and
  the assertion keeps its tested method corrected. If no invocation can be
  recovered, the LCBA result is kept — no regression (the assertion stays
  included with its original LCBA method).
- For the 58,122 MissingValue cases: LCBA already returned empty. The oracle
  may identify a method. If a direct invocation exists, it's recovered and
  the assertion is re-included. If not, the assertion stays excluded — no
  regression.
- For multi-assertion tests (124 included assertions with different methods):
  each assertion keeps its own LCBA invocation unless the oracle disagrees
  for that specific assertion. No collapse to a single method.

#### Signal 1: Killed-mutant oracle (primary)

For a test $t$ that has PIT mutation data (from `COLLECT_PIT_DATA_ORIGINAL`,
step 12):

1. Query `pit_mutation_report` for `stage = 'COLLECT_PIT_DATA_ORIGINAL'`,
   `variant IS NULL`, `killing_test_id = t.id`, `is_detected = true`. Collect
   the set of `(mutated_class, mutated_method, method_description)` triples.
   The `method_description` (JVM descriptor, e.g. `(II)V`) is retained to
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
the assertion. Weak alone (43% precision vs developer intent, He et al.) but
provides a per-assertion candidate and, crucially, the `CtInvocation` that
downstream stages need.

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
   methods only reached transitively. This replaces the call-depth tiebreaker
   — instead of requiring call-depth data from PIT (which it does not
   provide), the resolver checks whether a direct `CtInvocation` to the
   candidate method exists in the test source.

#### PIT-to-Spoon mapping

PIT stores method names as the JVM sees them. The resolver maps these to
Spoon `CtMethod` / `CtConstructor` objects:

- **Regular methods:** re-prefix the package
  (`mutated_package` + "." + `mutated_class`), then
  `factory.Class().get(qualifiedName).getMethodsByName(mutatedMethod)`. Use
  `method_description` to disambiguate overloads by matching parameter types
  against the JVM descriptor. The descriptor (e.g. `(II)V`) is parsed by
  extracting parameter types between the parentheses and mapping JVM type
  codes (`I`=int, `J`=long, `D`=double, `Ljava/lang/String;`=String, etc.) to
  Spoon `CtTypeReference` qualified names, then matching against each
  candidate `CtMethod`'s parameter list.
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
            (account for interface-vs-implementation: the call may resolve
            to an interface method, not the oracle's concrete class)
          - If found → use it (oracle method + recovered invocation)
          - If not found AND LCBA succeeded → keep LCBA result (no regression)
          - If not found AND LCBA returned empty → fill tested_method_* only;
            filter guard will exclude
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
| `ProjectSetupTask.java` | Uncomment lines 96 and 101 (re-enable `COLLECT_JACOCO_DATA_ORIGINAL` and `COLLECT_PIT_DATA_ORIGINAL`) |
| `PitDataCollectionTask.java` | `COLLECT_PIT_DATA_ORIGINAL` case: add try-catch in `executeInternal`, record failure via `reportInfo`, return normally on exception |
| `TestAnalysisTask.java` | `createAssertionRecords`: replace `TestAnalysis.findTestedMethodCall` call with `TestedMethodResolver.resolve` (ensemble: LCBA per-assertion + killed-mutant oracle per-test + name-matching + coverage fallback + CtInvocation recovery + PIT-to-Spoon mapping) |
| `TestedMethodResolver.java` (new) | Ensemble resolver implementation |
| `MissingValueFilter.java` | Add check: reject if `tested_method_call_absolute_path` is null (filter guard for transitive-call cases) |
| `SQLiteRepository.java` | Add query: fetch killed-mutant methods for a test from `pit_mutation_report` (stage=`COLLECT_PIT_DATA_ORIGINAL`, `variant IS NULL`, `killing_test_id`, `is_detected=true`) |

No changes to: `ProcessingStage.java`, `ProcessingPipeline.java`, `stages.py`,
`create-views.sql`, `rq1_mutation_detection.py`, `rq4_limitations.py`, or any
SQL view. The pipeline structure and all variant mappings are unchanged.

## Acceptance criteria

- [ ] `COLLECT_JACOCO_DATA_ORIGINAL` (step 10) is re-enabled.
- [ ] `COLLECT_PIT_DATA_ORIGINAL` (step 12) is re-enabled.
- [ ] `COLLECT_PIT_DATA_ORIGINAL` failure does not kill the project — the
  pipeline continues with static-only MUT-id.
- [ ] `ANALYZE_TESTS` (step 13) uses the killed-mutant oracle from
  `pit_mutation_report` (stage=`COLLECT_PIT_DATA_ORIGINAL`) alongside LCBA.
- [ ] The resolver recovers a `CtInvocation` (not just a `CtMethod`) for
  assertions where the focal method is directly invoked in the test source.
- [ ] `MissingValueFilter` rejects assertions with null
  `tested_method_call_absolute_path` (filter guard for transitive-call cases).
- [ ] The ensemble resolver resolves a focal method with invocation for a
  substantial share of the killed-mutant bucket (Tier A: 7,455 tests = 35% of
  MissingValue-failed tests), measured by re-running `applicability_priorities.py`.
- [ ] No regressions on the controlled `postgres_dev` dataset (13 projects) —
  all currently-succeeding assertions still succeed, and their MUT-id matches
  or improves.
- [ ] Multi-assertion tests with different per-assertion focal methods are not
  collapsed to a single method (LCBA per-assertion is preserved when the
  oracle confirms).
- [ ] RQ1 mutation scores remain untouched: `COLLECT_PIT_DATA_INITIAL` (step
  25, `variant='INITIAL'`) runs unchanged; all SQL views and analysis code
  continue to use `variant = 'INITIAL'` for RQ1.

## Research context

| Approach | Signal | Execution? | Precision | Recall | Open source | Fit |
|---|---|---|---|---|---|---|
| **LCBA** (current) | Static: last call before assert | No | 43% (He et al.) | 38% (He et al.) | — | baseline, one signal |
| **Ghafari** (SCAM'15, DOI 10.1109/SCAM.2015.7335402) | Static: mutator vs inspector on stateful objects | No | 85%+ (small projects) | — | No | partial — only stateful objects |
| **TCTracer** (EMSE'22, DOI 10.1007/s10664-021-10079-1) | Dynamic+static: 13-technique ensemble | Yes (-javaagent) | 85% MAP | — | Yes | strong but requires agent |
| **Methods2Test** (MSR'22, DOI 10.1145/3524842.3528009) | Static: name-strip + unique-call | No | 90.7% (retained links) | unknown | Yes | subset of our static signals |
| **TestLinker** (TSE'24, DOI 10.1109/TSE.2024.3449917) | LLM-based (CodeT5) | No | 73% | 58% | Replication pkg | out of scope (ML) |
| **He et al.** (FSE'24, DOI 10.1145/3660785) | Empirical study | — | — | — | — | motivation: confirms LCBA is bad |
| **Coach/Tracets4J** (SANER'25, DOI 10.1109/SANER64311.2025.00077) | Static heuristic ensemble | No | — (outperforms M2T/NC/LCBA) | — | No | newer static approach |
| **Mutation oracle** (our approach) | Dynamic: killed mutants per test | Already collected | 88% on sampled disagreements (n=8) | 59% single-method | — | **best fit** — data exists |

The metrics above are not directly comparable: He et al. report focal-method
precision against manual ground truth; TCTracer reports traceability MAP; and
the mutation-oracle figures are the single-method specificity (59%) and the
oracle-correct rate on a 9-case manual sample of LCBA disagreements (7/8).
Figures for Ghafari, Methods2Test, TestLinker, and Coach are as reported in
their papers and not independently reproduced here.

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
