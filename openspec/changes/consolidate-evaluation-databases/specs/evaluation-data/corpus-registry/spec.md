## Purpose

The corpus registry is the single declarative source of truth binding a semantic corpus id to one
physical database and to the expected shape of that database. It exists so that a database name
appears in exactly one place in the live code, and so a report cannot read the wrong corpus.

## ADDED Requirements

### Requirement: A registry entry declares the identity and expected shape of a corpus

Each entry MUST declare a corpus id, the physical database name, the corpus definition (data
directory and config directory) where one exists, and the expected project count. An entry MUST NOT record a name the
corpus previously carried: a superseded name is history, and history belongs in the commit that
changed it.

Every corpus in the registry is shipped. A corpus that no published figure is read from does not
belong in the registry.

#### Scenario: Reading a corpus by id

- **WHEN** a consumer asks the registry to resolve a corpus id
- **THEN** it receives the physical database name, the corpus definition, and the expected project
  count
- **AND** no consumer needs to know the physical name in order to ask

#### Scenario: An entry omits a required field

- **WHEN** the registry is loaded and an entry lacks any required field
- **THEN** loading fails and names the entry and the missing field

#### Scenario: Two entries collide on a physical name

- **WHEN** the registry is loaded and two entries declare the same physical database name
- **THEN** loading fails and names both corpus ids

#### Scenario: A corpus was renamed

- **WHEN** a corpus is renamed
- **THEN** the entry states only the current name
- **AND** the rename is recorded in the commit that performed it

#### Scenario: A database backs no published figure

- **WHEN** a database holds a real measurement that no published figure is read from
- **THEN** it is not a registry corpus
- **AND** it is handled by the retirement record instead

### Requirement: A corpus id states the evaluation condition it provides

A corpus id MUST state the evaluation condition the corpus provides, in the vocabulary the published
work already uses for that condition. It MUST be understandable without further context.

A corpus id MUST NOT contain a research question number, a report name, an implementation
identifier, or a run counter. A question and a report are consumers, which change independently of
the data. An implementation identifier means nothing to a reader of the published work. A counter
invites a reader to look for the other numbers, which are not published and are not missing.

#### Scenario: A corpus is named

- **WHEN** a corpus id is chosen
- **THEN** it names the condition the corpus provides, using the term the published work uses
- **AND** a reader who has only the artifact can tell what the corpus is for

#### Scenario: Two corpora provide contrasting conditions

- **WHEN** the published work contrasts two conditions and a corpus exists for each
- **THEN** their ids form that contrast

#### Scenario: A new consumer reads an existing corpus

- **WHEN** a new report or research question reads an existing corpus
- **THEN** neither the corpus id nor the physical database name changes

#### Scenario: Earlier runs of the same condition were superseded

- **WHEN** a condition was measured more than once during development and only the final run is
  published
- **THEN** the id carries no counter and does not imply a series
- **AND** the superseded runs are recorded in the retirement record, not in the name

#### Scenario: A candidate name comes from the implementation

- **WHEN** a candidate id uses a term that appears only in the implementation
- **THEN** it is rejected in favor of the term the published work uses

### Requirement: Live code holds no database name literal

The analysis package, the packaging and import tooling, and generated replication metadata MUST
obtain every evaluation database name from the registry. A literal evaluation database name in live code is
a defect. Archival run inputs are exempt, because they record what was run.

#### Scenario: A report declares the corpus it reads

- **WHEN** a report is registered
- **THEN** it names a corpus id
- **AND** the database it connects to is whatever the registry binds to that id

#### Scenario: A literal name is reintroduced into live code

- **WHEN** the repository is checked for evaluation database name literals in live code
- **THEN** the check fails and reports each offending location
- **AND** the check runs without a database connection

#### Scenario: An archival run input names a database

- **WHEN** a frozen run configuration or run script names the database it wrote
- **THEN** the check does not flag it
- **AND** the directory holding it states that its names predate the rename

#### Scenario: Replication metadata states which corpus backs a report

- **WHEN** a report and its published output identify the corpus they read
- **THEN** the manifest obtains that identity from the registry
- **AND** it cannot disagree with the corpus the report reads

### Requirement: A report declares the schema objects it needs, and nothing more

A report MUST declare the database objects and columns it depends on. That declaration alone MUST
decide whether the objects are checked before the report runs. No separate flag may describe a
report's schema, because a schema generation is a property of a database rather than of a report.

#### Scenario: A report declares required objects

- **WHEN** a report that declares required objects is run
- **THEN** those objects and columns are checked before the report builds
- **AND** a missing object fails with the object and column named

#### Scenario: A report declares no required objects

- **WHEN** a report that declares no required objects is run
- **THEN** no object check runs
- **AND** a missing object surfaces as the database error from the failing query

#### Scenario: A declaration cannot be silently ignored

- **WHEN** a report declares required objects
- **THEN** they are always checked
- **AND** no configuration can leave the declaration unchecked

### Requirement: The registry is verifiable against reality

A verification operation MUST report, for every entry, whether the database exists and whether its
project count matches the declaration, and MUST report any evaluation database that is neither a
registry corpus nor a validly named scratch database.

#### Scenario: A declared corpus is absent or has the wrong size

- **WHEN** verification runs and an entry's database is missing, or its project count differs from
  the declaration
- **THEN** verification fails and reports the corpus id, the expectation, and the observation

#### Scenario: An unclassified database exists

- **WHEN** verification finds an evaluation database that no entry declares and whose name is not
  valid scratch
- **THEN** verification reports it as unclassified
