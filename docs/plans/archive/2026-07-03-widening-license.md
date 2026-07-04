---
title: Widening License — Oracle-Coherent Input Generalization
type: spec
status: implemented
created: 2026-07-03
parent: 2026-06-26-teralizer-overview
archived: 2026-07-04
---

# Widening License — Oracle-Coherent Input Generalization

**One concern:** the generator widens inputs regardless of whether the extracted oracle can follow, producing property tests that are false by construction; an input may be widened **only as far as the extraction evidence licenses**, and a generalization with no licensed widening becomes a typed exclusion instead of a doomed artifact.

## Evidence

On the definitive single-variant spike (850 validated generalizations): 156 fail after passing the seed trial; 155 of them have `output_spec_class = 'NULL_CONCRETE'`. Failure rate by oracle class: NULL_CONCRETE 155/716 (21.6%) vs SYMBOLIC 1/131 (0.76%). 126 of the 156 additionally have an empty path condition (`total_constraint_count = 0`).

Mechanism (verified on octotron `_ValueTest_Generalized_TestGet_641`): the original `assertEquals(1L, new Value(1).GetLong().longValue())` is generalized by lifting the ctor argument into a jqwik parameter widened over `Arbitraries.integers()` — but `GetLong()` returns a boxed `Long`, the symbolic return attr is lost at boxing, `modelOutput == null`, so the expected side stays the concrete literal `1L` (`TestGeneralizationTask` replaces the expected side only when `outputJava != null`). The property claims `∀x: new Value(x).GetLong() == 1` — a claim the extraction never made. `NonPassingTestFilter` excludes it after a full build+run cycle, so nothing unsound ships; but ~18% of validated generalizations are burned on self-inflicted failures, drowning the informative failure signal (genuine overgeneralization / target bugs, ≈1%).

## The invariant

**License-based generalization:** every widening of an input beyond its concrete seed value must be licensed by extraction evidence. The input side already obeys this (clauses encoded by construction + the unconditional residual filter). The output side has no such discipline — this spec adds it. The same principle already governs the string sound-set and the symbolic-sibling-throws design; it is stated here once, as the named invariant new seams inherit.

## The license rule

Decided per generalization at `GENERALIZE_TESTS` time (all lifted inputs feed the tested call inside the assertion, so the license is effectively per-generalization; a per-parameter split is a refinement no current evidence demands):

| # | condition | verdict |
|---|---|---|
| 1 | output model is SYMBOLIC or CONSTANT (expected side is replaced by the rendered spec) | **widen** — today's behavior; the oracle co-varies (SYMBOLIC) or SPF proved path-constancy (CONSTANT) |
| 2 | output is EXCEPTION (`CapturedOutput.THROWN` — the oracle is "this throws") ∧ `concretization_events = 0` ∧ (path condition empty OR it names every widened parameter) | **widen** — reaching the throw is a control-flow property: with no concretized branches, every branch taken on the way to the throw either left a PC clause (enforced on generated inputs) or did not depend on the symbolic inputs, so every admitted input reaches the same throw. An unconditional throw (empty PC, zero events) holds for every input |
| 3 | NULL_CONCRETE ∧ tested method returns `boolean`/`java.lang.Boolean` ∧ the path condition contains at least one clause naming **every** widened parameter ∧ `concretization_events = 0` | **widen** — the asserted relation lives in the PC (the classifier-javadoc benign case); path-exactness pins the branch for every admitted input |
| 4 | anything else — including every empty-PC NULL_CONCRETE case | **no license** → typed exclusion `ORACLE_NOT_WIDENABLE` |

Rule 2's events condition is empirically load-bearing, not defensive: a corpus with
String inputs flowing into unmodeled parsing (`concretization_events > 0`, empty PC)
produced THROWN oracles whose widened inputs branch inside the concretized region and
fail elsewhere instead of reaching the expected throw — every such property fails
validation after a full build+run cycle. Concretized branches break the path-uniqueness
argument for throws exactly as they do for booleans (rule 3).

Justification for rule 3's shape, and its known residual risk:
- A *computed* boolean (`return a == b`) compiles to a branch, so a boolean result depending on a symbolic input forces a PC clause naming it — requiring the clause is requiring the evidence.
- A *pass-through* boolean (`return this.storedFlag` — store/load, no branch) varies with input while leaving the PC empty. This is why an empty PC is never licensable, even for boolean returns (observed: octotron `GetBoolean()` widened failures).
- Residual risk, accepted and documented rather than solved: a PC clause naming a parameter can come from a branch unrelated to the returned boolean while the return itself is pass-through. Rare; still caught by the validation net (`NonPassingTestFilter`); revisit only if post-change telemetry shows the class is populated.
- `concretization_events > 0` invalidates the inference — a concretized value branches without leaving clauses, so PC absence/presence proves nothing there.

## Policy for unlicensed generalizations

**Typed exclusion, not seed-only emission.** The generalization is excluded at `GENERALIZE_TESTS` with `exclusion_info = 'ORACLE_NOT_WIDENABLE'` (mechanism: the existing `is_included = false` + `exclusion_info` path, same as other typed exclusions).

Why not emit a seed-only property: it pollutes the IMPROVED-vs-NAIVE comparison with vacuous rows (an "improved" test that explores nothing), and the corpus no longer runs a BASELINE variant, so seed-replay carries no evaluation value.

Consequence, accepted deliberately: currently-passing unlicensed generalizations (survived 100 trials on sampling luck — their claim was never licensed) are excluded too. The validated corpus shrinks; the evaluation gains a named, quantified limitation category (`ORACLE_NOT_WIDENABLE` counts per project) that is honest about what path-exact extraction can and cannot oracle-check. Completeness recovery is `2026-07-03-boxed-output-capture` (converts the dominant NULL_CONCRETE slice into SYMBOLIC, restoring rule-1 licenses), not a weaker license.

## Where it lives

The decision executes in `TestGeneralizationTask` (it already loads the output model, the assertion, and the planner inputs, and already owns the expected-side-replacement branch this gate protects). Inputs to the rule, all available there or on the assertion record: output model shape (`modelOutput` null/constant/symbolic — same derivation as `OutputSpecClassifier`), tested method return type (from the re-materialized tested method), PC clauses per parameter (the planner's `ConstraintClause` view), `concretization_events` (assertion column). No schema change; no new stage.

`OutputSpecClassifier`'s javadoc is updated to name both NULL_CONCRETE siblings (benign boolean-in-PC vs unlicensed concrete-oracle) and point to the license rule as the consumer.

## Acceptance

- Model tests cover every license row: SYMBOLIC widens; CONSTANT widens; NULL_CONCRETE boolean with param-naming clauses and zero concretization events widens; NULL_CONCRETE boolean with empty PC excluded; NULL_CONCRETE boolean with concretization events excluded; NULL_CONCRETE non-boolean excluded (with and without PC clauses).
- Spike re-run: widened-failure rate (`tries > 1 ∧ ASSERTION_FAILED`) collapses toward the SYMBOLIC base rate (≈1%); `ORACLE_NOT_WIDENABLE` exclusion counts recorded per project; every excluded generalization would previously have been NULL_CONCRETE; zero SYMBOLIC/CONSTANT generalizations newly excluded.
- The invariant is cross-referenced from `2026-06-28-clause-driven-input-generation` so future planners inherit it by reference, not rediscovery.
