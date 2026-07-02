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

**Relationship to the fusion model:** `2026-07-02-mut-id-confidence-fusion` owns the
MUT-id design — lexicographic confidence tiers, two grades, and the
`mut_resolution_observation` provenance schema. This spec is its **runtime tier**: the
killed-mutant oracle enters as the strongest *corroborator/refuter* (T2 promotion,
coherent-shallow refutation, T4 disambiguation, `oracle_agreement` population), and as the
top *identifier* only where static dataflow is ambiguous — not as an unconditional
cascade-top. Rationale: the oracle answers "whose faults does this test detect?", static
dataflow answers "whose output does the assertion check?" (what spec extraction needs);
on divergence the static oracle-value method drives generalization and the oracle drives
refutation. The AST-only first phase is `2026-07-02-static-mut-id-fusion` (plan); this
spec's remaining scope is the PIT_ORIGINAL enablement, `CtInvocation` recovery, and
per-assertion disambiguation within the focal set.

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
methods had mutants killed — a high-specificity signal for the focal method
(helpers and getters rarely have killable mutants), though noisy: collateral
kills, private/indirect methods, and flaky tests can mis-attribute (see
§Research context). The
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

Of 21,081 MissingValue-failed tests (measured from a one-off PIT_ORIGINAL
experiment — the committed pipeline has **0** `COLLECT_PIT_DATA_ORIGINAL` rows;
re-enabling PIT_ORIGINAL regenerates this data, see
`2026-06-28-mut-id-targeting-and-coverage`):

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

The resolver design addresses this by disambiguating per-assertion within the
oracle's focal set (see §Combination strategy).

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

The resolver treats the killed-mutant oracle as the authority on **which** method
is focal, and recovers the test-side `CtInvocation` for that method independently.
LCBA is not consulted when the oracle has data — it is a separate mechanism, used
only as the no-data fallback and as an in-set tiebreaker. The two never compete:
the oracle decides the method, Spoon recovers its invocation.

1. **Oracle resolves the focal method (per-test, when PIT data exists).** From the
   test's killed mutants, take the set of mutated methods (see Signal 1). One method
   in the set (59% of cases) → that is the focal method. Multiple → disambiguate
   *within the set* per-assertion (next step). The oracle's verdict stands on its
   own; LCBA does not override it.

2. **Per-assertion disambiguation (only when the focal set has >1 method).** For the
   assertion at hand, pick the focal-set method this assertion exercises — by
   dataflow from the asserted value where available, with LCBA **constrained to the
   focal set** as a cheap tiebreaker (does the per-assertion LCBA land on a focal-set
   method?). LCBA here only ranks among methods the oracle already certified as focal;
   it never introduces a method outside the set.

3. **Independent `CtInvocation` recovery.** Given the chosen focal `CtMethod`, scan the
   test method's `CtInvocation`s for the call resolving to it. The scan accounts for
   interface-vs-implementation: PIT mutates concrete classes (e.g. `ArrayList.add`),
   but the test may call through an interface-typed variable (e.g. `List.add`), so
   `getExecutable().getDeclaration()` returns the interface method. Match on simple
   name + JVM descriptor against the focal method's declaring class or any supertype.
   If a direct or interface-routed invocation is found → use it. If none is found →
   **exclude** the assertion (fill `tested_method_*` from the `CtMethod`, leave
   `tested_method_call_*` null, let the filter guard drop it). The resolver never
   falls back to a non-focal LCBA method: generalizing a method the oracle says is
   not focal (a getter/helper with no killed mutants) produces a valid-but-meaningless
   generalization of the wrong method, which is worse than excluding.

4. **No oracle data → LCBA fallback (per-assertion).** When the test has no PIT
   mutation data, call `TestAnalysis.findTestedMethodCall(testMethod, assertionCall)`
   — it yields both the method and its invocation. This is the only path
   where LCBA decides the method.

5. **Filter guard:** extend `MissingValueFilter` to also reject assertions where
   `tested_method_call_absolute_path` is null, so a resolved method with no recovered
   invocation never reaches JPF instrumentation. Excluded with a clear reason
   ("tested method call could not be located in test source").

