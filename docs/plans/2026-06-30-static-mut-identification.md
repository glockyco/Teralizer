---
title: Static MUT Identification
type: plan
status: draft
created: 2026-06-30
parent: 2026-06-26-teralizer-overview
---

# Static MUT Identification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise method-under-test (MUT) identification recall on the dominant `MissingValue` applicability blocker *while bounding mis-targeted picks* — a wrong pick silently misses the intended method's regressions; abstain-on-ambiguity plus manual sampling (Tasks 1, 7) reduce and monitor it, but zero is not guaranteeable without labels — using AST-only (Spoon) analysis, no mutation testing.

**Architecture:** Replace the one-level LCBA in `TestAnalysis.findTestedMethodCall` with a **precedence cascade that resolves the asserted value to its single producing production-call and abstains on any ambiguity**. Three mechanisms, in order: (M1) resolve the asserted value through sub-expressions, variable/field definitions, and inspector wrappers to the deepest producer call; (M2) a class-under-test (CUT) veto that turns a low-confidence pick into an abstention; (M3) a fixed cascade whose only fallback is *abstain* (never a best guess). This is the static subset of `2026-06-27-ensemble-mut-identification`; the future killed-mutant oracle slots in as the top-precedence signal without redesign.

**Tech stack:** Java 8, Spoon AST (`spoon.reflect.*`), JUnit tests under `src/test/java`.

**Observability companion:** `2026-07-01-pipeline-observability-telemetry` defines the
MUT-resolution provenance and candidate type-eligibility facts that make this plan's diagnostic
steps queryable. Task 1 should consume those facts when they exist; if static MUT-id lands first,
its resolver should be shaped so the same provenance can be emitted later without redesign.

---

## Targeting-validity invariants (non-negotiable — every task preserves these)

1. **Prefer abstain over a low-confidence pick — a *targeting* policy, not a soundness claim.** A resolver failure returns `Optional.empty()` ⇒ `TestAnalysisTask` leaves `tested_*` null ⇒ `MissingValueFilter` rejects (safe exclusion). Abstaining avoids a mis-targeted test, but every abstention that was itself wrong is a **spurious exclusion** — pure recall loss, and recall (`MissingValue` ≈ half the corpus) is the dominant bottleneck. So abstention is reserved for genuine ambiguity and shallow-inspector cases and is empirically calibrated (Tasks 1 and 7); it is never a blanket "when unsure, drop."
2. **Wrong-MUT is silent *mis-targeting*, not a soundness failure.** Verified in `TestGeneralizationTask.generalizeTest`: the expected side is an **independent** SPF-derived output expression (`outputJava = ModelToJavaTransformer.transform(outputModel)`, `:313`, `:358-373`) and the actual side re-invokes the **picked** method with generated inputs (`:480-508`), under `@Property(seed=0, edgeCases=FIRST)` which injects the original concrete inputs first (`:423-426`). So:
   - *Incoherent pick* — the picked method's output is **not** the asserted value: expected ≠ actual on the injected seed → the property fails on the first edge case → `NonPassingTestFilter` **excludes it** (strong, not total: a coincidental seed match falls back to random sampling). Not silent.
   - *Coherent pick* — the picked method's output **is** the asserted value on the path: a valid, sound regression test for the picked method (passes on correct code, kills its own mutations). The residual harm is **invisible mis-attribution** — it misses regressions of the *intended* method and over-credits shallow inspectors/getters (He et al.); the gate cannot flag it.
   No `argmax`/best-effort fallback is permitted — it manufactures coherent-shallow mis-targets the gate cannot catch — but this is a **validity/quality** guard, not a soundness one.
3. **Ambiguity ⇒ abstain, gated on evidence.** More than one candidate producer, a cross-method-boundary slice, or an unresolved receiver type resolve to abstain. Any *new* veto that can reduce recall (the M2 CUT veto, inspector-unwrap abstention) lands **only after** Task 1 sizes the wrong-pick surface, with Task 7 manual sampling confirming it removes mis-targets rather than correct picks.
4. **No regression of the working corpus.** The ~250 currently-sound generalizations (direct-call and one-hop local-variable cases) must stay identified and sound; the cascade must reproduce those picks exactly.

