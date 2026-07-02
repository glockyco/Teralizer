---
title: Pipeline Architecture Review
type: audit
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Point-in-time architecture/implementation review of the symbolic-spec → property-based-test pipeline: Model⇔Java⇔JSON transformation, SPF native peers / model classes / solver bridge, the Teralizer input-generator construction, and spec-extraction + JPF-config + filters. Goal: capture improvement opportunities so they are not lost. These are findings, not committed work.

## Method & provenance caveat

Four read-only reviewers covered disjoint subsystems; findings were cross-checked against the published paper's roadmap (arXiv 2512.14475 §5.3), external sources (jpf-symbc issue #35: `symbolic.dp=z3`+`symbolic.fp=true` is upstream-broken; Z3 `fp.to_ieee_bv`/`mkFPToIEEEBV`), and direct re-reads of the worktree.

Caveat: the reviewers resolved relative paths against `master`, not this branch (`beat-jarvis-phase1-char-boolean`). `master` lacks the `planning/` generator redesign and the jpf-symbc raw-bits spike (both branch-only). Findings on files identical in both trees are valid as-is; findings on the generator and the spike were re-verified here against the worktree and corrected. Each section states its verification basis.

Effort tags: S (hours), M (a day or few), L (multi-day / research). "Sound?" = whether it can produce an unsound generated test (one whose generated value violates the SPF path predicate) or a wrong spec.

## Cross-cutting priorities

Observability companion: `2026-07-01-pipeline-observability-telemetry` owns the structured
telemetry design for the failure surfaces in this audit, especially SPF/spec extraction,
`BUILD_PROJECT_INSTRUMENTED`, report collection, and generated-build diagnostics.

1. **~~Silent-failure surfaces everywhere.~~** Mostly resolved by the unified expression model: `ModelFolder` hooks are now all-abstract, so a missing node kind is a compile error (A-3, A-1); `SpfToModelTransformer` is total — no `UnsupportedOperationException` (A-5); the Error JSON round-trip is fixed (A-2); `Operator.get()` throws `IllegalArgumentException` for unknown symbols (D-2, fixed). `ModelVisitor` still has no-op defaults (collectors are partial by intent), so visitor-based code is not compiler-enforced.
2. **~~One IR, three renderers, no shared contract.~~** The `ModelFolder` is now compile-strict (A-3), so a missing node kind is a build break. C-1 (factory codegen duplication) is partially addressed: `getBoxedType` is centralized in `SpoonUtils` and called by all three factories, but the factories remain separate codegen paths with residual drift risk.
3. **~~Global SPF configuration blocks per-probe analysis.~~** Resolved: the symbolic backend is selected per MUT by static detection (`SpfSymbolicConfigSelector`); `jpf-config.vm` exposes `symbolic.dp`/`symbolic.fp`/`symbolic.bvlength` as template variables. (B-4, D-3 — done)
4. **By-construction coverage is aggregated at plan level and wired to DB metrics.** Per-parameter `consumedClauseIds` are populated by `NumericDomainPlanner` and aggregated to plan level in `InputGenerationPlanner.plan()`; the DB metrics (`total_constraint_count`/`used_constraint_count`) now write from `InputGenerationPlan`, not the removed `VariableConstraintExtractor`. The residual filter stays unconditional (residual-only filtering is a non-goal per the clause-driven spec). (C-3)
5. **Latent correctness bugs:** the `LCMP` long→int truncation (B-5) and `RealConstraints` NaN-unguarded bounds (C-5) remain. The `MinMax` double lower-bound bug (B-2), the Error JSON round-trip (A-2), and unquoted `ConstantString` (A-4) are fixed.

---

## A. Model ⇔ Java ⇔ JSON transformation

Verification: `transformer/*` and `domain/*` are identical in both trees; reviewer findings apply to the worktree. Spot-confirm before fixing the specific line numbers.

