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

**This document is forward-looking only.** It holds the goal, the strategy sequence, and
the remaining work, ranked. Results, measurements, and completed-work records live in the
audits and the archive — nothing here describes what was done. When an item ships, it
leaves this queue; its evidence goes to the owning audit and its doc to `archive/`.

## Strategy sequence

1. **Keep the JARVIS comparison paper-ready** — preserve the current comparison contract,
   metrics, and guardrails while implementation work continues.
2. **Land the ranked funnel levers** — merge the implemented, gate-verified levers
   (`2026-07-06-funnel-improvement-levers`); the next corpus event measures them, and the
   SPF-loss reclassification unblocks the native-peer ranking.
3. **Regenerate paper numbers** — JARVIS refresh plus the batched corpus measurements at
   the next lever-wave boundary.

## Priority queue

Every planned work item, ranked. Ordering logic: cheap verification debt first (it can
invalidate later readings), then decision-gates (spikes and censuses that *rank* the work
below them), then the fixes those gates rank, then measurement infrastructure, then recall
expansion, then maintainability. Evidence-gated items never start before their gate.

**P1 — land the implemented levers (gate-verified, pending merge)**
1. **`2026-07-06-funnel-improvement-levers`** *(implemented on `funnel-levers`, batch-gate
   green — pending merge)* — the levers the rerun telemetry ranked: yield-gap engineering
   (floor property resolution, scaled generalized-suite timeout), assertion-kind support
   (equality-isomorphic Hamcrest, try/fail/catch exception oracles), SPF-loss
   reclassification, library-accessor unwrap. Merge, then the next corpus event measures them.
2. **`2026-06-28-native-peer-model-coverage`** *(spec, draft)* — crash-visible peer/model
   gaps, per-method ranked. GATED: the ranking is honest only after the levers' SPF-loss
   reclassification lands in a corpus run. The full rerun's generic UNCAUGHT bundle still
   holds 64.5% of JPF losses and masks the native-peer count.

**P2 — measurement infrastructure**
3. **`2026-07-06-telemetry-attribution-hardening`** *(implemented — analysis-side on
   master, lifecycle and outcome columns on `funnel-levers` pending merge)* — generic-code
   budgets as integrity invariants (already flagging the coarse UNCAUGHT and compile-failure
   buckets in the rerun report), first-cause attribution view, lifecycle cause propagation,
   intervention outcome records, loud-basis analysis CLIs, exemplar sampling.

**P3 — maintainability / independent tracks**
4. **`2026-06-25-replication-package-documentation-improvements`** *(plan)* — remaining:
   the deferred E2E smoke of the supervised replication runner. Gradle is free now that the
   rerun has finished.
5. **`2026-07-03-harness-support-artifact`** *(spec, draft)* — precompiled telemetry jar;
   deletes the generated-file language-level defect class. Worthwhile, no urgency coupling.
6. **C-1 single-emitter residue check** (`2026-06-28-pipeline-architecture-review`) —
   after the recipe unification wave, verify what if anything remains of the factory-drift
   concern; expected to shrink to nothing.
7. **Small items, no docs needed:** deferred mis-pick fixture; assertThrows-lambda
   expression-site replacement limitation in GeneralizationRecipe; debugger-grade trace of
   one antiaction widened-tuple NPE to decide whether the recipe must refuse substitution
   when the widened expression is load-bearing elsewhere in the test body
   (`2026-07-05-concretization-census-findings`, Finding 2).

## Standing gates (events, not queue slots)

- **JARVIS scoreboard refresh** — due at each lever-wave boundary, run only on explicit
  operator sign-off (measurement events are never started unilaterally — see AGENTS.md).
  REQUIRED before any paper claim (`skill://running-the-jarvis-scoreboard`). Batch the
  pending corpus-scale measurements into the same session. Current numbers:
  `2026-06-30-jarvis-comparison`.

## Parked (not scheduled — recorded for the paper's honesty)

- **Symbolic collections / heap shapes, reflection, regex `matches`, symbolic FP /
  transcendentals** — research-grade SPF work; the census records their weight so the paper
  can state the applicability ceiling honestly (archived maxUlps lane is the worked example).
- **Full-Unicode `Character.isWhitespace`** (general-category membership over thousands of
  code points) — only the ASCII interval subset is tractable
  (`2026-07-05-sound-char-predicates`, archived).
- **Branch-selected constant int oracles** — boxed `compareTo` idioms returning −1/0/1
  selected by a path-condition branch; licensing them soundly needs a
  constant-per-partition argument beyond the boolean-sibling license (mechanism in
  `2026-07-05-concretization-census-findings`).
- **`2026-06-27-ensemble-mut-identification`** *(spec, abandoned, archived)* — the
  killed-mutant runtime tier over fusion v1, deferred indefinitely by operator decision;
  rationale in the archived spec. T3/T4 picks stay the ~43%-precision population and are
  reported as such.
- **SPF-eval listener ports** (bit-exact float/NaN capture, heap-PC capture, raw-PC logging)
  — upstream-SPF effort with no current consumer.
- **FastMath/Interval generalization investigation** — superseded in value by the census;
  revisit only if a census ranks FastMath concretizations high.
- **Non-value-oracle generalization** (no-throw / expected-throw oracles) — revisit after
  P4 lands; the generator work is still unscoped.
- **`2026-06-26-data-reuse-and-msr-potential`** *(note)* — secondary MSR / data-paper
  backlog; not this paper.

## Reference map (design authorities and evidence — read on demand)

- `2026-06-30-jarvis-comparison` *(spec)* — comparison contract, metrics, guardrails (RQ0),
  and the current scorecard numbers.
- `2026-07-02-mut-id-confidence-fusion` *(spec)* — MUT-id design authority: tiers, grades,
  `mut_resolution_observation`; the standing contract.
- `2026-06-28-clause-driven-input-generation` *(spec)* — generator contract: clause-driven
  planners, type-capability source, fail-loud SPF→Model seam, SPF-extension evidence gate.
- `2026-06-28-generation-coverage-telemetry` *(note)* — telemetry schema behind the
  generation-coverage plan.
- Audits: `2026-06-26-applicability-barriers` (RQ6 barrier inventory),
  `2026-06-28-mut-id-targeting-and-coverage` (empirical findings home),
  `2026-06-28-pipeline-architecture-review` (residue tracker: C-1, D-1),
  `2026-07-01-rerun-observability-priorities` (telemetry ROI ranking),
  `2026-07-02-input-topology-spike` (R1/R2 opportunity bounds),
  `2026-07-02-recipe-seam-review` (recipe-seam sequencing + spike definitions),
  `2026-07-05-concretization-census-findings` (census ranking + lever measurements),
  `2026-07-06-evaluation-setup-audit` (run-script/config/analysis cleanup record).

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
