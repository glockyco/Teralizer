---
title: Baseline JaCoCo Stage Reassignment
type: spec
status: draft
created: 2026-07-08
parent: 2026-07-08-evaluation-analysis-redesign
---

# Baseline JaCoCo Stage Reassignment

A pipeline data-validity fix: `COLLECT_JACOCO_DATA_ORIGINAL` (baseline coverage of
the original test suite) is scheduled in the generalization phase but is only ever
consumed by the reduction phase, so generation-only runs render baseline coverage
they never use and manufacture spurious project-level failures. This moves the task
to the reduction phase, reclassifies it as Stage 5, and re-collects the RepoReapers
corpus into fresh databases.

## Problem (verified in source)

- `PipelinePhase.GENERALIZATION.schedule` (`src/main/java/teralizer/processing/PipelinePhase.java:97`)
  schedules `JacocoDataCollectionTask(COLLECT_JACOCO_DATA_ORIGINAL)`, and the stage is a
  member of `GENERALIZATION_STAGES` (line 203). The generalization phase is requested
  whenever `project.use_test_generalization` is true.
- The only consumer of that data is `PitDataCollectionTask` at `COLLECT_PIT_DATA_ORIGINAL`
  (`PitDataCollectionTask.java:152`, `PipelineQueries.fetchCoveredClasses(... COLLECT_JACOCO_DATA_ORIGINAL ...)`),
  which selects mutation target classes. `COLLECT_PIT_DATA_ORIGINAL` is a member of
  `REDUCTION_STAGES`, requested only when `project.use_test_reduction` is true.
- Tested-method identification (`TestAnalysisTask`) is pure Spoon static analysis and does
  not read baseline coverage. No stage 1-4 task consumes `COLLECT_JACOCO_DATA_ORIGINAL`.
- The raw coverage `.exec` is produced and preserved at `EXECUTE_TESTS_ORIGINAL`
  (`TestExecutionTask.java:148-160`, copied to `JacocoDataCollectionTask.preservedExecPath(... COLLECT_JACOCO_DATA_ORIGINAL ...)`).
  `COLLECT_JACOCO_DATA_ORIGINAL` only renders that preserved exec into `jacoco_coverage_report`.

Consequence: a generation-only run (`use_test_generalization` true, `use_test_reduction`
false, as in the current `postgres_reporeapers`) executes a reduction-only prerequisite.
When rendering fails it records a project-level failure at `COLLECT_JACOCO_DATA_ORIGINAL`.
The current RQ6 funnel shows 44 such "JaCoCo outputs not found" exclusions at Stage 1+2 that
would not exist if the task ran only when reduction is requested. The data is invalid for a
generation-only evaluation.

`stages.py` and `create-views.sql` map `COLLECT_JACOCO_DATA_ORIGINAL` to paper Stage 1+2
purely by pipeline position (step 10), not by purpose. Its purpose is mutation-target
selection, a Stage 5 concern.

## Fix

Move the render step into the reduction phase and reclassify it, so it runs only when
reduction is requested and is attributed to Stage 5.

### Pipeline (`src/main/java/teralizer/processing/`)

- `PipelinePhase.GENERALIZATION.schedule`: remove the
  `JacocoDataCollectionTask(COLLECT_JACOCO_DATA_ORIGINAL)` line; drop
  `COLLECT_JACOCO_DATA_ORIGINAL` from `GENERALIZATION_STAGES`. Keep `EXECUTE_TESTS_ORIGINAL`
  (it still runs the original suite and preserves the `.exec`).
- `PipelinePhase.REDUCTION.schedule`: before any original collection, schedule a build-state
  reset (see below), then `JacocoDataCollectionTask(COLLECT_JACOCO_DATA_ORIGINAL)`, then
  `COLLECT_PIT_DATA_ORIGINAL` (its consumer). Add `COLLECT_JACOCO_DATA_ORIGINAL` and the new
  reset stage to `REDUCTION_STAGES` and to the reduction phase `clear` deletion set.
