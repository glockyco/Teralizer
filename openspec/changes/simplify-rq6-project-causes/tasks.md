## 1. Remove the Project Type Taxonomy

- [x] 1.1 Capture the current ordered project-exclusion stage, cause, type, and count rows as the migration baseline.
- [x] 1.2 Remove the internal/external/mixed type from the project-cause model, classifier, and row construction in the RQ6 report.
- [x] 1.3 Remove the type column from the project-exclusion table declaration and retain `Cause of Project-level Exclusion` as the cause heading.
- [x] 1.4 Remove type-derived metric identities, validation, and provenance fields without a compatibility alias or placeholder.
- [x] 1.5 Search every report, test, publication declaration, and generated-artifact consumer for the retired taxonomy and migrate each caller.

## 2. Defend the Reduced Contract

- [x] 2.1 Update focused RQ6 report tests to assert the complete stage, cause, and count rows, the count-based within-stage order, and the absence of a type field.
- [x] 2.2 Add a migration assertion that the reduced row set equals the baseline projection and that project funnel reconciliation remains unchanged.
- [x] 2.3 Run the focused report tests that cover project exclusion construction, table rendering, metric identities, and reconciliation.
- [x] 2.4 Replace the zero-included-test fallback with separate filter-only, filter-plus-failure, and no-test-evidence rows.
- [x] 2.5 Preserve missing-report-file and unsupported-report-layout diagnostics as separate project causes.
- [x] 2.6 Add focused tests for the evidence-derived cause splits and unchanged funnel reconciliation.
- [x] 2.7 Search all entity-level exclusion construction for equivalent zero-count or cause-collapsing fallbacks and correct each occurrence.

## 3. Regenerate Publication Artifacts

- [x] 3.1 Run the registered RQ6 report against its declared pinned corpus and inputs.
- [x] 3.2 Inspect generated CSV, TeX, macro, manifest, and provenance diffs; confirm that every change follows from taxonomy removal or an evidence-backed cause correction.
- [x] 3.3 Provide the regenerated project-exclusion table and producing revision to the thesis change `restore-rq6-narrative`.

## 4. Verify and Commit

- [x] 4.1 Run `uv run --directory analysis pytest`.
- [x] 4.2 Run `uv run --directory analysis ruff check .` and `uv run --directory analysis ty check .`.
- [x] 4.3 Run `lefthook run pre-commit --all-files` and `openspec validate simplify-rq6-project-causes --strict`.
- [x] 4.4 Stage only the report change, inspect the staged diff, and commit the verified producer cutover with a causal body.

## 5. Correct Stage Boundaries and Project Buckets

- [x] 5.1 Replace the Stage 1 + 2 survivor test based on final inclusion status with assertion filter-survival evidence.
- [x] 5.2 Use recorded generalization attempts as the Stage 3-to-4 transition and assert that every survivor set is nested.
- [x] 5.3 Split complete specification-extraction loss by filter rejection, task exception, and build-quarantine mechanism sets.
- [x] 5.4 Distinguish instrumented-project compilation, initial-suite execution, missing specifications, and missing generalization attempts.
- [x] 5.5 Split Stage 5 project failures by initial or generalized suite when the task stage identifies the side.
- [x] 5.6 Add focused regression tests for corrected stage bands, complete ordered cause rows, and unchanged final totals.

## 6. Aggregate Reader-facing Project Causes

- [x] 6.1 Aggregate project rows by material reader-facing cause while preserving detailed diagnostics in generated evidence.
- [x] 6.2 Remove internal mechanism names and unapproved shorthand from report output, tests, and thesis prose.
- [x] 6.3 Add focused tests for the aggregate rows, Stage 4 overlap treatment, ordering, and funnel reconciliation.
- [x] 6.4 Add selected evidence-backed Stage 3 examples to the thesis prose with an overlap qualification and replication-package boundary.

## 7. Republish and Verify the Refined Table

- [x] 7.1 Run the registered RQ6 report and inspect every generated artifact change against the reader-facing causes.
- [x] 7.2 Update the thesis table, macros, and cross-chapter project-level narrative from the registered report.
- [x] 7.3 Run the focused and complete analysis checks, repository hooks, strict OpenSpec validation, and the thesis build and page inspection.
- [x] 7.4 Commit the verified producer and thesis changes as separate atomic commits with causal bodies.

## 8. Fit and Pair the Entity-exclusion Tables

- [x] 8.1 Make resize-to-width and full-width stretching mutually exclusive, and render resized tables from their measured natural width without paragraph indentation.
- [x] 8.2 Add focused renderer and RQ6 report tests for the width-strategy invariant, natural-width resize output, and local float placement.
- [x] 8.3 Regenerate the RQ6 artifacts and place Tables 5.16 and 5.17 together at one source boundary before the assertion-level discussion.
- [x] 8.4 Run the complete analysis checks, repository hooks, strict OpenSpec validation, thesis build, and page inspection; commit each repository separately.
- [x] 8.5 Remove setup and trailing whitespace from the measured resize box, target the explicit text width, rebuild Table 5.17, and verify that both rule endpoints align with Table 5.16.

## 9. Include Every Proactive Filter

