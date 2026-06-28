---
title: P1 — Clause-Driven Numeric Planner
type: plan
status: implemented
created: 2026-06-28
archived: 2026-06-28
parent: 2026-06-28-clause-driven-input-generation
---

# P1 — Clause-Driven Numeric Planner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `NumericDomainPlanner` interpret its parameter's `ConstraintClause`s directly and report the clause ids it consumes, instead of reading the pre-digested `PlanningContext.getConstraints()` map — the first, behavior-preserving step toward a clause-driven seam that a `StringDomainPlanner` can later join.

**Architecture:** `InputGenerationPlanner` already flattens the input model into top-level `ConstraintClause`s (id + model expression + rendered Java) and passes them in `PlanningContext.getClauses()`. Today `NumericDomainPlanner` ignores them and reads `context.getConstraints()` (the `VariableConstraintExtractor` output). This plan moves the per-parameter numeric bound interpretation into the planner, driven by clauses, recording consumed clause ids. The cross-parameter affine bounds (`InputGenerationPlanner.addAffineBounds`) and `VariableConstraintExtractor` stay as-is (shim) this round; the recipe Java and all generated output are unchanged, so every existing test stays green. Scope is **one planner**; the single-type-capability source, fail-loud visitor seam, and factory-emitter collapse are separate follow-up plans.

**Tech Stack:** Java 8, jqwik generation, JUnit/jqwik `@Example` tests via `./gradlew test`.

This plan does **not** touch: `Configuration.SUPPORTED_TYPES`, the front-end gate, `ModelVisitor`, the Baseline/Naive/Improved factories, or any DB/telemetry. Those are later plans (P-A2 single type source, P-A3 fail-loud seam, P-A4 emitter collapse, then P2 characterization+telemetry, P3 boolean, P4 string).

## Implementation outcome (as built)

Shipped cleaner than the per-round shim sketched above. Instead of leaving `VariableConstraintExtractor` + `addAffineBounds` feeding a pre-digested map, a single `NumericClauseInterpreter` now owns the numeric clause→bound semantics (atomic comparisons **and** affine), recording consumed clause ids per parameter. `PlanningContext` computes per-parameter interpretations from the clauses; `NumericDomainPlanner` is a pure consumer; `InputGenerationPlanner` no longer runs an extractor/affine pre-pass. `VariableConstraintExtractor` is left byte-identical — it still powers `TestGeneralizationTask`'s total/used constraint-count DB metrics; reconciling that counting onto the interpreter is deferred (A4). The plan-level `InputGenerationPlan` consumed set stays empty on purpose, so the generated supplier keeps the full input filter (sound fallback); emitting a residual-only filter from the per-parameter consumed ids is deferred to C-3 in `2026-06-28-pipeline-improvements.md`.

Commits: `d8836c3e` holder · `4f680d7f` interpreter + planner/context rewrite + RED→GREEN consumed-id test · `9ac4d51d` full-filter-fallback comment · `efc66f4d` consumed-id shape tests. Verified: `./gradlew build` green; `InputGenerationPlannerTest` (atomic / affine / overflow / char / first-value / residual) renders byte-identical recipes; `TestGeneralizationTaskTest` metrics intact.

---

## File structure

- Modify: `src/main/java/teralizer/jqwik/planning/NumericDomainPlanner.java` — add clause interpretation + consumed-id reporting; keep `createArbitrary`/`createNumberArbitrary`/`createRealArbitrary`/`createCharArbitrary` emit logic unchanged.
- Create: `src/main/java/teralizer/jqwik/planning/NumericClauseInterpretation.java` — small result holder `{VariableConstraints constraints, Set<Integer> consumedClauseIds}`.
- Test: `src/test/java/teralizer/jqwik/planning/NumericDomainPlannerClauseTest.java` — new, consumed-id assertions.
- Reference (unchanged, read to port logic): `src/main/java/teralizer/jqwik/VariableConstraintExtractor.java` (its `updateConstraints(...)` operator→bound mapping), `src/main/java/teralizer/jqwik/IntegerConstraints.java`, `RealConstraints.java`, `src/test/java/teralizer/jqwik/planning/InputGenerationPlannerTest.java` (behavior-preservation oracle).

---

## Task 1: Result holder for clause interpretation

**Files:**
- Create: `src/main/java/teralizer/jqwik/planning/NumericClauseInterpretation.java`

- [ ] **Step 1: Write the class**

