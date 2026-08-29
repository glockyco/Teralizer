# exclusion-accounting Specification

## Purpose

Define how the real-world evaluation turns every pipeline outcome into citable, denominator-explicit
exclusion results without hiding new mechanisms in an existing bucket.

## Requirements

### Requirement: Exclusion classification is total and exclusive

For each reported entity level, the report SHALL classify every entity exactly once as included or
under one known exclusion mechanism. The reader-facing outcome columns SHALL be an explicit collapse
of those mechanisms and SHALL sum to the level total.

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

### Requirement: Filter classification follows proactive exclusion behavior

The report SHALL classify a decision as filtering when it proactively excludes a candidate to
prevent an unsupported, unsafe, or predictably failing downstream operation. The classification
SHALL NOT depend on the pipeline stage, producer class, or persistence shape.

The filter-detail table SHALL use the columns `Level`, `Filter Name`, `Evaluated`, `Accept`, `Defer`,
and `Reject`. Filter names SHALL use the established PascalCase form and SHALL omit only an
implementation `Filter` suffix. Each row SHALL derive its applicable population and verdicts from
persisted evidence. `Evaluated` SHALL equal `Accept` plus `Defer` plus `Reject`.

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
Filter-detail tables SHALL render a midrule at each entity-level boundary. A long filter-detail table
MAY use an explicit local compact density, but it SHALL preserve readable text, semantic rules, and
the adjacent summary-table source boundary.

#### Scenario: The paired RQ6 exclusion tables are rendered
- **WHEN** the generated filter-detail table needs compact density to remain with its summary table
- **THEN** the renderer applies the density through its table-style contract
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

#### Scenario: Corpus inputs disagree
- **WHEN** the database projects, project configurations, completion markers, and attempt ledger do
  not describe the same complete corpus
- **THEN** report materialization fails before emitting citable artifacts
