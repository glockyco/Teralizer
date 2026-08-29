## REMOVED Requirements

### Requirement: Filter decisions exclude non-filter producers

**Reason**: The requirement classifies filters by their current producer stage. The report defines filtering by proactive exclusion behavior instead.

**Migration**: The filter-detail table includes proactive exclusions when persisted evidence identifies their evaluated population and verdicts. It continues to exclude reactive build and processing failures.

## ADDED Requirements

### Requirement: Filter classification follows proactive exclusion behavior

The report SHALL classify a decision as filtering when it proactively excludes a candidate to prevent an unsupported, unsafe, or predictably failing downstream operation. The classification SHALL NOT depend on the pipeline stage, producer class, or persistence shape.

The filter-detail table SHALL retain the columns `Level`, `Filter Name`, `Total`, `Accept`, `Defer`, and `Reject`. Filter names SHALL use the established PascalCase form and SHALL omit only an implementation `Filter` suffix. Each row SHALL derive its evaluated population and verdicts from persisted evidence. `Total` SHALL equal `Accept` plus `Defer` plus `Reject`.

#### Scenario: Pre-emission checks filter generalization attempts

- **WHEN** `SeedSpecConsistency` or `WideningLicense` proactively rejects a generalization attempt before source emission
- **THEN** the filter-detail table includes the rejection at the `Generalization` level
- **AND** the filter names are `SeedSpecConsistency` and `WideningLicense`
- **AND** source emission is not required for the candidate attempt to exist

#### Scenario: An inherited test method cannot be flattened

- **WHEN** the persisted declaring method differs from the concrete test class
- **THEN** the filter-detail table records an `InheritedTestMethod` decision at the `Test` level
- **AND** `INHERITED_METHOD_NOT_FLATTENABLE` produces a `Reject` verdict
- **AND** another resolved inherited method produces an `Accept` verdict

#### Scenario: Generalization filtering reconciles

- **WHEN** the report aggregates generalization-level filter rows
- **THEN** rejected `SeedSpecConsistency`, `WideningLicense`, and `NonPassingTest` populations are mutually exclusive
- **AND** their rejection counts sum to the generalization-level `Filtering` outcome

#### Scenario: Proactive decision evidence is incomplete

- **WHEN** persisted evidence cannot determine whether a candidate reached or passed a proactive filter
- **THEN** report generation fails and identifies the incomplete filter evidence
- **AND** the report does not infer a verdict from a mutable inclusion flag

#### Scenario: A reactive failure uses filter-result storage

- **WHEN** compilation removes a generated source and records a rejection-shaped row
- **THEN** the outcome remains a build failure
- **AND** it is absent from per-filter decision totals

#### Scenario: Several filters reject one entity

- **WHEN** multiple filters evaluate and reject the same entity
- **THEN** each filter row records its decision
- **AND** the entity contributes only once to the exclusion outcome total
