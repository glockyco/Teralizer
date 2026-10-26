# reporting/report-execution Specification

## Purpose

Define one complete report-run boundary from validated inputs through staged artifacts so failures
cannot expose an output assembled from only part of the selected report set.

## Requirements

### Requirement: A run builds and validates every selected report before rendering

A report run SHALL resolve and build every selected report before it writes a rendered artifact to its
final generator location. A failure to resolve, build, or validate any selected report SHALL leave all
final generator and consumer outputs unchanged.

#### Scenario: The last selected report fails to build
- **WHEN** earlier reports built successfully but a later report fails
- **THEN** no selected report is rendered into a final generator location
- **AND** no consumer receives an artifact

#### Scenario: Every selected report builds
- **WHEN** all report results and input snapshots pass validation
- **THEN** rendering may begin from the complete in-memory result set

### Requirement: Renderers emit one typed artifact set

Every renderer SHALL identify each emitted artifact by render target and stable artifact key and SHALL
record the report that produced it. The run SHALL accumulate these records in one artifact set before
promotion or delivery.

Two reports SHALL NOT emit the same artifact key for the same target. The same key MAY exist in
different targets.

#### Scenario: Two reports emit one LaTeX key
- **WHEN** both claim the same target and artifact key
- **THEN** accumulation fails naming the key and both reports
- **AND** no staged artifact is promoted or delivered

#### Scenario: One table has LaTeX and CSV forms
- **WHEN** both forms use the same semantic key under different targets
- **THEN** both artifacts coexist without a collision

#### Scenario: A renderer omits ownership
- **WHEN** an emitted path cannot be traced to one selected report or run-level aggregate
- **THEN** the artifact set is invalid

### Requirement: Rendering occurs under an isolated staging root

Renderers SHALL write only beneath the staging root supplied for the run. Before promotion, the runner
SHALL validate artifact identities, paths, provenance, aggregate outputs, and any consumer declaration
against the staged set.

A build, render, or pre-promotion validation failure SHALL leave final generator output unchanged.

#### Scenario: A renderer fails after another renderer succeeds
- **WHEN** some staged files exist and a later renderer fails
- **THEN** the staging output is discarded
- **AND** final generator and consumer paths remain unchanged

#### Scenario: A staged path escapes its root
- **WHEN** a renderer reports a path outside the run staging root
- **THEN** validation fails before promotion

### Requirement: The run constructs one coherent provenance manifest

A run SHALL construct its provenance manifest from the selected built reports, their declared input
snapshots, and the complete staged artifact set. Manifest construction SHALL NOT branch on a report id
or accept report-supplied input identity.


#### Scenario: A multi-input report is rendered
- **WHEN** one report used several declared inputs
- **THEN** its manifest entry records every input role through the common schema

#### Scenario: A report requests custom manifest structure
- **WHEN** report code attempts to add a report-specific top-level provenance shape
- **THEN** the common result model cannot represent that exception

### Requirement: Manifest replacement follows the selected report scope

A full report-set run SHALL reconstruct the complete manifest from the registry and SHALL remove stale
entries for reports that no longer exist. A partial local run SHALL replace only selected report entries
in memory and SHALL preserve unselected entries until a later full run verifies the whole set.

#### Scenario: A full run removes a report
- **WHEN** a previously recorded report is no longer registered
- **THEN** the reconstructed manifest omits its stale entry

#### Scenario: A partial local run updates selected reports

- **WHEN** only part of the registry is selected
- **THEN** only selected entries are replaced in memory
- **AND** unselected entries remain until a full run verifies the whole set

### Requirement: Promotion occurs only after complete validation

In ordinary report regeneration, the runner SHALL promote staged generator artifacts only after all
selected reports, render targets, manifest entries, artifact identities, and publication declarations
pass. Publication-only consumer delivery SHALL instead use the validated staged set without generator
promotion.


#### Scenario: A consumer declaration is incomplete or stale
- **WHEN** a declared artifact is absent from the staged set or a declared path is unsafe
- **THEN** promotion and delivery do not start

#### Scenario: Final generator promotion succeeds
- **WHEN** ordinary regeneration reaches every generator-owned final path
- **THEN** the promoted artifact set records those final paths

#### Scenario: A report is run locally without publication
- **WHEN** no consumer destination is supplied
- **THEN** the same build, staging, validation, and generator-promotion boundary applies
- **AND** consumer delivery is skipped

### Requirement: Promotion updates only generator-owned paths in scope

