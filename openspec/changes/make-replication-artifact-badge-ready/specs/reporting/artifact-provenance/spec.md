## MODIFIED Requirements

### Requirement: Provenance names the commit of the code that produced the artifact

An artifact's recorded commit SHALL be the last commit that changed the source file defining the function which produced it. It SHALL NOT be the current checkout position, because that position is unrelated to when the producing code last changed.

A recorded source reference SHALL resolve to the producing lines as they stood in the recorded commit.

#### Scenario: The producing source has not changed recently

- **WHEN** an artifact is generated from a source file whose last change predates the checkout position
- **THEN** the recorded commit is that source file's last change
- **AND** the source reference resolves to that commit

#### Scenario: An unrelated commit is made

- **WHEN** a commit changes files unrelated to a report, and the report is regenerated
- **THEN** the artifact's recorded commit is unchanged

## ADDED Requirements

### Requirement: Packaged report attribution uses verified embedded source revisions

A verified replication release without Git metadata SHALL embed per-source revisions preserving producing-code attribution. Report generation SHALL resolve source revisions from embedded release provenance. It SHALL NOT invent a checkout position, mark every source uncertain, or require the replicator to obtain repository history.

#### Scenario: A packaged release has no Git directory

- **WHEN** a replicator regenerates reports from a verified release whose embedded provenance covers every producing source
- **THEN** each artifact records the same per-source revision as generation from the corresponding source checkout
- **AND** no Git repository is required

### Requirement: Packaged source attribution requires complete matching provenance

Report generation from a verified replication release SHALL fail when embedded provenance is absent, does not cover a producing source, or does not match the packaged source bytes.

#### Scenario: Packaged source differs from embedded provenance

- **WHEN** a producing source file's checksum disagrees with the embedded release provenance
- **THEN** report generation fails naming that source before attributing or publishing an artifact

#### Scenario: Embedded provenance is incomplete

- **WHEN** a packaged report uses a producing source absent from the embedded provenance
- **THEN** report generation fails naming the absent source and provenance record
