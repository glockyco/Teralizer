## ADDED Requirements

### Requirement: Verification corpus scheduling is ownership-scoped and bounded

The repository SHALL run the full synthetic verification corpus after a push only when the pushed change intersects the declared verification-corpus input set. That set SHALL cover every tracked source, build input, configuration, fixture, golden, schema, script, and workflow declaration that can change the corpus execution or expected result.

The corpus SHALL remain manually dispatchable and SHALL run on its declared periodic schedule regardless of changed paths. A newer run for the same branch SHALL supersede an in-progress older run. Every corpus job SHALL have a timeout that exceeds the observed successful runtime range and prevents a stalled execution from consuming a runner indefinitely.

Repository validation SHALL prove the trigger boundary against representative included and excluded paths before the workflow change is accepted.

#### Scenario: A corpus owner path changes

- **WHEN** a pushed change modifies a declared verification-corpus input
- **THEN** the full verification corpus is scheduled for that revision

#### Scenario: An unrelated path changes

- **WHEN** a pushed change modifies only paths outside the declared verification-corpus input set
- **THEN** the push does not schedule the full verification corpus
- **AND** ordinary repository validation remains independently runnable

#### Scenario: Scheduled drift detection runs

- **WHEN** the periodic schedule fires without a source change
- **THEN** the full verification corpus runs against the selected default-branch revision

#### Scenario: An operator requests corpus verification

- **WHEN** an operator manually dispatches the verification-corpus workflow for a selected revision
- **THEN** the full verification corpus runs without requiring a matching changed path

#### Scenario: Corpus execution stalls

- **WHEN** a corpus job exceeds its declared runtime bound
- **THEN** the CI system cancels the job and reports a timeout failure

#### Scenario: A newer branch revision supersedes an active run

- **WHEN** a qualifying newer push starts verification for a branch that already has an in-progress corpus run
- **THEN** the older run is cancelled
- **AND** the newer revision becomes the branch's active corpus result
