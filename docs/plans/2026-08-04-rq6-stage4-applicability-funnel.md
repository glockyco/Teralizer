---
title: RQ6 Stage-4 Applicability Funnel
type: plan
status: active
created: 2026-08-04
parent: 2026-07-08-evaluation-analysis-redesign
superseded_by:
archived:
---

# RQ6 Stage-4 Applicability Funnel

Report real-world applicability as projects that obtain at least one validated
generalized test, ending the funnel at Stage 4 and reporting the generalization
level on the same signal. Evidence for the change: `2026-08-04-oracle-refusal-taxonomy`.

Stage-5 reduction exclusions leave the funnel because 28 of their 31 projects fail
while measuring the *original* suite with PIT or JaCoCo, so they describe mutation
tooling applicability rather than generalization. The run keeps collecting them; the
report stops presenting them as applicability.

RQ5 is frozen: `rq5_causes.py`, its SQL, and `postgres_dev` are out of scope.

## File map

- Modify `analysis/src/teralizer/eval/reports/_funnel.py`: owns eligibility, survivorship bands, cause attribution, and the funnel table.
- Modify `analysis/src/teralizer/eval/reports/_taxonomy.py`: owns internal-stage to reported-stage mapping and cause classification.
- Modify `analysis/src/teralizer/eval/reports/rq6_causes.py`: owns the RQ6 breakdown and filtering SQL, the report sections, and the metric set.
- Modify `analysis/tests/eval/test_funnel.py`: covers band arithmetic, success signal, and cause coverage.
- Modify `analysis/tests/eval/test_rq6_causes.py`: covers RQ6 report structure and metrics.
- Modify `docs/plans/2026-07-08-autonomous-eval-findings.md`: carries the superseded consumption decision.
- Modify `docs/plans/2026-07-07-evaluation-run-map.md`: names which corpus feeds RQ6.

Not touched: `analysis/src/teralizer/eval/reports/rq5_causes.py`, `_causes_common.py`
presentation helpers, and every RQ0–RQ4 report.

## Tasks

### Task 1: End the survivorship funnel at Stage 4

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/_funnel.py`
- Modify: `analysis/src/teralizer/eval/reports/_taxonomy.py`
- Test: `analysis/tests/eval/test_funnel.py`

- [x] Set `success_count` to the number of Stage-4 survivors rather than
      `len(survivor_sets[-1])`, and restrict the reported bands to `("1 + 2", "3", "4")` so
      the funnel table carries no Stage-5 rows. Keep `_survivor_sets` computing the
      `final_usable` set, keep the `_STAGE_5` taxonomy mapping, and keep classifying
      reduction exclusions — Task 3 reports them as metrics.
  Verification: `uv run --directory analysis pytest tests/eval/test_funnel.py`
  Expected: band arithmetic tests pass; `success_count == 73`, `eligible == 611`, and three
  bands are reported.

- [x] Add a test asserting the funnel table's stage column contains no `5` and that every
      Stage-4-excluded project still resolves to a coded cause.
  Verification: `uv run --directory analysis pytest tests/eval/test_funnel.py -k "stage_bands or uncoded"`
  Expected: no `5` in the table; no project left `UNCODED`. A Stage-4 exclusion whose earliest
  failure is a reduction stage is a finding to escalate with the project id, not a reason to
  widen a catch-all.

- [x] Update the band note built in `build_funnel` so the closing sentence reads as
      applicability rather than pipeline completion, and assert the wording in the test.
  Verification: `uv run --directory analysis pytest tests/eval/test_funnel.py`
  Expected: note ends with `73 of 611 projects produce at least one validated generalized test (11.9%).`

- [x] Commit.
  Message: `refactor(eval): end the RQ6 funnel at Stage 4`

### Task 2: Report the generalization level on the validated signal

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

- [x] In `BREAKDOWN_SQL`, change the `generalization_counts` bucket so `included` is
      `l.generated_filter_passed` instead of `l.final_usable`. Keep the `filtering` bucket
      as `ORACLE_NOT_WIDENABLE`, `INPUT_SPEC_NOT_SATISFIED_BY_SEED`, or a `REJECT`
      `filter_result`; everything else stays `failures`.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py`
  Expected: generalization row totals 4,121 with 1,061 included (25.7%), 3,019 filtering,
  41 failures, and the three buckets sum to the total.

