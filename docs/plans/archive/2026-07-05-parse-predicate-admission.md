---
title: Parse-Predicate Admission — Parseability Comparators as Sound String Clauses
type: spec
status: implemented
created: 2026-07-05
parent: 2026-06-26-teralizer-overview
archived: 2026-07-05
---

# Parse-Predicate Admission — Parseability Comparators as Sound String Clauses

**One concern:** SPF's string handler records `Integer.parseInt` (and the `Long`/`Float`/
`Double` siblings) on a symbolic string as `ISINTEGER`/`NOTINTEGER`-family path-condition
clauses, but Teralizer's ingestion refuses the comparators as unsupported, so every
parse-guarded string MUT dies typed at ingestion. Admit the four comparator pairs
(`ISINTEGER`, `ISLONG`, `ISFLOAT`, `ISDOUBLE` and their negations) to the sound set —
rendered as exact parseability predicates and generated as satisfying partitions — so
parse-guarded MUTs convert from typed exclusions to generalizations.

## Why now

- The collect-mode conformance work (`2026-07-05-collect-mode-conformance`) fixed the
  parse-family crash class: `SymbolicStringHandler` follows the concrete parse outcome in
  constraint collection and records `ISINTEGER`/`NOTINTEGER`, and Teralizer refuses the
  comparator typed instead of crashing. The refusal is now the only thing between those
  MUTs and generalization; the xenqtt `AppContext` family is the named example.
- The machinery is the shipped string sound-set pattern: `MethodCapabilities` admission,
  `SpfToModelTransformer` mapping, `ModelToJavaTransformer` rendering, and a
  `StringDomainPlanner` partition — an addition to an established seam, not new
  infrastructure.
- Sentinel evidence (five-project subset, 2026-07-05): 30 typed parse-comparator refusals —
  `isdouble` 14, `isfloat` 6, `isinteger` 5, `islong` 5. The double family outranks the
  integer family, so the spec admits all four comparator pairs in one pass: the rendering
  helper and planner partition are identical per type, and each delegates to its own
  `parseX` for exact semantics (`parseDouble` accepts `NaN`/`Infinity`/hex floats — exactly
  why delegation beats a regex).

## Design

1. **Ingestion.** Map the four comparator pairs in `SpfToModelTransformer`'s string
   comparator switch to Model predicates over the string variable, admitted through
   `MethodCapabilities` like the shipped string predicates. The node is the
   STATIC-qualified `Invocation` form (`Invocation(null, <helper class>, "isInteger",
   [s])`), not the instance form — `String` has no such method, so an instance-shaped
   node would render invalid Java. The NOT side wraps in `Not`.
2. **Rendering — exact semantics, no approximation.** The static qualifier is a helper
   class emitted into the generated test by `GeneralizedTestBuilder`, the house pattern
   for generated support code (`FirstValueArbitraryFactory`, `JqwikValueRecorder`). Each
   predicate delegates to the real parser:
   `try { Integer.parseInt(s); return true; } catch (NumberFormatException e)
   { return false; }`, with `Long.parseLong`/`Float.parseFloat`/`Double.parseDouble` for
   the siblings. Delegation makes sign handling, leading zeros, overflow, and the
   float/double extras (`NaN`, `Infinity`, hex floats, trailing `f`/`d`) exact by
   construction — a regex approximation would get overflow and the float grammar wrong.
3. **Generation.** `StringDomainPlanner`: a positive parse clause yields a satisfying
   arbitrary of numeric strings for that type (`Arbitraries.integers().map(String::valueOf)`
   and the per-type analogues, seed-first per the `edgeCases=FIRST` convention); the
   negative side yields the bounded ASCII default with the rendered predicate left to the
   residual filter. Generation must satisfy the partition, not cover it — a satisfying
   subset is sound.
4. **Scope boundary — parseability only.** When the MUT also uses the *parsed value* (the
   result of `parseX` flows into later branches), the path condition carries
   `SpecialIntegerExpression`/`SpecialRealExpression` terms, which `SpfToModelTransformer`
   already refuses typed. That refusal stands: this spec converts MUTs whose
   branch-relevant fact is *parseability*; parsed-value dataflow stays a typed exclusion.

## Acceptance

- Unit tests: transformer maps all four comparator pairs to their model predicates;
  renderer emits the per-type helper-delegating predicates; planner produces a satisfying
  numeric-string arbitrary for each positive clause and the residual-filtered default for
  each negative; a `SpecialIntegerExpression`/`SpecialRealExpression`-carrying constraint
  still refuses typed.
- New fixture: a boolean-returning MUT guarded by `Integer.parseInt` parseability and one
  guarded by `Double.parseDouble` (both branches seeded), golden pinning the conversion
  from typed exclusion to a widened generalization. A third arm using the parsed value
  pins the preserved typed refusal.
- One `scripts/verify-pipeline.sh`, other goldens byte-identical.
- Corpus-scale conversion (xenqtt `AppContext` family and siblings) batches into the next
  scheduled corpus evaluation event per the measurement policy in AGENTS.md.

## Non-goals

- The radix overload of `parseInt` (admitted later if evidence ranks it).
- `ISBOOLEAN`/`NOTBOOLEAN` — `Boolean.parseBoolean` never throws, so the comparator
  carries no partition worth widening, and the sentinel recorded zero refusals for it.
- Modeling the parsed value (`SpecialIntegerExpression`) — a separate, larger concern.
- Any change to `SymbolicStringHandler`'s collect-mode parse behavior (shipped and pinned).
