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
uv run --directory ../analysis python -m teralizer.corpus_publish \
  --summarize-package datasets
```

To restore only selected corpora from a complete package, run:

```bash
docker compose up -d postgres
./scripts/import-databases.sh --force --corpus controlled datasets
```

Repeat `--corpus` for more entries. The importer still verifies the complete package before it changes a database.

## Publish

Run publication only from a clean source checkout with every published corpus available:

```bash
./scripts/export-databases.sh
```

Publication stages every dump once, validates all manifest facts, and then replaces the package set. `checksums.sha256` and all size output derive from the manifest. Scratch databases are never published.
