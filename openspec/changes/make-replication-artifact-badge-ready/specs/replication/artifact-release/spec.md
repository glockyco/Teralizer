## Purpose

Defines how a complete Teralizer replication release is identified, assembled, verified, documented, extracted, and used without relying on the author's machines or on undeclared files.

## ADDED Requirements

### Requirement: A release manifest declares the complete release set

Every release SHALL contain a machine-readable release manifest that identifies the artifact version, version-specific DOI, concept DOI, source revision, paper identity, and every archive in the release. Each archive entry SHALL state its semantic component id, filename, checksum, byte size, unpacked size, purpose, required companion components, and internal manifest checksum.

#### Scenario: A complete release is assembled

- **WHEN** every declared component and its internal manifest verify
- **THEN** the release manifest names exactly those components and their measured facts
- **AND** the release is eligible for validation

### Requirement: Release membership comes only from the manifest declaration

Release membership SHALL come only from this declaration. A release builder SHALL NOT infer membership from filename patterns, directory contents, a fixed archive count, or files left by an earlier build.

#### Scenario: An output directory contains an old archive

- **WHEN** an archive exists that is not declared by the release being built
- **THEN** it is not included in the release or its checksum inventory

#### Scenario: A declared component is absent

- **WHEN** any declared archive, required input, or internal manifest is missing or invalid
- **THEN** release assembly fails naming every disagreement
- **AND** no incomplete release replaces the previous complete set

### Requirement: Release assembly is clean and atomic

A release SHALL be assembled from a committed source revision, every required submodule materialized at its recorded gitlink commit, a complete verified four-corpus package, a complete registered report run, every database and file input declared by that run, and explicitly declared workflow-specific project and data inputs. Assembly SHALL stage all release files separately, verify the staged set, and promote the complete set atomically.

#### Scenario: A release is built from verified inputs

- **WHEN** the source, corpus package, report artifacts, and declared optional components all verify
- **THEN** assembly produces one complete staged release with their recorded identities

#### Scenario: A late archive build fails

- **WHEN** one or more archives have staged successfully and a later archive fails
- **THEN** the prior published or locally promoted release remains unchanged
- **AND** the staged partial release is ineligible for upload

### Requirement: Release inputs have recorded attribution and isolated assembly

The release SHALL record every input revision and dirty state. Production release assembly SHALL reject dirty, unattributed, unresolved, source-checkout-fallback, or unintended duplicate inputs. After verified inputs exist, assembly SHALL NOT require a corpus database, author workstation, evaluation host, ambient ignored directory, or nested Git worktree.

#### Scenario: The source tree is dirty

- **WHEN** production release assembly observes source or declared inputs that differ from their recorded revisions
- **THEN** it fails before promoting any release file

### Requirement: Staged inputs have explicit payload dispositions and owners

The release SHALL prove that each registered report's declared inputs resolve inside the staged release and that every producing source, nested source, configuration, report output, and workflow input has one payload disposition or an explicit non-payload disposition. Every archive member path SHALL have exactly one owner.

#### Scenario: A report file input exists only in the source checkout

- **WHEN** a registered report declares a file input that no staged component owns
- **THEN** release validation fails with the report id, input role, and unresolved path
- **AND** clean archive acceptance cannot obtain the file from the source checkout

#### Scenario: A required submodule contains only a gitlink

- **WHEN** staged source records a submodule commit but omits the source bytes required by build or execution
- **THEN** release validation fails naming the submodule, commit, and consuming workflow

### Requirement: Large independent payloads are fine-grained and components remain isolated

Every downloadable archive SHALL contain a component manifest, release identity, purpose, payload inventory, dependencies, and applicable retained license files. Each archive SHALL use a unique semantic component identity independent of its filename and SHALL contain one declared wrapper root.

#### Scenario: A reviewer reproduces only RQ0

- **WHEN** the reviewer selects the RQ0 reproduction workflow
- **THEN** the release documentation requires core and the two JARVIS corpus components
- **AND** it does not require the controlled corpus, real-world corpus, or unrelated project components


