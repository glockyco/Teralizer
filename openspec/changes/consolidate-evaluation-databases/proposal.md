## Why

Measurement is finished. The databases that exist now are the final inputs to the replication
artifact and to the remaining thesis and paper prose, which makes them archival records. They are
not fit to be archived.

24 databases sit on the evaluation machine. `postgres_dev` holds the controlled corpus and
`postgres_test` holds RepoReapers, and neither name says so. Seven RQ6 corpora are separated only by
a `_v2`..`_v7` suffix whose meaning is recorded nowhere, `_rq6_` names a corpus after a research
question that reads it, and `census` and `scoreboard` are implementation words that appear in the
thesis zero times. A partial 451-project snapshot differs from the complete 1,161-project corpus
by the suffix `_local`, so pointing a report at the wrong one yields plausible wrong numbers instead
of an error. the frozen `run-rq6-analysis.sh:11` input still names `_v6` while the live RQ6 report reads `_v7`.
The run input must remain historical evidence, not become a second current corpus inventory.

The artifact cannot deliver any of this. `README.md:43` promises dumps that
`prepare-zenodo-package.sh` never builds. `replication/datasets/` ships only the two corpora whose
names mislead, omitting the RQ6 and JARVIS corpora every current figure is read from. The corpus
definition inputs that reports refuse to run without travel only in opt-in archives, so a replicator
following the documented path cannot generate the RQ6 report at all.

## What Changes

- Add a **corpus registry** as the single source of truth: corpus id, database, corpus definition,
  and expected project count. Every corpus in it is shipped; a database
  that backs no published figure does not belong in it. Live code, packaging, and generated replication metadata
  resolve names through it.
- **BREAKING**: live consumers address a **corpus id**. This change provides the registry and
  validation boundary. `make-report-runs-explicit` owns report input roles and migrates every report;
  non-report live tools resolve corpus ids here. A check keeps physical database literals out of live
  code while preserving archival run inputs unchanged.
- **BREAKING**: rename the corpora after the evaluation condition each provides, in the thesis's own
  vocabulary: `teralizer_controlled`, `teralizer_real_world`, `teralizer_jarvis_benchmark`, and
  `teralizer_jarvis_scenarios`. No deployment role, no research question, no counter, and no
  implementation identifier, and no alias to the superseded name.
- **Retire two databases instead of renaming them.** `postgres_test` backs no published figure once
  the corpus table is regenerated from the real-world corpus, so it is archived and dropped rather
  than shipped. `postgres_verification` is recreated by its own runner on every use and holds
  different content on each machine, so it becomes `scratch_verification`.
- **Treat the dump as the unit of record.** Publishing writes one dump per corpus plus a manifest
  carrying checksum, project count, and a provenance statement derived from the corpus. A live
  database becomes a materialization of a dump, for the author as much as for a replicator.
- **Ship what reports refuse to run without**: the attempt ledger, completion markers, and project
  configs that RQ6 checks, rather than leaving them behind an opt-in flag. Bulk run material that no
  report reads stays out.
- **Verify on import**: checksum and project count are checked before an import reports success.
- Two **lifecycle classes** replace the four-entry denylist: registry corpora, restored read-only,
  and `scratch_` names that no report may read.
- **Retire the sprawl once**, recording each decision and its evidence where the retirement happens.

## Capabilities

### New Capabilities

- `evaluation-data/corpus-registry`: the registry that binds a corpus id to a database and its
  expected shape, how a corpus is named, and the rule that live code holds no name literal.
- `evaluation-data/database-lifecycle`: the corpus and scratch classes, read-only corpora, and which
  databases a report may read.
- `evaluation-data/corpus-publication`: the dump, manifest, provenance, and verification contract the
  artifact depends on, including the inputs reports refuse to run without.

### Modified Capabilities

None. This repository has no existing specs; these three are the first.

## Impact

- **Analysis package**: the corpus registry reader, registry validation, non-report live consumers,
  and `config.py:32-83` (`VALID_VARIANTS`, `DB_NAME_DEV`/`DB_NAME_TEST`, the `_replication` suffix).
  `make-report-runs-explicit` consumes this registry and owns report declarations, connection
  resolution, `ReportSpec.schema` removal, and the clean removal of physical database overrides.
- **Packaging and replication**: `prepare-zenodo-package.sh`, `replication/quick-start.sh`,
  `replication/scripts/import-databases.sh` (whose default container name disagrees with
  `replication/docker-compose.yml:84-92`), `collect-disk-metrics.sh:148-156`, `REQUIREMENTS.md`.
- **Replication metadata**: the manifest records the current corpus identities and provenance. Accepted
  capability specs retain the durable registry, publication, and lifecycle contracts.
- **Not touched**: the Java pipeline, `project-configs/**`, `reference.conf`, the runner scripts, and
  `protected-databases.txt`. They are the record of what was run, they will not run again, and
  rewriting them would falsify that record. A note in each directory states that its names predate
  the rename.
- **Data on disk**: 24 databases on the evaluation machine, 8 locally, and the `data/*/status.tsv`
  ledgers that identify them.
- **No measured value changes, and no run is repeated.** Prose, tables, CSV values, and figures stay
  byte-identical to the canonical baseline. The physical rename deliberately changes only recorded
  corpus identity in provenance-bearing outputs; that difference is isolated and reviewed.
