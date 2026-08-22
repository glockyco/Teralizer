# planning-state Specification

## Purpose

Defines where current planning state lives and prevents historical or technical records from becoming a second source of intended work.

## Requirements

### Requirement: Current planning state has one home

The repository SHALL keep all current planning state under `openspec/`. Repository guidance SHALL name no other location for current plans, tasks, roadmap state, or pending decisions.

#### Scenario: A reader locates current work

- **WHEN** a reader follows repository guidance to find current planning state
- **THEN** the guidance directs the reader to `openspec/`
- **AND** it does not direct the reader to inspect another directory

#### Scenario: A contributor adds a current plan elsewhere

- **WHEN** a change adds current planning state outside `openspec/`
- **THEN** repository verification fails with the conflicting path

### Requirement: Each legacy planning record receives one disposition

Before the legacy planning home is removed, the repository SHALL assign each record exactly one disposition. Intended work SHALL move to a named OpenSpec change or capability contract. Durable technical evidence SHALL move to documentation that owns its subject. Historical records MAY move to a non-planning history location. Superseded records with no retained value SHALL be removed.

#### Scenario: A legacy record contains intended work

- **WHEN** a legacy record contains work that is still intended
- **THEN** the work exists in one named OpenSpec owner before the legacy record is removed
- **AND** no duplicate task remains outside that owner

#### Scenario: A legacy record contains durable evidence

- **WHEN** current code or documentation depends on evidence from a legacy record
- **THEN** the evidence moves to the non-planning document or capability contract that owns the subject
- **AND** the dependent reference points to that owner

#### Scenario: A legacy record has only historical value

- **WHEN** a legacy record no longer describes intended work but remains useful as history
- **THEN** the record is stored outside the current planning namespace
- **AND** its location and content do not present it as current guidance

#### Scenario: A legacy record has no retained value

- **WHEN** a legacy record is superseded and carries no unique evidence or decision
- **THEN** the record is removed

### Requirement: Current sources do not depend on retired planning paths

Current guidance, skills, source documentation, and capability contracts SHALL reference authoritative current owners. They SHALL NOT cite a retired planning record as current evidence, design authority, roadmap state, or operating instruction.

#### Scenario: A current source cites a retired plan

- **WHEN** verification finds a current source that cites the retired planning home
- **THEN** verification fails with the source path and retired target

#### Scenario: A historical link is needed for provenance

- **WHEN** a current source must retain a historical link for provenance
- **THEN** the link identifies the target as history
- **AND** the current source states the authoritative current owner separately

### Requirement: Planning-home verification guards the cutover

An automated repository check SHALL detect current planning documents outside `openspec/`, references to the retired planning home, and guidance that declares more than one planning location. The check SHALL run in the repository's normal validation path.

#### Scenario: The repository follows the planning contract

- **WHEN** the repository contains one current planning home and no retired-path references
- **THEN** the planning-home check passes

#### Scenario: A second planning home returns

- **WHEN** a file or guidance change creates a second current planning home
- **THEN** the planning-home check fails before the change is accepted

### Requirement: Existing OpenSpec ownership remains intact

Migration SHALL preserve the scope and task ownership of existing OpenSpec changes. If a legacy record duplicates work already owned by an active change, the migration SHALL remove the duplicate instead of creating another owner.

#### Scenario: Legacy work already has an OpenSpec owner

- **WHEN** a legacy task matches work in an existing OpenSpec change
- **THEN** the existing change remains the sole owner
- **AND** the migration does not create a second change for that task
