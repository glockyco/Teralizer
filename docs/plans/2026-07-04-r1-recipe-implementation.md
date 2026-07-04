---
title: R1 Expression-Slice Implementation
type: plan
status: active
created: 2026-07-04
parent: 2026-07-04-r1-expression-slice-recipes
---

# R1 Expression-Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The unit of generalization becomes the asserted actual expression: recipe schema v2 records the expression, its type, and path-located input sites; instrumentation emits the expression as the wrapper body; capture happens at the wrapper exit; the return-type gate reads the expression type.

**Architecture:** Recipe v2 is the only schema (clean cut, no dual-version reads). T0/T1 shapes — the actual expression IS the resolved call — flow through the existing index-keyed machinery unchanged; expression shapes activate a path-based site rewrite used by both instrumentation and generalized-test creation. The admit-list screen (`ExpressionSliceScreen`) is the structural soundness boundary. Design authority: `2026-07-04-r1-expression-slice-recipes`.

**Tech stack:** Java 8, Spoon AST (`CtExpression`, `CtPath`), jqwik `@Example` tests + `org.junit.Assert` for Teralizer's own suite, verification fixture corpus.

**Verification discipline:** fixture corpus for iteration (`--only expression-slice`), `scripts/verify-pipeline.sh` twice for done, sentinel subset before the final commit (census must be unchanged). Golden values are recorded from observation, never predicted. First-run numbers stand.

---

## Task 1: Recipe schema v2

**Files:**
- Modify: `src/main/java/teralizer/spoon/analysis/GeneralizationRecipe.java`
- Test: `src/test/java/teralizer/spoon/analysis/GeneralizationRecipeTest.java` (extend)

- [x] **Step 1: Write failing tests** (jqwik `@Example`, `org.junit.Assert`):
  - `CURRENT_VERSION` is 2 and `fromJson` rejects a version-1 payload with `IllegalArgumentException`.
  - A recipe built from an expression (`from(oracleMethod, someCtExpression, inputs, expressionType)`) round-trips through `toJson`/`fromJson` preserving `oracleExpressionType`.
  - `resolveAgainst` returns the oracle as `CtExpression` for a binary-operator expression path (build a small Spoon model with `Launcher`/`VirtualFile`; note virtual files have no `SourcePosition.getFile()` — path-based assertions only, per AGENTS.md).
- [x] **Step 2: Run, expect FAIL.** `./gradlew test --tests 'teralizer.spoon.analysis.GeneralizationRecipeTest'`
- [x] **Step 3: Implement.**
  - `CURRENT_VERSION = 2`.
  - Field `private final String oracleExpressionType;` serialized alongside `oracleType`; constructor, `fromJson` (null-check it like the other fields), `toJson` unchanged mechanics.
  - `from(...)` signature becomes `from(CtMethod<?> oracleMethod, CtExpression<?> oracleExpression, List<GeneralizableInput> inputs, String oracleExpressionType)`; the path derivation (`getPath().relativePath(containingMethod)`) already works on any `CtElement`.
  - `resolveAgainst` resolves `oracleExpressionPath` with target class `CtExpression.class` (was `CtInvocation.class`); `Resolved` carries `CtExpression<?> oracleExpression`.
  - Update ALL existing callers in the same commit (`TestAnalysisTask.java:147` passes the tested call as the expression and `testedMethod.getType()` as the expression type for T0/T1; `JpfInstrumentationTask`/`TestGeneralizationTask` consumers adjust to `CtExpression` and cast where they genuinely need the invocation — mark each such cast site, Tasks 5/7 remove them).
- [x] **Step 4: Run, expect PASS**, plus `./gradlew test --tests 'teralizer.spoon.analysis.*'` and the existing recipe consumers' tests (`./gradlew test --tests 'teralizer.processing.*'`).
- [x] **Step 5: Commit.** `feat(recipe): move schema to v2 with expression oracle`

---

## Task 2: `ExpressionSliceScreen` — the structural admit-list

**Files:**
- Create: `src/main/java/teralizer/spoon/analysis/ExpressionSliceScreen.java`
- Test: `src/test/java/teralizer/spoon/analysis/ExpressionSliceScreenTest.java`

