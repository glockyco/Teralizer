---
title: Pipeline Improvements
type: plan
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Ordered execution of the improvement opportunities in `2026-06-28-pipeline-architecture-review` (finding IDs referenced as A-n/B-n/C-n/D-n). Each task is TDD where testable, an atomic commit, and verified before the next. jpf-symbc tasks (B-*) require rebuilding the submodule; Teralizer tasks (A/C/D) use `./gradlew test`.

Soundness rule for every task: a generated value must still satisfy the SPF path predicate. By-construction encoding plus a residual filter is allowed; dropping the filter is only allowed for clauses provably enforced by construction.

## Phase 1 — Quick correctness wins

- [x] A-2 · Fix Error JSON round-trip: align the field name between `ModelToJsonTransformer.ErrorSerializer` (`error_type`) and `JsonToModelTransformer.ErrorDeserializer` (`type`); add a round-trip test. → done (`31d20f7c`).
- [x] A-4 · Quote/escape `ConstantString` in `ModelToJavaTransformer.postVisit(ConstantString)`; thread boolean types so `JpfAnalysisTask`'s transformer stops rendering booleans as bare int refs. → done (`c4767f1a` quoting, `87348924` boolean threading).
- [x] B-2 · `MinMax.minDouble` → `-Double.MAX_VALUE`; replaced the `SymbolicReal.UNDEFINED` magic-sentinel checks with a `solved` flag. → done (submodule `524130c`). Config-override sentinel pattern left as a low-risk edge case.
- [x] C-5 · Guard non-finite constant bounds in `RealConstraints` — the guard was already present in the rebased tree; added a regression test. → done (`c82780b9`).
- [x] D-2 · Make `Operator.get()` fail with a typed exception instead of assert-only. → done (`221cca83`).

## Phase 2 — Robustness & seams

- [ ] A-1 · Render the input model to Java only after non-supported parameters are filtered; turn unsupported operators into a typed "non-generalizable clause" outcome instead of a `RuntimeException`.
- [ ] A-3 · Make `ModelVisitor` hooks abstract (or seal the node hierarchy) so a missing case is a compile error; centralize type/operator mapping.
- [ ] A-5 / D-1 · Replace `SpfToModelTransformer`'s `UnsupportedOperationException`/silent-concretization paths with typed, attributable outcomes (tag concretized symbolic terms so incomplete specs are explicit, not silent narrowing).
- [ ] C-1 · Make the planner the single numeric emitter; reduce the three factories to thin Spoon wrappers; delete the legacy duplicate numeric methods + the triplicated `getBoxedType`.
- [ ] D-4 · Have `ParameterTypeFilter` consult `GeneralizableInput.derive(...)` so inline-constructor cases are not over-rejected.

## Phase 3 — Effectiveness (constraint encoding)

- [ ] C-3 · Populate `consumedClauseIds` per recipe and emit a residual-only filter (keep clauses unless provably enforced by construction).
- [ ] C-2 · Add a `BooleanDomainPlanner` + boolean constraint extraction.
- [ ] C-4 · Begin a by-construction recipe library for shapes filtering cannot satisfy (modulo, disequality, and the raw-bits ulps neighborhood) — shared with Phase 5.

## Phase 4 — Per-probe SPF configuration

- [ ] D-3 · Add per-probe template variables for `symbolic.dp`/`symbolic.fp`/`symbolic.bvlength` in `jpf-config.vm` + `Configuration`.
- [ ] B-4 · Select solver/precision per MUT (raw-bits MUTs → `z3bitvector`+`fp`+`bvlength=64`); everything else stays on `z3`.
- [ ] B-3 · Derive `ProblemZ3BitVector` FP width from the variable type (avoid the silent 32-bit default for doubles). *(deferred to Phase 5 — only active under `z3bitvector`+`fp`.)*

## Phase 5 — maxUlps raw-bits lane (research)

- [ ] Gap 2 · Add a `doubleToRawLongBits` Model node; map it in `SpfToModelTransformer` (A-5) and render it in `ModelToJavaTransformer` (A-3). Solver side (`PCParser`→`mkFPToIEEEBV`) already works.
- [ ] Gap 1 · Enable the raw-bits SPF config for that probe only (Phase 4).
- [ ] Gap 3 · Emit the by-construction ulps-neighborhood generator (`y = Double.longBitsToDouble(Double.doubleToRawLongBits(x) + delta)`), delta ∈ [−maxUlps, maxUlps], same sign (C-4).
- [ ] Re-run the scorecard; confirm the maxUlps assertTrue probe is sound and no Table-2 row regresses.

## Opportunistic (fold in where adjacent)

- [x] B-5 · Preserve long width in `LCMP`. → done (submodule `146a4a0`). The `toIntExact` overflow path runs through the same LCMP comparison but was not separately verified.
- [ ] B-1 · Add symbolic `abs/min/max` to the `Math` peer so they don't depend on model-class reachability. *(deferred to Phase 5 — needs `MathFunction.ABS` + Z3 translation; abs/min/max are piecewise/branching, not a quick peer stub.)*
- [ ] C-6 · Single recorder source consumed by both Spoon and text paths; assert first-value-first behavior.
- [ ] D-5 · Centralize + assert the native-peer/model classpath contract.

## Acceptance criteria

- Each finding lands as its own atomic commit with a test where testable.
- No generated test becomes unsound; the scorecard stays exclusion-free (except documented spike gaps until Phase 5 closes them).
- `./gradlew build`, focused tests, and `omp-plans check` pass at each phase boundary.
