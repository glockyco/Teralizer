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

## 3. Validate and deliver one run artifact set

- [ ] 3.1 Depend on `make-report-runs-explicit` for renderer return types, target-plus-key identity,
      collision detection, output containment, complete-run staging, manifest assembly, and generator
      promotion; add no nested emitted map or second report-run orchestrator.
- [ ] 3.2 Validate a destination declaration directly against the supplied `ArtifactSet` before
      generator promotion, without re-merging artifacts or repeating same-target collision checks.
      Verification: a table and CSV sharing a key validate together, and a missing declared artifact
      fails with its target and key.
- [ ] 3.3 Deliver the validated declared subset once, after generator promotion succeeds.
      Verification: an artifact owned by the final report is delivered, while an undeclared artifact
      remains only in generator output.
- [ ] 3.4 Confirm a report, render, manifest, or declaration failure leaves the consuming repository
      unchanged.
      Verification: failure injection at every pre-delivery boundary writes no consumer path.
- [ ] 3.5 Tests for 3.2, 3.3, and 3.4 using `ArtifactSet` fixtures.
- [ ] 3.6 Commit.
      Message: `fix(eval): deliver the validated run artifact set`

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

- [ ] 5.1 Generalise declaration inspection so it returns every render target the destination requires;
      the `make-report-runs-explicit` preflight uses that result to fail before any report is built when
      an invocation omits a declared target.
      Verification: a destination declaring figures and CSV files fails an invocation that omits CSV,
      and the failure names the missing target.
- [ ] 5.2 Confirm an invocation covering every declared kind proceeds.
- [ ] 5.3 Tests for 5.1 and 5.2.
- [ ] 5.4 Commit.
      Message: `fix(eval): check the invocation against every declared kind`

## 6. Record what the consuming repository must declare

- [ ] 6.1 Record the declaration the thesis must carry at `chapters/05-teralizer/publish.toml` from an
      explicit inventory, not directory membership. Include each generated LaTeX file the document
      inputs, including the aggregate macro file, each figure the document includes, and only those CSV
      files the thesis deliberately retains as reviewable evidence. Exclude hand-authored tables and
      generator outputs with no declared consumer purpose.
      Verification: every entry names its document input or evidence-retention reason, and the record is
      filed against the thesis change that owns the regeneration procedure.
- [ ] 6.2 Record the completeness audit from design.md Risks: compare the declaration with the thesis's
      tracked generated files and with one reviewed full-run manifest. Every tracked generated file is
      either declared or classified as obsolete and removed by the thesis change, and every declaration
      key is emitted by that run.
      Verification: the audit is a repeatable consumer step and does not infer intent from a directory
      or filename extension.
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
