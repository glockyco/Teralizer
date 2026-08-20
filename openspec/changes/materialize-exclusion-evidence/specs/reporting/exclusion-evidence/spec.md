## Purpose

Publish denominator-explicit exclusion evidence that preserves mechanism distinctions, reconciles to
the corpus, and supports every reader-facing causal claim with reproducible provenance.

## ADDED Requirements

### Requirement: Exclusion evidence preserves mechanism identity

The registered real-world exclusion report SHALL classify every included or excluded entity exactly
once as included, filter rejection, pre-emission refusal, unsupported capability, build quarantine, or
task failure. The mechanism-level counts SHALL sum to the eligible population at each reported entity
level.

#### Scenario: Known mechanisms are reported
- **WHEN** the corpus contains all five exclusion mechanisms
- **THEN** the report emits a citable mechanism partition by entity level
- **AND** no mechanism is hidden inside an unlabelled residual

#### Scenario: A new exclusion producer appears
- **WHEN** an exclusion code, record shape, or decision producer is not declared by the classifier
- **THEN** report materialization fails before publishing artifacts
- **AND** the failure identifies the unclassified producer or level

### Requirement: Reader-facing outcomes use an explicit semantic collapse

Reader-facing exclusion results SHALL collapse filter rejection, pre-emission refusal, and unsupported
capability into filtering. They SHALL collapse build quarantine and task failure into failures. The
uncollapsed mechanism partition SHALL remain available as supporting evidence.

#### Scenario: A generalized test is refused before emission
- **WHEN** an attempt fails a declared pre-emission gate and writes no lifecycle row
- **THEN** its reader-facing outcome is filtering
- **AND** its mechanism remains pre-emission refusal in supporting evidence

#### Scenario: Generated source fails compilation quarantine
- **WHEN** generated source is rejected by the build validator
- **THEN** its reader-facing outcome is failure even if the persistence channel resembles a filter
  decision

#### Scenario: A test shape is unsupported
- **WHEN** a typed capability gate excludes a test before generalization
- **THEN** its reader-facing outcome is filtering
- **AND** it remains distinguishable from a filter rejection

### Requirement: Generalization yield is denominator-explicit

The report SHALL publish the counts needed to reconstruct this ordered funnel: attempts, seed-gate
refusals, widening refusals, emitted generalized tests, failures before filtering, build quarantines,
filter-adjudicated generalized tests, filter-passed generalized tests, downstream attrition, and
final-usable generalized tests.

The report SHALL distinguish at least filter-passed per attempt, filter-passed per emitted test,
filter-passed per filter-adjudicated test, final usable per filter-passed test, and final usable per
attempt. A rate SHALL name its denominator in its key or adjacent label.

#### Scenario: A validator writes a rejection through filter storage
- **WHEN** a build quarantine has a persisted filter-shaped row
- **THEN** it is excluded from the filter-adjudicated denominator
- **AND** it is counted once as build quarantine

#### Scenario: An attempt is refused before test emission
- **WHEN** the first failing pre-emission gate rejects an attempt
- **THEN** the attempt contributes once to that gate's refusal count
- **AND** it does not contribute to emitted, adjudicated, or filter-passed counts

#### Scenario: A filter-passed test fails downstream
- **WHEN** a generalized test passes its filter but does not complete the required downstream stages
- **THEN** it contributes to filter-passed and downstream-attrition counts
- **AND** it does not contribute to final-usable count

### Requirement: Funnel and mechanism outputs reconcile

Materialization SHALL fail unless the funnel, mechanism partition, persisted refusal taxonomy, and
final-use outcome reconcile over the same corpus, variant, entity population, and report run.

#### Scenario: Funnel totals disagree with the mechanism table
- **WHEN** any stage identity or arithmetic invariant differs across the two outputs
- **THEN** no citable report, metric manifest, or publication artifact is emitted
- **AND** the diagnostic names the failed identity

#### Scenario: Corpus inputs differ between outputs
- **WHEN** two sections resolve different corpus identities or variants
- **THEN** report materialization fails rather than presenting them as one result

### Requirement: Citable results carry report provenance

Every published count, rate, mechanism partition, and funnel value SHALL carry the registered report's
corpus provenance and source provenance. Ad hoc database output SHALL be treated as diagnostic evidence
and SHALL NOT be a publication source.

#### Scenario: A thesis macro consumes a denominator
- **WHEN** a downstream consumer declares a funnel count or rate
- **THEN** the publication manifest traces it to the registered report metric and consistent corpus
  snapshot

#### Scenario: Supporting evidence is not declared by a consumer
- **WHEN** the report emits a mechanism or audit artifact that no publisher declares
- **THEN** it remains in the generator build output
- **AND** it is not copied to a consumer repository merely because it exists

### Requirement: Causal claims use appropriate evidence

Persisted typed refusal codes and controlled fixtures MAY support claims about immediate gate decisions
and executable mechanisms. A claim about deeper cause, prevalence within a heterogeneous cause, or
source-level context SHALL require a reproducible audit that retains its population, deterministic
selection, selected entity identifiers, corpus identity, source revision, observations, labels, and
review rationale.

#### Scenario: Report explains the immediate refusal distribution
- **WHEN** typed refusal codes partition all refused attempts
- **THEN** the report may publish their counts and shares without a manual source audit

#### Scenario: Report claims why concrete output arose
- **WHEN** persisted telemetry does not determine the source-level cause
- **THEN** the claim is omitted unless a reproducible audit supplies the missing evidence

#### Scenario: An earlier random audit retained no entity identities
- **WHEN** the report encounters conclusions from that audit
- **THEN** it does not publish them as current evidence

### Requirement: Lifecycle evidence does not invent attempted stages

A derived failure stage SHALL NOT be presented as proof that its stage ran unless an independent
attempt record exists. Final-use reporting SHALL remain valid when attempted-stage state is incomplete,
and any known attribution defect SHALL remain visible as a failing invariant or declared limitation.

#### Scenario: A failure stage has no matching task attempt
- **WHEN** a lifecycle record names a failure stage but no task record proves that stage ran
- **THEN** the report excludes attempted-stage wording for that record
- **AND** the integrity check continues to expose the inconsistency
