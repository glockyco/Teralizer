## 1. Registry and lifecycle, describing the current state

- [ ] 1.1 Add `src/main/resources/db/corpora.toml` with one entry per corpus, pointing at the current
      physical names, carrying `id`, `database`, `data_dir`, `config_dir`, `expected_projects`, and
      `notes`, with one entry each for `controlled`, `real-world`,
      `jarvis-benchmark`, and `jarvis-scenarios`
- [ ] 1.2 Add a Python registry reader over `tomllib` that fails on a missing field or a duplicate
      physical name, plus unit tests for both failures
- [ ] 1.3 Add the shell accessor that reads the registry through the Python helper
- [ ] 1.4 Define the `scratch_` pattern and a classifier that buckets a database name as corpus,
      scratch, or unclassified
- [ ] 1.5 Add `verify-corpora`: check existence and project count for every entry, and report
      unclassified databases found on the server
- [ ] 1.6 Add the name-literal check over live code only, exempting the frozen run machinery, running
      without a database connection, and wire it into CI

## 2. Expose the registry resolution boundary

- [ ] 2.1 Expose immutable lookup by semantic corpus id, returning the physical database, corpus
      definition paths, expected project count, and notes without a primary-corpus alias
- [ ] 2.2 Expose registry-owned existence and expected-project-count validation for callers that resolve
      one or more corpus roles; keep role-specific object validation outside this change
- [ ] 2.3 Test successful and unknown-id lookup, immutable entries, and count mismatch diagnostics
- [ ] 2.4 Complete these registry interfaces before `make-report-runs-explicit` removes
      `ReportSpec.schema`, physical defaults, and database overrides

## 3. Move the live path onto corpus ids

- [ ] 3.1 Replace physical database literals in non-report live consumers with registry corpus ids;
      `make-report-runs-explicit` owns every `ReportSpec` and registered-report migration
- [ ] 3.2 Point the four spike CLIs at the `real-world` corpus, run each once, and delete any whose
      SQL depends on legacy-only structure rather than keeping it alive by a dump
- [ ] 3.3 Remove `DB_NAME_DEV`, `DB_NAME_TEST`, `DATASET_VARIANT`, `VALID_VARIANTS`, and the
      `_replication` suffix from `config.py:32-83`, and update `.env.example`
- [ ] 3.4 Make registry resolution refuse an unknown corpus id and refuse a corpus whose project count
      disagrees with its entry; report-role resolution remains owned by `make-report-runs-explicit`
- [ ] 3.5 Preserve `run-rq6-analysis.sh:11` as an archival run input and add the scoped note that its
      physical name predates the registry; do not make it a live consumer
- [ ] 3.6 Point `collect-disk-metrics.sh:148-156` and the replication compose defaults at the registry
- [ ] 3.7 After the report-input cutover, regenerate the report set and confirm byte-identical output

## 4. Rename and read-only access

- [ ] 4.1 Rename the four corpora with `ALTER DATABASE ... RENAME TO`, updating only the registry, and
      rename `postgres_verification` to `scratch_verification`
- [ ] 4.2 Add a note to `project-configs/` recording that the frozen configs name the databases as
      they were when each run wrote them
- [ ] 4.3 Run `verify-corpora`, regenerate the report set, and confirm every measured value and rendered
      artifact is byte-identical while only reviewed corpus-identity provenance fields change
- [ ] 4.4 Point every analysis connection at `teralizer_ro`, then confirm at the database level that
      insert, update, delete, and schema change against a corpus are rejected

## 5. Publication and the artifact

- [ ] 5.1 Add the provenance query that emits, per corpus, each tool commit with its project count and
      the unattributed count
- [ ] 5.2 Add `publish-corpora`: dump each corpus, checksum it, and write
      `replication/datasets/manifest.json` with corpus id, file, sha256, bytes, project count, and
      provenance
- [ ] 5.3 Make publication fail when a corpus disagrees with its entry, or when a checked corpus's
      ledger, markers, or project configs are missing
- [ ] 5.4 Move the definition inputs RQ6 checks into the artifact a replicator downloads, and keep the
      13 GB of unread run material out of it
- [ ] 5.5 Rewrite `prepare-zenodo-package.sh` to take shipped dumps from the manifest, and correct
      `README.md:43` and the quick-start claims to match what it builds
- [ ] 5.6 Rewrite `import-databases.sh` to restore from the manifest and verify checksum and project
      count before reporting success, failing on a missing dump
- [ ] 5.7 Fix the container name disagreement between `import-databases.sh:19-22` and
      `replication/docker-compose.yml:84-92`
- [ ] 5.8 Validate that every registry corpus appears once in `replication/datasets/manifest.json` and
      reject retired physical names in live consumers or generated replication metadata;
      `make-report-runs-explicit` owns validation of every report corpus role against this registry
- [ ] 5.9 Re-measure the `REQUIREMENTS.md` disk and version tables from the artifact
- [ ] 5.10 Restore the author's own databases from the published dumps, so the artifact path is the
      tested path

## 6. End-to-end verification

- [ ] 6.1 Import the artifact into a clean environment with no access to the author's machines
- [ ] 6.2 Run the full report set there and confirm it reproduces the published figures
- [ ] 6.3 Confirm every guarded report passes using only the shipped inputs
- [ ] 6.4 Confirm a corrupt dump, a missing dump, and a partial corpus each fail loudly

## 7. Retire the sprawl

- [ ] 7.1 Trace the 15 project rows in the server's `postgres` database and record whether any
      reported figure depends on them
- [ ] 7.2 Resolve `create-views.sql`: fold it into the code generator's scratch setup or delete it,
      based on whether any code reads its views
- [ ] 7.3 Dump `postgres_test` and the superseded RepoReapers corpora, restore each one, and confirm
      its project count before trusting the dump
- [ ] 7.4 Drop the scratch families and the partial `_rq6_v7_local` snapshot, together with the
      `data/reporeapers-rerun-v7-local` ledger, recording each decision and its evidence
- [ ] 7.5 Run `verify-corpora` and confirm nothing is unclassified
