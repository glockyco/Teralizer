## 1. Lock the Evidence Boundary

- [x] 1.1 Define the versioned evidence-manifest schema, logical source roles, digest rules, and compatibility checks.
- [x] 1.2 Add the reconstruction status vocabulary and entity-identity contract.
- [x] 1.3 Add a guard that refuses pipeline, project, task, build, and retry execution from reconstruction commands.
- [x] 1.4 Add focused tests that prove reconstruction cannot invoke Teralizer, corpus runners, Gradle, Maven, JPF, or PIT.
- [x] 1.5 Add validation that current RepoReapers quantities resolve only to `postgres_reporeapers_rq6_v7`.
- [x] 1.6 Add validation that versions 1 through 6 cannot become RepoReapers corpus inputs.

## 2. Inventory the Collected Evidence

- [x] 2.1 Implement read-only inventory collection for the version 7 database export, facts record, project logs, project trees, run roots, configurations, and producer revisions.
- [x] 2.2 Record SHA-256 identities and observed path sets without copying raw evidence into the repository.
- [x] 2.3 Inventory the version 7 database export and verify its `facts.tsv` record.
- [x] 2.4 Prove that the Air log set covers `project-1.log` through `project-1161.log` exactly once.
- [x] 2.5 Inventory preserved RepoReapers project checkouts and compare their revisions with recorded database revisions.
- [x] 2.6 Inventory per-project `command-data`, `teralizer-data`, generated tests, reports, and configuration evidence.
- [x] 2.7 Record every missing, duplicate, incompatible, or undigested source as an inventory issue.
- [x] 2.8 Add synthetic inventory tests for complete, missing, duplicate, changed, and revision-mismatched evidence.

## 3. Build the Reconstruction Record Pipeline

- [x] 3.1 Implement the common claim, population, source, entity-decision, reviewer, and evidence-gap records.
- [x] 3.2 Implement schema-version and closed-vocabulary validation for normalized audit inputs.
- [x] 3.3 Implement reconciliation checks for resolved, unresolved, incompatible, and total populations.
- [x] 3.4 Implement read-only version 7 population extraction through the registered corpus input.
- [x] 3.5 Record the version 7 dump digest, schema identity, and read-only inspection method in derived provenance.
- [x] 3.6 Implement source-review packet generation without compiling or executing project code.
- [x] 3.7 Implement manual decision import, reviewer-state validation, and disagreement preservation.
- [x] 3.8 Add focused tests for invalid joins, inferred exact counts, unresolved decisions, and evidence-gap output.

## 4. Reconstruct `NoAssertions` Evidence

- [x] 4.1 Freeze the version 7 eligible `NO_ASSERTIONS` population and its stable identity digest.
- [x] 4.2 Define labels for genuine absence, reachable helper assertion, unsupported oracle, incompatible source, and unresolved evidence.
- [x] 4.3 Generate source-review packets with the exact test source, reachable collected context, and existing assertion evidence.
- [x] 4.4 Select and record an exhaustive or statistically justified stratified review design before labeling.
- [x] 4.5 Complete manual labels and record source locations, call paths, reviewer state, and rationale.
- [x] 4.6 Resolve reviewer disagreements without forcing ambiguous cases into true-positive or false-positive counts.
- [x] 4.7 Emit the normalized `NoAssertions` audit input with exact counts or a labeled sample estimate and confidence interval.
- [x] 4.8 Verify that resolved plus unresolved plus incompatible entities reconcile to the frozen population.

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

## 7. Integrate Reconstructed Evidence

- [ ] 7.1 Declare each accepted audit file as a versioned report input with validated upstream identities.
- [ ] 7.2 Extend the registered RQ6 report to publish reconstruction status and resolved, unresolved, and incompatible populations.
- [ ] 7.3 Publish exact rates only for complete version 7 classifications with compatible denominators.
- [ ] 7.4 Publish sample estimates with their sampling method and confidence interval instead of exact population counts.
- [ ] 7.5 Publish `contradicted` and `evidence-gap` outcomes without an unsupported replacement number.
- [ ] 7.6 Add provenance checks that prevent report-time access to the Air or remote project state.
- [ ] 7.7 Update retained metric and table inventories to include only reconstructed values that downstream thesis claims retain.

## 8. Verify the Complete Change

- [ ] 8.1 Run focused reconstruction and report tests with synthetic evidence fixtures.
- [ ] 8.2 Run `uv run --directory analysis pytest`.
- [ ] 8.3 Run `uv run --directory analysis ruff check .`.
- [ ] 8.4 Run `uv run --directory analysis ty check .`.
- [ ] 8.5 Verify that the real-world corpus registry still resolves only to `postgres_reporeapers_rq6_v7`.
- [ ] 8.6 Verify that version 7 base tables and the preserved Air evidence remain unchanged.
- [ ] 8.7 Verify that every target claim has one final reconstruction status and reconciled evidence totals.
- [ ] 8.8 Run `lefthook run pre-commit --all-files`.
- [ ] 8.9 Run `openspec validate reconstruct-reporeapers-evidence --strict`.
- [ ] 8.10 Review the final command record and prove that no pipeline, project, task, build, or retry occurred.
