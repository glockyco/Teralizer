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

- [x] 5.1 Freeze the version 7 assertion-resolution population from `mut_resolution_observation` and compatible filter evidence.
- [x] 5.2 Define “insufficient specification evidence” before inspecting weak or unresolved mappings.
- [x] 5.3 Preserve status, confidence tier, no-pick reason, source provenance, and filter outcome for every entity.
- [x] 5.4 Generate review packets for T3, T4, `NO_VISIBLE_CALL`, `UNRESOLVED_SOURCE_DECLARATION`, and ambiguous shapes.
- [x] 5.5 Complete manual review of the declared strata and preserve unresolved decisions.
- [x] 5.6 Emit the normalized assertion-to-MUT audit input with mechanism, confidence, and review partitions.
- [x] 5.7 Reconcile audit totals with version 7 observations and eligible report populations.

## 6. Reconstruct Output-Directory Evidence

- [x] 6.1 Freeze the version 7 project population whose failures can involve test, coverage, or mutation output discovery.
- [x] 6.2 Join each project to its preserved command logs, run root, configuration, build files, and recorded output paths.
- [x] 6.3 Define labels for default-directory mismatch, absent artifact, earlier build failure, unsupported layout, incompatible evidence, and unresolved evidence.
- [x] 6.4 Inspect whether each required artifact existed at failure time without running its project or build.
- [x] 6.5 Record the searched path, observed artifact path, failure stage, source identity, and rationale for each decision.
- [x] 6.6 Emit the normalized output-directory audit input and reconcile it with the frozen project population.

## 7. Integrate Reconstructed Evidence

- [x] 7.1 Declare each accepted audit file as a versioned report input with validated upstream identities.
- [x] 7.2 Extend the registered RQ6 report to publish reconstruction status and resolved, unresolved, and incompatible populations.
- [x] 7.3 Publish exact rates only for complete version 7 classifications with compatible denominators.
- [x] 7.4 Publish sample estimates with their sampling method and confidence interval instead of exact population counts.
- [x] 7.5 Publish `contradicted` and `evidence-gap` outcomes without an unsupported replacement number.
- [x] 7.6 Add provenance checks that prevent report-time access to the Air or remote project state.
- [x] 7.7 Update retained metric and table inventories to include only reconstructed values that downstream thesis claims retain.

## 8. Verify the Complete Change

- [x] 8.1 Run focused reconstruction and report tests with synthetic evidence fixtures.
- [x] 8.2 Run `uv run --directory analysis pytest`.
- [x] 8.3 Run `uv run --directory analysis ruff check .`.
- [x] 8.4 Run `uv run --directory analysis ty check .`.
- [x] 8.5 Verify that the real-world corpus registry still resolves only to `postgres_reporeapers_rq6_v7`.
- [x] 8.6 Verify that version 7 base tables and the preserved Air evidence remain unchanged.
- [x] 8.7 Verify that every target claim has one final reconstruction status and reconciled evidence totals.
- [x] 8.8 Run `lefthook run pre-commit --all-files`.
- [x] 8.9 Run `openspec validate reconstruct-reporeapers-evidence --strict`.
- [x] 8.10 Review the final command record and prove that no pipeline, project, task, build, or retry occurred.

## 9. Publish Thesis-Facing Reconstruction Metrics

- [x] 9.1 Add structured estimate fields to the normalized `NoAssertions` audit summary. Record the
      genuine-absence estimate, lower confidence bound, upper confidence bound, estimator, and
      confidence method without parsing the prose reason field.
- [x] 9.2 Extend the registered RQ6 report with structured metrics for the `NoAssertions` estimate and
      confidence bounds, reviewed assertion-to-MUT outcomes, and complete output-discovery outcomes.
- [x] 9.3 Add every new quantity to the retained metric inventory and verify that the existing aggregate
      LaTeX renderer emits stable macros. Do not add a new report, thesis-only export, CSV, or TeX table.
- [x] 9.4 Add focused tests for exact metric keys, value kinds, values, provenance, estimate semantics,
      outcome partitions, and generated macro names. Prove that changing the audit reason text does not
      change the metrics.
- [x] 9.5 Run the focused RQ6 and reconstruction report tests. Then run the complete analysis test, Ruff,
      and ty checks, regenerate all registered reports and provenance, and run repository pre-commit
      hooks.
- [x] 9.6 Commit the report implementation and audit-schema update as one coherent unit. Commit the
      regenerated report artifacts as a separate coherent unit. Use `personal_commit` for both.
      Verification: implementation commit `d1740e87306f876a22a865db51bf5c9fd298af6d`; generated-report
      commit `e9cf56d5646cca4782c1e52c544dc0cfadd1c837`; 65 focused tests and 560 complete analysis
      tests pass; Ruff, ty, and repository pre-commit hooks pass. Complete report generation used
      `md,figures,latex,csv`; provenance records the clean implementation revision.
- [x] 9.7 Validate this change and both delta specs with strict OpenSpec validation. Sync the delta specs
      into `openspec/specs/`, archive the change, and confirm strict validation after the archive.
