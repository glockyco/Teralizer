# exclusion-accounting Specification

## Purpose

Define how the real-world evaluation turns every pipeline outcome into citable, denominator-explicit
exclusion results without hiding new mechanisms in an existing bucket.

## Requirements

### Requirement: Exclusion classification is total and exclusive

For each reported entity level, the report SHALL classify every entity exactly once as included or
under one known exclusion mechanism. The reader-facing outcome columns SHALL be an explicit collapse
of those mechanisms and SHALL sum to the level total.

For every filter population, the report SHALL publish mutually exclusive and exhaustive verdict
membership. For `ParameterType`, the complete verdict partition SHALL include accepted, deferred,
and rejected decisions. The historical persisted `ParameterType` rejection population SHALL have
exactly four mutually exclusive causes: unsupported declared input types, zero declared parameters,
missing declaration evidence, and residual derivation gaps. Future source filtering SHALL defer when
selected-MUT declaration evidence is absent, but that behavior SHALL NOT rewrite a historical
persisted `Reject` into a `Defer`. The report SHALL fail before publication when the verdict partition
does not cover its filter population exactly, when the four historical rejection causes do not cover
the persisted rejection population exactly, or when any entity occurs in more than one partition
member.

#### Scenario: Known mechanisms are reported
- **WHEN** a report contains filter rejections, pre-emission refusals, unsupported capabilities,
  build quarantines, or task failures
- **THEN** each entity contributes once to its mechanism and once to the corresponding reader-facing
  outcome

#### Scenario: Unknown mechanism appears
- **WHEN** an exclusion code, record shape, or filter-result producer is not declared by the
  classification
- **THEN** report generation fails and names the unclassified level instead of assigning the entity
  through a catch-all branch

#### Scenario: A filter partition is incomplete or overlapping

- **WHEN** a filter population has an entity with no partition member or with more than one partition
  member
- **THEN** report materialization fails and identifies the filter and entity level

#### Scenario: ParameterType evidence classes are complete

- **WHEN** the registered report materializes the `ParameterType` detail
- **THEN** each entity occurs once in `Accept`, `Defer`, or `Reject`
- **AND** each historical persisted `Reject` occurs once in unsupported declared input types, zero
  declared parameters, missing declaration evidence, or residual derivation gaps
- **AND** the complete verdict total equals the `ParameterType` evaluated population
- **AND** the four historical rejection-cause counts equal the persisted `ParameterType` rejection
  population

### Requirement: Filter classification follows proactive exclusion behavior

The report SHALL classify a decision as filtering when it proactively excludes a candidate to
prevent an unsupported, unsafe, or predictably failing downstream operation. The classification
SHALL NOT depend on the pipeline stage, producer class, or persistence shape.

The filter-detail table SHALL use the columns `Level`, `Filter Name`, `Evaluated`, `Accept`, `Defer`,
and `Reject`. Filter names SHALL use the established PascalCase form and SHALL omit only an
implementation `Filter` suffix. Each row SHALL derive its applicable population and verdicts from
persisted evidence. `Evaluated` SHALL equal `Accept` plus `Defer` plus `Reject`.

For `ParameterType`, source filtering SHALL decide from the recipe-derived generalizable inputs. It
SHALL accept when at least one supported generalizable input exists and SHALL reject when none exists.
Selected-MUT declaration telemetry SHALL provide report classification evidence and dependency
evidence, but SHALL NOT replace the recipe-derived input evidence. Tested parameters SHALL represent
recipe-derived generalizable inputs. Call arguments SHALL be treated as derived inputs only when
derivation ran and otherwise as raw call-site evidence. Neither representation SHALL be treated as the
complete selected-MUT signature. If the declaration evidence required to establish that the selected
MUT is classifiable is absent, source filtering SHALL defer before evaluating recipe-derived input
support. Declaration support SHALL NOT decide acceptance. Recipe-derived evidence SHALL NOT override
or erase this dependency on declaration evidence. For a persisted `Reject` with declaration evidence,
reporting SHALL classify zero declared parameters when the declaration has zero parameters,
unsupported declared input types when no declared type supports generated inputs, and residual
derivation otherwise. For a historical persisted `Reject` whose present canonical observation has an
entirely null selected-MUT declaration tuple, reporting SHALL classify missing declaration evidence,
whatever resolution status or no-pick reason it records. A partially null tuple SHALL fail report
materialization as invalid evidence. A supported recipe-derived input SHALL preserve a persisted
`Accept` even when the declaration has zero or unsupported parameters.