### Requirement: Core and optional archives have isolated extraction roots

Core SHALL own the `teralizer/` workspace tree and complete reviewer guidance. Each optional archive SHALL write only one unique `teralizer/components/<component-id>/` subtree and include a short generated README pointing to the standalone release manifest and core guidance. A reviewer SHALL be able to extract core and selected optional archives with standard ZIP tooling into one clean workspace.

#### Scenario: A user extracts several components

- **WHEN** core and selected optional archives are extracted into one clean workspace with standard ZIP tooling
- **THEN** core writes the `teralizer/` workspace tree and each optional archive writes only its unique declared component subtree
- **AND** no archive rewrites or merges another component's payload

### Requirement: Archive validation rejects unsafe or conflicting members

Archive validation SHALL reject unsafe members, duplicate members, paths outside each declared wrapper root, and component-root collisions before publication. The release SHALL NOT provide a component installer, package database, reinstall protocol, archive cache, ownership ledger, or custom extraction API.

#### Scenario: Archive members escape their declared component root

- **WHEN** archive validation finds unsafe, duplicate, out-of-root, or colliding members
- **THEN** publication fails without introducing a custom installer or extraction API

### Requirement: Workflow preflight verifies every selected component

Workflow preflight SHALL verify the standalone release manifest, extracted component manifests, payload checksums, release identity, and explicit component roots. It SHALL name every missing, mixed-release, changed, or incomplete component before execution. Workflows SHALL write only to a separate disposable state root and SHALL NOT infer readiness from a nonempty shared directory.

#### Scenario: Extraction is interrupted or a component changes

- **WHEN** preflight finds an incomplete component root or bytes that differ from its manifest
- **THEN** it fails with the component id and the remove-verify-reextract recovery procedure
- **AND** it does not repair, merge, or overwrite that directory

### Requirement: Large independent payloads are separate only when workflows benefit

The release SHALL keep source, runtime support, registered results, compact report inputs, and small backing evidence in core. It SHALL publish each large semantic corpus and independent project family as a separate component when a documented workflow can omit that payload. It SHALL NOT split small or tightly coupled payloads without a measured download or redistribution benefit.

#### Scenario: Full real-world collection is selected

- **WHEN** the reviewer selects the full real-world collection workflow
- **THEN** preflight requires the self-contained `projects-real-world-full` component
- **AND** it does not require or combine `projects-real-world-sample`

### Requirement: Sample-within-full duplication preserves identical source metadata

A source input MAY feed both alternative real-world scope components only under the declared sample-within-full rule. Its identity, revision, configuration, bytes, and license metadata SHALL match across both components.

#### Scenario: Sample and full components overlap

- **WHEN** the release contains both alternative real-world scopes
- **THEN** every sample project is present in the full component
- **AND** its identity, revision, configuration, source bytes, and license metadata match

### Requirement: JARVIS evidence has a complete, non-duplicated release chain

The release SHALL bind RQ0 JARVIS evidence to the `jarvis-scenarios` and `jarvis-benchmark` corpus packages, declared scorecard and census fixture/config inventories, accepted run status and completion evidence, `jarvis-value-facts.json`, `cut_values.tsv`, retained raw CUT-PVC captures, and every registered RQ0 report artifact. Each link SHALL record a checksum, provenance, payload owner, and whether the reviewer workflow inspects frozen evidence or regenerates it.

#### Scenario: A reviewer reproduces RQ0 reports

- **WHEN** the reviewer extracts core and the two declared JARVIS corpus components and runs RQ0 reproduction
- **THEN** the report resolves every JARVIS database and file input from those verified component roots
- **AND** verification compares every declared RQ0 report, table, CSV, macro, and provenance artifact

### Requirement: Compact JARVIS facts retain their complete source-log lineage

The release SHALL bind every source value log declared by the validated `jarvis-value-facts.json` manifest through recorded counts and aggregate checksums.

#### Scenario: Compact facts have no backing logs

