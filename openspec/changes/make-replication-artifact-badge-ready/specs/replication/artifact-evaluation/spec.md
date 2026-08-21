## Purpose

Defines the bounded, isolated, evidence-backed workflows by which an artifact reviewer or replicator installs Teralizer, checks its results, reruns representative collection, and understands its reuse and security boundaries.

## ADDED Requirements

### Requirement: The supported reviewer environment is explicit and reproducible

The artifact SHALL declare at least one release-tested commodity host architecture and operating environment. Its primary reviewer path SHALL run every nontrivial software dependency in a pinned container or virtual-machine environment and SHALL require only the listed host runtime and archive tools. Dependency resolution SHALL be frozen and SHALL fail rather than substitute an unpinned version.

The artifact SHALL state the status of every additionally claimed architecture or operating system as release-tested, compatibility-tested, or unsupported. A reviewer SHALL NOT need an author account, private service, author filesystem path, Git checkout, language toolchain, or evaluation host.

#### Scenario: A reviewer uses the declared baseline

- **WHEN** the reviewer starts from a clean machine that satisfies the published baseline
- **THEN** the documented path installs and exercises the artifact without an undeclared host dependency

#### Scenario: A locked dependency is unavailable or inconsistent

- **WHEN** the environment cannot obtain or verify a declared dependency or image digest
- **THEN** setup fails naming that dependency
- **AND** it does not continue with an unlocked fallback

#### Scenario: A user has an unsupported native architecture

- **WHEN** the user runs preflight on an environment outside the declared support matrix
- **THEN** preflight reports the unsupported or emulated status before installation

### Requirement: Getting Started is bounded and proves useful behavior

The artifact SHALL provide one root-level Getting Started path that verifies the selected package, checks exact requirements, starts required services, restores and prepares every corpus needed by the smoke workflow, proves report-role read-only access, runs a representative report operation, and checks its expected output.

On the declared baseline with the release inputs already downloaded, this path SHALL complete within 30 minutes. Its documentation SHALL state measured setup and smoke times, the expected success summary, created state, and cleanup command.

#### Scenario: A reviewer completes Getting Started

- **WHEN** the reviewer follows the root-level command on the declared baseline
- **THEN** the command finishes within the stated bound
- **AND** reports each setup and smoke checkpoint as passed

#### Scenario: Setup fails after creating state

- **WHEN** a service, restore, preparation, or smoke checkpoint fails
- **THEN** the command identifies the failed checkpoint and preserved state
- **AND** provides a deterministic retry or cleanup action

#### Scenario: Getting Started is run again

- **WHEN** the verified setup already exists
- **THEN** the command verifies or reuses compatible state
- **AND** does not fail merely because its own services or ports are active

### Requirement: Supported paper claims map to executable evidence

The artifact SHALL contain a claims-to-evidence matrix covering every paper claim that it represents as supported. Each entry SHALL identify the paper section or research question, semantic corpus inputs, non-database inputs, command, output artifacts, expected values or invariants, comparison rule and tolerance, expected runtime, and whether the workflow regenerates evidence or only inspects it.

The matrix SHALL list paper claims not supported by the artifact and explain the boundary. A report name alone or a statement that differences are expected SHALL NOT substitute for an acceptance rule.

#### Scenario: A reviewer chooses one research question

- **WHEN** the reviewer follows its matrix entry
- **THEN** the named command consumes the declared inputs and produces the named evidence
- **AND** the verification result explains whether the paper claim passed

#### Scenario: A claim depends on stochastic collection

- **WHEN** exact reproduction is not expected
- **THEN** the matrix states the invariant or tolerance that remains meaningful
- **AND** verification distinguishes an accepted variation from a contradiction of the claim

#### Scenario: A claim has no distributable evidence

- **WHEN** legal, ethical, or practical constraints exclude evidence required for a paper claim
- **THEN** the matrix marks that claim unsupported and states the reason

### Requirement: Results reproduction verifies every declared output kind

The results-reproduction workflow SHALL run every registered report through its declared read-only inputs and compare regenerated evidence with the published reference set. Verification SHALL detect missing, extra, and changed report text, tables, machine-readable data, and figure evidence.

