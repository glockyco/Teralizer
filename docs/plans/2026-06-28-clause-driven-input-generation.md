---
title: Clause-Driven, Multi-Type Input Generation
type: spec
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Redesign the `IMPROVED` input-generation seam so new parameter types are added by registering one planner, ground it in what Symbolic PathFinder can actually specify, and make the whole path self-report what it could and could not represent.

## Goal

Three coupled outcomes:

1. **Extensible by construction.** Adding `string`, then `array`/`object` support is "register one `DomainPlanner`," not "edit five switch statements." Each planner encodes as much of its parameter's path-condition clauses into a jqwik arbitrary as it can; the unconditional residual filter remains the soundness net for the rest.
2. **Grounded in SPF reality.** The generator can only encode what SPF actually emits. A characterization test suite maps, per language construct, what SPF produces (a symbolic path-condition clause, a symbolic output oracle, a silent concretization, an unsupported error, or a crash). Recipes are built only for constructs SPF specifies; SPF extensions are targeted by evidence, not guessed.
3. **Self-reporting.** Each generalization records, as analysis metadata, which clauses it represented by construction and which it could not; the front end records which parameter *types* it could not admit; and the pipeline records which admitted inputs SPF gave **no** symbolic spec for. Analysis can then rank the most common gaps and prioritize the next planner, recipe, or SPF fix.

Supersedes `2026-06-27-residual-aware-input-generation`, whose v1 typed planner (`InputGenerationPlanner`/`DomainPlanner`/`TypeDomain`/`ConstraintClause`) shipped. This spec makes that seam clause-driven, grounds it in SPF capability, and adds the telemetry.

## SPF is the upstream bound

Teralizer extracts specs by running SPF in `collect_constraints` mode along the concrete path: the path condition is the input partition, the symbolic return value is the output oracle. So for any input the generator wants to constrain by construction, **SPF must first have produced a symbolic clause naming it** — and for any output oracle, SPF must have produced a symbolic return. If SPF concretizes (e.g. an unmodeled `String` method), the parameter reaches the generator with *no* clause: free generation + residual filter, regardless of how good the planner is.

The model layer is already ahead of this: SPF supports symbolic strings, and `SpfToModelTransformer` already maps string PCs (`StringConstraint`, `StringSymbolic`→`VariableString`, `equals`/`length`/`startsWith`/`contains`/`matches` via the `Operator` enum). But *which* string/array/object constructs SPF actually symbolizes under our config is not pinned down in-repo — only in the external `spf-eval` study. This spec makes that an in-repo, tested artifact.

## Architecture — the clean seam

### Clause-driven planners
`DomainPlanner.plan` receives the flattened `ConstraintClause`s naming its parameter (model expressions with stable ids) plus the concrete argument, and returns a recipe **and the clause ids it encoded**. Each planner interprets its own domain's operators (numeric reads `<`/`+`; string reads `equals`/`length`/`startsWith`). `VariableConstraintExtractor` is retired as the universal pre-digester; `NumericDomainPlanner` does its own numeric clause interpretation, with `IntegerConstraints`/`RealConstraints` demoted to numeric-planner-internal recipe builders.

### Single type-capability source
A type is generatable iff a registered `DomainPlanner` `supports(TypeDomain.from(type))`. The front-end gate (`GeneralizableInput.derive` / `ParameterTypeFilter`) and `SUPPORTED_TYPES` derive from the planner registry instead of a hand-maintained list. Adding a type = registering one planner.

### Fail-loud visitor seam
A node the renderer must handle but doesn't becomes a compile error (non-defaulted hooks on the `ModelToJavaTransformer` contract) rather than a silent stack imbalance; `SpfToModelTransformer`'s unsupported-node paths return a typed, attributable "non-generalizable (reason)" outcome instead of `UnsupportedOperationException` or silent concretization — which also feeds the SPF-gap telemetry below. (architecture-review **A-3**, **A-5/D-1**.)

### Single emitter
Collapse the Baseline/Naive/Improved factory triplication so the planner is the one typed emitter and the factories become thin variant selectors. (architecture-review **C-1**.)

### The residual filter stays unconditional
`filter(inputJava)` is always emitted. By-construction recipes narrow ranges; the filter is the soundness net for anything not encoded. Removing the filter for "consumed" clauses is **out of scope** — it changes no outcomes and only adds an unsoundness surface.

## SPF capability characterization (foundational)

A test suite that pins, per construct, what SPF→Model actually yields under our config — the empirical map that scopes which recipes are reachable and surfaces candidate SPF fixes.

- **Form:** small fixture MUTs (one construct each — `s.equals`, `s.length`, `s.substring`, `s.charAt`, array read/length, object field access, plus string/array/object *returns* for the oracle side) run through the JPF stage; assert on the emitted input/output specification (the `SpfToModelTransformer` result), tagging each construct `symbolic-clause` / `symbolic-output` / `concretized` / `unsupported` / `crash`.
- **Seed:** the external `spf-eval` study (`~/Projects/phd-thesis/projects/spf-eval/RESULTS.md`) already characterized SPF type support first-hand; port its relevant cases as the starting matrix, then verify against our config.
- **Output:** a living "SPF support matrix" that (a) tells the `StringDomainPlanner` which clause shapes can actually appear, (b) doubles as regression tests for the `SpfToModel` mapping, and (c) is the evidence base for which SPF extensions are worth it.

## Type planners (incremental, one seam)

Scoped to what characterization shows SPF specifies:

