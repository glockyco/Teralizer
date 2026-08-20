## 1. Reconcile Ownership and Establish Baselines

- [x] 1.1 Re-read the current artifacts for `consolidate-evaluation-databases`,
  `separate-report-values-from-presentation`, `declare-published-artifacts`, and
  `materialize-exclusion-evidence`; build a task-and-interface ownership matrix and resolve every
  overlap before editing implementation code.
- [x] 1.2 Update `consolidate-evaluation-databases` so it owns the corpus registry, registry validation,
  read-only lifecycle, dumps, and physical renames, while this change owns one-or-more report input
  roles, report construction context, removal of `ReportSpec.schema`, and removal of database
  overrides; correct every singular-corpus requirement and design premise.
- [x] 1.3 Update `separate-report-values-from-presentation` to depend on the explicit run result and
  artifact interfaces while retaining sole ownership of value kinds, entity rendering, and target
  formatting.
- [x] 1.4 Update `declare-published-artifacts` so it retains declaration syntax, declared-set policy,
  consumer guards, and delivery, but consumes this change's `ArtifactSet` and staged run instead of
  implementing a nested emitted map or generator-run orchestration.
- [x] 1.5 Update `materialize-exclusion-evidence` to depend on this change, declare its corpus and audit
  inputs through the common model, aggregate its canonical SQL facts before transfer, and add no
  report-run, manifest, renderer-return, or publication special case.
- [x] 1.6 Validate all affected OpenSpec changes and prove each capability, implementation interface,
  and task has one active owner.
- [x] 1.7 Trace every registered report's direct and transitive inputs, including secondary database
  connections, optional markers, committed data files, fallback data, and pinned project source trees;
  use the resulting inventory to define the migration, not as a second durable registry.
- [x] 1.8 Snapshot every generated Markdown report, provenance manifest, LaTeX artifact, CSV file, and
  figure outside the repository for byte-level comparison; record current artifact ownership by report
  and target.
- [x] 1.9 Create one new `docs(openspec)` commit containing only the reconciled planning artifacts, with
  a causal body and strict validation results; do not reset, rebase, amend, squash, or rewrite an
  existing commit.

## 2. Materialize, Define, and Resolve Report Inputs

- [x] 2.1 Add focused tests for corpus and file declarations, including unique roles,
  repository-relative paths, required and optional files, invalid corpus ids, and immutable declaration
  tuples.
- [x] 2.2 Implement the closed corpus-or-file declaration types and registration validation without a
  primary-input alias, generic directory type, or unconstrained physical database field.
- [x] 2.3 Implement resolved input handles and immutable corpus, file, and absent-input snapshots;
  expose role-based lookup with errors that name the report and role.
- [x] 2.4 Resolve corpus roles through the finalized corpus registry, verify expected project count,
  read-only access, corpus-definition completeness, and role-specific required objects before calling a
  builder.
- [x] 2.5 Resolve repository file roles with required or optional absence semantics, SHA-256, last file
  commit, and dirty state; reject absolute or escaping declaration paths.
- [x] 2.6 Add one project-source evidence extractor that writes a compact versioned per-project relation
  shared by dataset and RQ1, records nested repository or source-bundle identities and reconciliation
  totals, validates before atomic replacement, and matches both current raw-source report paths.
- [x] 2.7 Add one JARVIS evidence extractor that replaces the 1,524 database-selected jqwik log reads
  with normalized scoreboard and census PVC facts, records both corpus identities and selection totals,
  validates before atomic replacement, and matches current RQ0 results.
- [x] 2.8 Create one new `feat(eval)` commit for the normalized report evidence inputs and extractors,
  including the generated compact files and focused raw-source reconciliation checks.
- [x] 2.9 Use one `ExitStack` per report to own every corpus connection and prove connections remain live
  during construction and close before rendering.
- [x] 2.10 Recompute repository file snapshots after construction and fail before rendering when a file
  changed during the build.