The table SHALL group rows by test, assertion, and generalization level. It SHALL insert a midrule
only when the entity level changes. It SHALL NOT insert rules between test-filter rounds or around
`InheritedTestMethod`. Within each level, rows SHALL appear by descending `Evaluated`, descending
`Reject`, then ascending filter name.

#### Scenario: Entity levels are separated
- **WHEN** the filter-detail table contains test, assertion, and generalization decisions
- **THEN** midrules separate the three entity levels
- **AND** no midrule separates test decisions with different evaluated populations

#### Scenario: Decisions within a level are ranked
- **WHEN** the report renders one entity level
- **THEN** rows appear by descending `Evaluated`
- **AND** equal evaluated populations appear by descending `Reject`
- **AND** equal rejection counts appear by ascending filter name

#### Scenario: Pre-emission checks filter generalization attempts
- **WHEN** `SeedSpecConsistency` or `WideningLicense` proactively rejects a generalization attempt
  before source emission
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
- **THEN** rejected `SeedSpecConsistency`, `WideningLicense`, and `NonPassingTest` populations are
  mutually exclusive
- **AND** their rejection counts sum to the generalization-level `Filtering` outcome

#### Scenario: Proactive decision evidence is incomplete
- **WHEN** persisted evidence cannot determine whether a candidate reached or passed a proactive
  filter
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

#### Scenario: A selected MUT has only unsupported declared input types

- **WHEN** selected-MUT telemetry identifies a declaration with one or more input parameters, none of
  their declared types support generated inputs, and no supported recipe-derived input exists
- **THEN** source filtering records a `ParameterType` `Reject` with the existing
  `NO_GENERALIZABLE_PARAMETERS` reason
- **AND** the registered report classifies that rejection as unsupported declared input types
- **AND** reporting does not use recipe-derived input-list emptiness alone as the evidence for that
  cause

#### Scenario: A selected MUT declares zero parameters without recipe input

- **WHEN** selected-MUT telemetry identifies a declaration with zero input parameters and no supported
  recipe-derived generalizable input exists
- **THEN** source filtering records a `ParameterType` `Reject` with the existing
  `NO_GENERALIZABLE_PARAMETERS` reason
- **AND** the registered report classifies that rejection as zero declared parameters
- **AND** reporting does not label the entity as having unsupported declared input types

#### Scenario: Selected-MUT declaration evidence is missing for future filtering

- **WHEN** the assertion has no canonical resolution observation, or its observation has no complete
  selected-MUT declaration tuple
- **THEN** future source filtering records a `ParameterType` `Defer` before checking recipe-derived
  input support
- **AND** the deferral does not depend on the resolution status or no-pick reason
- **AND** the missing declaration evidence is not classified as an unsupported-parameter cause

#### Scenario: Historical rejection lacks declaration evidence

- **WHEN** a historical persisted `ParameterType` `Reject` has a present resolution observation whose
  declaration tuple is entirely null
- **THEN** the registered report retains the `Reject` verdict
- **AND** it assigns the rejection to the missing declaration evidence category
- **AND** a successful resolution status with a null declaration tuple does not remove the rejection
  from that category
- **AND** it does not assign the rejection to unsupported declared input types

#### Scenario: Recipe derivation supplies no generalizable input

