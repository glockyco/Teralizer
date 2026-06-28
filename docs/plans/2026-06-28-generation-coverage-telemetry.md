---
title: Generation-Coverage Telemetry
type: note
status: active
created: 2026-06-28
parent: 2026-06-28-clause-driven-input-generation
---

Design for the generation-coverage telemetry layer that lets the clause-driven
generator self-report which clauses it encoded by construction and which it
could not. Split out of `2026-06-28-clause-driven-input-generation` to keep that
spec focused on the generator seam; this note owns the telemetry schema and
analysis only.

## Signals

The seam self-reports; the filter stays unconditional, so tracking is metadata.
It separates the gaps, each with a different fix, and links them to actual lost
generalizations (exclusions):

| signal | meaning | fix |
|---|---|---|
| **entry gap** | parameter type never admitted (`ParameterTypeFilter` reject) | add a `DomainPlanner` (+ admit) |
| **SPF gap** | admitted, but SPF gave no/partial symbolic spec — tagged by role (constant-return = sound; value-dependent-lost / leakage / lost-composition = completeness/soundness risk) | extend SPF / config / peer |
| **recipe gap** | SPF gave a clause, no planner recipe encoded it (→ residual filter) | add a recipe |

## Records

- Per admitted parameter: `{type_domain, symbolic_spec_present, representation ∈ encoded | residual | none}`.
- Per top-level clause: `{type_domain, shape, consumed_by_construction}`.
- Per generalization: `{symbolic_output_present, excluded, exclusion_reason}` — ties spec imprecision to lost generalizations; `symbolic_output_present = false` distinguishes a (sound) constant-return oracle from a (lossy) lost one only in combination with `excluded`.

## Shape key

Operator-family + operand-kinds, literals stripped: `STRING:startsWith(var,const)`, `STRING:matches(var,const)`, `INTEGER:mod(var,const)≟const`, `ARRAY:length(var) op const`, `REAL:affine2(var+var op const)`.

## Schema (additive)

- `generation_clause(id, generalization_id FK, parameter_name, type_domain, shape, consumed)`.
- `generation_parameter(id, generalization_id FK, name, declared_type, type_domain, symbolic_spec_present, representation)`.
- Entry-gap capture: `rejected_parameter(assertion_id FK, declared_type, type_domain)` (or a structured `filter_result` column).
- Reuse `generalization.total_constraint_count` / `used_constraint_count`; join existing exclusion state for the SPF-gap↔exclusion correlation.

## Analysis

New `analysis/src/teralizer/generation_coverage.py` (sibling to `applicability_priorities.py`, which keeps the front-end funnel): top residual shapes, per-`TypeDomain` by-construction coverage, entry-gap-by-type, and the SPF-gap ranking joined to exclusions — the prioritized "next type / next recipe / next SPF fix" lists.

## Gates the modulo / further-recipe decision

The `generation_clause(shape, consumed)` surface is the prerequisite for deciding whether a by-construction modulo recipe (or any further recipe beyond the raw-bits ulps recipe) is worth building. C-3 already records consumed-vs-residual clause *volume* (`total_constraint_count` / `used_constraint_count`), but not *which shapes* fall residual. Only the shape-key here answers "how often is `INTEGER:mod(var,const)≟const` unconsumed, and with what divisor distribution?" — and large-divisor modulo is the only modulo case filtering cannot absorb. Build this surface + run the corpus before committing to a modulo recipe; until then modulo stays speculative (see `2026-06-28-pipeline-improvements` C-4).
