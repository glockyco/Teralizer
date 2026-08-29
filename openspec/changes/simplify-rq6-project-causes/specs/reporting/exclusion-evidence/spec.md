## ADDED Requirements

### Requirement: Project exclusion evidence avoids inferred ownership classes

The RQ6 project-level exclusion table SHALL publish each observed exclusion stage, concrete cause description, and count. It SHALL use `Cause of Project-level Exclusion` as the cause-column heading. It SHALL NOT publish an internal, external, or mixed type for a project-level row.

Removing the type SHALL NOT by itself change the row set, recorded stage, cause description, or count. When entity evidence or task diagnostics contradict a legacy fallback, the report SHALL correct the affected cause rows and stage attribution while preserving the project-funnel total. The table SHALL retain pipeline-stage order and SHALL order causes within each stage by descending count, then ascending cause text. Generated metrics, table cells, validation, and downstream publication artifacts SHALL use the reduced schema consistently, with no compatibility alias for the removed type.

#### Scenario: Project exclusions are rendered

- **WHEN** the RQ6 report renders the project-level exclusion table
- **THEN** each row contains its stage, cause description, and count
- **AND** neither the table nor its generated metrics contain an internal, external, or mixed type

#### Scenario: Taxonomy removal changes evidence

- **WHEN** the report migrates from the typed table to the reduced table
- **THEN** unchanged causes equal the corresponding field projection from the prior evidence
- **AND** each changed cause has entity or task-diagnostic evidence that contradicts the prior fallback
- **AND** causes within each stage appear by descending count, then ascending cause text
- **AND** generation fails if the reduced rows do not reconcile to the project funnel

#### Scenario: Complete test loss has different observed mechanisms

- **WHEN** projects with no included tests have different combinations of filter evidence, task failures, or missing entity records
- **THEN** the report publishes separate cause rows for those combinations
- **AND** it does not infer filter or failure involvement from an included-test count of zero
- **AND** the separate rows reconcile to the unchanged project-funnel exclusion total

#### Scenario: JUnit report collection has a diagnosed cause

- **WHEN** task diagnostics distinguish a missing report file from an unsupported report layout
- **THEN** the report preserves that distinction in its project-cause rows
- **AND** it does not collapse both diagnostics into a generic missing-report label

#### Scenario: A consumer expects the removed type

- **WHEN** report validation or publication encounters a metric, table declaration, or artifact that still requires the project exclusion type
- **THEN** the change remains incomplete until that consumer uses the reduced schema
- **AND** the producer does not emit a placeholder or deprecated type