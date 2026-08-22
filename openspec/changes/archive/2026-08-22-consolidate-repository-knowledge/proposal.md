## Why

The repository still carries six free-standing technical documents, duplicated project context in
`openspec/config.yaml`, and a completed R1 spike beside the verification fixture that superseded it.
These snapshots are already stale in several places, so they make readers choose between prose and
the executable or evidence-bearing authority.

## What Changes

- **BREAKING**: remove the `docs/` directory and every live reference to its six files. Do not move
  the files to another narrative documentation directory.
- Preserve only durable requirements that cannot safely be reconstructed on demand. Capture
  cross-stage pipeline contracts and exclusion-reporting semantics as accepted capability specs.
  Leave implementation maps to source, schema inventories to DDL, generated-artifact examples to
  verification outputs, and measured values to registered reports and provenance.
- Move concise operator-only safety rules from `docs/local-state.md` into the scoped agent guidance
  that needs them. Do not preserve volatile inventories or deferred retention notes as contracts.
- Reconcile `consolidate-evaluation-databases` so it no longer plans to generate
  `docs/database.md` or the already-removed `docs/evaluation-run-map`. The corpus registry, DDL, and
  evaluation-data specs remain its authorities.
- Reduce `openspec/config.yaml` to the configured schema. Project description, stack, research
  principles, commit policy, writing policy, and document pointers already live in repository
  guidance or can be read from the current tree.
- Delete `project-configs/spikes/r1-viability.conf` and the matching
  `verification/spikes/r1-viability/` project. They have no current consumer, and commit `cd71c553`
  promoted the same expression-slice cases into the ordinary verification corpus with an observed
  golden.
- Add repository validation that rejects a reintroduced free-standing `docs/` knowledge tree,
  duplicated project narrative in `openspec/config.yaml`, unresolved retired-document references,
  and completed spike fixtures left beside their promoted verification owner.

## Capabilities

### New Capabilities

- `repository/knowledge-authority`: where durable contracts, executable facts, empirical evidence,
  and operator guidance belong, and which duplicate documentation surfaces the repository rejects.
- `pipeline/cross-stage-contracts`: the stable phase, recipe, code-generation, persistence, and
  failure-isolation behavior that callers across pipeline stages rely on.
- `reporting/exclusion-accounting`: total exclusion classification, authoritative outcome fields,
  denominators, and fail-loud drift checks for RQ6 reporting.

### Modified Capabilities

None.

## Impact

- Removes `docs/{architecture,artifacts,database,exclusion-model,local-state,rq6-analysis}.md` and
  updates `README.md`, `AGENTS.md`, `.omp/rules/{db,pipeline}.md`, RQ6 source diagnostics, and tests
  that cite those paths.
- Adds accepted specs under `openspec/specs/` when this change is archived. The specs contain no
  corpus counts, database names, line numbers, package inventories, or generated-code listings.
- Revises the active `consolidate-evaluation-databases` planning artifacts before either change can
  recreate a retired documentation path.
- Removes the redundant R1 spike config and Maven project. The promoted
  `verification/fixtures/expression-slice` fixture, its config, and its golden remain.
- Changes no pipeline output, report value, corpus, database, or published thesis artifact.
