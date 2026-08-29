## REMOVED Requirements

### Requirement: Filter decisions exclude non-filter producers

**Reason**: The requirement classifies filters by their current producer stage. The report defines filtering by proactive exclusion behavior instead.

**Migration**: The filter-detail table includes proactive exclusions when persisted evidence identifies their evaluated population and verdicts. It continues to exclude reactive build and processing failures.

## ADDED Requirements

### Requirement: Filter classification follows proactive exclusion behavior

The report SHALL classify a decision as filtering when it proactively excludes a candidate to prevent an unsupported, unsafe, or predictably failing downstream operation. The classification SHALL NOT depend on the pipeline stage, producer class, or persistence shape.

The filter-detail table SHALL use the columns `Level`, `Filter Name`, `Evaluated`, `Accept`, `Defer`, and `Reject`. Filter names SHALL use the established PascalCase form and SHALL omit only an implementation `Filter` suffix. Each row SHALL derive its applicable population and verdicts from persisted evidence. `Evaluated` SHALL equal `Accept` plus `Defer` plus `Reject`.

The table SHALL group rows as first-round test filters, second-round test filters, inherited-method screening, assertion filters, and generalization filters. It SHALL insert a midrule between adjacent groups. `InheritedTestMethod` SHALL be separate from both test-filter rounds. The table SHALL preserve test, assertion, and generalization level order. Within each group, rows SHALL appear by descending `Evaluated`, descending `Reject`, then ascending filter name.

#### Scenario: Test decision populations are separated

- **WHEN** the filter-detail table contains both test-filter rounds and inherited-method screening
- **THEN** midrules separate all three test-level groups
- **AND** `NonPassingTest` and `TestType` appear in the first-round group
- **AND** `InheritedTestMethod` appears only in the inherited-method group
- **AND** the remaining test filters appear in the second-round group

#### Scenario: Filter subgroups are ranked

- **WHEN** the report renders a filter-detail subgroup
- **THEN** rows appear by descending `Evaluated`
- **AND** equal evaluated populations appear by descending `Reject`
- **AND** equal rejection counts appear by ascending filter name

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

### Requirement: Test filtering populations reconcile from persisted evidence

The registered report SHALL publish provenance-backed test-flow counts for identified tests, inherited-method screening, pre-filter failures, both test-filter rounds, overlapping first-round rejections, and intervening failures. It SHALL derive each population from persisted set membership and SHALL NOT add overlapping rejection counts.

#### Scenario: The first test-filter round is reached

- **WHEN** identified tests include inherited methods that cannot be flattened and tests that fail before filtering
- **THEN** the first-round population equals identified tests minus inherited-method rejections and pre-filter failures
- **AND** accepted inherited methods remain eligible for the first round

#### Scenario: The second test-filter round is reached

- **WHEN** first-round filters can reject the same test and a test can fail between rounds
- **THEN** the second-round population equals the first-round population minus the union of first-round rejections and intervening failures
- **AND** the overlap and intervening-failure counts remain registered evidence

### Requirement: Exclusion tables preserve semantic layout

Entity summary tables SHALL center the `Excluded` spanner over `Filtering` and `Failures`. Filter-detail tables SHALL render a midrule at each semantic group boundary. A long filter-detail table MAY use an explicit local compact density, but it SHALL preserve readable text, semantic rules, and the adjacent summary-table source boundary.

#### Scenario: The paired RQ6 exclusion tables are rendered

- **WHEN** the generated filter-detail table needs compact density to remain with its summary table
- **THEN** the renderer applies the density through its table-style contract
- **AND** it does not use negative spacing, global float changes, or generated-file edits

### Requirement: Excluded-test explanations match the executable predicate

A reader-facing explanation of `ExcludedTest` SHALL state that it rejects an assertion whose test was already excluded during collection, filtering, or processing. A concise discussion MAY omit the stage list, but it SHALL NOT attribute the mechanism only to an earlier test filter.

#### Scenario: The thesis explains ExcludedTest

- **WHEN** RQ6 or its discussion describes `ExcludedTest`
- **THEN** the description matches the complete parent-test exclusion predicate
- **AND** the discussion cites the `ExcludedTest` evidence rows
