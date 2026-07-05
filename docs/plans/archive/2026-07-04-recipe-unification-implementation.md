---
title: Recipe Unification Implementation
type: plan
status: implemented
created: 2026-07-04
parent: 2026-07-04-recipe-unification
archived: 2026-07-05
---

# Recipe Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One codegen path from recipe to wrapper and from recipe to generalized test. Invocation recipes become degenerate expression recipes, the sentinel ints give way to a typed `InputKind`, the mega-tasks split into orchestrators plus pure builders, and the resolver sheds its telemetry and focal concerns.

**Architecture:** Design authority is `2026-07-04-recipe-unification`. Sequence A3 → A1 → A2 → A4. A3 and A1 change contracts and must each land whole. A2 and A4 are behavior-preserving moves. The wave is behavior-preserving end to end: goldens are byte-identical after every task, and any golden movement is a defect in the wave, never something to record.

**Tech stack:** Java 8, Spoon AST, jqwik `@Example` + `org.junit.Assert` for Teralizer's suite, verification fixture corpus.

**Verification economy (operator-mandated):** per task, targeted unit tests plus ONE `scripts/verify-pipeline.sh` run (single, no double-run). No sentinel runs during the wave. At wave end only: the determinism double-run and one sentinel comparison. First-run numbers stand.

---

## Task 1 (A3): typed `InputKind` on `GeneralizableInput`

**Files:**
- Modify: `src/main/java/teralizer/spoon/analysis/GeneralizableInput.java`
- Modify: `src/main/java/teralizer/spoon/analysis/GeneralizationRecipe.java` (InputSite carries/serializes `kind`; `fromRecipe` passes it)
- Modify: consumers of `isReceiverConstructorArgument()`/`isExpressionSite()`/`isConstructorArgument()` (grep first; JpfInstrumentationTask, TestGeneralizationTask, TestAnalysisTask expected)
- Test: `src/test/java/teralizer/spoon/analysis/GeneralizableInputTest.java`, `GeneralizableInputExpressionTest.java`, `GeneralizationRecipeTest.java` (extend)

- [x] **Step 1: Write failing tests.** Every derivation path stamps the right kind: `derive` on a plain call yields `METHOD_ARG` sites, inline-ctor args yield `CTOR_ARG`, receiver-ctor args yield `RECEIVER_CTOR_ARG`, `deriveFromExpression` yields `EXPRESSION_SITE`. Recipe JSON round-trips the kind per site.
- [x] **Step 2: RED**, `./gradlew test --tests 'teralizer.spoon.analysis.*'`.
- [x] **Step 3: Implement.** `GeneralizableInput` gains `private final GeneralizationRecipe.InputKind kind` set by each derivation path. The boolean accessors become kind checks (`isExpressionSite()` ⇒ `kind == EXPRESSION_SITE`, etc.). The int fields STAY in this task as private positional detail for the still-index-keyed consumers. `InputKind` gains `EXPRESSION_SITE`. InSite JSON gains `kind` (schema stays v2 in this task — the version bump belongs to Task 2 where the fields change).
- [x] **Step 4: GREEN** on `teralizer.spoon.analysis.*` and `teralizer.processing.*`, then one `scripts/verify-pipeline.sh` run (goldens must not move).
- [x] **Step 5: Commit.** `refactor(recipe): type input sites with InputKind`

---

## Task 2 (A1): one derivation, schema v3, one codegen path

The contract-changing core. Lands as one task with ordered commits so the build stays green at each commit boundary.

