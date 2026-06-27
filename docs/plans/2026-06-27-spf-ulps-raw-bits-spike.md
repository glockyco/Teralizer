---
title: SPF Ulps Raw-Bits Spike
type: plan
status: active
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
---

Determine whether Teralizer can exceed JARVIS on Precision-style ulps checks by making SPF preserve symbolic information through `Double.doubleToRawLongBits(double)`.

## Goal

Turn `Precision.equals(double,double,int maxUlps)` from a documented raw-bits concession into either a working SPF capability or a precisely bounded upstream-SPF implementation task.

## Current blocker

`Double.doubleToRawLongBits(double)` is dispatched through JPF's native peer `jpf-core/src/peers/gov/nasa/jpf/vm/JPF_java_lang_Double.java`. The peer receives a concrete `double` parameter and returns `Double.doubleToRawLongBits(v0)`, so the symbolic attribute attached to the JVM stack slot is lost before the bit pattern reaches symbolic long operations.

This is not the same as `D2L`: numeric double-to-long conversion adds a numeric relation, while raw-bits reinterpretation needs IEEE-754 bit-vector semantics.

## Why this is worth a spike

SPF already has most of the required machinery:

- `symbolic.fp=true` switches Z3 real variables from rational `RealExpr` to IEEE-754 `FPExpr`.
- Z3's Java API provides `mkFPToIEEEBV(FPExpr)`, the exact `double -> 64-bit bit-vector` reinterpretation needed for raw bits.
- jpf-symbc already has symbolic long bitwise operations (`LAND`, `LXOR`, `LOR`, `LSHL`, `LSHR`, `LUSHR`) backed by Z3 bit-vectors.
- `ProblemZ3BitVector` already mixes FP and bit-vector sorts in other operations.

The spike is therefore an SPF integration task, not a new solver-research project.

## Spike tasks

- [ ] Add a minimal failing SPF test for symbolic `Double.doubleToRawLongBits(symDouble)` under `symbolic.dp=z3bitvector`, `symbolic.fp=true`, and `symbolic.bvlength=64`. The failure should show a concrete returned long or one collapsed path where sign/ulps branches should remain symbolic.
- [ ] Add an explicit JPF config path for the spike that sets `symbolic.fp=true`, `symbolic.bvlength=64`, and a negative `symbolic.min_double` bound so negative doubles are not made UNSAT by the current default lower bound.
- [ ] Add a solver operation such as `fpToIeeeBv(Object fpExpr)` to the Z3-backed problem interfaces and implement it with `ctx.mkFPToIEEEBV((FPExpr) fpExpr)` for `ProblemZ3` and `ProblemZ3BitVector`.
- [ ] Add a symbc-local `java.lang.Double` native peer override for `doubleToRawLongBits__D__J` that, when the argument has an FP symbolic attribute, returns a long with the `fpToIeeeBv` bit-vector expression attached; otherwise it preserves the concrete JPF behavior.
- [ ] Run the failing SPF test again and confirm that downstream long bitwise operations receive a symbolic bit-vector expression.
- [ ] Add a pinned Commons Math `Precision.equals(double,double,int maxUlps)` spike fixture only after the raw-bits unit test passes.
- [ ] If the peer cannot safely recover the argument symbolic attribute from MJI, stop and document the exact JPF native-peer boundary that blocks the implementation.

## Related SPF cleanup discovered by the spike

- Fix `MinMax`'s default double lower bound (`Double.MIN_VALUE` is the smallest positive nonzero double, not a negative bound) or make Teralizer emit an explicit negative `symbolic.min_double` in generated JPF configs.
- Inspect `LUSHR`'s double-assignment branch. It is not expected to block `Precision.equals(..., maxUlps)`, but it is a real symbolic-bytecode correctness bug if the overwritten branch is reachable.
- Keep `FastMath.abs` model-class work separate. Model classes express branch-equivalent Java methods; they cannot express `doubleToRawLongBits` bit reinterpretation.

## Acceptance criteria

- A symbolic raw-bits unit test distinguishes at least two input regions that currently collapse under the concrete native peer.
- The implementation is guarded so default rational-real runs are unaffected unless `symbolic.fp=true` is enabled.
- The spike records whether `Precision.equals(double,double,int maxUlps)` can produce path conditions over the ulps branch in the pinned Commons Math fixture.
- If successful, the JARVIS scorecard audit adds the pure maxUlps fixture as a separate, non-Table-2 probe instead of conflating it with the existing eps fixture.