- [x] Add a test asserting the generalization row's failure count is bounded by the
      generalized build and execution failures, so a future switch back to a
      reduction-dependent signal fails loudly.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k generalization_failures`
  Expected: fails before the SQL change (failures 592), passes after (41).

- [x] Commit.
  Message: `fix(eval): count validated generalizations in the RQ6 breakdown`

### Task 3: Emit the applicability metrics

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

- [x] Replace the `realworld.overall_inclusion_pct` metric with
      `realworld.applicability_projects` (73) and `realworld.applicability_pct` (0.119),
      and add `realworld.assertions_total` (135,628), `realworld.assertions_included` (4,528),
      `realworld.assertions_included_pct` (0.033), `realworld.generalization_attempts` (4,121),
      `realworld.generalizations_validated` (1,061), and
      `realworld.generalization_validated_pct` (0.257). Keep `realworld.eligible_projects`.

- [x] Add the reduction-attrition metrics the chapter cites to justify ending the funnel at
      Stage 4: `realworld.reduction_entering_projects` (73),
      `realworld.reduction_excluded_projects` (31), and
      `realworld.reduction_excluded_baseline_side` (28), the last counting projects whose
      earliest reduction failure is at `COLLECT_PIT_DATA_INITIAL` or
      `COLLECT_JACOCO_DATA_INITIAL`. These are metrics only; they must not enter the funnel
      table.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k reduction_attrition`
  Expected: the three metrics resolve to 73, 31, and 28, and
  `realworld.reduction_excluded_baseline_side` is strictly less than
  `realworld.reduction_excluded_projects`.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k metrics`
  Expected: every listed key resolves, percentages are fractions in `[0, 1]`, and
  `realworld.overall_inclusion_pct` is absent.

- [x] Commit.
  Message: `feat(eval): expose RQ6 applicability metrics`

### Task 4: Regenerate the report and the macro file

**Files:**
- Modify: `analysis/reports/rq6.md` (generated, untracked)
- Modify: `analysis/reports/provenance.json` (generated, untracked)

- [ ] Regenerate markdown and provenance.
  Run: `uv run --directory analysis python -m teralizer.eval rq6 --targets md`
  Expected: three stage bands, overall row 73 of 611 (11.9%), generalization row
  1,061 of 4,121 included, and no Stage-5 cause rows.

- [ ] Generate the LaTeX macro file for the thesis to consume.
  Run: `uv run --directory analysis python -m teralizer.eval rq6 --targets latex`
  Expected: `analysis/build/macros.tex` contains `\newcommand{\TzRealworldEligibleProjects}{611}`,
  `\TzRealworldApplicabilityProjects`, `\TzRealworldApplicabilityPct`, and the assertion and
  generalization keys from Task 3. The file is regenerated per invocation, so do not run
  another report before copying it.

- [ ] Run the full gate.
  Run: `uv run --directory analysis pytest tests/eval` then `uv run --directory analysis ruff check .` then `uv run --directory analysis ty check .`
  Expected: all green.

### Task 5: Refresh the superseded consumption decision

**Files:**
- Modify: `docs/plans/2026-07-08-autonomous-eval-findings.md`
- Modify: `docs/plans/2026-07-07-evaluation-run-map.md`

- [ ] Rewrite the "Consumption decision" paragraph in `2026-07-08-autonomous-eval-findings.md`
      to state the current truth: the thesis consumes `postgres_reporeapers_rq6`, reports
      applicability at Stage 4, and leaves RQ1–RQ5 on `postgres_dev` unchanged. Delete the
      claim that the thesis keeps the 632-project, 11-complete figures — do not annotate it
      as superseded.
  Verification: `grep -rn "632" docs/plans/2026-07-08-autonomous-eval-findings.md docs/plans/2026-07-07-evaluation-run-map.md`
  Expected: no hit presents 632 as the thesis figure.

- [ ] Check the plan tree.
  Run: `omp-plans index && omp-plans check`
  Expected: `check` exits zero.

- [ ] Commit.
  Message: `docs(plans): record the Stage-4 applicability consumption decision`
