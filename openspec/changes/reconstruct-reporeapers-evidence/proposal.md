## Why

Some version 7 RepoReapers claims depend on manual inspection, preserved logs, or preserved run state that the current reports do not consume. The evidence exists on the Air, but it needs a read-only and reproducible reconstruction process before the thesis can cite it.

## What Changes

- Inventory the preserved version 7 database, logs, project checkouts, run roots, configurations, and producer revisions on the Air.
- Keep `postgres_reporeapers_rq6_v7` as the only RepoReapers corpus for reports and thesis quantities.
- Prohibit every pipeline rerun, project rerun, task retry, and regenerated measurement during reconstruction.
- Reconstruct manual evidence for `NoAssertions`, assertion-to-MUT resolution, and output-directory failures from version 7 artifacts only.
- Record each reconstructed claim with its population, method, source identities, adjudication, uncertainty, and verification result.
- Record an explicit evidence gap when collected artifacts cannot support a claim. Do not replace missing evidence with a rerun or an inferred value.
- Normalize accepted reconstruction results into versioned audit inputs that registered reports can consume with provenance.

## Capabilities

### New Capabilities

- `reporting/evidence-reconstruction`: Defines read-only version 7 evidence recovery, manual adjudication, gap reporting, and corpus boundaries.

### Modified Capabilities

- `reporting/exclusion-evidence`: Requires reconstructed manual evidence and unresolved gaps to enter the retained RQ6 evidence surface through explicit audit inputs.

## Impact

This change affects analysis commands, audit-input schemas, report provenance, focused tests, and evidence documentation. It reads preserved version 7 evidence from the Air and `postgres_reporeapers_rq6_v7`. It does not run Teralizer, rerun projects, retry failed tasks, modify preserved project state, or replace the corpus.