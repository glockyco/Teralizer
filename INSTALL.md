# Installation

## Download

Download archives from Zenodo: [10.5281/zenodo.18242626](https://doi.org/10.5281/zenodo.18242626)

See README.md §Package Contents for which archives to download.

## Unpack

```bash
unzip teralizer-core.zip
cd teralizer-core
```

The quick-start script will automatically extract sibling archives.

## Prerequisites

Verify your system meets the requirements:

```bash
./replication/scripts/preflight-check.sh
```

All checks should pass before proceeding.

See [REQUIREMENTS.md](REQUIREMENTS.md) for detailed requirements.

## Quick Start

```bash
cd replication
./quick-start.sh
```

This script:
1. Extracts sibling archives (if present)
2. Starts PostgreSQL and imports the database dumps
3. Starts Jupyter Lab with the analysis notebooks

When complete, open http://localhost:8888 in your browser.

## Verification Checkpoints

### Checkpoint 1: Services Running

```bash
docker compose ps
```

**Expected**: Three services with status "Up":
- postgres-replication
- adminer-replication
- analysis-replication

### Checkpoint 2: Database Populated

```bash
./scripts/verify-results.sh
```

**Expected output**:
```
Primary Dataset (postgres_dev)
  ✓ Database connection OK
  ✓ Project count: 13 (expected 13)

Extended Dataset (postgres_test)
  ✓ Database connection OK
  ✓ Project count: 1161 (expected 1161)
```

### Checkpoint 3: Jupyter Works

Open http://localhost:8888 and open `analysis/notebooks/dataset-characteristics.ipynb`.

Run the first cell (imports and database connection).

**Expected**: Cell executes without errors.

### Checkpoint 4: Reproduce a Result

Run all cells in `rq1-mutation-detection.ipynb`.

**Expected**:
- Notebook completes without errors
- Output tables generated in `analysis/output/verify/tables/`

## Installation Complete

Installation is successful when:
- All four checkpoints pass
- Jupyter Lab is accessible at http://localhost:8888
- Database queries return expected row counts

## Stopping Services

```bash
# Stop containers (preserves data)
docker compose down

# Stop and remove all data
docker compose down -v
```
