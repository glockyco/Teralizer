## Purpose

Governs what a generated artifact records about the code that produced it, so that a reader can reach
those exact lines, and so that regenerating an artifact whose inputs have not changed produces no
difference.

## ADDED Requirements

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
