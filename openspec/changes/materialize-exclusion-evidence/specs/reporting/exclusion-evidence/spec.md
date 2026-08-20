## Purpose

Publishes denominator-explicit, provenance-bearing RQ6 evidence from the accepted exclusion-accounting
semantics, so every reader-facing quantity and supported causal claim can be reproduced and reconciled.

## ADDED Requirements

### Requirement: Exclusion evidence materializes the accepted mechanism accounting

The registered real-world report SHALL emit a citable partition of included entities and every known
exclusion mechanism at each published entity level. The partition SHALL consume the accepted
exclusion-accounting semantics and SHALL NOT hide a mechanism inside an unlabeled residual bucket.

Each entity SHALL be counted exactly once at a given level. Mechanism counts SHALL reconcile to the
eligible population and to the report's collapsed reader-facing outcomes.

#### Scenario: Known mechanisms are present

- **WHEN** the corpus contains entities attributed to every declared exclusion mechanism
- **THEN** the report emits each mechanism separately with count, share, entity level, and provenance
- **AND** the corresponding collapsed totals reconcile

#### Scenario: An unknown producer appears

- **WHEN** an exclusion code, record shape, or producer has no accepted mapping
- **THEN** report generation fails naming the unknown evidence
- **AND** no catch-all category is published

#### Scenario: Quarantine shares a storage shape with filtering

- **WHEN** a build-quarantine result is stored through the same field used by filter decisions
- **THEN** producer semantics classify it as quarantine rather than filter rejection

### Requirement: The generalization funnel names every observed population

The report SHALL emit distinct counts for attempted, emitted, filter-adjudicated, filter-passed,
validated, reduced, and final-usable populations where those states are observed. Each exclusion SHALL
be attributed to its first failing gate exactly once.

A missing attempt record SHALL remain unknown rather than being inferred from a later failure. Final-use
counts MAY still be published when attempt history is incomplete, provided the limitation is explicit.

#### Scenario: An entity reaches final use

- **WHEN** an entity has evidence for every required stage through reduction
- **THEN** it contributes once to final usable and to each compatible upstream population

#### Scenario: An entity can fail more than one later condition

- **WHEN** its first observed failure occurs before other rejecting conditions
- **THEN** the funnel attributes it only to the first failure

#### Scenario: Attempt evidence is absent

- **WHEN** historical state does not prove that a stage ran
- **THEN** the report records unknown attempt state
- **AND** it does not present a derived failure stage as proof of execution

### Requirement: Every published quantity states identity and denominator

Each registered RQ6 metric SHALL carry a stable semantic key, raw value, unit, population, semantic
corpus id, source revision, and provenance. A rate SHALL also identify its denominator. Report
generation SHALL fail when a numerator and denominator belong to incompatible populations or when a
thesis-declared metric key is missing.

#### Scenario: A rate is published

- **WHEN** the report emits a share or percentage
- **THEN** its record names the numerator population and denominator key
- **AND** both resolve to the same corpus and compatible entity level

#### Scenario: A consumer requests a missing metric

- **WHEN** a declared thesis artifact refers to a metric key the finalized report does not emit
- **THEN** publication fails naming that key

### Requirement: The thesis-consumed RQ6 evidence surface is complete

The registered report SHALL materialize every RQ6 quantity declared for thesis consumption as a metric,
macro, or table cell. The set SHALL cover project, test, assertion, and generalization populations;
mechanism and funnel counts; filter and failure causes; assertion-kind and type composition;
class-level cascades; output and exception-model outcomes; symbolic-argument reach; and declared
controlled-comparison quantities.

The values SHALL derive from the normalized evidence relation or an explicitly registered audit input.
No thesis-consumed quantity may exist only as an ad hoc query or rendered prose literal.

#### Scenario: The consumer inventory is checked

- **WHEN** the finalized report run completes
- **THEN** every declared thesis metric and table key is present exactly once
- **AND** each carries its population, denominator where applicable, and provenance

#### Scenario: Two outputs report the same fact

- **WHEN** a macro and a table cell represent one semantic quantity
- **THEN** both derive from the same metric identity rather than recomputing it independently

### Requirement: Causal evidence is reproducible and scoped

A causal claim not already proved by persisted codes or focused executable fixtures SHALL use a
registered audit that records semantic corpus id, source revision, candidate or sampling rule, selected
entity identities, observations, labels, and reviewer rationale.

The report SHALL distinguish a persisted-mechanism result from a reviewer interpretation. It SHALL NOT
publish an unidentifiable source sample as current evidence.

#### Scenario: A reviewer explains a widening refusal

- **WHEN** the explanation goes beyond the persisted refusal code
- **THEN** the supporting audit names the reviewed entities, source revision, observations, and rationale

#### Scenario: An historical audit omitted identities

- **WHEN** its selected entities cannot be reconstructed
- **THEN** the audit is not used as current causal evidence

### Requirement: Every emitted result carries reproducible provenance

Every exclusion table, metric, macro, and audit summary SHALL carry or resolve to provenance that names
the semantic corpus id, source revision, query or audit definition, and dirty state. Publication SHALL
occur only after corpus-completeness and reconciliation checks pass.

#### Scenario: Corpus inputs disagree

- **WHEN** database identity, project count, derived-view revision, or checked corpus input disagrees
- **THEN** report generation fails before publishing a citable result

#### Scenario: A result is reproduced

- **WHEN** another reviewer uses the recorded corpus, revision, and query or audit definition
- **THEN** the reviewer can regenerate the same raw result and reconciliation checks
