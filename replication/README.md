# Replication Package: Automated Test Generalization

This package enables reproduction of the results presented in our TOSEM paper on automated test generalization using symbolic execution.

**ACM Artifact Evaluators**: See [Verification Workflows](#verification-workflows) to begin.

## Security Notice

**Jupyter runs WITHOUT authentication** for ease of reproduction.
- Only run on trusted networks (not public WiFi)
- Stop containers when not in use: `docker compose down`
- All ports are bound to localhost only (127.0.0.1)

**The Java pipeline clones and executes code from GitHub.**
- For artifact evaluation: Use the provided database dumps (no code execution required)
- If running the pipeline: Projects are vetted open-source repositories
- Docker provides container-level isolation

---

## Quick Start (5 minutes)

For most reviewers, this is all you need:

```bash
cd replication
./quick-start.sh
```

This will:
1. Start PostgreSQL and import the database dumps
2. Start Jupyter Lab with the analysis notebooks
3. Open your browser to http://localhost:8888

**Access points:**
- Jupyter Lab: http://localhost:8888
- Database UI (Adminer): http://localhost:18080
  - System: PostgreSQL
  - Server: postgres
  - Username: teralizer
  - Password: teralizer
  - Database: postgres_dev or postgres_test

---

## Verification Workflows

Choose based on your evaluation goals:

### Workflow 1: Inspect Pre-computed Results (5 min)

Browse results without re-running anything.

1. Start services:
   ```bash
   ./quick-start.sh
   ```

2. Verify import:
   ```bash
   ./scripts/verify-results.sh
   ```

3. Explore:
   - **Jupyter**: http://localhost:8888 (browse notebooks)
   - **Adminer**: http://localhost:18080 (query databases)
   - **Files**: `analysis/output/original/` (pre-computed tables/figures)

### Workflow 2: Verify Analysis Reproducibility (10 min)

Confirm analysis code produces identical results on same data.

1. Setup (if not done):
   ```bash
   ./quick-start.sh
   ```

2. Re-run notebooks:
   ```bash
   ./scripts/run-notebooks.sh verify
   ```

3. Compare outputs:
   ```bash
   ./scripts/verify-outputs.sh original verify
   ```

**Expected**: All outputs match exactly.

### Workflow 3: Verify Pipeline Execution (15+ min)

Confirm data collection pipeline runs successfully.

1. Run pipeline on subset:
   ```bash
   ./scripts/run.sh --dataset extended --count 5
   ```

2. Run analysis on new data:
   ```bash
   ./scripts/run-notebooks.sh replicate
   ```

3. Compare (differences expected due to non-determinism):
   ```bash
   ./scripts/verify-outputs.sh original replicate
   ```

---

## System Requirements

- **Docker**: Version 20.10+ with Docker Compose V2
- **Bash**: For running the provided scripts
- **RAM**: 8GB minimum (16GB recommended for pipeline)
- **Disk**: 20GB for quick start, 50GB+ for full reproduction
- **OS**: Linux, macOS (Intel or Apple Silicon), Windows (WSL2)

**Note for Windows users**: Run all commands from within WSL2 (required by Docker Desktop anyway).

Check requirements:
```bash
./scripts/preflight-check.sh
```

---

## Manual Setup (Alternative to Quick Start)

If you prefer explicit control:

```bash
# 1. Start database
docker compose up -d postgres adminer

# 2. Wait for PostgreSQL to be ready
docker compose exec postgres pg_isready -U teralizer

# 3. Import database dumps
./scripts/import-databases.sh datasets/

# 4. Start Jupyter
docker compose up -d analysis

# 5. Open browser
open http://localhost:8888  # macOS
xdg-open http://localhost:8888  # Linux
```

---

## Verifying the Import

After importing, verify the data:

```bash
./scripts/verify-results.sh
```

Expected output:
```
Primary Dataset (postgres_dev)
  ✓ Database connection OK
  ✓ Project count: 13 (expected 13)
  Statistics:
    Tests: ...
    Assertions: ...
    Generalizations: ...

Extended Dataset (postgres_test)
  ✓ Database connection OK
  ✓ Project count: 1161 (expected 1161)
  ...
```

---

## Verifying Analysis Results

The package includes pre-computed outputs from our analysis in `analysis/output/original/`. You can verify these by re-running the analysis and comparing results.

### Pre-computed Outputs

The `analysis/output/original/` directory contains:
- `tables/` - LaTeX tables used in the paper
- `data/` - CSV files with computed statistics
- `figures/` - PDF figures used in the paper

### Re-running the Analysis

To re-run the analysis notebooks:

```bash
# Execute all notebooks on original data (outputs to verify/)
./scripts/run-notebooks.sh verify

# Also generate HTML exports for easy viewing
./scripts/run-notebooks.sh verify --html

# Or run with --dry-run to see what would be executed
./scripts/run-notebooks.sh verify --dry-run
```

This creates outputs in `analysis/output/verify/` using the same original databases.

Alternatively, use Docker Compose directly:

```bash
# Compare outputs using containerized verify tool
docker compose run --rm verify original verify
```

### Comparing Outputs

After re-running, compare your outputs against the pre-computed reference:

```bash
./scripts/verify-outputs.sh original verify
```

**Expected**: All files should match exactly. The analysis is deterministic on the same data.

### Output Variants

| Variant | Database Used | Output Directory | Purpose |
|---------|---------------|------------------|---------|
| `original` | postgres_dev/test | `output/original/` | Pre-computed reference (shipped) |
| `verify` | postgres_dev/test | `output/verify/` | Re-run on same data |
| `replicate` | postgres_dev/test_replication | `output/replicate/` | Full pipeline reproduction |

To change variants, set `DATASET_VARIANT` environment variable or in `.env`:
```bash
export DATASET_VARIANT=verify
# or: DATASET_VARIANT=replicate ./scripts/run-notebooks.sh replicate
```

---

## Running the Pipeline

The pipeline can be run on subsets for verification:

### Quick Verification (~5 minutes)

```bash
# Process 5 RepoReapers projects
./scripts/run.sh --dataset extended --count 5
```

### Extended Verification (~40 minutes)

```bash
# Process 50 RepoReapers projects
./scripts/run.sh --dataset extended --count 50
```

### Dry Run (Preview Without Executing)

```bash
./scripts/run.sh --dataset extended --count 10 --dry-run
./scripts/run.sh --dataset primary --phase generalization --dry-run
```

### Dataset Options

**Extended Dataset (RepoReapers):**
```bash
./scripts/run.sh --dataset extended                    # All 1161 projects (~15h)
./scripts/run.sh --dataset extended --count 100        # First 100 projects (~1.5h)
./scripts/run.sh --dataset extended --start 500 --count 50  # Projects 500-549
```

**Primary Dataset (EqBench + Commons Utils):**
```bash
# Generation phase (EvoSuite test generation)
./scripts/run.sh --dataset primary --phase generation --time 1s   # ~3h

# Generalization phase
./scripts/run.sh --dataset primary --phase generalization --time 1s  # ~8h
./scripts/run.sh --dataset primary --phase generalization --time dev # Commons Utils dev tests

# Specific project
./scripts/run.sh --dataset primary --phase generalization --project eqbench --time 10s
```

---

## Dataset Structure

### Primary Dataset (EqBench + Commons Utils)

Two-phase workflow:
1. **Generation**: EvoSuite generates tests with different time budgets (1s, 10s, 60s)
2. **Generalization**: Teralizer generalizes the generated tests

Plus `commons-utils-dev`: Uses original developer-written tests (no generation phase).

| Config | Generation | Generalization |
|--------|-----------|----------------|
| eqbench-1s | ~8h | ~25h |
| eqbench-10s | ~9h | ~28h |
| eqbench-60s | N/A | ~31h |
| commons-utils-1s | ~1.5h | ~8h |
| commons-utils-10s | ~1.5h | ~10h |
| commons-utils-60s | ~3h | ~9h |
| commons-utils-dev | N/A | ~3h |

### Extended Dataset (RepoReapers)

1161 open-source Java projects from GitHub:
- Average runtime: ~1 minute per project
- Total runtime: ~15 hours

---

## Databases

### Database Structure

The import creates four databases:

| Database | Contents | Purpose |
|----------|----------|---------|
| `postgres_dev` | Primary dataset (13 projects) | Verification workflows |
| `postgres_test` | Extended dataset (1161 projects) | Verification workflows |
| `postgres_dev_replication` | Empty schema | Pipeline reproduction |
| `postgres_test_replication` | Empty schema | Pipeline reproduction |

The `*_replication` databases are populated when you run the pipeline (Workflow 3).

### Key Tables

- `project` - Project metadata and configuration
- `test` - Extracted test methods
- `assertion` - Assertions within tests
- `generalization` - Generated property-based tests
- `task` - Pipeline execution records
- `filter_result` - Filter decisions during processing

### Connecting Directly

```bash
# Via Docker
docker compose exec postgres psql -U teralizer -d postgres_dev

# Via psql (if installed locally)
psql -h localhost -p 5432 -U teralizer -d postgres_dev
```

---

## Analysis Notebooks

The `analysis/notebooks/` directory contains Jupyter notebooks that reproduce the paper's figures and tables:

| Notebook | Paper Section | Description |
|----------|---------------|-------------|
| `dataset-characteristics.ipynb` | Evaluation Setup | Dataset statistics and characteristics |
| `rq1-mutation-detection.ipynb` | RQ1, RQ2 | Mutation score; Constraint complexity |
| `rq2-test-suite-effects.ipynb` | RQ3 | Test suite size and runtime |
| `rq3-runtime-requirements.ipynb` | RQ4 | Teralizer efficiency |
| `rq4-limitations.ipynb` | RQ5, RQ6 | Exclusion causes (controlled + real-world) |

---

## Configuration

Environment variables (set in `.env` or export):

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USER` | teralizer | Database user |
| `DB_PASSWORD` | teralizer | Database password |
| `DB_NAME_DEV` | postgres_dev | Primary dataset database |
| `DB_NAME_TEST` | postgres_test | Extended dataset database |
| `DB_PORT` | 5432 | PostgreSQL port |
| `ADMINER_PORT` | 18080 | Adminer web UI port |
| `JUPYTER_PORT` | 8888 | Jupyter Lab port |
| `DATASET_VARIANT` | verify | Output variant: `verify` or `replicate` |

Copy `.env.example` to `.env` to customize:
```bash
cp .env.example .env
```

---

## Troubleshooting

### Docker Issues

**"Cannot connect to Docker daemon"**
- Ensure Docker Desktop is running
- On Linux: `sudo systemctl start docker`

**"Port already in use"**
- Change ports in `.env`: `DB_PORT=5433`, `JUPYTER_PORT=8889`

### Database Issues

**"Cannot connect to database"**
```bash
# Check if PostgreSQL is running
docker compose ps

# View logs
docker compose logs postgres

# Restart
docker compose restart postgres
```

**"Database is empty"**
```bash
# Re-import dumps
./scripts/import-databases.sh --force datasets/
```

### Jupyter Issues

**"Jupyter won't start"**
```bash
# Check logs
docker compose logs analysis

# Rebuild container
docker compose build analysis
docker compose up -d analysis
```

### Pipeline Issues

**"Project cloning fails"**
- Check internet connectivity
- Some GitHub repos may have been deleted; the pipeline will skip them

**"Out of memory"**
- Increase Docker memory limit in Docker Desktop settings
- For pipeline: minimum 8GB recommended

---

## Complete Pipeline Reproduction

To reproduce all results from scratch (requires days of compute):

### Extended Dataset (~15 hours)

```bash
# Start database
docker compose up -d postgres

# Run all 1161 projects
./scripts/run.sh --dataset extended
```

### Primary Dataset (~100+ hours)

The primary dataset requires a two-phase workflow with manual intervention:

1. **Generate tests** (EvoSuite):
   ```bash
   ./scripts/run.sh --dataset primary --phase generation
   ```

2. **Manual fixes**: Some generated tests may not compile. Fix compilation errors in `projects/` directory.

3. **Generalize tests**:
   ```bash
   ./scripts/run.sh --dataset primary --phase generalization
   ```

**Note**: Results will NOT be identical to the provided database dumps because:
- EvoSuite uses randomized search algorithms
- Generalization progress depends on timeouts (machine-dependent)
- Some GitHub repositories may have changed or been deleted

### Analyzing Reproduction Results

After running the pipeline, analyze the reproduced data and compare with original results:

```bash
# Run analysis notebooks on the reproduced data
./scripts/run-notebooks.sh replicate

# Compare reproduced outputs against original
./scripts/verify-outputs.sh original replicate
```

The `replicate` variant:
- Reads from `postgres_dev_replication` and `postgres_test_replication` databases (populated by pipeline runs)
- Writes outputs to `analysis/output/replicate/`

Differences between `original/` and `replicate/` are expected due to the non-determinism noted above.

---

## Stopping Services

```bash
# Stop all containers (preserves data)
docker compose down

# Stop and remove all data
docker compose down -v
```

---

## File Structure

```
replication/
├── docker-compose.yml          # Docker services configuration
├── Dockerfile.analysis         # Jupyter container definition
├── .env.example                # Environment variable template
├── quick-start.sh              # One-command setup script
├── README.md                   # This file
├── datasets/
│   ├── postgres_dev.dump       # Primary dataset database dump
│   └── postgres_test.dump      # Extended dataset database dump
└── scripts/
    ├── preflight-check.sh      # System requirements check
    ├── import-databases.sh     # Database import script
    ├── export-databases.sh     # Database export script
    ├── run.sh                  # Unified pipeline runner
    ├── run-notebooks.sh        # Automated notebook execution
    ├── verify-results.sh       # Database verification
    └── verify-outputs.sh       # Output comparison between variants

project-configs/
├── primary/
│   ├── generation/             # EvoSuite test generation configs
│   └── generalization/         # Test generalization configs
├── extended/                   # RepoReapers project configs
└── examples/                   # Example configurations
```

---

## Citation

If you use this artifact, please cite:

```bibtex
@misc{glock_2025_teralizer,
  title={Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to Property-Based Tests},
  author={Johann Glock and Clemens Bauer and Martin Pinzger},
  year={2025},
  eprint={2512.14475},
  archivePrefix={arXiv},
  primaryClass={cs.SE},
  url={https://arxiv.org/abs/2512.14475},
}
```

---

## License

This artifact is released under [LICENSE]. The analyzed projects retain their original licenses.

## Contact

For questions or issues, please open an issue on the GitHub repository or contact the authors.
