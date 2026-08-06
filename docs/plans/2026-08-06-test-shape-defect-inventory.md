---
title: Test-Shape Handling Defect Inventory
type: audit
status: active
created: 2026-08-06
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Test-Shape Handling Defect Inventory

Every confirmed place where the pipeline mishandles a test, assertion, or report
shape, measured 2026-08-06 against the working tree at `b28ef100` and the partial
`postgres_reporeapers_rq6_v3` / `postgres_rq6_junit3_smoke` corpora. The fix work is
`2026-08-06-test-shape-handling-fixes`.

The structural cause is that no component owns the concept of a *test shape*. Framework,
annotation, lifecycle, fixture, and assertion membership are each re-derived ad hoc, so a
shape admitted upstream is silently dropped downstream. `extendsTestCase` is duplicated
verbatim as a private static in `JpfInstrumentationTask:279-288` and
`JunitDataCollectionTask:337-348`; known-test-annotation membership is re-derived at
`JunitDataCollectionTask:395-397`, `:422-424`, and `SpoonUtils:106-110`, against a
*separate* supported set in `TestTypeFilter:16`; lifecycle handling is split across
`SpoonUtils:30-39`, `GeneralizedTestBuilder:121-139`, and `JpfInstrumentationTask:45-50`,
each with a different membership and naming convention (simple vs qualified); test-library
package families are re-derived at `MethodUnderTestResolver:526-528`,
`MockingFrameworkFilter:31-37`, and `AssertionSemanticsClassifier:61-64`.

Every enumeration that matters uses **simple** annotation names, which is what allows a
foreign framework's `@Test` to pass as JUnit's.

## The generalized-class shape is the load-bearing finding

A generalized class cloned from a JUnit 3 source keeps `extends TestCase` and keeps its
sibling `test*` methods, because both deletions are annotation-driven. Two JUnit-platform
engines then claim the same class, and on surefire < 3.0.2 each writes its **own report
file**. Measured directly by running the pipeline's own emitted class under the pipeline's
own derived POM (`pom.teralizer.generalized.xml`, surefire floored 2.18.1 -> 2.22.2):

| Generalized class | Report file | Contents |
|---|---|---|
| leftovers present (as the pipeline emits it) | `TEST-<FQN>.xml` | `tests=2`: `testHas0`, `testHasStringChar` (vintage) |
| leftovers present | `TEST- ConstantTest Generalized testHasChar 13 Test.xml` | `tests=1`: `testHasChar` (jqwik) |
| leftovers deleted | `TEST-<FQN>.xml` | `tests=1 failures=1`: testcase `warning` (vintage) |
| leftovers deleted | `TEST- ... 99 Test.xml` | `tests=1`: `testHasChar` (jqwik) |

Two consequences, both measured rather than reasoned:

1. `identifyTestReportPath` returns the first *existing* candidate, so the vintage file
   shadows the jqwik file and the property's result is never found. The task throws
   `Failed to identify matching test case report`, and the generalization is recorded as a
   Stage-4 failure. This is the crash that stopped the v3 run.
2. Deleting the leftover methods does **not** fix it and is actively worse: vintage then
   emits JUnit 3's "no tests found" sentinel as a *failing* `warning` testcase, and
   `mvn test` exits `BUILD FAILURE` (`[ERROR] TestSuite$1 No tests found in ...`;
   `Tests run: 823, Failures: 1`). That would lose every generalization in the project,
   not one.

So the leftovers cannot simply be deleted while the class remains a `TestCase`. Either the
generalized class stops being a JUnit 3 test entirely, or the vintage report and its
sentinel must be interpreted rather than trusted.

`jason` (`postgres_rq6_junit3_smoke`) escaped the crash only because it declares no
surefire plugin, so the pipeline injects 3.2.5. Re-running the same two variants against a
3.2.5-pinned copy of the derived POM isolates the version as the deciding factor:

| Surefire | leftovers present | leftovers deleted |
|---|---|---|
| 2.22.2 (floor for a project-declared plugin) | **two** files; vintage FQN file shadows the jqwik file; property unreachable | two files; vintage file carries a failing `warning`; `BUILD FAILURE` |
| 3.2.5 (injected when none declared) | **one** merged FQN file containing `testHas0`, `testHasStringChar`, and `testHasChar`; property reachable | one file; sentinel present as an empty-named testcase; `BUILD FAILURE` |

Two conclusions follow. The report *split* is a surefire <3.0.2 behavior, so a content-directed
lookup is what makes ingestion correct on both generations. The vintage *sentinel* is not
version-dependent: deleting the leftover methods fails the build under 3.2.5 as well, so the
leftovers cannot be removed while the class remains a `TestCase`.

