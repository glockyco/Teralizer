## Purpose

Defines how a complete Teralizer replication release is identified, assembled, verified, documented, and installed without relying on the author's machines or on undeclared files.

## ADDED Requirements

### Requirement: A release manifest declares the complete release set

Every release SHALL contain a machine-readable release manifest that identifies the artifact version, version-specific DOI, concept DOI, source revision, paper identity, and every archive in the release. Each archive entry SHALL state its semantic component id, filename, checksum, byte size, unpacked size, purpose, required companion components, and internal manifest checksum.

Release membership SHALL come only from this declaration. A release builder SHALL NOT infer membership from filename patterns, directory contents, a fixed archive count, or files left by an earlier build.

#### Scenario: A complete release is assembled

- **WHEN** every declared component and its internal manifest verify
- **THEN** the release manifest names exactly those components and their measured facts
- **AND** the release is eligible for validation

#### Scenario: An output directory contains an old archive

- **WHEN** an archive exists that is not declared by the release being built
- **THEN** it is not included in the release or its checksum inventory

#### Scenario: A declared component is absent

- **WHEN** any declared archive, required input, or internal manifest is missing or invalid
- **THEN** release assembly fails naming every disagreement
- **AND** no incomplete release replaces the previous complete set

### Requirement: Release assembly is clean and atomic

A release SHALL be assembled from a committed source revision, a complete verified corpus package, a complete registered report run, and explicitly declared optional project and data inputs. Assembly SHALL stage all release files separately, verify the staged set, and promote the complete set atomically.

The release SHALL record every input revision and dirty state. Production release assembly SHALL reject dirty or unattributed inputs and SHALL NOT require access to a corpus database, author workstation, or evaluation host after the verified corpus package exists.

#### Scenario: A release is built from verified inputs

- **WHEN** the source, corpus package, report artifacts, and declared optional components all verify
- **THEN** assembly produces one complete staged release with their recorded identities

#### Scenario: A late archive build fails

- **WHEN** one or more archives have staged successfully and a later archive fails
- **THEN** the prior published or locally promoted release remains unchanged
- **AND** the staged partial release is ineligible for upload

#### Scenario: The source tree is dirty

- **WHEN** production release assembly observes source or declared inputs that differ from their recorded revisions
- **THEN** it fails before promoting any release file

### Requirement: Every archive is self-identifying and safely composable

Every downloadable archive SHALL contain an archive manifest, release identity, citation, license mapping, purpose, payload inventory, and instructions appropriate to that component. An archive SHALL use a unique component identity independent of its filename.

Installation SHALL verify each selected archive before extraction, stage its payload, and install every selected component. It SHALL detect path collisions and accept a collision only when both archives declare the same immutable file with the same checksum. The presence of an already populated shared directory SHALL NOT cause a selected archive to be skipped.

#### Scenario: Primary and real-world projects are selected

- **WHEN** a replicator installs both declared project components
- **THEN** every project from both components is installed
- **AND** neither component is skipped because the destination already contains projects

#### Scenario: Two components disagree on one path

- **WHEN** selected archives declare different bytes for the same installation path
- **THEN** installation fails naming both components and the path
- **AND** the existing installation remains unchanged

#### Scenario: The full component supersedes its sample

- **WHEN** a workflow selects both a full component and a sample that the release declares as its subset
- **THEN** installation either deduplicates checksum-identical members or rejects the redundant selection with a corrective command
- **AND** it never installs an ambiguous mixture

### Requirement: Public requirements and metadata state exact release facts

The release SHALL provide one authoritative reviewer entry point and a dedicated requirements document readable without executing artifact code. Together they SHALL state exact download and unpacked sizes, peak free-disk requirement and its scope, memory, CPU architecture, supported host environments, required host software, network use, setup time, results-reproduction time, reduced and full collection times, archive combinations per workflow, and cleanup effects.

The human-readable values SHALL be generated or checked against the verified release and corpus manifests. Placeholders such as “manifest-derived,” commands that must be run to discover basic requirements, and release values copied from a superseded version SHALL make release validation fail.

The Zenodo description, citation metadata, badge claims, paper link, licenses, archive inventory, commands, and database terminology SHALL agree with the packaged documentation before publication.

#### Scenario: A reader evaluates feasibility before download

- **WHEN** the reader opens the release landing page or packaged requirements document
- **THEN** they can determine every archive, resource, architecture, network, and time requirement for the chosen workflow without running the artifact

#### Scenario: A documented size is stale

- **WHEN** a documented archive or peak-disk value differs from the verified manifests
- **THEN** release validation fails naming the document, field, expected value, and observed value

#### Scenario: Public instructions name a retired command

- **WHEN** packaged or Zenodo instructions refer to an interface absent from the release
- **THEN** release validation fails before publication

### Requirement: A release is immutable and version-addressable

Each public release SHALL use a version-specific persistent DOI and SHALL also record the artifact's concept DOI. The packaged source revision, release manifest, checksums, archives, documentation, and public landing-page metadata SHALL identify the same version.

Publishing a revision SHALL create a new archival version. It SHALL NOT alter the bytes or claims of an existing published DOI.

#### Scenario: The v7 corpus release supersedes the first artifact

- **WHEN** the revised artifact is published
- **THEN** it receives a new version DOI under the existing concept DOI
- **AND** the first version remains retrievable and unchanged

#### Scenario: Citation metadata labels an identifier

- **WHEN** citation metadata describes a DOI as version-specific or conceptual
- **THEN** that label agrees with the archival repository's identifier relationship

### Requirement: Distribution rights and third-party provenance are explicit

The release SHALL contain a top-level license map covering source code, authored data and documentation, and third-party materials. Every redistributed project SHALL retain its original license and attribution and SHALL be listed with its origin URL, immutable source revision, license identity, and redistribution decision. A project without established redistribution permission SHALL NOT be included as though the artifact license covered it.

The release SHALL document the corpus selection source, whether human-participant or sensitive data is present, and any ethical or legal constraint relevant to reuse. Independently downloadable result and data components SHALL include their schema and provenance context.

#### Scenario: A redistributed project has a recognized license

- **WHEN** a project archive contains that project
- **THEN** its archive manifest and third-party notice identify the origin, revision, license, and retained license text

#### Scenario: Redistribution permission is unclear

- **WHEN** no license or other redistribution basis can be established for a project
- **THEN** release validation rejects its source payload
- **AND** the release may retain only legally distributable provenance and retrieval instructions

#### Scenario: A user downloads only the results component

- **WHEN** the results archive is opened without the core archive or Zenodo page
- **THEN** it still identifies the study, release, citation, licenses, data schemas, provenance, and files it contains
