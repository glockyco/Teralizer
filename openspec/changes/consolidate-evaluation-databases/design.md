## Context

See proposal.md - Why.

Measurement is finished, but reproducibility work is not. The corpus databases are immutable empirical
inputs. The scripts, configuration, view definitions, and report code that restore or read them remain
live because a replicator or a future correction must be able to run them again.

The current tree already contains `src/main/resources/db/corpora.toml` with semantic ids and current
physical database names. Reports increasingly resolve input roles through that registry. The remaining
risk comes from physical names embedded in runners and configuration, incomplete local installations,
and derived views whose installed definition is not verified before a report reads them.

The evaluation host holds the complete corpus set. A local workstation may validly hold only a subset.
Publication and local analysis therefore need different completeness modes while sharing the same
identity and integrity checks.

`separate-replication-data-from-source` owns the later source-storage cutover. This change remains the
authority for corpus identity, export, transfer, manifest assembly, validation, and import semantics;
the successor removes checkout-owned production outputs and requires its existing package boundary at
an explicit external path. It does not fork the publisher or redefine the corpus manifest.

The workstation reaches the complete PostgreSQL service through an SSH-forwarded port. Running
`pg_dump` on the workstation makes PostgreSQL send logical rows through that tunnel before the client
compresses them. The corpus databases are tens of gigabytes, while their compressed custom-format
archives are small enough to transfer as files. Publication must therefore create each compressed dump
beside PostgreSQL and transfer the archive instead of treating the forwarded port as a bulk-export
transport.

## Goals / Non-Goals

**Goals:**

- One stable semantic identity for each published corpus.
- Future reruns use the same registry as reports and publication.
- Restored corpora have known base data and known derived-view definitions before becoming read-only.
- Publication is reproducible from a dump and declared non-database inputs.
- Publication checkpoints each corpus durably and resumes without repeating verified exports.
- Partial local installations are valid for explicitly requested corpora; publication is complete.
- Retirement decisions are evidence-backed and leave no runnable stale consumer.

**Non-Goals:**

- Renaming the four physical databases that currently back published corpora.
- Re-running an evaluation merely to adopt semantic ids.
- Treating mutable scripts or configuration as frozen historical evidence.
- Shipping bulk logs or every intermediate database.
- Installing the analysis package on the evaluation host. That host performs only database-local export.
- Excluding database objects through physical-name wildcards or redesigning schemas before a
  server-local export demonstrates a separate bottleneck.
- Teaching Java a second registry implementation when a launcher can provide resolved connection
  settings.

## Decisions

### 1. The registry owns semantic identity and current physical resolution

Each entry carries a stable corpus id, current physical database name, data and configuration paths,
expected project count, publication status, and notes needed to distinguish similarly shaped corpora.
The id describes the empirical condition, not a machine role, research question, version counter, or
storage location.

Live callers accept a corpus id. One Python accessor and a thin shell command resolve the entry and
expose individual fields without duplicating TOML parsing. Launchers pass the resolved connection
settings to Java and other programs that already consume environment or configuration values.

A physical-name check scans live source, runner scripts, configuration, and packaging. It permits names
only in the registry, database lifecycle code that must address PostgreSQL, and generated provenance
that explicitly records the resolved endpoint. There is no blanket exemption for `project-configs/**`
or run scripts.

### 2. Semantic ids replace physical renaming

The four published corpora keep their current database names. No `ALTER DATABASE`, compatibility
alias, duplicated service, or renamed dump is needed. Consumers stop depending on those names, so a
future deployment may change them by editing one registry entry and regenerating provenance.

This is safer than the earlier rename plan. A physical rename would change scripts, dumps, protection
rules, host operations, and historical diagnostics while adding no semantic information beyond the
registry id. It would also create misleading churn in provenance-bearing output.

`postgres_test` is not mapped to a new id. It is retired only after a consumer audit and row-count check
show that no published report reads it. The verification fixture is scratch and uses a reserved
`scratch_` name because its runner recreates it.

