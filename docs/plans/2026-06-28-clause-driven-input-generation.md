---
title: Clause-Driven, Multi-Type Input Generation
type: spec
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Redesign the `IMPROVED` input-generation seam so new parameter types are added by registering one planner, ground it in what Symbolic PathFinder can actually specify (reusing the existing spf-eval characterization), and make the whole path self-report what it could and could not represent.

## Goal

Three coupled outcomes:

1. **Extensible by construction.** Adding `string`, then `array`/`object` support is "register one `DomainPlanner`," not "edit five switch statements." Each planner encodes as much of its parameter's path-condition clauses into a jqwik arbitrary as it can; the unconditional residual filter remains the soundness net for the rest.
2. **Grounded in SPF reality.** The generator can only encode what SPF actually emits. We do **not** re-characterize SPF from scratch — `phd-thesis/projects/spf-eval` already maps it — but we make the relevant slice an in-repo, tested artifact at the pipeline level (spec extraction → render → generate → PIT), because that is where Teralizer's soundness and completeness are actually decided.
3. **Self-reporting.** Each generalization records, as analysis metadata, which clauses it represented by construction and which it could not; the front end records which parameter *types* it could not admit; the pipeline records which admitted inputs SPF gave **no** symbolic spec for and whether the generalization survived or was excluded. Analysis can then rank the most common gaps and prioritize the next planner, recipe, or SPF fix.

Supersedes `2026-06-27-residual-aware-input-generation`, whose v1 typed planner shipped.

## SPF is the upstream bound — and what concretization actually costs

Teralizer runs SPF in `collect_constraints` (symcrete) mode along the concrete path: the path condition is the input partition, the symbolic return is the output oracle. For the generator to constrain an input by construction, **SPF must first have produced a symbolic clause naming it**; for an output oracle, a symbolic return. When SPF concretizes (an unmodeled method, a solver-bridge gap), the value reaches the generator with no clause.

**Concretization is not one bucket, and it is not automatically unsound.** The generated property asserts the rendered oracle against the *real* MUT on every generated input (original value first). So an imprecise spec either still satisfies the assertion (sound) or fails it — and a failing generalization is **excluded** (`NonPassingTestFilter` → PIT), never shipped. Spec imprecision therefore costs **completeness**, not soundness — except where a spec renders to compilable-but-wrong Java. Classify by role:

| concretization role | example (spf-eval) | effect |
|---|---|---|
| symbolic PC + **constant** return (`symbolicAttr` absent) | `int_return_const`, `sign(int)` | **sound + complete** — partition exact, oracle is a genuine constant for that branch |
| value-dependent return lost its symbolic attr, or PC under-constrained, or composition lost | `repeated_call_chained` | **sound but lossy** — divergent/wrong-oracle inputs fail → generalization **excluded** |
| leaked concrete heap state / un-renderable node | `array_2d` (concrete address in oracle) | **soundness risk** — must **fail loud** (non-generalizable), not silently render |

This makes the fail-loud SPF→Model seam (A-3/A-5) load-bearing for **soundness**, not just maintainability.

Self-validation makes the *shipped suite* sound — a wrong spec is excluded, not shipped — but it does **not** certify the extracted spec: a *passing* generalization only means the spec was not wrong for the sampled inputs, not that it is right. Every concretization is therefore a telemetry/exclusion risk until a characterization fixture proves its role safe; the role table above is a hypothesis to test, not a guarantee.

## Architecture — the clean seam

### Clause-driven planners
`DomainPlanner.plan` receives the flattened `ConstraintClause`s naming its parameter (model expressions with stable ids) plus the concrete argument, and returns a recipe **and the clause ids it encoded**. Each planner interprets its own domain's operators. `VariableConstraintExtractor` is retired as the universal pre-digester; `NumericDomainPlanner` does its own numeric clause interpretation, with `IntegerConstraints`/`RealConstraints` demoted to numeric-planner-internal recipe builders.

### Single type-capability source
A type is generatable iff a registered `DomainPlanner` `supports(TypeDomain.from(type))`. The front-end gate (`GeneralizableInput.derive` / `ParameterTypeFilter`) and `SUPPORTED_TYPES` derive from the registry, not a hand-maintained list.

