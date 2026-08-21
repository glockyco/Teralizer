# reporting/artifact-provenance Specification

## Purpose
Governs what a generated artifact records about the code that produced it, so that a reader can reach
those exact lines, and so that regenerating an artifact whose inputs have not changed produces no
difference.

## Requirements

### Requirement: Provenance names the commit of the code that produced the artifact

An artifact's recorded commit SHALL be the last commit that changed the source file defining the
function which produced it. It SHALL NOT be the current checkout position, because that position is
unrelated to when the producing code last changed.

A recorded source reference SHALL resolve to the producing lines as they stood in the recorded commit.

#### Scenario: The producing source has not changed recently

- **WHEN** an artifact is generated from a source file whose last change predates the checkout position
- **THEN** the recorded commit is that source file's last change
- **AND** the source reference resolves to that commit

#### Scenario: An unrelated commit is made

- **WHEN** a commit changes files unrelated to a report, and the report is regenerated
- **THEN** the artifact's recorded commit is unchanged

### Requirement: Regenerating unchanged inputs reproduces the artifact

Regenerating an artifact SHALL produce a byte-identical result when neither the producing source nor
the data it reads has changed.

An artifact SHALL NOT embed a value that varies with the checkout position, the wall clock, or the
state of unrelated files.

#### Scenario: Regeneration after no change

- **WHEN** the report set is regenerated with no change to source or data
- **THEN** no committed report output differs

#### Scenario: Publishing twice in succession

- **WHEN** publishing runs, and runs again with nothing else changed
- **THEN** the second run is not refused for a dirty generator tree caused by the first

### Requirement: Uncertainty is recorded per source file

An artifact SHALL be marked uncertain when the source file that produced it has uncommitted changes.
It SHALL NOT be marked uncertain because an unrelated file in the repository has uncommitted changes.

Where a producing source file has no commit at all, the artifact SHALL be marked uncertain and record
the checkout position, which is the only available answer.

#### Scenario: An unrelated file is edited

- **WHEN** a file that produces no artifact has uncommitted changes
- **THEN** artifacts produced by unmodified sources are not marked uncertain

#### Scenario: The producing source is edited

- **WHEN** the source file that produced an artifact has uncommitted changes
- **THEN** that artifact is marked uncertain

#### Scenario: A new report has never been committed

- **WHEN** an artifact is produced by a source file with no commit
- **THEN** it is marked uncertain
- **AND** it records the checkout position

### Requirement: Publishing still requires a clean tree

Publishing SHALL remain refused from a repository with uncommitted changes, with the documented
override, so that a recorded commit is never attributed to code that differs from it.

#### Scenario: Publishing from a dirty tree

- **WHEN** publishing runs from a tree with uncommitted changes and no override
- **THEN** it is refused

### Requirement: Provenance records every declared report input

Generated report provenance SHALL identify every corpus and repository file declared by the report run. Input records SHALL be organized by semantic role and SHALL use one common schema for every report.

A corpus input record SHALL include its semantic corpus id and the verified registry state used by the run. A present file input SHALL include its repository-relative location and stable content identity. An absent optional input SHALL be recorded as absent. A normalized external-evidence file SHALL also carry its validated upstream identities and reconciliation totals in its own versioned content.

#### Scenario: An artifact is produced from two corpora

- **WHEN** its report declares two corpus roles
- **THEN** provenance records both roles and both semantic corpus ids
- **AND** neither identity is reconstructed from a value asserted by the report builder

#### Scenario: An artifact uses a versioned audit file

- **WHEN** the report declares that file as an input
- **THEN** provenance records its repository-relative path, content identity, source revision, and dirty state

#### Scenario: An optional file is absent

- **WHEN** absence has a defined report meaning
- **THEN** provenance records the input role and its absent state

### Requirement: Input provenance is captured by the run boundary

The runner SHALL capture input snapshots before report construction and SHALL verify that repository inputs did not change before rendering. Report code SHALL NOT construct, replace, or omit its own input provenance.

#### Scenario: A builder reports a different database identity

- **WHEN** a builder would name an input differently from the resolved context
- **THEN** no builder-supplied input identity exists to override the runner snapshot

#### Scenario: A repository input changes during the run

- **WHEN** its content identity differs after report construction
- **THEN** rendering and publication are refused

### Requirement: Input changes are visible without changing code provenance

Changing a declared input SHALL update the report's input provenance without attributing a new commit to unchanged producing code. Unchanged code and unchanged declared inputs SHALL continue to regenerate byte-identical provenance and artifacts.

#### Scenario: Audit labels change but report code does not

- **WHEN** a committed audit input changes and the producing source file does not
- **THEN** the input snapshot changes
- **AND** the producing code commit remains the last commit that changed the source file

#### Scenario: Neither code nor inputs change

- **WHEN** the report set is regenerated
- **THEN** its provenance manifest is byte-identical

### Requirement: Publishing requires publishable declared inputs

Publishing SHALL be refused when a present declared repository input has uncommitted changes, under the same documented local-iteration override that governs producing source. An override SHALL preserve the input's dirty state in provenance.

#### Scenario: A declared audit file has uncommitted labels

- **WHEN** publication runs without the dirty-provenance override
- **THEN** publication is refused before rendering or consumer delivery

#### Scenario: Local iteration uses the override

- **WHEN** a declared input is dirty and the documented override is active
- **THEN** local rendering may continue
- **AND** the manifest records that input as dirty
