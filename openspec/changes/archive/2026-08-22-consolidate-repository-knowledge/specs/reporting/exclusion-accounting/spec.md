## Purpose

Define how the real-world evaluation turns every pipeline outcome into citable, denominator-explicit
exclusion results without hiding new mechanisms in an existing bucket.

## ADDED Requirements

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

### Requirement: Filter decisions exclude non-filter producers

The filter-decision table SHALL include only decisions made by actual filtering stages. A generated
source removed because it does not compile SHALL be treated as a build failure even when its retained
record uses the filter-result storage shape.

#### Scenario: Generated source is quarantined
- **WHEN** compilation removes a generated source and records a rejection-shaped row
- **THEN** the outcome is classified as a build failure and is absent from per-filter decision totals

#### Scenario: Several filters reject one entity
- **WHEN** multiple filters evaluate and reject the same entity
- **THEN** each filter row records its decision, while the entity contributes only once to the
  exclusion outcome total

### Requirement: Pre-emission refusals remain visible

A generalization attempt refused before source creation SHALL remain part of the attempt denominator,
SHALL have no emitted-test lifecycle record, and SHALL be attributed to its stable refusal cause.

#### Scenario: Widening is refused
- **WHEN** a soundness gate rejects an attempted generalization before writing source
- **THEN** the report counts one attempt and one refusal, and counts no emitted or filter-adjudicated
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
filter-adjudicated tests, filter-passed tests, and final-usable tests. Cross-corpus comparisons SHALL
apply the same registered report definition and join project identity by repository root rather than
by database-local identifiers.

#### Scenario: Generalization success is quoted
- **WHEN** prose or a generated macro reports a generalization success rate
- **THEN** it identifies whether the denominator is attempts, emitted tests, or adjudicated tests

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