### Fail-loud visitor seam (soundness-critical) — shipped
A node `ModelToJavaTransformer` must render but cannot is a compile error, not a silent stack imbalance; `SpfToModelTransformer`'s unsupported / leaked-state paths return a typed, attributable "non-generalizable (reason)" outcome instead of `UnsupportedOperationException` or silent concretization. This both stops the soundness risk above and feeds the SPF-gap telemetry. Shipped: `ModelFolder<T>` (A-3), `NonGeneralizableExpressionException` (A-1), `UnsupportedSpfTermException` (A-5).

### Single emitter — shipped
Collapse the Baseline/Naive/Improved factory triplication so the planner is the one typed emitter. Shipped (C-1).

### The residual filter stays unconditional
`filter(inputJava)` is always emitted; recipes narrow, the filter enforces. Removing it for "consumed" clauses is **out of scope** (no outcome change, only added unsoundness surface).

### Typed recipes — decouple planning from rendering (follow-up)
A "recipe" is currently a pre-rendered jqwik-Java string — and so is the rest of the plan→Java boundary. The planners build `Arbitraries.*` source with `String.format` and call `ModelToJavaTransformer` directly; `originalValue` is a rendered `(type) (value)` string; `ConstraintClause.getJavaExpression` holds the residual `filter(inputJava)` predicate as pre-rendered Java; and the numeric bound expressions (`n.min()`/`n.max()`, e.g. `(char) (65.0)`) are rendered before being interpolated into the recipe. So the planning layer does code generation across all of these, and the cast, first-value guard, arbitrary selection, predicate, and bounds rendering are scattered across the planners, the clause interpreter, `ConstraintClauses`, `defaultRecipe`, and both supplier factories — the same surface where the `double ^ double` and int-as-`(char)` bugs lived.

The cleaner end-state the "returns a recipe" wording already implies: planners emit a **typed** recipe (`FullRange`, `BoundedRange(min, max, scale, inclusivity)`, `Equality(value)`, `CharRange`, `BooleanChoice`) over typed bound expressions and a typed residual clause set, plus the original `MethodArgument`; one renderer in the Spoon layer turns all of it into Java. Stage it so the plan-model boundary stays coherent rather than a single-field cleanup: recipe body + `originalValue` first, then the residual predicate (typed clauses, not a pre-joined string), then the numeric bound expressions. Planning then stops importing `transformer` entirely, rendering lives in one place, and planner tests assert on typed recipes instead of brittle `contains("Arbitraries…")` matches. Behavior-preserving (guard each stage with a render-equivalence test), a quality/testability refactor not a correctness fix — no forcing function, so sequence it when the next planner would otherwise duplicate rendering, not before the type-planner work.

## SPF capability characterization — reuse, don't restart

`phd-thesis/projects/spf-eval` (run 2026-02-19, glockyco/jpf-symbc) already characterizes 100+ subjects with per-construct verdicts (Full / Partial / Crash / Degenerate), exact PC + return-attr notes, and a golden-file regression harness. **It is the baseline support matrix.** The in-repo work is narrower and pipeline-specific:

- **Map each relevant spf-eval verdict to the Teralizer-pipeline outcome:** does `SpfToModelTransformer` ingest it, does `ModelToJavaTransformer` render it, does the generalization survive PIT or get excluded? (spf-eval stops at the SPF spec; we own render→generate→PIT.)
- **Add pipeline-level fixtures only at the gaps/divergences** — small MUTs through the JPF stage asserting the emitted spec + the generalization outcome.
- **Re-run the double/float cases:** spf-eval's `double_linear`/`float_linear`/`double_nonlinear` ⚠️Partial are the `Double.MIN_VALUE` lower-bound bug **this branch fixed (B-2)**; confirm they flip to Full (validates B-2 against spf-eval's goldens, ideally by pointing spf-eval at this branch's jpf-symbc build).

## Type planners (incremental, one seam), scoped by the matrix

1. **`BooleanDomainPlanner`** — warmup validating the seam: `b == true|false` → `just`; else `Arbitraries.of(true, false)`. (spf-eval `boolean_input` ✅.)
2. **`StringDomainPlanner`** — recipes only where spf-eval shows SPF specifies the clause:
   - **Buildable now:** `equals(const)` → `just`; `length op n` → `ofMin/MaxLength` (`string_equality`, `string_length` ✅).
   - **Needs a content-shape recipe (later):** `substring`/`charAt`/`indexOf` produce constraints (✅ extraction) but positional construction is harder.
   - **Not characterized → characterize before building:** `startsWith`/`endsWith`/`contains`.
   - **SPF crash, not a recipe gap:** `isEmpty`, `compareTo`, null-string param crash SPF (`SymbolicStringHandler` gap) → these are SPF fixes (candidate "easy win" lane), not planner work.
   - Requires admitting `String` via the single type source + `symbolic.strings` for the probe.
