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

1. **Finish and promote the RQ6 v3 corpus** — `postgres_reporeapers_rq6_v3` is
   collecting on the tree with the funnel levers, JUnit 3 assertion analysis, and
   resolver-attributed rejects (`2026-08-04-rq6-recollection`). Every RQ6 figure the
   thesis cites comes from this one measurement event.
2. **Keep the JARVIS comparison paper-ready** — preserve the current comparison contract,
   metrics, and guardrails while evidence work continues.
3. **Rank the recall work on v3 evidence** — the SPF-loss reclassification is in the
   collected corpus, so the native-peer ranking and the refusal-taxonomy re-measurement
   run against v3 once it is signed off.

## Priority queue

Every planned work item, ranked. Ordering logic: cheap verification debt first (it can
invalidate later readings), then decision-gates (spikes and censuses that *rank* the work
below them), then the fixes those gates rank, then measurement infrastructure, then recall
expansion, then maintainability. Evidence-gated items never start before their gate.

**P1 — the RQ6 v3 measurement event**
1. **`2026-08-04-rq6-recollection`** *(plan, run in progress)* — monitor the
   `reporeapers-rq6-v3` collection to completion, verify the fixes landed in the data
   (telemetry invariant zero, resolver-reason decomposition, JUnit 3 shift), promote the
   corpus, and hand the numbers to the thesis.
2. **`2026-08-04-reporeapers-dataset-row`** *(plan, gated on v3)* — the dataset-statistics
   row for the thesis, recomputed against the promoted corpus.
3. **Refusal-taxonomy re-measurement** (`2026-08-04-oracle-refusal-taxonomy`) — re-run the
   bucket queries on v3; the JUnit 3 fix enlarges the analyzed assertion population by
   roughly a fifth, so shares must be re-derived before the thesis cites them.

**P2 — recall expansion (gated on v3 evidence)**
4. **`2026-06-28-native-peer-model-coverage`** *(spec, draft)* — crash-visible peer/model
   gaps, per-method ranked. GATED: rank against v3, whose collected corpus carries the
   SPF-loss reclassification.

**P3 — maintainability / independent tracks**
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

- `2026-07-07-evaluation-run-map` *(audit)* — which measurement feeds which RQ, at which
  corpus and database; names the canonical RQ6 database.
- `2026-06-30-jarvis-comparison` *(spec)* — comparison contract, metrics, guardrails (RQ0),
  and the current scorecard numbers.
- `archive/2026-07-02-mut-id-confidence-fusion` *(spec, shipped)* — MUT-id design
  authority: tiers, grades, `mut_resolution_observation`; the standing contract.
- `2026-06-28-clause-driven-input-generation` *(spec)* — generator contract: clause-driven
  planners, type-capability source, fail-loud SPF→Model seam, SPF-extension evidence gate.
- `2026-06-28-generation-coverage-telemetry` *(note)* — telemetry schema behind the
  generation-coverage plan.
- Audits: `2026-08-04-oracle-refusal-taxonomy` (refusal buckets, re-measure on v3),
  `2026-07-08-autonomous-eval-findings` (RQ6 consumption decision),
  `2026-06-26-applicability-barriers` (RQ6 barrier inventory; counts pre-v3),
  `2026-06-28-mut-id-targeting-and-coverage` (empirical findings home; counts pre-v3),
  `2026-06-28-pipeline-architecture-review` (residue tracker: C-1, D-1),
  `2026-07-01-rerun-observability-priorities` (telemetry ROI ranking),
  `2026-07-02-input-topology-spike` (R1/R2 opportunity bounds; counts pre-v3),
  `2026-07-02-recipe-seam-review` (recipe-seam sequencing + spike definitions),
  `2026-07-05-concretization-census-findings` (census ranking + lever measurements),
  `2026-07-06-evaluation-setup-audit` (run-script/config/analysis cleanup record).

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
