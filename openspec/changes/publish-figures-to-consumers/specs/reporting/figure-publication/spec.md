## Purpose

Governs how a figure declared by a report reaches a repository that prints it: which formats the
generator emits, how a consumer states which figures it takes and under which names, and what happens
when the declaration and the emitted set disagree.

## ADDED Requirements

### Requirement: Every declared figure is emitted in a print format and a screen format

For each figure a report declares, a report run with the figure target SHALL write a vector PDF and a
raster PNG. Neither format SHALL be conditional on a publish destination being supplied.

The PDF SHALL be written with the print settings the paper style declares, and SHALL NOT override
them with values that degrade print quality.

Both formats SHALL carry metadata naming the report and the commit that produced them, expressed in
the keys the target format defines.

#### Scenario: A report run emits both formats

- **WHEN** a report with figures runs with the figure target
- **THEN** a PDF and a PNG exist for every figure the report declares
- **AND** each carries the report id and the producing commit

#### Scenario: A format rejects a metadata key

- **WHEN** a metadata key is not defined for the output format
- **THEN** the renderer uses the key that format defines for the same purpose
- **AND** the run does not warn or fail on an unknown key

### Requirement: The consumer declares which figures it takes

A consuming repository SHALL declare the figures it takes and the path each figure is written to. The
generator SHALL NOT derive a consumer's file name from the figure key, and SHALL NOT deliver a figure
the consumer has not declared.

A declaration SHALL be resolvable to a path inside the consuming repository. Two consumers MAY declare
different names for the same figure.

#### Scenario: A figure has no consumer

- **WHEN** a report declares a figure that no consumer declares
- **THEN** the figure is emitted into the build tree
- **AND** it is not delivered to any consuming repository

#### Scenario: Two consumers name one figure differently

- **WHEN** two consuming repositories declare the same figure under different file names
- **THEN** each receives the figure under the name it declared

#### Scenario: Publishing without a declaration

- **WHEN** a publish destination supplies no figure declaration
- **THEN** no figure is delivered
- **AND** the tables and data the destination expects are still published

### Requirement: A declaration that disagrees with the emitted set fails the publish

Publishing SHALL fail when a declared figure is not emitted by any report in the run, and when a
declared path resolves outside the consuming repository.

A failure SHALL name the figure and the disagreement. Publishing SHALL NOT deliver a partial figure
set after detecting one.

#### Scenario: A declared figure does not exist

- **WHEN** a consumer declares a figure key no report emits
- **THEN** the publish fails naming that key
- **AND** no figure is written to the consumer

#### Scenario: A declared path escapes the consumer

- **WHEN** a declared path resolves outside the consuming repository
- **THEN** the publish fails naming that path

#### Scenario: A figure key is renamed in a report

- **WHEN** a report changes a figure's key while a consumer still declares the old key
- **THEN** the publish fails rather than silently leaving the consumer's figure stale

### Requirement: Publishing a figure is subject to the same guards as publishing a table

Figure delivery SHALL observe the preconditions that govern the delivery of other generated
artifacts: a clean generator tree, so provenance names the code that ran, and no uncommitted changes
to the delivered paths in the consuming repository.

#### Scenario: The consumer has edited a published figure

- **WHEN** a delivered figure path has uncommitted changes in the consuming repository
- **THEN** the publish is refused before any file is overwritten

#### Scenario: The generator tree is dirty

- **WHEN** publishing runs from a dirty generator tree without the documented override
- **THEN** the publish is refused, for figures as for tables
