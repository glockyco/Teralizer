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

## 3. Regenerate Publication Artifacts

- [x] 3.1 Run the registered RQ6 report against its declared pinned corpus and inputs.
- [x] 3.2 Inspect generated CSV, TeX, macro, manifest, and provenance diffs; confirm that the taxonomy removal causes every changed artifact.
- [ ] 3.3 Provide the regenerated project-exclusion table and producing revision to the thesis change `restore-rq6-narrative`.

## 4. Verify and Commit

- [ ] 4.1 Run `uv run --directory analysis pytest`.
- [ ] 4.2 Run `uv run --directory analysis ruff check .` and `uv run --directory analysis ty check .`.
- [ ] 4.3 Run `lefthook run pre-commit --all-files` and `openspec validate simplify-rq6-project-causes --strict`.
- [ ] 4.4 Stage only the report change, inspect the staged diff, and commit the verified producer cutover with a causal body.