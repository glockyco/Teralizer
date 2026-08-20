# Teralizer - Replication Package

This (https://doi.org/10.5281/zenodo.18242626) is a replication package for the paper:

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
| Zenodo Archive | [10.5281/zenodo.18242626](https://doi.org/10.5281/zenodo.18242626) |
| Paper (arXiv) | [arXiv:2512.14475](https://arxiv.org/abs/2512.14475) |
| Artifact Repository | [glockyco/Teralizer](https://github.com/glockyco/Teralizer) |
| Paper Repository | [glockyco/Teralizer-Paper](https://github.com/glockyco/Teralizer-Paper) |

---

## Package Contents

| Archive | Size | Contents |
|---------|------|----------|
| `teralizer-results` | ~1 MB | Markdown reports, tables, figures, and CSV data |
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
./replication/quick-start.sh
```

This starts PostgreSQL and imports the database dumps. Analysis reports run from the
`analysis/` Python project with `uv`.

**Access points** (open in browser after setup completes):
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
| 2. Verify analysis | Re-run `teralizer.eval` on existing data | `teralizer-core` | `verify/` |
| 3. Verify pipeline | Re-run data collection | `teralizer-core` + `teralizer-projects-*` | `replicate/` |

Workflow 2 should produce outputs identical to `original/`.
Workflow 3 outputs may differ due to resource limits and external factors (see [Complete Reproduction](#complete-reproduction)).

### Workflow 1: Inspect Pre-computed Results (~5 min)

Browse the pre-computed results without re-running anything.

1. Run quick-start:
   ```bash
   ./replication/quick-start.sh
   ```

2. Verify database import:
   ```bash
   ./replication/scripts/verify-results.sh
   ```

3. Explore:
   - **Adminer**: http://localhost:18080 — query databases
   - **Files**: `analysis/output/original/` — pre-computed tables, figures, and CSV data
   - **Reports**: `analysis/reports/` — rendered Markdown reports

### Workflow 2: Verify Analysis (~10 min)

Confirm the analysis code produces identical results on the same data.

1. Re-run every registered report against the shipped databases:
   ```bash
   ./replication/scripts/run-analysis.sh verify
   ```
   This invokes `uv run --directory analysis python -m teralizer.eval all`
   with Markdown, figure, LaTeX, and CSV targets.

2. Compare outputs:
   ```bash
   ./replication/scripts/verify-outputs.sh original verify
   ```

**Expected**: All outputs match exactly. The analysis is deterministic.

### Workflow 3: Verify Pipeline (~15 min)

Confirm the data collection pipeline executes successfully.

1. Run pipeline on a subset of projects:
   ```bash
   ./replication/scripts/run.sh --dataset extended --count 5
   ```

2. Run every registered report on the new data:
   ```bash
   ./replication/scripts/run-analysis.sh replicate
   ```

3. Compare outputs (differences expected due to non-determinism):
   ```bash
   ./replication/scripts/verify-outputs.sh original replicate
   ```

For full reproduction of all projects, see [Complete Reproduction](#complete-reproduction).

### Analysis reports

The Python package `teralizer.eval` is the single analysis path. It builds the
registered reports from the shipped PostgreSQL data and renders Markdown, figures,
LaTeX tables, and CSV data. Run it from `analysis/` under `uv`:

```bash
cd analysis
uv run python -m teralizer.eval all --targets md,figures,latex,csv
```

A single report can be built by its registry name (`dataset` or `rq0` through
`rq6`), and `--db NAME` selects a database when the report supports it. The
`--paper-out PATH` option is reserved for a complete `all` run and copies the
LaTeX tables and CSV data into the paper repository. The publishing wrapper
performs that complete run and refuses to overwrite uncommitted generated files:

```bash
PAPER_OUT=../phd-thesis/chapters/05-teralizer ./scripts/publish-analysis.sh
```

| Report | Paper coverage | Description |
|--------|----------------|-------------|
| `dataset` (`dataset_characteristics`) | Evaluation setup | Dataset statistics and characteristics |
| `rq0` (`rq0_jarvis`) | RQ0 | Comparison with JARVIS reported results and census context |
| `rq1` (`rq1_mutation_score`) | RQ1 | Mutation-score improvement |
| `rq2` (`rq2_constraint_complexity`) | RQ2 | Constraint complexity of generalized tests |
| `rq3` (`rq3_suite_size_runtime`) | RQ3 | Test-suite size and runtime effects |
| `rq4` (`rq4_efficiency_evosuite`) | RQ4 | Efficiency comparison with EvoSuite |
| `rq5` (`rq5_causes`) | RQ5 | Exclusion causes in the controlled dataset |
| `rq6` (`rq6_causes`) | RQ6 | Exclusion causes and diagnostics in the real-world dataset |

---

## Complete Reproduction

Full reproduction requires significant compute time and may produce non-identical results due to:
- Machine-dependent resource limits (timeouts, memory)
- Evaluated projects with unavailable dependencies (artifacts removed from repositories)
- Evaluated projects with unpinned dependency versions (breaking changes in newer versions)

### Extended Dataset (~12 hours)

```bash
./replication/scripts/run.sh --dataset extended
```

Processes all 1161 RepoReapers projects with the same 30-minute per-project cap
used for the paper's extended results. Most projects fail the pipeline early, and
a few long-running ones are stopped at the cap. Both outcomes are recorded in the
run ledger and are expected behavior.

### Primary Dataset (~100+ hours)

The primary dataset requires a two-phase workflow:

1. **Generate tests** (EvoSuite):
   ```bash
   ./replication/scripts/run.sh --dataset primary --phase generation
   ```

2. **Generalize tests**:
   ```bash
   ./replication/scripts/run.sh --dataset primary --phase generalization
   ```

### Analyzing Reproduced Data

```bash
./replication/scripts/run-analysis.sh replicate
./replication/scripts/verify-outputs.sh original replicate
```

---

## Project Structure

Use these executable and accepted authorities when you inspect or change the system:

- Pipeline stages: `src/main/java/teralizer/processing/ProcessingStage.java` and
  `src/main/java/teralizer/processing/PipelinePlanner.java`
- Database schema: `src/main/resources/db/create-tables.sql`
- Report set: `analysis/src/teralizer/eval/registry.py`
- Accepted behavioral contracts: `openspec/specs/`
- Regenerable expression-slice regression: `verification/fixtures/expression-slice/`,
  `project-configs/verification/fixture-expression-slice.conf`, and
  `verification/golden/expression-slice.tsv`

```
teralizer/
├── README.md                   # This file
├── INSTALL.md                  # Installation instructions
├── REQUIREMENTS.md             # System requirements
├── LICENSE-MIT                 # MIT license (code)
├── LICENSE-CC-BY-4.0           # CC BY 4.0 license (data, documentation)
├── src/                        # Java pipeline and schema declarations
├── analysis/
│   ├── src/                    # Python analysis package and registered reports
│   ├── reports/                # Rendered Markdown reports and provenance
│   └── output/                 # Regenerable publication artifacts
├── replication/               # Artifact packaging and reproduction scripts
├── project-configs/            # Pipeline configuration files
├── verification/              # Regenerable fixtures and observed goldens
└── openspec/                   # Active changes and accepted contracts
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