- **WHEN** selected-MUT telemetry identifies a declaration with at least one supported declared type,
  but no supported recipe-derived generalizable input exists
- **THEN** source filtering records a `ParameterType` `Reject` with the existing
  `NO_GENERALIZABLE_PARAMETERS` reason
- **AND** the registered report classifies that rejection as a residual derivation gap
- **AND** reporting does not relabel the case as zero declared parameters or unsupported declared
  input types

#### Scenario: A receiver or constructor supplies supported recipe input

- **WHEN** selected-MUT telemetry identifies zero declared parameters or unsupported declared types,
  but recipe-derived inputs contain at least one supported generalizable input from a receiver or
  constructor lift
- **THEN** source filtering records a `ParameterType` `Accept`
- **AND** reporting does not classify the entity as a zero-parameter or unsupported-parameter
  rejection

### Requirement: Test filtering populations reconcile from persisted evidence

The registered report SHALL publish provenance-backed test-flow counts for identified tests,
inherited-method screening, pre-filter failures, both test-filter rounds, overlapping first-round
rejections, and intervening failures. It SHALL derive each population from persisted set membership
and SHALL NOT add overlapping rejection counts.

#### Scenario: The first test-filter round is reached
- **WHEN** identified tests include inherited methods that cannot be flattened and tests that fail
  before filtering
- **THEN** the first-round population equals identified tests minus inherited-method rejections and
  pre-filter failures
- **AND** accepted inherited methods remain eligible for the first round

#### Scenario: The second test-filter round is reached
- **WHEN** first-round filters can reject the same test and a test can fail between rounds
- **THEN** the second-round population equals the first-round population minus the union of
  first-round rejections and intervening failures
- **AND** the overlap and intervening-failure counts remain registered evidence

### Requirement: Exclusion tables preserve semantic layout

Entity summary tables SHALL center the `Excluded` spanner over `Filtering` and `Failures`.
Filter-detail tables SHALL render a midrule at each entity-level boundary. A consuming document MAY
apply an explicit local compact density in its table wrapper, but it SHALL preserve readable text,
semantic rules, and the adjacent summary-table source boundary.

#### Scenario: The paired RQ6 exclusion tables are rendered
- **WHEN** the generated filter-detail table needs compact density to remain with its summary table
- **THEN** the consuming document applies the density through its manuscript-owned table wrapper
- **AND** it does not use negative spacing, global float changes, or generated-file edits

### Requirement: Excluded-test explanations match the executable predicate

A reader-facing explanation of `ExcludedTest` SHALL state that it rejects an assertion whose test
has already been excluded. It SHALL NOT attribute the mechanism only to an earlier test filter.

#### Scenario: The thesis explains ExcludedTest
- **WHEN** RQ6 or its discussion describes `ExcludedTest`
- **THEN** the description matches the complete parent-test exclusion predicate
- **AND** the discussion cites the `ExcludedTest` evidence rows

### Requirement: Pre-emission refusals remain visible

A generalization attempt refused before source creation SHALL remain part of the attempt denominator,
SHALL have no emitted-test lifecycle record, and SHALL be attributed to its stable refusal cause.

#### Scenario: Widening is refused
- **WHEN** a soundness gate rejects an attempted generalization before writing source
- **THEN** the report counts one attempt and one refusal, and counts no emitted or filter-result-recorded
  generalized test for it

### Requirement: Lifecycle fields define generalized-test yield

The report SHALL use the persisted generalized-test lifecycle to distinguish attempted, emitted,
filter-passed, and final-usable outcomes. A generic inclusion flag SHALL NOT be interpreted as proof
that a generated test completed a later stage.

#### Scenario: Generated test passes its filter but reduction never completes
- **WHEN** the lifecycle records a filter-passed generated test without successful final reduction
- **THEN** the report includes it in generalization success and excludes it from end-to-end final
  usability

