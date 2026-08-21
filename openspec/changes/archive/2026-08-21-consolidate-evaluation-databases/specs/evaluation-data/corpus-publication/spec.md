## Purpose

Defines the portable replication artifact for each published corpus: one verifiable dump, the manifest
facts that identify and prepare it, and the non-database inputs required to reproduce its reports.

## ADDED Requirements

### Requirement: Publication produces one dump and manifest entry per published corpus

Publication MUST produce one dump for every corpus marked published and bind each dump to exactly one
manifest entry. The entry MUST carry the semantic corpus id, current physical database name, checksum,
byte size, expected and observed project counts, producer revision and dirty state, corpus-definition
checksums, installed derived-schema revision, and a provenance statement derived from the corpus.

Publication MUST fail before promotion when any observed fact disagrees with the registry or when two
entries claim the same corpus.

#### Scenario: The artifact is built

- **WHEN** every published corpus verifies
- **THEN** the artifact contains one dump and one complete manifest entry for each

#### Scenario: A dump differs from its entry

- **WHEN** a checksum, byte size, project count, corpus id, or derived-schema revision disagrees
- **THEN** publication fails naming the corpus and difference
- **AND** no incomplete artifact is promoted

### Requirement: Corpus export is co-located, durable, and restartable

Publication MUST separate corpus export from package assembly. When the source PostgreSQL service and
package assembly reside on different hosts, the logical dump command MUST execute on the data host and
produce a compressed archive there. Only the completed archive and its checksum MAY cross the host
boundary. Publication MUST NOT carry the source database's logical row stream through the workstation
transport.

Each corpus MUST have an independent durable export boundary. A completed export MUST survive a later
corpus or transfer failure. A partial dump MUST be distinguishable from a completed dump and MUST NOT
be reused, transferred as complete, or promoted. An interrupted transfer MUST be resumable without
exporting the corpus again. Package assembly MUST accept only the complete verified published set and
MUST preserve the previous package on failure.

#### Scenario: The data host differs from the assembly host

- **WHEN** publication exports a corpus from a remote PostgreSQL service
- **THEN** the compressed dump is completed and checksummed on the data host before transfer
- **AND** package assembly receives the archive rather than a tunneled logical row stream

#### Scenario: A later corpus export fails

- **WHEN** one corpus has a verified completed export and a later corpus export fails
- **THEN** the completed export remains available for the next publication attempt
- **AND** the partial later export is not eligible for transfer or assembly

#### Scenario: Dump transfer is interrupted

- **WHEN** transfer stops after a corpus export has completed
- **THEN** transfer can continue without repeating the database export
- **AND** the received dump is accepted only after its checksum verifies

#### Scenario: Package assembly is incomplete

- **WHEN** any published corpus export is absent or invalid
- **THEN** assembly fails naming each absent or invalid corpus
- **AND** the previous complete package remains unchanged

### Requirement: Publication includes report-required non-database inputs

The artifact MUST include every corpus definition, attempt ledger, completion marker, and project
configuration that a registered report validates before reading a corpus. Bulk logs and intermediate
run output MAY remain optional when no report contract reads them.

#### Scenario: A report validates a completion ledger

- **WHEN** the report's declared inputs include that ledger
- **THEN** the published artifact includes it and records its checksum

#### Scenario: An optional bulk log is absent

- **WHEN** no registered report reads the log
- **THEN** its absence does not make the artifact incomplete

### Requirement: Import verifies identity, prepares derived schema, and proves read-only use

Import MUST verify the manifest and dump before restore. It MUST restore the declared corpus, run the
same preparation operation used by the author, verify project count and derived-schema revision, and
exercise the report's declared read-only input check before reporting success.

The documented import path MUST work on a machine that does not use the author's physical database
service names. No step MAY require access to the author's workstation or evaluation host.

#### Scenario: A replicator imports a corpus

- **WHEN** the dump and manifest verify
- **THEN** import restores the corpus, prepares its derived schema, and verifies read-only report access
- **AND** the corpus is addressable by its semantic id

#### Scenario: The imported view definition is stale

- **WHEN** preparation or verification observes a derived-schema revision different from the manifest
- **THEN** import fails naming the mismatch

#### Scenario: The author restores the artifact

- **WHEN** the author follows the documented replication path on a clean machine
- **THEN** registered reports reproduce the published artifacts without a private database dependency
