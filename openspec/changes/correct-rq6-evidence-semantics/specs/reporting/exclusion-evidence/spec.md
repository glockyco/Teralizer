## ADDED Requirements

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
