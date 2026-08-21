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

A full report-set run SHALL reconstruct the complete manifest from the registry and SHALL remove stale
entries for reports that no longer exist. A partial local run SHALL replace only selected report entries
in memory and SHALL preserve unselected entries until a later full run verifies the whole set.

#### Scenario: A full run removes a report
- **WHEN** a previously recorded report is no longer registered
- **THEN** the reconstructed manifest omits its stale entry

#### Scenario: A multi-input report is rendered
- **WHEN** one report used several declared inputs
- **THEN** its manifest entry records every input role through the common schema

#### Scenario: A report requests custom manifest structure
- **WHEN** report code attempts to add a report-specific top-level provenance shape
- **THEN** the common result model cannot represent that exception

### Requirement: Promotion occurs only after complete validation

The runner SHALL promote staged generator artifacts only after all selected reports, render targets,
manifest entries, artifact identities, and publication declarations pass. Consumer delivery SHALL begin
only after generator promotion succeeds.

A partial run SHALL update only paths owned by its selected reports and run-level aggregates. A full
run SHALL reconcile the complete generator-owned output set. Neither mode SHALL remove or overwrite a
consumer-maintained file.

#### Scenario: A consumer declaration is incomplete or stale
- **WHEN** a declared artifact is absent from the staged set or a declared path is unsafe
- **THEN** promotion and delivery do not start

#### Scenario: Final generator promotion succeeds
- **WHEN** every staged artifact reaches its generator-owned final path
- **THEN** consumer delivery may resolve the already validated artifact set

#### Scenario: A report is run locally without publication
- **WHEN** no consumer destination is supplied
- **THEN** the same build, staging, validation, and generator-promotion boundary applies
- **AND** consumer delivery is skipped

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
