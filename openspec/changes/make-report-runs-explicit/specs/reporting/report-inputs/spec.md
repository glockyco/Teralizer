## Purpose

Define how every report declares, receives, validates, and identifies all corpus and repository evidence
inputs so that no result depends on an invisible connection or path.

## ADDED Requirements

### Requirement: A report declares every input by semantic role

A registered report SHALL declare a closed set of named input roles. Each role SHALL identify a
registry corpus, a repository file, or a tracked repository tree. A report that reads more than one
corpus SHALL declare each corpus under a distinct role.

A report builder SHALL NOT open an additional evaluation database, substitute a physical database
name, or resolve an undeclared evidence path.

#### Scenario: A report compares two corpora
- **WHEN** a report derives one result from two evaluation corpora
- **THEN** its registration declares both corpus ids under distinct semantic roles
- **AND** neither connection is opened by the report builder

#### Scenario: A report reads committed audit data
- **WHEN** a report summarizes a versioned audit file
- **THEN** the file is declared as a report input
- **AND** the report does not resolve the file through a module-level default path

#### Scenario: A report uses no external file evidence
- **WHEN** a report depends only on declared corpora and producing code
- **THEN** it declares no placeholder file or tree input

### Requirement: Corpus inputs are resolved only through the corpus registry

A corpus input SHALL name a semantic corpus id and SHALL be resolved to its physical database through
the corpus registry. Before report construction, the runner SHALL verify the corpus's declared
identity, expected project count, read-only accessibility, corpus-definition inputs where declared,
and required database objects and columns.

An invocation SHALL NOT replace a declared corpus with an arbitrary physical database. Scratch and
unclassified databases SHALL remain unreadable by reports.

#### Scenario: A declared corpus is valid
- **WHEN** its registry entry, observed project count, corpus definition, and required objects agree
- **THEN** the report receives a read-only connection under the declared role

#### Scenario: One of several corpora is invalid
- **WHEN** any corpus role is absent, partial, writable through the report account, or lacks a required
  object
- **THEN** report construction does not start
- **AND** the failure names the input role and disagreement

#### Scenario: A caller supplies a physical database override
- **WHEN** a caller attempts to substitute a database name for a declared corpus role
- **THEN** the invocation is rejected before any connection is opened

### Requirement: Repository evidence inputs have stable identity

A repository file input SHALL declare its repository-relative path and whether absence is valid. A
tracked tree input SHALL declare its repository-relative root. The runner SHALL capture a stable content
identity for every present file or tree before report construction.

A required input that is absent SHALL fail before report construction. An optional input that is absent
SHALL remain an explicit absent input rather than silently becoming an empty or successful dataset.

#### Scenario: A required audit file is absent
- **WHEN** a registered report requires the file and it does not exist
- **THEN** construction fails naming its role and declared path

#### Scenario: An optional completion marker is absent
- **WHEN** absence has a defined report meaning
- **THEN** the report receives an explicit absent value for that role
- **AND** provenance records that the declared input was absent

#### Scenario: A tracked input changes during construction
- **WHEN** the input's content identity after construction differs from the identity captured before it
- **THEN** the report run fails before rendering

### Requirement: Builders receive only resolved report context

Report construction SHALL receive resolved inputs by their declared roles. Input identity, connection
lifetime, and provenance SHALL remain runner-owned and SHALL NOT be fields that a report builder can
assert independently.

Every registered report SHALL use the same construction boundary, including reports with one corpus,
several corpora, optional files, or tracked source trees.

#### Scenario: A builder names an input identity in its result
- **WHEN** the identity disagrees with the runner-resolved input
- **THEN** the architecture prevents that second identity from being represented

#### Scenario: A builder completes
- **WHEN** report construction returns successfully
- **THEN** all corpus connections are closed after the runner captures the result and input snapshots
- **AND** rendering does not require a live database connection

### Requirement: Every current report declares its actual input set

Migration SHALL account for every corpus connection and filesystem read that can affect a registered
report's values, completeness state, or presentation data. A report SHALL NOT be considered migrated
merely because its primary database moved behind the registry.

#### Scenario: A current report opens a secondary corpus internally
- **WHEN** the report is migrated
- **THEN** the secondary corpus becomes a declared role
- **AND** the internal connection path is removed

#### Scenario: A current report reads a fallback file or project tree
- **WHEN** that input can change a generated value
- **THEN** it is declared with its actual required or optional semantics
