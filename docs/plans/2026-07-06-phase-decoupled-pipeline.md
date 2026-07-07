---
title: Phase-Decoupled Pipeline (Generation / Generalization / Reduction)
type: spec
status: active
created: 2026-07-06
parent: 2026-06-26-teralizer-overview
---

# Phase-Decoupled Pipeline

Make **generation**, **generalization**, and **reduction** independently
requestable phases that execute sequentially and resume across invocations
against a persisted on-disk workspace. Requesting a phase runs or reruns it as
a whole unit.

## Problem

The pipeline schedules the entire DAG up front into one priority queue, gated
by two booleans (`use_test_generation`, `use_test_generalization`), and drains
it in a single pass. Two defects follow.

- **Failure coupling.** On any task failure, `ProcessingPipeline` drops every
  queued task that matches the failed task's `(projectId, testId, assertionId,
  generalizationId)` tuple. Variant is not in the predicate, and almost all
  stage tasks are project-level with the sub-ids null, so a failed project-level
  task drops the entire remainder of the project. A Stage-5 mutation or coverage
  failure (for example `COLLECT_PIT_DATA_INITIAL` hitting its timeout) therefore
  deletes the Stage-4 generalizations that never depended on it.
- **Order and paper mismatch.** `COLLECT_JACOCO_DATA_INITIAL` and
  `COLLECT_PIT_DATA_INITIAL` run before `GENERALIZE_TESTS`, and they carry
  `variant = null`, so the comparator (variant before step) sorts them ahead of
  every per-variant generalization stage. Measurement runs before the thing it
  measures, contradicting the paper's Stage 4 then Stage 5 order.

There is no resume today. Each invocation builds a fresh `ProjectRecord` and
cascades from `DOWNLOAD_PROJECT` through the whole DAG. Getting mutation numbers
requires one end-to-end run, so iterating on reduction re-does generalization.

## Goal

- Three independent phase toggles. Any combination is legal to request.
- Phases execute sequentially in canonical order: generation, generalization,
  reduction. Requesting a phase clears its prior outputs and re-executes it.
- A later invocation requesting only reduction runs Stage 5 against the
  generalization artifacts already on disk, without re-running generalization.
- A reduction failure can never drop generalization results.
- Two reportable milestones: passed generalization (Stage 4) and has reduction
  metrics (Stage 5).

## Approach

First-class phases plus a sequential planner (approach C). Tasks, the priority
queue, and the comparator are reused. The change lives in the orchestration
layer plus a targeted correctness fix to the cascade-drop predicate.

### Components

- **`PipelinePhase`** — one object per phase (`GENERATION`, `GENERALIZATION`,
  `REDUCTION`). Each owns: its internal stages, a `clear()` that idempotently
  tears down that phase's own DB rows and on-disk artifacts, a `preconditions()`
  check that fails loud when a required input is missing, and a
  `successPredicate()` used by reporting.
- **`PipelinePlanner`** — for each requested phase in canonical order: run
  `clear()`, verify preconditions, schedule the phase entry task, drain the
  queue, then advance. Draining between phases is what isolates them.
- **`ProjectIdentity`** — resolve-or-create the `ProjectRecord` by `root_path`.
  When a record already exists at that path, compare a hash of the persisted
  identity-defining configuration against the current render. A material
  mismatch fails loud rather than silently attaching to a different project.

### Phase-to-stage mapping

| Phase | Toggle | Internal stages | Paper stage |
|---|---|---|---|
| generation | `use-test-generation` | `GENERATE_EVOSUITE_TESTS`, `POSTPROCESS_EVOSUITE_TESTS` | pre-stage |
| generalization | `use-test-generalization` | `BUILD_SPOON_MODEL` through `FILTER_GENERALIZATIONS`, including per-variant `GENERALIZE_TESTS`, `BUILD_PROJECT_GENERALIZED`, `EXECUTE_TESTS_GENERALIZED`, `COLLECT_JUNIT_REPORTS_GENERALIZED`, and the `INITIAL` build and test execution | 1 through 4 |
| reduction | `use-test-reduction` (new) | `COLLECT_PIT_DATA_ORIGINAL`, `COLLECT_JACOCO_DATA_INITIAL`, `COLLECT_PIT_DATA_INITIAL`, then per variant `RESTORE_GENERALIZED_BUILD`, `COLLECT_JACOCO_DATA_GENERALIZED`, `COLLECT_PIT_DATA_GENERALIZED` | 5 |

The reduction measurement collectors are exactly the `stage_5` set defined in
`analysis/src/teralizer/stages.py`, so the paper-stage taxonomy is unchanged; the
new `RESTORE_GENERALIZED_BUILD` stage is a reduction-owned build step that carries
no measurement rows and is also classified Stage 5.
`COLLECT_JACOCO_DATA_ORIGINAL` stays in generalization: its only consumer is the
analysis-layer no-coverage exclusion, a Stage-1+2 membership concern, and
`filterTestOriginal` reads no coverage.

A lightweight bootstrap precedes the phases: resolve identity, ensure the
project is on disk (`ProjectDownloadTask` already skips when present), and ensure
the tool build files exist. Bootstrap is idempotent on resume.

### Rerun and resume semantics

Requesting a phase is uniform: `clear()` then run. `clear()` is idempotent, so a
first run and a rerun take the same path with no branching. `clear()` composes
the existing `CleanupTask` teardown for that phase's artifacts and deletes that
phase's own DB rows.

- `GENERALIZATION.clear()` removes generalization records and their filter
  results, coverage-telemetry and jqwik-execution rows, the phase's task rows,
  and the `_*_Generalized_*` sources on disk.
- `REDUCTION.clear()` removes `pit_mutation_report`, `pit_coverage_report`, and
  `jacoco_coverage_report` rows for the ORIGINAL, INITIAL, and GENERALIZED
  stages, plus the phase's task rows. It does not touch the preserved `.exec`
  files or the per-variant generalized-source archives, which are
  generalization-phase outputs it consumes as inputs.

A non-requested phase is not scheduled. Its artifacts must already exist on disk
and in the DB, which the downstream requested phase's precondition verifies.
Resume is therefore not a special mode. It is running a phase whose upstream
artifacts already exist.

### Preconditions (fail loud)

Preconditions are artifact-based, so they hold identically whether the
prerequisite ran in this invocation or a prior one.

- generalization requires the project's tests present on disk.
- reduction requires the per-variant generalized-source archives present under
  the data directory (the canonical reduction input, since the workspace holds
  only the last variant after generalization) and the original build artifacts.
  Reduction requested with no archived generalized sources raises a hard error
  naming the missing artifact rather than silently producing nothing.

This extends the existing fail-loud convention. `PitDataCollectionTask` already
throws on empty target classes, empty generalized tests, empty target tests, and
a missing report file.

### Per-stage JaCoCo `.exec` preservation

JaCoCo report goals only process an existing `jacoco.exec`. The agent is bound to
the test phase during `EXECUTE_TESTS_*`, and `prepare-agent` defaults to
`append = true` against the default `target/jacoco.exec`. `BUILD_PROJECT_GENERALIZED`
runs `mvn clean`, which deletes the INITIAL `.exec` before a deferred reduction
report could read it. Deferral therefore requires preserving each stage's `.exec`.

Each `EXECUTE_TESTS_*` copies `target/jacoco.exec` to a stage-scoped and
variant-scoped path under the project data directory. The deferred
`COLLECT_JACOCO_DATA_*` reports from the preserved copy (`jacoco:report` with the
data file pointed at the preserved path, or the Gradle `executionData`
equivalent). This keeps INITIAL and GENERALIZED coverage distinct and free of the
accumulation the shared default file would otherwise cause.

### Per-variant generalized-source preservation and isolated restore

Generalized test sources are variant-specific (each carries a unique
`generalizationId` in its class name), but the per-variant `CLEANUP_GENERALIZATION`
deletes every `_*_Generalized_*` source at the start of each variant iteration, so
the workspace holds exactly one variant at a time. That interleaving is
load-bearing: `BUILD_PROJECT_GENERALIZED` compiles and `EXECUTE_TESTS_GENERALIZED`
runs one variant in isolation, and `mvn pitest:mutationCoverage` mutates whatever
sits in `target/test-classes` with no lifecycle recompile. Deferring all
generalized measurement to a reduction phase would therefore run every variant's
mutation against the last variant's compiled classes.

The fix preserves per-variant isolation rather than letting variants coexist.
During generalization, `EXECUTE_TESTS_GENERALIZED` copies the variant's
`_*_Generalized_*` sources to a per-variant archive under the data directory
(`generalized-sources/<variant>/`), the same per-variant `data/` convention the
`.exec` preservation uses. The existing per-variant `CLEANUP_GENERALIZATION` is
unchanged, so generalization behaviour and single-variant fixtures are unaffected.

Reduction owns a new `RESTORE_GENERALIZED_BUILD` stage that, per variant, restores
that variant's archived sources into the workspace and rebuilds so `target/` holds
only that variant, before its `COLLECT_JACOCO_DATA_GENERALIZED` and
`COLLECT_PIT_DATA_GENERALIZED` run. The comparator already orders variant before
step, so each variant's restore-build-measure sequence completes before the next
variant begins, and the variant-null ORIGINAL/INITIAL collectors sort first and
run against the original build. Isolation holds at both source and `target/`, one
variant resident at any moment, never shared.

### Variant-aware cascade-drop

The cascade-drop predicate gains variant awareness. A variant-scoped task failure
drops only that variant's queued tasks. A shared task failure (variant null)
still cascades to the whole project, since per-variant work legitimately depends
on shared stages. This removes today's over-drop where one variant's failure
deletes sibling variants, and it matters for the multi-variant PVC sweep.

### Success views (two milestones)

`v_projects_successes` currently requires a `COLLECT_PIT_DATA_GENERALIZED` row,
which hard-requires reduction. Split into two views.
- `v_projects_generalized` — completed through `FILTER_GENERALIZATIONS`. Answers
  how many generalizations succeeded.
- `v_projects_reduced` — the current predicate, keyed on PIT rows. Answers how
  many projects have reduction metrics.

`v_projects_successes` keeps its meaning as an alias of `v_projects_reduced`, so
the roughly eighteen downstream analysis and view usages are unaffected.
Applicability reporting reads `v_projects_generalized`.

## Config surface

Add `teralizer.project.use-test-reduction` and the DDL column
`use_test_reduction BOOLEAN NOT NULL`. Reduction defaults on, preserving today's
end-to-end behavior. `use_test_generalization` keeps its study-membership meaning,
so no analysis query is rewritten. Identity comparison hashes the rendered
configuration excluding the three phase toggles, since those are run-scoped
rather than identity-defining.

## Analysis-layer impact

Analysis consumers key off stage names and row presence and are order
independent, so deferring reduction cannot change their results. The only
structural change is the success-view split, designed to be transparent to
existing consumers.

## Acceptance criteria

- Three independent toggles gate the three phases. Any subset is legal to request.
- Requesting a phase clears its prior DB rows and artifacts, then re-executes it.
- A reduction-only invocation against a project whose generalization artifacts
  exist on disk runs Stage 5 without re-running generalization.
- Reduction requested with no archived generalized sources fails loud, naming the
  missing artifact.
- A reduction-phase task failure leaves all generalization results intact, and a
  variant-scoped failure drops only that variant.
- Multi-variant reduction is isolated: each variant's PIT and JaCoCo run against
  that variant's own restored sources and rebuilt classes, never a sibling's.
- Preserved per-stage `.exec` files yield distinct, non-accumulated INITIAL and
  GENERALIZED coverage.
- `v_projects_generalized` and `v_projects_reduced` exist. `v_projects_successes`
  retains current semantics. Existing analyses produce unchanged numbers on a
  full end-to-end run.
- Attach resolves by `root_path`. A materially different configuration on the
  same path fails loud.
- The fixture batch gate stays green. A new reduction-resume fixture proves
  running generalization then reduction in separate invocations.
- Existing end-to-end configs behave identically, since reduction defaults on.

## Out of scope

- The PIT-at-scale timeout and cap decision (for example math's multi-hour
  mutation pass). This change makes reduction isolable and resumable, which is
  the enabler. Cap sizing is a separate operational call.
- Telemetry for compile-based quarantine and exclusion counts in the funnel and
  report. Tracked as a separate follow-up commit.

## Testing

- Unit tests for `PipelinePlanner` covering phase selection, clear-then-run,
  precondition fail-loud, and sequential draining, authored via the Tester agent.
- Unit tests for the variant-aware cascade-drop.
- The fixture batch gate as the integration oracle, plus a reduction-resume
  fixture exercising the two-invocation flow and a multi-variant reduction check
  proving per-variant source-and-target isolation.
