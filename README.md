# Teralizer Replication Package

Replication package for "Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to Property-Based Tests".

## Overview

Teralizer automates the transformation of conventional unit tests into property-based tests. It uses single-path symbolic analysis to extract path-exact specifications from tests and their implementations, enabling transformation to property-based tests that validate assertions across entire input partitions rather than individual input-output pairs.

## Links

| Resource | Location |
|----------|----------|
| Zenodo Archive | [10.5281/zenodo.17950381](https://zenodo.org/records/17950381) |
| Paper (arXiv) | [arXiv:2512.14475](https://arxiv.org/abs/2512.14475) |
| GitHub | [glockyco/Teralizer](https://github.com/glockyco/Teralizer) |

---

## Package Contents

| Archive | Size | Contents |
|---------|------|----------|
| `teralizer-results` | ~1MB | Tables, figures, HTML notebooks |
| `teralizer-core` | ~250MB | Code, database dumps, reference outputs |
| `teralizer-projects-primary` | ~50MB | EqBench + commons-utils source code |
| `teralizer-projects-extended-sample` | ~170MB | 100 sampled RepoReapers projects |
| `teralizer-projects-extended` | ~1.7GB | All 1161 RepoReapers projects |
| `teralizer-data-primary` | ~1.2GB | Logs, tool reports (JUnit/PIT/JaCoCo), generalized tests |
| `teralizer-data-extended` | ~0.3GB | Logs, tool reports (JUnit/PIT/JaCoCo), generalized tests |

- **Browse results only**: `teralizer-results`
- **Verify analysis**: `teralizer-core`
- **Replicate data collection**: `teralizer-core` + a `teralizer-projects-*` archive
- **Inspect intermediate outputs**: Add a `teralizer-data-*` archive

**Setup**: Extract `teralizer-core`, place other archives as siblings, run `./replication/quick-start.sh` (auto-extracts siblings).

---

## System Requirements

- **Docker**: Version 20.10+ with Docker Compose V2
- **Bash**: For running the provided scripts
- **RAM**: 8GB minimum (16GB recommended for pipeline)
- **Disk**: 20GB for quick start, 50GB+ for full reproduction
- **OS**: Linux, macOS (Intel or Apple Silicon), Windows (WSL2)

**Note for Windows users**: Run all commands from within WSL2.

Check requirements:
```bash
./replication/scripts/preflight-check.sh
```

See [REQUIREMENTS.md](REQUIREMENTS.md) for full details.

---

## Quick Start (5 minutes)

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
   cd replication
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
   cd replication
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
   cd replication
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

## Verifying the Import

After importing, verify the data:

```bash
cd replication
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

See [INSTALL.md](INSTALL.md) for detailed verification checkpoints.

---

## Verifying Analysis Results

The package includes pre-computed outputs from our analysis in `analysis/output/original/`. You can verify these by re-running the analysis and comparing results.

### Pre-computed Outputs

The `analysis/output/original/` directory contains:
- `tables/` - LaTeX tables used in the paper
- `data/` - CSV files with computed statistics
- `figures/` - PDF figures used in the paper
- `executed/` - Executed notebooks with cell outputs
- `html/` - HTML exports for easy browser viewing

### Re-running the Analysis

To re-run the analysis notebooks:

```bash
cd replication

# Execute all notebooks on original data (outputs to verify/)
./scripts/run-notebooks.sh verify

# Also generate HTML exports for easy viewing
./scripts/run-notebooks.sh verify --html

# Or run with --dry-run to see what would be executed
./scripts/run-notebooks.sh verify --dry-run
```

This creates outputs in `analysis/output/verify/` using the same original databases.

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

To change variants, set `DATASET_VARIANT` environment variable:
```bash
export DATASET_VARIANT=verify
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

Outputs are generated in `analysis/output/`:
- `tables/` - LaTeX table files
- `figures/` - PDF figure files
- `data/` - CSV data exports

---

## Running the Pipeline

The data collection pipeline can be run on subsets for verification:

### Quick Verification (~5 minutes)

```bash
cd replication
./scripts/run.sh --dataset extended --count 5
```

### Extended Verification (~40 minutes)

```bash
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
cp replication/.env.example replication/.env
```

---

## Complete Pipeline Reproduction

To reproduce all results from scratch (requires days of compute):

### Extended Dataset (~15 hours)

```bash
cd replication

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

After running the pipeline, analyze the reproduced data:

```bash
cd replication

# Run analysis notebooks on the reproduced data
./scripts/run-notebooks.sh replicate

# Compare reproduced outputs against original
./scripts/verify-outputs.sh original replicate
```

The `replicate` variant:
- Reads from `postgres_dev_replication` and `postgres_test_replication` databases
- Writes outputs to `analysis/output/replicate/`

Differences between `original/` and `replicate/` are expected due to the non-determinism noted above.

---

## Stopping Services

```bash
cd replication

# Stop all containers (preserves data)
docker compose down

# Stop and remove all data
docker compose down -v
```

---

## Manual Setup (Alternative to Quick Start)

If you prefer explicit control:

```bash
cd replication

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

## Development

For native development without Docker:

```bash
# Build and run
./gradlew build                                    # Build project including SPF submodules
./gradlew run -Dteralizer.config=<config-file>    # Run with specific configuration

# Database
./gradlew startPostgres    # Start PostgreSQL container
./gradlew stopPostgres     # Stop PostgreSQL container

# Analysis
cd analysis/
uv sync                    # Install Python dependencies
uv run jupyter lab         # Launch notebooks
uv run python validate.py  # Validate changes
```

See `docs/` for architecture and database schema documentation.

---

## File Structure

```
teralizer/
├── README.md                   # This file
├── INSTALL.md                  # Installation instructions
├── STATUS.md                   # Artifact badge claims
├── REQUIREMENTS.md             # System requirements
├── LICENSE                     # License file
├── src/                        # Teralizer Java source code
├── analysis/
│   ├── notebooks/              # Jupyter analysis notebooks
│   ├── src/                    # Python analysis modules
│   └── output/                 # Generated tables, figures, data
├── replication/
│   ├── docker-compose.yml      # Docker services configuration
│   ├── Dockerfile.analysis     # Jupyter container definition
│   ├── quick-start.sh          # One-command setup script
│   ├── datasets/
│   │   ├── postgres_dev.dump   # Primary dataset database dump
│   │   └── postgres_test.dump  # Extended dataset database dump
│   └── scripts/
│       ├── preflight-check.sh  # System requirements check
│       ├── import-databases.sh # Database import script
│       ├── run.sh              # Unified pipeline runner
│       ├── run-notebooks.sh    # Automated notebook execution
│       ├── verify-results.sh   # Database verification
│       └── verify-outputs.sh   # Output comparison
├── project-configs/            # Pipeline configuration files
└── docs/                       # Architecture documentation
```

---

## Citation

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

This artifact is released under [CC BY 4.0](LICENSE). Analyzed projects retain their original licenses.