### 3. Run machinery is live; provenance makes historical runs historical

Runner scripts, project configurations, corpus definitions, and view-installation SQL are executable
reproducibility machinery. They are migrated to semantic ids and current registry resolution. An old
run remains attributable through its commit, corpus manifest, attempt ledger, and generated provenance;
stale executable literals are not preserved as a substitute for history.

A run records the semantic corpus id, resolved physical database name, source commit, dirty state,
corpus-definition inputs, and view-definition revision. This keeps physical deployment information as
observed provenance without making it an API.

### 4. Corpus preparation is explicit, idempotent, and precedes read-only access

A single `prepare-corpus <id>` operation owns derived schema. It:

1. resolves and verifies the registered database and expected project count;
2. applies the checked-in `create-views.sql` transactionally as an owner role;
3. records the canonical checksum of that SQL as the installed view-definition revision;
4. verifies that required views exist and can be queried; and
5. enables or verifies the report read-only role.

The base measurement tables are never rewritten. Views and indexes are derived schema and may be
recreated from checked-in source after restore. Report preflight compares the installed revision with
the current checked-in revision and refuses a mismatch. A report must not silently use whichever
materialized-view version happened to survive in a restored database.

The preparation operation replaces ad hoc `create-views.sql` calls. Restore, local setup, the evaluation
host, and the replication quick start all use it.

### 5. Verification has requested-subset and complete-publication modes

`verify-corpora` without an explicit request inventories every database it can see, reports registered,
scratch, missing, and unclassified entries, and fails on malformed registry entries or collisions. It
does not treat a missing unrelated corpus as an error on a valid partial workstation.

`verify-corpora --require <id>...` verifies the named subset completely: database presence, expected
project count, corpus-definition paths, preparation revision, and read-only access.

Publication uses `verify-corpora --published` and requires every registry entry marked published. This
prevents a partial workstation from producing a plausible incomplete replication artifact.

### 6. Lifecycle classes are semantic corpus and disposable scratch

A database is either:

- a registered corpus: immutable base data, prepared derived schema, report-readable through a
  read-only role; or
- scratch: reserved-name, disposable, recreated by its owner, and forbidden as report input.

A database in neither class is unclassified. Verification reports it. It is retained until its
consumers and evidence are checked, then dumped or dropped in one causal retirement commit. A suffix
such as `_local`, `_v6`, or `_old` is not a lifecycle class.

Protection follows the registry. The database guard refuses destructive operations against every
registered corpus and permits a scratch database only when the requested name matches the reserved
pattern. The old hand-maintained physical-name list is removed after the registry guard passes positive
and negative tests.

### 7. Publication separates database-local export from package assembly

Publication has two execution planes:

- The evaluation-host data plane executes PostgreSQL's custom-format dump command beside the source
  service. It exports one corpus at a time to durable staging, writes a partial name until the command
  succeeds, records the archive checksum, and atomically marks the corpus export complete.
- The workstation control plane resolves the registry, performs small validation queries, inventories
  declared inputs, transfers completed archives, validates their checksums, assembles the manifest,
  and atomically promotes only the complete package.

The batch transport used for database-local export is independent of the long-lived SQL tunnel and
must terminate normally when automation captures its output. The SQL tunnel remains suitable for
small validation and provenance queries. It is not a bulk-export path. Archive transfer is resumable,
and an interrupted transfer never requires a new database export.

Durable staging is per corpus rather than per publication attempt. A failed command leaves an
ineligible partial file. A successful export remains reusable after another corpus fails, but only
when its recorded corpus id, physical database, byte size, and checksum still verify. This bounds
recovery to at most one corpus export or one archive transfer. Local package assembly never writes an
incomplete manifest set over the previous complete package.

