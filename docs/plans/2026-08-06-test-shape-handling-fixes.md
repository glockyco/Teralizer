---
title: Test-Shape Handling Fixes
type: plan
status: active
created: 2026-08-06
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Test-Shape Handling Fixes

Make the pipeline handle every test, assertion, and report shape it admits, so the RQ6
re-collection measures the tool rather than the tool's blind spots. Evidence and per-defect
consequences: `2026-08-06-test-shape-defect-inventory`.

The RQ6 v3 run was stopped 45 minutes in. Nothing relaunches until this plan's gate passes.

## Design

### One owner for test shape

Every defect in the inventory traces to the same absence: no component owns *test shape*, so
framework, annotation, lifecycle, fixture, and assertion membership are re-derived per call
site against inconsistent sets, using **simple** annotation names. A new owner replaces all
of them, and the duplicated predicates are deleted rather than left beside it:

- framework and annotation classification by **qualified** name, so `org.testng.annotations.Test`
  can never pass as `org.junit.Test`;
- the JUnit 3 shape: `TestCase` ancestry, the `public void test*()` no-arg method predicate,
  and the fixture methods `setUp` **and** `tearDown`;
- one lifecycle registry mapping every recognized fixture (annotated JUnit 4/5 *and*
  convention-named JUnit 3) onto its jqwik equivalent;
- disabled markers (`@Ignore`, `@Disabled`) as a first-class exclusion;
- test-library package families, today re-derived in three places.

Call sites consult the owner. No call site keeps its own set.

### JUnit 3 stays JUnit 3

The generalized class keeps `extends TestCase`. Nothing rewrites a project's inherited
assertions, and no generalization is refused for its source framework's shape. The earlier
idea of detaching `TestCase` is rejected: the inventory's measurements show it is not
required, and it would mean rewriting user code to buy a correction the metrics do not need.

What the class *does* need is that the pipeline reads it correctly and executes the property
under the same fixture the specification was extracted under. Three changes, none of which
touch the class's framework identity:

1. **Fixtures reach the property.** JUnit 3 declares its fixture by overriding `setUp` /
   `tearDown`, so the existing lifecycle rewrite never sees them. Annotating those same
   methods with `@BeforeProperty` / `@AfterProperty` makes jqwik run them, while vintage
   continues to run them for the leftover methods — correct for both. This closes the
   soundness gap where `JpfInstrumentationTask` ran `setUp` during extraction but the
   property ran without it.
2. **Sibling assertions leave the property.** `deleteOtherAssertionsInMethod` already strips
   JUnit 4/5 siblings; extending it to JUnit 3 stops the property failing for an assertion
   that is not the one being generalized. This is independent of the vintage engine.
3. **Leftover sibling methods stay.** They cannot be deleted: the inventory shows vintage
   then emits its "no tests found" sentinel as a failing testcase and the build fails, under
   2.22.2 *and* 3.2.5. Their only real consequence is that `NonPassingTestFilter` is
   class-scoped, so a failing leftover rejects the generalization. That is fixed where the
   defect actually is — in the filter's attribution, not in the emitted source: judge the
   generalization by the property's own test case. A leftover failure is a duplicate of an
   original test already in the suite, so it is a project condition, and a non-green
   assembled suite is already reported separately as `SUITE_NOT_GREEN`.

### Report ingestion is content-directed

A class may legitimately have several report files. Selection stops guessing from filenames:
collect every candidate, choose the one that actually contains the expected test case, and
throw only when no candidate does — naming every candidate inspected. Duplicate records
across engines are resolved deterministically instead of by `Files.walk` order.

This is what makes ingestion correct on both surefire generations the corpus exercises, and
it is why the surefire floor is **not** raised here. Pinning INITIAL and GENERALIZED to 3.x
would also merge the reports and would give the corpus one uniform report shape, but it
changes the build for every project that declares a surefire plugin, and 3.x drops legacy
configuration that some of the 1,161 projects rely on. Correctness does not need it, so that
trade is evaluated on its own rather than smuggled into a correctness fix.

## Tasks

### Task 1: Test-shape owner

- [x] Add the shape owner and route every call site through it, deleting the duplicated
      `extendsTestCase` in `JpfInstrumentationTask` and `JunitDataCollectionTask`, the three
      re-derived annotation-membership checks, and the three split lifecycle sets. Add
      `tearDown` to the JUnit 3 fixture concept. Classification is by qualified name.
  Verification: `grep -rn 'extendsTestCase\|KNOWN_TEST_ANNOTATIONS\|LIFECYCLE_ANNOTATIONS' src/main/java` shows the owner as the only definition site; `./gradlew test`
  Expected: no duplicate predicate remains; existing tests pass.

- [x] Reject foreign and disabled shapes explicitly: TestNG `@Test` and any `@Ignore` /
      `@Disabled` test get their own filter reason codes instead of being accepted as JUnit
      (inventory C1, C2).
  Verification: unit tests over Spoon fixtures for a TestNG `@Test`, a `@Test @Ignore`, and a `@Test @Disabled`
  Expected: each rejected with its own code, none reaching generalization.

- [x] Commit.
  Message: `refactor(shape): give test shape a single owner`

### Task 2: The property runs under its own fixture and its own assertion

- [ ] Annotate JUnit 3 `setUp` / `tearDown` on the generalized class with
      `@BeforeProperty` / `@AfterProperty` so jqwik runs the fixture the specification was
      extracted under, and extend `deleteOtherAssertionsInMethod` to JUnit 3 so only the
      generalized assertion remains in the property (inventory A4, A3, A5).
  Verification: unit tests asserting the emitted class for a JUnit 3 source carries jqwik lifecycle hooks on the inherited-or-declared fixture methods, retains its `TestCase` ancestor, and holds exactly one assertion in the property
  Expected: all three hold; the leftover sibling *methods* are deliberately still present.

