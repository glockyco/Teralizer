# Teralizer replication package

The corpus manifest at `datasets/manifest.json` defines the database artifacts in the package. It records each semantic corpus ID, physical database name, dump checksum and size, project counts, source inputs, producer provenance, and derived-view revision. Do not infer package contents from filenames or from a fixed database count.

## Verify and restore

From this directory, run:

```bash
./quick-start.sh
```

The quick start verifies the manifest, dump bytes, checksum inventory, and checked-in corpus inputs before it starts PostgreSQL. It then restores every published corpus, prepares derived schema through `prepare-corpus`, and exercises the report-only connection.

To inspect the verified package without restoring it, run:

```bash
uv run --frozen --directory ../analysis python -m teralizer.corpus_publish \
  --summarize-package datasets
```

To restore only selected corpora from a complete package, run:

```bash
docker compose up -d postgres
./scripts/import-databases.sh --force --corpus controlled datasets
```

Repeat `--corpus` for more entries. The importer still verifies the complete package before it changes a database.

## Publish

Publication uses two connections. `DB_HOST` and `DB_PORT` provide the read-only connection used for
small identity and provenance queries. Bulk database rows never use that connection. The export command
runs PostgreSQL's custom-format dump tool beside the source service and then transfers only the
compressed archive.

Set the deployment-specific batch endpoint without adding its host, executable path, or container name
to the corpus registry:

```bash
export CORPUS_EXPORT_HOST=<ssh-host>
export CORPUS_EXPORT_SPOOL=<durable-directory-on-data-host>
export CORPUS_EXPORT_DOCKER=<docker-executable-on-data-host>
export CORPUS_EXPORT_CONTAINER=<postgres-container-on-data-host>
./scripts/export-databases.sh
```

The command first inspects every published corpus. It then creates one durable `<corpus>.complete`
checkpoint per corpus, resumes transfer into `analysis/build/corpus-exports`, and assembles the verified
manifest set in `datasets/`. A failed export leaves only `<corpus>.partial`. A failed transfer leaves a
local partial file. Rerun the same command to continue without repeating verified exports. Set
`CORPUS_EXPORT_REPLACE=true` only to replace a completed checkpoint that fails identity or checksum
verification.

The assembled `manifest.json`, rather than filenames or a fixed count, defines package membership.
`checksums.sha256` and all disk requirements derive from it. Scratch databases are never published.
Pass the assembled package to the Zenodo builder with
`CORPUS_PACKAGE_DIR=/path/to/datasets ./scripts/packaging/prepare-zenodo-package.sh`.
