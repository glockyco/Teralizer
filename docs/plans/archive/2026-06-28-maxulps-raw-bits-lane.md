---
title: maxUlps Raw-Bits Lane
type: plan
status: implemented
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
archived: 2026-06-29
---

Make `Precision.equals(double, double, int maxUlps)` a sound, by-construction Teralizer capability instead of a documented concession. This splits the research-grade raw-bits work out of `2026-06-28-pipeline-improvements`; the findings come from `2026-06-28-pipeline-architecture-review` (Gap 1–3, B-1, B-3, B-4, D-3) and the proof-of-concept in archived `2026-06-27-spf-ulps-raw-bits-spike`. Probe context lives in `2026-06-26-jarvis-case-coverage`.

## Outcome (lane closed)

Gap 1 shipped and is reused: per-probe `SpfSymbolicConfig` + the
`SpfSymbolicConfigSelector` static detection, plus the renderer's fail-loud guard on
bitwise/shift over floating-point operands (D-3 + B-4). Gap 2, Gap 3, B-1, B-3, and
D-1 are **bounded upstream-SPF tasks, not silent concessions** (acceptance
criterion 3): research-grade Model-node / solver-bridge / by-construction-generator
work disproportionate to one non-Table-2 probe. `Precision.equals(double, double, int
maxUlps)` therefore stays an explicit, documented exclusion, and Rerun 2 confirms no
Table-2 row regresses.

## Why this is its own lane

The spike proved the SPF solver layer can preserve symbolic raw bits (`PCParser` → `mkFPToIEEEBV`, Z3 `fp.to_ieee_bv`). But three independent gaps block it end to end, and closing them needs research-grade design — a new Model node, solver-precision plumbing, and a by-construction generator — distinct from the engineering hardening in `pipeline-improvements`. The probe also cannot be made sound by filtering: the chance a random `(x, y, maxUlps)` triple lands within `maxUlps` ULPs is vanishing, so jqwik would exhaust its retry budget. Generation must be by construction.

## Three gaps (architecture-review)

- **Gap 1 — config off.** The scorecard JPF config uses `symbolic.dp=z3` (rational-real), so `doubleToRawLongBits` is evaluated concretely and the x/y↔maxUlps bit relation never enters the path condition.
- **Gap 2 — no Model node.** Even with raw bits symbolic, `SpfToModelTransformer` has no node for `doubleToRawLongBits`, and `ModelToJavaTransformer` cannot render it.
- **Gap 3 — no by-construction generator.** Filtering is infeasible (above); the generator must derive `y` from `x`'s bit representation within the ULP distance.

## Tasks

### Per-probe SPF configuration

- [x] D-3 · Per-probe `symbolic.dp`/`symbolic.fp`/`symbolic.bvlength` template variables in `jpf-config.vm` (was a hardcoded global `symbolic.dp=z3`), supplied per probe via the typed `SpfSymbolicConfig` value object. → done.
- [x] B-4 · Select the symbolic backend per MUT by **static detection** (`SpfSymbolicConfigSelector`), not an allowlist: a tested method calling a raw-bits FP conversion (`doubleToRawLongBits`/`floatToIntBits`/inverses) gets `z3bitvector` + `fp` + `bvlength=64`, everything else `z3`. Conservative, direct-body, FP-only (integer bitwise/shift is a separate, deferred profile). → done.
- [ ] B-3 · Per-variable bit-width in `ProblemZ3BitVector` (int→32-bit BV, long→64-bit BV, double→FPSort64, float→FPSort32). The solver bridge uses one global `bitVectorLength` for everything, so the raw-bits profile's `bvlength=64` correctly represents doubles/longs but **over-widths Java `int`s to 64-bit, changing 32-bit overflow semantics** — the raw-bits profile is therefore sound for the maxUlps probe (its `int maxUlps` is a non-overflowing bound widened to `long`) but **not generally sound** until per-variable width lands. Substantial solver-bridge change (thread the variable type/width to `makeIntVar`/`makeRealVar`).

### Symbolic raw-bits modeling

- [ ] B-1 · Add symbolic `abs` (and `min`/`max`) to the `Math`/`FastMath` peer — needs `MathFunction.ABS` + Z3 translation, or a branch-equivalent model class — so the `abs(xInt - yInt)` step stays symbolic without relying on model-class reachability.
- [ ] Gap 2 · Add a `doubleToRawLongBits` Model node; map it in `SpfToModelTransformer` (the A-5 typed-outcome seam, shipped as `UnsupportedSpfTermException`) and render it in `ModelToJavaTransformer` (the A-3 visitor seam, shipped as `ModelFolder`).
- [ ] D-1 · Tag concretized symbolic terms so incomplete specs are explicit, not silent narrowing — when a value is already concrete (raw bits under `z3`), the listener bakes it in as a constant, which is how the maxUlps spec collapsed to `0 < maxUlps`. Depends on Gap 1 (raw-bits SPF config) and Gap 2 (Model node); without those, tagging would flag every raw-bits probe as incomplete, which is already the documented concession.

### By-construction generation

- [x] Gap 1 · The raw-bits backend auto-activates for any tested method calling a raw-bits FP conversion (the maxUlps probe included) via `SpfSymbolicConfigSelector` → `SpfSymbolicConfig` → `jpf-config.vm`. → done (D-3 + B-4). End-to-end SPF verification (the path condition carrying the `fpToIEEEBV` relation rather than concretizing) additionally needs Gap 2 ingestion plus a pipeline run, and general soundness needs B-3.
- [ ] Gap 3 · Build the by-construction recipe-library infrastructure (`pipeline-improvements` C-4 lands here) and its first member, the ulps-neighborhood recipe, in the planner: `y = Double.longBitsToDouble(Double.doubleToRawLongBits(x) + delta)`, `delta ∈ [−maxUlps, maxUlps]`, same sign. The ulps recipe is C-4's only evidence-backed member; further recipes are gated on generation-coverage shape telemetry, not assumption.

### Verification

- [x] Re-ran the JARVIS scorecard on the scratch DB (Rerun 2): no Table-2 row regresses, and the `precisionEqualsMaxUlps` probe stays excluded. Residual blocker documented: Gap 2 ingestion + Gap 3 by-construction generation (both bounded upstream-SPF).

## Dependencies

- Gap 2's Model node uses the `SpfToModelTransformer` typed-outcome seam (A-5, shipped: `UnsupportedSpfTermException`) and the `ModelToJavaTransformer` rendering seam (A-3, shipped: `ModelFolder`) from `pipeline-improvements` Phase 2. D-1 (concretization tagging) is tracked in this lane's Symbolic raw-bits modeling section.
- Gap 3 builds the by-construction recipe library (`pipeline-improvements` C-4) — the ulps recipe is its first and only evidence-backed member.

## Acceptance criteria

- The `precisionEqualsMaxUlps` assertTrue probe generates inputs satisfying the ULP precondition by construction, with no residual-filter retry exhaustion.
- No Table-2 row regresses on the scorecard rerun.
- If a gap proves genuinely infeasible within scope, it is documented as a bounded upstream-SPF task, not left as a silent concession.