**Files:**
- Modify: `src/main/java/teralizer/processing/task/TestAnalysisTask.java` (unified derivation; `inferExpectedType` moves here)
- Modify: `src/main/java/teralizer/spoon/analysis/GeneralizableInput.java` (unified site derivation for plain calls; int accessors deleted at task end)
- Modify: `src/main/java/teralizer/spoon/analysis/GeneralizationRecipe.java` (schema v3: sites carry `kind` + `path` + parameter/argument records; index fields deleted; `CURRENT_VERSION = 3`; `fromJson` rejects v1/v2)
- Modify: `src/main/java/teralizer/processing/task/JpfInstrumentationTask.java` (single `createInstrumentedMethod` + single `createInstrumentedMethodCall`, path-based rewrite; `inferExpectedType` deleted here)
- Modify: `src/main/java/teralizer/processing/task/TestGeneralizationTask.java` (index-keyed replacement block deleted)
- Modify: `src/main/java/teralizer/jpf/TestGeneralizationListener.java`, `src/main/java/teralizer/jpf/ExtractionOutcome.java`, `src/main/java/teralizer/processing/task/JpfExecutionTask.java`, `src/main/resources/templates/jpf-config.vm` (capture always at wrapper exit; `expression_recipe` flag removed; `TARGET_NOT_ENTERED` kept only where the oracle expression IS the tested call, asserted at the task boundary from the recipe)
- Tests: extend the existing task/listener/recipe suites named in Tasks 1–7 of the R1 plan (`archive/2026-07-04-r1-recipe-implementation.md` lists them)

Ordered steps:

- [x] **Step 1: Unified derivation behind the existing contract.** `TestAnalysisTask` derives every recipe through one path: oracle expression = asserted actual when admissible, else the resolved call. Sites for the plain-call case are the call's argument positions with their kinds from Task 1 (argument positions are sites when their type supports generation, whatever the argument expression is — matching today's `derive`). `oracleExpressionType` is computed at analysis time for ALL recipes: the expression's static type for composites, the assertion-context-inferred type for plain calls (move `inferExpectedType` + `eraseGenerics` from JpfInstrumentationTask into a `spoon.analysis` helper and call it here). Unit tests: plain call, inline-ctor, receiver-ctor, …
- [x] **Step 2: Schema v3.** Index fields out of the persisted site, `kind` + `path` in, version bump, v1/v2 rejected, round-trip tests updated. All consumers compile against kind+path only. GREEN on `spoon.analysis` + `processing` suites. Commit: `feat(recipe): persist v3 sites as kind plus path`
- [x] **Step 3: One codegen path.** JpfInstrumentationTask: delete the invocation-shaped `createInstrumentedMethod` overload and the index-keyed argument loop inside `createInstrumentedMethodCall`. The path-based rewrite serves every recipe. `_target_` and `_local_*` lifting stay, computed after site rewriting (receiver handling keys on `RECEIVER_CTOR_ARG` presence and instance-ness of the oracle method, as today). TestGeneralizationTask: delete the index-keyed block, keep only `replaceInputSitesWithParameterReads`. Existing instrumentation/generation tests updated only where they asserted the old internal shape. GREEN. Commit: `refactor(codegen): rewrite all recipes through site paths`
- [x] **Step 4: One capture mode.** Listener: pinned frame is always the instrumented method. Delete the `expression_recipe` config key from the template, the instrumentation writer, and the listener constructor. `ExtractionOutcome.fromState` loses the mode parameter. JpfExecutionTask keeps `TARGET_NOT_ENTERED` as a failure only when the recipe's oracle expression is the tested call itself (read from the recipe at the task boundary). Existing listener tests updated: invocation-mode tests now run through the single mode and must pass with identical outcomes. GREEN on `teralizer.jpf.*`. Commit: `refactor(listener): capture at wrapper exit for every recipe`
- [x] **Step 5: Gate.** One `scripts/verify-pipeline.sh` run. Goldens byte-identical, 10/10 fixtures green. Tick this task in this plan file. Commit any remaining plan-file tick as part of Step 4's commit or a tiny docs commit.

---

## Task 3 (A2): Task/Builder split