- **A-1 · ~~String operators are unrenderable; render runs before type filtering.~~** → fixed: string operators render via `fold(Invocation)` through `MethodCapabilities`; `ModelFolder` is compile-strict so every node kind has a render path or a build break.
- **A-2 · ~~Error JSON round-trip is broken.~~** → fixed: both serializer and deserializer use the field name `type`; `ErrorJsonRoundTripTest` covers the path.
- **A-3 · ~~No single extensibility seam for a new expression kind.~~** → fixed: `ModelFolder` is all-abstract (one hook per node kind), so adding a node kind is compiler-guided — every folder must handle it or the build fails. `ModelVisitor` retains no-op defaults (its subclasses are collectors that are partial by intent), so the compile-enforcement is on the fold/render path, not the visitor path.
- **A-4 · ~~`ConstantString` renders unquoted; boolean-as-int hack is type-map-dependent.~~** → fixed: `Constant` with `STRING` domain renders via `renderStringLiteral` (quoted + escaped). Boolean variables still use a `variableTypes` map lookup, but the type-map dependency is now scoped to the `Variable` node only, not scattered across renderers.
- **A-5 · ~~`SpfToModelTransformer` throws on three node kinds.~~** → fixed: zero `UnsupportedOperationException`; unsupported terms raise a typed `UnsupportedSpfTermException`; `DerivedStringExpression.oprlist` is ingested via `Invocation.args` (no silent drop).

## B. SPF native peers, model classes, solver bridge

Verification: solver/peer files under `jpf-symbc/jpf-symbc/` are unchanged by the spike EXCEPT `PCParser`, `ProblemZ3BitVector`, `ProblemGeneral`, `LCMP`, plus the added `JPF_java_lang_Double`/`RawDoubleBitsExpression` — those were re-read here in the worktree. The reviewer read `master` (pre-spike); corrections noted.

- **B-1 · Symbolic `Math` is mostly stubbed.** `JPF_java_lang_Math` implements transcendentals (`sqrt/exp/sin/cos/tan/asin/acos/atan/atan2/log/pow`) but `abs/min/max/ceil/floor/rint` are commented out with TODOs; the `classes/java/lang/Math` model class implements `abs/min/max` as concrete (non-symbolic) if-else. The scorecard reaches `FastMath.abs` only via the prepended model classpath, not a symbolic peer. Opportunity: add symbolic `abs/min/max` (branch-based) so they don't depend on model-class reachability. Effort M · Sound? yes (concretization drops the relation).
- **B-2 · ~~`MinMax` double lower-bound bug.~~** → fixed: `MinMax.minDouble = -Double.MAX_VALUE` (verified at source). `SymbolicReal.UNDEFINED` remains `Double.MIN_VALUE` with a comment noting it sits inside the new range — a distinct sentinel would be cleaner but the soundness bug (silent positive-only constraints) is resolved.
- **B-3 · `ProblemZ3BitVector` uses one global `bitVectorLength` for every variable.** `makeIntVar` makes every int a `bitVectorLength`-wide bit-vector and `makeRealVar` keys the FP sort off it (`== 32 ? FPSort32 : FPSort64`). So the RAW_BITS profile's `bvlength=64` represents doubles/longs correctly but **over-widths Java `int`s to 64-bit, changing 32-bit overflow semantics**. Opportunity: derive width per variable from its type (int→32, long→64, double→FPSort64, float→FPSort32) — thread the type/width to `makeIntVar`/`makeRealVar`. Effort M · Sound? no (over-wide ints mis-model overflow; the RAW_BITS profile is sound only for MUTs without overflow-sensitive int arithmetic, e.g. the maxUlps probe).
- **B-4 · ~~Solver config is global static.~~** → done: the symbolic backend is selected per MUT by static detection (`SpfSymbolicConfigSelector`), not a JVM-global. Raw-bits-FP MUTs get `z3bitvector`+`fp`+`bvlength=64`, everything else `z3`.
- **B-5 · `LCMP` truncates long to int in one branch.** The GT branch narrows a concrete long constant to int, corrupting path conditions for 64-bit arithmetic (relevant to raw-bits longs and `toIntExact`). Opportunity: preserve long width. Effort M · Sound? yes. Related: `toIntExact`'s missing overflow path is the symcrete `LCMP`/`DCMP` control-flow decoupling.
- **B-6 · Raw-bits spike is solver-complete but pipeline-orphaned.** In the worktree, `RawDoubleBitsExpression` → `PCParser` (L93–94 `pb.fpToIEEEBV(...)`) → `ProblemZ3BitVector.fpToIEEEBV` (L253–255 `ctx.mkFPToIEEEBV`) is wired and `TestDoubleRawBits` passes. It is NOT consumed by Teralizer (A-5 throws on the function node) and NOT enabled by the scorecard config (D-3). Opportunity: finish the lane top-to-bottom (see E). Effort L · Sound? n/a.