- [x] 2.11 Add focused resolver tests with two corpus roles, a required missing file, an optional absent
  marker, a dirty file, a changed-during-build input, and one invalid corpus among several.

## 3. Cut Every Report Over to Explicit Context

- [ ] 3.1 Replace `ReportSpec`'s builder signature, default database, schema flag, report-level
  requirements, and optional single-corpus definition with the immutable input tuple and context-based
  builder contract.
- [ ] 3.2 Introduce `BuiltReport` as the renderable report plus runner-captured input snapshots, and
  remove physical database identity from `RQReport`.
- [ ] 3.3 Migrate the dataset report to declared controlled, real-world, and required
  `project-source-facts` roles; remove its internal real-world connection, `PROJECTS_PATH` traversal,
  and stale dataset-statistics fallback.
- [ ] 3.4 Migrate RQ0 to declared scenario, benchmark, required `jarvis-pvc-facts`, CUT-value, and
  completion-marker roles; remove its internal benchmark connection, dynamic jqwik-log reads,
  module-default evidence paths, and database-valued provenance metrics.
- [ ] 3.5 Migrate RQ1 to the controlled corpus and required `project-source-facts` role, removing its
  database-selected source traversal and stale mutants-per-project fallback; migrate RQ2 through RQ5
  to the controlled corpus with no placeholder file inputs.
- [ ] 3.6 Migrate RQ6 to the real-world corpus role and preserve its corpus-completeness validation;
  leave the future widening audit undeclared until its real file exists.
- [ ] 3.7 Remove `--db`, `--corpus-data-dir`, and `--corpus-config-dir`, add closed render-target parsing,
  and replace CLI override tests with resolver injection at the input boundary.
- [ ] 3.8 Add an architecture check that registered report builders and their report-facing helpers do
  not open evaluation connections or resolve undeclared default evidence paths.
- [ ] 3.9 Build all eight reports from their declarations and compare every Markdown, LaTeX, CSV, and
  figure artifact with the baseline; require byte identity before continuing.

## 4. Record Generic Input Provenance

- [ ] 4.1 Extend provenance tests so every built report records all declared roles, corpus identity,
  observed registry state, file content identity, optional absence, source revision, and dirty state
  through one schema; validate normalized evidence metadata without teaching the generic runner its
  domain fields.
- [ ] 4.2 Build manifest entries from `BuiltReport`, keeping current per-artifact source-file provenance
  separate from report-level input snapshots and preventing builders from supplying either input
  identity or input omission.
- [ ] 4.3 Remove the RQ0-specific `report_basis` branch only after mapping its corpus identities to input
  snapshots, its result values to existing typed metrics, and its table keys to generic report
  artifacts; verify no current consumer-visible information disappears.
- [ ] 4.4 Add publication checks for dirty declared inputs, the documented local override, and unchanged
  source-code commits when only an input file changes.
- [ ] 4.5 Regenerate the report set and confirm the only baseline difference is the intended generic
  manifest input structure; all values, prose, tables, CSVs, and figures remain byte-identical.
- [ ] 4.6 Create one new `refactor(eval)` commit for the complete report-input cutover and generic input
  provenance, with a causal body and focused unit, live-report, and byte-comparison verification.

## 5. Unify Renderer Output

- [ ] 5.1 Add focused tests for the closed render-target type, target-plus-key identity, report and
  run-aggregate ownership, same-target duplicate rejection, cross-target key reuse, and path containment.
- [ ] 5.2 Implement `ArtifactId`, `RenderedArtifact`, and `ArtifactSet` with one merge path that names both
  owners on collision and refuses a missing or escaping emitted path.
- [ ] 5.3 Migrate the Markdown renderer to write below a supplied root and return its report-owned
  artifacts without changing file content.
- [ ] 5.4 Migrate the CSV renderer to return target-keyed artifacts rather than a bare path list, preserving
  one CSV identity per table or figure-data export.
