## 1. Audit and approve the cross-schema mapping

- [ ] 1.1 Resolve the exact controlled and real-world source revisions, report input snapshots, required
      schema objects, variant identities, and project eligibility predicates from registered provenance.
      Do not use the current checkout as a substitute for the revision that produced either database.
- [ ] 1.2 Trace every controlled-revision writer that initializes, clears, or interprets
      `generalization.is_included`. Record which filters, task failures, and later stages can change it
      and which lifecycle boundary it can prove without inference.
- [ ] 1.3 Run read-only controlled-corpus audits that reconcile the retained `Improved (200 tries)`
      generalization rows with filter decisions, task outcomes, exclusion information, and the RQ5
      included/filtering/failure table partition. Record evidence that is absent from the legacy schema.
- [ ] 1.4 Map the real-world side from the accepted eligibility, generalization-lifecycle, and exclusion
      relations. Enumerate the attempted, emitted, filter-passed, validated, reduced, and final-usable
      candidates and prove which predicate, if any, matches the controlled lifecycle boundary.
- [ ] 1.5 Create the complete mapping matrix specified by the design. Classify each side and the combined
      comparison as exact, qualified, or unmappable; record denominator and project-population
      differences and the strongest interpretation the evidence permits.
- [ ] 1.6 Inspect the typed metric and provenance schema to determine whether the approved mapping identity
      and qualification survive manifest and macro publication. Propose the smallest typed metadata
      extension if the existing model cannot preserve them.
- [ ] 1.7 Present the source evidence, mapping matrix, qualifications, all plausible alternatives, and
      every unmappable case to the operator. Do not edit report code until the operator approves one
      normalized measure, its interpretation bound, and any required metadata extension.

## 2. Implement only the approved translation

- [ ] 2.1 Add the approved mapping identity and qualification to the existing typed metric/provenance
      model only if task 1.7 approved that extension. Do not store the qualification only in prose.
- [ ] 2.2 Implement separate read-only controlled and real-world adapters that emit the same typed
      normalized observation contract from their native schema shapes. Reject missing, duplicate,
      contradictory, or inferred source states.
- [ ] 2.3 Make one registered comparison owner declare both corpus inputs and invoke both adapters in one
      build. Keep existing RQ5 and RQ6 table queries and presentation unchanged unless task 1.7 approved
      a separately evidenced correction.
- [ ] 2.4 Emit numerator, denominator, and share metrics for each corpus with stable keys named after the
      approved lifecycle boundary, `Generalization` population metadata, operand relations, source
      provenance, mapping identity, and qualification.
- [ ] 2.5 Validate all metric relations and mapping invariants before report return. Publish no partial
      comparison when either side fails.

## 3. Defend mapping and publication behavior

- [ ] 3.1 Add focused adapter tests for exact source-to-normalized mappings, denominator conservation,
      variant selection, eligibility, and source identity at both schema shapes.
- [ ] 3.2 Add negative fixtures for every ambiguity found in task 1: competing lifecycle states, absent
      legacy evidence, duplicate identities, contradictory verdicts, and unmapped source states. Each
      must fail with the corpus and mapping identity rather than choose a fallback.
- [ ] 3.3 Prove the generated metric triplets agree with their adapters and, only where semantically
      equivalent, the existing RQ5 or RQ6 table row. Current count equality alone is not an assertion of
      mapping correctness.
- [ ] 3.4 Extend rendering and manifest coverage to prove aggregate LaTeX macros and provenance retain
      both corpus identities, denominator keys, mapping identity, and qualification.
- [ ] 3.5 Add a regression proving the comparison is labelled descriptive rather than paired or causal
      when project populations or eligibility differ.

## 4. Regenerate and verify producer outputs

- [ ] 4.1 Run the focused mapping, RQ5, RQ6, metric-model, rendering, and manifest tests in the pinned Nix
      environment.
- [ ] 4.2 Run the complete registered report set once through
      `uv run --directory analysis python -m teralizer.eval all`. Inspect both source reports, comparison
      metrics, aggregate macros, input snapshots, and provenance; confirm no unrelated value changes.
- [ ] 4.3 Update only normally generated, tracked producer artifacts required by the registered report
      run. Do not hand-edit a report, macro, provenance record, database, or corpus input.
- [ ] 4.4 Run `uv run --directory analysis pytest`, `uv run --directory analysis ruff check .`, and
      `uv run --directory analysis ty check .` in the pinned Nix environment.
- [ ] 4.5 Run `openspec validate publish-controlled-comparison-metrics --strict` and
      `lefthook run pre-commit --all-files`.

## 5. Commit and hand off the comparison contract

- [ ] 5.1 Review the approved mapping record, implementation, negative controls, generated-output diff,
      and provenance together. Confirm no unapproved schema interpretation, table rewrite, corpus
      mutation, or thesis claim entered the change.
- [ ] 5.2 Commit the implementation and generated comparison contract as one causal subject using
      `personal_commit`.
      Message: `feat(eval): publish mapped comparison metrics`
- [ ] 5.3 Record the clean producer revision, normalized measure, both metric triplets and macro
      identities, mapping classification, interpretation bound, and report/provenance verification for
      the blocked thesis reconciliation. Do not resume the thesis cutover from a dirty, unapproved, or
      unarchived producer state.
