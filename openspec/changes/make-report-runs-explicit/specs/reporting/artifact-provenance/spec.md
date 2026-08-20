## ADDED Requirements

### Requirement: Provenance records every declared report input

Generated report provenance SHALL identify every corpus, repository file, and tracked repository tree
declared by the report run. Input records SHALL be organized by semantic role and SHALL use one common
schema for every report.

A corpus input record SHALL include its semantic corpus id and the verified registry state used by the
run. A present file or tree input SHALL include its repository-relative location and stable content
identity. An absent optional input SHALL be recorded as absent.

#### Scenario: An artifact is produced from two corpora
- **WHEN** its report declares two corpus roles
- **THEN** provenance records both roles and both semantic corpus ids
- **AND** neither identity is reconstructed from a value asserted by the report builder

#### Scenario: An artifact uses a versioned audit file
- **WHEN** the report declares that file as an input
- **THEN** provenance records its repository-relative path, content identity, source revision, and dirty
  state

#### Scenario: An optional file is absent
- **WHEN** absence has a defined report meaning
- **THEN** provenance records the input role and its absent state

### Requirement: Input provenance is captured by the run boundary

The runner SHALL capture input snapshots before report construction and SHALL verify that repository
inputs did not change before rendering. Report code SHALL NOT construct, replace, or omit its own input
provenance.

#### Scenario: A builder reports a different database identity
- **WHEN** a builder would name an input differently from the resolved context
- **THEN** no builder-supplied input identity exists to override the runner snapshot

#### Scenario: A repository input changes during the run
- **WHEN** its content identity differs after report construction
- **THEN** rendering and publication are refused

### Requirement: Input changes are visible without changing code provenance

Changing a declared input SHALL update the report's input provenance without attributing a new commit
to unchanged producing code. Unchanged code and unchanged declared inputs SHALL continue to regenerate
byte-identical provenance and artifacts.

#### Scenario: Audit labels change but report code does not
- **WHEN** a committed audit input changes and the producing source file does not
- **THEN** the input snapshot changes
- **AND** the producing code commit remains the last commit that changed the source file

#### Scenario: Neither code nor inputs change
- **WHEN** the report set is regenerated
- **THEN** its provenance manifest is byte-identical

### Requirement: Publishing requires publishable declared inputs

Publishing SHALL be refused when a present declared repository input has uncommitted changes, under the
same documented local-iteration override that governs producing source. An override SHALL preserve the
input's dirty state in provenance.

#### Scenario: A declared audit file has uncommitted labels
- **WHEN** publication runs without the dirty-provenance override
- **THEN** publication is refused before rendering or consumer delivery

#### Scenario: Local iteration uses the override
- **WHEN** a declared input is dirty and the documented override is active
- **THEN** local rendering may continue
- **AND** the manifest records that input as dirty