1. **`BooleanDomainPlanner`** — warmup validating the clause-driven seam: `b == true|false` → `just`; else `Arbitraries.of(true, false)`.
2. **`StringDomainPlanner`** — by construction for the subset SPF specifies: `equals(const)`→`just`; `length op n`→`ofMin/MaxLength`; `startsWith`/`endsWith`/`contains(const)`→prefix/suffix/infix construction; `isEmpty`→`just("")`. Out of construction (→ residual filter): `matches(regex)`, `equalsIgnoreCase`, `charAt`/`indexOf`. Requires admitting `String` via the single type source + enabling `symbolic.strings` for that probe.
3. **`Array`/`Object` planners** — incremental, subset-first: array length + element recipe (SPF symbolic array length is a known limit, barrier #20); objects extend `GeneralizableInput`'s inline-constructor flattening.

## SPF extension

In scope when reasonably accomplished, but **evidence-gated and not first priority**: pursue an SPF/model/peer/config extension only when the characterization suite shows it unblocks a frequent construct (telemetry-ranked) and the fix is tractable. An "easy fix, large improvement" (e.g. a one-line config or a missing peer method) jumps the queue; a deep one (new FP theory, symbolic array length) is recorded as a bounded upstream task, not attempted inline. The `maxulps-raw-bits-lane` is the worked example of a deep, scoped SPF extension.

## Generation-coverage telemetry

The seam self-reports. Because the filter stays unconditional, tracking is metadata only — a mislabel is a wrong statistic, never an unsound test. Crucially it separates the *three distinct gaps*, each with a different fix:

| signal | meaning | fix |
|---|---|---|
| **entry gap** | a parameter type was never admitted (`ParameterTypeFilter` reject) | add a `DomainPlanner` (+ front-end admit) |
| **SPF gap** | admitted, but SPF produced no symbolic clause/output for it (concretized) | extend SPF / config / a peer |
| **recipe gap** | SPF gave a clause, but no planner recipe encoded it (→ residual filter) | add a recipe to the planner |

### Records
- Per admitted parameter: `{type_domain, symbolic_spec_present, representation ∈ encoded | residual | none}` — `none` with `symbolic_spec_present = false` is the SPF gap; `residual` is the recipe gap.
- Per top-level clause: `{type_domain, shape, consumed_by_construction}`.
- Per generalization: `{symbolic_output_present}` — whether the oracle is a real symbolic return or a concretized value.

### Shape key
Operator-family + operand-kinds, literal values stripped: `STRING:startsWith(var,const)`, `STRING:matches(var,const)`, `INTEGER:mod(var,const)≟const`, `ARRAY:length(var) op const`, `REAL:affine2(var+var op const)`.

### Schema (additive)
- `generation_clause(id, generalization_id FK, parameter_name, type_domain, shape, consumed)`.
- `generation_parameter(id, generalization_id FK, name, declared_type, type_domain, symbolic_spec_present, representation)`.
- Entry-gap capture: `rejected_parameter(assertion_id FK, declared_type, type_domain)` (or a structured column on `filter_result`).
- Reuse `generalization.total_constraint_count` / `used_constraint_count` for the coarse rate.

### Analysis
New `analysis/src/teralizer/generation_coverage.py` (sibling to `applicability_priorities.py`, which keeps the front-end filter/stage funnel): top residual shapes, per-`TypeDomain` by-construction coverage, the entry-gap-by-type ranking, and the **SPF-gap ranking** (admitted-but-not-symbolized constructs) — the prioritized "next type / next recipe / next SPF fix" lists.

## Phasing

- **A. Clean seam** — clause-driven `DomainPlanner` + single type source + fail-loud visitor seam + single emitter; emits the clause/parameter telemetry from here on.
- **Characterization** — the SPF support matrix; runs alongside A and gates the string/array/object recipe scope. The front-end entry-gap capture is independent and cheap, so it can land first for immediate type prioritization.
- **B. `BooleanDomainPlanner`** — seam validation.
- **C. `StringDomainPlanner`** — the SPF-confirmed subset + front-end admit + per-probe `symbolic.strings`.
- **D. Arrays/objects** — subset-first.
- SPF fixes are interleaved opportunistically per the evidence rule above.

## Acceptance criteria

- Adding a parameter type is registering one `DomainPlanner`; no edits to a hand-maintained type list or per-variant factory switches.
- The characterization suite exists and tags each covered construct; its findings, not assumption, scope the string/array/object recipes.
- A clause a planner cannot encode falls to the still-present residual filter; an unhandled Model node fails loud, never silently degrades a spec.
- Telemetry distinguishes entry / SPF / recipe gaps; `generation_coverage.py` produces the three rankings.
- String inputs generate by construction for the SPF-confirmed subset and pass; the rest filter; no generated test becomes unsound.

## Non-goals

- Runtime residual-only filtering (dropping the filter for consumed clauses) — telemetry only; the filter stays.
- Deep SPF extensions attempted inline (full regex string generation, symbolic array length, FP theory) — recorded as bounded upstream tasks, pursued only when evidence + tractability justify.

## Relationship to existing docs

- **Supersedes** `2026-06-27-residual-aware-input-generation` (v1 typed planner, shipped).
- **Implements / absorbs** architecture-review findings A-3, A-5/D-1, C-1, C-2, C-4 (itemized in `2026-06-28-pipeline-improvements`) and **reframes C-3** as telemetry; the derived implementation plan reconciles the overlap.
- **Extends** `2026-06-27-generalizable-input-rule` (admitting string/array/object inputs via the single type source).
- The SPF characterization complements `2026-06-26-applicability-barriers` (which classifies SPF-stage failures at the corpus level) with in-repo per-construct tests.
- `2026-06-28-maxulps-raw-bits-lane` is the worked deep-SPF-extension example and consumes the fail-loud `SpfToModel`/`ModelToJava` seams + the recipe library.
