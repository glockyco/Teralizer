# Teralizer corpus storage

Production corpus data moves through four storage zones. Do not copy production dumps into the source checkout.

| Zone | Contents | Owner |
|---|---|---|
| Source checkout | Corpus declarations, schemas, code, and synthetic fixtures | Git |
| Author staging | Completed dumps, `manifest.json`, and `checksums.sha256` | Operator-selected external directory |
| Release staging | Verified archive inputs and generated release archives | `prepare-zenodo-package.sh` output directory |
| Published installation | Immutable archives selected by a version DOI | Zenodo |

The source checkout uses `verification/fixtures/corpus-package/` for synthetic CI input. This fixture is not a production package.

## Export and assemble

Set the deployment endpoint and two external staging paths:

```bash
export CORPUS_EXPORT_HOST=<ssh-host>
export CORPUS_EXPORT_SPOOL=<durable-directory-on-data-host>
export CORPUS_EXPORT_DOCKER=<docker-executable-on-data-host>
export CORPUS_EXPORT_CONTAINER=<postgres-container-on-data-host>
export CORPUS_EXPORT_DUMP_DIR=/external/author-stage/dumps
./replication/scripts/export-databases.sh /external/author-stage/package
```

The command creates one `<corpus>.complete` checkpoint on the data host. It resumes interrupted transfers without replacing a verified dump.

The package manifest defines package membership. It records corpus identity, dump checksum, byte size, project count, source inputs, producer provenance, and derived-view revision. The checksum inventory remains beside the manifest and dumps.

Set `CORPUS_EXPORT_REPLACE=true` only when a completed checkpoint fails identity or checksum verification.

## Build release archives

Give the release builder the verified external package:

```bash
CORPUS_PACKAGE_DIR=/external/author-stage/package \
  ./scripts/packaging/prepare-zenodo-package.sh \
  --corpus-package /external/author-stage/package
```

The builder verifies the complete package before it changes release staging. Zenodo is the public authority for production corpus payloads. Publish changed bytes as a new version under concept DOI `10.5281/zenodo.17950380`.

## Verify and restore

An installed release contains its verified corpus component at `replication/datasets/`. Run this command from the installed release:

```bash
./replication/quick-start.sh
```

The quick start verifies the package before it starts PostgreSQL. It restores each selected corpus, prepares derived schema, and checks report-only access.

In a source checkout, select an installed or downloaded package explicitly:

```bash
CORPUS_PACKAGE_DIR=/external/published-installation/replication/datasets \
  ./replication/quick-start.sh
```

To restore selected corpora without the quick start, run:

```bash
docker compose -f replication/docker-compose.yml up -d postgres
./replication/scripts/import-databases.sh --force \
  --corpus controlled \
  /external/published-installation/replication/datasets
```

The importer verifies the complete package before it changes a database.
