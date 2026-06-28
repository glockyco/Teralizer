---
title: Pipeline Improvements
type: plan
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Ordered execution of the improvement opportunities in `2026-06-28-pipeline-architecture-review` (finding IDs referenced as A-n/B-n/C-n/D-n). Each task is TDD where testable, an atomic commit, and verified before the next. jpf-symbc tasks (B-*) require rebuilding the submodule; Teralizer tasks (A/C/D) use `./gradlew test`.

Soundness rule for every task: a generated value must still satisfy the SPF path predicate. By-construction encoding plus a residual filter is allowed; dropping the filter is only allowed for clauses provably enforced by construction.

Scope: Teralizer-side hardening and constraint encoding. Per-probe SPF configuration and the maxUlps raw-bits lane (architecture-review B-3/B-4/D-3 + Gap 1–3 + B-1) are tracked separately in `2026-06-28-maxulps-raw-bits-lane`.

## Phase 1 — Quick correctness wins

- [x] A-2 · Fix Error JSON round-trip: align the field name between `ModelToJsonTransformer.ErrorSerializer` (`error_type`) and `JsonToModelTransformer.ErrorDeserializer` (`type`); add a round-trip test. → done (`31d20f7c`).
- [x] A-4 · Quote/escape `ConstantString` in `ModelToJavaTransformer.postVisit(ConstantString)`; thread boolean types so `JpfAnalysisTask`'s transformer stops rendering booleans as bare int refs. → done (`c4767f1a` quoting, `87348924` boolean threading).
- [x] B-2 · `MinMax.minDouble` → `-Double.MAX_VALUE`; replaced the `SymbolicReal.UNDEFINED` magic-sentinel checks with a `solved` flag. → done (submodule `524130c`).
- [x] C-5 · Guard non-finite constant bounds in `RealConstraints`. → done (`c82780b9`).
- [x] D-2 · Make `Operator.get()` fail with a typed exception instead of assert-only. → done (`221cca83`).

## Phase 2 — Robustness & seams

- [x] A-1 · Render the input model to Java only after non-supported parameters are filtered; turn unsupported operators into a typed "non-generalizable clause" outcome instead of a `RuntimeException`. → done (`13f4821b` typed exception, `f4350514` render-after-filter + sound clause dropping).
- [x] A-3 · Make `ModelVisitor` hooks abstract (or seal the node hierarchy) so a missing case is a compile error; centralize type/operator mapping. → done (`c9b4fcd4`): `ModelFolder<T>` total fold; operator mapping stays in `fold(Operation)` and is handled by A-1's typed outcome.
- [x] A-5 · Replace `SpfToModelTransformer`'s `UnsupportedOperationException` paths with typed, attributable outcomes. → done (`99d1dd08`): `UnsupportedSpfTermException` for the three unsupported SPF term kinds.
- [ ] D-1 · Tag concretized symbolic terms so incomplete specs are explicit, not silent narrowing. Deferred to `2026-06-28-maxulps-raw-bits-lane` (blocked on raw-bits config Gap 1 + Model node Gap 2).
- [x] C-1 · Make the planner the single numeric emitter; reduce the three factories to thin Spoon wrappers; delete the legacy duplicate numeric methods + the triplicated `getBoxedType`. → done (single-sourced `getBoxedType` in `SpoonUtils`; legacy 5-arg overloads + numeric emitters removed).
- [x] D-4 · Have `ParameterTypeFilter` consult `GeneralizableInput.derive(...)` so inline-constructor cases are not over-rejected. → done (`daef24ba`): verified the filter already accepts via `TestAnalysisTask`'s stored unwrapped inputs; regression test pins it.

## Phase 3 — Effectiveness (constraint encoding)

- [x] C-3 · Populate `consumedClauseIds` per recipe for generation-coverage telemetry (which clauses each recipe enforced by construction). The residual filter stays unconditional — residual-only filtering is a non-goal per `2026-06-28-clause-driven-input-generation` (no outcome change, only added unsoundness surface). → done: per-parameter consumed ids (`4f680d7f`) aggregated to plan level in `InputGenerationPlanner.plan()`; DB metrics (`total_constraint_count`/`used_constraint_count`) wired to `InputGenerationPlan`, replacing the removed `VariableConstraintExtractor` (which undercounted affine bounds); `InputGenerationPlan` gains `getFullPredicate()`/`hasClauses()` so the factory filter stays unconditional.
- [x] C-2 · Add a `BooleanDomainPlanner` + boolean constraint extraction. → done (`d3f9a0e0` + `45e8e3da`). Tracked in `archive/2026-06-28-type-capability-and-boolean-planner`.
- [ ] C-4 · Begin a by-construction recipe library for shapes filtering cannot satisfy (modulo, disequality, and the raw-bits ulps neighborhood) — the ulps recipe is consumed by `2026-06-28-maxulps-raw-bits-lane`.

## Opportunistic (fold in where adjacent)

- [x] B-5 · Preserve long width in `LCMP`. → done (submodule `146a4a0`).
- [x] C-6 · Single recorder source consumed by both Spoon and text paths; assert first-value-first behavior. → done (`d995e463`): recorder equivalence verified on both paths; `FirstValueArbitrary.generator(int)` now emits `firstValue` first.
- [x] D-5 · Centralize + assert the native-peer/model classpath contract. → done (`f5be4664`): `Configuration.JPF_SYMBC_MODEL_CLASSPATH` single source of truth.

## Acceptance criteria

- Each finding lands as its own atomic commit with a test where testable.
- No generated test becomes unsound; the scorecard stays exclusion-free (except the documented maxUlps gap until `2026-06-28-maxulps-raw-bits-lane` closes it).
- `./gradlew build`, focused tests, and `omp-plans check` pass at each phase boundary.
