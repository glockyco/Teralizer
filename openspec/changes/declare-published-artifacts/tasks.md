Edits are scoped to this repository. The consuming repository's own declaration is recorded in group 6
and written there, not here.

## 1. Narrow the figure capability

- [x] 1.1 Retire the outcome in the figure spec stating that tables and data are published when no
      declaration exists. Done at source before that change was archived, so the contradiction never
      entered the accepted spec set (`1487dae3`).
- [ ] 1.2 When this change is archived and its specs merge, narrow the Purpose of
      `reporting/figure-publication` to the formats a figure is emitted in. A requirement delta cannot
      restate a Purpose, so the removal of its four delivery requirements would otherwise leave a
      Purpose describing behaviour the capability no longer governs.
      Verification: the accepted figure spec lists exactly one requirement, and its Purpose names no
      declaration and no delivery.

## 2. Declare artifacts per render target

- [ ] 2.1 Read a declaration section per render target, per design.md Decision 1. An existing
      `[figures]` section keeps its current meaning and its current behaviour.
      Verification: a declaration carrying only `[figures]` behaves exactly as it does today.
- [ ] 2.2 Fail on a section that names no known render target, naming the section, so a typo is not a
      silently empty declaration.
      Verification: an unknown section fails and names itself.
- [ ] 2.3 Resolve every declared path against the consuming repository's root and refuse one that
      escapes it, for every kind.
      Verification: an escaping path fails for a table as it already does for a figure.
- [ ] 2.4 Tests: a multi-kind declaration, an unknown section, a non-string value, an escaping path,
      and a figures-only declaration that must keep working.
- [ ] 2.5 Commit.
      Message: `feat(eval): declare published artifacts per render target`

## 3. Accumulate per kind and deliver once

- [ ] 3.1 Stop copying inside the render step. Have it report what it emitted, per kind, as name to
      path, and remove the two unconditional copy loops.
      Verification: a run without a publish destination writes nothing outside the build tree.
- [ ] 3.2 Accumulate across reports per kind, and fail on a duplicate name within a kind, naming the
      artifact and the reports that claim it. A name shared between a table and its data file is not a
      collision, per design.md Decision 3.
      Verification: a table and a CSV of the same name publish together; two reports emitting one table
      name fail.
- [ ] 3.3 Deliver once, after the last report, resolving the declaration against everything the run
      emitted.
      Verification: an artifact only the final report emits is delivered.
- [ ] 3.4 Confirm a run that fails before finishing leaves the consuming repository unchanged.
      Verification: a failure injected after the first report leaves the consumer untouched.
- [ ] 3.5 Tests for 3.2, 3.3, and 3.4.
- [ ] 3.6 Commit.
      Message: `fix(eval): deliver published artifacts once per run`

## 4. Guard every delivered path

- [ ] 4.1 Take the union of declared paths across kinds and ask the consuming repository once, refusing
      before the first write, per design.md Decision 4.
      Verification: an uncommitted change to a declared table refuses the publish and leaves every file
      untouched.
- [ ] 4.2 Confirm publishing never removes a file, including one an earlier publish deposited.
      Verification: an undeclared file in a delivery directory survives a publish unchanged.
- [ ] 4.3 Tests for both directions of 4.1 and for 4.2.
- [ ] 4.4 Commit.
      Message: `fix(eval): guard every delivered path against consumer edits`

## 5. Fail before building when a declared kind is not requested

- [ ] 5.1 Generalise the argument check that today considers only a figure declaration, so a run fails
      before any report is built when the invocation omits a target the destination declares.
      Verification: a destination declaring figures and CSV files fails an invocation that omits CSV,
      and the failure names the missing target.
- [ ] 5.2 Confirm an invocation covering every declared kind proceeds.
- [ ] 5.3 Tests for 5.1 and 5.2.
- [ ] 5.4 Commit.
      Message: `fix(eval): check the invocation against every declared kind`

## 6. Record what the consuming repository must declare

- [ ] 6.1 Record the declaration the thesis must carry at `chapters/05-teralizer/publish.toml`: the
      LaTeX artifacts it prints, the CSV files it prints, and the figures it already declares. Derive
      each set rather than restating a count: the LaTeX set is the tracked files under
      `chapters/05-teralizer/tables` that a report emits, which excludes the hand-authored table that
      no report produces; the CSV set is the tracked files under `chapters/05-teralizer/data`.
      Verification: the record names how each set is derived, and is filed against the thesis change
      that owns the regeneration procedure.
- [ ] 6.2 Record the completeness check from design.md Risks: the delivered set and the consuming
      repository's tracked generated files must agree once, when the declaration is written, because a
      missing entry cannot fail on its own.
      Verification: the check is stated as a step the consuming repository performs.
- [ ] 6.3 Commit.
      Message: `docs(openspec): record the consumer declaration the thesis must carry`

## 7. Verification

- [ ] 7.1 Run the eval suite, the linter, the formatter check, and the type check.
      Expected: all pass.
- [ ] 7.2 Publish into a scratch clone of the consuming repository and confirm the delivered set is
      exactly the declared set.
      Expected: no undeclared file is written, and every declared artifact lands at its declared path.
- [ ] 7.3 Confirm figure delivery is unchanged by comparing delivered figures against the set the
      previous behaviour produced.
      Expected: identical set, identical paths.
- [ ] 7.4 Commit any residue.
      Message: `chore(eval): record the delivery verification results`
