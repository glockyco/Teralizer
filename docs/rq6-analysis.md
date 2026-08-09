# Canonical RQ6 analysis

RQ6 figures must come from the canonical report implementation, not ad hoc queries over raw task or entity totals:

```bash
scripts/run-rq6-analysis.sh
```

The default database is `postgres_reporeapers_rq6_v6`. Override it with `RQ6_DB`; select report targets with `RQ6_TARGETS`:

```bash
RQ6_DB=postgres_reporeapers_rq6_v6 \
RQ6_TARGETS=md,latex,csv \
scripts/run-rq6-analysis.sh
```

The command materializes the registered `teralizer.eval` RQ6 report. It does not sync files to the paper repository. Generated Markdown, LaTeX, and CSV artifacts live under `analysis/reports/` and `analysis/build/`.

## Authoritative definitions

The definitions live in:

- `analysis/src/teralizer/eval/reports/_funnel.py`: project eligibility, stage survivors, project-level exclusions, and root causes.
- `analysis/src/teralizer/eval/reports/rq6_causes.py`: eligible-corpus item outcomes and filter decisions.
- `analysis/src/teralizer/eval/reports/_taxonomy.py`: reader-facing stages and cause attribution.

Do not duplicate their SQL in one-off scripts. Run the report so all tables use the same eligibility predicate, variant, and stage semantics.

A project is ineligible only when a project-level task fails in one of these stages:

```python
INELIGIBLE_STAGES = {
    "SETUP_PROJECT",
    "ADD_DEPENDENCIES",
    "BUILD_PROJECT_ORIGINAL",
}
```

Failures after those stages are RQ6 outcomes. They belong inside the eligible-project funnel and must not shrink its denominator.

## RQ6 result hierarchy

The report produces three primary table shapes:

1. **Project funnel and processing causes** (`tab:processing-failures`)
   - Starts with eligible projects.
   - Reports survivors through Stages 1 + 2, 3, 4, and 5.
   - The headline end-to-end applicability is `Stage 5 passing / eligible`.
   - The Stage 4 passing count is the number of projects holding a validated generalized test before reduction.
   - Root-cause rows support the prose explaining each stage loss.

2. **Item outcome breakdown** (`tab:exclusions-breakdown`)
   - Restricted to eligible projects.
   - Splits tests, assertions, and generalizations into included, filtering, and failure outcomes.
   - A generalization is included here when `generated_filter_passed`; this is not the same measure as final usability after Stage 5.

3. **Filter decisions** (`tab:exclusions-filtering`)
   - Restricted to eligible projects.
   - Reports distinct evaluated entities per filter with accept, defer, and reject counts.
   - Filter rows overlap and must not be summed as unique exclusions.

## Supporting analysis for prose

Use supporting metrics to explain the primary tables, not as replacement denominators:

- Project-level classified causes from the funnel table explain stage exclusions.
- `task_diagnostic` and JPF extraction telemetry provide concrete failure roots.
- MUT-resolution confidence tiers and candidate choice sensitivity qualify `ParameterType` rejections.
- Generalization lifecycle fields distinguish filter-passed output from final usable output.
- Raw stage success/failure counts are operational diagnostics; they are not the RQ6 project funnel.
- Raw database entity totals include ineligible projects and therefore are not the RQ6 item tables.

Every quoted figure must identify its measure and denominator. Cross-database comparisons must run both databases through the same report code and join projects by `root_path`, never by database ID.

## Freezing a corpus result

The wrapper refuses to generate RQ6 artifacts unless the configured project files, done markers, attempt ledger, and database project paths describe the same complete corpus. It then runs every report query in one read-only, repeatable-read database snapshot.

The defaults validate `data/reporeapers-rerun-v6` against `project-configs/replication/extended`. Set `RQ6_DATA_DIR` and `RQ6_CONFIG_DIR` together when materializing another corpus:

```bash
RQ6_DATA_DIR=data/another-run \
RQ6_CONFIG_DIR=project-configs/another-run \
scripts/run-rq6-analysis.sh
```

Intermediate snapshots must use the lower-level `python -m teralizer.eval` command and remain explicitly labelled provisional. The final generated tables and CSVs are the evidence to cite; do not transcribe values from an interactive query.