- [x] **Step 1: Write failing tests.** Build expressions in a Spoon model (`Launcher` + `VirtualFile`) and assert `ExpressionSliceScreen.isSelfContained(expr)`:
  - admitted: literal; static call with literal args; ctor call; binary operator over two static calls; unary negation; cast of a call; chained instance call whose root receiver is a static-factory call (`Box.of(5).value()`).
  - rejected: variable read; field read; array access; lambda; call whose root receiver is a variable (`c.compare(a, b)`); assignment.
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement.** A recursive structural check:

```java
public static boolean isSelfContained(CtExpression<?> expression) {
    if (expression instanceof CtLiteral) {
        return true;
    }
    if (expression instanceof CtConstructorCall) {
        return ((CtConstructorCall<?>) expression).getArguments().stream()
            .allMatch(ExpressionSliceScreen::isSelfContained);
    }
    if (expression instanceof CtInvocation) {
        CtInvocation<?> invocation = (CtInvocation<?>) expression;
        CtExpression<?> target = invocation.getTarget();
        boolean targetOk = target == null
            || target instanceof CtTypeAccess
            || isSelfContained(target);
        return targetOk && invocation.getArguments().stream()
            .allMatch(ExpressionSliceScreen::isSelfContained);
    }
    if (expression instanceof CtBinaryOperator) {
        CtBinaryOperator<?> op = (CtBinaryOperator<?>) expression;
        return isSelfContained(op.getLeftHandOperand()) && isSelfContained(op.getRightHandOperand());
    }
    if (expression instanceof CtUnaryOperator) {
        return isSelfContained(((CtUnaryOperator<?>) expression).getOperand());
    }
    // Casts are attributes of the inner expression in Spoon; a cast-wrapped call arrives as
    // the call with a non-empty getTypeCasts() list, so no separate node case exists.
    return false;
}
```

  Verify the cast claim against Spoon's actual model in the test (a `(long) call()` expression) and adjust if Spoon materializes a distinct node.
- [x] **Step 4: Run, expect PASS.**
- [x] **Step 5: Commit.** `feat(analysis): add the expression-slice admit screen`

---

## Task 3: Expression-site derivation + analysis wiring

**Files:**
- Modify: `src/main/java/teralizer/spoon/analysis/GeneralizableInput.java`
- Modify: `src/main/java/teralizer/processing/task/TestAnalysisTask.java:143-148` (recipe branch)
- Test: `src/test/java/teralizer/spoon/analysis/GeneralizableInputExpressionTest.java`

- [x] **Step 1: Write failing tests.** For an admitted composite (`intCompare(4, 1) > 0` in a Spoon model):
  - `GeneralizableInput.deriveFromExpression(expression)` returns two sites (the literals `4` and `1`), typed `int`, with deterministic distinct names.
  - Literal operands of the operator itself (`> 0`'s right side... the `0`) are NOT lifted — only call/ctor argument positions become sites (the spec's screen rule).
  - For a non-admitted expression the method is never consulted (screen gate lives in the caller); for an expression that IS a lone invocation, derivation falls back to `derive(testedMethod, call)` output (same sites, same names) so T0/T1 recipes are unchanged.
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement `deriveFromExpression`.** Walk the expression; for every `CtInvocation`/`CtConstructorCall` argument position holding a `CtLiteral` of a `TypeCapability.supportsGeneratedInput` type, create a `GeneralizableInput` whose `sourceExpression` is that literal (indices set to a new `EXPRESSION_SITE` sentinel `-2`; path is what locates it downstream). Name sites `site0`, `site1`, … in traversal order, `sanitize`d like ctor inputs.
- [x] **Step 4: Wire analysis.** In `TestAnalysisTask`, after MUT resolution succeeds: locate the assertion's actual expression (`TestAnalysis.getActualParameterIndex(assertion)` → `assertion.getArguments().get(idx)`; for `assertTrue`/`assertFalse` the condition argument). If that expression is NOT the tested call itself AND `ExpressionSliceScreen.isSelfContained(actualExpression)` AND it contains the resolved tested call: derive sites from the expression, build the recipe with the expression + its `getType().getQualifiedName()` as `oracleExpressionType`. Otherwise: today's exact path (recipe from the tested call, expression type = method return type).
- [x] **Step 5: Run, expect PASS**, plus `./gradlew test --tests 'teralizer.processing.task.*'`.
- [x] **Step 6: Commit.** `feat(analysis): derive expression-slice recipes behind the admit screen`

