## Purpose

The corpus registry is the single declarative source of truth that gives each empirical corpus a stable
semantic identity and resolves it to the deployment-specific inputs required by reports, reruns, and
publication.

## ADDED Requirements

### Requirement: A registry entry declares corpus identity and expected shape

Each registered corpus MUST have exactly one stable semantic id, one current physical database name,
its corpus-definition paths, an expected project count, and publication status. An entry MUST NOT use a
research-question number, storage role, version counter, or machine location as its semantic id.

A physical database name previously carried by a corpus is history and MUST NOT be recorded as an
alias. History belongs to the commit and provenance that changed the mapping.

#### Scenario: A consumer asks for a corpus id

- **WHEN** a live consumer requests a registered corpus id
- **THEN** it receives the current database and corpus-definition inputs from that one entry

#### Scenario: An entry omits required metadata

- **WHEN** a registry entry omits its database, expected project count, corpus definition, or publication status
- **THEN** registry validation fails naming the corpus id and missing field

#### Scenario: Two entries collide

- **WHEN** two registry entries share an id or resolve to the same published corpus ambiguously
- **THEN** registry validation fails before a consumer connects

### Requirement: Live consumers use semantic corpus ids

Reports, rerun commands, project configuration, packaging, import, and diagnostics MUST address a
corpus by semantic id. A physical database literal MUST NOT appear in live consumer code or executable
configuration outside the registry and database lifecycle machinery.

Generated provenance MAY record the physical database that resolution observed. That record is an
observation, not an input alias.

#### Scenario: A report requests a corpus

- **WHEN** a report declares its empirical input
- **THEN** it declares a semantic corpus id or role that resolves to one
- **AND** it does not carry a physical database name

#### Scenario: A rerun command is executed

- **WHEN** a runner is invoked for a registered corpus
- **THEN** it resolves the corpus id through the same registry used by reports
- **AND** it records the resolved identity in provenance

#### Scenario: Live configuration embeds a database name

- **WHEN** validation finds a registered physical database literal in a runner or executable configuration
- **THEN** validation fails naming the file and literal

### Requirement: Verification distinguishes requested subsets from publication completeness

A workstation MAY hold only a subset of registered corpora. Verification of an explicitly requested
subset MUST require every named corpus and validate its complete entry. Inventory mode MUST report
missing and unclassified databases without treating unrelated absent corpora as corruption.

A publication build MUST require every corpus marked published.

#### Scenario: A local workstation has one requested corpus

- **WHEN** verification requests that corpus and its database and inputs satisfy the registry
- **THEN** verification succeeds even if unrelated registered corpora are absent

#### Scenario: Publication runs from an incomplete host

- **WHEN** any published corpus is absent or invalid
- **THEN** publication fails naming each missing or invalid corpus

#### Scenario: An unclassified database exists

- **WHEN** inventory finds a database that is neither registered nor valid scratch
- **THEN** it reports the database as unclassified
- **AND** it does not silently assign that database to a corpus
