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
- [x] B-2 · `MinMax.minDouble` → `-Double.MAX_VALUE`; replaced the `SymbolicReal.UNDEFINED` magic-sentinel checks with a `solved` flag. → done (submodule `524130c`). Config-override sentinel pattern left as a low-risk edge case.
- [x] C-5 · Guard non-finite constant bounds in `RealConstraints` — the guard was already present in the rebased tree; added a regression test. → done (`c82780b9`).
- [x] D-2 · Make `Operator.get()` fail with a typed exception instead of assert-only. → done (`221cca83`).

## Phase 2 — Robustness & seams

- [x] A-1 · Render the input model to Java only after non-supported parameters are filtered; turn unsupported operators into a typed "non-generalizable clause" outcome instead of a `RuntimeException`. → done: typed `NonGeneralizableExpressionException` (`13f4821b`) replaces the renderer's generic throws; `transformPredicate` (`f4350514`) renders the input predicate after parameter filtering and drops a non-generalizable clause only when it references at least one variable and none is generated (sound: those stay concrete), while rethrowing when the clause constrains a generated parameter or references no variable. `ConstraintClauses.from` carries the same rule so the planner's clause set stays consistent with the residual predicate.
- [x] A-3 · Make `ModelVisitor` hooks abstract (or seal the node hierarchy) so a missing case is a compile error; centralize type/operator mapping. → done (`c9b4fcd4`): introduced a total `ModelFolder<T>` bottom-up fold with one abstract hook per concrete node and a `fold()` dispatch on every `Model` node; migrated `ModelToJavaTransformer` off the stack-based no-op visitor onto the folder (a missing node case is now a compile error). Observers (`ModelStatisticsExtractor`, `VariableConstraintExtractor`) keep `ModelVisitor`. Operator-to-Java mapping stays a switch inside `fold(Operation)` and is deferred to A-1's typed non-generalizable outcome.
- [ ] A-5 / D-1 · Replace `SpfToModelTransformer`'s `UnsupportedOperationException`/silent-concretization paths with typed, attributable outcomes (tag concretized symbolic terms so incomplete specs are explicit, not silent narrowing).
- [x] C-1 · Make the planner the single numeric emitter; reduce the three factories to thin Spoon wrappers; delete the legacy duplicate numeric methods + the triplicated `getBoxedType`. → done: removed the 5-arg overloads + legacy numeric emitters + `Names`/`generateInclusionCheck`; `getBoxedType` now single-sourced in `SpoonUtils`; dropped the dead `constraints` and `arguments` params from `createSupplierClass`.
- [ ] D-4 · Have `ParameterTypeFilter` consult `GeneralizableInput.derive(...)` so inline-constructor cases are not over-rejected.

## Phase 3 — Effectiveness (constraint encoding)

- [ ] C-3 · Populate `consumedClauseIds` per recipe and emit a residual-only filter (keep clauses unless provably enforced by construction). → partial: P1 populates per-parameter consumed ids (`4f680d7f`); plan-level aggregation / residual-only filter deliberately deferred for soundness (documented at `InputGenerationPlanner`'s `Collections.emptySet()`).
- [x] C-2 · Add a `BooleanDomainPlanner` + boolean constraint extraction. → done (`d3f9a0e0` BooleanDomainPlanner + registry; `45e8e3da` numeric-domain gate). Tracked in `archive/2026-06-28-type-capability-and-boolean-planner`.
- [ ] C-4 · Begin a by-construction recipe library for shapes filtering cannot satisfy (modulo, disequality, and the raw-bits ulps neighborhood) — the ulps recipe is consumed by `2026-06-28-maxulps-raw-bits-lane`.

## Opportunistic (fold in where adjacent)

- [x] B-5 · Preserve long width in `LCMP`. → done (submodule `146a4a0`). The `toIntExact` overflow path runs through the same LCMP comparison but was not separately verified.
- [ ] C-6 · Single recorder source consumed by both Spoon and text paths; assert first-value-first behavior.
- [ ] D-5 · Centralize + assert the native-peer/model classpath contract.

## Acceptance criteria

- Each finding lands as its own atomic commit with a test where testable.
- No generated test becomes unsound; the scorecard stays exclusion-free (except the documented maxUlps gap until `2026-06-28-maxulps-raw-bits-lane` closes it).
- `./gradlew build`, focused tests, and `omp-plans check` pass at each phase boundary.
