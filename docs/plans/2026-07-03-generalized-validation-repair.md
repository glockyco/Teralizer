---
title: Generalized Validation-Layer Repair
type: plan
status: active
created: 2026-07-03
parent: 2026-06-26-teralizer-overview
---

# Generalized Validation-Layer Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the generalized validation stages (`BUILD_PROJECT_GENERALIZED` → `EXECUTE_TESTS_GENERALIZED` → `COLLECT_JUNIT_REPORTS_GENERALIZED`) succeed across the fusion-spike corpus so that `jqwik_property_execution` coverage rises from 2/19 projects (168/2003 generalizations) to every project that fits the execution budget — the precondition for the seed-kill share metric in `2026-07-02-static-mut-id-fusion` Task 11 Step 5.

**Architecture:** Three independent defects, each fixed at its source seam: (1) the generated telemetry harness requires Java 8 syntax but projects pin `-source 1.5/1.6/1.7` — floor the *test* compilation level in the Teralizer-owned build-file copy; (2) `BaselineTestParametersSupplierFactory` renders `(java.lang.Long) -9223372036854775808L`, which javac re-parses as binary minus (JLS 15.16: reference-type casts take `UnaryExpressionNotPlusMinus`) — parenthesize the cast operand; (3) generalized-test reports are unmatchable (surefire ≥3.0.2 writes jqwik *display names* as `classname`) or absent entirely (surefire <2.22 has no JUnit-platform provider, silently skipping every jqwik class while the stage reports SUCCEEDED) — normalize the collector's matching, floor pinned surefire versions, and fail loud when generalized classes produce no reports.

**Tech stack:** Java 8, dom4j (pom mutation), Spoon codegen, maven-surefire report parser (`TestSuiteXmlParser`), jqwik `@Example` + `org.junit.Assert` tests.