This approach means:
- **Single-method oracle (59%):** the oracle's method is focal outright; its invocation
  is recovered by Spoon. LCBA is irrelevant. Where LCBA would have disagreed, the
  oracle corrects it (the 2,993 LCBA-method-has-no-kills cases).
- **Multi-method oracle (41%):** the oracle bounds the candidates; per-assertion
  disambiguation picks within the set, so multi-assertion tests keep per-assertion
  resolution without collapsing to one method.
- **The 58,122 MissingValue cases:** LCBA returned empty. The oracle may resolve a
  focal method; if its invocation is recoverable, the assertion is re-included,
  otherwise it stays excluded — no regression.
- **No PIT data (46% of MissingValue tests):** LCBA fallback, behavior unchanged.

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

#### Signal 2: Coverage-based (secondary — PIT data exists but no kills)

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

#### Signal 3: LCBA (static fallback — always available)

The current `TestAnalysis.findTestedMethodCall` — the last invocation before
the assertion. Weak alone (43% precision vs developer intent, He et al.) but
provides a per-assertion candidate and, crucially, the `CtInvocation` that
downstream stages need.

#### Signal 4: Name-matching (static corroborator / in-set tiebreaker)

Used to disambiguate within an oracle or coverage focal set, and as a static
fallback when no PIT data exists:

1. **Naming Conventions (NC):** test method name matches production method
   name after removing "test" prefix.
2. **Naming Conventions — Contains (NCC):** test method name contains
   production method name.
3. **Longest Common Subsequence (LCS):** ratio of LCS length to the longer
   name length.

These are cheap static checks applied to all methods in the focal class
(inferred from the test class name via NC/NCC: `FooTest` → `Foo`).

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

The resolver operates per-assertion as a confidence-ordered cascade. Execution-grounded
PIT signals decide the focal method first (killed mutants, then coverage); the static
signals (LCBA, name-matching) are the fallback when no PIT signal is available:

```
for each assertion in test:
    1. Killed-mutant oracle (Signal 1) — test has killed mutants:
       a. one killed method → focal = that method
       b. multiple → disambiguate within the set per-assertion, by kill count +
          tiebreakers (name-match, then in-set LCBA, then alphabetical)
       c. recover the CtInvocation: scan the test source for a call resolving to
          `focal` (account for interface-vs-implementation: the call may resolve to
          an interface method, not the oracle's concrete class)
          - found → use it (oracle method + recovered invocation)
          - not found → fill tested_method_* from the CtMethod only; the filter guard
            excludes it. Do NOT substitute a non-focal method.
       d. killed-mutant oracle yields nothing usable (no kills, or focal method
          unmappable) → step 2
    2. Coverage signal (Signal 2) — PIT coverage exists but no kills:
       a. candidates = covered methods in the focal class, preferring directly-invoked
          ones; one → focal, multiple → disambiguate within the set as in 1b
       b. recover the CtInvocation as in 1c; found → use it, not found → step 3
    3. Static fallback — no usable PIT signal:
       a. LCBA (Signal 3) → CtMethod + CtInvocation per-assertion
       b. name-matching (Signal 4, NC/NCC/LCS) corroborates or disambiguates among
          focal-class methods
       c. nothing → leave tested_method_* null (MissingValue excludes)
```

This design ensures:
- **Oracle is authoritative when present:** a single-method oracle is used outright;
  a multi-method oracle is disambiguated within its set. LCBA never overrides it and
  never contributes a method outside the killed set.
- **No meaningless inclusions:** when the oracle's focal method has no recoverable
  invocation, the assertion is excluded rather than generalized against a non-focal
  LCBA method.
- **Per-assertion granularity for multi-assertion tests:** disambiguation runs
  per-assertion within the focal set, so different assertions keep different focal
  methods.