## C. Teralizer input-generator construction

Verification: re-read in the worktree (this is the redesigned `planning/` package + the three factories). These supersede the reviewer's `master`-based generator findings.

- **C-1 · Numeric codegen is duplicated across four emitters.** Partially addressed: `getBoxedType` is centralized in `SpoonUtils` and called by all three factories, reducing one drift vector. The factories (`Baseline`/`Naive`/`ImprovedTestParametersSupplierFactory`) remain separate codegen paths with their own rendering logic — drift risk is reduced but not eliminated. Opportunity: make the planner the single emitter; reduce the factories to a thin Spoon wrapper. Effort M · Sound? no (drift risk).
- **C-2 · ~~Boolean constraints are discarded; char only recently encoded.~~** → fixed: `BooleanDomainPlanner` handles equality/just and is registered in `DomainPlanners`.
- **C-3 · ~~`consumedClauseIds` is plumbed but never populated → by-construction coverage untracked.~~** → done: per-parameter consumed ids aggregated to plan level in `InputGenerationPlanner.plan()`; DB metrics wired to `InputGenerationPlan` (replacing the removed `VariableConstraintExtractor`); `InputGenerationPlan` gains `getFullPredicate()`/`hasClauses()` so the factory filter stays unconditional. The residual filter stays unconditional (residual-only filtering is a non-goal per the clause-driven spec — no outcome change, only added unsoundness surface).
- **C-4 · By-construction recipes for rare-precondition shapes — evidence-gated, not standalone.** Everything is `between/just/of` over independent params + filter; there is no recipe that *derives* one param from another beyond affine bounds. By-construction pays off only when the **satisfying** set is sparse (filter-miss exhaustion), not when the *excluded* set is sparse — §2.3's filtering-unsatisfiable class. Applying that test: the **ulps neighborhood** (`y = longBitsToDouble(doubleToRawLongBits(x)+delta)`) is the one shape that genuinely cannot be filtered and has a named consumer (the maxUlps probe); disequality (`x != y`) excludes a measure-zero surface so filtering is ~free → not a recipe; equality (`x == y`) is already the affine-equality recipe; large-divisor modulo is buildable+sound but speculative (small divisors filter fine; no case/corpus evidence). Opportunity: build the recipe-library infrastructure + the ulps recipe together inside the maxUlps lane (Gap 3, E); rank any further recipes from generation-coverage shape telemetry, not assumption. Effort L · Sound? yes (recipe must imply the clause).
- **C-5 · `RealConstraints` bound lists accept NaN unguarded.** `addConstantLowerBound/UpperBound` store `Double.NaN` without guard; it renders as `Double.NaN` into the generated `Collections.max/min` lists. The scale crash was fixed separately, but a NaN constant bound can still poison bound selection. Opportunity: drop/guard non-finite constant bounds at construction. Effort S · Sound? yes (latent).
- **C-6 · Recorder has two construction paths; first-value only via edge cases.** `JqwikValueRecorderFactory` still builds the recorder twice — `createRecorderClass` (Spoon, production) and `createRecorderSource` (text, tests) — now sharing only the `escapeValue` body; drift risk remains for the rest. `FirstValueArbitrary` injects the original concrete input only through `edgeCases()`, so under limited `tries`/edge-case modes the seed input's exercise is not guaranteed structural. Opportunity: single recorder source consumed by both; assert first-value-first behavior in a test. Effort S–M · Sound? no.

## D. Spec extraction, JPF config, filters

Verification: `jpf-config.vm`, `JpfInstrumentationTask`, `TestGeneralizationListener`, `SpfToModelTransformer`, filters, `Configuration`, `GeneralizableInput` are identical in both trees; reviewer findings valid.

