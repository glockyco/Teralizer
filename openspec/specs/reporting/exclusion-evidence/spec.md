# Exclusion Evidence Specification

## Purpose

Publishes denominator-explicit, provenance-bearing RQ6 evidence from the accepted exclusion-accounting
semantics, so every reader-facing quantity and supported causal claim can be reproduced and reconciled.

## Requirements

### Requirement: Exclusion evidence materializes the accepted mechanism accounting

The registered real-world report SHALL emit a citable partition of included entities and every known
exclusion mechanism at each published entity level. One executable mapping SHALL own mechanism keys and
the reader-facing collapse. Separate typed relations SHALL preserve the lifecycle, assertion,
filter-result, and generated-generalization evidence available at their respective levels. They
SHALL NOT force absent evidence into one universal row shape or hide a mechanism inside an unlabeled
residual bucket.

Each entity SHALL be counted exactly once within a compatible relation and level. Mechanism counts SHALL
reconcile to the eligible population and to the report's collapsed reader-facing outcomes wherever the
required evidence exists.

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

The report SHALL emit distinct counts for attempted, emitted, filter-result-recorded, filter-passed,
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

Each registered RQ6 metric SHALL carry a stable semantic key, raw value, value kind, population,
optional denominator key, and the existing code-provenance reference. A rate SHALL identify its
denominator. Semantic corpus identity, repository-file input revisions, content identities, and dirty
state SHALL resolve through the owning report run's input snapshots and provenance manifest; the metric
SHALL NOT duplicate those run-level input facts. Report generation SHALL fail when a numerator and
denominator belong to incompatible populations or when a retained thesis-declared metric key is missing.

#### Scenario: A rate is published

- **WHEN** the report emits a share or percentage
- **THEN** its record names the numerator population and denominator key
- **AND** both resolve to the same corpus and compatible entity level

#### Scenario: A consumer requests a missing metric

- **WHEN** a declared thesis artifact refers to a metric key the finalized report does not emit
- **THEN** publication fails naming that key

### Requirement: The retained thesis RQ6 evidence surface is complete

The registered report SHALL materialize each quantity retained by the downstream thesis claim inventory
as a metric, macro, or semantically keyed table cell. The retained set SHALL cover the mechanism
partition, supported funnel populations, denominators, and only the comparison or causal quantities
needed by the final argument. Existing macros, table cells, and prose literals SHALL be treated as
candidate consumers, not as a requirement to preserve every historical scalar.

The values SHALL derive from the appropriate typed evidence relation or, when a surviving causal claim
requires it, an explicitly registered audit input. No retained thesis quantity may exist only as an ad
hoc query or rendered prose literal.

#### Scenario: The retained consumer inventory is checked

- **WHEN** the finalized report run completes
- **THEN** every retained thesis metric and table key is present exactly once
- **AND** each carries its population, denominator where applicable, and resolvable provenance

#### Scenario: A historical quantity is not retained

- **WHEN** the thesis claim inventory removes a macro, table cell, or prose quantity
- **THEN** this capability does not require a replacement metric solely to preserve that old output

#### Scenario: Two outputs report the same fact

- **WHEN** a macro and a table cell represent one retained semantic quantity
- **THEN** both derive from the same metric identity rather than recomputing it independently

### Requirement: Filtering retention is comparable only at the shared filtering boundary

The reporting system SHALL publish controlled and RepoReapers filtering-retention evidence as two corpus-local observations with the same entity type and boundary. Each observation SHALL count generalized tests that have a filtering result, partition that count into retained and excluded tests, and publish the retained share with the filtering total as its denominator.

The controlled observation SHALL derive its boundary from explicit filtering and generation-task evidence rather than from `generalization.is_included` alone. The RepoReapers observation SHALL use the accepted real-world generalization relation and its filtering result. For each corpus, retained plus excluded SHALL equal the filtering total.

The report SHALL preserve separate corpus identities, inputs, numerators, denominators, and provenance. It SHALL NOT combine the two observations into one effect size or treat either filtering denominator as the corpus's complete generalization-attempt or project population.

#### Scenario: Both corpus-local filtering observations are complete

- **WHEN** the controlled and RepoReapers inputs contain supported generalized tests with retained and excluded filtering results
- **THEN** the report emits filtering total, retained count, excluded count, and retained share for each corpus
- **AND** each retained share names its own corpus-local filtering total as denominator
- **AND** retained plus excluded equals that filtering total

#### Scenario: A controlled generated test lacks a filtering result

- **WHEN** controlled evidence shows that generalized test creation or its task failed before filtering produced a result
- **THEN** that test is excluded from the controlled filtering denominator
- **AND** it is not inferred to be retained or excluded from `generalization.is_included`

#### Scenario: Corpus-local results are interpreted together

- **WHEN** downstream RQ6 analysis compares the two retained shares
- **THEN** the evidence supports only the bounded finding that filtering retains a similar proportion in both settings
- **AND** it does not represent overall generalization success, project applicability, a paired-project effect, or a causal estimate

### Requirement: Filtering comparison artifacts use established thesis terms

Generated tables, metric labels, macro documentation, and handoff records SHALL describe this boundary using the established terms **filtering**, **filter results**, **retained**, **excluded**, and **generalized tests**. They SHALL NOT introduce a new reader-facing lifecycle term for the filtering step.

