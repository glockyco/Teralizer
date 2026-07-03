---
title: Input Topology Spike
type: audit
status: active
created: 2026-07-02
parent: 2026-07-02-mut-id-confidence-fusion
---

# Input Topology Spike — where do generalizable inputs live?

Corpus evidence for the question the MUT-id fusion spec did not yet answer: **the "tested
method" (whose output the assertion checks) is frequently not the call whose inputs get
parameterized.** In `assertTrue(new Thing(a, b).isOk())` the oracle is `isOk()` — zero
parameters — but the generalizable inputs are the constructor's `a, b`. This audit
classifies every supported-shape assertion in `postgres_test` (RepoReapers, 122,166
assertions / 425 projects; 92,776 of `assertEquals`/`assertTrue`/`assertFalse`) by the
*shape of the asserted actual expression*, sizes each input-topology case against the
current filter outcomes, and recommends which recipe increments are worth building.

Classifier: `analysis/src/teralizer/input_topology.py`
(`uv run --directory analysis python -m teralizer.input_topology`). Textual heuristic over
Spoon-printed source — expect a few percent noise; the AST-exact successor is the
`actual_shape` column in `mut_resolution_observation`. Caveat: `postgres_test` predates
both the string-support branch and the receiver-constructor support (`d8c50af9`,
2026-06-28), so `ReturnTypeFilter` rejects are overstated for String returns and the
`CTOR_RECEIVER_*` rows show the *pre-support* state.

## The three roles (first-principles model)

Every generalizable assertion involves three distinct roles that the current architecture
conflates into one `CtInvocation`:

1. **Input sites** — the expression positions where generated values enter (method
   arguments, constructor arguments, literals in the slice). SPF symbolizes these.
2. **Oracle expression** — the expression whose value the assertion checks and whose
   SPF-derived symbolic form becomes the expected side. Its *type* must be supported
   (`TypeCapability.supportsReturnValue`).
