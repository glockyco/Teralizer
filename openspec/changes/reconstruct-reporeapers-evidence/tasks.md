## 1. Lock the Evidence Boundary

- [ ] 1.1 Define the versioned evidence-manifest schema, logical source roles, digest rules, and compatibility checks.
- [ ] 1.2 Add the reconstruction status vocabulary and entity-identity contract.
- [ ] 1.3 Add a guard that refuses pipeline, project, task, build, and retry execution from reconstruction commands.
- [ ] 1.4 Add focused tests that prove reconstruction cannot invoke Teralizer, corpus runners, Gradle, Maven, JPF, or PIT.
- [ ] 1.5 Add validation that current RepoReapers quantities resolve only to `postgres_reporeapers_rq6_v7`.
- [ ] 1.6 Add validation that historical databases cannot become corpus aliases or version 7 population inputs.

## 2. Inventory the Collected Evidence

- [ ] 2.1 Implement read-only inventory collection for database exports, facts records, project logs, project trees, run roots, configurations, and producer revisions.
- [ ] 2.2 Record SHA-256 identities and observed path sets without copying raw evidence into the repository.
- [ ] 2.3 Inventory all eight preserved RepoReapers database generations and verify each available `facts.tsv` record.
- [ ] 2.4 Prove that the Air log set covers `project-1.txt` through `project-1161.txt` exactly once.
- [ ] 2.5 Inventory preserved RepoReapers project checkouts and compare their revisions with recorded database revisions.
- [ ] 2.6 Inventory per-project `command-data`, `teralizer-data`, generated tests, reports, and configuration evidence.
- [ ] 2.7 Record every missing, duplicate, incompatible, or undigested source as an inventory issue.
- [ ] 2.8 Add synthetic inventory tests for complete, missing, duplicate, changed, and revision-mismatched evidence.

## 3. Build the Reconstruction Record Pipeline

- [ ] 3.1 Implement the common claim, population, source, entity-decision, reviewer, and evidence-gap records.
- [ ] 3.2 Implement schema-version and closed-vocabulary validation for normalized audit inputs.
- [ ] 3.3 Implement reconciliation checks for resolved, unresolved, incompatible, and total populations.
- [ ] 3.4 Implement read-only version 7 population extraction through the registered corpus input.
- [ ] 3.5 Implement historical dump inspection through isolated scratch restores without report registration.
- [ ] 3.6 Record dump digests, schema identities, and read-only inspection methods in derived provenance.
- [ ] 3.7 Implement source-review packet generation without compiling or executing project code.
- [ ] 3.8 Implement manual decision import, reviewer-state validation, and disagreement preservation.
- [ ] 3.9 Add focused tests for invalid joins, inferred exact counts, unresolved decisions, and evidence-gap output.

## 4. Reconstruct `NoAssertions` Evidence

- [ ] 4.1 Freeze the version 7 eligible `NO_ASSERTIONS` population and its stable identity digest.
- [ ] 4.2 Define labels for genuine absence, reachable helper assertion, unsupported oracle, incompatible source, and unresolved evidence.
- [ ] 4.3 Generate source-review packets with the exact test source, reachable collected context, and existing assertion evidence.
- [ ] 4.4 Select and record an exhaustive or statistically justified stratified review design before labeling.
- [ ] 4.5 Complete manual labels and record source locations, call paths, reviewer state, and rationale.
- [ ] 4.6 Resolve reviewer disagreements without forcing ambiguous cases into true-positive or false-positive counts.
- [ ] 4.7 Emit the normalized `NoAssertions` audit input with exact counts or a labeled sample estimate and confidence interval.
- [ ] 4.8 Verify that resolved plus unresolved plus incompatible entities reconcile to the frozen population.

## 5. Reconstruct Assertion-to-MUT Evidence

