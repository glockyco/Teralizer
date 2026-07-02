---
title: Residual-Aware Input Generation
type: spec
status: superseded
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
superseded_by: 2026-06-28-clause-driven-input-generation
archived: 2026-07-02
---

Define the next `IMPROVED` generator architecture as a typed, residual-aware compiler from SPF input specifications to jqwik arbitraries.

## Goal

Make `IMPROVED` generate values that satisfy as much of the path condition as possible by construction while preserving Teralizer's path-exact contract. The generated test remains plain jqwik source: no runtime SMT solver, no hidden sidecar process, and no overapproximation unless a residual filter still enforces the original condition.

## Problem

`IMPROVED` already uses a `flatMap` chain and can propagate simple previous-parameter bounds into later parameter generators. The current bottleneck is broader:

- `VariableConstraintExtractor` recognizes only atomic `var op var|const` numeric constraints.
- Compound constraints such as `a + b < n` stay in the full input predicate.
- The generator always appends the full `inputJava` filter when an input predicate exists, even for clauses already encoded in a parameter arbitrary.
- The code path is numeric-shaped, so strings, arrays, and object constructors would require parallel ad-hoc logic if added directly.

Filtering is acceptable as a safety net. The design optimizes for soundness first: a value that reaches the property must still satisfy the SPF-derived input predicate.

## Architecture

Introduce a typed planning layer between the SPF model and Java source emission.

```text
SPF input model
  -> ConstraintClause list
  -> InputGenerationPlanner
  -> ParameterGenerationPlan
  -> jqwik source emitter
```

The planner owns constraint reasoning. The emitter only renders recipes into jqwik code.

Core concepts:

- `ConstraintClause`: one top-level conjunct from the input model, with a stable clause id and original model expression.
- `TypeDomain`: normalized parameter domain (`INTEGER`, `REAL`, `BOOLEAN`, `CHAR`, `STRING`, `ARRAY`, `OBJECT`).
- `DomainPlanner`: a small strategy object that can consume clauses for one domain and produce a generation recipe.
- `ParameterGenerationPlan`: the recipe for one parameter plus the clause ids it consumes.
- `InputGenerationPlan`: ordered parameter plans, consumed clause ids, residual clause ids, and extraction metrics.
- `ResidualPredicate`: the Java expression rendered from unconsumed clauses. If no residual clauses remain, no final filter is emitted.

Suggested interfaces:

```java
interface DomainPlanner {
    boolean supports(TypeDomain domain);
    ParameterGenerationPlan plan(ParameterContext parameter, PlanningContext context);
}

final class ParameterGenerationPlan {
    private final MethodParameter parameter;
    private final GenerationRecipe recipe;
    private final Set<Integer> consumedClauseIds;
}

interface GenerationRecipe {
    String emit(EmitContext context);
}
```

The first implementation may keep the full `inputJava` filter while the plan/recipe boundary lands. Residual-only filtering is the preferred follow-up once clause accounting is reliable.

## Initial domain support

### Integer, long, short, byte, char

Support constants, closed/open bounds, equality, and variable-dependent bounds against already-generated parameters.

Examples:

- `b > a` -> `bMin = a + 1`.
- `b >= a` -> `bMin = a`.
- `b == a + 1` -> `Arbitraries.just(a + 1)` when overflow-safe.
- `a + b < n` -> for later `b`, `bMax = n - a - 1` for integral domains.

Overflow handling must be explicit. If a transformed bound can overflow, keep the clause residual instead of emitting an unsound bound.

### Real, float, double

Support constants, closed/open bounds, equality, and affine variable-dependent bounds against already-generated parameters.

Examples:

- `b > a` -> lower bound `a`, exclusive.
- `a + b <= n` -> for later `b`, upper bound `n - a`, inclusive.

Special values policy:

- finite path bounds use jqwik `between(..., included, ..., included)`;
- `NaN` and infinities remain residual unless a domain planner explicitly supports them;
- unbounded real domains use a deliberate finite distribution only when the path condition supplies finite bounds, otherwise the existing jqwik default is retained with the residual filter.

### Boolean

Support equality and inequality when the input model exposes them. Otherwise use `Arbitraries.booleans()` and residual filtering.

### String

Do not implement full string solving in the first pass. Reserve the extension point for length, character-range, prefix/suffix, contains, and regex clauses.

The target jqwik backend is `Arbitraries.strings()` with length and character configurators where possible; unsupported clauses remain residual.

### Arrays and collections

Do not implement in the first pass. Reserve an `ArrayDomainPlanner` that separates length recipes from element recipes.

Examples for later:

- concrete length -> `.array(type).ofSize(n)`;
- symbolic length generated first -> `get_array(n)` via `flatMap`;
- element constraints -> element arbitrary plus residual when quantifiers or aliasing are not supported.

### Objects

Do not implement arbitrary heap graphs in this pass. Keep object support as construction recipes over already-derived scalar inputs: inline constructor receivers and inline constructor arguments described by `2026-06-27-generalizable-input-rule`.

Future object graph generation may use Korat/UDITA-style bounded generation, but that is a separate stateful-object track.

## Library boundary

Use jqwik as the generated-test runtime backend. Its `map`, `flatMap`, `combine`, strings/chars/collections, shrinking, and deterministic seeds are the right primitives for emitted properties.

Do not make Choco, Z3, or another solver part of the default generated tests. A solver backend can be considered later for hard residual domains, but it would add runtime dependencies, weaken jqwik shrinking, and make generated tests harder to explain.

## Metrics

Every generated `IMPROVED` property records enough planning metadata to explain behavior:

- total top-level clauses;
- consumed clauses;
- residual clauses;
- whether a final filter remains;
- existing used/total constraint counts for backward compatibility.

The JARVIS comparison should report generator shape and PVC together. A PVC loss with a large residual filter is a generator-planning issue; a PVC loss with no residual filter is a distribution/sampling issue.

## Acceptance criteria

- Constraint reasoning is isolated from Java source emission.
- Numeric and char planners support atomic bounds and simple affine two-variable constraints without changing jqwik test structure.
- Unsupported clauses remain sound through a residual filter or the current full filter during the transition.
- The architecture can add string, array, and object planners without rewriting numeric planning.
- Generated tests remain plain jqwik source with no default runtime solver dependency.
- Scoreboard evidence distinguishes by-construction constraints from residual filtering.
