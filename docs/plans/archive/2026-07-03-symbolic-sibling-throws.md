---
title: Symbolic-Sibling Throws in SPF Extraction
type: spec
status: superseded
created: 2026-07-03
parent: 2026-06-26-teralizer-overview
superseded_by: 2026-07-05-collect-mode-conformance
archived: 2026-07-05
---

# Symbolic-Sibling Throws in SPF Extraction

**One concern:** an application-thrown exception on an *off-concrete-path symbolic sibling* aborts the whole SPF search and zeroes the assertion's specs; it should become a typed per-assertion outcome (or a pruned-and-excluded partition) so the concrete path's spec survives.

## Symptom

xenqtt `AppContext` methods (`getArgAsInt/Boolean/…`, `isFlagSpecified`) produce 0 generalizations; all fail `EXECUTE_JPF`. Mechanism (verified): SPF symbolizes the `String` flag argument; `XenqttUtil.validateNotEmpty` branches on `value == null || value.trim().equals("")` and throws `IllegalArgumentException` on the empty partition. SPF explores both partitions; the empty sibling throws. `TestGeneralizationListener.propertyViolated` (`src/main/java/teralizer/jpf/TestGeneralizationListener.java:85`) re-types only one specific NPE shape; every other uncaught exception falls through to JPF's `NoUncaughtExceptionsProperty`, which aborts the entire search → run FAILED → 0 specs. Not a regression: the pre-fusion resolver never routed these assertions into JPF. `trim` and `equals("")` are both in the string-support sound set, so the concrete (non-empty) path's clause `!(flag.trim().equals(""))` is captured soundly — the throwing partition is an input-widening sibling, not the path being generalized.

## Design direction

Mirror the shipped unsupported-string-condition pattern (`2026-06-30-partial-sound-string-support` Task 6): a hostile condition on a partition becomes a typed per-assertion outcome and the search continues; a whole-search crash is never the answer. This is the same pattern for a new category — *application-thrown exception on a symbolic sibling*. Invariant: execution is only a backstop.

The fix reshapes the listener→renderer contract, so it goes through design→approval, not ad-hoc.

**Must distinguish two throw cases:**
- *Throw on the concrete path* — the test's oracle is "this throws"; `CapturedOutput.THROWN` already models it; must keep producing a THROWN spec.
- *Throw on a symbolic sibling* — prune/skip the partition and continue the search; the emitted spec's path condition MUST exclude the throwing partition (see soundness cliff).

## Soundness cliff

Pruning the throwing partition is sound **only if** the emitted spec's path condition excludes it. Prune + domain-restriction are a matched pair: a silent prune without the excluding clause yields a spec claiming to hold for inputs that actually throw. Unconfirmed: whether `StringDomainPlanner` satisfies a composed `trim().equals("")` clause or leans on the residual full-predicate filter — the spike must answer this before the spec is finalized.

## Spike (precedes finalizing this spec)

On the xenqtt `AppContext` case:
1. Verify the concrete (non-empty) partition is actually captured when the abort is suppressed — if SPF explores the empty sibling first and dies there, "don't abort" is insufficient; the search must continue past the throwing sibling and complete the non-empty path.
2. Verify domain-exclusion soundness: the generated inputs for the surviving spec never enter the throwing partition (via `StringDomainPlanner` or the residual filter — determine which, and whether the composed `trim().equals("")` clause is handled).
3. Verify THROWN-on-concrete-path still works (no conflation).

## Acceptance

- xenqtt `AppContext` assertions produce specs for the non-empty partition (baseline recovered: ≥ 8 generalizations).
- A JPF listener test covers each: symbolic-sibling throw → typed outcome + search continues; concrete-path throw → THROWN spec (existing tests keep passing).
- No emitted spec's input domain intersects a pruned throwing partition (spike evidence + planner/filter test).
