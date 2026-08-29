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