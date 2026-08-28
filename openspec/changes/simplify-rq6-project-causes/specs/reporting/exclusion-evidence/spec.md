## ADDED Requirements

### Requirement: Project exclusion evidence avoids inferred ownership classes

The RQ6 project-level exclusion table SHALL publish each observed exclusion stage, concrete cause description, and count. It SHALL use `Cause of Project-level Exclusion` as the cause-column heading. It SHALL NOT publish an internal, external, or mixed type for a project-level row.

Removing the type SHALL NOT change the row set, recorded stage, cause description, count, corpus, or provenance. Generated metrics, table cells, validation, and downstream publication artifacts SHALL use the reduced schema consistently, with no compatibility alias for the removed type.

#### Scenario: Project exclusions are rendered

- **WHEN** the RQ6 report renders the project-level exclusion table
- **THEN** each row contains its stage, cause description, and count
- **AND** neither the table nor its generated metrics contain an internal, external, or mixed type

#### Scenario: Taxonomy removal changes evidence

- **WHEN** the report migrates from the typed table to the reduced table
- **THEN** the ordered stage, cause, and count rows equal the corresponding fields in the prior evidence
- **AND** generation fails if removing the type changes a count or drops a cause row

#### Scenario: A consumer expects the removed type

- **WHEN** report validation or publication encounters a metric, table declaration, or artifact that still requires the project exclusion type
- **THEN** the change remains incomplete until that consumer uses the reduced schema
- **AND** the producer does not emit a placeholder or deprecated type