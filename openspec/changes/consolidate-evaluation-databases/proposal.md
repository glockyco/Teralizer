## Why

The same empirical corpus is currently identified three ways: a report role, a physical PostgreSQL
name, and an informal version suffix. The mapping is scattered across report declarations, runners,
configuration, packaging scripts, and prose. A wrong physical name can therefore select a plausible
but incomplete corpus, while the replication artifact does not ship or verify every input its reports
require.

The durable identity is the corpus and its provenance, not the database service name. Physical
renaming would add operational risk without improving that identity. Future reruns must also remain
possible, so runner scripts and configuration are live code rather than frozen historical evidence.

## What Changes

- Add a **corpus registry** as the single source of truth for semantic corpus id, current physical
  database name, corpus-definition paths, expected project count, and publication status. Reports,
  runners, packaging, and diagnostics resolve a corpus id through it.
- **BREAKING**: live consumers address semantic corpus ids. Physical database names remain deployment
  details and are forbidden outside the registry, database lifecycle machinery, and reproducible
  provenance that explicitly records the resolved endpoint.
- Keep the four published corpus databases under their current physical names. Do not rename them and
  do not add aliases. Stable semantic ids remove the need for a risky `ALTER DATABASE` migration.
- Retire databases that are neither registered published corpora nor valid scratch databases.
  `postgres_test` is retired after proving that no published result reads it.
  `postgres_verification` becomes a reserved `scratch_` database because its runner recreates it.
- Treat runner scripts, project configuration, view definitions, and preparation commands as live
  reproducibility code. Update them to accept semantic corpus ids and resolve current physical names
  at runtime. Git and generated provenance record which revision produced an historical run.
- Add one idempotent **prepare-corpus** boundary. After restore and before read-only access, it installs
  the checked-in derived views and records their definition revision. Report preflight rejects a
  corpus whose installed view revision does not match the source revision it is about to query.
- Treat the dump as the portable unit of record. Publishing writes one dump per corpus plus a manifest
  carrying checksum, byte size, project count, semantic id, source revision, view-definition revision,
  and a corpus-derived provenance statement.
- Ship every non-database input that a report validates: corpus definition, attempt ledger, completion
  markers, and project configurations. Bulk logs and intermediate run output remain optional.
- Verify checksums, project counts, corpus ids, and view definitions on import. A full publication run
  requires every registered published corpus. Ordinary local analysis may request a verified subset.
- Replace the physical-name denylist with lifecycle rules for registered corpora and `scratch_`
  databases. Reports use a read-only role and may never read scratch.

## Capabilities

### New Capabilities

- `evaluation-data/corpus-registry`: stable corpus identity, required metadata, resolution of physical
  names, and the boundary that keeps deployment literals out of live consumers.
- `evaluation-data/database-lifecycle`: preparation, derived-view revision, read-only corpus access,
  scratch isolation, and evidence-backed retirement.
- `evaluation-data/corpus-publication`: verified dumps, manifests, provenance, and the complete set of
  non-database inputs required to reproduce a report.

### Modified Capabilities

None. These are the repository's first accepted evaluation-data contracts.

## Impact

- `src/main/resources/db/corpora.toml` and one shared Python/shell accessor for reports, runners,
  packaging, import, diagnostics, and publication. Java receives the resolved connection settings and
  need not implement a second registry parser.
- `src/main/resources/db/create-views.sql`, corpus preparation, report preflight, and read-only database
  roles. The measured base tables stay unchanged; preparation installs only checked-in derived schema
  before access is frozen.
- Runner scripts and `project-configs/**` are migrated as executable reproducibility machinery. They
  are not exempted as historical text.
- Packaging and replication scripts, `REQUIREMENTS.md`, generated manifests, and scoped repository
  guidance are updated to use semantic corpus ids.
- The four published databases keep their current physical names. Scratch databases may be recreated;
  superseded and partial databases are retired only after their consumers and project counts are
  checked.
- Existing measured values are expected to remain unchanged. Any report diff must be explained by the
  resolved corpus identity or corrected preparation state, not accepted as rename noise.
