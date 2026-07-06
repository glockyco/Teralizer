# Teralizer - Replication Package

> This README documents the frozen Zenodo artifact package.
> For live development guidance, use [AGENTS.md](AGENTS.md) and the planning docs in [docs/](docs/).
> Artifact commands below stay compatible with the archived package layout.

This (https://doi.org/10.5281/zenodo.17950380) is a replication package for the paper:

**Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to Property-Based Tests**

Our work proposes a semantics-based test generalization approach that automatically transforms conventional unit tests into property-based tests by extracting specifications from implementations via single-path symbolic analysis. We demonstrate this approach through Teralizer, a prototype tool for Java that transforms JUnit tests into property-based jqwik tests.

---

## Contents

- [Links](#links)
- [Package Contents](#package-contents)
- [Quick Start](#quick-start)
- [Verification Workflows](#verification-workflows)
- [Complete Reproduction](#complete-reproduction)
- [Project Structure](#project-structure)
- [Citation](#citation)
- [License](#license)

---

## Links

| Resource | Location |
|----------|----------|
| Zenodo Archive | [10.5281/zenodo.17950380](https://zenodo.org/records/17950380) |
| Paper (arXiv) | [arXiv:2512.14475](https://arxiv.org/abs/2512.14475) |
| Artifact Repository | [glockyco/Teralizer](https://github.com/glockyco/Teralizer) |
| Paper Repository | [glockyco/Teralizer-Paper](https://github.com/glockyco/Teralizer-Paper) |

---

## Package Contents

| Archive | Size | Contents |
|---------|------|----------|
| `teralizer-results` | ~1 MB | Tables, figures, HTML notebooks |
| `teralizer-core` | ~250 MB | Code, database dumps, reference outputs |
| `teralizer-projects-primary` | ~45 MB | EqBench + commons-utils source code |
| `teralizer-projects-extended-sample` | ~170 MB | 100 sampled RepoReapers projects |
| `teralizer-projects-extended` | ~1.7 GB | All 1161 RepoReapers projects |
| `teralizer-data-primary` | ~1.1 GB | Logs, tool reports, generalized tests |
| `teralizer-data-extended` | ~260 MB | Logs, tool reports, generalized tests |

**What to download:**
- **Browse results only**: `teralizer-results`
- **Verify analysis**: `teralizer-core`
- **Verify pipeline**: `teralizer-core` + `teralizer-projects-extended-sample`
- **Full reproduction**: `teralizer-core` + `teralizer-projects-primary` + `teralizer-projects-extended`

---

## Quick Start

See [REQUIREMENTS.md](REQUIREMENTS.md) for system requirements and [INSTALL.md](INSTALL.md) for detailed setup instructions.

```bash
cd replication
./quick-start.sh
```

This starts PostgreSQL, imports the database dumps, and launches Jupyter Lab.

**Access points** (open in browser after setup completes):
- Jupyter Lab: http://localhost:8888
- Database UI (Adminer): http://localhost:18080
  - System: PostgreSQL
  - Server: postgres
  - Username: teralizer
  - Password: teralizer
  - Database: postgres_dev (or postgres_test)

**Stopping services:**
```bash
docker compose down      # Stop containers (preserves data)
docker compose down -v   # Stop and remove all data
```

---

## Verification Workflows

Three workflows verify the artifact at increasing levels of depth:

| Workflow | What it does | Archives needed | Output |
|----------|--------------|-----------------|--------|
| 1. Inspect | Browse pre-computed results | `teralizer-results` | — |
| 2. Verify analysis | Re-run notebooks on existing data | `teralizer-core` | `verify/` |
| 3. Verify pipeline | Re-run data collection | `teralizer-core` + `teralizer-projects-*` | `replicate/` |

Workflow 2 should produce outputs identical to `original/`.
Workflow 3 outputs may differ due to resource limits and external factors (see [Complete Reproduction](#complete-reproduction)).

### Workflow 1: Inspect Pre-computed Results (~5 min)

Browse the pre-computed results without re-running anything.

1. Run quick-start:
   ```bash
   cd replication && ./quick-start.sh
   ```

2. Verify database import:
   ```bash
   ./scripts/verify-results.sh
   ```

3. Explore:
   - **Jupyter**: http://localhost:8888 — browse notebooks
   - **Adminer**: http://localhost:18080 — query databases
   - **Files**: `analysis/output/original/` — pre-computed tables and figures

### Workflow 2: Verify Analysis (~10 min)

Confirm the analysis code produces identical results on the same data.

1. Re-run all notebooks:
   ```bash
   ./scripts/run-notebooks.sh verify
   ```

2. Compare outputs:
   ```bash
   ./scripts/verify-outputs.sh original verify
   ```

**Expected**: All outputs match exactly. The analysis is deterministic.

### Workflow 3: Verify Pipeline (~5 min, project-dependent)

Confirm the data collection pipeline executes successfully. RepoReapers projects
take about 44 seconds at the median, but dependency downloads and outliers can
make small samples vary.

1. Run pipeline on a subset of projects:
   ```bash
   ./scripts/run.sh --dataset extended --count 5
   ```

2. Run analysis on the new data:
   ```bash
   ./scripts/run-notebooks.sh replicate
   ```

3. Compare outputs (differences expected due to non-determinism):
   ```bash
   ./scripts/verify-outputs.sh original replicate
   ```

For full reproduction of all projects, see [Complete Reproduction](#complete-reproduction).

### Analysis Notebooks

All evaluation figures and tables in the paper are generated by notebooks in `analysis/notebooks/`. Outputs are saved to `analysis/output/` as LaTeX tables, PDF figures, and CSV data.

| Notebook | Paper Section | Description |
|----------|---------------|-------------|
| `dataset-characteristics.ipynb` | Evaluation Setup | Dataset statistics and characteristics |
| `rq1-mutation-detection.ipynb` | RQ1, RQ2 | Mutation scores, constraint complexity |
| `rq2-test-suite-effects.ipynb` | RQ3 | Test suite size and runtime effects |
| `rq3-runtime-requirements.ipynb` | RQ4 | Teralizer efficiency analysis |
| `rq4-limitations.ipynb` | RQ5, RQ6 | Exclusion causes (primary + extended) |

---

## Complete Reproduction

Full reproduction requires significant compute time and may produce non-identical results due to:
- Machine-dependent resource limits (timeouts, memory)
- Evaluated projects with unavailable dependencies (artifacts removed from repositories)
- Evaluated projects with unpinned dependency versions (breaking changes in newer versions)

### Extended Dataset (~12 hours capped)

```bash
./scripts/run.sh --dataset extended
```

Processes all 1161 RepoReapers projects with the same 30-minute per-project cap
used for the paper's extended results. The capped run is expected to take about
12 hours on an M2. The same dataset measured 23.6 hours uncapped. A handful of
projects legitimately run to the cap and are recorded as exit 124 in
`replication/run-state/extended/status.tsv`. That is expected behavior, not a
hang. The median project takes about 44 seconds.

### Primary Dataset (~100+ hours)

The primary dataset requires a two-phase workflow:

1. **Generate tests** (EvoSuite):
   ```bash
   ./scripts/run.sh --dataset primary --phase generation
   ```

2. **Generalize tests**:
   ```bash
   ./scripts/run.sh --dataset primary --phase generalization
   ```

### Analyzing Reproduced Data

```bash
./scripts/run-notebooks.sh replicate
./scripts/verify-outputs.sh original replicate
```

---

## Project Structure

```
teralizer/
├── README.md                   # This file
├── INSTALL.md                  # Installation instructions
├── REQUIREMENTS.md             # System requirements
├── LICENSE-MIT                 # MIT license (code)
├── LICENSE-CC-BY-4.0           # CC BY 4.0 license (data, docs)
├── src/                        # Teralizer Java source code
├── analysis/
│   ├── notebooks/              # Jupyter analysis notebooks
│   ├── src/                    # Python analysis modules
│   └── output/                 # Generated tables, figures, data
├── replication/
│   ├── docker-compose.yml      # Docker services configuration
│   ├── quick-start.sh          # One-command setup script
│   ├── datasets/               # Database dumps
│   └── scripts/                # Automation scripts
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

This artifact uses dual licensing:

| Component | License |
|-----------|---------|
| Source code (Java, Python, scripts) | [MIT](LICENSE-MIT) |
| Data, documentation | [CC BY 4.0](LICENSE-CC-BY-4.0) |

Analyzed projects retain their original licenses.
