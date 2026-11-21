# Teralizer corpus storage

Production corpus data moves through four storage zones. Do not copy production dumps into the source checkout.

| Zone | Contents | Owner |
|---|---|---|
| Source checkout | Corpus declarations, schemas, code, and synthetic fixtures | Git |
| Author staging | Completed dumps, `manifest.json`, and `checksums.sha256` | Selected external directory |
| Release staging | Staged evidence and release candidates | Directories given to `python -m teralizer.release build` |
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

Give the release builder the staged evidence, the verified external package and the retrieved project inputs. It writes each candidate into a new directory and refuses an existing or partly written one:

```bash
uv run --directory analysis python -m teralizer.release build \
  --candidate /external/release/candidates/<name> \
  --evidence /external/release/staged-evidence \
  --corpus-package /external/author-stage/package \
  --real-world-projects /external/release/real-world-projects \
  --third-party-upstream /external/release/third-party-upstream
```

The commands that stage the evidence and retrieve the project inputs are listed in `analysis/src/teralizer/release/__main__.py`. The builder plans every archive from the release declaration and the inventory in `scripts/packaging/release/` and verifies each archive after writing it. Zenodo is the public authority for production corpus payloads. Publish changed bytes as a new version under concept DOI `10.5281/zenodo.17950380`.

## Verify and restore

An extracted release keeps the corpus manifest at `replication/corpus-manifest.json` in core and each dump in its corpus component, `components/corpus-<id>/`. The reviewer commands in `./artifact` restore the corpora that a workflow needs. To restore selected corpora directly, start PostgreSQL and name them:

```bash
docker compose -f replication/docker-compose.yml up -d --wait postgres
./replication/scripts/import-databases.sh --force controlled
```

The importer checks each named dump against the manifest before it changes a database. It restores the corpus, prepares its derived schema and checks access through the report role.
