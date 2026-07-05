---
title: Parse-Predicate Admission — ISINTEGER/NOTINTEGER as Sound String Clauses
type: spec
status: draft
created: 2026-07-05
parent: 2026-06-26-teralizer-overview
---

# Parse-Predicate Admission — ISINTEGER/NOTINTEGER as Sound String Clauses

**One concern:** SPF's string handler records `Integer.parseInt` on a symbolic string as an
`ISINTEGER`/`NOTINTEGER` path-condition clause, but Teralizer's ingestion refuses the
comparator as unsupported, so every parse-guarded string MUT dies typed at ingestion. Admit
the two comparators to the sound set — rendered as an exact parseability predicate and
generated as satisfying partitions — so parse-guarded MUTs convert from typed exclusions to
generalizations.

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

## Design

1. **Ingestion.** Map `ISINTEGER(s)` / `NOTINTEGER(s)` in `SpfToModelTransformer`'s string
   comparator switch to a Model predicate over the string variable (an `Invocation` with a
   dedicated method symbol, negated via `Not` for the NOT side), admitted through
   `MethodCapabilities` like the shipped string predicates.
2. **Rendering — exact semantics, no approximation.** The predicate renders against an
   emitted helper in the generated test class (the house pattern for generated support
   code): `try { Integer.parseInt(s); return true; } catch (NumberFormatException e)
   { return false; }`. Delegating to `parseInt` itself makes sign handling, leading zeros,
   radix-10 digits, and overflow exact by construction — a regex approximation would get
   overflow wrong.
3. **Generation.** `StringDomainPlanner`: an `ISINTEGER` clause yields a satisfying
   arbitrary of integer-valued strings (`Arbitraries.integers().map(String::valueOf)`,
   seed-first per the `edgeCases=FIRST` convention); `NOTINTEGER` yields the bounded ASCII
   default with the rendered predicate left to the residual filter. Generation must satisfy
   the partition, not cover it — a satisfying subset is sound.
4. **Scope boundary — parseability only.** When the MUT also uses the *parsed value* (the
   result of `parseInt` flows into later branches), the path condition carries
   `SpecialIntegerExpression` terms, which `SpfToModelTransformer` already refuses typed.
   That refusal stands: this spec converts MUTs whose branch-relevant fact is
   *parseability*; parsed-value dataflow stays a typed exclusion.

## Acceptance

- Unit tests: transformer maps both comparators to the model predicate; renderer emits the
  helper-delegating predicate; planner produces a satisfying integer-string arbitrary for
  `ISINTEGER` and the residual-filtered default for `NOTINTEGER`; a
  `SpecialIntegerExpression`-carrying constraint still refuses typed.
- New fixture: a boolean-returning MUT guarded by `Integer.parseInt` parseability (both
  branches seeded), golden pinning the conversion from typed exclusion to a widened
  generalization. A second arm using the parsed value pins the preserved typed refusal.
- One `scripts/verify-pipeline.sh`, other goldens byte-identical.
- Corpus-scale conversion (xenqtt `AppContext` family and siblings) batches into the next
  scheduled corpus evaluation event per the measurement policy in AGENTS.md.

## Non-goals

- `ISFLOAT`/`ISLONG`/`ISDOUBLE` and the radix overload of `parseInt` (same pattern,
  admitted later if evidence ranks them).
- Modeling the parsed value (`SpecialIntegerExpression`) — a separate, larger concern.
- Any change to `SymbolicStringHandler`'s collect-mode parse behavior (shipped and pinned).
