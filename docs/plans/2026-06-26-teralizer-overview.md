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
2. **Unblock corpus applicability** — finish string corpus verification, then close the
   remaining evidence-ranked extraction and generation gaps.
3. **Extend data collection** — add the provenance/tagging that makes the corpus reusable.
4. **Re-run the full evaluation** — regenerate RQ results on the broader dataset.

No full evaluation rerun is planned until string corpus verification lands.

## Priority queue

Every planned work item, ranked. Ordering logic: cheap verification debt first (it can
invalidate later readings), then decision-gates (spikes and censuses that *rank* the work
below them), then the fixes those gates rank, then measurement infrastructure, then recall
expansion, then maintainability. Evidence-gated items never start before their gate.

**P1 — decision gates (produce the evidence that ranks later fixes)**
1. **R2 decision data** — the `actual_shape` + `receiver_provenance` telemetry
   (`2026-07-02-input-topology-spike` §Telemetry) on the next rerun sizes the
   zero-arg-inspector sub-family R2 could soundly take, and measures R1's realized share
   beyond the sentinel signal.

**P2 — evidence-ranked fixes (start as their gate resolves)**
2. **`2026-07-05-parse-predicate-admission`** *(spec, draft)* — admit ISINTEGER/NOTINTEGER
   to the string sound set: parse-guarded string MUTs die typed at ingestion
   (`2026-07-05-collect-mode-conformance`); rendering delegates to `parseInt` for exact
   semantics, generation satisfies the partition. Converts the xenqtt `AppContext` family
   and siblings.
3. **`2026-06-28-native-peer-model-coverage`** *(spec, draft)* — crash-visible peer/model
   gaps, per-method ranked (commons-math `Precision.*` targets already named). Complements
   the concretization census: census = silent concretization, this = hard crashes. Task 1
   (ranking query) is cheap and independent; fixes are evidence-gated per target.

**P3 — measurement infrastructure (parallelizable with P2)**
4. **`2026-07-02-generation-coverage-telemetry`** *(plan, draft)* — clause-shape + parameter
   telemetry. Gates C-4 (by-construction recipes) and feeds the paper's effectiveness
   story.
5. **`2026-07-01-pipeline-observability-telemetry`** *(spec, draft)* — reason codes +
   provenance for rerun analysis; its `task_diagnostic` reason codes subsume the typed
   exclusion taxonomy. Implement before the next large rerun, not before.

**P4 — recall expansion (after soundness/evidence work above)**
6. **`2026-06-27-inherited-test-method-support`** *(spec, draft)* — ~5,758 tests across 52
   projects; adds tests to projects that already work. Do before the full rerun, after the
   soundness work — it only grows the denominator.

**P5 — maintainability / independent tracks**
7. **`2026-06-25-replication-package-documentation-improvements`** *(plan, 6/10)* — ACM
   artifact eval; independent of pipeline work, schedule by paper deadline.
8. **`2026-07-03-harness-support-artifact`** *(spec, draft)* — precompiled telemetry jar;
   deletes the generated-file language-level defect class. Worthwhile, no urgency coupling.
9. **C-1 single-emitter residue check** (`2026-06-28-pipeline-architecture-review`) —
   after the recipe unification wave, verify what if anything remains of the factory-drift
   concern; expected to shrink to nothing.
10. **Small items, no docs needed:** deferred mis-pick fixture; assertThrows-lambda
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
- **Full evaluation rerun** — not before string corpus verification lands; regenerates RQ
  results on the broader dataset. Implement the P3 telemetry and P4 recall items first so
  the rerun collects their data in one pass.

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
  `2026-07-05-concretization-census-findings` (census ranking + lever measurements).

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