3. **`Array`/`Object` planners** — subset-first, matrix-scoped: array element/length-guard/return are ✅, but `array_store_con`/null-array crash and `array_2d` leaks a concrete address (fail-loud case); objects extend `GeneralizableInput`'s inline-constructor flattening (lazy-init/field cases ✅).

## SPF extension

In scope when reasonably accomplished, **evidence-gated, not first priority.** Pursue a fix only when the characterization + telemetry show it unblocks a frequent construct and it is tractable. An "easy fix, large improvement" (e.g. adding `isEmpty`/`compareTo` to `SymbolicStringHandler`, or a missing peer method) jumps the queue; a deep one (transcendental solver theory, symbolic FP) is recorded as a bounded upstream task. `2026-06-28-maxulps-raw-bits-lane` is the worked deep example.

## Generation-coverage telemetry

The generator self-reports which clauses it encoded and which fell to the residual filter, so analysis can rank the most common gaps (entry / SPF / recipe) and prioritize the next planner, recipe, or SPF fix. The signal taxonomy, record shape, schema, and analysis module design live in `2026-06-28-generation-coverage-telemetry`.

## Phasing

- **A. Clean seam** — clause-driven planners + single type source + fail-loud seam + single emitter; emits clause/parameter telemetry.
- **Characterization** — map the spf-eval matrix to pipeline outcomes + gap fixtures + re-run double/float post-B-2. Runs alongside A; gates the recipe scope. Entry-gap capture is independent and cheap → can land first.
- **B. `BooleanDomainPlanner`** — seam validation.
- **C. `StringDomainPlanner`** — the matrix-confirmed `equals`/`length` subset first; characterize `startsWith`/`contains`; content-shape recipes later.
- **D. Arrays/objects** — subset-first.
- SPF fixes interleaved opportunistically per the evidence rule.

## Acceptance criteria

- Adding a parameter type is registering one `DomainPlanner`; no hand-maintained type list or per-variant factory switch edits.
- The pipeline characterization maps the spf-eval matrix to render/generate/PIT outcomes; recipe scope follows it, not assumption; the post-B-2 double/float re-run is recorded.
- A clause no planner encodes falls to the residual filter; an un-renderable or leaked-state Model node fails loud, never silently degrades or mis-renders a spec.
- Telemetry distinguishes entry / SPF / recipe gaps and links SPF gaps to exclusions; `generation_coverage.py` produces the rankings.
- String inputs generate by construction for the matrix-confirmed subset and pass; the rest filter; no generated test becomes unsound.

## Non-goals

- Runtime residual-only filtering — telemetry only; the filter stays.
- Re-characterizing SPF from scratch — reuse spf-eval; extend in-repo only at pipeline-specific gaps.
- Deep SPF extensions inline (transcendental/FP theory, full regex generation, symbolic array length) — bounded upstream tasks, evidence-gated.

## Relationship to existing docs

- **Supersedes** `2026-06-27-residual-aware-input-generation` (v1 typed planner, shipped).
- The fail-loud seam (A-3: `ModelFolder`, A-5: `UnsupportedSpfTermException`) and single emitter (C-1) are shipped in `2026-06-28-pipeline-improvements`; this spec designs the generator that builds on them.
- C-4 (by-construction recipe library) is reframed (evidence-gated): its only recipe with a named consumer is the raw-bits ulps neighborhood, consumed by `2026-06-28-maxulps-raw-bits-lane` (Gap 3), where the recipe-library infrastructure is built. Further recipes are gated on generation-coverage shape telemetry, not assumption. Not a dependency of this spec.
- **Extends** `2026-06-27-generalizable-input-rule` (admitting string/array/object inputs).
- **Baseline matrix:** `phd-thesis/projects/spf-eval` (`RESULTS.md` + golden harness); the in-repo characterization complements `2026-06-26-applicability-barriers` (corpus-level SPF-stage funnel) with per-construct pipeline fixtures, and confirms B-2 cleared the spf-eval double/float bounds bug.
- `2026-06-28-maxulps-raw-bits-lane` is the worked deep-SPF-extension example and consumes the fail-loud seams + recipe library.
