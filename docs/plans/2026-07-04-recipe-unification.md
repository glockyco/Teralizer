---
title: Recipe Unification — One Codegen Path
type: spec
status: active
created: 2026-07-04
parent: 2026-07-04-architecture-implementation-review
---

# Recipe Unification — One Codegen Path

**One concern:** the invocation recipe becomes a degenerate expression recipe, so exactly one
codegen path exists from recipe to wrapper and from recipe to generalized test. The
sentinel-int encoding in `GeneralizableInput` is replaced by the recipe's typed `InputKind`,
and the two mega-tasks split into orchestrators plus pure, unit-testable builders. Tier A of
`2026-07-04-architecture-implementation-review` (A3 → A1 → A2 → A4).

## Why

Post-R1, both `JpfInstrumentationTask` and `TestGeneralizationTask` carry branch pairs
(`expressionRecipe ? … : …`): two wrapper builders, two call-site builders, two site-rewrite
blocks. The invocation path is index-keyed (`methodArgumentIndex`/`constructorArgumentIndex`
with −1/−2 sentinels) while the expression path is path-keyed. Every future codegen change pays both
paths. Structurally, `assertEquals(7, gcd(a, b))` IS an expression recipe whose oracle
expression is the call and whose sites are its argument positions — the distinction is
historical, not semantic.

## Design

### A3 — one input-site vocabulary

`GeneralizableInput` drops the sentinel ints as its public contract. Each site carries:

- `InputKind kind` — `METHOD_ARG | CTOR_ARG | RECEIVER_CTOR_ARG | EXPRESSION_SITE` (the
  recipe's existing enum, extended with `EXPRESSION_SITE`),
- its site path (already persisted per site by the recipe),
- parameter/argument records as today.

The int accessors go. The recipe JSON keeps `methodArgumentIndex`/`constructorArgumentIndex`
fields only as far as consumers still need positional info during the transition inside A1.
At the end of A1 the persisted schema carries `kind` + `path` and the index fields are gone
(schema v3, clean cut like v2: no dual-version reads, version bump enforced in `fromJson`).

### A1 — invocation recipes become expression recipes

`TestAnalysisTask` derives every recipe the same way. The oracle expression is the asserted
actual expression when admissible, otherwise the resolved call. Sites are path-addressed
positions inside it. For the plain-call case the derived sites are exactly the argument
positions (kind `METHOD_ARG`), receiver-constructor args keep kind `RECEIVER_CTOR_ARG`,
inline-ctor args keep `CTOR_ARG` — the derivation logic is shared, only the admissible-shape
walk differs.

Codegen consumes ONLY the path-based rewrite (`Resolved.replaceInputSitesWithParameterReads`):

- `JpfInstrumentationTask`: one `createInstrumentedMethod`, one `createInstrumentedMethodCall`.
  The `_target_` receiver parameter and `_local_*` lifted locals remain — they become
  explicit wrapper-environment concerns computed AFTER site rewriting, not a second rewrite
  mechanism. Two type roles stay distinct. The recipe's `oracleExpressionType` is the
  oracle's SEMANTIC type — the tested method's declared return type for a plain call, the
  expression's static type for a composite — and it feeds `ReturnTypeFilter` and the
  widening license. The WRAPPER's Java return type is a codegen concern: the
  assertion-context-inferred type (`inferExpectedType`, e.g. `long` for an
  `assertEquals(long, long)` overload around an int-returning call), computed at
  instrumentation time from the resolved oracle expression as today. Conflating the two
  breaks the filter: an `assertEquals(Object, Object)` context would infer `Object` and
  wrongly reject a supported oracle.
- `TestGeneralizationTask`: the index-keyed argument-replacement block is deleted. The
  path-based rewrite is the only site rewrite.
- The listener's two capture modes collapse: capture is ALWAYS at the wrapper exit.
  For a plain-call wrapper (`return call(...)`) the wrapper's return attr is the call's return
  attr — same capture, one mechanism. `wasTargetEntered` stays an observation.
  `TARGET_NOT_ENTERED` remains a failure only where the recipe's oracle expression is the
  tested call itself (the wrapper cannot exit normally without entering it — enforced as an
  assertion on that path, not a mode flag). The `expression_recipe` JPF config flag is
  removed.

### A2 — Task/Builder split

- `InstrumentedClassBuilder` (spoon.analysis or a new codegen package): recipe + records-free
  inputs in → `CtClass` out. No DB, no filesystem, no Velocity.
- `GeneralizedTestBuilder`: same shape for the generalized test class.
- The tasks keep: scheduling, record bookkeeping, path management, Velocity artifacts
  (driver + JPF config), file writing, DB stores. Target sizes: each task ≤ ~300 lines;
  each builder unit-tested directly against Spoon models.

### A4 — resolver extraction

- `FocalTypeResolver` (focal inference + `TypeIndex`/`Focal` caches) and
  `InputTopologyClassifier` (`classifyShape`, `receiverProvenance` + helpers) move out of
  `MethodUnderTestResolver`. Caches move from static synchronized WeakHashMaps to
  `TaskContext` (per-project lifetime, no weak-ref subtlety, no synchronization beyond what
  TaskContext already provides).
- "Telemetry never changes the pick" becomes structural: the classifier has no access to
  resolution state.

## Invariants (the review gates)

1. Recipe JSON v3 round-trips. v1/v2 payloads are rejected.
2. The 10-fixture corpus goldens are byte-identical before and after each landing task. This
   wave is behavior-preserving by definition, so ANY golden movement is a defect in the wave.
3. The sentinel invocation-shaped census stays identical to the pinned headers, and the
   expression rows may not regress (the two included JadConfig wins stay included).
4. Generated-file text for the corpus fixtures stays semantically identical Java. Cosmetic
   pretty-printer differences (parenthesization, qualification) are acceptable ONLY where a
   golden does not pin them.

## Verification economy

Per task: targeted unit tests. Per landing (A3, A1, A2, A4): one fixture-corpus run
(`verify-pipeline.sh`, single). At wave end ONLY: the determinism double-run and one sentinel
comparison. First-run numbers stand.

## Out of scope

- Any change to planner/license/filter semantics.
- The harness-support jar (own spec) and Velocity artifacts (driver/config stay templated).
- R2/statement slices.