#### Scenario: Generalized source was never emitted
- **WHEN** an attempt was refused before source creation
- **THEN** no lifecycle row is inferred or synthesized for that attempt

### Requirement: Every quoted result identifies its measure and denominator

A published result SHALL distinguish project eligibility, generalization attempts, emitted tests,
filter-result-recorded tests, filter-passed tests, and final-usable tests. Cross-corpus comparisons SHALL
apply the same registered report definition and join project identity by repository root rather than
by database-local identifiers.

#### Scenario: Generalization success is quoted
- **WHEN** prose or a generated macro reports a generalization success rate
- **THEN** it identifies whether the denominator is attempts, emitted tests, or tests with a recorded filter result

#### Scenario: Corpora are compared
- **WHEN** the same measure is compared across two corpus databases
- **THEN** both values come from the same registered report implementation and corresponding projects
  are matched by repository root

### Requirement: Project eligibility is separated from downstream attrition

Only failures before successful original-project setup and build SHALL remove a project from the
eligible denominator. Failures in test analysis, generalization, validation, or reduction SHALL be
reported inside the eligible-project funnel.

#### Scenario: Downstream analysis fails
- **WHEN** a project builds successfully and later fails during analysis
- **THEN** the project remains in the eligible denominator and appears as downstream attrition

### Requirement: Citable output is provenance bearing

Citable RQ6 values SHALL be emitted by the registered report from one read-only consistent corpus
snapshot after corpus-completeness checks pass. Ad hoc database queries SHALL be treated as audit or
diagnostic evidence, not publication output.

The registered report SHALL reconstruct historical `ParameterType` categories from persisted
selected-MUT observation telemetry in that same snapshot. That telemetry SHALL include the count of
declared parameters, support evidence for the declared types, and the resolved declared parameter
types. An entirely null declaration tuple in a present resolution observation SHALL be treated as
missing declaration evidence, not as evidence of unsupported parameters. An absent or duplicate
observation, a partially null declaration tuple, a negative declaration count, a non-array resolved
type list, a type-list cardinality that disagrees with the count, or a zero count with true support
SHALL fail report materialization rather than become a classification. The report SHALL preserve the
historical corpus rows and SHALL NOT rewrite corpus data or rerun projects. The citable detail SHALL
retain the source observation provenance needed to distinguish declaration evidence from
recipe-derived evidence. Resolution status and no-pick reason SHALL NOT override the declaration
tuple. A present observation that records a pick with an entirely null declaration tuple SHALL be
treated as missing declaration evidence, not as contradictory telemetry.

#### Scenario: Corpus inputs disagree
- **WHEN** the database projects, project configurations, completion markers, and attempt ledger do
  not describe the same complete corpus
- **THEN** report materialization fails before emitting citable artifacts

#### Scenario: Citable ParameterType evidence uses one snapshot

- **WHEN** a registered report publishes `ParameterType` detail and its collapsed exclusion outcome
- **THEN** both use persisted selected-MUT telemetry read from one read-only consistent corpus snapshot
- **AND** the report records provenance for that snapshot and the source observation

#### Scenario: Historical ParameterType evidence is reconstructed

- **WHEN** a historical registered report is materialized from an immutable corpus database
- **THEN** it derives unsupported declared input types, zero declared parameters, missing declaration
  evidence, and residual derivation gaps from persisted observation telemetry
- **AND** it retains the persisted `Reject` verdict for every historical rejection
- **AND** it does not rewrite corpus rows or rerun any project

#### Scenario: ParameterType telemetry is absent or contradictory

- **WHEN** a historical `ParameterType` rejection has no unique corresponding observation or has a
  partially null declaration tuple, invalid count, invalid resolved-type array, mismatched type-list
  cardinality, or true support for a zero-parameter declaration
- **THEN** report materialization fails and identifies the inconsistent observation population
- **AND** it does not assign the rejection to missing declaration evidence or another catch-all cause
