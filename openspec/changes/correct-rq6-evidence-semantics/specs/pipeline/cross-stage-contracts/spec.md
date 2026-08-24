## MODIFIED Requirements

### Requirement: MUT resolution and filtering share one persisted observation

Method-under-test resolution SHALL classify a selected call as resolved only when its source declaration has the stable paths and identity required for generalization. It SHALL persist that call and every required tested-method field as one coherent observation before assertion filtering consumes it. Filtering SHALL derive missing-value and type decisions from the persisted generalization inputs rather than from declaration capability alone.

#### Scenario: Resolution selects a source method

- **WHEN** the resolver selects a call whose source declaration and signature are available
- **THEN** the selected call identity, declaration identity, parameter types, and return type are persisted together
- **AND** downstream filtering observes the same method shape

#### Scenario: A selected source declaration has no stable path

- **WHEN** the selected call belongs to an anonymous or local source class whose declaration cannot be addressed for generalization
- **THEN** resolution records a characterization-only outcome with an explicit unpathable-source reason
- **AND** it does not publish the pick as a resolved generalization-grade method

#### Scenario: Filtering evaluates parameter support

- **WHEN** assertion filtering evaluates the selected call's generated inputs
- **THEN** its decision uses the normalized persisted generalization parameters
- **AND** a supported declared parameter supplied only by a constant or `null` does not count as a generated input