#### Scenario: Filtering comparison artifacts are rendered

- **WHEN** the registered report publishes the comparison and its aggregate macros
- **THEN** reader-facing labels use the established filtering vocabulary
- **AND** internal storage or implementation terms do not appear as replacement thesis terminology

### Requirement: Causal evidence is reproducible, scoped, and conditional

The claim inventory SHALL first resolve each retained causal explanation through persisted codes or
focused executable fixtures. If those sources close every retained explanation, the change SHALL record
that no qualitative audit is required and SHALL NOT create a placeholder audit input.

A retained causal explanation that still requires reviewer interpretation SHALL use one registered audit
for that bounded question. The audit SHALL record semantic corpus id, source revision, candidate or
sampling rule, selected entity identities, observations, labels, exclusions, and reviewer rationale.
The report SHALL distinguish a persisted-mechanism result from a reviewer interpretation. It SHALL NOT
publish an unidentifiable source sample as current evidence.

#### Scenario: Existing evidence closes the causal claims

- **WHEN** persisted codes and focused fixtures support every retained causal explanation
- **THEN** no qualitative audit input or summary is required

#### Scenario: A reviewer explains a widening refusal

- **WHEN** a retained explanation goes beyond the persisted refusal code and focused fixtures
- **THEN** the supporting audit names the reviewed entities, source revision, observations, and rationale

#### Scenario: An historical audit omitted identities

- **WHEN** its selected entities cannot be reconstructed
- **THEN** the audit is not used as current causal evidence

### Requirement: Every emitted result carries reproducible provenance

Every exclusion table, metric, macro, and conditional audit summary SHALL resolve through its owning
report and run manifest to the semantic corpus id, declared repository-file inputs, source identities,
query or audit definition, and dirty state. Code provenance SHALL remain attached through the existing
provenance reference. Publication SHALL occur only after corpus-completeness and reconciliation checks
pass.

#### Scenario: Corpus inputs disagree

- **WHEN** database identity, project count, derived-view revision, or checked corpus input disagrees
- **THEN** report generation fails before publishing a citable result

#### Scenario: A result is reproduced

- **WHEN** another reviewer uses the recorded corpus, revision, and query or audit definition
- **THEN** the reviewer can regenerate the same raw result and reconciliation checks

### Requirement: Exclusion evidence uses stable semantic identities

Every published filter boundary, mechanism, metric, row, label, aggregate macro, and provenance entry SHALL use one semantic identity consistently. A retired identity SHALL be removed from every producer and consumer without a compatibility alias.

#### Scenario: A filter-result boundary is published

- **WHEN** reports render the population that has a recorded filter result
- **THEN** code, generated artifacts, macros, provenance, tests, and accepted contracts use the same filter-result identity
- **AND** no separate review-stage synonym remains

#### Scenario: An inherited test cannot be generalized safely

- **WHEN** the generalized test would retain a superclass type variable or private superclass member that cannot be inlined
- **THEN** the exclusion evidence names the inherited-test inlining limit
- **AND** it does not place the test in a generic unsupported-capability bucket

### Requirement: Published filter evidence is internally consistent

A published filter decision SHALL agree with the normalized evidence on which the decision depends. A method-under-test observation SHALL be resolved only when its declaration is stably addressable for generalization. Parameter capability evidence SHALL distinguish declared parameter types from the actual generated inputs persisted for the selected call.

#### Scenario: A selected MUT declaration is unpathable

- **WHEN** a selected call belongs to an anonymous or local source declaration without a stable generalization path
- **THEN** evidence records a characterization-only unpathable-source outcome
- **AND** reporting does not count it as a resolved pick with missing persistence

#### Scenario: A declared parameter is supported but no generated input exists

- **WHEN** a selected call supplies every supported declared parameter with a constant or `null`
- **THEN** parameter filtering may reject the assertion because its persisted generated-input list is empty
- **AND** reporting does not classify that rejection as contradictory evidence

### Requirement: Failure attribution names the failed operation

Project and mechanism evidence SHALL attribute a retained failure to the operation that failed rather than to an earlier tool whose output was being consumed.

#### Scenario: PIT output exists but coverage import fails

- **WHEN** PIT produced its report and the pipeline later fails while importing or persisting that report
- **THEN** exclusion evidence names report import or persistence as the failed operation
- **AND** it does not classify the outcome as PIT execution failure

### Requirement: Reader-facing mechanisms exclude diagnostic-only checks

A filter that only records `DEFER` evidence and cannot exclude an entity SHALL remain diagnostic evidence. It SHALL NOT enter reader-facing exclusion partitions, thesis-facing mechanisms, or limitation counts.

#### Scenario: A diagnostic filter records technical shape evidence

- **WHEN** a filter records a non-excluding `DEFER` result
- **THEN** audit output may retain that result
- **AND** published exclusion evidence does not present it as an exclusion mechanism

### Requirement: Corrected evidence preserves the measured run

Corrected reports and generated artifacts SHALL be derived from the preserved first-run database and its matching run root. The correction SHALL NOT rerun a project or corpus to replace measured outcomes.

#### Scenario: Corrected evidence is regenerated

- **WHEN** the semantic corrections are ready for publication
- **THEN** the registered reports regenerate affected artifacts from the preserved measurement record
- **AND** the manifest and provenance identify the exact source revision and inputs

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
