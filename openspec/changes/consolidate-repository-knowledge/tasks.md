## 1. Prove Current Owners

- [x] 1.1 Map every test shape in `verification/spikes/r1-viability` to the promoted
      `verification/fixtures/expression-slice` case and its observed golden; record any unmatched
      behavior before deleting anything.
- [x] 1.2 Confirm the expression-slice config and golden participate in the ordinary verification
      manifest, and run the focused expression-slice verification path if the current gate supports
      a single-fixture invocation.
- [x] 1.3 Inventory every non-OpenSpec reference to the six `docs/` files and assign each retained
      contract, safety rule, or evidence claim to the capability specs, executable source, report,
      provenance, or scoped guidance named in `design.md`.
- [x] 1.4 Confirm that the strict expected-failure invariant still owns the lifecycle "failed"
      versus "not attempted" defect before removing its historical defect table.

## 2. Reconcile the Database Change

- [x] 2.1 Revise `consolidate-evaluation-databases/proposal.md` so it no longer promises generated
      `docs/database.md` or a corpus table under the removed `docs/evaluation-run-map`.
- [x] 2.2 Revise that change's design and evaluation-data delta specs so DDL, corpus registry data,
      replication manifests, and accepted requirements are the only maintained schema and corpus
      authorities.
- [x] 2.3 Replace task 5.8 and every dependent task or acceptance check with validation of the actual
      registry or manifest consumers; do not add a replacement narrative document.
- [x] 2.4 Run strict validation for `consolidate-evaluation-databases` and confirm its remaining scope
      does not create or prescribe any path below `docs/`.

## 3. Extend Repository-State Validation

- [x] 3.1 Generalize `analysis/tests/test_planning_home.py` into a repository-state guard while
      preserving every existing single-planning-home positive control and repository assertion.
- [x] 3.2 Add injected positive controls that reject a tracked `docs/` path and an operative
      non-OpenSpec reference to each retired technical-document path; allow OpenSpec migration
      records to name those paths.
- [x] 3.3 Add an injected positive control that rejects project-specific context, rules, or operation
      guidance in `openspec/config.yaml` while accepting the single `schema: spec-driven` mapping.
- [x] 3.4 Add an injected positive control that rejects the known R1 spike paths after promotion, and
      make the real-repository check require the expression-slice config and golden to remain.
- [x] 3.5 Run the focused guard controls before cutover and prove each known-bad fixture fails for its
      intended reason rather than through an unrelated check.

## 4. Migrate Operative Guidance

- [x] 4.1 Replace the architecture and database links in `AGENTS.md` and
      `.omp/rules/{pipeline,db}.md` with direct executable owners and the new capability paths;
      retain only non-obvious stage-alignment, exclusion, and destructive-operation rules.
- [x] 4.2 Move safety-critical local-state retention rules to the narrowest applicable agent guidance
      for databases, measurement run roots, verification residue, detached-run records, and
      regenerable scratch outputs; discard volatile inventories and unowned defer-until-later notes.
- [x] 4.3 Make RQ6 report comments, diagnostics, and invariant-test messages self-contained or point
      to `reporting/exclusion-accounting`; remove prescriptive links to `docs/exclusion-model.md`.
- [x] 4.4 Update README navigation to point to stage declarations, DDL, the report registry, accepted
      specs, and the regenerable expression-slice fixture instead of a `docs/` directory.
- [x] 4.5 Remove the active R1-spike row from project-config guidance and verify no current guidance
      describes the experiment as a runnable lane.

## 5. Perform the Atomic Cutover

- [x] 5.1 Delete `project-configs/spikes/r1-viability.conf`, the empty config directory, and
      `verification/spikes/r1-viability/`; keep the promoted fixture, config, and golden unchanged.
- [x] 5.2 Reduce `openspec/config.yaml` to the single `schema: spec-driven` mapping without moving its
      former narrative into another file.
- [x] 5.3 Delete all six files under `docs/` and remove the empty directory in the same change as their
      operative reference updates.
- [x] 5.4 Enable the real-repository knowledge assertions and confirm there is no tracked `docs/`
      path, no operative retired-document reference outside OpenSpec, no duplicate OpenSpec context,
      and no superseded spike path.
- [x] 5.5 Review the complete diff by subject and confirm it changes no Java pipeline behavior,
      report query, generated report value, database, corpus input, or published consumer artifact.

## 6. Verify the Cutover

- [x] 6.1 Run the focused repository-state test module, including every positive control and the real
      repository assertion.
- [x] 6.2 Run the complete non-database analysis test suite.
- [x] 6.3 Run the repository's formatting, lint, type, and file-hygiene commit hooks over all changed
      files.
- [x] 6.4 Run `openspec validate --all --strict` and confirm both this change and the reconciled
      database change are valid.
- [x] 6.5 Re-run the tracked-path and reference audit as a positive-control check, then review the
      final diff for unresolved links, empty retired directories, and accidental evidence changes.
