## Purpose

Defines how evaluation databases are prepared, protected, read, classified, and retired without
mutating published empirical inputs or allowing scratch state to become report evidence.

## ADDED Requirements

### Requirement: Every evaluation database is a corpus or scratch

An evaluation database MUST be either a corpus declared in the registry or a scratch database whose
name matches the reserved scratch pattern. A database that is neither MUST be reported as unclassified.

A scratch database MUST be recreated by its owning command and MUST NOT be read by a published report.

#### Scenario: A report selects scratch

- **WHEN** a report input resolves to a scratch database
- **THEN** report preflight fails before querying it

#### Scenario: An unclassified database is found

- **WHEN** lifecycle verification finds a database outside both classes
- **THEN** it reports the database and requires an explicit retain, dump, or drop disposition

### Requirement: Corpus base data is immutable and report access is read-only

Published corpus base tables MUST NOT be modified after measurement. Reports MUST connect through a
role that cannot write corpus data or schema. Destructive database operations MUST refuse every
registered corpus.

#### Scenario: A report attempts a write

- **WHEN** a report connection attempts to modify a corpus
- **THEN** the database refuses the operation

#### Scenario: A destructive command targets a corpus

- **WHEN** a reset or drop command resolves to a registered corpus
- **THEN** the guard refuses the command and names the corpus id

### Requirement: Derived schema is prepared from versioned source before reports run

A restored or newly registered corpus MUST be prepared before report access. Preparation MUST install
required derived views from checked-in source, record a deterministic revision of that source, verify
the required views, and then establish read-only report access. Preparation MUST be idempotent and MUST
NOT rewrite measured base rows.

Report preflight MUST compare the installed derived-schema revision with the revision expected by the
running source and refuse a mismatch.

#### Scenario: A corpus is restored from a dump

- **WHEN** restore completes
- **THEN** preparation installs and verifies the current declared derived schema before reports may read it
- **AND** report access is read-only afterward

#### Scenario: A stale materialized view survives

- **WHEN** the installed derived-schema revision differs from the checked-in revision
- **THEN** report preflight fails naming the corpus and both revisions

#### Scenario: Preparation runs twice

- **WHEN** preparation is repeated against an already prepared corpus
- **THEN** it succeeds with the same derived-schema revision and unchanged measured base rows

### Requirement: Retirement is evidence-backed and happens after replacement

A corpus, partial snapshot, or unclassified database MUST NOT be dropped until its consumers, observed
project count, and disposition are recorded. Any retained dump MUST be verified before the drop. Live
consumers and protection rules MUST already resolve through the replacement lifecycle model.

#### Scenario: A superseded database is retired

- **WHEN** its consumer audit finds no current reader and any required dump verifies
- **THEN** it may be dropped together with its retirement record

#### Scenario: Retirement evidence is incomplete

- **WHEN** a database lacks a consumer result, project count, or disposition
- **THEN** retirement is refused and the database remains intact
