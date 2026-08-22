# knowledge-authority Specification

## Purpose

Define one maintained authority for each kind of repository knowledge so that current behavior and
evidence are not shadowed by narrative snapshots.

## Requirements

### Requirement: Knowledge has a subject authority

The repository SHALL keep durable contracts in accepted OpenSpec capability specs, executable facts
in source, configuration, and tests, empirical results in registered reports with provenance, and
operator instructions in the narrowest applicable agent guidance. It SHALL NOT maintain a second
free-standing narrative copy of those facts.

#### Scenario: Reader needs current implementation facts
- **WHEN** a reader needs a stage list, schema inventory, or generated artifact example
- **THEN** the repository identifies the executable declaration or regenerable verification output
  as the authority instead of a manually maintained technical document

#### Scenario: Reader needs measured evidence
- **WHEN** a reader needs a corpus count, rate, or outcome distribution
- **THEN** the repository identifies a registered report and its provenance rather than a prose
  snapshot

### Requirement: Free-standing documentation trees are rejected

Repository validation SHALL reject a tracked `docs/` knowledge tree and SHALL reject live references
to retired technical-document paths.

#### Scenario: Narrative snapshot is added
- **WHEN** a tracked file is added below `docs/`
- **THEN** the ordinary repository validation path fails and identifies the disallowed path

#### Scenario: Retired document remains referenced
- **WHEN** current source, guidance, or tests prescribe a removed technical document as an authority
- **THEN** validation fails and identifies the unresolved reference; OpenSpec artifacts may still
  name the path as historical evidence or as a migration target

### Requirement: OpenSpec configuration contains only OpenSpec configuration

`openspec/config.yaml` SHALL contain the selected workflow schema and SHALL NOT duplicate project
architecture, toolchain, policy, evidence, or repository-navigation prose.

#### Scenario: Planning context is needed
- **WHEN** an agent creates or updates an OpenSpec artifact
- **THEN** it reads current repository guidance and the relevant executable or evidence source on
  demand instead of receiving a duplicated narrative from OpenSpec configuration

#### Scenario: Project narrative is added to OpenSpec configuration
- **WHEN** project-specific context, artifact rules, or operation guidance is added without a
  configuration-only need
- **THEN** repository validation fails

### Requirement: Safety guidance stays at the point of action

Non-obvious destructive-operation and retention rules SHALL be stated in the scoped agent guidance
that governs the affected paths. Volatile inventories, measured sizes, and deferred cleanup notes
SHALL NOT be promoted to durable contracts.

#### Scenario: Agent considers deleting local state
- **WHEN** a path has a non-obvious retention or ownership rule
- **THEN** the applicable scoped guidance states whether the path is regenerable, retained evidence,
  or protected state

### Requirement: Superseded experiments do not remain as parallel fixtures

A completed experiment SHALL be removed after its behavior is covered by the ordinary verification
corpus with an observed golden. Historical rationale remains available from version control.

#### Scenario: Spike behavior is promoted
- **WHEN** the ordinary verification corpus covers the spike cases and records their expected output
- **THEN** the spike project, its run configuration, and guidance that lists it as active are absent