## Background (verified at source)

- `src/main/java/teralizer/spoon/analysis/TestAnalysis.java:88-177` — `findTestedMethodCall`: `assertThrows` → last `CtInvocation` in the lambda body (crude); otherwise the asserted `actual` (via `getActualParameterIndex`) is returned if it is a direct `CtInvocation`, or a local-variable read is back-scanned one hop to a write whose RHS is a direct `CtInvocation`. `CtFieldRead` (`:121-122`) and non-local variables (`:125-127`) are `@TODO` → empty. A chained inspector `sut.compute(x).isEmpty()` returns the **outer** `isEmpty()`; a zero-arg inspector has no generalizable input so this shape is usually filtered downstream — the silent case that survives is a *coherent-but-shallow* pick (an inspector/getter whose value the assertion does check).
- `src/main/java/teralizer/processing/task/TestAnalysisTask.java:115-177` — consumes the resolved `CtInvocation`; if `testedMethodCall.getExecutable().getDeclaration()` is null (library / unresolved) the `tested_*` fields that need the declaration stay null.
- `src/main/java/teralizer/processing/task/TestGeneralizationTask.java:313,358-373,480-508` — the generated property compares an **independent** SPF-derived output expression (expected) against a re-invocation of the **picked** method with generated inputs (actual); `:423-426` sets `@Property(seed=0, edgeCases=FIRST)`. This is why an *incoherent* pick self-excludes on the seed while a *coherent* pick is a valid test of whatever method was picked.
- `src/main/java/teralizer/processing/filter/MissingValueFilter.java:16-41` — rejects when any of `testedFilePath` / `testedClassName` / `testedMethodName` / `testedMethodParameters` is null.
- `src/main/java/teralizer/spoon/SpoonFactory.java:11-29` + `SpoonModelBuildingTask.java:25` — the launcher already receives `projectRecord.getClasspath()` (`.jar` entries → `setSourceClasspath`), so **receiver-type references resolve against project dependencies** even when a library method has no source declaration. Type resolution (needed by M1c/M2) is available; source *method* declarations exist only for in-source types.
- There is **no** existing unit test for `TestAnalysis` (glob of `src/test/**/*TestAnalysis*` is empty). MUT-id is currently untested; this plan introduces the test file.

## File map

- Create: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java` — the cascade + mechanisms, extracted from `TestAnalysis` so the logic is unit-testable in isolation and `TestAnalysis` stays a thin facade.
- Modify: `src/main/java/teralizer/spoon/analysis/TestAnalysis.java` — `findTestedMethodCall` delegates to `MethodUnderTestResolver.resolve(...)`.
- Modify: `src/main/java/teralizer/processing/task/TestAnalysisTask.java` — record a distinct reason when a producer call resolves but its declaration is a shadow-library type (so it is a *typed* exclusion, not counted as an unidentified `MissingValue`).
- Create: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java` — Spoon-model-based unit tests for every cascade branch.

## Tasks

### Task 1: Empirical bucketing of the MissingValue blocker (diagnostic, no production code)

Size each lever before building; confirm the extracted-but-unresolved slice split before treating any of it as reclassification.

**Files:** none (SQL against a disposable DB — the 20-project spike `postgres_reporeapers_rerun`, or `postgres_test`; never a core DB).

- [ ] **Step 1: Bucket first-reject MissingValue assertions** into (a) *no producer visible* (no call in the asserted slice), (b) *producer visible but not extracted* (a call exists but `findTestedMethodCall` returns empty), (c) *extracted but unresolved* (`getDeclaration()` null). Use `assertion` + `filter_result` (first-reject via min `filter_result.id` per assertion, per `analysis/.../applicability_priorities.py`).
- [ ] **Step 2: Split bucket (c)** into *shadow-library* (declaring type resolves to a classpath `.jar`, package outside the project source roots) vs *unresolved-source/CUT* (declaring type is in a project source package but the declaration is still null). Distinguish by comparing `tested_class_name`/receiver package against the project's source roots and classpath entries.
- [ ] **Step 3: Record the counts** in `2026-06-28-mut-id-targeting-and-coverage` (audit) as the sizing baseline. Acceptance: each of Task 3–6 has a quantified expected surface; if bucket (c) unresolved-source is non-trivial, add a follow-up task for classpath/receiver-resolution rather than assuming it is all library.

