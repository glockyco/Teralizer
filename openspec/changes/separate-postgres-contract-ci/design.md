## Context

See `proposal.md` for motivation and `specs/repository/ci-validation/spec.md` for the contract. The current `analysis` job declares a PostgreSQL service before any step runs, then prepares a 13-row synthetic controlled corpus before hooks, OpenSpec validation, and marker-free tests. Those later checks do not consume the service, but a connection error in preparation prevents all of them from running.

Teralizer applications use `DB_HOST`, `DB_PORT`, `DB_USER`, and `DB_PASSWORD`. Native PostgreSQL clients use libpq defaults unless callers translate those values. `scripts/lib/psql.sh` is the maintained translation boundary for shell queries. The corpus registry remains the only owner of the physical database selected by semantic id `controlled`.

The real-database proof remains valuable because unit tests replace the report-role configuration and database operations with test doubles. Only PostgreSQL can prove that the canonical DDL and views execute and that the report role cannot write.

## Goals / Non-Goals

**Goals:**

- Let hooks, OpenSpec validation, and marker-free tests run without PostgreSQL availability.
- Preserve one real PostgreSQL proof of corpus preparation and report-role enforcement.
- Avoid PostgreSQL startup for changes outside the proof's declared input boundary.
- Keep semantic corpus identity and database connection ownership aligned with accepted contracts.
- Keep workflow dependencies and failure output small enough to diagnose from one CI job.

**Non-Goals:**

- Run a production corpus or any database-marked report test in GitHub Actions.
- Replace the corpus registry, database lifecycle, or report-role implementation.
- Generalize one CI scenario into a database test framework or shell lifecycle API.
- Change reviewer artifact preflight, release acceptance, or long-running collection.
- Add a third-party path-filter action or a reusable workflow with no second caller.

## Decisions

### 1. Use two workflows with independent failure domains

The existing build workflow keeps the database-free `analysis` job and removes its PostgreSQL service and fixture setup. A separate PostgreSQL contract workflow owns the service and synthetic lifecycle scenario.

GitHub Actions creates job services before steps, so a conditional step inside the existing job would not avoid service startup. Separate workflows also let hook, planning, and unit-test failures report even when PostgreSQL setup fails.

**Alternative:** Keep one job and repair its client flags. Rejected because every analysis run would still pay for and depend on an unrelated service.

**Alternative:** Remove PostgreSQL from CI entirely. Rejected because mocks do not verify PostgreSQL DDL, view installation, role grants, or write refusal.

### 2. Use native event path filters and manual dispatch

The PostgreSQL contract workflow runs for pushes and pull requests only when the diff touches an owning input. The input set covers the workflow, base and derived DDL, corpus registry and preparation code, database connection boundary, registry and PostgreSQL shell helpers, the synthetic fixture, and execution dependencies such as the analysis lock and Nix development environment. `workflow_dispatch` provides an explicit backstop.

The database-free analysis workflow remains the universal repository gate. The path-scoped PostgreSQL workflow is not configured as a universally required check because GitHub does not create it for unrelated changes.

**Alternative:** Use a third-party change-detection action inside an always-started workflow. Rejected because it adds a dependency and cannot prevent service startup when services remain on the job.

**Alternative:** Run the contract on every push to `master`. Rejected because it preserves the unrelated cost this change removes; manual dispatch remains available when an operator wants an extra run.

### 3. Resolve corpus identity before database creation

The contract selects semantic id `controlled` and obtains its current physical database from the corpus registry. The workflow does not embed `postgres_dev` or another deployment-specific name. It applies the tracked synthetic fixture to that resolved database.

This makes a registry mapping change one coherent edit: the registry remains authoritative and the contract follows it automatically.

**Alternative:** Preserve the physical literal because the database service is ephemeral. Rejected because executable configuration would still carry a second owner for corpus identity.

### 4. Reuse the connection helper without adding a lifecycle abstraction

All SQL execution uses `teralizer_psql`, which translates Teralizer's `DB_*` settings to native or container transport. The single `createdb` invocation receives explicit host, port, owner, and password values. Report-role checks invoke the same helper with temporary report credentials.

A new `teralizer_createdb` helper is not introduced for one native callsite. The replication importer deliberately owns a different Compose-local lifecycle and is not migrated by this change.

**Alternative:** Export duplicate `PGHOST`, `PGPORT`, and `PGUSER` values beside `DB_*`. Rejected because the two connection descriptions can drift.

**Alternative:** Add generic create/drop helpers and migrate replication scripts. Rejected because it expands a focused CI correction into unrelated lifecycle redesign.

### 5. Keep the one-caller scenario visible in the workflow

The contract workflow performs the short ordered sequence directly: resolve identity, create the ephemeral database, apply base DDL and fixture rows, prepare twice, read as the report role, and prove a write fails. It uses existing production boundaries for every meaningful operation.

A new verification wrapper is deferred until a second caller needs the exact scenario or the scenario gains cleanup, fault injection, or multiple fixture variants. Moving a single short caller into a new script would add indirection without a reusable interface.

### 6. Verify the workflow in its actual host

Local verification uses an isolated PostgreSQL 17.1 service on a non-conflicting port and executes the same contract commands. Static workflow validation and repository gates run before delivery. After the change is pushed, the GitHub Actions PostgreSQL contract result is the final behavioral proof; a local service alone cannot prove GitHub service networking or event configuration.

## Risks / Trade-offs

- **The path set omits a future dependency.** -> Keep the owning input list grouped by contract boundary, include execution locks and environment declarations, and require any interface expansion to update the workflow in the same change. Manual dispatch remains a backstop, not a substitute for ownership.
- **A path-scoped workflow is mistaken for a universal required check.** -> Keep the database-free analysis job universal and document that the PostgreSQL contract exists only for matching events or manual dispatch.
- **The semantic registry resolves a production-looking physical name.** -> Create it only inside the ephemeral CI service and load only the tracked synthetic fixture; never connect to author infrastructure or restore a corpus package.
- **Inline workflow steps grow into an orchestration language.** -> Extract a maintained verification command only when reuse or additional states make the workflow sequence nontrivial.
- **The real contract still adds CI time on database changes.** -> Accept the measured service cost for changes that can break DDL, views, preparation, or role enforcement; avoid it everywhere else.
- **A workflow-only correction passes locally but fails on GitHub networking.** -> Do not claim completion until the pushed workflow succeeds in GitHub Actions.

## Migration Plan

1. Inventory the current synthetic lifecycle operations and declare the complete owning path set.
2. Add the focused PostgreSQL contract workflow with manual dispatch and path-scoped push and pull-request events.
3. Resolve `controlled` through the registry, route queries through the connection helper, and retain the full lifecycle and read-only proof.
4. Remove the PostgreSQL service and fixture preparation from the default analysis job without changing its hooks, OpenSpec validation, or marker-free tests.
5. Validate the workflow syntax, exercise the exact contract against an isolated PostgreSQL 17.1 service, and run repository gates.
6. Push the change and inspect both GitHub Actions workflows. Completion requires the focused PostgreSQL job and the independent analysis job to report successfully.

Rollback restores the prior workflow structure in one commit. It does not mutate external data because both designs use an ephemeral GitHub service and the tracked synthetic fixture.
