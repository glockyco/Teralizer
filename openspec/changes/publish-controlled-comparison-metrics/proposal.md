## Why

The thesis retains one controlled-versus-RepoReapers comparison, but RQ5 and RQ6 record generalized-test
outcomes in different schema shapes. The controlled side exposes only a legacy terminal inclusion flag
inside a rendered RQ5 table row, while RQ6 distinguishes attempt, emission, filter, validation, and
reduction lifecycle states. Without an explicit evidence-backed mapping, stable metric identities alone
would make unlike measures look comparable.

## What Changes

- Resolve each corpus's producer commit and reconstruct its historical pipeline graph from
  `ProcessingStage`, `PipelinePlanner`, scheduled task dependencies, and persisted outcomes. Inventory
  schema fields, writer semantics, eligibility rules, variant selection, and lifecycle boundaries in
  that commit rather than projecting the current Stage 1-5 organization onto RQ5.
- Define one normalized comparison measure and map each corpus to it independently. Classify each mapping
  as exact, qualified, or unmappable, with executable invariants and an explicit interpretation bound.
- Stop and present any unmappable or multiply plausible case to the operator. Do not choose the most
  convenient signal, infer missing lifecycle state, or narrow the comparison silently.
- Emit numerator, denominator, and share metrics for both sides from one registered cross-corpus
  comparison implementation only after the mapping is approved.
- Attach each side's `Generalization` population, corpus identity, denominator key, source-query
  provenance, and mapping identity to the generated comparison.
- Retain the existing RQ5 and RQ6 tables as their own presentations. Use table agreement as a checked
  consequence where the mapped measure is equivalent, not as evidence that the schemas mean the same
  thing.
- Publish the approved comparison metrics through the existing aggregate macro artifact so downstream
  consumers use generated values rather than table-position lookup or prose literals.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Requires the retained controlled comparison to expose stable,
  denominator-explicit metric identities backed by approved mappings from each corpus's historical
  pipeline and persisted evidence.

## Impact

- Controlled RQ5 and real-world RQ6 report inputs, their focused evidence queries, and comparison tests.
- Registered comparison metric inventory, provenance output, and generated aggregate LaTeX macros.
- Existing controlled and real-world tables remain unchanged unless the mapping audit proves that a
  current table claim is itself incorrect; that case requires operator review before scope changes.
- No database schema, corpus contents, pipeline execution, or handwritten generated artifact changes.
- The mapping may add read-only queries and a second declared corpus input to the owning registered
  report. It does not alter either corpus or reinterpret an unmappable field as evidence.