- [ ] 5.5 Migrate the LaTeX renderer and aggregate macro writer to distinguish report-owned table
  artifacts from the run-owned macro artifact without changing names or bytes.
- [ ] 5.6 Migrate figure materialization to return print, screen, and data outputs through the same
  artifact model while preserving the existing figure keys and publication behavior.
- [ ] 5.7 Migrate provenance-manifest rendering to a run-aggregate artifact and remove renderer-specific
  emitted-map translations from the CLI and publisher.
- [ ] 5.8 Render all targets for all reports through `ArtifactSet` and prove artifact names, owners,
  paths, and bytes match the baseline.
- [ ] 5.9 Create one new `refactor(eval)` commit for the common renderer artifact contract, with a causal
  body and focused renderer, collision, and byte-comparison verification.

## 6. Stage and Promote Complete Runs

- [ ] 6.1 Add failure-injection tests proving a late input-resolution, report-build, built-result
  validation, renderer, manifest, artifact-set, and consumer-declaration failure changes no final
  generator or consumer path.
- [ ] 6.2 Extract functional run phases from `cli.py`: report selection, preflight, complete construction,
  input revalidation, staged rendering, manifest assembly, artifact validation, promotion, and optional
  delivery.
- [ ] 6.3 Build and validate every selected report in memory before creating any rendered final output;
  keep report construction sequential and close all corpus connections before rendering.
- [ ] 6.4 Render selected targets beneath a same-filesystem temporary root and validate every staged path,
  target, key, owner, provenance record, aggregate artifact, and consumer declaration before promotion.
- [ ] 6.5 Implement full-run manifest reconstruction from the complete registry and partial-run in-memory
  replacement of selected entries; require a full run when a preserved entry has no registered owner.
- [ ] 6.6 Implement a same-filesystem promotion journal with atomic per-path replacement, backup of prior
  generated paths, stale-path reconciliation on full runs, selected-owner updates on partial runs, and
  reverse-order rollback on ordinary promotion failure.
- [ ] 6.7 Begin declaration-driven consumer delivery only after generator promotion succeeds, passing the
  already validated `ArtifactSet` into the existing publication policy.
- [ ] 6.8 Add tests for a successful partial run, successful full run, removed report, stale manifest
  entry, injected failure at each promotion boundary, rollback restoration, and no consumer destination.
- [ ] 6.9 Run the complete report set and prove its promoted generator output is byte-identical to the
  renderer baseline except for the already accepted manifest schema change.
- [ ] 6.10 Create one new `fix(eval)` commit for staged complete-run promotion and post-promotion delivery,
  with a causal body and failure-injection, rollback, full-run, and partial-run verification.

## 7. Verify the Architecture and Downstream Readiness

- [ ] 7.1 Run all focused registry, input-resolution, provenance, model, renderer, artifact-set, CLI,
  publication, and report smoke tests.
- [ ] 7.2 Run the complete non-database analysis suite once and investigate every new skip, expected
  failure, warning, or order-dependent result.
- [ ] 7.3 Run every registered report once from its declared inputs and record the exact report, metric,
  table, CSV, figure, and manifest artifact counts.
- [ ] 7.4 Run one report and the full report set from a working directory outside the repository root to
  prove every declared path is repository-relative and no current-directory fallback remains.
- [ ] 7.5 Run repository formatting, lint, type, file-hygiene, and commit hooks once over the completed
  architecture change.
- [ ] 7.6 Validate every active OpenSpec change and confirm the database, value, publication, exclusion,
  and repository-knowledge plans reference the final interfaces and dependency order.
- [ ] 7.7 Review the final diff by subject and confirm no evaluation value, caption, table layout, figure,
  corpus, Java pipeline behavior, consumer declaration, or undeclared compatibility path changed.
- [ ] 7.8 Confirm the branch remains append-only relative to its starting history and every new commit has
  one subject, a causal body, and the verification appropriate to that subject.
