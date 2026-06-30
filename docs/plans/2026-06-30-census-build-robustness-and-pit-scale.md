---
title: Census Completion — PIT Timeout & Generated-Build Visibility
type: spec
status: draft
created: 2026-06-30
parent: 2026-06-29-beyond-jarvis-generalization-census
---

# Census Completion — PIT Timeout & Generated-Build Visibility

## Context

The first beyond-JARVIS census (`2026-06-29-beyond-jarvis-census-implementation`) ran but did not
complete cleanly. After the listener-correctness fixes (I1 boxed-value capture, P1 typed return-attr;
P2 found unnecessary — see `2026-06-29-beyond-jarvis-census-findings`), two completion items remain:
**I3** (PIT timed out) and **I2** (a malformed generated test cost a whole variant).

Both were re-scoped against DB ground truth (`postgres_dev` = eqbench/commons-utils,
`postgres_test` = RepoReapers) rather than the checked-in HOCON defaults. That evidence overturned
the first draft of this spec: I3 is a **timeout** problem (scoping already exists and is correct),
and I2 is a **visibility** problem (the failure is already recorded, just not surfaced).

## I3 — PIT timeout at census scale

### Problem

`PitDataCollectionTask` runs PIT under a `ConsoleCommand` wall-clock timeout equal to
`pitest.max-execution-time` (census configs: **300s**), enforced at `PitDataCollectionTask:185`. PIT
over a realistic covered-class set takes **hours**, so the census run is killed at 300s and the
variant's PIT + mutation-gain data are lost.

### What already exists (and is correct — do not change)

PIT is already scoped, not run over `*`:

- `targetClasses` = JaCoCo-covered classes with `INSTRUCTION_COVERED > 0`
  (`SQLiteRepository.fetchCoveredClasses`), held **identical for INITIAL and GENERALIZED**
  (`PitDataCollectionTask:130`) so the mutation-gain set difference stays comparable.
- `targetTests` = the included test classes, plus the included generalized classes for the
  GENERALIZED stage.

This is the established all-covered-classes methodology. It is complete and not broken.

### Evidence (DB ground truth)

- **`postgres_dev` (eqbench/commons-utils evaluation):** `COLLECT_PIT_DATA_GENERALIZED` ran **up to
  23,847s (~6.6 h)**, avg ~3 h for `NAIVE_200_TRIES`, **all SUCCEEDED**. The stored
  `project.configuration` has `pitest = { "mutators": "DEFAULTS" }` — **no `max-execution-time`** —
  i.e. PIT ran effectively **unbounded**. Covered classes: 652 (eqbench), 131 (commons-utils).
- **`postgres_test` (RepoReapers evaluation):** ran under the 300s cap and shows **64
  `COLLECT_PIT_DATA_INITIAL` tasks FAILED at ~300.1s** (timeout) plus 270 GENERALIZED failures.

So PIT at this scale legitimately needs hours; the prior *successful* evaluation had no effective PIT
timeout; the census's 300s is the anomaly.

### Recommendation

Raise the census `pitest.max-execution-time` to match the prior evaluation's effective budget — a
large value (≥ the observed ~7 h max, or effectively unbounded). Same class scope, larger budget.
It already reads from HOCON, so this is a per-census-config value.

**Rejected — narrowing `targetClasses` to the classes-under-test:** it would change the mutation
denominator and break comparability with the established all-covered methodology (and with the prior
eval, which ran full covered-scope to completion under a large budget). The first draft of this spec
recommended it; the DB evidence shows it is neither necessary nor methodologically safe.

### Cost / caveat

Census-scale PIT is inherently long (hours per PIT task; several variants → many hours per project),
exactly as the eqbench evaluation took. If faster turnaround is wanted, reduce the **variant set** or
trim the **allowlist** (config choices), not the class scope.

### Acceptance criteria

- Census PIT (`COLLECT_PIT_DATA_GENERALIZED` and `_INITIAL`) over commons-math completes without
  hitting the timeout and produces mutation data; the mutation-gain metric populates.
- `targetClasses` scope is unchanged and identical INITIAL vs GENERALIZED (still comparable).
- The PIT timeout is a per-census-config knob set to a realistic large value.

## I2 — Generated-build failure visibility

### Problem

`ProjectBuildTask` compiles the whole generated suite atomically; one uncompilable generated file
fails `BUILD_PROJECT_GENERALIZED`, dropping **all** that variant's generalizations and downstream
(execution/JaCoCo/PIT). `ProcessingPipeline` catches the exception, records `task.status = FAILED`
with the stack trace in `task.info`, removes the dependent queued tasks, and **continues** other
configs/variants. The run-script `set -euo pipefail` does not catch it because the in-process
pipeline exits 0 — so a dropped variant is only discoverable by querying the `task` table.

### Reassessment (post-I1)

The I1 fix removed the boxed-value codegen bug behind the only observed instance, so uncompilable
generated tests should now be **rare**. When one does occur the failure is already **recorded and
diagnosable**, and the pipeline already isolates other configs/variants. A rare hard-fail-and-record
is therefore acceptable, and a loud failure usefully *surfaces* a generator bug that silent
per-file quarantine would hide. Full validate-and-quarantine is over-engineering at this point.

### Recommendation (minimal — visibility, not quarantine)

Surface dropped variants instead of recording them silently. The swallowing is in
`ProcessingPipeline` (it catches a task exception, marks `task.status = FAILED`, removes dependents,
and continues), so the gradle `run` task — and therefore the script's `set -euo pipefail` — see
success. Fix at that layer, **after the queue drains** so the current mark-and-continue is preserved
(one bad variant must not abort later independent configs): have the runner exit non-zero if any
task ended `FAILED` (the script's `set -e` then surfaces it), and/or emit an end-of-run summary of
the `FAILED` tasks. Cheap, fail-loud, no methodology impact.

### Deferred — validate-and-quarantine

Implement **only if** a full post-I1 census shows uncompilable generated tests *recur*. Sketch for
that case: an in-process `javax.tools.JavaCompiler` check over the generated tests (one invocation,
per-file `Diagnostic`s) → for each failure mark `generalization.is_included = false` with an
`exclusion_info` reason **and remove the `.java` from the build source set** (flagging the row is
insufficient — `ProjectBuildTask` compiles the whole tree; the `…/teralizer-data/tests/<variant>/…`
provenance copy is kept). Rejected the resilient-rebuild-with-output-parsing alternative as fragile.

### Acceptance criteria (for the minimal change)

- A census run that drops a variant build surfaces it (summary line and/or non-zero signal); the
  lost variant is not discoverable only by manual `task`-table query.

## Sequencing & out of scope

- **I3 (timeout) is the immediate census-completion blocker — do it first.** I2 visibility is a
  cheap follow-on. Quarantine is deferred pending evidence of recurrence. Then run a full census to
  validate end-to-end.
- Out of scope: the spf-eval P3/P4/P5 ports, the `reference.conf` variant leak (I4, mitigated by
  report scoping), the mutation-gain metric itself (implemented), and any change to PIT's
  class-scope methodology.
