## Purpose

Publication turns the registry into the artifact a third party receives: one dump per corpus, a
manifest binding each dump to its corpus and to what produced it, and an import that verifies what
it restored. The artifact currently promises dumps it never builds and ships two corpora that no
current figure is read from.

## ADDED Requirements

### Requirement: Publication produces a dump and a manifest for every registry corpus

Publishing MUST produce one dump per corpus and a manifest binding each dump to its corpus id,
physical database name, checksum, byte size, project count, and provenance statement.

#### Scenario: The artifact is built

- **WHEN** publication runs
- **THEN** every registry corpus has a dump and a manifest entry
- **AND** no corpus a report reads is absent

#### Scenario: A corpus disagrees with its entry

- **WHEN** a corpus's project count differs from its registry entry
- **THEN** publication fails, names the corpus and the difference, and writes no manifest

### Requirement: The manifest states what produced each corpus

Each manifest entry MUST carry a provenance statement derived from the corpus: the tool commits that
wrote it with the number of projects each wrote, and the number of projects carrying no recorded
version. Mixed provenance MUST be stated rather than summarized away, because these corpora are
accepted as they stand and will not be re-measured.

#### Scenario: A corpus was written by several tool commits

- **WHEN** a corpus's projects carry more than one tool version
- **THEN** its statement lists every commit with the number of projects that commit wrote
- **AND** publication continues, because mixed provenance is accepted

#### Scenario: Projects carry no tool version

- **WHEN** a corpus contains projects with no recorded tool version
- **THEN** the statement reports how many, and attributes no commit to them

#### Scenario: A statement is derived, not asserted

- **WHEN** a provenance statement is produced
- **THEN** it comes from reading the corpus
- **AND** editing it to claim a cleaner history than the corpus contains is a defect

#### Scenario: A reader traces a figure

- **WHEN** a reader has a figure and the corpus id it came from
- **THEN** the manifest yields that corpus's provenance statement without access to the author's
  machines

### Requirement: The artifact ships everything a report refuses to run without

Where a report checks its corpus definition, the artifact MUST ship the inputs it checks: the
attempt ledger, the completion markers, and the project configs. These MUST NOT depend on an optional
packaging flag. Bulk run material that no report reads MUST NOT be required.

#### Scenario: A replicator generates a guarded report

- **WHEN** a replicator imports the artifact and generates a report whose corpus is guarded
- **THEN** the ledger, markers, and project configs are present and the guard passes
- **AND** no additional download is needed

#### Scenario: A definition input is missing at publication time

- **WHEN** a checked corpus's ledger, markers, or configs are absent
- **THEN** publication fails and names the corpus and the missing input

#### Scenario: Bulk fixture material is large and unread

- **WHEN** a corpus directory holds bulk material that no report reads
- **THEN** it is not shipped as part of the artifact a replicator must download

### Requirement: The packaging path consumes the manifest

The archive builder MUST take the set of shipped dumps from the manifest rather than from a
hand-maintained list, and the documented archive contents MUST match what the builder produces.

#### Scenario: A corpus is added to the registry

- **WHEN** a corpus is added and publication is rerun
- **THEN** the archive contains its dump with no edit to the packaging script

#### Scenario: Documented contents disagree with the build

- **WHEN** documentation describes archive contents the builder does not produce
- **THEN** the check fails and names the discrepancy

### Requirement: Import verifies what it restored

Importing MUST verify each restored corpus against its manifest entry before reporting success, and
MUST fail loudly on any mismatch.

#### Scenario: A dump restores correctly

- **WHEN** a corpus is imported
- **THEN** its checksum and project count match the manifest
- **AND** the import reports the corpus id it restored

#### Scenario: A dump is corrupt, truncated, or missing

- **WHEN** a restored corpus's checksum or project count does not match, or a listed dump is absent
- **THEN** the import fails, names the corpus and the problem, and does not report success

### Requirement: A replicator reproduces the reported figures from the artifact alone

Following the documented path on a machine that is not the author's MUST yield the reported figures,
without depending on the author's host names, absolute paths, or credentials.

#### Scenario: A third party runs the documented path

- **WHEN** a replicator imports the artifact and runs the report set
- **THEN** the reports reproduce the published figures
- **AND** no step requires access to the author's machines

#### Scenario: A script addresses a database service by name

- **WHEN** a script names the database service it connects to
- **THEN** that name resolves in the environment the artifact ships

#### Scenario: The author restores from the artifact

- **WHEN** the author needs a corpus locally
- **THEN** it is restored from the published dump by the same path a replicator uses
