## Purpose

Defines the source, author-staging, and release-handoff boundaries for production corpus artifacts while keeping source control independently usable and free of generated research payloads.

## ADDED Requirements

### Requirement: Source control excludes production corpus payloads

The source repository MUST NOT track production database dumps, generated corpus-package manifests, or generated checksum inventories through Git objects or Git LFS. It MAY track corpus declarations, package schemas, release references, and deliberately scoped synthetic fixtures that are small enough for normal source-control use.

#### Scenario: A production dump is added

- **WHEN** repository validation scans tracked files or staged changes containing a production database dump
- **THEN** validation fails with the path and the permitted external package boundary

#### Scenario: A focused test needs a database archive

- **WHEN** a test uses a declared synthetic dump fixture within the repository's fixture boundary
- **THEN** repository validation accepts it without treating it as a published corpus

### Requirement: Publication uses explicit external staging

Corpus export MUST write production dumps and their generated metadata to a path outside the tracked source tree. Package assembly MUST receive that complete package path explicitly and MUST NOT infer production inputs from the checkout, filename patterns, Git LFS state, or ambient build residue.

#### Scenario: A complete staged package is supplied

- **WHEN** the supplied package contains every declared corpus dump, manifest entry, checksum, and required non-database input
- **THEN** release assembly verifies and consumes that package

#### Scenario: No package path is supplied

- **WHEN** a production release command has no explicit corpus package input
- **THEN** it fails before archive creation and explains how to provide a verified package

#### Scenario: Checkout residue resembles a package

- **WHEN** dump files exist in or below the source checkout but are not the explicit package input
- **THEN** release assembly does not consume them

### Requirement: Installed packages verify before restore

Maintainer and installed-package workflows MUST verify the corpus manifest, semantic identities, dump sizes, and checksums before restoring a database. Normal source checkout, formatting, linting, and fixture-based CI MUST NOT require downloading production corpus payloads.

#### Scenario: A complete package is selected for restore

- **WHEN** a maintainer or installed artifact supplies a corpus package
- **THEN** the workflow verifies its manifest and all required bytes before database mutation

#### Scenario: Normal CI checks source changes

- **WHEN** pull-request or push validation runs without production data
- **THEN** repository hygiene and corpus behavior are exercised with declarations and synthetic fixtures
- **AND** no Git LFS or archival corpus download is required

### Requirement: Published history remains stable

Removing production payloads from the current source boundary MUST preserve published commits, DOI-linked tags, existing Zenodo versions, and remote Git LFS objects. The migration MUST NOT claim that deleting a pointer or current file purges historical bytes.

#### Scenario: The source boundary is migrated

- **WHEN** current source stops tracking production payloads
- **THEN** published history, DOI tags, existing Zenodo versions, and remote LFS objects remain unchanged
