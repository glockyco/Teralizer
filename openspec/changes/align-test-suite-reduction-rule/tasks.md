## 1. Establish the View Contract

- [x] 1.1 Locate the focused database-view test owner for `mv_generalization_effects` and record its existing fixture conventions.
- [x] 1.2 Add synthetic rows for a replaceable single-assertion test, a fully represented multi-assertion test, a partially represented multi-assertion test, and a mutation-redundant generalization.
- [x] 1.3 Add assertions that retained generalized tests contribute added cost and each completely represented source test contributes removed cost exactly once.

## 2. Align the Reduction View

- [x] 2.1 Carry source assertion identity through the retained-generalization relation in `src/main/resources/db/create-views.sql`.
- [x] 2.2 Derive one replaceable-source-test row only when distinct retained source assertions equal the source test's nonzero assertion count.
- [x] 2.3 Aggregate removed test count, source lines, and runtime from replaceable source-test rows while preserving the view schema and added-generalization metrics.

## 3. Verify Report Compatibility

- [x] 3.1 Run the focused database-view test and confirm all single-, complete-multi-, partial-multi-, and redundant-generalization cases.
- [x] 3.2 Query the finalized controlled database read-only to compare current and corrected removal identities and RQ3 measures.
- [x] 3.3 Run `nix develop --command ./gradlew build`.
- [ ] 3.4 Run `nix develop --command scripts/verify-pipeline.sh` once for the completed change wave.
- [ ] 3.5 Run `nix develop --command lefthook run pre-commit --all-files`.
