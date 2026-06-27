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
- `2026-06-26-jarvis-case-coverage` *(audit)* — the per-case head-to-head evidence behind it.

## Children

| doc | type | scope | tag |
|---|---|---|---|
| `2026-06-27-jarvis-scoreboard-evaluation-lane` | spec | clean lane for reproducible pinned JARVIS Table-2 evidence | current focus (contract) |
| `2026-06-27-jarvis-scoreboard-evidence-run` | plan | executed pinned JARVIS-era scoreboard run and decided the claim | implemented |
| `2026-06-27-receiver-constructor-inputs` | plan | close the `Interval.getSize()` inline receiver-constructor blocker exposed by the scoreboard run | implemented |
| `2026-06-26-jarvis-case-coverage` | audit | per-case JARVIS head-to-head evidence + provenance | current focus (evidence) |
| `2026-06-26-applicability-barriers` | audit | RQ6 real-world applicability barrier evidence (inventory / funnel / ledger) | deferred — step (2) reference |
| `2026-06-26-data-reuse-and-msr-potential` | note | secondary MSR / data-paper backlog | deferred, gated on (1) |
| `2026-06-25-replication-package-documentation-improvements` | plan | verifiable replication package for ACM artifact eval | independent track |
| `2026-06-27-teralizer-capability-and-improvement-directions` | audit | current capabilities, limitations, improvement directions (post-Phase 1) | reference |
| `2026-06-27-ensemble-mut-identification` | spec | ensemble focal-method oracle (killed mutants + LCBA + name-matching) for MUT identification | applicability track |
| `2026-06-27-inherited-test-method-support` | spec | flatten inherited `@Test` methods into Spoon clones so 5,758 dropped tests parse | backlog |

## Win condition (summary)

"Beat JARVIS" = **capability** (10 JARVIS Table-2 rows entering the pipeline; the current evidence run tracks 14 assertion-level Teralizer probes) **+ PVC/IC**
(parameter-value & instruction coverage ≥ JARVIS on SPF-amenable cases). Current run status: evidence collected, claim not yet supported; `Interval` now enters and passes through receiver-constructor input promotion, while `Abs`/`Precision` remain SPF raw-bits blockers. The original `Interval` paradigm concession only applies after the row enters the pipeline; `Precision` remains an ulps/raw-bits concession. NaN is a shared gap. Full criteria
and the metric definition live in `2026-06-26-beat-jarvis-phase1`; the active evidence lane is `2026-06-27-jarvis-scoreboard-evaluation-lane`, using the archived receiver-input and evidence-run plans plus the live `2026-06-26-jarvis-case-coverage` audit.

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- Current capabilities, limitations, and improvement directions: `2026-06-27-teralizer-capability-and-improvement-directions` *(audit)*.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
