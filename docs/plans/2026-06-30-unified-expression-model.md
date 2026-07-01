---
title: Unified Expression Model
type: spec
status: draft
created: 2026-06-30
parent: 2026-06-26-teralizer-overview
---

# Unified Expression Model

**Goal:** Replace the type-fragmented `teralizer.domain` expression zoo with a small, uniform model
that represents variables, constants, true operators, and method/function calls of *any arity*, gated
by a single capability registry. Adding an operation becomes one registry row + one SPF handler
instead of edits across four sites that drift out of sync.

**Sequencing (decided):** This refactor is the **next** work item and **precedes** static MUT-id. It
is the clean foundation the remaining string operations need; the leftover string ops
(`trim`, `toLowerCase`, `toUpperCase`, `replace`) are absorbed into Phase 1 rather than hacked in
ad-hoc. Only after this lands do we move on to `2026-06-30-static-mut-identification`. The
`2026-06-30-partial-sound-string-support` plan's Task 4b + corpus verification also fold into / follow
this refactor.

**Tech stack:** Java 8, Spoon AST, SPF/jpf-symbc (`SpfToModelTransformer` ingestion), the
`ModelVisitor`/`ModelFolder` traversal infrastructure, jqwik planners, Gson JSON adapters.

## Motivation — three broken layer contracts (verified at source)

The pain that surfaced adding string ops is not string-specific; it is three implicit contracts the
model never made explicit, so every new operation accretes a special-case.

1. **Representation: `Operation(left, Operator, right)` is strictly binary, but the domain is n-ary
   method calls.** Unary calls break the node's own invariant (`_trim()` yields
   `Operation(null, TRIM, receiver)`, yet `Operation.toString` asserts `left == null ⇒ right == null`);
   ternary calls (`replace`, `substring`, `valueOf`) do not fit and are stuffed into SPF's `oprlist`,
   which `SpfToModelTransformer.postVisit(DerivedStringExpression)` **silently drops** (the A-5 TODO).
   Math functions (`SQRT/POW/SIN/…`) only *look* fine because they are ≤2 args and fit the two slots
   by luck — the same latent mismatch.
2. **Ingestion (`SpfToModelTransformer`) is neither total nor consistent.** It reads only `left`/`right`,
   drops `oprlist`, and mixes the operand-slot convention (math unary uses `left`; string unary uses
   `right`). There is no guarantee "SPF emitted a term ⇒ the model represents it faithfully or refuses
   loudly."
3. **Soundness knowledge is scattered across four sites with no source of truth:** the `fold` arms
   (what renders), the `isStringOperator`/`isStringExpression` guards, `StringOperationFilter`'s
   hardcoded unsupported list (the pre-screen), and the planner's per-op logic. They drift — the
   `equalsIgnoreCase` bug (sound-set said "included," the SPF handler threw) was exactly this class.

Corpus-wide symptoms confirming it is general, not string-only:
- **Three byte-identical function nodes** — `SymbolicIntegerFunction`, `SymbolicRealFunction`,
  `SymbolicStringFunction` — with the same `fold` (`name + "(" + args + ")"`).
- **Per-type leaf nodes** `Variable{Integer,Real,String}` / `Constant{Integer,Real,String}`, and
  *inconsistently* — no `VariableChar`/`VariableBoolean` exists (char/boolean already ride the integer
  node), so the "one class per type" rule is already half-broken.
- The `Operator` enum conflates true operators (`+ - * / % == < & | ^ << …`) with function calls
  (`SQRT/POW/SIN/…`, `CONCAT/TRIM/EQUALS/STARTSWITH/…`, parse predicates, `EMPTY`).

## Target architecture

### Node model — six expression kinds

| Today | Target | Rationale |
|---|---|---|
| `VariableInteger` / `VariableReal` / `VariableString` (char/bool ride Integer) | **`Variable(name, TypeDomain)`** | type is data, not a class |
| `ConstantInteger` / `ConstantReal` / `ConstantString` | **`Constant(value, TypeDomain)`** | same |
| `SymbolicIntegerFunction` / `SymbolicRealFunction` / `SymbolicStringFunction` **+** the function-operators in `Operator` (`SQRT/POW/SIN/…`, `CONCAT/TRIM/EQUALS/STARTSWITH/…`) | **`Invocation(receiver?, qualifier?, method, args[])`** | one node for every method/function call, any arity; instance → `receiver.method(args)`, static → `qualifier.method(args)` (`Math.sqrt`, `String.valueOf`). Exactly one of `receiver`/`qualifier` is set. |
| `NOTEQUALS` / `NOTSTARTSWITH` / `NOTEMPTY` … twins | **`Not(operand)`** | one negation wrapper; retires every `NOT*`; renders `(!(…))` |
| `Operation(left, Operator, right)` | **retained**; `Operator` shrinks to **true operators only** — arithmetic (`+ - * / %`), bitwise/shift (`& | ^ << >> >>>`), comparison (`== != < <= > >=`) | operators and calls stop being conflated |
| `ArrayExpression` / `ArrayElementExpression` | unchanged | orthogonal |

Net: arity is native (`Invocation.args` — no `oprlist` drop, no unary-slot hack, no binary-node abuse);
exactly one representation per concept.

### Capability registry — the single source of truth

One table keyed by symbol (operator or method), replacing the four scattered sites:

