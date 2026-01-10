# Installation

## Prerequisites

Verify your system meets the requirements:
```bash
./replication/scripts/preflight-check.sh
```

All checks should pass (green checkmarks).

## Quick Start

```bash
cd replication
./quick-start.sh
```

This script:
1. Starts PostgreSQL and imports the database dumps
2. Starts Jupyter Lab with the analysis notebooks
3. Opens your browser to http://localhost:8888

## Verification Checkpoints

### Checkpoint 1: Services Running

```bash
docker compose ps
```

**Expected**: Three services with status "Up (healthy)":
- postgres-replication
- adminer-replication
- analysis-replication

### Checkpoint 2: Database Populated

Open http://localhost:18080 (Adminer) and log in:
- System: PostgreSQL
- Server: postgres
- Username: teralizer
- Password: teralizer
- Database: postgres_dev

**Expected**: The `project` table contains 13 rows (primary dataset).

Switch to `postgres_test` database.

**Expected**: The `project` table contains 1161 rows (extended dataset).

### Checkpoint 3: Jupyter Works

Open http://localhost:8888 and open any notebook (e.g., `analysis/notebooks/dataset-characteristics.ipynb`).

Run the first cell (imports and database connection).

**Expected**: Cell executes without errors. Database connection established.

### Checkpoint 4: Reproduce a Result

Run all cells in `rq1-mutation-detection.ipynb`.

**Expected**:
- Notebook completes without errors
- Output tables match paper Tables 2-4
- Figures generated in `analysis/output/figures/`

## Troubleshooting

### "Port already in use"

Another service is using port 5432, 8888, or 18080. Either:
- Stop the conflicting service
- Configure alternate ports in `.env` (copy from `.env.example`)

### "Cannot connect to database"

```bash
docker compose logs postgres
docker compose restart postgres
```

### "Jupyter won't start"

```bash
docker compose logs analysis
docker compose build analysis
docker compose up -d analysis
```

## Stopping Services

```bash
# Stop containers (preserves data)
docker compose down

# Stop and remove all data
docker compose down -v
```
