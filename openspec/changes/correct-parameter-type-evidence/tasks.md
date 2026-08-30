## 1. Establish the Declaration-dependent Filter Contract

- [x] 1.1 Change `ParameterTypeFilter` to accept one assertion-scoped `MutResolutionObservationRecord` and recipe-derived capability evidence; verify the API exposes no unrestricted observation lookup and preserves the `ParameterTypeFilter` producer identity.
- [x] 1.2 Update `TestFilteringTask` to obtain the canonical observation by assertion identity and pass the recipe-derived `tested_method_parameters` evidence; verify duplicate observations fail as persistence errors instead of being selected arbitrarily.
- [x] 1.3 Preserve all-or-nothing mapper declaration telemetry for a picked `CtMethod` (`candidate_param_count`, `candidate_param_supported`, and ordered `resolved_parameter_types`); verify no-declaration outcomes leave every declaration-dependent field null.
- [x] 1.4 Implement declaration-first decisions: defer on a missing observation (`MUT_RESOLUTION_NOT_RECORDED`) or incomplete declaration or recipe evidence (`MISSING_TESTED_PARAMS`), accept when a complete declaration has a supported derived input, and reject otherwise; verify exact decisions, reason codes, and `MISSING_MUT` dependency values.
- [x] 1.5 Remove the call-argument empty-list decision branch and keep `NO_GENERALIZABLE_PARAMETERS`, `MISSING_TESTED_PARAMS`, `MUT_RESOLUTION_NOT_RECORDED`, and `MISSING_MUT` identities unchanged; verify no compatibility alias or renamed filter is introduced.

## 2. Add Focused Producer and Lifted-input Coverage

- [x] 2.1 Extend focused filter tests for no observation, missing declaration, incomplete declaration tuples, null recipe evidence, supported derived inputs, and no supported derived inputs; verify each case produces the prescribed `Accept`, `Defer`, or `Reject` result and stable reason/dependency fields.
- [x] 2.2 Extend mapper tests for a missing selected declaration and a picked declaration with zero, supported, and unsupported formal parameters; verify count, support, and ordered type fields agree or are all null.
- [x] 2.3 Add a zero-formal-parameter receiver-constructor lift case and an argument-constructor lift case; verify the recipe is accepted because it contains supported generalizable input and the lift is not counted as a formal method parameter.
- [x] 2.4 Add an unsupported-formal-parameter case with a supported receiver or constructor lift; verify the recipe is accepted without using `candidate_param_supported` as the recipe decision.
- [x] 2.5 Add a supported-formal-parameter case with no supported derived recipe input, including skipped or unsupported derivation sites; verify the recipe is rejected with `NO_GENERALIZABLE_PARAMETERS`.
- [x] 2.6 Use the assertion identity for the caller lookup and jOOQ `fetchOne` cardinality enforcement; verify the focused filtering tests compile through the updated caller and duplicate observations cannot be selected arbitrarily.

## 3. Reconstruct Historical ParameterType Evidence

- [x] 3.1 Replace list-based `PARAMETER_TYPE_REJECTION_SQL` with a read-only relation that starts from the exact eligible persisted `ParameterType` rejection set and joins each assertion to one canonical resolver observation; verify the join checks assertion, project, and test identities without mutating the database.
- [x] 3.2 Implement the four historical categories: `unsupported_types`, `zero_parameters`, `missing_declaration`, and `derivation_gap`; verify no historical rejection is assigned to `no_arguments`, `unknown`, or another catch-all category.
- [x] 3.3 Classify `zero_parameters` only from a complete declaration with count zero, and classify `unsupported_types` only from a complete positive count with false support; verify recipe-list emptiness never supplies either formal-signature claim.
- [x] 3.4 Classify `missing_declaration` from an existing observation with absent declaration-tuple evidence, while retaining status and no-pick reason as provenance only; verify a successful resolution status cannot turn null declaration telemetry into unsupported parameters.
- [x] 3.5 Compute `derivation_gap` as the residual category for a complete positive-count declaration with supported input capability; verify recipe evidence does not populate formal parameter count or create a fifth category.
- [x] 3.6 Preserve accepted historical lifts and immutable persisted rejection verdicts during reconstruction; verify receiver and constructor inputs affect recipe capability only and no assertion, filter-result, or observation row is rewritten.

## 4. Enforce Exact Partitions and Evidence Invariants