Engine selection is not an escape route. Surefire 3.x can exclude the vintage engine, which
would stop the leftovers running, but `EXECUTE_TESTS_GENERALIZED` also produces the
generalized suite's `jacoco.exec` and legitimately includes the project's original test
classes; excluding vintage would drop those originals from the measurement.

## Findings

Severity: **soundness** = a wrong number could be reported; **attrition** = data lost but
not misreported; **telemetry** = diagnostic fields wrong.

### JUnit 3 generalized-class shape

| # | Site | Defect | Consequence | Severity |
|---|---|---|---|---|
| A1 | `JunitDataCollectionTask:253-269` | report path is chosen by first existing filename, not by content | property result unreachable; generalization lost as a failure | attrition |
| A2 | `SpoonUtils:101-104` | `deleteOtherTestMethodsInClass` is annotation-only | leftover JUnit 3 `test*` run inside the generalized class. Bounded, not a wrong number: the generalized suite already includes the original test classes (235 entries in the run's own includes file), so these are *duplicate* executions of tests already present, and coverage (a union) and mutation detection (a disjunction) are unchanged. What remains is that a failure among them rejects the generalization class-level via `NonPassingTestFilter`, and runtime inflates. Per-generalization kill credit would also be wrong, but `killing_generalization_id` has no consumer in `analysis/src` | attrition |
| A3 | `SpoonUtils:112-117` | `deleteOtherAssertionsInMethod` filters JUnit 4/5 only | sibling JUnit 3 assertions remain in the property, so it can fail for reasons unrelated to the generalized assertion | soundness |
| A4 | `GeneralizedTestBuilder:201-218` | lifecycle rewrite is annotation-only | JUnit 3 `setUp` never runs for the property although `JpfInstrumentationTask:265-275` runs it during extraction: the property is validated against different state than was analyzed | soundness |
| A5 | `SpoonUtils:186-190` | `isFlattenableInheritedMethod` is annotation-only | inherited JUnit 3 test and fixture methods are misjudged when cloning | attrition |

### JUnit 3 upstream handling

| # | Site | Defect | Consequence | Severity |
|---|---|---|---|---|
| B1 | `ProjectSetupTask:150-162` | `junit-<version>.jar` is bucketed `JUNIT_4`; `TestFramework` has no JUnit 3 member | a JUnit 3-only project gets JUnit 4 / vintage dependency injection (`MavenDependencyManager:47-55`, `GradleDependencyManager:52-60`) | soundness |
| B2 | `driver-class.vm:13-24` + `InstrumentedClassBuilder:46-64` | the symbolic driver always calls `new InstrumentedClass()`, but the clone preserves the source constructors, and a JUnit 3 class commonly declares only `TestCase(String name)` | instrumented build fails, so the whole project's extraction is lost | attrition |
| B3 | `JpfInstrumentationTask:248-276`, `driver-class.vm:13-24` | no `tearDown` anywhere; `Configuration:195-197` defines only `JUNIT3_FIXTURE_METHOD = "setUp"` | JUnit 3 cleanup never runs, in extraction or in the generalized property; state leaks between properties | soundness |
| B4 | `GeneralizedTestBuilder:52-78` | generalized clone preserves constructors with no adaptation | a String-only constructor may make the jqwik container non-instantiable | UNVERIFIED |
| B5 | pipeline-wide | JUnit 3 `suite()` static factories are not recognized or rewritten | a `suite()` may keep selecting original tests instead of the property | UNVERIFIED |
| B6 | `JunitDataCollectionTask:330-337` | JUnit 3 method predicate checks name prefix, arity, and ancestry, but not `public void` | shape misattribution | UNVERIFIED |

### Foreign and disabled shapes

| # | Site | Defect | Consequence | Severity |
|---|---|---|---|---|
| C1 | `JunitDataCollectionTask:422-428` + `TestTypeFilter:16` | annotation matched by **simple** name, so `org.testng.annotations.Test` is accepted as JUnit `Test`. TestNG projects are present in the corpus (`dataset/dataset_java.csv:2044,2625,4144,4684,6281,7145`) and TestNG is already known as a test library at `MethodUnderTestResolver:526-528` | TestNG tests are analyzed and generalized as if JUnit | soundness |
| C2 | `JunitDataCollectionTask:422-434` | no `@Ignore` / `@Disabled` gate exists anywhere in `src/main/java/teralizer` | tests the developer disabled are analyzed and generalized as live | soundness |
| C3 | `GeneralizedTestBuilder:50,195-199` | every `@RunWith` is stripped without inspecting the runner | `@RunWith(Parameterized.class)` and custom runners lose their construction and execution semantics silently | soundness |
| C4 | `AssertionSemanticsClassifier:66-72,106-116` | 3-argument Hamcrest `assertThat(reason, actual, matcher)` reads focus from argument 0 and matcher from argument 1 | reason string recorded as the focus, actual expression recorded as the matcher name | telemetry |

### Throw-instead-of-degrade paths

Each of these aborts analysis for a shape the pipeline admits. This is the same failure
mode that cost 21% of assertions their telemetry before `647c1b1e`.

| # | Site | Trigger |
|---|---|---|
| C5 | `MethodUnderTestResolver:112-113,154-155` | dispatches `assertThrows` on name alone, then reads argument index 1 unchecked: an admitted non-JUnit-5 or malformed `assertThrows` throws `IndexOutOfBoundsException` |
| C6 | `TestAnalysis:239-240` | `getExpectedParameterIndex` throws for any non-JUnit-5 `assertThrows` instead of returning `Optional.empty()` |
| C7 | `AssertionInLoopFilter:29-30`, `TestedMethodInLoopFilter:34-35` | missing Spoon path throws `IllegalStateException` out of the filter instead of producing a filter result |

### Report ingestion

| # | Site | Defect | Consequence | Severity |
|---|---|---|---|---|
| D1 | `PitDataCollectionTask:51-62,418-443` | test-name regex recognizes only constrained class/engine/method forms; unresolved names are kept with null ids and logged at debug | mutation kills unattributed. Measured on `postgres_rq6_junit3_smoke`: **3.6%** of detected generalized mutations and **1.4%** of initial have neither `killing_test_id` nor `killing_generalization_id` | soundness |
| D2 | `JunitDataCollectionTask:163-225` | discovery flattens all XML then `Collectors.toMap` keeps the first record per method key | when two engines report the same method, which record survives depends on `Files.walk` order, so result and failure attribution is nondeterministic | soundness |
| D3 | `JunitDataCollectionTask:169-170` | one unparseable XML propagates out of the stream | a single malformed report loses all test discovery for the project | attrition |
| D4 | `JunitDataCollectionTask:74-78` vs `TaskDiagnosticClassifier:208-219` | missing report *directory* message is not mapped, falling through to `UNSUPPORTED_REPORT_LAYOUT` | missing report conflated with unsupported shape | telemetry |
| D5 | `JacocoDataCollectionTask:75-127,175-192`, `EvoSuitePostprocessingTask:58-128` | one fixed report path; naive `split(",")` with no row-width or quoting handling; header-only file accepted as success | a quoted comma corrupts or shifts every field; "ran with no coverage" is indistinguishable from "report malformed" | soundness |
| D6 | `JunitDataCollectionTask:349-363` | `replaceSpaces` forces spaces to underscores before the first `(` | legitimate names containing spaces can be made unmatchable; distinct parameterized display names can collapse onto one source method | attrition |
| D7 | `JunitDataCollectionTask:266` | `@TODO`: alternative filename still too long is unhandled | report unreachable for very long class names | attrition |

## Verified correct

Recorded so the sweep is auditable rather than open-ended.

- The `TEST_MARKER_JUNIT3` lifecycle is coherent end to end: set at
  `JunitDataCollectionTask:422-431`, listed at `Configuration:192-199`, accepted at
  `TestTypeFilter:15-21`. No Python consumer in `analysis/src/` enumerates
  `test_annotation_name` values, so nothing downstream misclassifies the marker.
- `TestAnalysis.isAssertion:187-199` admits the JUnit 3 declaring types, and after
  `647c1b1e` the framework lookup and index tables cover JUnit 3 with the correct
  message-first convention. The two-String `assertEquals(String,String)` overload maps
  expected 0 / actual 1 correctly, and `isDoubleDelta:445-447` requires every parameter to
  be `double`, so message-first double overloads do not take the delta rows.
- `JpfInstrumentationTask.beforeMethodsFor:248-276` walks the hierarchy parent-first, so an
  inherited JUnit 3 fixture runs before its override.
- `PitDataCollectionTask:46-61` explicitly handles JUnit Vintage runner identity forms.
- PIT and JaCoCo report *absence* is classified separately from tool failure
  (`TaskDiagnosticClassifier:80-95`), and JUnit result mapping
  (`JunitDataCollectionTask:513-524`) throws on an unknown shape rather than conflating it.
- Raw reports are preserved to the data directory before parsing in all three collectors,
  which is what made this inventory possible after the fact.

## Deliberate limitations, not defects

- `Configuration:210-214` restricts `GENERALIZABLE_ASSERTS` to equality, booleans, and
  `assertThrows`. Nullness, sameness, array, inequality, and non-`equalTo` matcher forms are
  recognized and classified, then rejected by `UnsupportedAssertionFilter`. That is scope,
  and the rejection is countable.
- JUnit 3 has no `assertThrows` API, so its absence from normalization is correct; only the
  unchecked argument access in C5/C6 is a defect.
- Class-level PIT identities carry no method and are intentionally left unlinked.
