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

**P1 — decision gates (produce the evidence that ranks P2)**
1. **R2 decision data** — the `actual_shape` + `receiver_provenance` telemetry
   (`2026-07-02-input-topology-spike` §Telemetry) on the next rerun sizes the
   zero-arg-inspector sub-family R2 could soundly take, and measures R1's realized share
   beyond the sentinel signal (694 expression attempts, 2 sound wins, all refusals typed —
   the honest baseline).

   *Resolved:* **`2026-07-04-concretization-census`** *(implemented, archived)* — the ranked
   census landed in `2026-07-05-concretization-census-findings`. Ranking by load-bearing
   blocked generalizations reframed the result: the `valueOf` family is incidental (its
   refusals come from a concrete non-boolean oracle, not the event count; the mechanism
   audit confirmed the box round trip preserves attrs and the refused int is a
   branch-selected constant, so the classification is correct and no SPF fix exists), the
   top load-bearing blocker is a widening-license over-refusal, and the genuine bounded
   peer gaps are small. It promoted the two specs now at the head of P2.

**P2 — evidence-ranked fixes (start as their gate resolves)**

*The two-lever census wave is implemented and archived.* `2026-07-05-sound-char-predicates`
shipped the ASCII interval model (sentinel conversion measured: exactly the 4 recorded
refusals, 311 to 315 included). `2026-07-05-exception-message-widening` shipped the
divergence-risk telemetry and the refined EXCEPTION license after its soundness spike passed
all adversarial shapes. Its antiaction hotspot number batches into the next scheduled corpus
evaluation event. The JARVIS refresh at this wave boundary now waits on operator sign-off
(see standing gates).

3. **ISINTEGER/NOTINTEGER sound-set admission** — the parse-family crash class is fixed
   (`2026-07-05-collect-mode-conformance`), and parse-reaching string MUTs now die typed at
   ingestion. Admitting the parse comparators to the sound set (rendered as a parse-based
   predicate) converts those exclusions into specs, including the xenqtt `AppContext`
   family. Census-gated with the other string-op growth.
4. **`2026-06-28-native-peer-model-coverage`** *(spec, draft)* — crash-visible peer/model
   gaps, per-method ranked (commons-math `Precision.*` targets already named). Complements
   the census: census = silent concretization, this = hard crashes. Task 1 (ranking query)
   is cheap and independent; fixes are evidence-gated per target.

**P3 — measurement infrastructure (parallelizable with P2)**
5. **`2026-07-02-generation-coverage-telemetry`** *(plan, draft)* — clause-shape + parameter
   telemetry; ready to execute as written. Gates C-4 (by-construction recipes) and feeds the
   paper's effectiveness story.
6. **`2026-07-01-pipeline-observability-telemetry`** *(spec, draft)* — reason codes +
   provenance for rerun analysis; `mut_resolution_observation` already landed via fusion.
   Implement before the next large rerun, not before.

**P4 — recall expansion (after soundness/evidence work above)**
7. **`2026-06-27-ensemble-mut-identification`** *(spec, draft)* — the killed-mutant runtime
    tier over fusion v1: PIT_ORIGINAL enablement, oracle corroboration/refutation. The
    designed answer to the coherent-shallow mis-target risk fusion accepted.
8. **`2026-06-27-inherited-test-method-support`** *(spec, draft)* — ~5,758 tests across 52
    projects; adds tests to projects that already work. Do before the full rerun, after the
    soundness work — it only grows the denominator.

**P5 — maintainability / independent tracks**
9. **`2026-06-25-replication-package-documentation-improvements`** *(plan, 6/10)* — ACM
    artifact eval; independent of pipeline work, schedule by paper deadline.
10. **`2026-07-03-harness-support-artifact`** *(spec, draft)* — precompiled telemetry jar;
    deletes the generated-file language-level defect class. Worthwhile, no urgency coupling.
11. **C-1 single-emitter residue check** (`2026-06-28-pipeline-architecture-review`) —
    after the recipe unification wave (P1a.2), verify what if anything remains of the
    factory-drift concern; expected to shrink to nothing.
12. **Small items, no docs needed:** typed exclusion taxonomy (crash exclusions still carry
    raw stack traces); deferred mis-pick fixture; assertThrows-lambda expression-site
    replacement limitation in GeneralizationRecipe (surfaced by the exception-message
    fixture work). The manual read of the T1-widened failures is done: zero mis-picks,
    recorded in `2026-06-28-mut-id-targeting-and-coverage`.

## Standing gates (events, not queue slots)

- **JARVIS scoreboard refresh** — the P2 census wave has reached its boundary, so the
  refresh is due, but it runs only on explicit operator sign-off (measurement events are
  never started unilaterally — see AGENTS.md). REQUIRED before any paper claim
  (`skill://running-the-jarvis-scoreboard`). Pair the refresh with the free manual read of
  the 9 T1-widened failures (no DB contention, feeds the same paper section) and pick up
  the batched antiaction hotspot conversion number for the findings audit in the same
  session.
- **Full evaluation rerun** — not before string corpus verification (P0) and the P2 wave
  land; regenerates RQ results on the broader dataset.

## Parked (infeasible at reasonable effort — recorded for the paper's honesty, not scheduled)

- **Symbolic collections / heap shapes, reflection, regex `matches`, symbolic FP /
  transcendentals** — research-grade SPF work; the census records their weight so the paper
  can state the applicability ceiling honestly (archived maxUlps lane is the worked example).
- **Full-Unicode `Character.isWhitespace`** (general-category membership over thousands of
  code points) — the ASCII interval subset ships via `2026-07-05-sound-char-predicates`.
- **Branch-selected constant int oracles** — the sentinel's 678 `Long.valueOf` refusals are
  boxed `compareTo` idioms returning −1/0/1 selected by a path-condition branch; licensing
  them soundly needs a constant-per-partition argument beyond the boolean-sibling license
  (mechanism recorded in `2026-07-05-concretization-census-findings`).
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