A partial run SHALL update only paths owned by its selected reports and run-level aggregates. A full
run SHALL reconcile the complete generator-owned output set. Neither mode SHALL remove or overwrite a
consumer-maintained file.

#### Scenario: A partial run promotes its artifacts

- **WHEN** a selected report set completes promotion
- **THEN** only paths owned by selected reports and run-level aggregates are updated
- **AND** consumer-maintained files remain unchanged

### Requirement: The command interface cannot create an incoherent run

The report command SHALL reject unknown render targets, incomplete target coverage required by a
consumer declaration, physical database overrides, and input overrides that do not name a declared
semantic role. These checks SHALL occur before report construction.

#### Scenario: A target name is misspelled
- **WHEN** an invocation requests an unknown render target
- **THEN** it fails before resolving report inputs

#### Scenario: A consumer declares a target the invocation omits
- **WHEN** the selected targets cannot produce every declared target kind
- **THEN** the invocation fails before building any report

### Requirement: Run orchestration is report-agnostic

Input resolution, report construction, validation, rendering, manifest assembly, artifact promotion,
and publication SHALL operate on registered declarations and typed results. They SHALL NOT contain a
branch keyed to a particular report or research question.

#### Scenario: A new report uses existing input and artifact kinds
- **WHEN** it is registered with valid declarations
- **THEN** the existing runner builds, renders, records, and optionally publishes it without modifying
  run orchestration

### Requirement: Publication-only execution leaves the generator worktree unchanged

A publication-only report run MUST build, render, and validate every declared consumer artifact in staging. It MUST deliver the validated set without promoting generated reports, aggregate provenance, or other tracked generator outputs into the source worktree.


#### Scenario: A clean worktree publishes consumer artifacts

- **WHEN** a publication-only run starts from a clean generator worktree and all declared artifacts validate
- **THEN** the consumer receives the complete declared artifact set
- **THEN** the generator worktree remains clean
- **THEN** no tracked report or provenance file changes

#### Scenario: Commit metadata differs from a tracked report snapshot

- **WHEN** rendering would record the current source commit in a generated report or aggregate provenance file
- **THEN** publication uses that metadata in its staged validation and delivered artifacts where required
- **THEN** it does not rewrite the tracked report or aggregate provenance snapshot

#### Scenario: A publication-only run fails

- **WHEN** report construction, rendering, validation, or consumer declaration validation fails
- **THEN** no consumer artifact is delivered
- **THEN** tracked generator outputs remain unchanged
- **THEN** staged outputs are discarded according to the report-run transaction boundary

### Requirement: Publication-only work preserves tracked inputs and experiment state

The run MUST leave tracked source, report, provenance, preserved input, and generated-dataset paths byte-identical to their state before the command. It MUST NOT start a corpus, project, sentinel, hotspot, or benchmark experiment.

Ignored staging or build outputs MAY change. Such outputs MUST NOT be an input selected in place of the run's registered preserved evidence.

#### Scenario: Publication uses ignored staging output

- **WHEN** a publication-only run changes ignored staging or build outputs
- **THEN** tracked paths remain byte-identical and no experiment starts
- **AND** registered preserved evidence is not replaced by ignored output

#### Scenario: The generator worktree is already dirty

- **WHEN** a publication-only command detects tracked or untracked changes outside ignored build outputs
- **THEN** it fails before report construction or consumer delivery
- **THEN** it names the dirty paths that block publication

### Requirement: Publication-only delivery uses the validated staged set

Consumer delivery in publication-only mode MUST use the same staged artifact set that passed report, provenance, identity, path, and declaration validation. It MUST NOT read a tracked generated report as a substitute for a staged artifact.

Publication-only mode MUST preserve the existing all-or-nothing consumer delivery guarantee. Normal report regeneration without publication-only mode MAY continue to promote generator-owned outputs under the existing report-execution contract.

#### Scenario: Staged and tracked generated outputs differ

- **WHEN** a staged artifact differs from a tracked generated report or prior build output
- **THEN** validation and delivery use the staged artifact
- **THEN** the tracked generated output remains unchanged

#### Scenario: Every declared artifact validates

- **WHEN** the full declared set passes validation in publication-only mode
- **THEN** consumer delivery begins from that validated staged set
- **THEN** the command does not require generator promotion before delivery

#### Scenario: A normal report regeneration runs

- **WHEN** the operator runs the ordinary report-regeneration mode without a consumer-only publication request
- **THEN** the existing generator staging and promotion behavior remains available
- **THEN** that run is distinct from the clean-worktree publication-only contract
