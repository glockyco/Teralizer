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

1. **Silent-failure surfaces everywhere.** `ModelVisitor` hooks default to no-ops; `Operator.get()` is assert-only; `SpfToModelTransformer` throws `UnsupportedOperationException` on three node kinds; the Error JSON serializer/deserializer field names disagree. Failures surface as crashes or silently-wrong specs rather than typed, attributable outcomes. (A-1, A-2, A-5, D-2)
2. **One IR, three renderers, no shared contract.** `ModelToJavaTransformer`, `SpfToModelTransformer`, `Model(To/From)Json`, and the four generator code emitters each re-encode type/operator knowledge. Adding one expression kind (e.g. `doubleToRawLongBits`) means editing ~6 places with no compiler enforcement. (A-3, C-1)
3. **Global SPF configuration blocks per-probe analysis.** `symbolic.dp`/`symbolic.fp`/`symbolic.bvlength` are global statics rendered from one template; the raw-bits lane (and any future precision/solver tradeoff) needs per-probe config. (B-4, D-3)
4. **By-construction coverage is aggregated at plan level and wired to DB metrics.** Per-parameter `consumedClauseIds` are populated by `NumericDomainPlanner` and aggregated to plan level in `InputGenerationPlanner.plan()`; the DB metrics (`total_constraint_count`/`used_constraint_count`) now write from `InputGenerationPlan`, not the removed `VariableConstraintExtractor`. The residual filter stays unconditional (residual-only filtering is a non-goal per the clause-driven spec). (C-3)
5. **Latent correctness bugs worth fixing regardless of the roadmap:** the `MinMax` double lower-bound bug, `LCMP` long→int truncation, the Error JSON round-trip, and unquoted `ConstantString`. (B-2, B-5, A-2, A-4)

---

## A. Model ⇔ Java ⇔ JSON transformation

Verification: `transformer/*` and `domain/*` are identical in both trees; reviewer findings apply to the worktree. Spot-confirm before fixing the specific line numbers.

- **A-1 · String operators are unrenderable; render runs before type filtering.** `Operator` defines ~56 operators but `ModelToJavaTransformer.postVisit(Operation)` renders ~28 and the default branch throws `RuntimeException`; the 26 string operators (`EQUALS`…`NOREGIONMATCHES`) have no case. `TestGeneralizationTask` renders the *full* `inputModel` to Java before non-supported (String) parameters are removed, so a mixed-type MUT whose path condition mentions a String parameter crashes generalization instead of partially generalizing. Opportunity: render after parameter filtering, and treat unsupported operators as a typed "non-generalizable clause" outcome rather than a throw. Effort M · Sound? no (crashes, not unsound).
- **A-2 · Error JSON round-trip is broken.** `ModelToJsonTransformer` writes the field `error_type`; `JsonToModelTransformer.ErrorDeserializer` reads `type` → NPE on any Error round-trip. Opportunity: single shared field-name constant; add a round-trip test. Effort S · Sound? no (crash).
- **A-3 · No single extensibility seam for a new expression kind.** Adding a node requires touching the domain class, `ModelVisitor`, `ModelToJavaTransformer`, `Model(To/From)JsonTransformer`, `SpfToModelTransformer`, and the generator emitters — none compiler-enforced because `ModelVisitor` hooks are no-op defaults. This is the structural reason the raw-bits node (E) is expensive. Opportunity: make visitor hooks abstract (or a sealed node hierarchy) so a missing case is a compile error; centralize type/operator mapping. Effort M · Sound? yes (silent drops corrupt specs).
- **A-4 · `ConstantString` renders unquoted; boolean-as-int hack is type-map-dependent.** `postVisit(ConstantString)` pushes the raw value (no quotes/escaping); `postVisit(VariableInteger)` emits `(_p_.x ? 1 : 0)` only when the `variableTypes` map says boolean, and `JpfAnalysisTask` constructs the transformer with an empty type map (so booleans render as bare int refs there). Opportunity: quote/escape strings; thread types through one construction path. Effort S–M · Sound? yes (silently wrong rendering).
- **A-5 · `SpfToModelTransformer` throws on three node kinds.** `postVisit(FunctionExpression)`, `postVisit(SpecialIntegerExpression)`, `postVisit(SpecialRealExpression)` throw `UnsupportedOperationException`; `DerivedStringExpression.oprlist` is silently dropped (TODO). This is the Teralizer-side blockage for raw bits and any SPF "function" term (see E). Opportunity: map these to typed Model nodes (starting with the raw-bits function). Effort M · Sound? yes.

