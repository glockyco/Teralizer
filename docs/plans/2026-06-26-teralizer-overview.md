---
title: Teralizer — Project Overview
type: overview
status: active
created: 2026-06-26
---

# Teralizer — Project Overview

Teralizer turns JUnit tests into property-based jqwik tests by running Symbolic
PathFinder (SPF) in constraint-collection mode along a test's concrete path,
extracting a path-exact specification (input partition + symbolic output oracle) and
generalizing the test to explore more inputs within the same path. PhD-thesis tool.

The planning north-star is paper-ready evidence against JARVIS on its own benchmark
shape, followed by reusable corpus data and then a full evaluation rerun.

## Strategy sequence

1. **Keep the JARVIS comparison paper-ready** — preserve the current comparison contract,
   metrics, and guardrails while implementation work continues.
2. **Unblock corpus applicability** — fix MUT identification first, then run string corpus
   verification against the newly-resolved targets.
3. **Extend data collection** — add the provenance/tagging that makes the corpus reusable.
4. **Re-run the full evaluation** — regenerate RQ results on the broader dataset.

No full evaluation rerun is planned until MUT identification and string corpus verification land.

## Current focus

The next implementation work, **in this order**:

1. **`2026-07-02-static-mut-id-fusion`** *(plan, draft)* — confidence-ranked fusion MUT-id
   (evidence-recording, tier-graded picks per `2026-07-02-mut-id-confidence-fusion`) targeting
   the `MissingValue` blocker and making the string funnel payoff measurable.
2. **`2026-06-30-partial-sound-string-support`** *(plan, active)* — run Task 7 corpus
   verification after static MUT-id resolves String-parameter/return MUTs. Scope is sound string
   operators, String returns, and structural exclusion of unsupported string terms.

## Map (by current relevance)

Read this after `INDEX.md`. Grouped by what an implementer needs next; `INDEX.md` carries
the full status and parent tree.

**Baseline & paper-facing comparison**
- `2026-06-30-jarvis-comparison` *(spec, RQ0)* — comparison contract, metric definitions,
  guardrails, and paper-facing interpretation for the JARVIS baseline.

**Next implementation path**
- `2026-07-02-mut-id-confidence-fusion` *(spec)* — the MUT-id design authority: lexicographic
  confidence tiers, two grades, `mut_resolution_observation` provenance.
- `2026-07-02-static-mut-id-fusion` *(plan, draft)* — the AST-only v1 of that spec; the next
  implementation item.
- `2026-06-30-partial-sound-string-support` *(plan, active)* — sound string-operator support;
  corpus verification remains gated on MUT-id.

**Generator contracts**
- `2026-06-28-clause-driven-input-generation` *(spec)* — clause-driven planners, single
  type-capability source, fail-loud SPF→Model seam.
- `2026-06-28-generation-coverage-telemetry` *(note)* — telemetry schema the generator
  self-reports against.
- `2026-06-28-pipeline-architecture-review` *(audit)* — architecture findings feeding the
  paper's roadmap.

**MUT identification context**
- `2026-06-27-ensemble-mut-identification` *(spec, draft)* — killed-mutant focal-method oracle
  replacing LCBA.
- `2026-06-28-mut-id-targeting-and-coverage` *(audit)* — MUT-id targets, mutation-data
  coverage, telemetry gaps.
- `2026-07-01-rerun-observability-priorities` *(audit)* — which rerun telemetry is worth adding
  because it produces planning evidence.
- `2026-07-01-pipeline-observability-telemetry` *(spec, draft)* — additive schema/design for
  MUT-resolution provenance, SPF/spec rollups, build/report diagnostics, assertion semantics,
  and generated-property lifecycle tracking.

**Independent track**
- `2026-06-25-replication-package-documentation-improvements` *(plan)* — verifiable
  replication package for ACM artifact eval.

**Deferred reference**
- `2026-06-26-applicability-barriers` *(audit)* — RQ6 real-world barrier inventory.
- `2026-06-26-data-reuse-and-msr-potential` *(note)* — secondary MSR / data-paper backlog.

**Backlog**
- `2026-06-27-inherited-test-method-support` *(spec, draft)* — flatten inherited `@Test`
  methods so inherited tests parse.
- FastMath/Interval generalization investigation.
- SPF-eval listener ports: bit-exact float/NaN-payload capture, heap-PC capture for object/boxed
  params, and raw-PC logging.
- Non-value-oracle generalization: no-throw and expected-throw oracles for tests without a
  comparable value result, after value-oracle MUT discovery is unclogged.

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
