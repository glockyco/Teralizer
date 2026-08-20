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
- [x] 1.7 Commit. `9e4c70f7`
      Message: `fix(eval): attribute provenance to the code that produced the artifact`

## 2. Resolve figure metadata by the same rule

- [x] 2.1 In `render/figures.py`, take the commit from the file defining the figure's build function
      instead of `HEAD`.
      Verification: a figure's embedded provenance names its report module's last commit.
- [x] 2.2 Tests: the embedded string carries the resolved commit, and is unchanged by an unrelated
      commit.
      Run: `uv run --directory analysis python -m pytest tests/eval/test_render_figures.py -q`
- [x] 2.3 Commit. `031cb2bd`
      Message: `fix(eval): resolve figure provenance through the figure's source`

## 3. Verify reproducibility

- [x] 3.1 Regenerated the full set with all four targets twice. The second run left zero dirty files
      in `analysis/reports` and zero in the whole tree.
- [x] 3.2 A full publish now leaves the generator tree with zero dirty files, so the generator's
      clean-tree guard no longer blocks the next publish. The consumer-side guard still refuses a
      second publish over the files the first one delivered, which is its purpose and is unrelated.
- [x] 3.3 Classified every changed line rather than sampling: 962 changed lines, 962 of them
      provenance, 0 other. No metric value, table cell, caption, or figure geometry differs.
- [x] 3.4 Checked all 227 provenance entries rather than one per module: every recorded commit and
      path resolves with `git show`, and every recorded line number lands on a definition. 11 distinct
      commit and path pairs, zero failures.
- [x] 3.5 11 distinct files resolve in 175 ms, about 16 ms each, and 220 cached lookups take 0.03 ms.
      That is 0.6 percent of a 27 second run, so the resolver is not batched.
- [x] 3.6 Full suite: 327 passed, 1 xfailed.
      Run: `uv run --directory analysis python -m pytest tests -q`
- [x] 3.7 Committed separately as `chore(eval): rewrite report provenance with correct commits`.

## 4. Hand off

- [x] 4.1 Record that the regeneration procedure in the thesis repository can now state a single
      publish step, with no instruction to commit churn between runs.
      Verification: the note is filed against that repository's authoring-guidance change.
