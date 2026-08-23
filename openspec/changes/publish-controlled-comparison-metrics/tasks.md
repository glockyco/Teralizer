## 1. Audit and approve the cross-schema mapping

- [ ] 1.1 Resolve each database's exact producer commit independently from its later report-query commit,
      plus its input snapshot, required schema objects, variant identity, and project eligibility
      predicate. Use corpus manifests and run evidence; do not substitute the current checkout when a
      producer revision is unknown.
- [ ] 1.2 At the controlled producer commit, reconstruct the executable pipeline graph from
      `ProcessingStage`, `PipelinePlanner`, scheduled tasks, prerequisites, and persisted task outcomes.
      Record task transitions without assigning current or thesis RQ6 Stage 1-5 meanings.
- [ ] 1.3 At the real-world producer commit, reconstruct the same executable graph and relate its cleaner
      attempted, emitted, filter-passed, validated, reduced, and final-usable evidence to the tasks that
      produced each transition.
- [ ] 1.4 Diff the two historical graphs. Record every task split, move, reorder, changed prerequisite,
      and changed persistence boundary that can affect the retained generalization denominator or
      numerator. Stage names and ordinals are not mapping keys.
- [ ] 1.5 Trace every controlled-revision writer that initializes, clears, or interprets
      `generalization.is_included`, then run read-only controlled-corpus audits against filter decisions,
      task outcomes, exclusion information, and the RQ5 table partition. Record which historical task
      transition the flag proves and which evidence is absent.
- [ ] 1.6 Map the real-world side from its historical planner graph and accepted eligibility, lifecycle,
      and exclusion relations. Prove which persisted transition, if any, matches the controlled boundary
      without relying on stage position or current count equality.
- [ ] 1.7 Create the complete mapping matrix specified by the design. Classify each side and the combined
      comparison as exact, qualified, or unmappable; record historical graph differences, denominator
      and project-population differences, and the strongest interpretation the evidence permits.
- [ ] 1.8 Inspect the typed metric and provenance schema to determine whether producer commits, mapping
      identity, and qualification survive manifest and macro publication. Propose the smallest typed
      metadata extension if the existing model cannot preserve them.
- [ ] 1.9 Present both historical graphs, source evidence, mapping matrix, qualifications, all plausible
      alternatives, and every unmappable case to the operator. Do not edit report code until the
      operator approves one normalized measure, its interpretation bound, and any metadata extension.

## 2. Implement only the approved translation

- [ ] 2.1 Add the approved mapping identity and qualification to the existing typed metric/provenance
      model only if task 1.9 approved that extension. Do not store the qualification only in prose.
- [ ] 2.2 Implement separate read-only controlled and real-world adapters that emit the same typed
      normalized observation contract from their native schema shapes. Reject missing, duplicate,
      contradictory, or inferred source states.
- [ ] 2.3 Make one registered comparison owner declare both corpus inputs and invoke both adapters in one
      build. Keep existing RQ5 and RQ6 table queries and presentation unchanged unless task 1.9 approved
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
