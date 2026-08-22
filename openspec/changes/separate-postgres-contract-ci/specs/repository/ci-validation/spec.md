## Purpose

Defines fast repository validation and focused real-database contract verification without making unrelated changes depend on PostgreSQL service setup.

## ADDED Requirements

### Requirement: Default analysis validation is database-independent

The repository SHALL run commit hooks, strict OpenSpec validation, and the marker-free analysis test suite without starting, connecting to, or preparing PostgreSQL. These checks SHALL report their results independently of the PostgreSQL contract path.

#### Scenario: An ordinary source change is validated

- **WHEN** push or pull-request validation runs for a change outside the PostgreSQL contract input set
- **THEN** the default analysis checks run without a PostgreSQL service
- **AND** no database setup failure can prevent those checks from reporting their results

#### Scenario: A default analysis check fails

- **WHEN** a hook, OpenSpec artifact, or marker-free test fails
- **THEN** the default analysis path fails naming that check
- **AND** it does not attribute the failure to an absent corpus or database service

### Requirement: PostgreSQL contract validation is isolated and input-scoped

The repository SHALL run its real PostgreSQL contract in a separate validation path when a push or pull request changes the database schema, derived views, corpus registry or preparation boundary, database connection boundary, synthetic fixture, contract workflow, or a dependency needed to execute that contract. The contract path SHALL also support explicit manual execution.

A change outside that declared input set SHALL NOT start PostgreSQL merely to run the default analysis checks.

#### Scenario: A database contract input changes

- **WHEN** validation detects a change to one of the declared PostgreSQL contract inputs
- **THEN** it schedules the isolated PostgreSQL contract path
- **AND** the default analysis path remains independently runnable

#### Scenario: An unrelated renderer changes

- **WHEN** validation detects only a report-rendering change outside the declared PostgreSQL contract inputs
- **THEN** it does not start the PostgreSQL contract service
- **AND** the default analysis checks still run

#### Scenario: An operator requests the contract explicitly

- **WHEN** an operator manually dispatches PostgreSQL contract validation at a selected revision
- **THEN** the same isolated contract and acceptance checks execute for that revision

### Requirement: The PostgreSQL contract uses synthetic semantic inputs

The PostgreSQL contract SHALL use an ephemeral supported PostgreSQL service and the tracked synthetic corpus fixture only. It SHALL resolve the corpus by semantic id, apply the canonical base schema, verify the declared project count, prepare the derived schema twice, prove the expected derived-schema revision and views, prove report-role reads, and prove that PostgreSQL refuses a report-role write.

The contract SHALL NOT download, restore, query, mutate, or infer success from any registered production corpus, corpus package, author database, report output, or collection result. A failed checkpoint SHALL fail the contract and identify the operation that did not satisfy the contract.

#### Scenario: The synthetic lifecycle contract passes

- **WHEN** the canonical schema, registry, preparation boundary, connection boundary, and synthetic fixture agree
- **THEN** both preparation attempts succeed with the same derived-schema revision and project count
- **AND** the report role reads the corpus while PostgreSQL refuses its attempted write

#### Scenario: The report role can modify the fixture

- **WHEN** the report role successfully changes a synthetic corpus base row
- **THEN** the PostgreSQL contract fails rather than treating command success as an accepted result

#### Scenario: Production corpus state is unavailable

- **WHEN** PostgreSQL contract validation runs in a clean CI environment with no production corpus or package
- **THEN** the contract uses only the tracked synthetic fixture and succeeds when its declared behavior holds
