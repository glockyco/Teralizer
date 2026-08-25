## ADDED Requirements

### Requirement: Retained manual claims use reconstructed audit evidence

A retained RQ6 claim that depends on manual inspection or preserved version 7 run state SHALL derive from a declared evidence-reconstruction audit input. The report SHALL publish the reconstruction status, resolved population, unresolved population, and source identity with each such claim.

The report SHALL NOT convert `partially-supported`, `contradicted`, or `evidence-gap` status into a complete-population numeric claim.

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

### Requirement: Thesis-facing reconstruction results use structured metrics

The registered RQ6 report SHALL publish each reconstruction quantity used by thesis prose as a
structured metric. Aggregate LaTeX rendering SHALL derive its macro from that metric. The metric SHALL
preserve whether the value is an exact count, a sample estimate, or a confidence bound and SHALL carry
the reconstruction audit provenance.

The thesis-facing metric surface SHALL include the stratified `NoAssertions` genuine-absence estimate
and confidence bounds, the reviewed assertion-to-MUT outcome counts, and the complete output-discovery
outcome counts. The report SHALL NOT require the thesis to include reconstruction audit tables or copy
values from prose fields.

#### Scenario: The thesis uses the `NoAssertions` estimate

- **WHEN** the report renders aggregate macros
- **THEN** it emits separate macros for the estimate and both confidence bounds
- **AND** the values derive from structured estimate metrics
- **AND** none is represented as an exact population rate

#### Scenario: The thesis uses a reviewed outcome count

- **WHEN** the report renders the assertion-to-MUT or output-discovery quantity
- **THEN** the aggregate macro derives from a structured count metric
- **AND** the metric identifies its reviewed or complete population boundary

#### Scenario: Reconstruction audit tables remain producer-side

- **WHEN** the thesis publishes the aggregate macros
- **THEN** it does not need the reconstruction summary or outcome table as a document input
- **AND** report provenance still traces each macro to the committed audit input