- **No regression where the oracle is silent:** tests without PIT data keep the
  existing LCBA behavior.
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
| `TestAnalysisTask.java` | `createAssertionRecords`: replace `TestAnalysis.findTestedMethodCall` call with `TestedMethodResolver.resolve` (killed-mutant oracle per-test as primary + independent CtInvocation recovery + PIT-to-Spoon mapping; LCBA fallback per-assertion + name-matching when the oracle is silent) |
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
  `pit_mutation_report` (stage=`COLLECT_PIT_DATA_ORIGINAL`) as the primary signal, with LCBA as the no-data fallback.
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
  collapsed to a single method (disambiguated per-assertion within the oracle's
  focal set).
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

### Novelty verdict (literature survey, 2026-06-28)

No surveyed paper uses per-test killed-mutant data as the **primary** focal-method
oracle ("the methods whose mutants this test kills are the methods under test").
This was confirmed against OpenAlex + the test-to-code-traceability citation chain
(Van Rompaey & Demeyer `10.1109/CSMR.2009.39`; SCOTCH `10.1109/ICSM.2011.6080773`;
Ghafari `10.1109/SCAM.2015.7335402`; TCTracer `10.1007/s10664-021-10079-1`;
Methods2Test `10.1145/3524842.3528009`; He et al. `10.1145/3660785`; TestLinker
`10.1109/TSE.2024.3449917`; Tracets4J `10.1109/SANER64311.2025.00077`). The two
closest precedents differ fundamentally:

- **Vercammen et al., Goal-Oriented Mutation Testing with Focal Methods**
  (`10.1145/3278186.3278190`) runs the *reverse* direction: it assumes focal-method
  links to restrict and speed mutation testing. It does not infer focal methods from
  killed-mutant tables.
- **TCTracer** (`10.1007/s10664-021-10079-1`) ranks executed methods by dynamic
  call-trace + static features, and explicitly warns that executed helper/getter/
  setup methods are false-positive risks — killed-mutant attribution is a
  stronger-than-coverage signal it does not use.

So the approach is novel-but-adjacent: reusing PIT kill tables already collected
for mutation scoring as the focal oracle, with LCBA demoted to a no-data fallback.

### Known pitfalls (literature) and how this design handles them

- **Equivalent mutants** (`10.1109/TSE.2010.62`): no kill ≠ not focal. Handled —
  the oracle only *fires* on kills; absence of kills falls through to coverage then
  LCBA, never concludes "not focal."
- **Collateral / coincidental kills and multi-method kills** (Vercammen
  `10.1145/3278186.3278190`; Du et al. `10.1145/3597926.3598090`): a test can kill
  mutants in non-intended methods. Handled — the killed set is treated as candidate
  *focal candidates*, disambiguated per-assertion within the set, not collapsed to one.
- **Flaky tests corrupt kill attribution** (Shi et al. `10.1145/3293882.3330568`;
  Alshammari et al. `10.1109/ICSTW60967.2024.00054`): an open risk for this design;
  PIT_ORIGINAL kills inherit any suite flakiness. Mitigation deferred (quarantine/
  re-run) — recorded here as a known threat to validity.
- **Private/indirect focal methods** (Vercammen `10.1145/3278186.3278190`): a kill
  in a private helper may reflect testing a public wrapper. The interface-vs-impl
  scan only covers interface→implementation dispatch, not wrapper→private-helper:
  if the test invokes only the public wrapper, no test-side `CtInvocation` to the
  helper exists, so recovery fails and the assertion is **excluded** by the filter
  guard (open case, handled conservatively — excluded, never mis-attributed).
- **Per-test kill tables are coarse for multi-assert tests** (ATLAS
  `10.1145/3377811.3380429`): handled by per-assertion disambiguation within the set.
- **Coverage is weaker than kills** (Chekam et al. `10.1109/ICSE.2017.61`): hence
  coverage is Signal 2 (secondary), never equal to the kill oracle.

Teralizer's cost advantage is relative, not zero: PIT is already part of the
pipeline (no new agent or instrumentation, unlike TCTracer's tracing agent), so
the oracle reuses an existing capability. But the oracle needs `PIT_ORIGINAL`
(pre-filter) kills, which the committed pipeline does not produce — enabling it
adds an earlier PIT pass with its own runtime cost (see the runtime-cost note and
`2026-06-28-mut-id-targeting-and-coverage`).

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
