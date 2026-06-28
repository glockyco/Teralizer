---
title: maxUlps Raw-Bits Lane
type: plan
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Make `Precision.equals(double, double, int maxUlps)` a sound, by-construction Teralizer capability instead of a documented concession. This splits the research-grade raw-bits work out of `2026-06-28-pipeline-improvements`; the findings come from `2026-06-28-pipeline-architecture-review` (Gap 1–3, B-1, B-3, B-4, D-3) and the proof-of-concept in archived `2026-06-27-spf-ulps-raw-bits-spike`. Probe context lives in `2026-06-26-jarvis-case-coverage`.

## Why this is its own lane

The spike proved the SPF solver layer can preserve symbolic raw bits (`PCParser` → `mkFPToIEEEBV`, Z3 `fp.to_ieee_bv`). But three independent gaps block it end to end, and closing them needs research-grade design — a new Model node, solver-precision plumbing, and a by-construction generator — distinct from the engineering hardening in `pipeline-improvements`. The probe also cannot be made sound by filtering: the chance a random `(x, y, maxUlps)` triple lands within `maxUlps` ULPs is vanishing, so jqwik would exhaust its retry budget. Generation must be by construction.

## Three gaps (architecture-review)

- **Gap 1 — config off.** The scorecard JPF config uses `symbolic.dp=z3` (rational-real), so `doubleToRawLongBits` is evaluated concretely and the x/y↔maxUlps bit relation never enters the path condition.
- **Gap 2 — no Model node.** Even with raw bits symbolic, `SpfToModelTransformer` has no node for `doubleToRawLongBits`, and `ModelToJavaTransformer` cannot render it.
- **Gap 3 — no by-construction generator.** Filtering is infeasible (above); the generator must derive `y` from `x`'s bit representation within the ULP distance.

## Tasks

### Per-probe SPF configuration

- [ ] D-3 · Add per-probe template variables for `symbolic.dp`/`symbolic.fp`/`symbolic.bvlength` in `jpf-config.vm` + `Configuration` (currently a single global setting).
- [ ] B-4 · Select solver/precision per MUT: raw-bits MUTs → `z3bitvector` + `fp` + `bvlength=64`; everything else stays on `z3`.
- [ ] B-3 · Derive `ProblemZ3BitVector` FP width from the variable type so doubles are not silently solved at 32-bit (`makeRealVar` uses `FPSort32` when `bvlength == 32`).

### Symbolic raw-bits modeling

- [ ] B-1 · Add symbolic `abs` (and `min`/`max`) to the `Math`/`FastMath` peer — needs `MathFunction.ABS` + Z3 translation, or a branch-equivalent model class — so the `abs(xInt - yInt)` step stays symbolic without relying on model-class reachability.
- [ ] Gap 2 · Add a `doubleToRawLongBits` Model node; map it in `SpfToModelTransformer` (the A-5 typed-outcome seam, shipped as `UnsupportedSpfTermException`) and render it in `ModelToJavaTransformer` (the A-3 visitor seam, shipped as `ModelFolder`).
- [ ] D-1 · Tag concretized symbolic terms so incomplete specs are explicit, not silent narrowing — when a value is already concrete (raw bits under `z3`), the listener bakes it in as a constant, which is how the maxUlps spec collapsed to `0 < maxUlps`. Depends on Gap 1 (raw-bits SPF config) and Gap 2 (Model node); without those, tagging would flag every raw-bits probe as incomplete, which is already the documented concession.

### By-construction generation

- [ ] Gap 1 · Enable the raw-bits SPF config for the `precisionEqualsMaxUlps` probe only (via D-3/B-4).
- [ ] Gap 3 · Build the by-construction recipe-library infrastructure (`pipeline-improvements` C-4 lands here) and its first member, the ulps-neighborhood recipe, in the planner: `y = Double.longBitsToDouble(Double.doubleToRawLongBits(x) + delta)`, `delta ∈ [−maxUlps, maxUlps]`, same sign. The ulps recipe is C-4's only evidence-backed member; further recipes are gated on generation-coverage shape telemetry, not assumption.

### Verification

- [ ] Re-run the JARVIS scorecard on the scratch DB; confirm the `precisionEqualsMaxUlps` assertTrue probe is sound (precondition satisfied by construction) and no Table-2 row regresses. If still infeasible, document the precise residual blocker.

## Dependencies

- Gap 2's Model node uses the `SpfToModelTransformer` typed-outcome seam (A-5, shipped: `UnsupportedSpfTermException`) and the `ModelToJavaTransformer` rendering seam (A-3, shipped: `ModelFolder`) from `pipeline-improvements` Phase 2. D-1 (concretization tagging) is tracked in this lane's Symbolic raw-bits modeling section.
- Gap 3 builds the by-construction recipe library (`pipeline-improvements` C-4) — the ulps recipe is its first and only evidence-backed member.

## Acceptance criteria

- The `precisionEqualsMaxUlps` assertTrue probe generates inputs satisfying the ULP precondition by construction, with no residual-filter retry exhaustion.
- No Table-2 row regresses on the scorecard rerun.
- If a gap proves genuinely infeasible within scope, it is documented as a bounded upstream-SPF task, not left as a silent concession.
