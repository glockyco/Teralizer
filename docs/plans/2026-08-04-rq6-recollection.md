---
title: RQ6 Re-Collection After the Reduction Fixes
type: plan
status: active
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# RQ6 Re-Collection After the Reduction Fixes

Re-collect the full RepoReapers corpus once the reduction-path fixes are in, so the RQ6
figures the thesis cites come from one coherent measurement event on fixed code rather than
from a corpus carrying known harness defects.

A full run takes about 29.5 hours wall clock, serial over 1,161 projects
(`2026-08-04-reduction-failure-anatomy`). Cost is dominated by the projects that already
worked, not by the ones the fixes recover.

**Not a partial re-run.** Re-running only the affected projects into the existing database
would be cheaper but would produce a corpus measured with two code versions, which breaks
the one-run-one-measurement-event discipline and cannot be recorded honestly in the
provenance manifest.

JUnit 3 support is included in the re-collected pipeline
(`archive/2026-08-04-junit3-support-spike`). Two consequences to expect and to check rather than
be surprised by: 9,617 tests move out of `TestType` rejections and into the assertion-level
gates, mostly a missing tested method, so the filter attribution shifts without applicability
changing; and admitting those tests raises per-project analysis cost, so the run will exceed the
previous 29.5 hours. One sampled project alone took about 55 minutes.

Configuration is unchanged: `project-configs/reporeapers-rq6.conf`, the inherited budgets
(original-suite tests 300 s, mutation 3600 s), `pitest.original.enabled = false`, and the
single `IMPROVED_200_TRIES` variant. Resource limits are accepted attrition and are not
tuned to improve outcomes.

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

## Tasks

### Task 1: Launch

- [ ] Launch the fixed code into a fresh `postgres_reporeapers_rq6_v2` database, preserving
      `postgres_reporeapers_rq6` as the previous measurement until the new one is signed off.
      Store data under `data/reporeapers-rerun-v2`, keep the 14400 s project ceiling, and use the
      process name `reporeapers-rq6-v2`. Do not resume the partial pre-fix corpus.

- [ ] Monitor to completion, reading the log rather than polling the database.
  Verification: the final log line reports `attempted=1161`.
  Expected: run completes without a structural halt. A halt is a defect to fix and relaunch,
  not a partial corpus to analyze.

### Task 2: Confirm the fixes landed in the data

- [ ] Check each fixed cause is gone or reduced, using the new reason codes.
  Verification: query reduction-stage `task_diagnostic` rows by `reason_code` on the new database
  Expected: no `MINION_DIED` caused by an argLine token, no `PLUGIN_UNUSABLE` for projects
  pinning old pitest versions, no failure from unparsed jqwik identifiers, and fewer
  `SUITE_NOT_GREEN` than the 6 recorded previously.

- [ ] Confirm the JUnit 3 attribution shift and that applicability did not fall: `TestType`
      rejections drop by roughly 9,617, `MissingValue` rejections rise correspondingly, and
      Stage 1+2 admits at least as many projects as before.
  Verification: compare filter tables between the previous and new database
  Expected: the shift appears at the assertion level, and no project that was applicable becomes
  inapplicable.

- [ ] Compare the funnel against the previous measurement and account for every band that
      moved.
  Verification: `uv run --directory analysis python -m teralizer.eval rq6 --db postgres_reporeapers_rq6_v2 --targets md`
  Expected: applicability is at least the previous 73 of 611; the overall row rises from 42;
  Stages 1+2 and 3 are unchanged within the noise of non-deterministic build failures, and any
  band that moved has a named cause.

### Task 3: Promote the corpus

- [ ] Point `rq6_causes.DEFAULT_DB` and the dataset report's second connection at the new
      database, and regenerate every RQ6 artifact plus the dataset report.
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
