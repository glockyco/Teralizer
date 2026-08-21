## 1. Complete the registry and lifecycle model

- [x] 1.1 Add `src/main/resources/db/corpora.toml` with one entry per corpus, pointing at the current
      physical names, carrying `id`, `database`, `data_dir`, `config_dir`, `expected_projects`, and
      `notes`, with entries for `controlled`, `real-world`, `jarvis-benchmark`, and `jarvis-scenarios`.
- [x] 1.2 Add a Python registry reader over `tomllib` that fails on a missing field or duplicate
      physical name, plus unit tests for both failures.
- [x] 1.3 Add the shell accessor that reads the registry through the Python helper.
- [x] 1.4 Add explicit publication status, define the reserved `scratch_` pattern, and classify every
      observed database as registered corpus, scratch, or unclassified.
- [x] 1.5 Extend `verify-corpora` with three modes: inventory all observed databases, fully verify an
      explicitly requested subset, and require every published corpus. Test a valid partial local
      installation, missing requested corpus, complete publication host, count mismatch, and
      unclassified database.
- [x] 1.6 Scan live reports, runner scripts, executable project configuration, packaging, and
      diagnostics for registered physical-name literals. Permit only the registry, lifecycle code that
      must address PostgreSQL, and generated observed provenance. Run the check without a database and
      wire it into CI.

## 2. Expose the registry resolution boundary

- [x] 2.1 Expose immutable lookup by semantic corpus id, returning the physical database, corpus
      definition paths, expected project count, and notes without a primary-corpus alias.
- [x] 2.2 Expose registry-owned existence and expected-project-count validation for callers that resolve
      one or more corpus roles; keep role-specific object validation outside this change.
- [x] 2.3 Test successful and unknown-id lookup, immutable entries, and count mismatch diagnostics.
- [x] 2.4 Complete these registry interfaces before `make-report-runs-explicit` removes
      `ReportSpec.schema`, physical defaults, and database overrides.
- [x] 2.5 Extend the shell boundary to return one requested field safely and to export resolved
      connection settings for existing Java and shell runners; do not add a second Java TOML parser.

## 3. Move every live path onto corpus ids

- [x] 3.1 Replace physical database literals in non-report consumers with corpus ids.
      `make-report-runs-explicit` owns every registered `ReportSpec` migration.
- [x] 3.2 Migrate `run-rq6-analysis.sh`, rerun scripts, and executable `project-configs/**` to accept a
      semantic corpus id and receive the resolved physical connection at runtime. Preserve historical
      attribution through commit and provenance, not stale runnable literals.
- [x] 3.3 Point the four spike CLIs at `real-world`, run each once, and delete any spike whose SQL
      depends on legacy-only structure instead of keeping a database alive for it.
- [x] 3.4 Remove `DB_NAME_DEV`, `DB_NAME_TEST`, `DATASET_VARIANT`, `VALID_VARIANTS`, and the
      `_replication` suffix from `config.py`, and update `.env.example` to the semantic-id boundary.
- [x] 3.5 Point disk metrics, replication compose defaults, packaging, import, and diagnostics through
      the registry accessor. No script may parse `corpora.toml` independently.
- [x] 3.6 Record semantic corpus id, resolved physical name, source commit, dirty state, corpus inputs,
      and derived-view revision in run provenance.
- [x] 3.7 Regenerate all registered reports from the same corpora and compare with the canonical
      baseline. Require measured values and rendered content to remain unchanged; review only explicit
      provenance-field differences.

## 4. Prepare derived schema and enforce read-only access

- [x] 4.1 Add one idempotent `prepare-corpus <id>` operation. Resolve and validate the corpus, apply
      `src/main/resources/db/create-views.sql` transactionally as owner, record its canonical checksum,
      verify required views, and establish the report read-only role without modifying measured rows.
- [x] 4.2 Add report preflight that compares the installed derived-view revision with the checked-in
      revision and fails before a query on absence or mismatch. Test stale, missing, current, and
      repeated preparation states.
- [x] 4.3 Route restore, local setup, evaluation-host setup, and replication quick start through
      `prepare-corpus`. Remove every ad hoc view-installation path after the shared command passes.
- [x] 4.4 Point analysis connections at the read-only role and prove insert, update, delete, and schema
      changes fail while every registered report query succeeds.
- [x] 4.5 Replace `protected-databases.txt` with registry-backed corpus protection and reserved scratch
      handling. Test destructive refusal for every corpus and successful recreation of a valid scratch
      database.
