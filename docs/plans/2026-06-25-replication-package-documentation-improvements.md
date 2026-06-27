---
title: "Replication Package Documentation Improvements"
type: plan
status: active
created: 2026-06-25
parent: 2026-06-26-teralizer-overview
---

# Replication Package Documentation Improvements

Address evaluator feedback to create an exceptional replication package with clear verification workflows.

---

## Priority 1: Three Verification Workflows (Critical)

**Problem**: Verification info is scattered. Evaluators don't have clear step-by-step instructions.

**Solution**: Add prominent "Verification Workflows" section near top of README with three numbered workflows.

### Changes to `replication/README.md`

Add new section after "Quick Start" (before "Use Cases"):

```markdown
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
```

Also update "Use Cases" table to reference these workflows.

---

## Priority 2: Container Naming Consistency

**Problem**: Scripts mix `docker exec postgres-replication` with `docker compose exec postgres`.

**Files to update**:
- `replication/scripts/verify-results.sh` line 68
- `replication/scripts/import-databases.sh` (multiple lines)

**Change**: Use `docker compose exec -T postgres` consistently.

---

## Priority 3: Database Variant Clarification

**Problem**: `DATASET_VARIANT` explanation unclear - not obvious it controls both database AND output paths.

### Changes to `replication/README.md`

Expand "Verifying Analysis Results" section with clearer explanation:

```markdown
### Understanding Dataset Variants

`DATASET_VARIANT` controls **both database selection and output directory**:

| Variant | Databases Used | Output Directory | Use Case |
|---------|----------------|------------------|----------|
| `original` | - | `output/original/` | Pre-computed reference (read-only) |
| `verify` | postgres_dev, postgres_test | `output/verify/` | Re-run on original data |
| `replicate` | postgres_dev_replication, postgres_test_replication | `output/replicate/` | After running pipeline |

The `original/` outputs ship with the package. Evaluators generate `verify/` or `replicate/` outputs.
```

### Changes to `replication/.env.example`

Expand comments to be clearer about what each variant does.

---

## Priority 4: Explain All Four Databases

**Problem**: README never explains why 4 databases are created.

### Add to README "Databases" section:

```markdown
### Database Structure

The import creates four databases:

| Database | Contents | Purpose |
|----------|----------|---------|
| `postgres_dev` | Primary dataset (13 projects) | Verification workflows |
| `postgres_test` | Extended dataset (1161 projects) | Verification workflows |
| `postgres_dev_replication` | Empty schema | Pipeline reproduction |
| `postgres_test_replication` | Empty schema | Pipeline reproduction |

The `*_replication` databases are populated when you run the pipeline (Workflow 3).
```

---

## Priority 5: Notebook-to-Paper Mapping

**Problem**: No indication which notebook produces which paper RQs.

### Update "Analysis Notebooks" section:

The paper has 6 RQs but notebooks are named rq1-rq4 (two RQs were split during writing).

```markdown
| Notebook | Paper RQs | Description |
|----------|-----------|-------------|
| `dataset-characteristics.ipynb` | Evaluation setup | Dataset statistics and characteristics |
| `rq1-mutation-detection.ipynb` | RQ1, RQ2 | Mutation score; Constraint complexity |
| `rq2-test-suite-effects.ipynb` | RQ3 | Test suite size and runtime |
| `rq3-runtime-requirements.ipynb` | RQ4 | Teralizer efficiency |
| `rq4-limitations.ipynb` | RQ5, RQ6 | Exclusion causes (controlled + real-world) |
```

---

## Priority 6: Minor Polish Items

### 6.1 Update disk space requirement
- README line 60: Change "10GB" to "20GB" for quick start

### 6.2 Add evaluator quick link at top of README
```markdown
**ACM Artifact Evaluators**: See [Verification Workflows](#verification-workflows) to begin.
```

### 6.3 Rename "Full Reproduction" section
- Change to "Running the Complete Pipeline" for clarity

### 6.4 Fix `verify-outputs.sh` exit code
- Exit 1 when `original` vs `verify` has differences (should be identical)
- Exit 0 for `original` vs `replicate` (differences expected)

### 6.5 Improve error messages in `run-notebooks.sh`
- When a notebook fails, show debug command

---

## Files to Modify

| File | Changes |
|------|---------|
| `replication/README.md` | Add verification workflows section, expand database docs, add notebook mapping, add evaluator link, update disk space |
| `replication/scripts/verify-results.sh` | Use `docker compose exec` |
| `replication/scripts/import-databases.sh` | Use `docker compose exec` |
| `replication/scripts/verify-outputs.sh` | Fix exit codes |
| `replication/scripts/run-notebooks.sh` | Improve error messages |
| `replication/.env.example` | Expand variant documentation |

---

## Not Applicable (Verified)

- **C3 (generate-replication-configs.sh)**: Configs are pre-generated and tracked in git. Evaluators don't need to run this script.

---

## Implementation Order

1. README verification workflows section (highest impact)
2. README database explanation
3. Container naming consistency in scripts
4. Variant clarification in README and .env.example
5. Notebook-to-paper mapping (requires verification against paper)
6. Minor polish items
