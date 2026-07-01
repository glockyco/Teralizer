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

## Current focus

Step 1 (beat JARVIS) is won and consolidated, so the next focused implementation work is two
no-PIT improvements, each attacking one dominant applicability-funnel blocker. Land both before any
full re-run so a single run measures the improved implementation.

1. **`2026-06-30-static-mut-identification`** *(plan)* — precedence-cascade MUT-id (abstain-on-ambiguity,
   with evidence-gated mis-targeting checks) attacking the `MissingValue` blocker (≈ half the corpus).
   **Do first** — it takes the safe single-producer recall, and its Task 1 diagnostic sizes the other
   levers before any recall-reducing veto lands.
2. **`2026-06-30-partial-sound-string-support`** *(plan, mostly implemented)* — generalize over the
   string ops SPF handles soundly (equals / equalsIgnoreCase / startsWith / endsWith / contains /
   length / indexOf / concat / isEmpty + `String` returns). **Funnel caveat:** the rerun shows the
   `ParameterType` / `ReturnType` rejects are dominated by *parameterless* methods, *unresolved* MUTs,
   and *custom-object* returns — **not** String — so this lever's corpus payoff is gated behind MUT-id
   (few resolved String MUTs surface until then). Capability + soundness are in place and tested now.

MUT-id is `draft` (do first); string support is `active` and largely implemented on the `string-support` branch (see its Progress section — Task 4b + corpus verification remain). Each plan carries its own task breakdown and acceptance.

## Map (by current relevance)

Read this after `INDEX.md`. Grouped by how load-bearing each doc is now; `INDEX.md`
carries the full status/parent tree and `archive/` the retired docs.

**Read first — the JARVIS comparison (step 1) is won and consolidated**
- `2026-06-30-jarvis-comparison` *(spec, RQ0)* — **the single home for the whole JARVIS
  comparison**: unit (distinct MUT), metrics (IC by construction, PVC construct-validity,
  mutation as the honest metric), and four axes with current-code evidence — Table-2
  head-to-head (8/9 MUTs; PVC 4 win / 5 trail / 1 absent; SPF spike 8 FULL/1 PARTIAL/2
  BLOCKED), budget elasticity (PVC ~10x, kills flat), breadth (26 MUTs / 250 sound
  properties across 6 classes), and the soundness/automation tradeoff — plus threats and
  provenance. The absorbed per-case, elasticity, and census fragments are in `archive/`.
  Next: write RQ0 into the paper and migrate the granular numbers to an analysis notebook.

**Active — generator & applicability tracks**
- `2026-06-28-clause-driven-input-generation` *(spec)* — generator design: clause-driven
  planners, single type-capability source, fail-loud SPF→Model seam.
- `2026-06-27-generalizable-input-rule` *(spec)* — shared input-eligibility rule for every consumer.
- `2026-06-28-generation-coverage-telemetry` *(note)* — telemetry schema the generator self-reports against.
- `2026-06-28-pipeline-architecture-review` *(audit)* — architecture findings feeding the paper's §5.3 roadmap.
- `2026-06-27-ensemble-mut-identification` *(spec, draft)* — killed-mutant focal-method oracle replacing LCBA.
- `2026-06-28-mut-id-targeting-and-coverage` *(audit)* — MUT-id targets, mutation-data coverage, telemetry gaps.
- `2026-06-30-static-mut-identification` *(plan, draft)* — static (no-PIT) MUT-id recall via a precedence cascade with hard abstain; the static subset of the ensemble spec.
- `2026-06-30-partial-sound-string-support` *(plan, active)* — string ops SPF handles soundly (incl. `isEmpty`, `equalsIgnoreCase`, `String` returns); unsound/unsupported ops excluded structurally. Mostly implemented; Task 4b + corpus verification (gated on MUT-id) remain.

**Active — independent track**
- `2026-06-25-replication-package-documentation-improvements` *(plan)* — verifiable replication package for ACM artifact eval.

**Reference & deferred — step (2), gated on beating JARVIS**
- `2026-06-26-applicability-barriers` *(audit)* — RQ6 real-world barrier evidence (inventory / funnel / ledger).
- `2026-06-26-data-reuse-and-msr-potential` *(note)* — secondary MSR / data-paper backlog.

**Backlog**
- `2026-06-27-inherited-test-method-support` *(spec, draft)* — flatten inherited `@Test` methods so 5,758 dropped tests parse.
- Census follow-ups (deferred): FastMath/Interval yield no FULL generalizations (investigate —
  transcendentals → `NonGeneralizableExpressionException`, loop-style assertions → `DEFER`);
  spf-eval listener ports — P3 bit-exact float/NaN-payload capture, P4 heap-PC capture for
  object/boxed params, P5 `header.toString()` vs `stringPC()` for raw PC logging.
- Non-value-oracle generalization (deferred, low priority): generalize tests whose MUT yields no
  comparable value, replacing the symbolic-output oracle. Two sub-ideas: (a) **no-throw oracle** —
  for assertion-free tests that completed normally, generalize the MUT's input partition and assert
  the call raises no exception; (b) **expected-throw oracle** — for `@Test(expected=…)` /
  `assertThrows` / `ExpectedException`-rule tests, assert the *same* exception holds across the
  partition. Caveats: the ~7,002 `NoAssertionsFilter`-rejected tests are the natural population but
  are test-level (no `assertion` rows) and split between (a) and (b) — a no-throw oracle is only
  valid for runs that actually completed normally, so the two must be told apart first. The hard
  part is anchor-free **MUT discovery** (no asserted value to trace, unlike the value-oracle MUT-id)
  and the oracle is weak (only catches throw-behavior flips). Resolved void MUTs today are
  negligible (24 / 35,299 assertions ≈ 0.07%, ~0 yield); a breadth idea for after the value-oracle
  funnel is unclogged.

**Superseded & shipped** — lineage in `INDEX.md` / `archive/`.

## Win condition (summary)

"Beat JARVIS" = **capability** + **honestly-caveated metrics**, all consolidated in
`2026-06-30-jarvis-comparison`:
- **Capability:** 8 of JARVIS's 9 Table-2 MUTs soundly generalized (the 10 rows enter as 12
  passing assertion-level probes); the one gap, `Precision.equals`, is a deliberate raw-bits
  soundness abstention. The rejected paper handled 9/10 only because its static-method/numeric
  selection excluded `char`, instance-method / object-construction, and FastMath — all now
  enter and pass.
- **PVC:** IMPROVED beats JARVIS's published Scala-PBT PVC on 4 of 9 scored rows (both `char`,
  `toIntExact`, `IntervalTest`); the 5 trails are single-`double` rows (a sampling-strategy
  difference, not a soundness/capability gap); `PrecisionTest` is absent (sound-excluded). PVC
  is also budget-elastic (×10 with tries) while kills stay flat — so mutation score, not PVC,
  is the honest metric.
- **Breadth beyond JARVIS:** 26 distinct MUTs / 250 sound properties across 6 upstream classes
  (`CharUtils` 2→10 within a JARVIS class; 4 whole classes JARVIS never reported).

## Pointers

- Repo conventions + commands: `AGENTS.md`. Planning index: `docs/plans/INDEX.md`.
- SPF spike harness + type-support study: `~/Projects/phd-thesis/projects/spf-eval/`.
- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