For deterministic outputs, the comparison SHALL use byte identity or a documented canonical form. For rendered figures or nondeterministic outputs, it SHALL compare the underlying values and a documented structural or tolerance rule. Counting files alone SHALL NOT establish content equivalence.

The workflow SHALL produce one machine-readable and one human-readable summary that names every paper claim and output category as passed or failed. It SHALL return failure when any required comparison fails.

#### Scenario: All deterministic evidence matches

- **WHEN** the registered reports reproduce the canonical reference values and structure
- **THEN** verification reports every supported claim and output category as passed
- **AND** exits successfully

#### Scenario: A figure exists but encodes changed data

- **WHEN** the generated figure count matches but its declared source values differ
- **THEN** verification fails the associated output and paper claim

#### Scenario: A rendered report is missing

- **WHEN** tables and CSV files match but a declared report is absent
- **THEN** verification fails naming the missing report

### Requirement: Long-running collection has reduced and full paths

Every collection workflow whose full execution exceeds one day or ordinary artifact-review resources SHALL provide a reduced path that exercises the same stages on a declared representative subset and completes within one day on the baseline. Documentation SHALL distinguish the smoke, reduced, and full scopes and SHALL state which paper claims each can and cannot validate.

Both reduced and full paths SHALL record progress, per-item outcomes, resumable state, resource caps, expected nondeterminism, and analysis instructions. A full run SHALL resume completed work rather than require a restart after interruption.

#### Scenario: A reviewer runs the reduced real-world collection

- **WHEN** the reviewer selects the documented subset
- **THEN** the same production stages execute under the declared reduced scope
- **AND** the result states which full-study claims the run does not establish

#### Scenario: Full collection is interrupted

- **WHEN** a long run stops after completed projects or configurations
- **THEN** its ledger and checkpoints preserve those outcomes
- **AND** rerunning continues from the first incomplete unit

### Requirement: Release acceptance executes the archive as a stranger would

Before a release is eligible for publication, an automated acceptance run SHALL start from the staged downloadable archives in a new filesystem location, with empty service volumes and no access to the source checkout, author databases, author host, package-builder state, or undeclared credentials. It SHALL install the documented archive combination, complete Getting Started, reproduce all registered reports through read-only connections, verify the claims summary, test cleanup, and prove that the primary reviewer workflow remains usable without network access after declared setup dependencies are present.

The acceptance record SHALL identify the release manifest checksum, host and container architecture, image digests, measured times and peak resources, commands, and results. Source-level tests or a run from the repository checkout SHALL NOT count as release acceptance.

#### Scenario: Source tests pass but the archive omits a required file

- **WHEN** the clean acceptance run installs the staged archive
- **THEN** it fails naming the missing packaged input
- **AND** the release is ineligible for publication

#### Scenario: The archive reproduces all supported claims

- **WHEN** clean acceptance completes every declared checkpoint
- **THEN** its record binds the passing evidence to the exact release manifest and environment

#### Scenario: Analysis attempts an author-only connection

- **WHEN** a packaged report resolves a private database or host dependency
- **THEN** clean acceptance fails before the release is published

### Requirement: Reuse and untrusted-code boundaries are documented and enforced

The artifact SHALL document how to inspect and query the published data, interpret database and result schemas, add a project, define a project configuration, run Teralizer on new inputs, add or modify a report, and export evidence. Each reuse path SHALL identify stable interfaces and distinguish them from release-only internals.

The artifact SHALL treat third-party project builds as untrusted execution. Collection SHALL run them in a disposable boundary without author credentials, Docker socket access, or writable mounts over the packaged source. Documentation SHALL state network use, writable state, resource limits, cleanup, and the risk of executing project build logic.

#### Scenario: A researcher adds a new Java project

- **WHEN** they follow the reuse documentation using a supported project shape
- **THEN** the artifact validates its configuration and runs it in scratch state without modifying a published corpus

#### Scenario: A project build executes arbitrary logic

- **WHEN** collection invokes third-party Maven or Gradle code
- **THEN** that code is confined to the documented disposable workspace and resource boundary
- **AND** it cannot read author credentials or mutate the packaged source

#### Scenario: A user inspects a result column

- **WHEN** they consult the packaged data documentation
- **THEN** they can determine the column's meaning, unit, null semantics, and provenance without reading implementation code
