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
- [x] 1.6 Commit. `ed2e8fc1`
      Message: `fix(eval): emit a print format for every figure`

## 2. Resolve the consumer declaration

- [x] 2.1 Read `publish.toml` from the publish destination with `tomllib`, taking `[figures]` as a
      figure key to consumer-relative path mapping. A destination with no such file declares no
      figures.
      Verification: a destination without the file publishes tables and data and no figure.
- [x] 2.2 Resolve the consumer's root with `git -C <paper-out> rev-parse --show-toplevel`. Resolve each
      declared path against it.
      Verification: a declared path resolves to an absolute path under that root.
- [x] 2.3 Fail when the root cannot be resolved, naming the destination.
      Verification: publishing into a directory outside a git repository fails with that reason.
- [x] 2.4 Tests: a well-formed declaration, an absent file, a malformed file, and an unresolvable root.
      Run: `uv run --directory analysis python -m pytest tests/eval -q`
- [x] 2.5 Commit. Groups 2, 3 and 4 landed as one commit, `bc602f76`: the declaration
      parser, the delivery, and the consumer-side guard are one subject, and splitting them would have
      committed a parser no caller used.
      Message: `feat(eval): read the consumer's figure declaration`

## 3. Validate before delivering

- [x] 3.1 Collect every figure key the run emits, then check each declared key against that set.
      Report every declared key that nothing emits, not only the first.
      Verification: a declaration naming two absent keys reports both.
- [x] 3.2 Check that each resolved path stays inside the consumer's root, and report every path that
      does not. Moved into the declaration's construction: containment is a static property, so this
      now fails before a report run rather than after one. See design.md Decision 5's sibling note in
      the module docstring.
      Verification: a declaration escaping the root fails naming that path, and cannot be constructed
      at all.
- [x] 3.3 Perform both checks before copying any figure, and copy nothing when either fails.
      Verification: after a failed publish the consumer's figure paths are unchanged.
- [x] 3.4 Do not treat an emitted figure that no consumer declares as an error.
      Verification: publishing with `evosuite_runtime_phases` undeclared succeeds and does not deliver
      it.
- [x] 3.5 Tests: missing key, escaping path, multiple simultaneous failures, no-copy-on-failure, and
      the undeclared-figure case.
      Run: `uv run --directory analysis python -m pytest tests/eval -q`
- [x] 3.6 Commit. See 2.5.
      Message: `feat(eval): stop a publish whose figure declaration disagrees`

## 4. Deliver, under the existing guards

- [x] 4.1 Copy each declared figure's PDF from the build tree to its resolved path, creating parent
      directories.
      Verification: a publish run leaves each declared file at its declared path.
- [x] 4.2 Guard the declared figure paths against uncommitted consumer changes. Implemented in the
      delivery code rather than in `scripts/publish-analysis.sh` as this task first said: the shell
      layer would have to parse TOML to learn those paths, or hold a second copy of them, and a guard
      maintained apart from the paths it protects drifts into reporting success in the case it exists
      to catch. See design.md Decision 5.
      Verification: an uncommitted change to a declared figure path refuses the publish and leaves the
      file untouched; a committed one is overwritten.
- [x] 4.3 Confirm the generator's clean-tree requirement already covers this path and add nothing.
      Verification: publishing from a dirty generator tree is refused before any figure is written,
      and the documented override permits it.
- [x] 4.4 Commit. See 2.5.
      Message: `feat(eval): publish figures to the paths a consumer declares`

## 5. Verification against the thesis

- [x] 5.1 Confirmed against the local evaluation databases, which hold `postgres_dev` and
      `postgres_reporeapers_rq6_v7` natively on this machine. RQ1, RQ3, and RQ4 emit all five figures
      in both formats.
- [x] 5.2 Compared by rasterising both PDFs at equal width and differencing pixels.
      `mutation_detection_comparison` and `test_runtime_differences`: identical, empty difference box.
      `teralizer_efficiency`: no pixel differs beyond anti-aliasing. `teralizer_runtimes`: bars,
      markers, axes and dividers identical, so the data is identical; its value labels sit about one
      native pixel lower, which is a text-metric difference present before this change and reduced by
      task 5.8.
      Verification: no plotted value differs on any of the four.
- [x] 5.3 Confirm `evosuite_runtime_phases` was emitted and not delivered.
      Verification: emitted to the build tree by RQ4; absent from every consumer declaration, so
      `deliver` never writes it.
- [x] 5.4 Publish into a scratch clone of the thesis with a declaration, and confirm the four files
      land at their declared paths with no other file touched.
      Verification: `git status` in the scratch clone shows exactly four modified figure paths, plus
      the tables and data the publish also delivers.
- [x] 5.5 Confirm no report content changed. Four PNGs differed only by the embedded provenance
      commit and were restored; only the runtime figure changed in pixels, explained by the divider fix.
      Run: `git diff --stat -- analysis/build analysis/reports`
      Expected: figure additions only; no table, CSV, macro, or Markdown value differs.
- [x] 5.6 Full test suite. 185 passed, 1 xfailed, with ruff and ty clean.
      Run: `uv run --directory analysis python -m pytest tests -q`
      Expected: pass.
- [x] 5.7 Commit any fix this verification required.

- [x] 5.8 Fix the stage-band divider in the runtime figure. It was drawn midway between adjacent group
      centres, which is a boundary only when both groups are the same width. Stage 3 carries one
      variant against Stage 4's seven, so the line landed at 1.58 inside Stage 4's span of 1.20 to
      3.60 and drew BASELINE on the Stage 3 side. Now drawn in the gap between the groups, at 1.05,
      which is where the committed figure has it.
      Verification: a regression test asserts every Stage 4 marker lies right of the Stage 3 boundary,
      and it fails on the old formula with `1.35 > 1.575`.

## 6. Hand off

- [x] 6.1 Record what the thesis repository must now do: add `publish.toml` at
      `chapters/05-teralizer/`, declaring the four figures at their current paths, and stop treating
      `figures/teralizer-*` as hand-imported assets.
      Verification: the note is filed against the thesis repository's authoring-guidance change, which
      owns the procedure text.
- [x] 6.2 Record whether the paper repository should gain a declaration, per design.md Open Questions.
      Answered no, and closed rather than deferred: `projects/**` are pinned submodules the thesis must
      not edit, and the paper's figures are its record at its own commit.
      Verification: the answer is recorded, or the question is closed as not applicable.