---

## Task 4: `ReturnTypeFilter` gates on the expression type

**Files:**
- Modify: `src/main/java/teralizer/processing/filter/ReturnTypeFilter.java`
- Modify: whatever populates the filter's input — the filter reads `assertionRecord`; add the recipe's `oracleExpressionType` to the assertion record write in `TestAnalysisTask` (column `tested_method_return_type` stays; new column NOT needed — the filter reads the recipe JSON via `assertionRecord.getGeneralizationRecipe()`).
- Test: `src/test/java/teralizer/processing/filter/ReturnTypeFilterTest.java` (extend or create following the existing filter-test pattern; check `src/test/java/teralizer/processing/filter/` for the harness style first)

- [x] **Step 1: Write failing tests.** An assertion record whose recipe carries `oracleExpressionType = "boolean"` but `tested_method_return_type = "int"` → ACCEPT (expression type wins). Recipe absent → today's behavior (DEFER on null return type, etc.).
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement.** Parse the recipe (null-safe) at the top of `check()`; when present use `recipe.getOracleExpressionType()` in place of `getTestedMethodReturnType()` for the void/support checks (keep the DEFER branch for a null expression type).
- [x] **Step 4: Run, expect PASS.**
- [x] **Step 5: Commit.** `feat(filter): gate return type on the recipe's expression type`

---

## Task 5: Instrumentation — expression wrapper body

**Files:**
- Modify: `src/main/java/teralizer/processing/task/JpfInstrumentationTask.java:194-294` (`createInstrumentedMethod`)
- Test: extend the existing instrumentation test class (find it via `./gradlew test --tests 'teralizer.processing.task.JpfInstrumentation*'` and read its harness before writing)

- [x] **Step 1: Write failing tests.** For an expression recipe (`intCompare(4,1) > 0`, sites at the two literals): the instrumented method's body is `return (ExpressionSliceCut.intCompare(site0, site1) > 0)` (sites replaced by parameter reads), return type `boolean`, parameters `(int site0, int site1)`.
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement.** When the recipe is expression-shaped (sites carry the `EXPRESSION_SITE` sentinel): clone the oracle expression, resolve each site's path inside the clone, `CtExpression#replace` it with a snippet reading the parameter name, emit `return <clone>`; wrapper return type from `oracleExpressionType`. Index-keyed logic (lines 234-275) remains the T0/T1 path, untouched.
- [x] **Step 4: Run, expect PASS**, plus the full instrumentation test class.
- [x] **Step 5: Commit.** `feat(instrumentation): emit expression bodies for slice recipes`

---

## Task 6: Listener — wrapper-exit capture, target-entered as observation

**Files:**
- Modify: `src/main/java/teralizer/jpf/TestGeneralizationListener.java:158-171` (`methodExited`), `:145-147`, config plumbing at the constructor
- Modify: `src/main/java/teralizer/jpf/ExtractionOutcome.java` (`fromState`)
- Modify: `src/main/resources/templates/jpf-config.vm` + `JpfInstrumentationTask` config write (new boolean `test_generalization.expression_recipe`)
- Test: `src/test/java/teralizer/jpf/` — extend the `JpfListenerHarness` tests with a target whose wrapper computes a composite of two helper calls (pattern: existing targets in `src/test/java/teralizer/jpf/targets/`)

- [x] **Step 1: Write failing tests.**
  - Expression mode: capture fires at the instrumented-wrapper exit; the input model reflects the whole expression's PC; a wrapper whose focal helper is short-circuited past still extracts (target-entered=false is not a failure).
  - Invocation mode (flag off): existing capture semantics byte-identical — rerun `TestGeneralizationListenerCaptureTest` + `...SymbolicTest` unmodified.
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement.** New config key read in the constructor. In expression mode the pinned frame is the *instrumented* method (`instrumentedMethodSpec`) instead of the tested method — the depth-pinning and single-capture logic transfer verbatim; `ExtractionOutcome.fromState` gains the mode so `TARGET_NOT_ENTERED` only fires for invocation mode; the listener still records `wasTargetEntered` (it lands in task info for telemetry).
- [x] **Step 4: Run, expect PASS** for the whole `teralizer.jpf.*` suite.
- [x] **Step 5: Commit.** `feat(listener): capture at the wrapper exit for expression recipes`

