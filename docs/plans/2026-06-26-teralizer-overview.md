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

The paper was rejected on three grounds: weak RQ1 mutation-score gains, low RQ6
real-world applicability, and no comparison to the SOTA tool **JARVIS**. The
north-star is therefore: **beat JARVIS on its own cases.**

## Strategy sequence

1. **Beat JARVIS** — match/exceed it on its published Table-2 cases.
2. **Extend data collection** — add the provenance/tagging that makes the corpus reusable.
3. **Re-run the full evaluation** — regenerate RQ results on the broader dataset.

**(2) and (3) happen only if (1) is promising. No full re-run is planned now** — so any
work that depends on a re-run is deferred until (1) lands.

## Map (by current relevance)

Read this after `INDEX.md`. Grouped by how load-bearing each doc is now, not by
type; `INDEX.md` carries the full status/parent tree and `archive/` the retired docs.

**Read first — current focus: (1) beat JARVIS**
- `2026-06-27-jarvis-scoreboard-evaluation-lane` *(spec)* — evaluation-lane contract for pinned JARVIS-era evidence.
- `2026-06-28-clause-driven-input-generation` *(spec)* — current generator design: clause-driven planners, single type-capability source, fail-loud SPF→Model seam.
- `2026-06-28-generation-coverage-telemetry` *(note)* — telemetry schema the generator self-reports against.
- `2026-06-27-generalizable-input-rule` *(spec)* — shared input-eligibility rule for every consumer.
- `2026-06-28-residual-aware-generator-rerun` *(audit)* — latest scorecard rerun: zero Table-2 exclusions after two robustness fixes.
- `2026-06-26-jarvis-case-coverage` *(audit)* — per-case Table-2 targets + provenance.
- `2026-06-28-pipeline-architecture-review` *(audit)* — architecture/implementation findings feeding the paper's §5.3 roadmap.
- `2026-06-28-pipeline-improvements` *(plan)* — ordered execution of those findings.
- `2026-06-28-maxulps-raw-bits-lane` *(plan)* — research lane for the maxUlps raw-bits probe.

**Active — applicability track (secondary to JARVIS)**
- `2026-06-27-ensemble-mut-identification` *(spec, draft)* — killed-mutant focal-method oracle replacing LCBA.
- `2026-06-28-mut-id-targeting-and-coverage` *(audit)* — MUT-id concrete targets, mutation-data coverage, telemetry gaps.

**Active — independent track**
- `2026-06-25-replication-package-documentation-improvements` *(plan)* — verifiable replication package for ACM artifact eval.

**Reference & deferred — step (2), gated on beating JARVIS**
- `2026-06-26-applicability-barriers` *(audit)* — RQ6 real-world barrier evidence (inventory / funnel / ledger).
- `2026-06-26-data-reuse-and-msr-potential` *(note)* — secondary MSR / data-paper backlog.

**Backlog**
- `2026-06-27-inherited-test-method-support` *(spec, draft)* — flatten inherited `@Test` methods so 5,758 dropped tests parse.

**Superseded & shipped** — lineage in `INDEX.md` / `archive/`: `2026-06-27-residual-aware-input-generation` (superseded by clause-driven-input-generation), plus the shipped redesign, evidence-run, and receiver-constructor plans.

## Win condition (summary)

"Beat JARVIS" = **capability** (10 JARVIS Table-2 rows entering the pipeline; the current evidence run tracks 14 assertion-level Teralizer probes, plus one separate non-Table-2 maxUlps spike) **+ transparent PVC**.
Current run status: all 10 Table-2 rows enter and all generated tests pass. IC is retained as a sanity check only because the current project-level JaCoCo rows conflate all generated probes; the observed Math IC increase comes entirely from the separate maxUlps probe in `Precision.equals(double,double,int)`. Remaining explicit concessions are row-scoped: NaN/signed-zero for min/max and the `toIntExact` overflow path. Full criteria
and the metric definition live in `2026-06-26-beat-jarvis-phase1`; the active evidence lane is `2026-06-27-jarvis-scoreboard-evaluation-lane`, using the archived receiver-input and evidence-run plans plus the live `2026-06-26-jarvis-case-coverage` audit.

The residual-aware generator rerun (2026-06-28) is exclusion-free: all 30 NAIVE + IMPROVED generated tests pass after fixing a non-finite real-scale crash and a surrogate value-log crash. IMPROVED is now ≥ NAIVE on 11/14 passing probes; the affine `eps >= y - x` encoding lowers PVC on the three `Precision.equals` probes (still above JARVIS's 102). Refreshed per-probe PVC and the maxUlps spike gap live in `2026-06-28-residual-aware-generator-rerun`.

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
