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

Configuration is unchanged: `project-configs/reporeapers-rq6.conf`, the inherited budgets
(original-suite tests 300 s, mutation 3600 s), `pitest.original.enabled = false`, and the
single `IMPROVED_200_TRIES` variant. Resource limits are accepted attrition and are not
tuned to improve outcomes.

## Preconditions

- [ ] `2026-08-04-reduction-path-fixes` is implemented and its gate passes.
- [ ] `2026-08-04-pre-rerun-investigations` is closed, and any fix it surfaced is either
      implemented or explicitly deferred with a reason.
- [ ] `./gradlew build` green on the commit that will be launched, and the commit recorded
      here by hash once known.

## Tasks

### Task 1: Launch

- [ ] Launch detached into a fresh database, preserving `postgres_reporeapers_rq6` as the
      previous measurement until the new one is signed off.
  Run: `REPOREAPERS_PROFILE=project-configs/reporeapers-rq6.conf REPOREAPERS_DB=postgres_reporeapers_rq6_v2 REPOREAPERS_PROJECT_TIMEOUT=14400 bash scripts/detached-run.sh start reporeapers-rq6-v2 -- caffeinate -i bash scripts/run-reporeapers-rerun.sh --reset-db`
  Expected: `data/detached/reporeapers-rq6-v2.meta` records the start; the log opens with the
  database reset.

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
