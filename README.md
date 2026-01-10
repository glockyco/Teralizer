# Teralizer Replication Package

Replication package for "Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to Property-Based Tests".

## Overview

Teralizer automates the transformation of conventional unit tests into property-based tests. It uses single-path symbolic analysis to extract path-exact specifications from tests and their implementations, enabling transformation to property-based tests that validate assertions across entire input partitions rather than individual input-output pairs.

## Provenance

| Resource | Location |
|----------|----------|
| Zenodo Archive | [10.5281/zenodo.17950381](https://zenodo.org/records/17950381) |
| Paper (arXiv) | [arXiv:2512.14475](https://arxiv.org/abs/2512.14475) |
| GitHub | [glockyco/Teralizer](https://github.com/glockyco/Teralizer) |

## Artifact Inventory

| Artifact | Type | Location | Description |
|----------|------|----------|-------------|
| Teralizer | Software | `src/` | Java tool for test generalization |
| Analysis Notebooks | Code | `analysis/notebooks/` | Jupyter notebooks reproducing paper results |
| Database Dumps | Data | `replication/datasets/` | PostgreSQL dumps with all processing results |
| Project Configs | Config | `project-configs/` | Pipeline configuration files |

## Paper Claims

| Research Question | Notebook | Content |
|-------------------|----------|---------|
| Dataset statistics | `dataset-characteristics.ipynb` | Dataset size and characteristics |
| RQ1: Mutation score effects | `rq1-mutation-detection.ipynb` | Mutation detection rates |
| RQ2: Constraint complexity | `rq1-mutation-detection.ipynb` | Detection comparison by model |
| RQ3: Test suite effects | `rq2-test-suite-effects.ipynb` | Test count, LOC, runtime changes |
| RQ4: Runtime requirements | `rq3-runtime-requirements.ipynb` | Pipeline runtimes, Pareto analysis |
| RQ5-6: Unsuccessful generalizations | `rq4-limitations.ipynb` | Exclusion causes and rates |

## Requirements

- **Hardware**: 8 GB RAM minimum, 25 GB disk
- **Software**: Docker 20.10+, Docker Compose V2
- **OS**: Linux, macOS, or Windows (WSL2)
- **Time**: 15-30 minutes for setup

See [REQUIREMENTS.md](REQUIREMENTS.md) for full details.

## Quick Start

```bash
# 1. Verify prerequisites
./replication/scripts/preflight-check.sh

# 2. Start services and import data
cd replication
./quick-start.sh
```

This starts PostgreSQL, imports database dumps, and opens Jupyter Lab at http://localhost:8888.

**Access Points:**
- Jupyter Lab: http://localhost:8888
- Database UI: http://localhost:18080 (login: teralizer / teralizer)

## Verification

After setup, verify the installation:

1. **Services running**: `docker compose ps` shows three healthy containers
2. **Database populated**: Adminer shows 13 projects in `postgres_dev`, 1161 in `postgres_test`
3. **Notebooks work**: First cell of any notebook executes without errors

See [INSTALL.md](INSTALL.md) for detailed verification checkpoints.

## Reproducing Results

Open Jupyter Lab and run the notebooks in `analysis/notebooks/`:

1. **dataset-characteristics.ipynb** — Dataset overview and statistics
2. **rq1-mutation-detection.ipynb** — RQ1-2: Mutation detection analysis
3. **rq2-test-suite-effects.ipynb** — RQ3: Test suite size and runtime effects
4. **rq3-runtime-requirements.ipynb** — RQ4: Pipeline runtime and Pareto analysis
5. **rq4-limitations.ipynb** — RQ5-6: Exclusion causes and applicability barriers

Outputs are generated in `analysis/output/`:
- `tables/` — LaTeX table files
- `figures/` — PNG figure files
- `data/` — CSV data exports

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port already in use | Edit `.env` to change ports |
| Cannot connect to database | `docker compose restart postgres` |
| Jupyter won't start | `docker compose logs analysis` |

See [replication/README.md](replication/README.md) for detailed troubleshooting.

## Full Reproduction

To re-run the data collection pipeline (requires days of compute):

```bash
# Extended dataset (~15 hours)
./replication/scripts/run.sh --dataset extended

# Primary dataset (~100+ hours, requires manual intervention)
./replication/scripts/run.sh --dataset primary --phase generation
./replication/scripts/run.sh --dataset primary --phase generalization
```

**Note**: Results will vary due to randomized algorithms and machine-dependent timeouts.

## Stopping Services

```bash
cd replication
docker compose down      # Stop containers (preserves data)
docker compose down -v   # Stop and remove all data
```

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

## License

This artifact is released under [CC BY 4.0](LICENSE). Analyzed projects retain their original licenses.
