## ADDED Requirements

### Requirement: Project exclusion evidence avoids inferred ownership classes

The RQ6 project-level exclusion table SHALL publish each observed exclusion stage, concrete cause description, and count. It SHALL use `Cause of Project-level Exclusion` as the cause-column heading. It SHALL NOT publish an internal, external, or mixed type for a project-level row.

Removing the type SHALL NOT by itself change the row set, recorded stage, cause description, or count. When entity evidence, stage-transition evidence, or task diagnostics contradict a legacy fallback, the report SHALL correct the affected cause rows and stage attribution while preserving the eligible-project total, final inclusion count, and total project exclusions. An earlier stage transition SHALL use evidence recorded at that boundary and SHALL NOT use a final mutable entity status that a later stage can change. The table SHALL retain pipeline-stage order and SHALL order causes within each stage by descending count, then ascending cause text. Generated metrics, table cells, validation, and downstream publication artifacts SHALL use the reduced schema consistently, with no compatibility alias for the removed type.

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

#### Scenario: Later failures change final entity status

- **WHEN** an assertion survives Stage 1 + 2 filtering and a later processing failure excludes it
- **THEN** the project remains in the Stage 1 + 2 survivor set
- **AND** project exclusion is attributed to the later stage that owns the failure
- **AND** every stage survivor set remains a subset of the preceding survivor set

#### Scenario: Internal mechanisms have the same reader-facing interpretation

- **WHEN** internal mechanism combinations identify the same failed transition and do not change its interpretation
- **THEN** the report aggregates them under one established reader-facing cause
- **AND** the cause does not expose internal mechanism names
- **AND** the aggregated rows reconcile to the project-funnel exclusion total

#### Scenario: Project and entity granularity remain separate

- **WHEN** a project-cause row is explained by filter rejections, processing failures, widening refusals, or another entity mechanism
- **THEN** the project row records the failed transition and its material reader-facing cause
- **AND** filter classes, exception subtypes, internal mechanism combinations, and individual entity counts remain in dedicated generated evidence

#### Scenario: Result prose explains an aggregate cause

- **WHEN** the thesis describes a broad project-cause category
- **THEN** it gives selected evidence-backed examples that explain the principal mechanisms
- **AND** it distinguishes overlapping subtype counts from exclusive project rows
- **AND** it leaves exhaustive subtype distributions to the replication package
- **AND** it does not present an outcome code or catch-all diagnostic as a validated root cause

#### Scenario: Reduction fails on different suite sides

- **WHEN** reduction diagnostics identify failures for the initial and generalized suites
- **THEN** project-cause rows distinguish the suite side when it changes the failed operation
- **AND** both rows remain within the Stage 5 project denominator

#### Scenario: JUnit report collection has detailed diagnostics

- **WHEN** task diagnostics distinguish a missing report file from an unsupported report layout
- **THEN** generated evidence preserves both diagnostics
- **AND** the project table aggregates them as a JUnit report collection failure
- **AND** the result description MAY state the material diagnostic split

#### Scenario: A consumer expects the removed type

- **WHEN** report validation or publication encounters a metric, table declaration, or artifact that still requires the project exclusion type
- **THEN** the change remains incomplete until that consumer uses the reduced schema
- **AND** the producer does not emit a placeholder or deprecated type