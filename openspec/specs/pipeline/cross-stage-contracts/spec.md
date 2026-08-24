# cross-stage-contracts Specification

## Purpose

Define the observable contracts that let independently scheduled pipeline stages exchange persisted
artifacts without changing the meaning of a generalized test.

## Requirements

### Requirement: Pipeline phases execute in canonical order

When enabled, generation SHALL complete before generalization, and generalization SHALL complete
before reduction. Each phase SHALL drain its scheduled work before the next phase starts.

#### Scenario: Reduction fails
- **WHEN** reduction fails after generalization has produced validated outputs
- **THEN** those generalization outputs remain recorded and are not removed by the reduction failure

#### Scenario: Phase is disabled
- **WHEN** a phase is disabled and the next enabled phase has all required persisted inputs
- **THEN** the pipeline runs the next enabled phase without rerunning the disabled phase

### Requirement: Resumed work preserves project identity

A later invocation SHALL attach to persisted work only when the project root and effective
configuration identify the same project state. Missing required phase artifacts or an identity
mismatch SHALL fail before dependent work is scheduled.

#### Scenario: Configuration changed before resume
- **WHEN** a caller tries to resume a persisted workspace with a different effective configuration
- **THEN** the pipeline rejects the attachment instead of combining states from two runs

#### Scenario: Required artifact is missing
- **WHEN** an enabled phase depends on an artifact that the persisted workspace does not contain
- **THEN** the phase fails its precondition without scheduling partial downstream work

### Requirement: Instrumentation and generation consume one recipe

The oracle expression, generated input sites, and oracle type for an assertion SHALL be derived once,
persisted together, and consumed unchanged by both symbolic instrumentation and generalized-test
creation. A consumer SHALL report a typed refusal when the recipe cannot be consumed.

#### Scenario: Expression-shaped assertion is generalized
- **WHEN** the pipeline instruments and later generalizes an assertion whose oracle is an expression
- **THEN** both outputs use the same oracle expression and the same lifted input sites

#### Scenario: Persisted recipe is unsupported
- **WHEN** either consumer cannot represent a persisted recipe element
- **THEN** the assertion or generalization receives a typed exclusion instead of a silently altered
  oracle

### Requirement: Widening is evidence bounded

A generated input SHALL range beyond its captured seed only when the extracted output relation and
path condition justify applying the oracle to that input. A case without sufficient evidence SHALL
produce a typed pre-emission refusal and no generalized source.

#### Scenario: Output relation and path cover the generated inputs
- **WHEN** extraction provides a supported output relation and the path condition covers every
  widened parameter
- **THEN** generalized-test creation may widen those parameters

#### Scenario: Oracle evidence is insufficient
- **WHEN** the output relation is unavailable or the path condition does not cover a widened
  parameter under the applicable widening rule
- **THEN** generalized-test creation records a typed refusal and emits no generalized test

### Requirement: Generated code does not mutate the target build definition

Pipeline build changes SHALL be applied to derived build files. The target project's original build
file SHALL remain unchanged, and original-test execution SHALL continue to use the project's native
test-runner configuration.

#### Scenario: Dependencies are injected
- **WHEN** the pipeline adds analysis or generated-test dependencies
- **THEN** it updates a derived build file and leaves the target build file byte-unchanged

### Requirement: Generated executions are reproducible

A generated property SHALL exercise the captured concrete tuple first and SHALL use a fixed random
seed with shrinking disabled. Later random tuples SHALL not repeat a tuple already emitted by the
same property execution.

#### Scenario: Property execution starts
- **WHEN** a generalized property is run
- **THEN** its first tuple is the concrete tuple captured from the developer-written test

### Requirement: Failures have bounded downstream scope

A variant-specific task failure SHALL cancel only dependent work for that variant. A shared task
failure MAY cancel project-wide dependent work but SHALL NOT erase completed outputs from an earlier
drained phase.

#### Scenario: One generalization variant fails
- **WHEN** a task attached to one variant fails
- **THEN** sibling variants remain eligible to complete

### Requirement: MUT resolution and filtering share one persisted observation

Method-under-test resolution SHALL classify a selected call as resolved only when its source declaration has the stable paths and identity required for generalization. It SHALL persist that call and every required tested-method field as one coherent observation before assertion filtering consumes it. Filtering SHALL derive missing-value and type decisions from the persisted generalization inputs rather than from declaration capability alone.

#### Scenario: Resolution selects a source method

- **WHEN** the resolver selects a call whose source declaration and signature are available
- **THEN** the selected call identity, declaration identity, parameter types, and return type are persisted together
- **AND** downstream filtering observes the same method shape

#### Scenario: A selected source declaration has no stable path

- **WHEN** the selected call belongs to an anonymous or local source class whose declaration cannot be addressed for generalization
- **THEN** resolution records a characterization-only outcome with an explicit unpathable-source reason
- **AND** it does not publish the pick as a resolved generalization-grade method

#### Scenario: Filtering evaluates parameter support

- **WHEN** assertion filtering evaluates the selected call's generated inputs
- **THEN** its decision uses the normalized persisted generalization parameters
- **AND** a supported declared parameter supplied only by a constant or `null` does not count as a generated input