### Task 2: Extract the cascade skeleton with explicit abstain (no behavior change)

Introduce `MethodUnderTestResolver.resolve(CtMethod<?> testMethod, CtInvocation<?> assertion): Optional<CtInvocation<?>>` reproducing today's picks exactly, then delegate. This is a pure refactor + characterization tests.

**Files:**
- Create: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java`
- Create: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java`
- Modify: `src/main/java/teralizer/spoon/analysis/TestAnalysis.java:88-177`

- [ ] **Step 1: Write characterization tests** for the currently-working cases, building a Spoon model from source snippets with `spoon.Launcher` (see the pattern in `src/test/java/teralizer/jqwik/planning/InputGenerationPlannerTest.java` for in-test model building). Cases: `assertEquals(3, gcd(a,b))` → `gcd`; `int r = gcd(a,b); assertEquals(3, r);` → `gcd`; `assertTrue(isPrime(n))` → `isPrime`; `assertThrows(E.class, () -> parse(s))` → `parse`.

```java
// MethodUnderTestResolverTest.java (sketch of one case)
@Test
void directInvocationInActualPosition_resolvesToThatCall() {
    CtMethod<?> test = testMethodFrom("void t(){ org.junit.Assert.assertEquals(3, Math2.gcd(6,9)); }", /* + Math2 source */);
    CtInvocation<?> assertion = firstAssertion(test);
    Optional<CtInvocation<?>> mut = MethodUnderTestResolver.resolve(test, assertion);
    assertTrue(mut.isPresent());
    assertEquals("gcd", mut.get().getExecutable().getSimpleName());
}
```

- [ ] **Step 2: Run tests, expect FAIL** (`MethodUnderTestResolver` absent). Run: `./gradlew test --tests 'teralizer.spoon.analysis.MethodUnderTestResolverTest'`.
- [ ] **Step 3: Move the existing logic** from `TestAnalysis.findTestedMethodCall` into `MethodUnderTestResolver.resolve` verbatim (assertThrows-last-call, actual-index, direct-invocation, one-hop local-var back-scan, the `getDeclaration` producer-only fallback). Keep the method signature returning `Optional<CtInvocation<?>>`.
- [ ] **Step 4: Delegate** — `TestAnalysis.findTestedMethodCall` becomes `return MethodUnderTestResolver.resolve(method, assertion);`.
- [ ] **Step 5: Run tests, expect PASS.** Run the same command.
- [ ] **Step 6: Commit.** `refactor: extract MethodUnderTestResolver from TestAnalysis`

### Task 3: M1a — transitive local-variable + field-read resolution (recall, safe)

Resolve the asserted value through variable copies and field writes to the single producing call; abstain on multiple definitions.

**Files:** `MethodUnderTestResolver.java`, `MethodUnderTestResolverTest.java`.

- [ ] **Step 1: Write failing tests.**
  - `int a = foo(); int b = a; assertEquals(3, b);` → `foo`.
  - `this.r = foo(); assertEquals(3, this.r);` → `foo`.
  - `int x = foo(); x = bar(); assertEquals(3, x);` → **abstain** (two producers for `x`).
  - `assertEquals(3, this.field);` with no in-method write of `field` → **abstain** (write is out of scope; do not guess).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement `resolveProducers(CtExpression, CtMethod): Set<CtInvocation>`** as a bounded backward walk over the test method's statements (reuse the existing statement-index scan): for a `CtVariableRead`/`CtFieldRead`, collect *every* write to that variable/field before the assertion; if exactly one write and its RHS is an expression, recurse on the RHS; zero or >1 writes ⇒ empty set (abstain). Follow variable→variable copies transitively (bounded by statement count; guard against cycles with a visited set). Field reads: match `CtFieldWrite` whose target field reference equals the read's field reference within the same test method body (setup-method fields are out of scope for v1 → abstain).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit.** `feat: resolve MUT through variable copies and field writes`