- [ ] Scope `NonPassingTestFilter` to the generalization's own test case rather than every
      test case in its class, so a leftover original's failure no longer rejects it
      (inventory A2). A non-green assembled suite stays reported by `SUITE_NOT_GREEN`.
  Verification: unit test with a report containing a passing property and a failing sibling
  Expected: accepted, with the sibling failure recorded but not decisive.

- [ ] Commit.
  Message: `fix(generalization): run properties under their source fixture`

### Task 3: Content-directed report ingestion

- [ ] Select the report file by content across all candidates; resolve cross-engine duplicate
      records deterministically; map missing-report-directory to its own diagnostic; handle a
      malformed report without losing the project's discovery (inventory A1, D2, D3, D4).
  Verification: unit tests over fixture report sets — FQN-only, display-name-only, both present with the case in only one, both present with duplicates, and one malformed file among good ones
  Expected: the correct case is found in every arrangement; the malformed file does not abort discovery.

- [ ] Commit.
  Message: `fix(reports): select surefire reports by content`

### Task 4: Upstream JUnit 3 correctness

- [ ] Add a JUnit 3 member to `TestFramework`, detect a JUnit 3-only classpath, and give the
      Maven and Gradle dependency managers a correct branch for it (inventory B1).
- [ ] Make the symbolic driver construct the wrapper for classes without a no-argument
      constructor, and invoke `tearDown` after the test (inventory B2, B3).
  Verification: run the two pinned JUnit 3 projects through extraction; `grep -c 'No tests found\|cannot be applied' ` on the build output
  Expected: instrumented build succeeds where it previously failed; teardown appears in the generated driver.

- [ ] Commit.
  Message: `fix(junit3): detect the framework and drive its fixtures`

### Task 5: Degrade instead of throwing

- [ ] Make the resolver's `assertThrows` dispatch framework- and arity-checked, return
      `Optional.empty()` for a non-JUnit-5 `assertThrows` expected index, and turn the two
      loop filters' missing-path `IllegalStateException` into filter results
      (inventory C5, C6, C7).
  Verification: unit tests for a JUnit 3-declared `assertThrows`, a 1-argument `assertThrows`, and an assertion with no Spoon path
  Expected: each degrades to a recorded outcome; none propagates out of the task.

- [ ] Commit.
  Message: `fix(analysis): degrade unsupported assertion shapes`

### Task 6: Attribution and telemetry accuracy

- [ ] Recognize display-name-shaped PIT test identities so generalized mutation kills are
      attributed, and fix the 3-argument Hamcrest focus and matcher indices
      (inventory D1, C4).
- [ ] Parse JaCoCo and EvoSuite CSV with quoting and row-width validation, and distinguish an
      empty report from a malformed one (inventory D5).
  Verification: re-run PIT collection on a pinned project and query the unattributed share; unit tests for a quoted-comma CSV row and a 3-argument `assertThat`
  Expected: unattributed detected mutations reach zero for resolvable identities; CSV fields survive quoting.

- [ ] Commit.
  Message: `fix(collection): attribute mutations and parse reports faithfully`

### Task 7: Gate, then relaunch

- [ ] Run the two pinned JUnit 3-heavy projects into a scratch database and confirm the shape
      contract end to end.
  Verification: zero `Failed to identify matching test case report` on a project whose
  surefire is floored to 2.22.2 (the split-report case) and on one running the injected 3.2.5;
  every generalized property has a `junit_test_report` row; no generalization is rejected on a
  leftover sibling's failure; `realworld.assertions_without_resolution` is zero; JUnit 3
  fixtures appear in the emitted properties.
  Expected: all hold on both surefire generations, which is the point of the content-directed
  lookup.

- [ ] Full gates: `./gradlew test`, `uv run --directory analysis pytest tests/eval -q`.
  Expected: green, except the corpus-dependent telemetry test until v4 exists.

- [ ] Relaunch the corpus into a fresh database, superseding `postgres_reporeapers_rq6_v3`.
      Update `2026-08-04-rq6-recollection` and the run map to the new measurement basis in the
      same commit.

## Assumptions & contingencies

- **Both surefire generations stay in scope.** The pipeline floors a project-declared surefire
  to 2.22.2 and injects 3.2.5 when none is declared, so every report-shape fix must be
  verified against both. Do not "fix" this by forcing one version: the floor exists so the
  project's own ORIGINAL runner is preserved, and raising it changes builds corpus-wide.
- **Raising the surefire floor to 3.x is a separate decision.** It would merge the reports and
  make report shapes uniform across the corpus, which is attractive for the replication
  package. It is not needed for correctness once ingestion is content-directed, and it risks
  breaking projects whose configuration 3.x no longer accepts. If it is ever taken, it needs
  its own measurement of how many projects' generalized builds change.
- **Leftover sibling methods are deliberately retained.** Deleting them fails the build under
  both surefire generations. If a future change makes the generalized class stop being a
  `TestCase` for unrelated reasons, revisit, because the deletion becomes safe at that point.
- **B4/B5/B6 are unverified.** Constructor instantiability, `suite()` factories, and the
  `public void` predicate are structurally present but unproven. Task 2 and Task 4 add loud,
  countable diagnostics for them; if the gate shows hits, they graduate to their own tasks
  rather than being fixed speculatively.
- **v3 is not salvageable.** It carries 45 minutes of data from the defective tree and its
  successor supersedes it entirely. Do not merge or compare against it.
