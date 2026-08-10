---
title: RepoReapers Dataset Row for the Eligible Corpus
type: plan
status: active
created: 2026-08-04
parent: 2026-07-08-evaluation-analysis-redesign
superseded_by:
archived:
---

# RepoReapers Dataset Row for the Eligible Corpus

Make the RepoReapers rows of the dataset-characteristics report describe the same
corpus RQ6 reports on: the 1,161 projects in the completed v6 run.
Today the report mixes sources — test counts come from the paper-era RepoReapers
database while file, class, and line counts aggregate every checkout in the
statistics CSV, which is why the row cannot be reconciled with either corpus.

The EqBench and Commons rows are frozen. RQ1–RQ5 read those rows, and this plan must
not change their values.

## File map

- Modify `analysis/src/teralizer/eval/reports/dataset_characteristics.py`: owns the report, and opens the second connection that supplies the RepoReapers side.
- Modify `analysis/src/teralizer/dataset_characteristics.py`: owns the statistics CSV load, the per-dataset aggregation, and `_get_test_counts_from_db`.
- Modify `analysis/tests/test_dataset_characteristics.py`: covers the aggregation (create it if the module has no test file yet).

## Tasks

### Task 1: Scope the RepoReapers aggregation to the eligible corpus

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/dataset_characteristics.py`
- Modify: `analysis/src/teralizer/dataset_characteristics.py`
- Test: `analysis/tests/test_dataset_characteristics.py`

- [ ] In `build`, open the second connection on `postgres_reporeapers_rq6_v6` instead of
      `postgres_test`, and in `_get_test_counts_from_db` restrict the RepoReapers query to
      eligible projects, reusing `_funnel.INELIGIBLE_STAGES` rather than restating the stage list.
  Verification: `uv run --directory analysis pytest tests/test_dataset_characteristics.py -k repo_reapers_counts`
  Expected: take the project and test-method values from the regenerated report, not from a hand query.
      The RQ6 basis figures live in `docs/exclusion-model.md`.

- [ ] Restrict the `github_com_` rows of the statistics CSV to those same projects by joining on
      `project.root_path`, never on `id`, and derive the total, mean, and median rows from the
      restricted frame.
  Verification: `uv run --directory analysis pytest tests/test_dataset_characteristics.py -k repo_reapers_rows`
  Expected: take the total row's project count and aggregate values from the regenerated report,
      not from a hand query. The join leaves no unmatched eligible project, and mean times project
      count equals the total for every column. The RQ6 basis figures live in `docs/exclusion-model.md`.

- [ ] Commit.
  Message: `fix(eval): scope RepoReapers dataset rows to the eligible corpus`

### Task 2: Regenerate the dataset report

**Files:**
- Modify: `analysis/reports/dataset.md` (generated, untracked)

- [ ] Regenerate and read the RepoReapers rows.
  Run: `uv run --directory analysis python -m teralizer.eval dataset --targets md`
  Expected: take the RepoReapers total, mean, and median values from the regenerated report,
      not from a hand query. The RQ6 basis figures live in `docs/exclusion-model.md`; EqBench and
      Commons rows are byte-identical to the previous output.

- [ ] Run the gate.
  Run: `uv run --directory analysis pytest tests` then `uv run --directory analysis ruff check .` then `uv run --directory analysis ty check .`
  Expected: all green.

- [ ] Commit.
  Message: `chore(eval): regenerate the dataset report`
