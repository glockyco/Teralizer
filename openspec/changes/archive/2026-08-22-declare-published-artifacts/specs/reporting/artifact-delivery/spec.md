## Purpose

Governs which generated artifacts reach a repository that intentionally retains them: how that
repository states what it takes and under which paths, what publishing guarantees about the set it
delivers, and what happens when the declaration and the set a run emitted disagree.

## ADDED Requirements

### Requirement: A consuming repository declares every artifact it takes

A consuming repository SHALL declare the generated artifacts it takes and the path each artifact is
written to. The declaration SHALL cover every kind of generated artifact, and SHALL NOT be limited to
one kind.

Publishing SHALL NOT derive a consumer's path from the name a generator gave an artifact. Two
consumers MAY declare different paths for the same artifact.

A declared path SHALL resolve inside the consuming repository. A consumer MAY declare an artifact that
is not a document input when it intentionally retains that artifact as reviewable evidence. The
consumer's declaration remains the authority for that choice.

#### Scenario: A repository declares artifacts of several kinds

- **WHEN** a consuming repository declares tables, data files, and figures
- **THEN** publishing writes each declared artifact to the path the consumer named

#### Scenario: A consumer retains machine-readable evidence

- **WHEN** a consuming repository declares a generated evidence file that its document does not include
- **THEN** publishing delivers the file because the declaration records the repository's intent to retain it

#### Scenario: An artifact has no consumer

- **WHEN** a report emits an artifact that no consumer declares
- **THEN** the artifact remains in the generator's own build tree
- **AND** it is not delivered to any consuming repository

#### Scenario: Two consumers name one artifact differently

- **WHEN** two consuming repositories declare the same artifact under different paths
- **THEN** each receives the artifact under the path it declared

### Requirement: Publishing delivers the declared set and nothing else

Publishing SHALL write every declared artifact and no other generated artifact. A destination that
declares nothing SHALL receive nothing.

Publishing SHALL NOT remove a file the consuming repository already holds, including a file an earlier
publish deposited. Removing a file is the consumer's own act, so that a publish cannot delete work it
does not understand.

#### Scenario: The run emits more than the consumer declares

- **WHEN** a run emits artifacts the consuming repository has not declared
- **THEN** none of those artifacts is written to that repository

#### Scenario: A destination declares nothing

- **WHEN** a publish destination supplies no declaration
- **THEN** no generated artifact is delivered to it

#### Scenario: An earlier publish left an undeclared file behind

- **WHEN** the consuming repository holds an undeclared file from an earlier publish
- **THEN** publishing neither overwrites nor removes it

### Requirement: A declaration that disagrees with the emitted set fails the publish

Publishing SHALL fail when a declared artifact is not emitted by any report in the run, and when a
declared path resolves outside the consuming repository.

A failure SHALL name the artifact and the disagreement. Publishing SHALL NOT deliver part of the
declared set after detecting a disagreement.

#### Scenario: A declared artifact does not exist

- **WHEN** a consumer declares an artifact that no report in the run emits
- **THEN** the publish fails naming that artifact
- **AND** no artifact is written to the consumer

#### Scenario: An artifact is renamed in a report

- **WHEN** a report changes an artifact's name while a consumer still declares the old name
- **THEN** the publish fails rather than leaving the consumer's copy stale

#### Scenario: A declared path escapes the consumer

- **WHEN** a declared path resolves outside the consuming repository
- **THEN** the publish fails naming that path

### Requirement: An artifact name identifies one artifact across the whole report set

No two reports SHALL emit the same name for the same kind of artifact. A run that produces a duplicate
name SHALL fail, naming the artifact and the reports that claim it.

A consumer declares an artifact by name and not by report, so a duplicate name is an ambiguity the
declaration cannot express, and report order would otherwise decide which artifact a consumer prints.

#### Scenario: Two reports emit one name

- **WHEN** a report emits an artifact name another report in the run already emitted
- **THEN** the run fails naming that artifact
- **AND** nothing is delivered

### Requirement: Delivery happens once, after the whole run

Publishing SHALL resolve a declaration against everything the run emitted, and SHALL deliver nothing
before every report in the run has been rendered.

A run that fails before it finishes SHALL leave the consuming repository unchanged, so that a consumer
never holds a set assembled from part of a run.

#### Scenario: A report fails partway through the run

- **WHEN** a report fails after an earlier report in the same run has been rendered
- **THEN** the consuming repository is unchanged

#### Scenario: A declared artifact is emitted by a later report

- **WHEN** a consumer declares an artifact that only the last report of the run emits
- **THEN** the publish succeeds

### Requirement: Uncommitted consumer changes block delivery of any artifact

Publishing SHALL be refused when a declared path has uncommitted changes in the consuming repository,
for every kind of artifact.

The refusal SHALL happen before any file is overwritten, because an uncommitted change to a generated
path is work the generator cannot reproduce.

#### Scenario: The consumer has edited a delivered table

- **WHEN** a declared table path has uncommitted changes in the consuming repository
- **THEN** the publish is refused before any file is written

#### Scenario: The consumer has edited a delivered figure

- **WHEN** a declared figure path has uncommitted changes in the consuming repository
- **THEN** the publish is refused before any file is written

### Requirement: A run that cannot produce a declared kind fails before building

When a destination declares artifacts of a kind that the invocation does not ask the run to produce,
the run SHALL fail before any report is built, and the failure SHALL name the missing target.

Without this, every declared artifact of that kind would be reported as absent, which blames the
consumer's declaration for a mistake in the invocation.

#### Scenario: The invocation omits a declared kind

- **WHEN** a destination declares figures and the invocation does not ask for figures
- **THEN** the run fails naming the missing target
- **AND** no report is built

#### Scenario: The invocation covers every declared kind

- **WHEN** an invocation asks for every kind the destination declares
- **THEN** the run proceeds
