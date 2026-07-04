---
title: R1 Expression-Slice Recipes
type: spec
status: active
created: 2026-07-04
parent: 2026-06-26-teralizer-overview
---

# R1 Expression-Slice Recipes

**One concern:** the unit of generalization widens from "one `CtInvocation`" to "the asserted
actual expression": the recipe records the whole expression with its input sites, the
instrumented wrapper's body IS that expression, capture happens at the wrapper exit, and the
oracle type is the expression's type. Scoped by the per-shape evidence in
`2026-07-04-r1-viability-spike`; opportunity bounds in `2026-07-02-input-topology-spike` (T2,
~7-8k assertions upper bound, realized share measured by `actual_shape` telemetry).

## Evidence this design rests on

- 8/9 hand-built expression wrappers produce sound included properties through today's
  unmodified extraction + license machinery; the ninth (control-flow-mediated library value)
  refuses typed (`2026-07-04-r1-viability-spike`). No new soundness machinery is required.
- Instrumentation is already a synthetic-wrapper recipe (seam review finding 2): the builder
  clones the test class, lifts inputs to wrapper parameters, and emits `return <rewritten>`.
  R1 generalizes the rewritten body from one invocation to one expression.
- The recipe seam (R-A) is first-class: one derivation in `TestAnalysisTask`, persisted JSON,
  two consumers. R1 is a recipe-payload change, not a three-task rewrite.

## Design

### Recipe schema v2 (`GeneralizationRecipe`)

- `CURRENT_VERSION` 1 → 2 (recipes are derived per run; no persisted v1 rows are consumed
  across binaries).
- `oracleExpressionPath` resolves to `CtExpression` (was `CtInvocation`) — the asserted
  actual expression, path-relative to the containing test method as today.
- New field `oracleExpressionType`: the expression's static type (Spoon `getType()`),
  qualified name. This is what `ReturnTypeFilter` gates on and what the generated property
  casts/compares with. `oracleType` (the focal method's return type) remains for
  attribution-facing consumers.
- `oracleMethodPath` keeps pointing at the fusion-picked focal method. Attribution and
  recipe decouple (the three-role split): the focal pick is telemetry/reporting; the
  expression is what runs.
- `InputKind` unchanged (METHOD_ARG, CTOR_ARG, RECEIVER_CTOR_ARG); sites may now come from
  any call/ctor inside the expression, so a site's path alone locates it — the kind is
  descriptive, not positional.

### Derivation scope (structural screen, v1 admit-list)

`GeneralizableInput.derive` walks the asserted actual expression. An expression is
R1-admissible iff every node is one of:

- literal (kept in place — operator operands and non-lifted args stay part of the oracle
  shape; only call/ctor **argument positions** with supported types become input sites),
- invocation whose receiver is itself an admitted sub-expression or whose target is static,
- constructor call,
- unary/binary operator over admitted sub-expressions,
- cast of an admitted sub-expression.

Everything else — variable reads, field reads, array access, lambdas/method refs,
assignments — makes the expression non-self-contained and falls back to today's T0/T1
derivation (single resolved invocation) or its existing rejection. External-receiver
composites (`c.compare(i1,i2) > 0` where `c` is a local) are T3-adjacent and stay out of v1;
the `receiver_provenance` telemetry sizes them for the R2 decision. Real
`equals(Object)`-based equality stays out of v1 (virtual dispatch + `instanceof`
degeneration, per the spike caveat); `assertEquals(obj1, obj2)` object equality is unchanged
from today.

### Instrumentation (`JpfInstrumentationTask`)

The wrapper body becomes `return <expression-with-sites-rewritten>`, using the same
site-rewrite machinery that receiver-ctor support added. Wrapper return type =
`oracleExpressionType`. Everything else (driver, `@Before` execution, `symbolic.method` on
wrapper params, constraint collection) is unchanged.

### Capture (`TestGeneralizationListener`)

The return-attr capture and search termination move from the tested-method frame exit to the
**instrumented wrapper's** frame exit (the depth-pinning logic transfers unchanged — the
wrapper is the pinned frame). The focal method's entered/exited signal remains recorded:
`wasTargetEntered` becomes an observation (a short-circuited focal call is a legitimate
concrete path for an expression oracle), not an extraction gate; the wrapper itself is always
entered by the driver. `TARGET_NOT_ENTERED` therefore only remains a failure outcome for
T0/T1 recipes, where wrapper exit and tested-method exit coincide.

### Filters

`ReturnTypeFilter` gates on `oracleExpressionType` when the recipe is expression-shaped,
`oracleType` otherwise. `ParameterTypeFilter` consumes the lifted sites exactly as it
consumes ctor-lifted sites today.

### Generation (`TestGeneralizationTask`)

No structural change: the supplier re-executes the wrapper-shaped expression with generated
inputs, the expected side comes from the SPF output model as today, and the widening license
applies unchanged — the spike showed it already covers the degenerate expression classes
(boolean-in-PC licensed, concretized non-boolean refused).

## Risks (bounded, named)

- Side effects inside the expression run once per trial — the same class of risk as today's
  T0/T1 re-invocation; no new category.
- Deep chains through unresolvable library types fail the type gates — measured by
  telemetry, not guessed.
- The v1 self-containment screen shrinks the realized share below the 7-8k upper bound —
  intended; the screen is the soundness boundary, and telemetry measures what it excludes.

## Acceptance

- New fixture in the verification corpus: a test class asserting the spike's expression
  shapes **directly** (unwrapped — `assertTrue(intCompare(a,b) > 0)` style), generalized via
  R1 recipes, reproducing the spike's per-shape outcomes through the real seam (golden pins
  them: SYMBOLIC shapes included FULL, boolean-in-PC shapes licensed FULL, library-size
  shape refused ORACLE_NOT_WIDENABLE).
- `scripts/verify-pipeline.sh` green twice, identical: the nine existing fixtures are
  T0/T1-only and their goldens must not move.
- Sentinel subset census unchanged (T0/T1 paths byte-identical; the sentinel projects
  contain no R1-admissible shapes that currently generalize).
- Recipe round-trip: v2 JSON persists and resolves for both expression-shaped and
  invocation-shaped recipes; v1-consuming code paths removed (no dual-version support).
- Focal-entered observation recorded for expression recipes (column or task info), never an
  extraction gate for them.