- [x] 9.1 Replace the implementation-stage filter boundary with a normalized proactive-filter evidence relation while keeping reactive failures excluded.
- [x] 9.2 Add `InheritedTestMethod`, `SeedSpecConsistency`, and `WideningLicense` rows with evidence-derived evaluated populations and verdicts.
- [x] 9.3 Add focused tests for exact filter names, complete row arithmetic, pre-emission ordering, inherited-method evidence, and generalization filtering reconciliation.
- [x] 9.4 Regenerate the registered RQ6 artifacts and update the thesis table and interpretation without changing column terminology.
- [x] 9.5 Run complete analysis checks, repository hooks, strict OpenSpec validation, thesis build, and page inspection; commit each repository separately.

## 10. Group and Rank Filter Decisions

- [x] 10.1 Replace the hand-curated filter-name order with semantic decision groups and descending rejection counts.
- [x] 10.2 Insert a midrule at every group boundary, including between the two test-filter rounds.
- [x] 10.3 Add focused regressions for group membership, rejection-count ordering, and rendered test-round separation.
- [x] 10.4 Regenerate affected reports, update both thesis filter-detail tables, run all quality gates, and commit each repository separately.

## 11. Correct the Filtering Contract

- [x] 11.1 Replace the five-group model with test, assertion, and generalization level groups; insert rules only at level boundaries.
- [x] 11.2 Rename the filter-detail `Total` column to `Evaluated` and sort each level by descending evaluated population, descending rejection count, then ascending filter name.
- [x] 11.3 Rename filtering-outcome symbols, metrics, macros, provenance, generated labels, tests, and reader-facing consumers from retained to included without changing unrelated retention terms.
- [x] 11.4 Align accepted producer and thesis terminology contracts with included and excluded filtering outcomes and per-filter Accept, Defer, and Reject verdicts.

## 12. Register Flow and Rendering Evidence

- [x] 12.1 Materialize provenance-backed test-flow metrics for inherited-method screening, pre-filter failures, both rounds, first-round overlap, and intervening failures.
- [x] 12.2 Add focused reconciliation tests that derive each round population from persisted set evidence instead of summed rejection counts.
- [x] 12.3 Remove the RQ5 summary-table alignment override and assert that the shared renderer centers the `Excluded` spanner.
- [x] 12.4 Add an explicit local compact-density style for the long RQ6 filter-detail table without changing global float behavior or generated TeX.
- [x] 12.5 Add focused shared-renderer and report tests for three level groups, evaluated-population ordering, centered headers, and compact density.

## 13. Republish the Corrected Evidence

- [x] 13.1 Regenerate the registered RQ5 and RQ6 reports from their declared inputs and inspect every artifact and provenance change.
- [x] 13.2 Update thesis Tables 5.13, 5.14, 5.16, and 5.17 from generated artifacts without hand-editing producer output.
- [x] 13.3 Correct both thesis `ExcludedTest` explanations and cite its rows without publishing an unregistered cause breakdown.
- [x] 13.4 Replace retained-as-outcome and proactive-exclusion prose with included, excluded, and filtering while leaving valid retention language unchanged.
- [x] 13.5 Keep the test-flow narrative concise: preserve the two-round explanation and explain inherited-method screening as a separate conditional population.

## 14. Verify the Complete Correction

- [x] 14.1 Run focused filtering, rendering, metric, reconciliation, and RQ5/RQ6 report tests.
- [x] 14.2 Run the complete analysis tests, Ruff, Ty, repository hooks, and strict OpenSpec validation.
- [x] 14.3 Build the thesis and run its style checks, then inspect Tables 5.13 through 5.17 and their page boundaries in the rendered PDF.
- [x] 14.4 Commit the producer and thesis changes as separate atomic commits with causal bodies.

## 15. Reconcile the Reader-facing Semantic Audit

- [x] 15.1 Publish provenance-backed `TestType` declaration-category metrics and prove that they partition the rejection population.
- [x] 15.2 Correct the RQ6 result and discussion descriptions of JUnit theories, overridden inherited declarations, and TestNG tests.
- [x] 15.3 Distinguish assertion recognition, tested-method identification, and assertion-to-MUT mapping; bound the proposed `does not throw` extension.
- [x] 15.4 Scope widening conclusions to the Stage 4 population and remove the invalid attempt-to-validation comparison.
- [x] 15.5 Regenerate producer artifacts, publish the new macros, build the thesis, and inspect the affected RQ6 pages.
- [x] 15.6 Run complete producer and thesis quality gates, then commit each repository separately with causal bodies.

## 16. Restore the Entity-level Source Narrative Conservatively

- [ ] 16.1 Compare the `Test, Assertion, and Generalization Exclusions` results and their later Chapter 5–7 interpretations with the TOSEM source; exclude the RQ6 introduction and project-level results from the mutable scope.
- [ ] 16.2 Inventory registered RQ6 metrics, generated tables, and reconstruction audits; identify genuine accepted-population quantity gaps before adding or rerunning analysis.
- [ ] 16.3 Collect and register any missing accepted-population quantities, then restore supported source wording, paragraph order, and narrative progression without changing the frozen text.
- [ ] 16.4 Remove redundant filter-row references from the immediate table discussion and replace misleading unnumbered-heading references in later text while preserving useful numbered section, table, and row references.
- [ ] 16.5 Build the thesis, inspect every affected RQ6 and later-interpretation page, run thesis quality gates, and commit the revision separately.