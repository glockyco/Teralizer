---
title: Test-Shape Handling Fixes
type: plan
status: draft
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

### A generalized class is a pure jqwik container

This is forced by measurement, not preference. A cloned JUnit 3 class keeps `extends TestCase`
and its sibling `test*` methods, so two engines claim it and — on surefire < 3.0.2 — each
writes its own report. The inventory records the experiment: leftovers present yields two
report files and the vintage one shadows the jqwik one; leftovers deleted makes vintage emit
its "no tests found" sentinel as a *failing* testcase and `mvn test` exits `BUILD FAILURE`,
which would lose every generalization in the project.

So the only shape that is correct under both surefire generations is a class the vintage
engine will not claim at all:

1. leftover JUnit 3 `test*` methods deleted, as annotated siblings already are;
2. leftover JUnit 3 sibling assertions deleted from the property;
3. `setUp` / `tearDown` converted to `@BeforeProperty` / `@AfterProperty`;
4. `extends TestCase` removed, and the inherited assertion calls the property still needs
   qualified to `junit.framework.Assert.assertX(...)` — always valid, since it is a public
   static class;
5. constructors reduced to what jqwik can instantiate.

**Drop or refuse.** Step 4 is safe only when everything the retained code references from
above the cloned class is an assertion (qualifiable) or a fixture (converted). When an
intermediate base contributes anything else — a helper, a field — the class cannot be
detached, and keeping it as a `TestCase` would reintroduce either the shadowed report or the
build failure. In that case the generalization is **refused** with a countable diagnostic
rather than emitted with contaminated attribution. This follows the widening license's
existing discipline: the tool declines a claim it cannot support. The refusal rate is
measured in the gate below, and if it is material the fallback gets revisited before the
relaunch rather than after.

### Report ingestion is content-directed

A class may legitimately have several report files. Selection stops guessing from filenames:
collect every candidate, choose the one that actually contains the expected test case, and
throw only when no candidate does — naming every candidate inspected. Duplicate records
across engines are resolved deterministically instead of by `Files.walk` order.

## Tasks

### Task 1: Test-shape owner

- [ ] Add the shape owner and route every call site through it, deleting the duplicated
      `extendsTestCase` in `JpfInstrumentationTask` and `JunitDataCollectionTask`, the three
      re-derived annotation-membership checks, and the three split lifecycle sets. Add
      `tearDown` to the JUnit 3 fixture concept. Classification is by qualified name.
  Verification: `grep -rn 'extendsTestCase\|KNOWN_TEST_ANNOTATIONS\|LIFECYCLE_ANNOTATIONS' src/main/java` shows the owner as the only definition site; `./gradlew test`
  Expected: no duplicate predicate remains; existing tests pass.

- [ ] Reject foreign and disabled shapes explicitly: TestNG `@Test` and any `@Ignore` /
      `@Disabled` test get their own filter reason codes instead of being accepted as JUnit
      (inventory C1, C2).
  Verification: unit tests over Spoon fixtures for a TestNG `@Test`, a `@Test @Ignore`, and a `@Test @Disabled`
  Expected: each rejected with its own code, none reaching generalization.

- [ ] Commit.
  Message: `refactor(shape): give test shape a single owner`

### Task 2: Generalized classes become pure jqwik containers

- [ ] Delete leftover JUnit 3 test methods and sibling assertions, convert JUnit 3 fixtures
      to jqwik lifecycle annotations, detach `extends TestCase` with inherited assertions
      qualified, and adapt constructors (inventory A2, A3, A4, A5, B4).
- [ ] Refuse with a countable diagnostic when the class cannot be detached safely, rather
      than emitting a contaminated class.
  Verification: unit tests asserting the emitted class for a JUnit 3 source has no `TestCase` ancestor, exactly one test-shaped method, no sibling assertions, and jqwik lifecycle hooks; plus a fixture whose base contributes a helper, which must refuse
  Expected: both outcomes hold; the refusal carries its reason code.

- [ ] Commit.
  Message: `fix(generalization): emit generalized tests as pure jqwik containers`

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
  Verification: for every generalized class, exactly one surefire report file exists; no
  report contains a `warning` sentinel or a leftover original test; zero
  `Failed to identify matching test case report`; `realworld.assertions_without_resolution`
  is zero; the Stage-4 refusal breakdown separates oracle refusals from shape refusals.
  Expected: all hold, and the shape-refusal rate is recorded here before the relaunch decision.

- [ ] Full gates: `./gradlew test`, `uv run --directory analysis pytest tests/eval -q`.
  Expected: green, except the corpus-dependent telemetry test until v4 exists.

- [ ] Relaunch the corpus into a fresh database, superseding `postgres_reporeapers_rq6_v3`.
      Update `2026-08-04-rq6-recollection` and the run map to the new measurement basis in the
      same commit.

## Assumptions & contingencies

- **The refusal fallback is rare.** If Task 2's gate shows a material shape-refusal rate, the
  fallback is the wrong trade: revisit before relaunching, because refusing a large share of
  JUnit 3 generalizations would understate applicability as badly as contaminating it would
  overstate it.
- **Both surefire generations stay in scope.** The pipeline floors a project-declared surefire
  to 2.22.2 and injects 3.2.5 when none is declared, so every report-shape fix must be
  verified against both. Do not "fix" this by forcing one version: the floor exists so the
  project's own ORIGINAL runner is preserved.
- **B4/B5/B6 are unverified.** Constructor instantiability, `suite()` factories, and the
  `public void` predicate are structurally present but unproven. Task 2 and Task 4 add loud,
  countable diagnostics for them; if the gate shows hits, they graduate to their own tasks
  rather than being fixed speculatively.
- **v3 is not salvageable.** It carries 45 minutes of data from the defective tree and its
  successor supersedes it entirely. Do not merge or compare against it.