- **Build-state (required, not a fallback):** after the generalization phase the last variant's
  `_..._Generalized_` sources remain in the test tree (`archiveGeneralizedSources` copies but
  does not delete; `CLEANUP_GENERALIZATION` runs only at each variant's start). Both
  `jacoco:report` and PIT invoke the build against that tree, so the original render must run on
  a cleaned, rebuilt original tree. Add a new reduction-band `ProcessingStage`
  (`RESTORE_ORIGINAL_BUILD`) and a dedicated `RestoreOriginalBuildTask` that, under that single
  stage, deletes the generalized sources (project-level, all variants) then calls
  `ProjectBuildTask.buildProject(...)` inline, mirroring how `GeneralizedSourceRestoreTask`
  restores-and-builds for a variant. Schedule it first in `REDUCTION.schedule`: one task, one
  stage row. Do NOT reuse `CLEANUP_GENERALIZATION` (Stage-4 stage, wrong phase ownership), and
  do NOT emit a separate `ProjectBuildTask` under `BUILD_PROJECT_ORIGINAL` or a duplicate
  `RESTORE_ORIGINAL_BUILD` row.
- `ProcessingStage`: renumber the whole enum densely (contiguous, no gaps) to reflect the new
  order. Remove `COLLECT_JACOCO_DATA_ORIGINAL` from position 10; insert `RESTORE_ORIGINAL_BUILD`
  then `COLLECT_JACOCO_DATA_ORIGINAL` into the reduction band before `COLLECT_PIT_DATA_ORIGINAL`.
  The reduction band becomes: `FILTER_GENERALIZATIONS`, `RESTORE_ORIGINAL_BUILD`,
  `COLLECT_JACOCO_DATA_ORIGINAL`, `COLLECT_PIT_DATA_ORIGINAL`, `COLLECT_JACOCO_DATA_INITIAL`,
  `COLLECT_PIT_DATA_INITIAL`, `RESTORE_GENERALIZED_BUILD`, `COLLECT_JACOCO_DATA_GENERALIZED`,
  `COLLECT_PIT_DATA_GENERALIZED`. Keep `ProcessingStage`, `stages.py`, and `create-views.sql` in
  lockstep.

### Stage mapping (analysis + SQL)

- `analysis/src/teralizer/stages.py`: move `COLLECT_JACOCO_DATA_ORIGINAL` from `stage_1_2` to
  `stage_5`.
- `src/main/resources/db/create-views.sql`: move `COLLECT_JACOCO_DATA_ORIGINAL` in the stage
  grouping / `stage_order` CASE from the Stage 1+2 group to the Stage 5 group, keeping the
  stage-number lockstep invariant with `ProcessingStage` and `stages.py`.
- `analysis/src/teralizer/eval/reports/_taxonomy.py`: the `COLLECT_JACOCO_DATA_ORIGINAL` rule
  returns Stage `"5"` (not `"1 + 2"`); it reuses the existing JaCoCo cause labels. Update the
  taxonomy test accordingly. In a generation-only corpus the stage no longer runs, so the rule
  is dormant there and active only for full-pipeline corpora.

### Eval consequences

- The funnel eligibility zero-coverage rule (`has_jacoco_artifact & ~has_actual_coverage`) is
  unchanged and correct: with no baseline JaCoCo in a generation-only corpus, no project is
  dropped for zero coverage, so the eligible denominator becomes setup-based only (matching the
  redesign's definition). No further eval change is required beyond the taxonomy stage.

## Verification

1. `./gradlew build` (compiles the pipeline + runs unit tests).
2. `scripts/verify-pipeline.sh` (full fixture corpus, exercises reduction) — the gate proving
   the moved render and PIT target selection still produce the golden coverage/mutation output.
   A regression here means the build-state needs the cleanup step above.
3. Add a fixture reproducing the defect if one does not exist: a generation-only project must
   record no `COLLECT_JACOCO_DATA_ORIGINAL` task at all (the stage is reduction-only).

## Re-collection

The current `postgres_reporeapers` data is invalid for RQ6 and must be re-collected after the
fix. Per the operator directive, collect into FRESH databases so the eval scripts keep working
against the existing data while collection runs:

- New real-world database (e.g. `postgres_reporeapers_v2`) via the RepoReapers rerun runner,
  leaving `postgres_reporeapers` intact for eval-script iteration.
- Re-collect the JARVIS census/scoreboard databases only if the stage move changes their
  coverage-dependent numbers (RQ0); otherwise leave them.
- When collection completes, point RQ6 (`rq6_causes` default db) at the new database, regenerate
  `analysis/reports/rq6.md`, and confirm the funnel no longer contains baseline-JaCoCo Stage-1+2
  exclusions.

Re-collection is a measurement event: it runs once, into new databases, and its first-run
numbers stand.

## Acceptance

- Generation-only runs schedule no `COLLECT_JACOCO_DATA_ORIGINAL` task; the RQ6 funnel has no
  baseline-JaCoCo Stage-1+2 exclusion.
- Full-pipeline (reduction) runs still select PIT targets from baseline coverage and produce the
  fixture-golden mutation/coverage output.
- `stages.py`, `create-views.sql`, and `ProcessingStage` agree on `COLLECT_JACOCO_DATA_ORIGINAL`
  as Stage 5 (stage-number lockstep invariant holds).
- RQ6 regenerated from the fresh database; the eval test suite green.
