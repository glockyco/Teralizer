---
title: Concretization Census — Which SPF Gaps Are Worth Closing
type: spec
status: draft
created: 2026-07-04
parent: 2026-06-26-teralizer-overview
---

# Concretization Census — Which SPF Gaps Are Worth Closing

**One concern:** concretization events now carry a direct, measurable cost — the widening license refuses THROWN and boolean-in-PC generalizations whenever `concretization_events > 0` — but the telemetry records only a count, so we cannot say *which* unmodeled methods cause the refusals or which SPF extensions would pay. Record concretizing-method identities, census them weighted by blocked generalizations, and fix strictly by the evidence gate.

## Why now

- The license made concretization a first-class refusal reason: every avoided event converts refusals into licensed, validated generalizations one-for-one — per-fix ROI is measurable from the DB.
- Observed hotspot: antiaction's 74 THROWN-oracle generalizations all refuse on `concretization_events > 0` (String inputs entering unmodeled JSON parsing). Hundreds more NULL_CONCRETE assertions across the spike carry events.
- Targeted SPF extensions are demonstrably shippable, not hypothetically: `isEmpty` sound modeling and typed unsupported-op signaling landed via `2026-06-30-partial-sound-string-support` Tasks 6/8; the `Long/Boolean.valueOf` attr loss is characterized with a known mechanism (`2026-07-03-boxed-output-capture`).

## Design

1. **Identity telemetry (small listener change).** The `EXECUTENATIVE` hook that increments `concretization_events` additionally records the concretized method's qualified name; per-assertion, persisted as a JSON frequency map in a new nullable TEXT column beside the existing count (JSON-in-TEXT precedent: `mut_resolution_observation`, `generalization_recipe`). The count column stays — it is the license's input and cheap to query.
2. **Census (no full re-run).** Data sources, in cost order: the pipeline fixture corpus (`2026-07-04-pipeline-fixture-corpus`), the five-project sentinel subset, single-project runs on known hotspots (antiaction ≈5 min). Rank methods by `(assertions affected) × (generalizations refused or NULL_CONCRETE because of them)` — events on assertions whose generalizations are licensed and passing carry zero weight.
3. **Triage buckets, fixed policy** (the evidence gate from `2026-06-28-clause-driven-input-generation` §SPF extension governs; this spec only instantiates it):
   - *Bounded* — missing/lossy attr propagation in native peers for value-carrying methods (the `valueOf` family is the worked candidate); individual String ops adjacent to the shipped sound set (`startsWith`/`endsWith`/`contains` — characterize first, per the string plan). Fix the top of this bucket as normal tasks with fixture coverage.
   - *Medium* — content-shape string recipes, bounded-index `charAt`/`substring` (already named deferred in the string plan). Spec individually if the census ranks them high.
   - *Research-grade, recorded not attempted* — symbolic collections/heap shapes, reflection, regex/`matches`, symbolic FP/transcendentals (the archived maxUlps lane is the worked example). These become bounded upstream notes with their census weight attached, so the paper can state the ceiling honestly.

## Acceptance

- Telemetry: per-assertion concretized-method identities persisted; existing count column and license behavior unchanged; listener test covering a native-concretization target (extend `TestGeneralizationListenerConcretizationTest` / `NativeConcretizationTarget`).
- Census: a ranked table (method → affected assertions → blocked gens → bucket) recorded in an audit doc, produced from fixture + sentinel + hotspot runs only — no full-corpus run required.
- At least the top bounded-bucket item fixed with a fixture pinning it, and the refusal→licensed conversion measured on the sentinel subset.
- Research-grade items listed with weights and explicitly not scheduled.
