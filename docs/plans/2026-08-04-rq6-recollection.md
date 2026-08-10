---
title: RQ6 Re-Collection After the Reduction Fixes
type: plan
status: complete
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# RQ6 Re-Collection After the Reduction Fixes

The full RepoReapers corpus was re-collected after the reduction-path fixes, so the RQ6
figures the thesis cites come from one coherent measurement event on fixed code rather than
from a corpus carrying known harness defects.

The run completed at 1,161 of 1,161 projects into `postgres_reporeapers_rq6_v6`. Exclusion semantics and counts live in [docs/exclusion-model.md](../exclusion-model.md).

**Not a partial re-run.** Re-running only the affected projects into the existing database
would be cheaper but would produce a corpus measured with two code versions, which breaks
the one-run-one-measurement-event discipline and cannot be recorded honestly in the
provenance manifest.

Configuration is unchanged: `project-configs/reporeapers-rq6.conf`, the inherited budgets
(original-suite tests 300 s, mutation 3600 s), `pitest.original.enabled = false`, and the
single `IMPROVED_200_TRIES` variant. Resource limits are accepted attrition and are not
tuned to improve outcomes.

Measurement basis: commit `b28ef100` (`feat(eval): enforce the resolver telemetry
invariant`), whose tree includes the reduction fixes, telemetry corrections,
project-owned plugin configuration merge, runner-annotation removal, runtime mocking
diagnostics, the method-scoped mocking filter, JUnit 3 assertion analysis with
observation-before-semantics persistence, and resolver-attributed `MissingValue`
rejects. The telemetry invariant (`realworld.assertions_without_resolution` = 0) is
enforced by `test_rq6_every_assertion_carries_resolver_telemetry`.

## Preconditions

- [x] `2026-08-04-reduction-path-fixes` is implemented and its gate passes.
- [x] `2026-08-04-pre-rerun-investigations` is closed; its remaining question was answered by
      the live coverage check, which confirmed the plugin floors recover the family.
- [x] `./gradlew build` green before the first launch.
- [x] PIT collection overrides project-owned output, line-coverage, verbosity, and macOS focus
      settings so every compatible plugin produces bounded XML telemetry.
- [x] Property-managed JaCoCo, PIT, and Surefire pins resolve before the version floors are
      applied.
- [x] Generalized jqwik classes do not retain JUnit 4 runner annotations from their source class.
- [x] JPF failures caused by executing a mocking framework receive a distinct diagnostic code.
- [x] Test methods with a direct mocking-framework dependency are rejected before JPF. The
      method-scoped filter accepts an unused framework import and unrelated methods in the same
      class rather than excluding the entire class.

## Tasks

### Task 1: Launch

- [x] Launch the fixed code into a fresh `postgres_reporeapers_rq6_v6` database, preserving
      the superseded `postgres_reporeapers_rq6` and `postgres_reporeapers_rq6_v2` databases
      until the new measurement is signed off. Store data under `data/reporeapers-rerun-v6`,
      keep the 14400 s project ceiling, and use the process name `reporeapers-rq6-v6`. Do not
      resume any partial pre-fix corpus: v2 was stopped at 1,037 of 1,161 projects because its
      assertions lack resolver telemetry for the JUnit 3 population and its tail mixes code
      versions.

- [x] Monitor to completion, reading the log rather than polling the database.
  Verification: the final log line reports `attempted=1161`.

### Task 2: Confirm the fixes landed in the data

- [ ] Check each fixed cause is gone or reduced, using the new reason codes.
  Verification: query reduction-stage `task_diagnostic` rows by `reason_code` on the new database
  Expected: no `MINION_DIED` caused by an argLine token, no `PLUGIN_UNUSABLE` for projects
  pinning old pitest versions, no failure from unparsed jqwik identifiers, and fewer
  `SUITE_NOT_GREEN` than the 6 recorded previously.

- [ ] Confirm the telemetry invariant holds at corpus scale.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py::test_rq6_every_assertion_carries_resolver_telemetry -q`, and the assertion and `mut_resolution_observation` row counts are equal.
  Expected: the metric reads zero; no assertion row lacks an observation.

- [ ] Confirm the JUnit 3 attribution shift and that applicability did not fall: `TestType`
      rejections move to the assertion-level gates, `MissingValue` rejections rise correspondingly
      and decompose into the four resolver reasons, and Stage 1+2 admits at least as many projects
      as before.
  Verification: compare filter tables between the superseded and new database; group
  `MissingValue` rejects by `reason_code`
  Expected: the shift appears at the assertion level; the primary reasons are
  `LIBRARY_DECLARATION`, `UNSUPPORTED_ASSERTION_SHAPE`, `NO_VISIBLE_CALL`, and
  `UNRESOLVED_SOURCE_DECLARATION` with zero `RESOLUTION_NOT_RECORDED` and no
  `MISSING_TESTED_FILE` majority; and no project that was applicable becomes inapplicable.

- [ ] Compare the funnel against the previous measurement and account for every band that
      moved.
  Verification: `uv run --directory analysis python -m teralizer.eval rq6 --db postgres_reporeapers_rq6_v6 --targets md`
  Expected: compare each band with the superseded corpus, account for any movement with a named
  cause, and record the observed values in the regenerated report.

### Task 3: Promote the corpus

- [ ] Regenerate every RQ6 artifact plus the dataset report against the new database.
      `rq6_causes.DEFAULT_DB` already names `postgres_reporeapers_rq6_v6`; the dataset
      report's second connection still needs pointing at it.
  Verification: `uv run --directory analysis pytest tests/eval` then `uv run --directory analysis python -m teralizer.eval rq6 --targets md,latex` then `uv run --directory analysis python -m teralizer.eval dataset --targets md`
  Expected: tests green against the new database; `reports/rq6.md`, `reports/dataset.md`,
  `reports/provenance.json`, and `analysis/build/macros.tex` all regenerated.

- [ ] Record the promotion in `2026-07-07-evaluation-run-map` and
      `2026-07-08-autonomous-eval-findings`, and state which database is canonical.
  Verification: `omp-plans check`
  Expected: exits zero, and no document still presents the superseded corpus as canonical.

- [ ] Commit.
  Message: `chore(eval): promote the re-collected RepoReapers corpus`

### Task 4: Hand the numbers to the thesis

- [ ] Refresh the figures in the thesis repository's planning documents from the regenerated
      report, so the chapter work proceeds against final values: the corpus size, the
      applicability figures, the assertion and generalization levels, the refusal taxonomy, and
      the reduction attrition split.
  Verification: in the thesis repository, `omp-plans check`
  Expected: `2026-08-04-rq6-results-framing` and `2026-08-04-oracle-refusal-taxonomy`'s
  thesis-facing counterparts cite the new values, and no plan still carries a
  pre-recollection figure.

- [ ] Commit.
  Message: `docs(plans): refresh RQ6 figures from the re-collected corpus`