Publication dumps the complete database. It does not infer the derived-schema boundary from object
name prefixes such as `mv_` or `v_`. Import restores the archive and then runs `prepare-corpus`, which
recreates the versioned derived schema and verifies its revision. If server-local measurement later
shows that derived objects dominate export cost, moving them into a dedicated schema is a separate
design change.

The manifest binds each dump to:

- semantic corpus id and current physical database name;
- checksum and byte size;
- expected and observed project counts;
- producer commit and dirty state;
- corpus-definition files and their checksums;
- installed view-definition revision; and
- the attempt ledger, completion markers, and project configurations the report validates.

Import uses the same documented path for the author's clean restoration and a replicator. It verifies
the archive before restore, runs `prepare-corpus`, checks the installed view revision and project
count, and then runs the report's declared read-only input check. Publication fails before promotion
if any manifest fact disagrees.

### 8. Retirement is the last phase

First migrate and validate consumers, runners, preparation, protection, and publication. Then audit
unclassified databases on both hosts. Retire only entries with a recorded consumer result, observed
project count, and disposition. Drop superseded and partial databases only after their required dump or
evidence has been retained.

The destructive phase is one append-only commit after all replacement paths pass. No reset, history
rewrite, or modification of frozen corpus rows is part of rollback.

## Risks / Trade-offs

- **A registry entry points at the wrong but plausible database.** -> Verify expected project count,
  corpus-definition inputs, and installed view revision before any report runs. Publication additionally
  checks the complete set.
- **A future view change alters results without changing corpus rows.** -> Treat view definitions as
  versioned executable input. Install them explicitly and record their checksum in provenance and the
  publication manifest.
- **A local machine lacks most corpora.** -> Requested-subset verification remains usable. Only a
  published-artifact build requires the complete set.
- **A stale staged dump is mistaken for the current corpus.** -> Partial and complete names are
  distinct. Reuse requires the recorded corpus identity, physical database, byte size, and checksum to
  verify before transfer or assembly.
- **A transport failure loses expensive work.** -> Complete each dump durably on the data host, then
  use resumable transfer. Never place all corpus exports in one attempt-scoped temporary directory.
- **Publication observes mutable source data.** -> Keep published corpora read-only and verify their
  registered shape and provenance before export. Refuse publication when those facts disagree.
- **A validation command rewrites dependency state.** -> Run Python commands with frozen dependency
  resolution and require a clean source snapshot before package assembly.
- **Physical names remain unattractive.** -> Accepted. They are deployment details hidden behind
  semantic ids. Renaming them would spend operational risk on presentation.
- **A runner bypasses the registry.** -> Include runners and configuration in the physical-literal
  check and exercise the actual rerun command in both host and local modes.
- **Preparing derived schema needs write access.** -> Keep a narrow owner-only preparation phase,
  transactionally apply checked-in SQL, then return to the report read-only role. Reports themselves
  never receive write access.

## Migration Plan

1. Finish the registry accessor, semantic report inputs, and requested-subset verification.
2. Migrate runner scripts, project configuration, packaging, import, and diagnostics to corpus ids.
3. Add idempotent view preparation and report preflight over the installed view revision.
4. Replace the physical-name guard with registry and scratch lifecycle rules.
5. Prove database-local export, checksum transfer, restore, preparation, and read-only access with the
   smallest published corpus. Do not start the complete export before this positive control passes.
6. Export every published corpus into durable per-corpus staging, transfer verified archives, and
   assemble one complete package on the workstation.
7. Import that package into an isolated PostgreSQL volume with no corpus-host dependency, then
   reproduce every registered report read-only.
8. Audit and retire unclassified and superseded databases only after all replacement paths pass.

Rollback is append-only: revert the responsible code or registry commit, restore a retired database
from its retained dump when required, and rerun verification. Never mutate a published corpus to make a
check pass.

## Open Questions

None. The earlier physical-rename question is resolved in favor of stable semantic ids, and the view
installation question is resolved by the explicit preparation boundary.
