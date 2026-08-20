## Why

The repository retired its exclusion-model narrative before proving that every current contract,
measurement, and causal observation had a maintained replacement. The registered RQ6 report preserves
the collapsed headline results, but it does not publish the five-mechanism partition or the full
attempt-to-final-usable denominator funnel, and the old qualitative source audit cannot be reproduced
because it retained no sampled entity identities.

## What Changes

- Add a claim-level retirement gate: every substantive claim in a retired knowledge source must be
  assigned to an accepted contract, executable behavior with a focused check, registered empirical
  output with provenance, reproducible audit evidence, or an explicit stale/discarded disposition.
- Re-audit all six documents removed by `consolidate-repository-knowledge` from current source,
  configuration, tests, report behavior, and read-only corpus observations. Historical prose is only a
  checklist, never evidence.
- Materialize the RQ6 five-mechanism exclusion partition and the complete generalization denominator
  funnel as registered, provenance-bearing outputs.
- Make the semantic collapse explicit: filter rejection, pre-emission refusal, and unsupported
  capability are filtering outcomes; javac quarantine and task failure are failures.
- Define filter adjudication to exclude quarantine rows written through the `filter_result` storage
  shape, and expose distinct attempt, emitted, adjudicated, filter-passed, and final-usable measures.
- Preserve the existing fail-loud behavior for unknown exclusion codes, record shapes, and
  non-filter producers.
- Replace the unrepeatable widening source audit with a deterministic v7 audit that retains corpus,
  source revision, selected entities, observations, labels, and reviewer rationale. Use it only for
  causal claims beyond the immediate gate decisions already encoded by persisted refusal codes.
- Correct source comments and diagnostics that contradict current classification without changing
  pipeline behavior or frozen corpus data.
- Reconcile this change with every active reporting, database, publication, and repository-knowledge
  change before either overlapping change completes.
- Keep the existing Git history. Implementation lands as new, causally scoped Conventional Commits;
  no reset, rebase, amend, or force-push is part of this change.

## Capabilities

### New Capabilities

- `repository/knowledge-retirement`: proof required before a maintained knowledge source can be
  retired, including claim-level disposition and replacement-owner verification.
- `reporting/exclusion-evidence`: citable exclusion-mechanism partitions, denominator-explicit yield
  results, reproducible causal evidence, and fail-loud reconciliation rules.

### Modified Capabilities

None. `repository/knowledge-authority`, `reporting/exclusion-accounting`, and
`pipeline/cross-stage-contracts` are introduced by the still-active
`consolidate-repository-knowledge` change rather than the accepted spec set. This change reconciles
that active change's planning artifacts during implementation instead of creating competing deltas
for capabilities that do not yet exist in the main spec set.

## Impact

- `analysis/src/teralizer/eval/reports/rq6_causes.py`, `_causes_common.py`, and a focused report helper
  if separation is needed: mechanism and denominator materialization.
- `make-report-runs-explicit`: RQ6 declares the real-world corpus and deterministic audit input through
  `ReportSpec`, receives them through `ReportContext`, and returns evidence through `BuiltReport` and
  `ArtifactSet`. This change adds no report-run, manifest, renderer-return, or publication special
  case.
- `analysis/src/teralizer/eval/model.py` and renderers only through the interfaces finalized by
  `separate-report-values-from-presentation`; this change does not introduce a parallel table model.
- RQ6 report tests, corpus-backed invariants, metric-manifest tests, and generated report output.
- A versioned deterministic v7 audit-data input and registered analysis for qualitative causal claims
  not supported by persisted refusal codes and controlled fixtures.
- `openspec/changes/consolidate-repository-knowledge/`: claim-level disposition, corrected capability
  requirements, reopened cutover checks, and removal of assumptions that source history alone is a
  current evidence owner.
- `consolidate-evaluation-databases`: new outputs resolve the corpus through its registry and
  publication manifest rather than embedding a physical database name.
- `separate-report-values-from-presentation`: new tables use typed values and target-owned rendering.
- `declare-published-artifacts`: supporting evidence remains in the generator build unless a consumer
  explicitly declares it; thesis-facing macros and printed artifacts are delivered through the common
  declaration path.
- The thesis receives no hand-maintained numbers from this change. A later thesis change consumes the
  finalized report artifacts and macros under the thesis repository's generated-artifact procedure.
- No pipeline scheduling, exclusion decision, frozen database, measured entity, or existing commit is
  rewritten by this change.