**Files:**
- Create: `src/main/java/teralizer/spoon/codegen/InstrumentedClassBuilder.java`
- Create: `src/main/java/teralizer/spoon/codegen/GeneralizedTestBuilder.java`
- Modify: `JpfInstrumentationTask.java`, `TestGeneralizationTask.java` (orchestration only: scheduling, records, paths, Velocity, file writes)
- Test: `src/test/java/teralizer/spoon/codegen/InstrumentedClassBuilderTest.java`, `GeneralizedTestBuilderTest.java` (direct builder tests against Spoon models; port the codegen assertions that currently live in the task tests)

- [x] **Step 1:** Extract `InstrumentedClassBuilder`: inputs are the resolved recipe, the instrumented class/method names, and the factory. Output is the finished `CtClass`. No DB records, no filesystem, no Velocity. The task calls it and keeps everything else. Targeted tests green (existing instrumentation tests keep passing unmodified — the split must not change printed output).
- [x] **Step 2:** Same for `GeneralizedTestBuilder` (inputs additionally: the plan/license results and variant config the codegen genuinely consumes). Targeted tests green.
- [x] **Step 3:** One `scripts/verify-pipeline.sh` run, goldens unmoved. Both tasks at or under ~300 lines each. Commit per builder: `refactor(codegen): extract the instrumented-class builder`, `refactor(codegen): extract the generalized-test builder`

---

## Task 4 (A4): resolver extraction

**Files:**
- Create: `src/main/java/teralizer/spoon/analysis/FocalTypeResolver.java` (focal inference, `TypeIndex`, `Focal`; caches injected, not static)
- Create: `src/main/java/teralizer/spoon/analysis/InputTopologyClassifier.java` (`classifyShape`, `receiverProvenance`, helpers)
- Modify: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java` (delegates; static caches deleted)
- Modify: `src/main/java/teralizer/processing/TaskContext.java` (owns the per-project focal/type-index cache)
- Modify: callers wiring the context through (TestAnalysisTask)
- Test: split `MethodUnderTestResolverTest` accordingly; new `FocalTypeResolverTest`, `InputTopologyClassifierTest`

- [x] **Step 1:** Extract `InputTopologyClassifier` (pure functions, no state). The classifier gets no access to resolution internals — `resolve` composes `resolveInternal` + classifier output exactly as today. Tests moved, green.
- [x] **Step 2:** Extract `FocalTypeResolver` with instance caches owned by `TaskContext` (per-project lifetime; plain maps, no weak refs, no synchronization beyond TaskContext's own). Tests moved, green.
- [x] **Step 3:** One `scripts/verify-pipeline.sh` run, goldens unmoved. Commit per extraction: `refactor(resolver): extract the topology classifier`, `refactor(resolver): extract focal-type resolution`

---

## Task 5: wave gates + doc refresh

- [ ] **Step 1:** `./gradlew build` green.
- [ ] **Step 2:** Determinism double-run: `scripts/verify-pipeline.sh` twice, normalized-identical output.
- [ ] **Step 3:** Sentinel subset once (scratch DB per AGENTS.md). Invocation-shaped census identical to the pinned headers. Expression rows not regressed (the two included JadConfig wins stay included). First-run numbers stand.
- [ ] **Step 4:** Refresh `docs/artifacts.md` from a regenerated expression-slice family (the `expression_recipe` config key is gone; the JPF config listing must show the real post-wave file). Update `docs/architecture.md` cross-stage contracts if the recipe wording mentions v2.
- [ ] **Step 5:** `omp-plans complete` both unification docs. Final commit.

---

## Self-review

- Spec coverage: A3 = Task 1, A1 = Task 2 (derivation, v3, codegen, capture), A2 = Task 3, A4 = Task 4, invariants = every task's gate plus Task 5.
- Clean cut: v3 rejects v1/v2 (Task 2 Step 2), the mode flag and index accessors are deleted, not deprecated.
- The behavior-preserving invariant is enforced mechanically: goldens byte-identical at every task gate, sentinel census at wave end.
- Order: Task 2 Step 1 lands the unified derivation while the old consumers still work (kinds exist since Task 1), Step 2 flips the schema, Step 3 deletes the old path, Step 4 collapses capture. Each commit compiles and is green.