**Ground rules for every task:**
- Run from the repo root. Do NOT run project-wide formatters or the full test suite unless a step says so; verification commands are given explicitly.
- New tests use `net.jqwik.api.Example` + `org.junit.Assert` (JUnit 4 `@Test` is not discovered — no vintage engine in Teralizer's own suite).
- Never edit anything under `projects/` (read-only submodules). The mutated build file is the *copy* (`pom.teralizer.xml` / custom gradle file) written at pipeline runtime — code changes only.
- Commit your own task's work: one commit per task, Conventional Commit subject plus a prose body explaining *why* (constraint/tradeoff), via `bun ~/.omp/agent/skills/commit/commit-helper.ts` — see each task's commit note. Never `git commit -m` for bodies; never push.
- Dense logic gets a javadoc explaining why (yardstick: `MethodUnderTestResolver`); no planning-doc references in code comments.

---

## Current defects (what is broken and why)

Validation-layer status on `postgres_fusion_spike` (23-project corpus, 2003 generalizations):

| failure class | projects | mechanism |
|---|---|---|
| `BUILD_PROJECT_GENERALIZED` fails, lambda/method-ref `-source` errors | kouchat, jeff, Dicebot, combinatoricslib, gedcom4j, WZWave, octotron, simplecsv, antiaction, xenqtt | generated files inline the jqwik-value-recorder harness (Java 8 syntax); project pins test source < 8 |
| `BUILD_PROJECT_GENERALIZED` fails, `integer number too large` | JadConfig | `Arbitraries.just((java.lang.Long) -9223372036854775808L)` — cast to a reference type re-parses `-` as binary minus, detaching the literal from unary minus |
| `COLLECT_JUNIT_REPORTS_GENERALIZED` fails, "Failed to identify matching test case report" | svdrp4j | surefire 3.0.2 writes the JUnit-platform *display name* as `classname` (`" LSTCTest Generalized … Test"` — underscores→spaces, package stripped); collector compares against the FQN |
| `COLLECT_JUNIT_REPORTS_GENERALIZED` fails, "Unable to identify test report path" — and `EXECUTE_TESTS_GENERALIZED` was a **false green** | MarkupTagScanner | project pins surefire 2.17 (< 2.22 = no JUnit-platform provider); every jqwik class is silently skipped, zero reports written, stage still SUCCEEDED |
| `EXECUTE_TESTS_GENERALIZED` timeout at 60s | TDD-Katas, gwt-commons-codec, htm_java, sparkey-java | stage runs the original included suite (30–46s alone) *plus* all generalized classes under the same uniform 60s ceiling |

## Binding decisions

- **Timeouts stay uniform.** `teralizer.junit.max-execution-time = 60` is not raised, split per-stage, or overridden per-project. Timeout exclusions are expected and recorded as first-class data (Task 5 acceptance + exclusion table in the audit doc). Rationale: individual adjustments bias the corpus; a documented uniform budget is explainable in the paper.
- **Floor, never assign.** The test-compilation level becomes `max(effective, 1.8)`; a project already at 8/11/17 is untouched. Only *test* source/target move — main compilation keeps the project's level.
- **Act only on explicit pins.** If no below-floor level is visible in the build-file copy (inherited from an unavailable parent pom, or plugin default), do nothing — a visible build failure is better than mis-flooring a project whose parent pins 11+.
- **One constant per floor.** The generated-harness language requirement (`1.8`) and the surefire JUnit-platform floor (`2.22.2`) each live once in `Configuration`, referenced by every seam.

---

### Task 1: Test-compilation source floor (Maven + Gradle)

**Files:**
- Modify: `src/main/java/teralizer/util/Configuration.java` (add constants)
- Modify: `src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java`
- Modify: `src/main/java/teralizer/processing/dependencies/GradleDependencyManager.java`
- Create: `src/test/java/teralizer/processing/dependencies/MavenTestCompilerFloorTest.java`

- [ ] **Step 1: Add the constants to `Configuration`** (near the existing `*_DEPENDENCY` constants):

```java
/**
 * Language level the generated test harness (jqwik-value-recorder template) requires.
 * Generated property tests use lambdas and method references, so the *test* compilation
 * of a target project must be at least this level; main compilation is never touched.
 */
public static final String GENERATED_TEST_LANGUAGE_LEVEL = "1.8";
```

- [ ] **Step 2: Write the failing test.** Model: read a pom string with dom4j, run the floor, assert the mutated XML. Cover four cases: (a) plugin config pins `<source>1.5</source>` → `testSource`/`testTarget` set to `1.8`; (b) properties pin `<maven.compiler.source>1.7</maven.compiler.source>` → `maven.compiler.testSource`/`testTarget` properties set to `1.8`; (c) pom at `1.8` → document unchanged; (d) no pin anywhere → document unchanged. Test the new package-private static method directly (no `ProjectRecord` needed):

```java
public class MavenTestCompilerFloorTest {
    @Example
    public void plugin_pin_below_floor_gets_test_floor() throws Exception {
        Document doc = readPom("<source>1.5</source><target>1.5</target>");
        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);
        Assert.assertTrue(changed);
        // testSource/testTarget added to the compiler plugin's <configuration>:
        Assert.assertTrue(doc.asXML().contains("<testSource>1.8</testSource>"));
        Assert.assertTrue(doc.asXML().contains("<testTarget>1.8</testTarget>"));
        // main source untouched:
        Assert.assertTrue(doc.asXML().contains("<source>1.5</source>"));
    }
    // … cases (b)-(d) analogous; helper builds a minimal pom with the
    // maven-compiler-plugin declared under /project/build/plugins.
}
```

- [ ] **Step 3: Run it to verify it fails** — `./gradlew test --tests 'teralizer.processing.dependencies.MavenTestCompilerFloorTest'` → compile error (method missing).
- [ ] **Step 4: Implement `applyTestCompilerFloor(Document)` in `MavenDependencyManager`** as a package-private static method (mirrors the static `updatePitestTargets` precedent). Effective-level detection order: compiler-plugin `<configuration>` `testSource` → `source`, then properties `maven.compiler.testSource` → `maven.compiler.source` → `maven.compiler.release`. Below-floor set: `{1.1…1.7, 5, 6, 7}` (a `release` value is 9+ by definition, never below floor; unknown/absent → return false). Where a below-floor pin was found in plugin config, set/overwrite `testSource`+`testTarget` in that `<configuration>`; where found in properties, set the `maven.compiler.testSource`/`maven.compiler.testTarget` properties. Use `Configuration.GENERATED_TEST_LANGUAGE_LEVEL`. Wire the call into `addRequiredDependencies()` as `hasModifiedDocument |= applyTestCompilerFloor(this.document);` before the write-out at line 55.
- [ ] **Step 5: Run the test to verify it passes.**
- [ ] **Step 6: Gradle parity.** In `GradleDependencyManager`, add `addTestCompilerFloor()` called from `addRequiredDependencies()`, appending (via the existing `appendToBuildFile`, which brackets with the `TOOL_COMMENT` markers) a version-portable block; skip when the build file already contains `compileTestJava`-level compatibility config:

```groovy
tasks.matching { it.name == 'compileTestJava' }.all {
    if (JavaVersion.toVersion(sourceCompatibility) < JavaVersion.VERSION_1_8) {
        sourceCompatibility = '1.8'
        targetCompatibility = '1.8'
    }
}
```

- [ ] **Step 7: Verify each observed-failing project is actually covered.** For each of the 10 lambda-failure projects, confirm its `pom.xml` (read-only, under `projects/`) carries an explicit below-floor pin reachable by Step 4's detection. Any project whose pin is *not* visible (parent-pom inheritance) → note it in the task summary; it stays failing by design (binding decision 3).

**Commit subject:** `fix(pipeline): floor generated-test compilation at the harness language level`

---

### Task 2: Surefire JUnit-platform floor (Maven)

**Files:**
- Modify: `src/main/java/teralizer/util/Configuration.java`
- Modify: `src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java`
- Create: `src/test/java/teralizer/processing/dependencies/MavenSurefireFloorTest.java`

- [ ] **Step 1: Add the constant:**

```java
/**
 * Oldest maven-surefire-plugin able to run JUnit-platform (jqwik) tests. A project pinning
 * an older surefire silently skips every generated property test while the build stays
 * green — the worst failure mode, a false pass.
 */
public static final String SUREFIRE_MIN_VERSION = "2.22.2";
```

- [ ] **Step 2: Write the failing test** (same dom4j-string model as Task 1): (a) `maven-surefire-plugin` pinned `2.17` under `/project/build/plugins` → version text becomes `2.22.2`; (b) pinned under `/project/build/pluginManagement/plugins` → also floored; (c) pinned `3.0.2` → unchanged; (d) no surefire plugin declared → unchanged (Maven's own default is platform-capable).
- [ ] **Step 3: Run to verify failure.**
- [ ] **Step 4: Implement `applySurefireFloor(Document)`** — package-private static; numeric `major.minor.patch` comparison (missing parts = 0); overwrite only the `<version>` text node. Wire into `addRequiredDependencies()` like Task 1.
- [ ] **Step 5: Run to verify pass.**

**Commit subject:** `fix(pipeline): floor pinned surefire to a junit-platform-capable version`

---

### Task 3: Parenthesize cast operands in supplier rendering

**Files:**
- Modify: `src/main/java/teralizer/spoon/generalization/BaselineTestParametersSupplierFactory.java:85`
- Create: `src/test/java/teralizer/spoon/generalization/BaselineSupplierRenderingTest.java` (mirror `NaiveSupplierRenderingTest`)

- [ ] **Step 1: Write the failing test:** a `Long`-typed argument with value `Long.MIN_VALUE` renders a supplier body whose cast operand is parenthesized — `((java.lang.Long) (-9223372036854775808L))` — and an `Integer.MIN_VALUE` case likewise. Assert on the rendered string containing `") (-"` (operand opens with a paren) rather than `") -"`.
- [ ] **Step 2: Run to verify failure** (current rendering emits `(java.lang.Long) -9223372036854775808L`).
- [ ] **Step 3: Fix line 85:**

```java
return "return net.jqwik.api.Arbitraries.just((" + argument.getJavaType() + ") (" + value + "))";
```

- [ ] **Step 4: Run to verify pass.**
- [ ] **Step 5: Sweep for siblings.** `ast_grep`/grep every renderer emitting `just((` + type + `) ` + value (Naive/Improved factories, `InputGenerationPlanner`, `FirstValueArbitrary` template, `jqwik-value-recorder.vm`): any site that can interpolate a *negative numeric literal* after a *reference-type* cast gets the same parenthesization. (`NumericDomainPlanner` already parenthesizes — `(%s) (%s)` — and primitive-keyword casts like `(long)` are immune; both need no change. Null-literal casts are immune.) List swept sites in the task summary.

**Commit subject:** `fix(codegen): parenthesize cast operands so MIN_VALUE literals stay unary-minus operands`

---

### Task 4: Report matching for display-name classnames + fail-loud on silent skips

**Files:**
- Modify: `src/main/java/teralizer/processing/task/JunitDataCollectionTask.java` (`parseTestCaseReports`, lines 362-381)
- Modify: `src/main/java/teralizer/processing/task/TestExecutionTask.java` (`executeInternal`, after line 111)
- Create: `src/test/java/teralizer/processing/task/SurefireReportMatchingTest.java`

Surefire ≥3.0.2 writes JUnit-platform testcases with the *display name* as `classname`: jqwik beautifies `_LSTCTest_Generalized_testWithGroupsAndIds_1995_Test` to `" LSTCTest Generalized testWithGroupsAndIds 1995 Test"` (underscores→spaces, package dropped). The existing `replaceSpaces` restores underscores — including the leading one — but cannot restore the package, so the exact-equality check at lines 369-373 fails.

- [ ] **Step 1: Extract a testable predicate.** Add package-private static methods to `JunitDataCollectionTask`:

```java
/**
 * A surefire report identifies a testcase either by fully-qualified name (surefire < 3.0.2,
 * and all vintage-engine tests) or — for JUnit-platform tests since surefire 3.0.2 — by the
 * engine's display name, which jqwik beautifies by replacing underscores with spaces and
 * dropping the package. After space→underscore normalization the display-name shape is the
 * package-less suffix of the expected qualified name; the '.' boundary keeps simple-name
 * collisions from matching.
 */
static boolean matchesQualifiedName(String expectedQualifiedName, String normalizedReportName) {
    return expectedQualifiedName.equals(normalizedReportName)
        || expectedQualifiedName.endsWith("." + normalizedReportName);
}
```

- [ ] **Step 2: Write the failing test** (string-level, no XML needed):

```java
public class SurefireReportMatchingTest {
    @Example
    public void fqn_shape_matches_exactly() {
        Assert.assertTrue(JunitDataCollectionTask.matchesQualifiedName(
            "org.x._FooTest_Generalized_bar_1_Test.bar", "org.x._FooTest_Generalized_bar_1_Test.bar"));
    }
    @Example
    public void display_name_shape_matches_as_package_less_suffix() {
        Assert.assertTrue(JunitDataCollectionTask.matchesQualifiedName(
            "org.hampelratte.svdrp.commands._LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds",
            "_LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds"));
    }
    @Example
    public void simple_name_collision_does_not_match() {
        Assert.assertFalse(JunitDataCollectionTask.matchesQualifiedName(
            "org.x.OtherTest.bar", "Test.bar"));
    }
}
```

- [ ] **Step 3: Run to verify the method is missing; implement; wire** both filter branches of `parseTestCaseReports` through it (method-name branch keeps the `replaceAll("\\(.*", "")` paren-stripping before the call; class-name branch normalizes the report's `getFullClassName()` through `replaceSpaces` first — display-name shapes never carry a package, FQN shapes pass through `endsWith` unaffected... they equal-match first).
- [ ] **Step 4: Run to verify pass.**
- [ ] **Step 5: Fail loud on silently-skipped generalized classes.** In `TestExecutionTask.executeInternal`, after the `consoleCommand.execute(...)` try/catch for `EXECUTE_TESTS_GENERALIZED` only: the generalized class list from line 62 is non-empty (guaranteed by line 68), so require that at least one report file for a generalized class exists under `this.projectRecord.getTestReportsPath()` — file name containing `Generalized` (both surefire naming shapes — FQN and space-mangled — preserve that token). Zero such files ⇒ `throw new RuntimeException("Test execution reported success but produced no reports for any generalized test class. The project's test runner likely cannot run JUnit-platform tests (surefire < 2.22); refusing to record a false pass.")`. Guard `Files.exists(reportsPath)` first.
- [ ] **Step 6: Regression-verify matching against real fixtures.** Copy two real report XMLs (one FQN-shape from unicrypt, one display-name-shape from svdrp4j — sanitize paths) into `src/test/resources/surefire-reports/` and add an `@Example` running the full `TestSuiteXmlParser` + filter path over them.

**Commit subject:** `fix(pipeline): match display-name surefire reports and refuse false-green generalized runs`

---

### Task 5: Spike re-run + acceptance (operator-assisted)

Full re-run against the fixed binary; this run is also `2026-07-02-static-mut-id-fusion` Task 11 Step 1's rerun (one run serves both plans).

**Files:** none (disposable DB). Results recorded in `docs/plans/2026-06-28-mut-id-targeting-and-coverage.md`.

- [ ] **Step 1:** `./gradlew build` green (full gate, 292+ tests), commits from Tasks 1-4 in place.
- [ ] **Step 2:** Re-run the spike with a fresh DB (don't touch `src/` during the run; no heavy concurrent CPU):

```
REPOREAPERS_DB=postgres_fusion_spike REPOREAPERS_DATA_DIR=data/fusion-spike \
REPOREAPERS_CONFIG_DIR=project-configs/fusion-spike scripts/run-reporeapers-rerun.sh --reset-db
```

- [ ] **Step 3: Acceptance.**
  - Census stability: total generalizations ≈ 2003 and per-project counts match the previous spike run (extraction was untouched; any drift ⇒ investigate before proceeding).
  - `BUILD_PROJECT_GENERALIZED` failures from `-source`/literal errors: 0 (was 11 projects). Projects excluded by binding decision 3 (invisible parent-pom pin), if any, are named and explained.
  - svdrp4j + MarkupTagScanner: per-generalization `COLLECT_JUNIT_REPORTS_GENERALIZED` succeed; both projects have `jqwik_property_execution` rows.
  - Timeout exclusions: recorded per project (expect TDD-Katas, gwt-commons-codec, htm_java, sparkey-java unless faster builds change the margin), with `EXECUTE_TESTS_INITIAL` runtime alongside — the exclusion-accounting table for the audit doc and the paper.
  - `jqwik_property_execution` coverage: reported as gens-with-rows / total and projects-with-rows / projects-with-gens; expected ≥ 13/19 projects.
- [ ] **Step 4:** Hand the DB to `2026-07-02-static-mut-id-fusion` Task 11 Step 5 (seed-kill share) and Step 7 (recording); tick Task 11 Step 1 there.

---

## Self-review

- **Coverage:** every failure class in the defect table maps to a task (lambda floor → 1; literal → 3; display-name → 4; silent skip → 2 + 4 Step 5; timeouts → binding decision + Task 5 exclusion accounting).
- **Type consistency:** `Configuration.GENERATED_TEST_LANGUAGE_LEVEL` (Tasks 1) and `Configuration.SUREFIRE_MIN_VERSION` (Task 2) are the only new symbols shared across files; both defined in Task 1/2 Step 1 before use.
- **No placeholders:** each step names exact files/lines and carries the code or the exact assertion target.
