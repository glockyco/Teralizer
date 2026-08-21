## Why

The report runner cannot currently state all inputs or all outputs of a run: two reports open secondary
corpora themselves, file inputs are invisible to provenance, builders can contradict the runner's
database identity, and renderers write incompatible output shapes directly into final directories.
The upcoming database, renderer, publication, and exclusion changes need one explicit run boundary or
each will add another local convention around these gaps.

## What Changes

- **BREAKING**: replace the single-connection report builder with a `ReportContext` that resolves every
  named corpus and repository-file input declared by `ReportSpec`. Large ignored source and run-data
  trees are reduced once by domain extractors to compact, versioned evidence files rather than hidden
  behind a generic directory abstraction.
- **BREAKING**: remove physical database identity from `RQReport`; input identity is captured by the
  runner and cannot be supplied or overridden by report code.
- **BREAKING**: remove the ambiguous `--db` and single-corpus directory override interfaces. Reports
  read only the semantic inputs declared in the registry and report specification.
- Migrate every registered report, including the two multi-corpus reports and existing filesystem
  readers, so builders neither open undeclared connections nor resolve hidden evidence paths.
- Add `BuiltReport`, which pairs a renderable `RQReport` with immutable input snapshots captured by the
  runner.
- Add one typed `ArtifactSet` for every renderer, keyed by render target and artifact key and carrying
  the producing report. It owns duplicate detection and complete-run accumulation.
- Build every selected report before rendering, render the complete run into a staging root, construct
  one complete manifest, validate all artifact and consumer declarations, then promote generator
  output and deliver consumer output.
- Replace report-specific manifest branches with generic report input snapshots and code provenance.
  Corpus inputs record semantic corpus identity and verified registry state; file inputs record stable
  content identity. Compact evidence extracts also carry their upstream corpus or source identities and
  fail-loud reconciliation totals.
- Reconcile task ownership and dependency order with `consolidate-evaluation-databases`,
  `separate-report-values-from-presentation`, `declare-published-artifacts`, and
  `materialize-exclusion-evidence`. Those changes consume this run architecture rather than introducing
  compatibility helpers or parallel representations.
- Keep the implementation deliberately small: concrete dataclasses and orchestration functions, not a
  plugin framework, query language, dependency DAG, ORM layer, or generic evidence graph.
- Preserve existing Git history. The architecture migration lands as new, causally scoped Conventional
  Commits with focused verification; it performs no reset, rebase, amend, squash, or force-push.

## Capabilities

### New Capabilities

- `reporting/report-inputs`: how a report declares all corpus and repository evidence inputs, how the
  runner resolves them, and how their identities reach generated provenance.
- `reporting/report-execution`: the build, validation, staging, artifact accumulation, promotion, and
  delivery boundary for one complete report run.

### Modified Capabilities

- `reporting/artifact-provenance`: extend artifact provenance from producing code alone to the declared
  input snapshots of the report run, without weakening its existing per-source-file commit semantics
  or reproducibility guarantees.

## Impact

- `analysis/src/teralizer/eval/registry.py`: `ReportSpec` declares a closed set of named inputs instead
  of one default database, one schema flag, and an optional single-corpus definition.
- New focused modules under `analysis/src/teralizer/eval/` for input resolution, run orchestration, and
  typed emitted artifacts; `cli.py` becomes argument parsing and delegation.
- `analysis/src/teralizer/eval/model.py`: remove run-input identity from `RQReport` and add the minimal
  result types needed between build, render, and publication.
- `analysis/src/teralizer/eval/provenance.py` and `render/manifest.py`: combine per-artifact code
  provenance with generic report-level input snapshots and build the manifest once per run.
- Every renderer returns the same artifact abstraction and writes only below a supplied staging root.
- `analysis/src/teralizer/eval/publish.py`: consumes a validated `ArtifactSet`; it remains responsible
  for consumer declarations and delivery, not report building or render orchestration.
- All eight registered report modules: builder signatures, declared corpus and file roles, compact
  project-source and JARVIS evidence extraction, and removal of builder-owned connection, dynamic raw
  path, or fallback resolution.
- `consolidate-evaluation-databases`: retains ownership of the corpus registry, lifecycle, dumps, and
  physical renames; this change owns multi-input report resolution and corrects its single-corpus
  assumption.
- `separate-report-values-from-presentation`: retains ownership of semantic cell values and target
  formatting; its renderers emit through `ArtifactSet` after their value migration.
- `declare-published-artifacts`: retains ownership of consumer declarations and delivered-set policy;
  its implementation uses this change's staged run and artifact abstraction.
- `materialize-exclusion-evidence`: declares the real-world corpus and widening audit as inputs and
  keeps exclusion classification and funnel logic domain-specific.
- Generated values and figures remain unchanged by this refactor. The migration requires byte-identical
  report, table, CSV, and figure output before later feature changes are allowed.
