## Purpose

Defines the bounded, isolated, evidence-backed workflows by which an artifact reviewer or replicator extracts Teralizer, checks its results, reruns representative collection, and understands its reuse and security boundaries.

## ADDED Requirements

### Requirement: The supported reviewer environment is explicit and reproducible

The artifact SHALL declare at least one release-tested commodity host architecture and operating environment. Its primary reviewer path SHALL run every nontrivial software dependency in a pinned container or virtual-machine environment and SHALL require only the listed host runtime and archive tools. Dependency resolution SHALL be frozen and SHALL fail rather than substitute an unpinned version.

#### Scenario: A reviewer uses the declared baseline

- **WHEN** the reviewer starts from a clean machine that satisfies the published baseline
- **THEN** the documented path extracts, verifies, and exercises the artifact without an undeclared host dependency

#### Scenario: A locked dependency is unavailable or inconsistent

- **WHEN** the environment cannot obtain or verify a declared dependency or image digest
- **THEN** setup fails naming that dependency
- **AND** it does not continue with an unlocked fallback

### Requirement: Additional platforms declare their support status

The artifact SHALL state the status of every additionally claimed architecture or operating system as release-tested, compatibility-tested, or unsupported. A reviewer SHALL NOT need an author account, private service, author filesystem path, Git checkout, language toolchain, or evaluation host.

#### Scenario: A user has an unsupported native architecture

- **WHEN** the user runs preflight on an environment outside the declared support matrix
- **THEN** preflight reports the unsupported or emulated status before workflow execution

### Requirement: Getting Started is bounded and proves useful behavior

The artifact SHALL provide one root-level Getting Started path that verifies the selected package, checks exact requirements, starts required services, restores and prepares every corpus needed by the smoke workflow, proves report-role read-only access, runs a representative report operation, and checks its expected output.

#### Scenario: A reviewer completes Getting Started

- **WHEN** the reviewer follows the root-level command on the declared baseline
- **THEN** the command finishes within the stated bound
- **AND** reports each setup and smoke checkpoint as passed

### Requirement: Getting Started has a measured time and cleanup contract

On the declared baseline with the release inputs already downloaded, this path SHALL complete within 30 minutes. Its documentation SHALL state measured setup and smoke times, the expected success summary, created state, and cleanup command.

#### Scenario: Setup fails after creating state

- **WHEN** a service, restore, preparation, or smoke checkpoint fails
- **THEN** the command identifies the failed checkpoint and preserved state
- **AND** provides a deterministic retry or cleanup action

#### Scenario: Getting Started is run again

- **WHEN** the verified setup already exists
- **THEN** the command verifies or reuses compatible state
- **AND** does not fail merely because its own services or ports are active

### Requirement: The paper's results come from the compared outputs

The reference outputs SHALL be the tables, figures, macros and reports the paper uses, so that matching every regenerated output with the reference reproduces the paper's results. The documentation SHALL name where the results of each research question are.

#### Scenario: A reviewer checks one research question

- **WHEN** the reviewer opens the regenerated report of that research question
- **THEN** its tables, figures and numbers are the ones the comparison checked against the reference

### Requirement: JARVIS evidence separates regeneration from collection

For the JARVIS comparison, the documentation SHALL cite the external paper, identify the fixture revisions, the restored JARVIS corpora and the non-database evidence of RQ0, and distinguish report regeneration from scorecard, census, and CUT-PVC collection. Possession of frozen compact facts SHALL NOT be described as independent repetition of their original collection.

#### Scenario: A reviewer regenerates reports from frozen JARVIS facts

- **WHEN** the workflow uses frozen compact facts
- **THEN** the documentation identifies their lineage and does not claim independent repetition of collection

### Requirement: Results reproduction verifies every declared output kind

The results-reproduction workflow SHALL run every registered report through its declared read-only inputs and compare regenerated evidence with the published reference set. Verification SHALL detect missing, extra, and changed report text, tables, machine-readable data, and figure evidence.


#### Scenario: All deterministic evidence matches

- **WHEN** the registered reports reproduce the canonical reference values and structure
- **THEN** verification reports every output category as passed
- **AND** exits successfully

### Requirement: Results comparison verifies content rather than file counts

For deterministic outputs, the comparison SHALL use byte identity or a documented canonical form. For rendered figures or nondeterministic outputs, it SHALL compare underlying values and a documented structural or tolerance rule. Counting files alone SHALL NOT establish content equivalence.

#### Scenario: A figure exists but encodes changed data

- **WHEN** the generated figure count matches but its declared source values differ
- **THEN** verification fails the associated output

#### Scenario: A rendered report is missing

- **WHEN** tables and CSV files match but a declared report is absent
- **THEN** verification fails naming the missing report

### Requirement: Reproduction summaries report every required comparison

The workflow SHALL produce one machine-readable and one human-readable summary that names every output category as passed or failed. It SHALL return failure when any required comparison fails.

#### Scenario: A required comparison fails

- **WHEN** any output-category comparison fails
- **THEN** both summaries identify its failure and the workflow returns failure

### Requirement: Long-running collection has reduced and full paths

Every collection workflow whose full execution exceeds one day or ordinary artifact-review resources SHALL provide a reduced path that exercises the same stages on a declared representative subset and completes within one day on the baseline. Documentation SHALL distinguish the smoke, reduced, and full scopes.

#### Scenario: A reviewer runs the reduced real-world collection

- **WHEN** the reviewer selects the documented subset
- **THEN** the same production stages execute under the declared reduced scope

### Requirement: Collection paths preserve progress and resume completed work

Both reduced and full paths SHALL record progress, per-item outcomes, resumable state, resource caps, expected nondeterminism, and analysis instructions. A full run SHALL resume completed work rather than require a restart after interruption.

#### Scenario: Full collection is interrupted

- **WHEN** a long run stops after completed projects or configurations
- **THEN** its ledger and checkpoints preserve those outcomes
- **AND** rerunning continues from the first incomplete unit

### Requirement: The final archives pass one clean run before publication

Before publication, the final release archives SHALL be extracted with standard ZIP tooling into a new directory, with empty service volumes, on the release-tested baseline. Smoke, all-report reproduction with the evidence verifier, and cleanup SHALL pass there. Source-level tests or a run from the repository checkout SHALL NOT replace this run.

#### Scenario: Source tests pass but the archive omits a required file

- **WHEN** the clean run extracts the final archives and runs the reviewer commands
- **THEN** a missing packaged input fails a command
- **AND** the release is not published until a rebuilt candidate passes

### Requirement: Reuse paths are documented

The artifact SHALL document how to query the published data, what each table, report view and result column means, and how to run Teralizer on a new project in scratch state.

#### Scenario: A researcher runs Teralizer on a new Java project

- **WHEN** they follow the documentation with a supported project shape
- **THEN** the run uses its own scratch state and leaves the published corpora unchanged

#### Scenario: A user inspects a result column

- **WHEN** they consult the packaged data dictionary
- **THEN** they can determine the column's meaning, unit and null semantics without reading implementation code

### Requirement: Untrusted project builds execute within a disposable boundary

The artifact SHALL treat third-party project builds as untrusted execution. Collection SHALL run them in a disposable boundary without author credentials, Docker socket access, or writable mounts over the packaged source. Documentation SHALL state network use, writable state, resource limits, cleanup, and the risk of executing project build logic.

#### Scenario: A project build executes arbitrary logic

- **WHEN** collection invokes third-party Maven or Gradle code
- **THEN** that code is confined to the documented disposable workspace and resource boundary
- **AND** it cannot read author credentials or mutate the packaged source

