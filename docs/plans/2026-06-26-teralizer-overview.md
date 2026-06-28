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

## Current focus: (1) beat JARVIS

- `2026-06-27-jarvis-scoreboard-evaluation-lane` *(spec)* — the clean evaluation-lane contract for pinned JARVIS-era evidence.
- `2026-06-28-clause-driven-input-generation` *(spec)* — the current generator design: clause-driven `DomainPlanner`s, a single type-capability source, a fail-loud SPF→Model seam, SPF-capability characterization, and generation-coverage telemetry (supersedes `2026-06-27-residual-aware-input-generation`).
- `2026-06-26-jarvis-case-coverage` *(audit)* — the per-case head-to-head evidence behind it.
- `2026-06-28-residual-aware-generator-rerun` *(audit)* — clean rerun on the typed generator: zero Table-2 exclusions after two robustness fixes.
- `2026-06-28-pipeline-architecture-review` *(audit)* — holistic Model⇔Java / SPF-peer / generator / spec-pipeline findings feeding the paper's §5.3 roadmap.
- `2026-06-28-pipeline-improvements` *(plan)* — ordered execution of the architecture-review findings: Teralizer-side hardening, seams, and constraint encoding (correctness wins → seams → encoding).
- `2026-06-28-maxulps-raw-bits-lane` *(plan)* — the research lane to make `Precision.equals(double,double,int maxUlps)` sound by construction (per-probe SPF config + raw-bits Model node + ulps generator).

## Children

| doc | type | scope | tag |
|---|---|---|---|
| `2026-06-27-jarvis-scoreboard-evaluation-lane` | spec | clean lane for reproducible pinned JARVIS Table-2 evidence | current focus (contract) |
| `2026-06-27-residual-aware-input-generation` | spec | v1 typed planner architecture (shipped) | superseded by clause-driven-input-generation |
| `2026-06-28-clause-driven-input-generation` | spec | clause-driven multi-type generation seam + SPF characterization + coverage telemetry | current focus (generator design) |
| `2026-06-27-improved-generator-redesign` | plan | typed planner + jqwik emitter rewrite; shipped, residual-only filtering carried to pipeline-improvements C-3 | implemented |
| `2026-06-27-jarvis-scoreboard-evidence-run` | plan | executed pinned JARVIS-era scoreboard run and decided the claim | implemented |
| `2026-06-27-receiver-constructor-inputs` | plan | close the `Interval.getSize()` inline receiver-constructor blocker exposed by the scoreboard run | implemented |
| `2026-06-26-jarvis-case-coverage` | audit | per-case JARVIS head-to-head evidence + provenance | current focus (evidence) |
| `2026-06-28-residual-aware-generator-rerun` | audit | scorecard rerun on the typed planner: two robustness fixes + refreshed per-probe PVC | current focus (evidence) |
| `2026-06-28-pipeline-architecture-review` | audit | architecture/implementation findings across the spec→generation pipeline | current focus (roadmap) |
| `2026-06-28-pipeline-improvements` | plan | architecture-review findings: Teralizer hardening, seams, constraint encoding | current focus (roadmap) |
| `2026-06-28-maxulps-raw-bits-lane` | plan | per-probe SPF config + raw-bits Model node + by-construction ulps generator for the maxUlps probe | current focus (raw-bits lane) |
| `2026-06-26-applicability-barriers` | audit | RQ6 real-world applicability barrier evidence (inventory / funnel / ledger) | deferred — step (2) reference |
| `2026-06-26-data-reuse-and-msr-potential` | note | secondary MSR / data-paper backlog | deferred, gated on (1) |
| `2026-06-25-replication-package-documentation-improvements` | plan | verifiable replication package for ACM artifact eval | independent track |
| `2026-06-27-ensemble-mut-identification` | spec | ensemble focal-method oracle (killed mutants + LCBA + name-matching) for MUT identification | applicability track |
| `2026-06-27-inherited-test-method-support` | spec | flatten inherited `@Test` methods into Spoon clones so 5,758 dropped tests parse | backlog |

## Win condition (summary)

"Beat JARVIS" = **capability** (10 JARVIS Table-2 rows entering the pipeline; the current evidence run tracks 14 assertion-level Teralizer probes, plus one separate non-Table-2 maxUlps spike) **+ transparent PVC**.
Current run status: all 10 Table-2 rows enter and all generated tests pass. IC is retained as a sanity check only because the current project-level JaCoCo rows conflate all generated probes; the observed Math IC increase comes entirely from the separate maxUlps probe in `Precision.equals(double,double,int)`. Remaining explicit concessions are row-scoped: NaN/signed-zero for min/max and the `toIntExact` overflow path. Full criteria
and the metric definition live in `2026-06-26-beat-jarvis-phase1`; the active evidence lane is `2026-06-27-jarvis-scoreboard-evaluation-lane`, using the archived receiver-input and evidence-run plans plus the live `2026-06-26-jarvis-case-coverage` audit.

The residual-aware generator rerun (2026-06-28) is exclusion-free: all 30 NAIVE + IMPROVED generated tests pass after fixing a non-finite real-scale crash and a surrogate value-log crash. IMPROVED is now ≥ NAIVE on 11/14 passing probes; the affine `eps >= y - x` encoding lowers PVC on the three `Precision.equals` probes (still above JARVIS's 102). Refreshed per-probe PVC and the maxUlps spike gap live in `2026-06-28-residual-aware-generator-rerun`.

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