### Task 4: M1b — sub-expression descent (recall, safe)

Descend through casts / unary / binary operators to the single producing sub-call.

**Files:** `MethodUnderTestResolver.java`, `MethodUnderTestResolverTest.java`.

- [ ] **Step 1: Write failing tests.**
  - `assertTrue(foo(n) > 0)` → `foo`.
  - `assertEquals(5, (int) foo(n))` → `foo`.
  - `assertEquals(5, foo(n) + bar(n))` → **abstain** (two producer sub-calls; the assertion constrains a composite, not a single MUT).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Extend `resolveProducers`** for `CtBinaryOperator` (union of operands' producers), `CtUnaryOperator` and `CtTypeCastExpression` (producers of the operand). The cascade returns a MUT only when the producer set has size exactly 1.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit.** `feat: descend sub-expressions to the producing call`

### Task 5: M1c — inspector unwrapping (mis-targeting fix)

When the asserted value is a pure inspector call on a computed receiver, the MUT is the receiver-producing call; the inspector is part of the oracle.

**Files:** `MethodUnderTestResolver.java`, `MethodUnderTestResolverTest.java`.

- [ ] **Step 1: Write failing tests.**
  - `assertTrue(sut.compute(x).isEmpty())` where `sut`/`compute` resolve to the CUT → `compute` (not `isEmpty`).
  - `assertEquals(2, build(x).size())` → `build`.
  - `assertTrue(list.isEmpty())` where `list` is a plain field/param, receiver is **not** a producer call → resolve `list`'s producer if any, else **abstain** (do not pick `isEmpty`).
  - Inspector whose receiver type does not resolve → **abstain**.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement `isInspector(CtInvocation)`** (Ghafari mutator/inspector, ICST'15): zero-argument, non-`void` return, and either a name in a conservative inspector set (`isEmpty`, `size`, `length`, `get*`, `is*`, `has*`, `toString`, `hashCode`, `name`, `ordinal`, `value`) or a declaring type in the JDK collection/`Optional`/wrapper packages. In `resolveProducers`, when a `CtInvocation` is an inspector **and** its receiver is itself a `CtInvocation`, recurse on the receiver instead of returning the inspector. Only unwrap when the receiver call's declaring type resolves (needed for Task 6's veto); otherwise abstain.
- [ ] **Step 4: Run, expect PASS.** Confirm the plain-field inspector case abstains rather than picking `isEmpty`.
- [ ] **Step 5: Commit.** `feat: unwrap inspector calls to the receiver-producing MUT`

### Task 6: M2 — CUT veto + shadow-library reclassification (precision guard + honest taxonomy)

A resolved MUT is accepted only if its declaring type is the class-under-test or a resolvable collaborator; a shadow-library MUT becomes a distinct typed exclusion rather than an unidentified `MissingValue`.

> **Recall-reducing — evidence-gated.** This veto can turn a would-be pick into an abstention, so it lands **only after** Task 1 sizes the wrong-pick surface and is validated by Task 7's manual sample (it must remove mis-targets, not correct picks). If Task 1 shows the wrong-pick surface is small, prefer keeping the M1 single-producer pick over adding the veto.

**Files:** `MethodUnderTestResolver.java`, `MethodUnderTestResolverTest.java`, `src/main/java/teralizer/processing/task/TestAnalysisTask.java`.

- [ ] **Step 1: Write failing tests.**
  - CUT resolution: test class `FooTest`/`FooTests`/`FooIT` → focal type `Foo` (naming-derived, resolved against the model). When naming yields no resolvable focal type, the veto is inconclusive (the single-producer M1 result stands), never blessed by a dominant-receiver guess.
  - Veto: an asserted call resolving to a helper on a *different* project type unrelated to the CUT → **abstain**.
  - A call on a JDK/library type (declaration null, declaring type in a classpath jar) → the resolver returns the call unchanged (its result stays `Optional<CtInvocation<?>>`); `TestAnalysisTask` derives library status from the null declaration + classpath origin (Step 4). The resolver never carries status.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement `focalType(CtMethod)`** — derive the focal type by stripping `Test`/`Tests`/`IT`/`ITCase` suffix or `Test` prefix from the declaring class simple name and resolving it against the model. The dominant production-call receiver type is **telemetry / low-confidence only**: use it toward the veto solely when it agrees with the naming-derived focal type or the source-package convention; a bare dominant-receiver guess must never affirm a candidate (it would bless a helper-heavy test's collaborator as the CUT). Add a `cutConsistent(mut, focalType)` check: when a confident focal type exists, the MUT's declaring type must equal it or be a collaborator instantiated/received on it, else abstain; when no confident focal type exists, the veto is inconclusive and does not fire. **Veto only — it never *promotes* a candidate.**
- [ ] **Step 4: Reclassify library MUTs in `TestAnalysisTask`** — when the resolved call's `getDeclaration()` is null and the declaring type resolves to a non-source (classpath) type, record the assertion with a distinct rejection reason (e.g. set a `library_mut` telemetry flag / a dedicated `filter_result` reason) instead of leaving all `tested_*` fields silently null. It still does not generalize (SPF needs source), but it is a *typed* exclusion, not an unidentified miss.
- [ ] **Step 5: Run, expect PASS.**
- [ ] **Step 6: Commit.** `feat: veto non-CUT MUTs and reclassify library targets`

### Task 7: Corpus verification (empirical, no new code)

Prove recall rose without introducing mis-targeted picks.

**Files:** none (run against a disposable DB).

- [ ] **Step 1: Build + unit tests.** `./gradlew build` — all `MethodUnderTestResolverTest` cases green, whole suite green.
- [ ] **Step 2: Re-run the applicability funnel** on the disposable spike DB (`postgres_reporeapers_rerun`, or a fresh disposable DB) for the 20-project spike; compare `MissingValue` first-reject counts before/after. Expected: a measurable drop matching Task 1's bucket sizing.
- [ ] **Step 3: Mis-targeting spot check.** Sample 20 newly-identified assertions; confirm the recorded `tested_method_name` is the method the assertion is intended to exercise (manual read of the test source). A coherent-but-shallow / wrong-layer pick is a *mis-targeting* failure (the intended method's regressions go undetected) → tighten the cascade toward abstain for that pattern before proceeding. Incoherent picks are already excluded by the seed check, so they should not surface here.
- [ ] **Step 4: No-regression check.** Confirm the census generalization count (the ~250 sound generalizations on `postgres_jarvis_census`) does not drop. Acceptance: `MissingValue` down, mis-targeting rate zero in the sample, census non-regressed.

## Self-review

- **Spec coverage:** M1 = Tasks 3–5; M2 = Task 6; M3 (cascade + abstain) = Task 2 skeleton, enforced by every task returning empty on ambiguity. Diagnostic sizing = Task 1; verification = Task 7.
- **Targeting validity:** every branch's failure path is `Optional.empty()` (abstain); the only new *acceptances* are single-producer resolutions that pass the CUT veto. No `argmax`, no best-effort pick. Incoherent picks (wrong oracle value) self-exclude via the seed check; the abstain heuristics guard only the coherent-shallow mis-targets the gate cannot catch, and are evidence-gated (Tasks 1, 7) so they do not trade away the dominant recall lever.
- **Deferred (not in this plan):** the differential-oracle conjunct (retain the original asserted relationship as an independent conjunct in the emitted test) — a backstop that changes emitted-test structure for all variants; scope separately if the Task 7 mis-targeting sample is not clean. Setup-method / cross-method field writes (v1 abstains). Killed-mutant ensemble signal (`2026-06-27-ensemble-mut-identification`, needs PIT).
