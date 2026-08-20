## 1. Resolve provenance per source file

- [x] 1.1 Add a memoised resolver that returns, for a source file, the last commit that changed it and
      whether it has uncommitted changes.
      Verification: two calls for one file run one `git log`; an unrelated commit does not change the
      answer.
- [x] 1.2 Record `HEAD` and mark dirty when a file has no commit.
      Verification: an uncommitted new module yields `HEAD` and dirty rather than an empty commit.
- [x] 1.3 Derive the repository-relative path from `inspect.getsourcefile`, replacing the path rebuilt
      from the module name.
      Verification: the path matches the file the interpreter loaded, for a module under `analysis/src`
      and for one outside it.
- [x] 1.4 Point `capture` at the resolver, keeping its signature.
      Verification: a captured provenance carries the producing file's last commit, not `HEAD`.
- [x] 1.5 Keep `require_publishable_tree` tree-wide and unchanged.
      Verification: publishing from a dirty tree is still refused, and the override still works.
- [x] 1.6 Tests: per-file resolution, unrelated-commit stability, uncommitted producing file,
      never-committed file, path derivation, and that the publish guard is untouched.
      Run: `uv run --directory analysis python -m pytest tests/eval/test_provenance.py -q`
- [ ] 1.7 Commit.
      Message: `fix(eval): attribute provenance to the code that produced the artifact`

## 2. Resolve figure metadata by the same rule

- [x] 2.1 In `render/figures.py`, take the commit from the file defining the figure's build function
      instead of `HEAD`.
      Verification: a figure's embedded provenance names its report module's last commit.
- [x] 2.2 Tests: the embedded string carries the resolved commit, and is unchanged by an unrelated
      commit.
      Run: `uv run --directory analysis python -m pytest tests/eval/test_render_figures.py -q`
- [ ] 2.3 Commit.
      Message: `fix(eval): resolve figure provenance through the figure's source`

## 3. Verify reproducibility

- [ ] 3.1 Regenerate the full report set twice in succession and confirm the second run leaves no diff.
      Run: the report set with all targets, then `git status --porcelain -- analysis/reports`
      Expected: empty after the second run.
- [ ] 3.2 Confirm publishing twice in succession is not refused for a tree the first publish dirtied.
      Verification: the second publish runs, having committed nothing in between.
- [ ] 3.3 Confirm the rewrite touched only provenance fields.
      Run: `git diff -- analysis/reports`
      Expected: commit, dirty, and source-URL values only; no metric value, table cell, or caption
      differs.
- [ ] 3.4 Confirm each rewritten permalink resolves to the producing lines.
      Verification: spot-check one metric per report module against the file at the recorded commit.
- [ ] 3.5 Measure the added `git log` cost across a full run.
      Verification: recorded; if it is material, the resolver is batched instead.
- [ ] 3.6 Full suite.
      Run: `uv run --directory analysis python -m pytest tests -q`
- [ ] 3.7 Commit the regenerated reports separately from the code change, so the rewrite is reviewable
      on its own.
      Message: `chore(eval): rewrite report provenance with correct commits`

## 4. Hand off

- [ ] 4.1 Record that the regeneration procedure in the thesis repository can now state a single
      publish step, with no instruction to commit churn between runs.
      Verification: the note is filed against that repository's authoring-guidance change.
