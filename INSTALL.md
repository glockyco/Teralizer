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
3. Starts the analysis service used by the verification scripts

When complete, open http://localhost:18080 in your browser to inspect the
imported databases with Adminer.

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
./replication/scripts/verify-results.sh
```

The command inventories the server and verifies every published semantic corpus id against its registered physical database, project count, checked inputs, and derived-view revision.

### Checkpoint 3: Evaluation CLI Works

From the repository root, build the dataset report against the imported database:

```bash
cd analysis
uv run python -m teralizer.eval dataset --targets md
cd ..
```

**Expected**: The command completes without errors and writes the rendered report
to `analysis/reports/dataset.md`.

### Checkpoint 4: Reproduce the Reports

Run the verification wrapper to build every registered report:

```bash
./replication/scripts/run-analysis.sh verify
```

**Expected**:
- All reports complete without errors
- Tables, figures, and CSV files are generated in `analysis/output/verify/`

## Installation Complete

Installation is successful when:
- All four checkpoints pass
- Adminer is accessible at http://localhost:18080
- Database queries return expected row counts
- `teralizer.eval` produces the dataset report and verification outputs

## Stopping Services

```bash
# Stop containers (preserves data)
docker compose down

# Stop and remove all data
docker compose down -v
```
