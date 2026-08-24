## MODIFIED Requirements

### Requirement: MUT resolution and filtering share one persisted observation

Method-under-test resolution SHALL persist the selected call and every required tested-method field as one coherent observation before assertion filtering consumes it. Filtering SHALL derive missing-value and type decisions from that persisted observation rather than reconstructing a conflicting method shape.

#### Scenario: Resolution selects a source method

- **WHEN** the resolver selects a call whose source declaration and signature are available
- **THEN** the selected call identity, declaration identity, parameter types, and return type are persisted together
- **AND** downstream filtering observes the same method shape

#### Scenario: Required resolved fields cannot be persisted

- **WHEN** resolution selected a call but a required tested-method field cannot be stored
- **THEN** the pipeline records an explicit persistence defect
- **AND** it does not emit a normal missing-value limitation for that assertion

#### Scenario: Filtering evaluates parameter support

- **WHEN** assertion filtering evaluates the selected method's parameters
- **THEN** its decision uses the normalized persisted parameter types
- **AND** a supported generated-input domain cannot coexist with an unexplained unsupported-parameter rejection