3. **Focal attribution** — the production method the test is *about* (for reporting,
   mutation analysis, and the paper's claims). Fusion tiers grade this.

`assertEquals(3, gcd(a, b))` collapses all three onto `gcd` — the case the pipeline was
built around. `new Thing(a, b).isOk()` splits them: inputs at the constructor, oracle at
`isOk()`, attribution arguably the constructor+`isOk` pair. The generalization **recipe**
(the code the generated property re-executes) is what unifies them: re-run the whole
expression with generated values at the input sites, compare against the SPF expectation
for the oracle expression. Recipes differ only in how much context they must reproduce.

## Shape × first-reject cross-tab (postgres_test, 2026-07-02)

| shape | Excluded | MissingValue | ParamType | ReturnType | NONE | TOTAL |
|---|---:|---:|---:|---:|---:|---:|
| SINGLE_CALL | 9,728 | 11,171 | 6,936 | 25,407 | 921 | **54,163** |
| CHAINED_CALLS_END0ARG | 2,071 | 4,794 | 839 | 3,410 | 0 | **11,114** |
| VARIABLE | 787 | 3,432 | 390 | 3,860 | 126 | **8,595** |
| OPERATOR_COMPOSITE | 1,493 | 5,413 | 0 | 0 | 0 | **6,906** |
| FIELD_OR_QUALIFIED_NAME | 895 | 2,866 | 0 | 12 | 0 | **3,773** |
| CHAINED_CALLS_ENDNARG | 625 | 1,794 | 117 | 686 | 24 | **3,246** |
| LITERAL | 462 | 1,948 | 0 | 7 | 0 | **2,417** |
| ARRAY_INDEX | 112 | 1,010 | 0 | 0 | 0 | **1,122** |
| CTOR_ONLY | 388 | 334 | 0 | 0 | 0 | **722** |
| CTOR_RECEIVER_CALL_0ARG | 3 | 64 | 26 | 254 | 0 | **347** |
| CTOR_RECEIVER_CALL_NARG | 10 | 8 | 15 | 161 | 1 | **195** |
| LAMBDA_OR_METHODREF | 1 | 94 | 0 | 31 | 0 | **126** |
| CHAINED_CALLS (middle-0arg) | 5 | 22 | 0 | 23 | 0 | **50** |

Key drill-downs:

- **SINGLE_CALL splits by pick arity**: 34,586 resolved picks are **zero-argument
  inspectors** (`obj.getFoo()`) vs 19,577 has-args picks. The zero-arg family rejects at
  ParameterType (5,835), ReturnType (11,854), or was excluded/`MissingValue` — its oracle
  value is determined by *receiver state built elsewhere*, not by any argument. This is
  the single biggest family in the corpus.
- **OPERATOR_COMPOSITE** (6,906): 1,241 `instanceof` (degenerate — type-check oracles),
  701 pure variable/literal comparisons, **4,964 contain a call** (`c.compare(i1,i2) > 0`,
  `immediate.compareTo(high) < 0`) — slice-viable; all currently die at `MissingValue`
  because multi-producer expressions abstain.
- **CHAINED_*** (14,410): 8,643 have ≥1 non-empty argument list (input sites exist);
  5,532 end in a call whose name suggests a supported oracle type (`.size()`, `.isX()`,
  `.getAsInt()`, …); **2,229 have both**; 1,755 of those are additionally self-contained
  (first segment is itself a call with args — no external receiver state needed).
- **CTOR_RECEIVER_CALL_*** (542): the user-named case. Already implemented end-to-end on
  this branch (`GeneralizableInput` `RECEIVER_CONSTRUCTOR_ARGUMENT_INDEX`,
  `JpfInstrumentationTask:196-259`, `TestGeneralizationTask:481-491`) — the table shows
  the pre-support DB state. Small corpus share (0.6%), but the same *mechanism* (lift
  constructor args to inputs) is what T2 recipes generalize.

## Input-topology taxonomy

| Case | Example | Input sites | Oracle | Recipe needed | Status |
|---|---|---|---|---|---|
| **T0 direct** | `assertEquals(3, gcd(a,b))` | args of the call | call result | single call (today's) | supported |
| **T1 inline-ctor receiver** | `new Thing(a,b).isOk()` | ctor args | inspector result | ctor + call | **supported** (`d8c50af9`); also inline ctor *arguments* (`0ca8e48e`) |
| **T2 expression slice** | `build(x).size()`, `c.compare(i1,i2) > 0`, `new Thing(a,b)` equality | literals/args anywhere in the actual expression | whole-expression value | re-execute the asserted expression with symbolized sites | **not supported — recommended next** |
| **T3 statement slice** | `Thing t = new Thing(a,b); t.add(c); assertEquals(2, t.size())` | ctor/mutator args in *prior statements* | inspector on receiver | replay the receiver-building statement slice | not supported — decide after T2 + telemetry |
| **T4 fixture state** | receiver built in `@Before`/helper | cross-method | inspector | cross-method slice + environment capture | **out of scope** |
| **T5 environment** | I/O, time, mocks, statics | not in test code | any | can't re-execute soundly | **out of scope** (research wall) |

## Recipe increments — cost/value

### R0 = the fusion plan (`2026-07-02-static-mut-id-fusion`) — committed
Better *attribution and recall within T0/T1*: dataflow through variables/fields, ranked
guesses, provenance. Addresses the `VARIABLE` (8.6k) and part of the `MissingValue`
buckets. Does **not** move the input-topology walls: a T2/T3 shape stays rejected, just
with an honest observation row instead of a silent null.

### R1 = expression-slice recipes (T2) — recommended, medium effort
Generalize the *unit of generalization* from "one `CtInvocation`" to "the asserted actual
expression": SPF instruments a synthetic method whose body is the expression with input
sites lifted to parameters; the generated property re-executes that expression with
generated values; the oracle is the expression's value (type-gated as today); the
coherence seed-check applies unchanged (the expression evaluated at the original inputs
must reproduce the original oracle value).

- **Opportunity (upper bounds, pre-filter):** 2,229 chained-with-sites+supported-end +
  4,964 operator-composite-with-calls + 722 ctor-only (needs `equals`-oracle support on
  the constructed object — count only if that lands) ≈ **7–8k assertions**, concentrated
  in `MissingValue`/`ReturnType` rejects that R0 cannot touch. Realized gain will be
  lower (external receivers in the chain, unsupported literal types) — the
  `actual_shape` + `receiver_provenance` telemetry below measures it exactly.
- **Why it is structurally cheap:** it is the *same mechanism* as the shipped
  receiver-ctor support, applied uniformly. `GeneralizableInput` already models
  "input at a position inside a larger expression" (`constructorArgumentIndex`);
  the instrumented-method builder already rebuilds composite expressions with
  symbolized leaves. The pipeline schema already stores the full assertion source.
- **What changes:** `GeneralizableInput.derive` walks the whole actual expression (not
  one call's args); `JpfInstrumentationTask` emits the expression as the instrumented
  body; `ReturnTypeFilter` gates on the *expression* type, not the picked method's
  return type; focal attribution (fusion tiers) stays as-is — attribution and recipe
  decouple, which the fusion spec's three-role split makes explicit.
- **Risk:** side effects *inside* the expression execute once per property trial (same
  as today for T0/T1 — no new risk class); deep chains through unresolvable library
  types still fail type gates (measured, not guessed, via telemetry).

### R2 = statement-slice recipes (T3) — defer; instrument first
The 34.6k zero-arg-inspector family is the biggest prize, but the recipe must replay
receiver-building statements: which statements belong to the slice (aliasing, multiple
receivers), whether they are side-effect-safe to re-execute, and what the focal
attribution even is (the mutator? the inspector?). That is a research-grade slicing
problem, not an increment. **Decision gate:** the `receiver_provenance` column (below)
on the next rerun tells us how many zero-arg inspectors have a *local, ctor-rooted,
mutator-free* receiver — the only sub-family a sound v1 slice could take. If that
sub-family is large (>5k), design R2 properly; if small, T3 stays out of scope and the
paper names it as the stateful-setup wall alongside T4/T5.

### Out of scope (name it in the paper, don't build it)
- **T4 fixture/cross-method state** — requires environment capture; conflicts with the
  per-assertion pipeline unit.
- **T5 environment/mocks/IO/statics** — unsound to re-execute; JPF can't model it.
- `instanceof`/`assertSame`/identity oracles (1.2k+) — type/identity checks don't
  benefit from input generalization.
- `LITERAL`/`UNEXTRACTABLE` actuals (2.4k) — constant oracles; nothing to generalize.

## Telemetry to make the R2 decision (added to the fusion spec/plan)

Two columns on `mut_resolution_observation`, both cheap at resolution time:

- `actual_shape` — the AST-exact version of this audit's classifier (enum above).
- `receiver_provenance` — for a picked call with a receiver:
  `INLINE_CTOR | LOCAL_CTOR | LOCAL_CTOR_MUTATED | LOCAL_OTHER | FIELD | PARAM_OR_STATIC | NONE`.
  `LOCAL_CTOR` = receiver is a local whose reaching definition is a constructor call and
  no intervening statement invokes a method on it; `LOCAL_CTOR_MUTATED` = same but with
  intervening calls (the mutator-then-inspect family, R2's target).

With those populated, the R1 realized-gain and the R2 sub-family sizes are single
`GROUP BY` queries on the next rerun.

## Bottom line

- The user-named case (`new Thing(a,b).isOk()`) is **already supported** — inline
  constructor receivers/arguments landed 2026-06-28; `postgres_test` predates it.
- The general lesson stands: input sites ≠ oracle ≠ focal attribution. The fusion spec
  now names the three roles; recipes are the axis that scales.
- **Build next:** R1 expression-slice recipes (~7-8k upper-bound opportunity, mechanism
  already half-shipped) — after R0 lands and its telemetry sizes the realized share.
- **Decide with data:** R2 statement slices gated on `receiver_provenance` counts.
- **Declare out of scope:** fixture state, environment, identity/type-check oracles.
