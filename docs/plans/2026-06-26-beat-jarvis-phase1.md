---
title: Beat JARVIS Phase 1
type: plan
status: active
created: 2026-06-26
parent: 2026-06-26-teralizer-overview
---

The ordered, checkbox-tracked work to beat JARVIS on its Table-2 cases, plus the win condition.

## Win condition

Beat JARVIS on Table 2 by scoring Teralizer on **capability + PVC/IC**, not mutation score.

Acceptance criteria:

- **Capability:** ≥10/10 Table-2 cases enter the Teralizer pipeline.
- **Coverage:** PVC/IC ≥ JARVIS on SPF-amenable cases.
- **Bug-finding parity:** match JARVIS only on exception/precondition cases that the SPF-derived oracle can express.
- **Interval concession:** `Interval` is a PVC win but a bug-finding loss because JARVIS learns an independent example-pair oracle, while Teralizer derives its oracle from the implementation.
- **Precision concession:** `Precision` remains blocked on the ulps fast-path and `doubleToRawLongBits` concretization.
- **NaN is a shared gap:** JARVIS cannot sample NaN either, so SPF NaN support is not required for this win.

Metric definition:

- **PVC** measures distinct parameter values generated for the MUT under the matched JARVIS Table-2 budget.
- **IC** measures instruction coverage for the generated property-based tests against the same cases.
- Match JARVIS's PVC definition, tries budget of 100, and seed.
- Record generated jqwik values via a property/supplier hook or jqwik `Statistics`; read original values from `assertion.tested_method_call_arguments`.
- Use existing IC data from `jacoco_coverage_report` where it is sufficient, and add finer per-test collection where Table-2 replication needs it.

## Tasks

- [x] Confirm #10 double/float bounds are irrelevant to Teralizer collect-constraints mode and leave jpf-symbc solver bounds unchanged for Phase 1.
- [x] Add #1 `char` and `boolean` support in `Configuration.SUPPORTED_TYPES`, generated value rendering, and boolean-return assertion rewriting.
- [x] Add #11 a `FastMath` SPF model with JARVIS-case `abs(double)`, `min(double,double)`, `max(double,double)`, and `toIntExact(long)` compatibility.
- [ ] Fix #19 the `TestGeneralizationListener.writeSpecificationFiles` listener NPE.
- [ ] Implement #3 object-construction inputs in `JpfInstrumentationTask`, scoped to fixed-arity inline construction with constructing-input generation.
- [ ] Implement #18 exception-path capture in `TestGeneralizationListener` / `JpfExecutionTask` so SPF records thrown-exception specs instead of aborting the whole analysis.
- [ ] Build the PVC/IC measurement harness around generated jqwik PBT values and per-test JaCoCo coverage for JARVIS Table-2 scoreboard runs.

## Validation strategy

- Use the `jarvis_*` spike harness for SPF-side changes; cases should move from BLOCKED/PARTIAL to FULL when the targeted capability lands.
- Use the PVC/IC harness to compare generated jqwik PBTs with JARVIS Table 2 under the matched PVC definition, tries budget, and seed.
- Run pipeline experiments only against a scratch database or throwaway config.
- Never mutate the collected `postgres_dev` or `postgres_test` evaluation databases.
- Keep mutation score out of the JARVIS win claim; it belongs to the separate applicability narrative.

## Needs design before implementation

- #3 object-construction inputs needs its own `spec` for ctor-args-as-inputs instrumentation, constructing generators, and the inline-construction boundary.
- #18 exception-path capture needs its own `spec` for how thrown exceptions become specs: implicit preconditions, negative cases, or assertThrows-style behavior.
- #5 Ghafari mutator/inspector MUT identification needs its own `spec` when it is scheduled for the applicability track.
