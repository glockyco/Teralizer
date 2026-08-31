## ADDED Requirements

### Requirement: Publication-only execution leaves the generator worktree unchanged

A publication-only report run MUST build, render, and validate every declared consumer artifact in staging. It MUST deliver the validated set without promoting generated reports, aggregate provenance, or other tracked generator outputs into the source worktree.

The run MUST leave tracked source, report, provenance, preserved input, and generated-dataset paths byte-identical to their state before the command. It MUST NOT start a corpus, project, sentinel, hotspot, or benchmark experiment.

Ignored staging or build outputs MAY change. Such outputs MUST NOT be an input selected in place of the run's registered preserved evidence.

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