- **WHEN** `jarvis-value-facts.json` is present but its declared source-log count or aggregate checksum cannot be verified from the evidence payload
- **THEN** release validation fails the JARVIS evidence chain

#### Scenario: CUT values have no retained capture lineage

- **WHEN** `cut_values.tsv` is declared regenerable but the raw captures, fixture revisions, capture plan, or aggregate checksum are absent
- **THEN** release validation rejects the regenerability claim
- **AND** the release cannot silently relabel the values as independently reproduced


### Requirement: JARVIS lineage does not require undeclared author state

The release SHALL NOT require complete author working run roots when selected source evidence and corpus packages close the declared lineage. It SHALL NOT use a stale detached completion marker, an alternate source-cache path, or a source-checkout file as an implicit substitute for declared payload.

#### Scenario: A JARVIS evidence input exists only in author state

- **WHEN** a required lineage link is available only through an undeclared author path or stale marker
- **THEN** release validation rejects that substitute
### Requirement: Public requirements and metadata state exact release facts

The release SHALL provide one authoritative reviewer entry point and a dedicated requirements document readable without executing artifact code. Together they SHALL state exact download and unpacked sizes, peak free-disk requirement and scope, memory, CPU architecture, supported hosts, required host software, network use, setup and results-reproduction time, reduced and full collection times, archive combinations per workflow, and cleanup effects.

#### Scenario: A reader evaluates feasibility before download

- **WHEN** the reader opens the release landing page or packaged requirements document
- **THEN** they can determine every archive, resource, architecture, network, and time requirement for the chosen workflow without running the artifact

### Requirement: Human-readable release requirements agree with verified manifests

Human-readable values SHALL be generated or checked against verified release and corpus manifests. Placeholders such as "manifest-derived", commands needed to discover basic requirements, and release values copied from a superseded version SHALL make release validation fail.

#### Scenario: A documented size is stale

- **WHEN** a documented archive or peak-disk value differs from the verified manifests
- **THEN** release validation fails naming the document, field, expected value, and observed value

### Requirement: Public metadata agrees with the packaged release

The Zenodo description, citation metadata, badge claims, paper link, licenses, archive inventory, commands, and database terminology SHALL agree with the packaged documentation before publication.

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


#### Scenario: A redistributed project has an established license

- **WHEN** a project archive contains that project
- **THEN** its component manifest and third-party notice identify the origin, revision, license, attribution, redistribution decision, and retained license text

#### Scenario: Redistribution permission is unclear

- **WHEN** no license or other redistribution basis can be established for a project
- **THEN** release validation rejects its source payload
- **AND** the release may retain only legally distributable provenance and retrieval instructions

### Requirement: Shared reuse and licensing guidance is complete in core

The release SHALL document the corpus selection source, whether human-participant or sensitive data is present, and ethical or legal constraints relevant to reuse. Core SHALL own shared schema, provenance, citation, and licensing guidance.

#### Scenario: A user downloads only core

- **WHEN** core is opened without any corpus or project component or the Zenodo page
- **THEN** it identifies the study, release, citation, licenses, report and evidence schemas, provenance, included results, and available workflows

### Requirement: Independent components retain identity and licensing guidance

Each independently downloadable corpus or project component SHALL carry machine-readable identity and provenance in its component manifest, applicable retained license files, and a short generated README pointing to shared guidance.

#### Scenario: A user downloads one corpus component

- **WHEN** a corpus archive is opened without core or the Zenodo page
- **THEN** its manifest identifies the release, semantic corpus, payload, provenance, licenses, and required companion components
- **AND** its short README identifies the extraction root and points to the authoritative release manifest and core guidance

### Requirement: Project inventories verify existing licensing declarations

The project inventory SHALL verify established license identifiers and retained upstream license files against packaged bytes. It SHALL NOT require a new license-classification subsystem or repeated adjudication of unchanged declared inputs.

#### Scenario: An unchanged licensed project is packaged

- **WHEN** the project inventory verifies its declared license identity and retained upstream license files
- **THEN** verification compares those declarations with packaged bytes without new license adjudication