- [x] 4.6 Confirm the four published corpus databases retain their current physical names. Perform no
      `ALTER DATABASE`, alias, or dump rename.

## 5. Publish and import the complete artifact

- [x] 5.1 Add the provenance query that emits, per corpus, each tool commit with its project count and
      the unattributed count.
- [x] 5.2 Split publication planning and package assembly from corpus export. Require every published
      entry, accept only explicitly staged dump inputs, and write a manifest containing semantic id,
      physical name, file, SHA-256, bytes, expected and observed project counts, producer revision and
      dirty state, corpus-input checksums, derived-view revision, and provenance. Local assembly must
      never invoke bulk `pg_dump` through the configured report endpoint.
- [x] 5.3 Fail publication before promotion on a missing published corpus, entry disagreement, duplicate
      entry, stale view revision, missing checked input, or dump/manifest mismatch.
- [x] 5.4 Include corpus definitions, attempt ledgers, completion markers, and project configurations
      required by registered reports. Keep bulk logs and unread intermediate run material out.
- [x] 5.5 Add the evaluation-host export boundary. Run PostgreSQL 17 custom-format dumps beside the
      source service, one published corpus at a time, through batch transport independent of the SQL
      tunnel. Dump the complete database and remove object-name wildcard exclusions.
- [x] 5.6 Persist each remote corpus export under distinct partial and complete names, checksum it before
      completion, and reuse only exports whose corpus identity, physical database, byte size, and
      checksum verify. Exercise interruption during one corpus and prove an earlier completed export
      survives.
- [x] 5.7 Transfer only completed archives through a resumable file transport, verify the source and
      destination checksums, and prove an interrupted transfer continues without another database
      export.
- [x] 5.8 Validate that every published registry corpus appears exactly once in the manifest and that
      every manifest input exists with the recorded checksum.
- [x] 5.9 Rewrite `prepare-zenodo-package.sh`, the quick start, README claims, and disk requirements to
      consume explicitly exported dumps and the verified assembled manifest. Document the data-host
      export, transfer, assembly, and recovery boundaries without author-specific service names.
- [x] 5.10 Rewrite `import-databases.sh` to verify dump checksum and identity before restore, run
      `prepare-corpus`, verify project count and derived-view revision, and exercise read-only report
      preflight before success.
- [ ] 5.11 Fix the replication container-name disagreement and prove import works with deployment names
      unrelated to the author's database service names.
- [x] 5.12 Use `jarvis-scenarios` as the positive control. Complete database-local export, checksum,
      transfer, isolated restore, preparation, and read-only report preflight before exporting the full
      corpus set.
- [ ] 5.13 Restore the author's own corpus installation through the assembled package and documented
      clean import path. Do not keep a private restoration path.

## 6. End-to-end verification

- [ ] 6.1 Import the artifact into a clean environment with no access to the author's machines.
- [ ] 6.2 Run every registered report read-only and confirm it reproduces the published artifact set.
- [ ] 6.3 Confirm a partial workstation can verify and run an explicitly requested installed corpus,
      while complete publication from that workstation fails naming the missing corpora.
- [ ] 6.4 Confirm a corrupt dump, missing dump, wrong project count, stale derived-view revision, scratch
      report input, and unclassified database each fail with specific diagnostics.
- [ ] 6.5 Run repository tests, lint, format, type checks, hooks, strict OpenSpec validation, and the
      physical-name positive-control scan with frozen Python dependency resolution. Require a clean
      source tree before and after validation.

## 7. Retire only proven sprawl

- [ ] 7.1 Trace the 15 project rows in the server's `postgres` database and record whether any published
      artifact depends on them.
- [ ] 7.2 Audit `postgres_test`, every superseded RepoReapers database, scratch families, and the partial
      local v7 snapshot. Record current consumers, observed project count, required retained dump, and
      final disposition for each.
- [ ] 7.3 Build and restore every dump required by a retirement record before trusting it.
- [ ] 7.4 Drop only databases whose retirement records are complete, together with disposable ledgers
      that no registered report or artifact reads. Preserve all others and record why.
- [ ] 7.5 Run inventory on the evaluation host and local workstation. Confirm every remaining database
      is a registered corpus or valid scratch and that each published corpus passes complete
      verification.
- [ ] 7.6 Commit the destructive retirement phase separately after every non-destructive replacement
      and publication check passes. Roll back only with a new revert or restore commit.
