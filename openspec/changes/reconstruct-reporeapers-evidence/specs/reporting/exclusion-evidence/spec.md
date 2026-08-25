## ADDED Requirements

### Requirement: Retained manual claims use reconstructed audit evidence

A retained RQ6 claim that depends on manual inspection or historical run state SHALL derive from a declared evidence-reconstruction audit input. The report SHALL publish the reconstruction status, resolved population, unresolved population, and source identity with each such claim.

The report SHALL NOT convert `partially-supported`, `contradicted`, or `evidence-gap` status into a complete-population numeric claim. It SHALL NOT substitute a historical corpus quantity for a version 7 quantity.

#### Scenario: Manual classifications cover the complete population

- **WHEN** the declared audit input resolves every entity in its version 7 population
- **THEN** the report may publish the complete classification counts and rates
- **AND** each rate names the version 7 denominator

#### Scenario: Manual classifications are incomplete

- **WHEN** the audit input leaves one or more entities unresolved
- **THEN** the report publishes the resolved and unresolved populations separately
- **AND** it does not publish an exact complete-population classification rate

#### Scenario: A retained claim has an evidence gap

- **WHEN** its audit input has status `evidence-gap`
- **THEN** the report emits the evidence-gap status and checked-source summary
- **AND** it emits no unsupported numeric value for that claim