```java
package teralizer.jqwik.planning;

import teralizer.jqwik.VariableConstraints;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The numeric constraints derived for one parameter from the input clauses, plus the ids of the clauses that contributed. */
final class NumericClauseInterpretation {
    private final VariableConstraints constraints;
    private final Set<Integer> consumedClauseIds;

    NumericClauseInterpretation(VariableConstraints constraints, Set<Integer> consumedClauseIds) {
        this.constraints = constraints;
        this.consumedClauseIds = new LinkedHashSet<>(consumedClauseIds);
    }

    VariableConstraints getConstraints() {
        return this.constraints;
    }

    Set<Integer> getConsumedClauseIds() {
        return Collections.unmodifiableSet(this.consumedClauseIds);
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (new file compiles; not yet used).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/teralizer/jqwik/planning/NumericClauseInterpretation.java
git commit -m "feat(planning): add NumericClauseInterpretation result holder"
```

---

## Task 2: Failing test — the planner reports consumed clause ids

**Files:**
- Test: `src/test/java/teralizer/jqwik/planning/NumericDomainPlannerClauseTest.java`

- [ ] **Step 1: Write the failing test**

Mirror the construction style of `InputGenerationPlannerTest` (build a `Model`, a `List<MethodParameter>`, call the planner). A single clause `a < 5` over one `int` parameter must report that clause as consumed.

```java
package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;

import java.util.Collections;
import java.util.List;

public class NumericDomainPlannerClauseTest {
    @Example
    void reportsConsumedClauseIdForAtomicIntegerBound() {
        // a < 5
        Operation model = new Operation(new VariableInteger("a"), Operator.LT, new ConstantInteger(5));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("int", "a"));

        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("a", "int"));
        PlanningContext context = new PlanningContext(parameters, clauses);

        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);

        Assert.assertEquals(
            "the single a<5 clause must be reported consumed",
            Collections.singleton(0),
            plan.getConsumedClauseIds()
        );
    }
}
```

- [ ] **Step 2: Run, verify it fails**

Run: `./gradlew test --tests teralizer.jqwik.planning.NumericDomainPlannerClauseTest`
Expected: FAIL — `getConsumedClauseIds()` is currently the empty set (`NumericDomainPlanner.plan` passes `Collections.emptySet()`).

---

## Task 3: Interpret clauses in NumericDomainPlanner

**Files:**
- Modify: `src/main/java/teralizer/jqwik/planning/NumericDomainPlanner.java`

- [ ] **Step 1: Add the clause interpreter**

Add a `static NumericClauseInterpretation interpret(MethodParameter parameter, PlanningContext context)` that iterates `context.getClauses()`, and for each clause whose `getExpression()` is an `Operation` with a comparison `Operator` (`EQ`/`NE`/`LT`/`LE`/`GT`/`GE`) that names `parameter`, applies the **same** operator→bound mapping that `VariableConstraintExtractor.updateConstraints(...)` uses today (port that logic: build an `IntegerConstraints`/`RealConstraints` via `addConstant*Bound`/`addVariable*Bound`/`addEquality` with the correct `isIncluded` and orientation), and records the clause id. Seed the per-parameter constraints from the existing `context.getConstraints().get(parameter.getName())` first so the cross-parameter affine bounds injected by `InputGenerationPlanner.addAffineBounds` are preserved (shim); merge the clause-derived atomic bounds on top.

```java
static NumericClauseInterpretation interpret(MethodParameter parameter, PlanningContext context) {
    String name = parameter.getName();
    VariableConstraints constraints = context.getConstraints().get(name); // affine/previous-parameter bounds (shim, this round)
    Set<Integer> consumed = new LinkedHashSet<>();
    for (ConstraintClause clause : context.getClauses()) {
        if (!(clause.getExpression() instanceof Operation)) {
            continue;
        }
        Operation op = (Operation) clause.getExpression();
        // PORT: the operator->bound logic from VariableConstraintExtractor.updateConstraints,
        // restricted to a single top-level comparison that names `parameter`. On a match,
        // lazily create the IntegerConstraints/RealConstraints (mirror ensureIntegerConstraints/
        // ensureRealConstraints in InputGenerationPlanner), apply the bound, and:
        //   consumed.add(clause.getId());
        // Leave `constraints` untouched (and do not add the id) for clauses that do not
        // reduce to a single supported atomic bound on this parameter.
        // ... ported logic ...
    }
    return new NumericClauseInterpretation(constraints, consumed);
}
```

(Keep this faithful to the existing extractor: `var < const` ⇒ constant upper bound, exclusive; `var <= const` ⇒ inclusive; `var > const`/`>=` ⇒ lower; `const < var` flips orientation; `var == const` ⇒ equality; `var op var` ⇒ the variable-bound forms — exactly the cases `VariableConstraintExtractor` already handles. Do not invent new cases here; uncovered shapes stay residual and unconsumed.)

- [ ] **Step 2: Use it in `plan`**

Replace the body of `plan` so the recipe is built from the interpreted constraints and the consumed ids are reported:

```java
@Override
public ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context) {
    TypeDomain domain = TypeDomain.from(parameter.getType());
    Optional<MethodArgument> argument = context.getArguments().containsKey(parameter.getName())
        ? Optional.of(context.getArguments().get(parameter.getName()))
        : Optional.empty();
    NumericClauseInterpretation interpretation = interpret(parameter, context);
    String body = createArbitrary(parameter, argument, interpretation.getConstraints());
    return new ParameterGenerationPlan(parameter, domain, new RawJavaRecipe(body), interpretation.getConsumedClauseIds());
}
```

Add imports: `java.util.LinkedHashSet`, `java.util.Set`. `createArbitrary` and the `create*Arbitrary` helpers are unchanged.

- [ ] **Step 3: Run the new test**

Run: `./gradlew test --tests teralizer.jqwik.planning.NumericDomainPlannerClauseTest`
Expected: PASS.

---

## Task 4: Prove behavior preservation (recipes unchanged)

**Files:** none (verification only).

- [ ] **Step 1: Run the existing planner + supplier suites**

Run: `./gradlew test --tests teralizer.jqwik.planning.InputGenerationPlannerTest --tests teralizer.spoon.generalization.ImprovedSupplierRenderingTest --tests teralizer.processing.task.TestGeneralizationTaskTest`
Expected: PASS — the emitted `between(...)`/`just(...)` recipe Java is byte-identical because `createArbitrary` is unchanged and `interpret` seeds from the same affine constraints. If any recipe assertion changed, the interpretation diverged from the extractor — fix `interpret` before continuing (do not adjust the expected recipe).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/teralizer/jqwik/planning/NumericDomainPlanner.java src/test/java/teralizer/jqwik/planning/NumericDomainPlannerClauseTest.java
git commit -m "feat(planning): drive numeric recipes from clauses, report consumed ids"
```

---

## Task 5: Consumed-id tests for the remaining numeric shapes

**Files:**
- Modify: `src/test/java/teralizer/jqwik/planning/NumericDomainPlannerClauseTest.java`

- [ ] **Step 1: Add tests for each ported shape**

One `@Example` per shape the interpreter claims to consume, each asserting both the rendered recipe (narrowed range) and the consumed-id set; and one asserting a shape it does **not** encode stays unconsumed (residual). Cover: `double` constant bound (`x >= 1.5`), `char` equality (`c == 'A'` as the `(char)65` form), a previous-parameter variable bound (`b > a`), and an unsupported shape (e.g. `a % 2 == 0`) that must yield an **empty** consumed set.

```java
@Example
void leavesUnsupportedShapeResidual() {
    // a % 2 == 0  — no atomic single-parameter bound; must stay unconsumed
    Operation model = new Operation(
        new Operation(new VariableInteger("a"), Operator.MOD, new ConstantInteger(2)),
        Operator.EQ, new ConstantInteger(0));
    List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("int", "a"));
    List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("a", "int"));
    ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), new PlanningContext(parameters, clauses));
    Assert.assertTrue("modulo is not an atomic bound; stays residual", plan.getConsumedClauseIds().isEmpty());
}
```

(Add the positive-shape `@Example`s alongside, mirroring Task 2's structure and the bound-rendering assertions in `InputGenerationPlannerTest`.)

- [ ] **Step 2: Run**

Run: `./gradlew test --tests teralizer.jqwik.planning.NumericDomainPlannerClauseTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/teralizer/jqwik/planning/NumericDomainPlannerClauseTest.java
git commit -m "test(planning): cover consumed-id reporting across numeric shapes"
```

---

## Acceptance criteria

- `NumericDomainPlanner.plan` returns a non-empty `consumedClauseIds` for the atomic numeric shapes it encodes, and an empty set for shapes it cannot (verified by `NumericDomainPlannerClauseTest`).
- All pre-existing tests stay green (`InputGenerationPlannerTest`, `ImprovedSupplierRenderingTest`, `TestGeneralizationTaskTest`) — generated recipe Java is unchanged.
- No change to `SUPPORTED_TYPES`, the front-end gate, `ModelVisitor`, the factories, or any DB/telemetry surface.

## Follow-up plans (not this plan)

- **P-A2** single type-capability source (registry-derived gate; retire the hand-maintained `SUPPORTED_TYPES` list).
- **P-A3** fail-loud visitor seam (`ModelToJavaTransformer` non-defaulted hooks; `SpfToModelTransformer` typed non-generalizable outcomes) — soundness-critical per the spec.
- **P-A4** single emitter (collapse Baseline/Naive/Improved numeric duplication; retire `VariableConstraintExtractor` once the planner owns all interpretation, including affine).
- **P2** characterization + telemetry; **P3** boolean planner; **P4** string planner.
