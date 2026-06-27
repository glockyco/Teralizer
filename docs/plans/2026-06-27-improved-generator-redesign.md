---
title: Improved Generator Redesign
type: plan
status: active
created: 2026-06-27
parent: 2026-06-27-residual-aware-input-generation
---

Ordered implementation work for the typed planner behind the `IMPROVED` jqwik input generator.

## Goal

Replace the numeric-shaped `VariableConstraintExtractor` path with a typed `InputGenerationPlanner` that can grow from numbers/chars to strings, arrays, and construction-backed objects without another rewrite.

## Files

- Modify: `src/main/java/teralizer/jqwik/VariableConstraintExtractionResult.java` to expose planner metrics while preserving existing callers.
- Create: `src/main/java/teralizer/jqwik/planning/ConstraintClause.java` for top-level conjunct identity.
- Create: `src/main/java/teralizer/jqwik/planning/ConstraintClauses.java` for flattening `AND` trees.
- Create: `src/main/java/teralizer/jqwik/planning/TypeDomain.java` for normalized parameter domains.
- Create: `src/main/java/teralizer/jqwik/planning/InputGenerationPlan.java` for ordered parameter plans and residual metadata.
- Create: `src/main/java/teralizer/jqwik/planning/ParameterGenerationPlan.java` for one parameter's recipe and consumed clauses.
- Create: `src/main/java/teralizer/jqwik/planning/GenerationRecipe.java` for jqwik recipe emission.
- Create: `src/main/java/teralizer/jqwik/planning/PlanningContext.java` for parameters, parameter indexes, clause list, and type map.
- Create: `src/main/java/teralizer/jqwik/planning/DomainPlanner.java` for type-specific planning strategies.
- Create: `src/main/java/teralizer/jqwik/planning/NumericDomainPlanner.java` for the first integer/real/char recipes.
- Create: `src/main/java/teralizer/jqwik/planning/InputGenerationPlanner.java` as the orchestrator.
- Modify: `src/main/java/teralizer/spoon/generalization/ImprovedTestParametersSupplierFactory.java` to consume `InputGenerationPlan` instead of raw constraint maps.
- Modify: `src/main/java/teralizer/processing/task/TestGeneralizationTask.java` only at the call boundary: construct the plan once, pass it to the improved supplier, and keep existing metric fields populated.
- Test: `src/test/java/teralizer/jqwik/planning/ConstraintClausesTest.java`.
- Test: `src/test/java/teralizer/jqwik/planning/InputGenerationPlannerTest.java`.
- Test: `src/test/java/teralizer/spoon/generalization/ImprovedSupplierRenderingTest.java`.
- Test: `analysis/tests/test_jarvis_scoreboard.py` only if planning metadata changes the scorecard output.

## Tasks

### Task 1: Clause model and flattening

- [x] Add `ConstraintClause` with `id`, `Model expression`, and `String javaExpression` rendered through `ModelToJavaTransformer`.
- [x] Add `ConstraintClauses.from(Model inputModel, Map<String, String> parameterTypes)` that flattens only top-level `Operator.AND` nodes and preserves every other expression as one clause.
- [x] Write `ConstraintClausesTest` first for a three-conjunct input and verify the test fails before implementation.
- [x] Implement the flattener without changing generator behavior.
- [x] Run `./gradlew test --tests teralizer.jqwik.planning.ConstraintClausesTest`.
- [x] Commit the clause model and tests.

### Task 2: Planner result types

- [ ] Add `TypeDomain` normalization for primitive/wrapper numeric types, `char`, `boolean`, `String`, arrays, and a fallback `OBJECT`.
- [ ] Add `GenerationRecipe` with an initial `RawJavaRecipe` implementation that wraps existing emitted arbitrary body strings.
- [ ] Add `ParameterGenerationPlan` and `InputGenerationPlan` with consumed/residual clause id sets and total/used counts.
- [ ] Write `InputGenerationPlannerTest` first for plan metadata with no consumed clauses and verify the test fails before implementation.
- [ ] Implement the result types and a pass-through `InputGenerationPlanner` that produces existing-style per-parameter recipes plus a full residual filter when input clauses exist.
- [ ] Run `./gradlew test --tests teralizer.jqwik.planning.InputGenerationPlannerTest`.
- [ ] Commit the planner skeleton.

### Task 3: Wire supplier through the planner without behavior change