---

## Task 7: Generalized-test creation — path-based site rewrite

**Files:**
- Modify: `src/main/java/teralizer/processing/task/TestGeneralizationTask.java:503-522` (argument-replacement region)
- Test: fixture-level (Task 8) + extend the task's unit tests only if a seam is unit-testable without Spoon-file positions

- [x] **Step 1: Implement** (fixture-verified): when the recipe is expression-shaped, resolve each site path inside the cloned test method's asserted expression and replace it with `_p_.<name>` — same mechanism as Task 5's wrapper rewrite; reuse one shared helper (put it on `GeneralizationRecipe.Resolved` so both consumers call it). The index-keyed T0/T1 replacement stays.
- [x] **Step 2: Run** `./gradlew build` (compile + unit green).
- [x] **Step 3: Commit.** `feat(generation): rewrite expression sites from recipe paths`

---

## Task 8: `expression-slice` fixture + golden

**Files:**
- Create: `verification/fixtures/expression-slice/` (pom mirrors `verification/fixtures/string-sound-set/pom.xml`; package `teralizer.verification.expressionslice`)
- Create: `project-configs/verification/fixture-expression-slice.conf`
- Create: `verification/golden/expression-slice.tsv`

- [x] **Step 1: CUT + test.** `ExpressionSliceCut` with the spike's helpers (`intCompare`, `timesTwo`, `buildList`, plus `Box`/`Pair` as in `verification/spikes/r1-viability/`); test methods asserting expressions DIRECTLY (the spike wrapped them — this fixture exercises the real seam):

```java
assertTrue(ExpressionSliceCut.intCompare(4, 1) > 0);
assertTrue(new Pair(2).compareTo(new Pair(5)) < 0);
assertFalse(new Pair(1, 4).equalsPair(new Pair(1, 5)));
assertEquals(12L, (long) ExpressionSliceCut.timesTwo(6));
assertEquals(14, ExpressionSliceCut.timesTwo(3) + ExpressionSliceCut.timesTwo(4));
assertEquals(10, Box.of(5).twice().value());
assertEquals(3, ExpressionSliceCut.buildList(3).size());
```

  (`equalsPair` note: the ctor-equality arm stays a project method per the spec's real-`equals` exclusion.)
- [x] **Step 2: Run** `scripts/run-verification-corpus.sh --only expression-slice`; inspect the DB; the expected SHAPE from the spike: composites/compareTo/equalsPair included via license, cast/arithmetic/chain included SYMBOLIC, `buildList(...).size()` refused `ORACLE_NOT_WIDENABLE`. Record OBSERVED values in the golden. Any contradiction with the spike shape → STOP, investigate, escalate with evidence.
- [x] **Step 3: Commit.** `feat(verification): pin expression-slice recipes with a fixture golden`

---

## Task 9: Full gates

- [ ] **Step 1:** `scripts/verify-pipeline.sh` twice — green and identical, the nine pre-existing goldens unmoved.
- [ ] **Step 2:** `./gradlew build` green.
- [ ] **Step 3:** Sentinel subset (scratch DB per AGENTS.md) — census identical to the headers' pinned values (sentinels contain no currently-generalizing R1 shapes; a shift means an unintended T0/T1 behavior change). First-run numbers stand; a runtime-limit failure is recorded, not rerun.
- [ ] **Step 4:** Commit any golden/doc deltas; `omp-plans complete 2026-07-04-r1-expression-slice-recipes` and `omp-plans complete 2026-07-04-r1-recipe-implementation` only after all gates pass.

---

## Self-review

- Spec coverage: schema v2 (T1), admit screen (T2), derivation + analysis (T3), filter (T4), instrumentation (T5), capture (T6), generation (T7), fixture acceptance (T8), no-regression gates (T9). The spec's "no dual-version" is T1 (version bump + reject); "focal-entered observation" is T6.
- Clean cut: v1 recipes unreadable by design; the only preserved "old path" is the index-keyed T0/T1 machinery, which is the supported production path, not legacy.
- Order matters: T1 breaks compilation of consumers if done naively — its Step 3 updates all callers in the same commit; T5/T7 then replace the marked casts.