```
Capability {
  spfCollectable    // SPF handler produces it soundly → drives the pre-screen + ingestion admission
  inputGeneratable  // planner can build a satisfying arbitrary (else filter-only backstop)
  outputRenderable  // fold can emit Java             → else typed NonGeneralizableExpressionException
  render            // Java method name, static qualifier (Math/String), instance-vs-static
}
```

This is the home for the **input-vs-output distinction**: an op may be
`outputRenderable = true, inputGeneratable = false` (e.g. `trim` as a return oracle but not a
satisfiable input constraint). Consumers: the pre-screen (`StringOperationFilter` generalizes to a
capability-driven screen), `SpfToModelTransformer` ingestion admission, `fold` rendering, and the
planners. Adding an op = one row + one SPF handler.

### Layer contracts (each independently testable)

- **SPF → Model** (`SpfToModelTransformer`): **total** — every term becomes a node or throws a typed
  `UnsupportedTerm`; no silent drops; n-ary via `Invocation.args`.
- **Model → Java** (`ModelToJavaTransformer` / `fold`): uniform — `Invocation` renders by descriptor,
  `Operation` by operator, `Not` by negation. `outputRenderable = false` ⇒ typed
  `NonGeneralizableExpressionException` (the existing per-record exclusion path).
- **Model → generation** (planners): consult the registry, nothing else.

## Migration — four phases (each independently shippable, green before the next)

1. **Foundations + string migration.** Add `Invocation` + `Not` nodes with `ModelVisitor`/`ModelFolder`
   hooks + the `Capability` registry. Rework `SpfToModelTransformer` so string terms
   (`StringConstraint`, `DerivedStringExpression` incl. `oprlist`) map to `Invocation`/`Not` totally.
   Add `fold(Invocation)`/`fold(Not)`; remove the string arms + `isStringOperator`/`isStringExpression`
   guards from `fold(Operation)`; retire string entries from `Operator`; delete `SymbolicStringFunction`;
   point the planner + screen at the registry. → unlocks `trim`/`toLowerCase`/`toUpperCase`/`replace`.
   **Green: string + native SPF tests.**
2. **Function migration.** Move `SQRT/POW/EXP/LOG/SIN/…/ATAN2` + `valueOf` → `Invocation` (static
   `Math`/`String`). Delete `SymbolicIntegerFunction`/`SymbolicRealFunction`; retire those `Operator`
   entries. **Green: the ~250 numeric/char/boolean generalizations + native tests.**
3. **Leaf unification.** `Variable{Integer,Real,String}` → `Variable(name, type)`;
   `Constant{…}` → `Constant(value, type)`. Update planners, visitor/folder, JSON adapters.
   **Green: full suite + 250 generalizations.**
4. **Delete legacy + finalize.** Remove retired `Operator` entries, dead nodes, the hardcoded
   `StringOperationFilter` list (now registry-driven); sync plan docs.

## Testing

- **Behavioral guardrail (not textual):** the ~250 numeric/char/boolean generalizations must still
  **compile and pass** (generated jqwik tests execute green) across Phases 2–4. This — not byte
  identity — is the invariant protecting the JARVIS results.
- **Golden snapshots for intended-unchanged cases:** capture rendered Java for a deterministic
  representative set of numeric/char/boolean/string specs as golden files; diff per phase. A diff is a
  *review prompt*, not an auto-failure — an intentional parenthesization/spacing change is accepted once
  its generated test still compiles+passes; an unintended semantic change is caught. (Byte-identical
  output is **not** required: `Invocation` may legitimately re-parenthesize vs the old
  `Symbolic*Function` / `Operator`-arm output.)
- **Unit:** new-node rendering (`Invocation` instance/static/n-ary, `Not`), registry lookups,
  **ingestion totality** (every SPF term → node or typed refusal), `Model → JSON → Model` round-trip.
- **Native SPF:** `TestSymbolicString{Symcrete,IsEmpty,EqualsIgnoreCase}` stay green; new
  `toLowerCase`/`toUpperCase` handlers get their own.
- **Per-phase gate:** full `./gradlew test` + native SPF tests green.

## Risks & tradeoffs

- **Numeric-path churn is the main risk** (Phase 2 touches the render path behind ~250 generalizations
  + the JARVIS results). Mitigation: the behavioral guardrail + golden snapshots above, and Phase 2 is
  isolated from the string work (Phase 1) so a regression is localized.
- **Blast radius:** `ModelVisitor`/`ModelFolder` gain/lose node hooks; every planner, the JSON adapters,
  and the pre-screen are touched. Phasing keeps each change small and green.
- **JSON format changes** (node shapes change), but the spec is regenerated per run (not persisted
  across versions), so **no data migration** is needed.

## Non-goals / deferred

- **Tier-3 string ops stay deferred** even after this refactor lands: `compareTo`/`compareToIgnoreCase`
  (lexicographic-ordering generation), `matches`/`replaceAll` (regex), `substring`/`charAt`/
  `regionMatches` (SIOOBE bounds fork). The refactor makes them *cheaper* to add later (one row + one
  handler) but does not add them.
- **Arrays** (`ArrayExpression`/`ArrayElementExpression`) are untouched.
- **MUT-id** (`2026-06-30-static-mut-identification`) follows this refactor.