- [ ] Add an overload `ImprovedTestParametersSupplierFactory.createSupplierClass(..., InputGenerationPlan plan)` and keep the old signature as a small adapter during the migration.
- [ ] Write a rendering test that proves existing full-filter behavior is unchanged for `_p_.x > 0.0`.
- [ ] Write a rendering test that proves no filter is emitted when the plan has no residual clauses.
- [ ] Verify both tests fail before implementation.
- [ ] Implement the supplier changes by reading recipe bodies from `ParameterGenerationPlan`.
- [ ] Update `TestGeneralizationTask` to build one `InputGenerationPlan` for `IMPROVED` and set existing total/used constraint metrics from it.
- [ ] Run `./gradlew test --tests teralizer.spoon.generalization.ImprovedSupplierRenderingTest --tests teralizer.processing.task.TestGeneralizationTaskTest`.
- [ ] Commit the behavior-preserving planner wiring.

### Task 4: Atomic numeric and char recipes

- [ ] Move existing atomic bound/equality logic from `VariableConstraintExtractor`, `IntegerConstraints`, and `RealConstraints` into `NumericDomainPlanner` while preserving generated source for supported cases.
- [ ] Write tests for `b > a`, `b >= a`, `b < a`, `b <= a`, `b == a`, constant bounds, and char bounds.
- [ ] Verify the tests fail before implementation.
- [ ] Implement consumed-clause tracking for clauses fully encoded by the numeric planner.
- [ ] Keep a full residual filter if any consumed-clause uncertainty remains; prefer soundness over performance.
- [ ] Run `./gradlew test --tests teralizer.jqwik.planning.InputGenerationPlannerTest --tests teralizer.spoon.generalization.ImprovedSupplierRenderingTest`.
- [ ] Commit atomic numeric planning.

### Task 5: Simple affine two-variable recipes

- [ ] Add an internal affine-term representation for `constant + coeff * variable` over integer and real expressions.
- [ ] Support solving one comparison for the current parameter when every other variable in the affine term was generated earlier.
- [ ] Write tests for `a + b < n`, `a + b <= n`, `a - b > n`, and `b == a + 1`.
- [ ] Verify the tests fail before implementation.
- [ ] Implement integer overflow guards; leave an unsafe affine clause residual instead of emitting an unsound bound.
- [ ] Implement real affine bounds with jqwik inclusive/exclusive `between` and dynamic scale.
- [ ] Run `./gradlew test --tests teralizer.jqwik.planning.InputGenerationPlannerTest --tests teralizer.spoon.generalization.ImprovedSupplierRenderingTest`.
- [ ] Commit affine numeric planning.

### Task 6: Residual filter emission

- [ ] Make `InputGenerationPlan` render a residual Java predicate from only unconsumed clauses.
- [ ] Write supplier rendering tests for all-consumed input, partly-consumed input, and unsupported residual input.
- [ ] Verify the tests fail before implementation.
- [ ] Implement residual-only filter emission.
- [ ] If residual rendering is brittle, keep the full filter and record residual metadata; do not block the planner redesign on filter minimization.
- [ ] Run `./gradlew test --tests teralizer.jqwik.planning.InputGenerationPlannerTest --tests teralizer.spoon.generalization.ImprovedSupplierRenderingTest`.
- [ ] Commit residual filter behavior or the explicit full-filter fallback.

### Task 7: Scoreboard rerun and documentation

- [ ] Run focused Java tests for the planner and supplier.
- [ ] Run `./gradlew build`.
- [ ] Run `uv run --directory analysis pytest tests/test_jarvis_scoreboard.py -q`.
- [ ] Rerun the JARVIS scorecard against `postgres_jarvis_scoreboard` only if the generator output changed.
- [ ] Update `docs/plans/2026-06-26-jarvis-case-coverage.md` with any PVC deltas and generator-shape explanation.
- [ ] Update `docs/plans/2026-06-26-teralizer-overview.md` only if the JARVIS win summary changes.
- [ ] Run `omp-plans index && omp-plans check`.
- [ ] Commit evidence/doc updates separately from code commits.

## Acceptance criteria

- The planner boundary exists and is covered by tests before production changes.
- `IMPROVED` remains path-exact: unsupported or uncertain clauses are filtered.
- Atomic numeric behavior is not regressed.
- The first affine cases reduce dependence on final filtering where safe.
- Strings, arrays, and objects have explicit planner extension points even if only numeric/char planners are implemented now.
- Each task lands as an atomic commit.