- [ ] 5.1 Freeze the version 7 assertion-resolution population from `mut_resolution_observation` and compatible filter evidence.
- [ ] 5.2 Define “insufficient specification evidence” before inspecting weak or unresolved mappings.
- [ ] 5.3 Preserve status, confidence tier, no-pick reason, source provenance, and filter outcome for every entity.
- [ ] 5.4 Generate review packets for T3, T4, `NO_VISIBLE_CALL`, `UNRESOLVED_SOURCE_DECLARATION`, and ambiguous shapes.
- [ ] 5.5 Complete manual review of the declared strata and preserve unresolved decisions.
- [ ] 5.6 Emit the normalized assertion-to-MUT audit input with mechanism, confidence, and review partitions.
- [ ] 5.7 Reconcile audit totals with version 7 observations and eligible report populations.

## 6. Reconstruct Output-Directory Evidence

- [ ] 6.1 Freeze the version 7 project population whose failures can involve test, coverage, or mutation output discovery.
- [ ] 6.2 Join each project to its preserved command logs, run root, configuration, build files, and recorded output paths.
- [ ] 6.3 Define labels for default-directory mismatch, absent artifact, earlier build failure, unsupported layout, incompatible evidence, and unresolved evidence.
- [ ] 6.4 Inspect whether each required artifact existed at failure time without running its project or build.
- [ ] 6.5 Record the searched path, observed artifact path, failure stage, source identity, and rationale for each decision.
- [ ] 6.6 Emit the normalized output-directory audit input and reconcile it with the frozen project population.

## 7. Recover the Historical Doubled-Timeout Evidence

- [ ] 7.1 Identify the historical producer revision, timeout configuration, database, and logs behind the 89-project baseline claim.
- [ ] 7.2 Recover the exact 89 project roots, project revisions, failure stages, and baseline outcomes from collected evidence.
- [ ] 7.3 Search collected configurations, databases, logs, and run artifacts for the claimed doubled-timeout execution.
- [ ] 7.4 Require matching producer, project revision, timeout setting, and paired outcome before accepting a project transition.
- [ ] 7.5 Reconcile the claimed 2 recovered projects, all other transitions, and the 89-project denominator if the pair exists.
- [ ] 7.6 Emit a historical audit input with `supported`, `partially-supported`, or `contradicted` status when evidence permits.
- [ ] 7.7 Emit an `evidence-gap` with all checked sources when collected evidence cannot prove the pair.
- [ ] 7.8 Confirm that no timeout, project, task, or pipeline execution occurred during recovery.

## 8. Integrate Reconstructed Evidence

- [ ] 8.1 Declare each accepted audit file as a versioned report input with validated upstream identities.
- [ ] 8.2 Extend the registered RQ6 report to publish reconstruction status and resolved, unresolved, and incompatible populations.
- [ ] 8.3 Publish exact rates only for complete version 7 classifications with compatible denominators.
- [ ] 8.4 Publish sample estimates with their sampling method and confidence interval instead of exact population counts.
- [ ] 8.5 Publish `contradicted` and `evidence-gap` outcomes without an unsupported replacement number.
- [ ] 8.6 Preserve historical timeout evidence as a bounded historical result separate from version 7 quantities.
- [ ] 8.7 Add provenance checks that prevent report-time access to the Air, historical dumps, or scratch databases.
- [ ] 8.8 Update retained metric and table inventories to include only reconstructed values that downstream thesis claims retain.

## 9. Verify the Complete Change

- [ ] 9.1 Run focused reconstruction and report tests with synthetic evidence fixtures.
- [ ] 9.2 Run `uv run --directory analysis pytest`.
- [ ] 9.3 Run `uv run --directory analysis ruff check .`.
- [ ] 9.4 Run `uv run --directory analysis ty check .`.
- [ ] 9.5 Verify that the real-world corpus registry still resolves only to `postgres_reporeapers_rq6_v7`.
- [ ] 9.6 Verify that version 7 base tables and the preserved Air evidence remain unchanged.
- [ ] 9.7 Verify that every target claim has one final reconstruction status and reconciled evidence totals.
- [ ] 9.8 Run `lefthook run pre-commit --all-files`.
- [ ] 9.9 Run `openspec validate reconstruct-reporeapers-evidence --strict`.
- [ ] 9.10 Review the final command record and prove that no pipeline, project, task, build, or timeout rerun occurred.
