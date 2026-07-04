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
2. **Unblock corpus applicability** — MUT-id fusion v1 has landed; run string corpus
   verification against the newly-resolved targets, then close the evidence-ranked SPF gaps.
3. **Extend data collection** — add the provenance/tagging that makes the corpus reusable.
4. **Re-run the full evaluation** — regenerate RQ results on the broader dataset.

No full evaluation rerun is planned until MUT identification and string corpus verification land.

## Priority queue

Every planned work item, ranked. Ordering logic: cheap verification debt first (it can
invalidate later readings), then decision-gates (spikes and censuses that *rank* the work
below them), then the fixes those gates rank, then measurement infrastructure, then recall
expansion, then maintainability. Evidence-gated items never start before their gate.

**P0 — verification debt (blocks clean readings)**
1. **Sentinel exposure of the ingestion-totality change** — no doc; run the sentinel subset
   once (~10 min). The ingestion contract change (typed refusal of derived string symbols)
   has never seen real-world projects; its census shift must be known before any other run
   is interpreted. Doubles as `2026-06-30-partial-sound-string-support` Task 7 Steps 2–3,
   likely completing that plan.

**P1 — decision gates (produce the evidence that ranks P2)**
2. **R1 viability spike** (`2026-07-02-recipe-seam-review` §Spikes) — ~1 day, hand-built
   wrappers, no pipeline changes. Decides whether the R1 expression-slice spec gets written
   at all and which topology buckets pay; scopes the next implementation wave.
3. **`2026-07-04-concretization-census`** *(spec, draft — awaiting review)* — identity
   telemetry + ranked census of concretizing methods. Produces the ROI ranking that gates
   every SPF-gap fix below (boxed-capture, native peers, string-op growth).

**P2 — evidence-ranked fixes (start as their gate resolves)**
4. **Boxed-capture upstream** (`Long/Boolean.valueOf` attr loss) — the worked candidate for
   the census's bounded bucket; mechanism already characterized, so it can start as soon as
   the census confirms its rank (or immediately if review stalls — the mechanism evidence
   already exists).
5. **`2026-07-03-symbolic-sibling-throws`** *(spec, draft)* — spike first (three questions
   in the doc), then finalize. Recovers the xenqtt −8 class; the pruning-soundness cliff
   makes this design-gated, not ad-hoc.
6. **`2026-06-28-native-peer-model-coverage`** *(spec, draft)* — crash-visible peer/model
   gaps, per-method ranked (commons-math `Precision.*` targets already named). Complements
   the census: census = silent concretization, this = hard crashes. Task 1 (ranking query)
   is cheap and independent; fixes are evidence-gated per target.

**P3 — measurement infrastructure (parallelizable with P2)**
7. **`2026-07-02-generation-coverage-telemetry`** *(plan, draft)* — clause-shape + parameter
   telemetry; ready to execute as written. Gates C-4 (by-construction recipes) and feeds the
   paper's effectiveness story.
8. **`2026-07-01-pipeline-observability-telemetry`** *(spec, draft)* — reason codes +
   provenance for rerun analysis; `mut_resolution_observation` already landed via fusion.
   Implement before the next large rerun, not before.

**P4 — recall expansion (after soundness/evidence work above)**
9. **`2026-06-27-ensemble-mut-identification`** *(spec, draft)* — the killed-mutant runtime
   tier over fusion v1: PIT_ORIGINAL enablement, oracle corroboration/refutation. The
   designed answer to the coherent-shallow mis-target risk fusion accepted.
10. **`2026-06-27-inherited-test-method-support`** *(spec, draft)* — ~5,758 tests across 52
    projects; adds tests to projects that already work. Do before the full rerun, after the
    soundness work — it only grows the denominator.

**P5 — maintainability / independent tracks**
11. **`2026-06-25-replication-package-documentation-improvements`** *(plan, 6/10)* — ACM
    artifact eval; independent of pipeline work, schedule by paper deadline.
12. **`2026-07-03-harness-support-artifact`** *(spec, draft)* — precompiled telemetry jar;
    deletes the generated-file language-level defect class. Worthwhile, no urgency coupling.
13. **C-1 single emitter** (`2026-06-28-pipeline-architecture-review`) — last open
    architecture-review residue besides D-1 (D-1's telemetry half = the census). Codegen
    drift risk, effort M.
14. **Small items, no docs needed:** typed exclusion taxonomy (crash exclusions still carry
    raw stack traces); manual read of the 9 T1-widened failures (license residual-risk
    story); deferred mis-pick fixture.

## Standing gates (events, not queue slots)

- **JARVIS scoreboard refresh** — stale since the widening license; REQUIRED before any
  paper claim (`skill://running-the-jarvis-scoreboard`). Every behavior change re-stales
  it, so run it at a wave boundary (after P2 settles), never mid-wave.
- **Full evaluation rerun** — not before string corpus verification (P0) and the P2 wave
  land; regenerates RQ results on the broader dataset.

## Parked (infeasible at reasonable effort — recorded for the paper's honesty, not scheduled)

- **Symbolic collections / heap shapes, reflection, regex `matches`, symbolic FP /
  transcendentals** — research-grade SPF work; the census records their weight so the paper
  can state the applicability ceiling honestly (archived maxUlps lane is the worked example).
- **SPF-eval listener ports** (bit-exact float/NaN capture, heap-PC capture, raw-PC logging)
  — upstream-SPF effort with no current consumer.
- **FastMath/Interval generalization investigation** — superseded in value by the census;
  revisit only if the census ranks FastMath concretizations high.
- **Non-value-oracle generalization** (no-throw / expected-throw oracles) — revisit after
  P4 lands; value-oracle MUT discovery had to unclog first (fusion), but the generator work
  is still unscoped.
- **`2026-06-26-data-reuse-and-msr-potential`** *(note)* — secondary MSR / data-paper
  backlog; not this paper.

## Reference map (design authorities and evidence — read on demand)

- `2026-06-30-jarvis-comparison` *(spec)* — comparison contract, metrics, guardrails (RQ0).
- `2026-07-02-mut-id-confidence-fusion` *(spec)* — MUT-id design authority: tiers, grades,
  `mut_resolution_observation`. Implementation (fusion v1) landed; stays as the contract.
- `2026-06-28-clause-driven-input-generation` *(spec)* — generator contract: clause-driven
  planners, type-capability source, fail-loud SPF→Model seam, SPF-extension evidence gate.
- `2026-06-28-generation-coverage-telemetry` *(note)* — telemetry schema behind P3.7.
- Audits: `2026-06-26-applicability-barriers` (RQ6 barrier inventory),
  `2026-06-28-mut-id-targeting-and-coverage` (empirical findings home),
  `2026-06-28-pipeline-architecture-review` (residue tracker: C-1, D-1),
  `2026-07-01-rerun-observability-priorities` (telemetry ROI ranking),
  `2026-07-02-input-topology-spike` (R1/R2 opportunity bounds),
  `2026-07-02-recipe-seam-review` (recipe-seam sequencing + spike definitions).

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
