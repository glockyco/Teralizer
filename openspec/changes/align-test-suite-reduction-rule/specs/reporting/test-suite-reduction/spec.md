## Purpose

Defines how reduction reports select mutation-useful generalized tests and determine whether retained generalized tests completely replace an original test.

## ADDED Requirements

### Requirement: Retained generalized tests contribute observed mutation detection

A reduction report SHALL retain a generalized test only when that test detects at least one mutant that the selected original-suite baseline does not detect. Selection SHALL be evaluated independently for each project, baseline, and generalization variant.

#### Scenario: Generalized test detects a new mutant

- **WHEN** a generalized test kills a mutant that the selected original-suite baseline does not detect
- **THEN** the reduction report retains that generalized test
- **AND** its source assertion is represented in the retained generalized suite

#### Scenario: Generalized test adds no detected mutant

- **WHEN** a generalized test detects no mutant outside those detected by the selected original-suite baseline
- **THEN** the reduction report does not retain that generalized test
- **AND** its source assertion is not represented by that test

### Requirement: Original-test replacement requires complete retained assertion representation

A reduction report SHALL count an original test as removable only when the test has at least one recorded assertion and every recorded assertion identity is represented by a retained generalized test for the same project and variant. It SHALL count each removable original test once, irrespective of its assertion count or the number of mutants detected by its retained generalized tests.

#### Scenario: Retained generalization replaces a single-assertion test

- **WHEN** an original test has one recorded assertion
- **AND** a retained generalized test represents that assertion
- **THEN** the report counts the original test as removable once

#### Scenario: Retained generalizations replace every assertion in a multi-assertion test

- **WHEN** an original test has multiple recorded assertions
- **AND** retained generalized tests represent every assertion identity
- **THEN** the report counts the original test as removable once

#### Scenario: A multi-assertion test is only partially represented

- **WHEN** at least one recorded assertion in an original test has no retained generalized test
- **THEN** the report preserves the original test
- **AND** it still retains any mutation-useful generalized tests for represented assertions

#### Scenario: A test has no recorded assertion

- **WHEN** an original test has no recorded assertion
- **THEN** the report does not remove it by vacuous coverage

### Requirement: Replacement costs use original-test identity

For every removable original test, the reduction report SHALL subtract that original test's count, source lines, and measured runtime exactly once. Generalized-test additions SHALL remain the costs of the retained generalized tests.

#### Scenario: Several retained generalizations replace one original test

- **WHEN** several retained generalized tests represent the assertions of one removable original test
- **THEN** the report adds every retained generalized test
- **AND** subtracts the source original's count, lines, and runtime once