- **D-1 · PC→Model extraction can silently concretize/drop unsupported terms.** The listener + `SpfToModelTransformer` map SPF's constraint chain to the Model; unsupported SPF terms either throw (A-5) or, when a value is already concrete (raw bits under `z3`), are baked in as constants — which is exactly how the maxUlps spec collapsed to `0 < maxUlps`. Opportunity: detect and tag "concretized symbolic term" so an incomplete spec is a typed outcome, not a silent narrowing. Effort M · Sound? yes (silently incomplete spec → unsound assertTrue).
- **D-2 · ~~`Operator.get()` is assert-only.~~** → fixed: `Operator.get()` throws `IllegalArgumentException` for unknown symbols; `OperatorTest` covers the unknown-symbol path.
- **D-3 · ~~One global JPF config template; no per-probe variables.~~** → done: `jpf-config.vm` exposes `symbolic.dp`/`symbolic.fp`/`symbolic.bvlength` as template variables, supplied per probe via the typed `SpfSymbolicConfig` (was a hardcoded global `symbolic.dp=z3`).
- **D-4 · ~~Filter gates are narrower than real capability.~~** → narrowed: `ParameterTypeFilter` now consults `TypeCapability.supportsGeneratedInput()` (derived from registered `DomainPlanner`s, not a hand-maintained `SUPPORTED_TYPES` list). Constructor-unwrapped params flow through `TestAnalysisTask` → `testedMethodParameters` → the filter, so inline-constructor cases are admitted. The filter does not call `GeneralizableInput.derive()` directly, but the effect is equivalent since `TestAnalysisTask` stores the unwrapped params.
- **D-5 · Native-peer/model classpath wiring is brittle.** Peer/model reachability depends on prepending `${jpf-symbc}/build/...` to the generated config (the `FastMath.abs` fix); ordering-sensitive and easy to regress. Opportunity: centralize and assert the classpath contract. Effort S · Sound? no (silent loss of symbolic models).

## E. The maxUlps raw-bits lane — archived

The maxUlps lane (`Precision.equals(double,double,int maxUlps)`) is archived as bounded-upstream
(`archive/2026-06-28-maxulps-raw-bits-lane.md`). Its dependencies on this audit are resolved or
stale: Gap 1 (config) was done (B-4/D-3); Gap 2 (ingestion/rendering) was blocked by A-5, now
fixed — `SpfToModelTransformer` no longer throws on function terms; Gap 3 (generation) needed the
ulps-neighborhood recipe (C-4), still unbuilt. B-3 (per-variable bit-vector width) remains open
for general raw-bits soundness. The native-peer spec (`2026-06-28-native-peer-model-coverage`)
absorbs the `abs` task (B-1) from this lane.

## Alignment with the paper's §5.3 roadmap

- §5.3.1 applicability (type support is the #1 barrier): A-1/A-5 are fixed (string operators render, ingestion is total); D-1 (typed concretization tagging) and the type ceiling remain the real blockers.
- §5.3.2 effectiveness ("extended constraint encoding to reduce filter-and-regenerate"): C-3 (consumed-clause telemetry, done) is the measurement seam; C-4 (by-construction recipes) is the encoding seam, gated on generation-coverage shape telemetry.
- §5.3.3 efficiency: C-1 (partially addressed — centralized `getBoxedType`, factories still separate) and C-6 reduce codegen drift and per-test overhead.
- Deliberate non-goal: solver-in-the-loop generation (§3.4.3) — recommendations stay by-construction + residual filter, never a runtime solver.

## Suggested sequencing (updated post-unified-expression-model)

1. ~~Quick correctness wins (S): A-2, A-4, B-2, C-5, D-2.~~ A-2/A-4/B-2/D-2 fixed; C-5 remains.
2. ~~Robustness/seams (M): A-1/A-3 (render-after-filter + enforced visitor), D-1 (typed concretization outcome), C-1 (single emitter), D-4 (filter consults `GeneralizableInput`).~~ A-1/A-3 fixed; D-4 narrowed; C-1 partially addressed (centralized `getBoxedType`, factories still separate); D-1 and C-1 remain.
3. ~~Effectiveness: C-3 consumed-clause telemetry~~ (done). C-4 is gated on generation-coverage shape telemetry.
4. ~~Per-probe config: B-4 + D-3~~ (done); B-3 (per-variable bit-vector width) remains.
5. ~~Raw-bits lane (L): A-5 + A-3 node (Gap 2)~~ — A-5 fixed, A-3 fixed; the lane is archived as bounded-upstream. B-3 remains the open raw-bits-soundness item.
