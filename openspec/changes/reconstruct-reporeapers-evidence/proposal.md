## Why

Some RepoReapers claims depend on manual inspection, historical logs, or preserved run state that the current reports do not consume. The evidence exists on the Air, but it needs a read-only and reproducible reconstruction process before the thesis can cite it.

## What Changes

- Inventory the preserved RepoReapers databases, logs, project checkouts, run roots, configurations, and producer revisions on the Air.
- Keep `postgres_reporeapers_rq6_v7` as the only canonical RepoReapers corpus for current reports and thesis quantities.
- Prohibit every pipeline rerun, project rerun, task retry, and regenerated measurement during reconstruction.
- Reconstruct manual evidence for `NoAssertions`, assertion-to-MUT resolution, output-directory failures, and the historical doubled-timeout claim from collected artifacts only.
- Record each reconstructed claim with its population, method, source identities, adjudication, uncertainty, and verification result.
- Record an explicit evidence gap when collected artifacts cannot support a claim. Do not replace missing evidence with a rerun or an inferred value.
- Normalize accepted reconstruction results into versioned audit inputs that registered reports can consume with provenance.
- Keep historical database dumps as evidence sources. Do not register them as current corpora or aliases of the canonical corpus.

## Capabilities

### New Capabilities

- `reporting/evidence-reconstruction`: Defines read-only historical evidence recovery, manual adjudication, gap reporting, and canonical-corpus boundaries.

### Modified Capabilities

- `reporting/exclusion-evidence`: Requires reconstructed manual evidence and unresolved gaps to enter the retained RQ6 evidence surface through explicit audit inputs.

## Impact

This change affects analysis commands, audit-input schemas, report provenance, focused tests, and evidence documentation. It reads archived evidence from the Air and current evidence from `postgres_reporeapers_rq6_v7`. It does not run Teralizer, rerun projects, retry failed tasks, modify preserved project state, or replace the canonical corpus.