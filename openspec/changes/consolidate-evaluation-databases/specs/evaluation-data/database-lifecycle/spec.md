## Purpose

Every evaluation database is either a corpus, which is an archival record restored from a dump and
never written, or scratch, which is disposable and unreadable by reports. Two classes replace a
four-entry denylist that cannot keep up with new names.

## ADDED Requirements

### Requirement: Every evaluation database belongs to exactly one class

A database MUST be either a corpus declared in the registry or a scratch database whose name matches
the reserved scratch pattern. A database that is neither MUST be reported as unclassified.

#### Scenario: A scratch database is named

- **WHEN** an experiment, a fixture gate, or a code generator needs a database
- **THEN** its name matches the reserved scratch pattern
- **AND** the pattern distinguishes it from every corpus name at a glance

#### Scenario: A database is recreated by its own runner on every use

- **WHEN** a database is recreated from scratch whenever its runner executes
- **THEN** it is classified scratch rather than a corpus
- **AND** it is not published, because its content is not a measurement of record

#### Scenario: An unclassified database is found

- **WHEN** a server is inspected and a database is neither a registry corpus nor validly named
  scratch
- **THEN** it is reported as unclassified, with its size and project count
- **AND** the report does not delete it

### Requirement: A corpus is read-only

A corpus MUST be restored and then left read-only: the account the reports use MUST NOT hold
privileges that would let it modify a measurement. Because measurement is finished, no routine
operation reopens a corpus for writing.

#### Scenario: A report reads a corpus

- **WHEN** a report connects to a corpus
- **THEN** it can read every table it needs
- **AND** it holds no privilege that would let it write

#### Scenario: A write reaches a corpus

- **WHEN** an insert, update, delete, or schema change is attempted against a corpus
- **THEN** the database rejects it
- **AND** the rejection comes from the database, not only from application code

#### Scenario: A local copy is damaged

- **WHEN** a local materialization of a corpus is damaged or deleted
- **THEN** it is restored from the corpus dump
- **AND** no measurement is recomputed

### Requirement: A report may only read a registry corpus

A report MUST refuse to read a scratch or unclassified database, and MUST refuse a corpus whose
project count disagrees with its registry entry, so a partial or experimental dataset cannot
silently produce a reported figure.

#### Scenario: A report is pointed at a scratch database

- **WHEN** a report is asked to read a database that is not a registry corpus
- **THEN** it refuses, names the corpus ids that are available, and produces no output

#### Scenario: A report is pointed at a partial corpus

- **WHEN** a report is asked to read a registry corpus whose project count does not match its entry
- **THEN** it refuses and reports the expected and observed counts

### Requirement: Scratch databases are disposable, corpora are not droppable

Creating and dropping a scratch database MUST require no override. Dropping or renaming a registry
corpus MUST be refused by the tooling.

#### Scenario: A runner recreates its scratch database

- **WHEN** a runner or code generator resets a scratch database
- **THEN** the operation succeeds without an override flag

#### Scenario: A drop targets a corpus

- **WHEN** tooling attempts to drop or rename a database the registry declares
- **THEN** the attempt is refused and the corpus id is named

### Requirement: Databases that are neither published nor scratch are retired once

Every database now present on an evaluation machine MUST reach one of three end states: declared in
the registry, retained only as a dump, or dropped. The decision and its evidence MUST be recorded
where the retirement happens, not kept as a permanent field.

#### Scenario: A superseded corpus is retired

- **WHEN** a corpus is superseded and no report and no document reads it
- **THEN** that evidence is recorded, and the corpus is retained as a dump or dropped

#### Scenario: A partial snapshot exists beside its complete corpus

- **WHEN** a database holds a subset of a corpus and differs from it only by a name suffix
- **THEN** it is dropped together with the ledger that identifies it

#### Scenario: A retained dump is verified before its database is dropped

- **WHEN** a corpus is to be dropped after being retained as a dump
- **THEN** the dump is restored and its project count checked first
- **AND** the drop happens only after that check passes
