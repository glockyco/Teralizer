## 1. Emit a print format

- [x] 1.1 In `analysis/src/teralizer/eval/render/figures.py`, save each drawn figure as PDF as well as
      PNG. The PNG keeps its current path, suffix, dpi, and metadata. The PDF goes to
      `analysis/build/figures/<rq>/<key>.pdf`, omits the explicit dpi so the paper style's 300
      applies, and keeps the tight bounding box.
      Verification: a report run with the figure target leaves both files for every declared figure.
- [x] 1.2 Carry provenance into the PDF using a key its information dictionary defines. Keep the PNG's
      `Comment`. Leave `Creator` to matplotlib.
      Verification: the run emits no unknown-keyword warning, and the PDF's metadata holds the report
      id and commit.
- [x] 1.3 Update the module docstring, which states the narrowed PNG-only intent.
      Verification: the docstring names both formats and what each is for.
- [x] 1.4 Return both paths from `materialize`, so a caller can tell which artifact is publishable.
      Verification: callers of `materialize` handle the changed return without a type error.
- [x] 1.5 Tests: both formats written, PDF at the build path, provenance present in each, no warning
      raised.
      Run: `uv run --directory analysis python -m pytest tests/eval -q`
- [ ] 1.6 Commit.
      Message: `fix(eval): emit a print format for every figure`

## 2. Resolve the consumer declaration

- [ ] 2.1 Read `publish.toml` from the publish destination with `tomllib`, taking `[figures]` as a
      figure key to consumer-relative path mapping. A destination with no such file declares no
      figures.
      Verification: a destination without the file publishes tables and data and no figure.
- [ ] 2.2 Resolve the consumer's root with `git -C <paper-out> rev-parse --show-toplevel`. Resolve each
      declared path against it.
      Verification: a declared path resolves to an absolute path under that root.
- [ ] 2.3 Fail when the root cannot be resolved, naming the destination.
      Verification: publishing into a directory outside a git repository fails with that reason.
- [ ] 2.4 Tests: a well-formed declaration, an absent file, a malformed file, and an unresolvable root.
      Run: `uv run --directory analysis python -m pytest tests/eval -q`
- [ ] 2.5 Commit.
      Message: `feat(eval): read the consumer's figure declaration`

## 3. Validate before delivering

- [ ] 3.1 Collect every figure key the run emits, then check each declared key against that set.
      Report every declared key that nothing emits, not only the first.
      Verification: a declaration naming two absent keys reports both.
- [ ] 3.2 Check that each resolved path stays inside the consumer's root, and report every path that
      does not.
      Verification: a declaration escaping the root fails naming that path.
- [ ] 3.3 Perform both checks before copying any figure, and copy nothing when either fails.
      Verification: after a failed publish the consumer's figure paths are unchanged.
- [ ] 3.4 Do not treat an emitted figure that no consumer declares as an error.
      Verification: publishing with `evosuite_runtime_phases` undeclared succeeds and does not deliver
      it.
- [ ] 3.5 Tests: missing key, escaping path, multiple simultaneous failures, no-copy-on-failure, and
      the undeclared-figure case.
      Run: `uv run --directory analysis python -m pytest tests/eval -q`
- [ ] 3.6 Commit.
      Message: `feat(eval): stop a publish whose figure declaration disagrees`

## 4. Deliver, under the existing guards

- [ ] 4.1 Copy each declared figure's PDF from the build tree to its resolved path, creating parent
      directories.
      Verification: a publish run leaves each declared file at its declared path.
- [ ] 4.2 Extend the consumer-side guard in `scripts/publish-analysis.sh` to cover the paths the
      declaration resolves to, computed from the declaration rather than hardcoded.
      Verification: an uncommitted change to a declared figure path refuses the publish.
- [ ] 4.3 Confirm the generator's clean-tree requirement already covers this path and add nothing.
      Verification: publishing from a dirty generator tree is refused before any figure is written,
      and the documented override permits it.
- [ ] 4.4 Commit.
      Message: `feat(eval): publish figures to the paths a consumer declares`

## 5. Verification against the thesis

- [ ] 5.1 Regenerate the full report set and confirm all five figures exist in both formats.
      Run: the publish command from `.omp/rules/generated-artifacts.md` once that rule is corrected,
      or `publish-analysis.sh` directly with a scratch destination.
- [ ] 5.2 Compare each of the four thesis figures against the copy committed in the thesis, by content
      rather than by bytes. A rendering difference is expected and acceptable. A difference in a
      plotted value is a stop: these four draw from the frozen controlled corpus, so the data must
      match.
      Verification: recorded per figure, with the verdict and what was compared.
- [ ] 5.3 Confirm `evosuite_runtime_phases` was emitted and not delivered.
- [ ] 5.4 Publish into a scratch clone of the thesis with a declaration, and confirm the four files
      land at their declared paths with no other file touched.
      Verification: `git status` in the scratch clone shows exactly four modified figure paths, plus
      the tables and data the publish also delivers.
- [ ] 5.5 Confirm no report content changed.
      Run: `git diff --stat -- analysis/build analysis/reports`
      Expected: figure additions only; no table, CSV, macro, or Markdown value differs.
- [ ] 5.6 Full test suite.
      Run: `uv run --directory analysis python -m pytest tests -q`
      Expected: pass.
- [ ] 5.7 Commit any fix this verification required.

## 6. Hand off

- [ ] 6.1 Record what the thesis repository must now do: add `publish.toml` at
      `chapters/05-teralizer/`, declaring the four figures at their current paths, and stop treating
      `figures/teralizer-*` as hand-imported assets.
      Verification: the note is filed against the thesis repository's authoring-guidance change, which
      owns the procedure text.
- [ ] 6.2 Record whether the paper repository should gain a declaration, per design.md Open Questions.
      Verification: the answer is recorded, or the question is closed as not applicable.
