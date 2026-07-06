---
title: Telemetry Attribution Hardening
type: spec
status: active
created: 2026-07-06
parent: 2026-06-26-teralizer-overview
---

# Telemetry Attribution Hardening

Design and acceptance for preventing the misattribution class the midpoint analysis exposed:
five incorrect conclusions this cycle, each traceable to a systemic telemetry or tooling
property rather than a one-off reading error. The lifecycle telemetry catching the
is_included inflation shows the design principle works — this spec applies it uniformly.

## The failure modes (observed, not hypothetical)

1. Generic reason codes swallowing distinct causes (`UNCAUGHT_EXCEPTION_PATH` = 60% of SPF
   losses, bundling native-peer gaps, model gaps, and JPF-divergent assertions;
   `OTHER_COMPILE_FAILURE` = 100% of generalized build failures).
2. Causal chains fragmented across tables (filter says `MISSING_TESTED_FILE`, resolver knew
   `UNSUPPORTED_ASSERTION_SHAPE` — the report showed only the downstream symptom).
3. Knowable causes dropped at stage boundaries (`final_failure_code = <none>` for 62% of
   non-usable generalizations while the failing task carried "timeout exceeded" in its info).
4. Pipeline interventions without outcome records (the test-source floor silently skipped on
   unresolvable properties; nothing records applied/skipped/why).
5. Silent defaults in analysis tooling (`mut_resolution_funnel` ignored `--db`; the delta
   ledger originally returned silently empty on a wrong path).

## Mechanisms

### M1 — generic-code budget as an integrity invariant

A generic code (`OTHER_*`, `UNCAUGHT_EXCEPTION_PATH`, `UNKNOWN`) exceeding 30% of its
stage's losses is a telemetry defect, not a finding. The report's integrity section gains
one invariant per generic code: share of stage losses, with `holds = share <= 0.30`.
A violated budget means "decompose this code before drawing conclusions from this table."

The two current violations are already queued: the SPF reclassification is
`2026-07-06-funnel-improvement-levers` Lever 3, and `GENERATED_SOURCE_LEVEL_TOO_NEW`
(specced in `2026-07-01-rerun-observability-priorities` B2, never implemented) lands with
the floor fix in Lever 2.

### M2 — first-cause attribution view

One report section joins the causal chain per excluded assertion and attributes the loss
to its upstream-most explanation, in precedence order: assertion-kind support
(`assertion_semantics`) → MUT resolution (`mut_resolution_observation.no_pick_reason`) →
filter reason code → SPF loss cause → license refusal → lifecycle failure. Every excluded
assertion appears exactly once, under its first cause. The existing per-table sections
stay (they answer per-stage questions); the attribution view answers "why did we lose it,"
which is the question every planning decision actually asks. Pure analysis work: the DB
already carries every edge.

### M3 — knowable causes propagate to lifecycle rows

`generalization_lifecycle.final_failure_code` must never be `<none>` when the failing
stage's task diagnostic knows the cause. The propagation plumbing already exists:
`ProcessingPipeline` hands `TaskDiagnosticWriter.recordFailure`'s reason code straight to
`GeneralizationLifecycleWriter.recordStageFailed`. The defect is upstream:
`TaskDiagnosticWriter.isDiagnosticStage` excludes `EXECUTE_TESTS_GENERALIZED`, so
`recordFailure` returns null before classifying anything. Fix: admit the stage, and add a
classifier rule mapping the `ConsoleCommandException` timeout shape ("Command execution
timeout exceeded") to a new stable code `SUITE_TIMEOUT` (distinct from `EXECUTION_TIMEOUT`,
which is the per-assertion SPF abort). Integrity invariant: non-usable lifecycle rows with
empty failure_code = 0.

### M4 — interventions record outcomes

Every silent pipeline mutation gets an outcome record in the existing observation tables.
First targets, one column each on `build_environment_observation`:

- `test_source_floor`: `APPLIED | NOT_NEEDED | SKIPPED_UNRESOLVABLE`
- `surefire_floor`: same shape

The sweep of stale generated tests and the import merge follow the same pattern only if
they ever surface in an investigation (YAGNI until then). Rule for future interventions:
an intervention that can silently skip MUST record that it did.

### M5 — analysis CLI convention: loud basis, no silent defaults

Every analysis module that reads a database:

- takes `--db` (no hardwired engine calls in `main()`),
- prints a basis header before any table: resolved database, project/assertion counts,
  and the run-progress state (so "at 60% of the corpus" qualifies every number
  automatically),
- fails loudly on missing inputs (the ledger convention from the delta section),
- skips with a printed reason when a table/column predates the snapshot (the existing
  `_SKIP_NOTE` convention).

A small shared helper in `analysis/src/teralizer/` carries the header and the engine
resolution so the convention costs one import.

### M6 — exemplars beside every top-N count

Aggregates invite narrative; instances resist it. The report's top reason-code tables gain
a `sample` column: up to three randomly sampled `assertion_source_code` snippets (or task
messages) per top bucket. The boxing-spec refutation and the UNCAUGHT decomposition both
came from sampling — the report should hand the reader the sample before they build a
story on a count.

## Tasks

- [x] M1 generic-code budget invariants in the report integrity section.
- [ ] M3 lifecycle failure-code propagation + `SUITE_TIMEOUT` code + invariant
      (implementation shared with levers spec Lever 2's timeout work).
- [ ] M4 floor/surefire outcome columns + writer changes + jOOQ regen.
- [ ] M2 first-cause attribution view in the report.
- [ ] M5 basis-header helper + adoption in the DB-reading analysis modules.
- [x] M6 exemplar sampling on the top reason-code tables.

## Non-goals

- A generic event/tracing framework (rejected in `2026-07-01-rerun-observability-priorities`,
  still right).
- Retroactive reclassification of existing snapshots (the codes improve from the next run
  forward; old snapshots stay as-of-date evidence).
- Schema changes beyond the two observation columns and one stable code.