## B. SPF native peers, model classes, solver bridge

Verification: solver/peer files under `jpf-symbc/jpf-symbc/` are unchanged by the spike EXCEPT `PCParser`, `ProblemZ3BitVector`, `ProblemGeneral`, `LCMP`, plus the added `JPF_java_lang_Double`/`RawDoubleBitsExpression` — those were re-read here in the worktree. The reviewer read `master` (pre-spike); corrections noted.

- **B-1 · Symbolic `Math` is mostly stubbed.** `JPF_java_lang_Math` implements transcendentals (`sqrt/exp/sin/cos/tan/asin/acos/atan/atan2/log/pow`) but `abs/min/max/ceil/floor/rint` are commented out with TODOs; the `classes/java/lang/Math` model class implements `abs/min/max` as concrete (non-symbolic) if-else. The scorecard reaches `FastMath.abs` only via the prepended model classpath, not a symbolic peer. Opportunity: add symbolic `abs/min/max` (branch-based) so they don't depend on model-class reachability. Effort M · Sound? yes (concretization drops the relation).
- **B-2 · `MinMax` double lower-bound bug.** `MinMax.minDouble = Double.MIN_VALUE` (4.9E-324) instead of `-Double.MAX_VALUE`; the config-override sentinel pattern repeats it; `SymbolicReal.UNDEFINED = Double.MIN_VALUE` collides with this default (a solved value equal to `Double.MIN_VALUE` is indistinguishable from "unset"). Net: symbolic doubles are silently constrained to positives. Opportunity: `-Double.MAX_VALUE` lower bound; a distinct `UNDEFINED` sentinel. Effort S · Sound? yes (wrong/over-narrow specs). Matches the paper's RQ-noted double bounds bug.
- **B-3 · `ProblemZ3BitVector` defaults to 32-bit; doubles need `bvlength=64`.** `bitVectorLength = SymbolicInstructionFactory.bvlength` defaults to 32 → `FPSort32` (single precision) unless 64 is set; `mkFPToIEEEBV` exists (added by the spike) but the default width is a silent single/double mismatch. Opportunity: derive width from the variable type, or default to 64 when fp is on. Effort S–M · Sound? yes.
- **B-4 · Solver config is global static.** `SymbolicInstructionFactory.fp`/`bvlength` are set once per JVM run; there is no per-method/per-class override. The raw-bits lane needs `z3bitvector`+`fp` for one probe while everything else stays on `z3` (the paper/scorecard's tuned rational-real mode; and `z3`+`fp` is upstream-broken per jpf-symbc#35). Opportunity: per-probe config selection (pairs with D-3). Effort M · Sound? no (capability gate).
- **B-5 · `LCMP` truncates long to int in one branch.** The GT branch narrows a concrete long constant to int, corrupting path conditions for 64-bit arithmetic (relevant to raw-bits longs and `toIntExact`). Opportunity: preserve long width. Effort M · Sound? yes. Related: `toIntExact`'s missing overflow path is the symcrete `LCMP`/`DCMP` control-flow decoupling.
- **B-6 · Raw-bits spike is solver-complete but pipeline-orphaned.** In the worktree, `RawDoubleBitsExpression` → `PCParser` (L93–94 `pb.fpToIEEEBV(...)`) → `ProblemZ3BitVector.fpToIEEEBV` (L253–255 `ctx.mkFPToIEEEBV`) is wired and `TestDoubleRawBits` passes. It is NOT consumed by Teralizer (A-5 throws on the function node) and NOT enabled by the scorecard config (D-3). Opportunity: finish the lane top-to-bottom (see E). Effort L · Sound? n/a.

## C. Teralizer input-generator construction

Verification: re-read in the worktree (this is the redesigned `planning/` package + the three factories). These supersede the reviewer's `master`-based generator findings.

- **C-1 · Numeric codegen is duplicated across four emitters.** `getBoxedType` is copied verbatim in `Improved`/`Naive`/`Baseline` factories; `NumericDomainPlanner` re-implements `createRealArbitrary`/`createNumberArbitrary` that still also live in `ImprovedTestParametersSupplierFactory` (reached via the legacy 5-arg overload and tests). The redesign added a 4th codegen path without retiring the 3rd. Opportunity: make the planner the single emitter; reduce the factories to a thin Spoon wrapper; delete the legacy numeric methods once the 5-arg overload is gone. Effort M · Sound? no (drift risk).
- **C-2 · Boolean constraints are discarded; char only recently encoded.** `NumericDomainPlanner.supports` covers INTEGER/REAL/CHAR; BOOLEAN falls to `defaultRecipe` → `Arbitraries.of(true,false)` with no constraint encoding, and boolean constraints are not extracted upstream anyway. Opportunity: a `BooleanDomainPlanner` (equality/just) + boolean extraction. Effort S–M · Sound? no (filter still guards; just lost diversity/boundary precision).
- **C-3 · ~~`consumedClauseIds` is plumbed but never populated → by-construction coverage untracked.~~** → done: per-parameter consumed ids aggregated to plan level in `InputGenerationPlanner.plan()`; DB metrics wired to `InputGenerationPlan` (replacing the removed `VariableConstraintExtractor`); `InputGenerationPlan` gains `getFullPredicate()`/`hasClauses()` so the factory filter stays unconditional. The residual filter stays unconditional (residual-only filtering is a non-goal per the clause-driven spec — no outcome change, only added unsoundness surface).
- **C-4 · No by-construction recipes for rare-precondition shapes.** Everything is `between/just/of` over independent params + filter; there is no recipe that *derives* one param from another beyond affine bounds (e.g. modulo, disequality, or a raw-bits ulps neighborhood `y = longBitsToDouble(doubleToRawLongBits(x)+delta)`). This is precisely the class the paper (§2.3) flags as unsatisfiable by filtering. Opportunity: a small library of by-construction recipes keyed off recognized clause shapes; this is also Gap 3 of the maxUlps lane (E). Effort L · Sound? yes (recipe must imply the clause).
- **C-5 · `RealConstraints` bound lists accept NaN unguarded.** `addConstantLowerBound/UpperBound` store `Double.NaN` without guard; it renders as `Double.NaN` into the generated `Collections.max/min` lists. The scale crash was fixed separately, but a NaN constant bound can still poison bound selection. Opportunity: drop/guard non-finite constant bounds at construction. Effort S · Sound? yes (latent).
- **C-6 · Recorder has two construction paths; first-value only via edge cases.** `JqwikValueRecorderFactory` still builds the recorder twice — `createRecorderClass` (Spoon, production) and `createRecorderSource` (text, tests) — now sharing only the `escapeValue` body; drift risk remains for the rest. `FirstValueArbitrary` injects the original concrete input only through `edgeCases()`, so under limited `tries`/edge-case modes the seed input's exercise is not guaranteed structural. Opportunity: single recorder source consumed by both; assert first-value-first behavior in a test. Effort S–M · Sound? no.

## D. Spec extraction, JPF config, filters

Verification: `jpf-config.vm`, `JpfInstrumentationTask`, `TestGeneralizationListener`, `SpfToModelTransformer`, filters, `Configuration`, `GeneralizableInput` are identical in both trees; reviewer findings valid.

- **D-1 · PC→Model extraction can silently concretize/drop unsupported terms.** The listener + `SpfToModelTransformer` map SPF's constraint chain to the Model; unsupported SPF terms either throw (A-5) or, when a value is already concrete (raw bits under `z3`), are baked in as constants — which is exactly how the maxUlps spec collapsed to `0 < maxUlps`. Opportunity: detect and tag "concretized symbolic term" so an incomplete spec is a typed outcome, not a silent narrowing. Effort M · Sound? yes (silently incomplete spec → unsound assertTrue).
- **D-2 · `Operator.get()` is assert-only.** Unknown operator symbols yield an `AssertionError` (asserts on) or `null` (asserts off, i.e. production `-da`). Opportunity: explicit exception or typed unsupported result. Effort S · Sound? yes.
- **D-3 · One global JPF config template; no per-probe variables.** `jpf-config.vm` hardcodes `symbolic.dp=z3`, omits `symbolic.fp`, and has no injection point for per-probe solver/precision settings; `Configuration` has no per-probe knobs. Opportunity: template variables for `dp`/`fp`/`bvlength` chosen per MUT (e.g. raw-bits MUTs → `z3bitvector`+`fp`+`bvlength=64`). Pairs with B-4. Effort M · Sound? no (capability gate).
- **D-4 · Filter gates are narrower than real capability.** `ParameterTypeFilter` checks declared `testedMethodParameters` against `SUPPORTED_TYPES` without accounting for `GeneralizableInput`'s constructor-argument unwrapping (so inline-constructor cases can be rejected even though the pipeline can handle them — see the generalizable-input spec). Opportunity: have the filter consult `GeneralizableInput.derive(...)`. Effort S–M · Sound? no (over-rejection).
- **D-5 · Native-peer/model classpath wiring is brittle.** Peer/model reachability depends on prepending `${jpf-symbc}/build/...` to the generated config (the `FastMath.abs` fix); ordering-sensitive and easy to regress. Opportunity: centralize and assert the classpath contract. Effort S · Sound? no (silent loss of symbolic models).

## E. The maxUlps raw-bits lane — precise current state

The approved lane (`Precision.equals(double,double,int maxUlps)`) needs all of:
- **Gap 1 — config:** enable `symbolic.fp=true`+`symbolic.dp=z3bitvector`+`bvlength=64` for this probe only (B-3, B-4, D-3). `z3`+`fp` is upstream-broken (jpf-symbc#35), so bitvector is mandatory.
- **Gap 2 — ingestion/rendering:** `SpfToModelTransformer` must map the raw-bits function node to a new Model node (A-5), and `ModelToJavaTransformer` must render `Double.doubleToRawLongBits(_p_.x)` (A-3). The solver side already works (B-6).
- **Gap 3 — generation:** the within-`maxUlps` precondition is too rare to filter; needs a by-construction ulps-neighborhood recipe (C-4). The paper (§2.3) classifies this as a fundamental, not incidental, limitation — so a bespoke recipe is the sanctioned route (full constraint solving is deliberately out of scope per §3.4.3).

Effort L overall; low generality (the recipe is shape-specific). Value: a genuine differentiator vs JARVIS, which cannot express raw-bits ulps at all.

## Alignment with the paper's §5.3 roadmap

- §5.3.1 applicability (type support is the #1 barrier): A-1/A-5/D-1/D-4 are the concrete in-code blockers for strings/objects and for not-silently-dropping terms.
- §5.3.2 effectiveness ("extended constraint encoding to reduce filter-and-regenerate"): C-3 (consumed-clause telemetry) + C-4 (by-construction recipes) are the exact seams; our affine work already moved past Algorithm 1.
- §5.3.3 efficiency: C-1/C-6 reduce codegen drift and per-test overhead; C-4 by-construction recipes reduce filter-miss retries for rare-precondition shapes.
- Deliberate non-goal: solver-in-the-loop generation (§3.4.3) — recommendations stay by-construction + residual filter, never a runtime solver.

## Suggested sequencing (engineering-first, research-later)

1. Quick correctness wins (S): A-2, A-4, B-2, C-5, D-2.
2. Robustness/seams (M): A-1/A-3 (render-after-filter + enforced visitor), D-1 (typed concretization outcome), C-1 (single emitter), D-4 (filter consults `GeneralizableInput`).
3. Effectiveness (M–L): ~~C-3 consumed-clause telemetry~~ (done), then C-4 by-construction recipe library.
4. Per-probe config (M): B-4 + D-3, unblocking the raw-bits lane's Gap 1.
5. Raw-bits lane (L): A-5 + A-3 node (Gap 2), then C-4 ulps recipe (Gap 3).