- [x] 4.1 Validate the complete `ParameterType` verdict partition (`Accept`, `Defer`, `Reject`) for every filter population; verify each entity occurs exactly once and the verdict total equals the evaluated population.
- [x] 4.2 Validate the four-category historical rejection partition, including declared zero-count category rows; verify category predicates are mutually exclusive and exhaustive over persisted `ParameterType` rejections.
- [x] 4.3 Validate canonical observation cardinality and identity; verify a missing or duplicate observation raises an evidence error that names the assertion instead of selecting a fallback row.
- [x] 4.4 Validate declaration telemetry invariants; verify counts are non-negative, ordered JSON type arrays have matching cardinality, support is non-null for complete declarations, and partial null tuples cannot enter a semantic category.
- [x] 4.5 Keep recipe-capability evidence independent from declaration classification; verify absent historical recipe evidence remains unavailable provenance, while malformed present evidence cannot balance totals by inference.
- [x] 4.6 Reject contradictory declaration count, support flag, and resolved-type evidence in the registered query; verify report materialization raises an evidence error rather than assigning an existing category, while status and no-pick reason remain non-authoritative provenance.
- [x] 4.7 Reconcile category counts with distinct persisted `ParameterType` rejections and the canonical filter-detail `Reject` count; verify overlapping filter decisions contribute once to outcome totals while remaining visible in per-filter rows.

## 5. Register and Regenerate Report Artifacts

- [x] 5.1 Update registered report metrics and metric keys for `zero_parameters`, `unsupported_types`, `missing_declaration`, and `derivation_gap`; verify old `no_arguments` identities are removed without a compatibility alias and all shares use the persisted rejection denominator.
- [x] 5.2 Update filter-detail and historical-category table declarations, labels, ordering, and zero-count rows; verify `Evaluated = Accept + Defer + Reject`, level grouping remains unchanged, and category totals reconcile before rendering.
- [x] 5.3 Record one read-only consistent snapshot and source-observation provenance for the registered report; verify the citable metrics, tables, and collapsed outcomes use the same snapshot and eligible corpus inputs.
- [ ] 5.4 Regenerate registered CSV, TeX, Markdown, manifest, macro, and provenance artifacts from the corrected report; verify every changed value and label follows from the four-category calculation and no database or corpus files change.
- [ ] 5.5 Inspect generated artifact diffs and report metadata; verify the filter name remains `ParameterType`, the category labels contain exactly four historical causes, and no ad hoc query output is used as publication output.

## 6. Update Thesis Literals and Discussion

- [ ] 6.1 Replace ParameterType-specific `no_arguments` and `unknown` literals in thesis source, tables, macros, and supporting report consumers with `zero_parameters`, `unsupported_types`, `missing_declaration`, and `derivation_gap`; verify no stale category literal remains in the scoped thesis material.
- [ ] 6.2 Replace the related thesis discussion with evidence-based distinctions between formal selected-MUT declarations and recipe-derived generalizable inputs; verify the prose does not treat call-argument or derived-list emptiness as signature evidence.
- [ ] 6.3 Explain missing declaration evidence and derivation gaps without changing historical rejection totals or implying a corpus rerun; verify each reported claim cites the registered metric, table, or provenance artifact.
- [ ] 6.4 Regenerate affected thesis tables and macros from registered report artifacts; verify the rendered values and labels match the report outputs and no hand-edited publication value remains.

## 7. Apply the Atomic Source-and-report Cutover

- [ ] 7.1 Integrate the producer, mapper, registered-report SQL, validations, metrics, generated artifacts, and thesis updates as one evidence-contract revision; verify the source and report paths cannot be deployed with different declaration semantics.
- [ ] 7.2 Verify the migration is read-only against historical databases; verify no corpus rerun, project rerun, synthetic observation, synthetic filter result, or database mutation is present in the implementation or report procedure.
- [ ] 7.3 Verify rollback reverts the complete source-and-report revision before new measurements; verify rollback leaves all historical rows and generated evidence untouched and does not restore the obsolete interpretation as a corrected result.

## 8. Run Focused and Full Repository Gates

- [ ] 8.1 Run focused Java tests for `ParameterTypeFilter`, filtering telemetry, lifted inputs, mapper evidence, and caller observation cardinality; verify the decision matrix and declaration invariants pass.
- [ ] 8.2 Run focused Python report tests for canonical joins, four-category calculations, exact partitions, provenance, metric registration, table rendering, and macro generation; verify malformed and contradictory evidence fails closed.
- [ ] 8.3 Run the full Java repository test and build gates plus the full analysis test, Ruff, and Ty gates; verify all repository implementation checks pass without running a corpus or changing a database.
- [ ] 8.4 Run repository hooks and the thesis build and style gates, then inspect affected rendered pages; verify generated report values, thesis literals, and discussion remain consistent with the registered read-only report.
- [ ] 8.5 Perform a final change-scope audit; verify only intended source, tests, registered report code, generated artifacts, thesis consumers, and this task plan are changed, with no corpus rerun or database mutation evidence.
