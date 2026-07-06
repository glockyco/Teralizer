---
title: Rerun Report Extension
type: plan
status: active
created: 2026-07-06
parent: 2026-06-26-teralizer-overview
---

# Rerun Report Extension

Extend `analysis/src/teralizer/reporeapers_rerun_report.py` so every question the full
evaluation rerun exists to answer is a telemetry query instead of log parsing. Development
happens NOW against the midpoint snapshot (52k assertions is plenty to develop against);
the final numbers regenerate in minutes once the run lands. Measurement conclusions stay
post-run.

## Design decisions (review these)

- **Extend in place, replace don't parallel.** The report's existing log-parsing sections
  (SPF losses from `task.info` free text, build causes from command logs) are REPLACED by
  telemetry queries, not kept alongside. The tables are the authority now.
- **Baseline compatibility by graceful skip.** The July baseline DB predates the telemetry
  tables. Telemetry-backed sections check table existence and skip with an explicit note
  when absent. This is not a legacy path: the report stays runnable against any snapshot,
  and the skip names what the older snapshot cannot answer.
- **CLI:** `--db` keeps its role, default moves to `postgres_reporeapers_rerun2`. New
  `--baseline-db` (default `postgres_reporeapers_rerun`) and `--ledger`
  (default `data/reporeapers-rerun-2/status.tsv`) feed the delta section.
- **Capped-project exclusion comes from the ledger, not the DB.** The DB has no exit codes.
  The delta section reads `status.tsv`, takes `exit_code == 124` root paths, and excludes
  those projects PAIRWISE (from both sides of every baseline comparison). Baseline joins
  are on `root_path`, never `id`.
- **The R2 decision query is a non-goal here.** `mut_resolution_funnel.py` already owns it.
  The post-run task runs that module against the full snapshot.
- **The paper notebooks' regex classifiers (`exclusions.py`, `rq4_limitations.py`) stay
  untouched.** They serve the frozen primary corpora where no telemetry exists.
  Reconciliation is a separate decision after the paper numbers settle.
- **Exports** follow the existing convention (`save_csv_data` per section).

## Tasks

Each section is one commit. Verification per section: run the report against the live
midpoint snapshot (read-only) and eyeball the output for internal consistency; the
`validate.py --changed` gate before each commit.

- [x] **Section 0 — telemetry integrity.** Totality invariants printed first:
      `assertion == mut_resolution_observation == assertion_semantics` (exact),
      `generalization_lifecycle.generated_source_created` all true,
      lifecycle rows ≤ generalizations, jqwik outcome rows ≥ lifecycle final_usable... any
      violated invariant prints loudly. This is the section that catches a broken writer
      before a 24 h run wastes itself.
- [x] **Section 1 — funnel by reason code.** Replace the filter-name alias table with
      `filter_result.reason_code` counts (assertion- and test-scope), keeping the filter
      class as a secondary column.
- [x] **Section 2 — SPF loss rollup.** `jpf_extraction_summary` aggregated corpus-wide:
      scheduled → instrumented → succeeded → specs written, plus `failure_counts` JSONB
      unpacked into a ranked stable-cause table. Replaces the `task.info` free-text
      classifier. This table is the direct input for the P2 native-peer ranking.
- [x] **Section 3 — build failure causes.** `task_diagnostic` (stage, reason_code, first
      compiler diagnostic) joined with `build_environment_observation` (source/target level,
      generated-source feature flags) for the two build stages. Sizes the Java-8
      generated-source blocker exactly. Replaces the build-log path parser.
- [x] **Section 4 — true yield.** `generalization_lifecycle` stage flags ×
      `jqwik_property_execution.diagnostic_kind`: included vs final_usable per project and
      corpus-wide, with the failure stage/code breakdown for the gap. Kills `is_included`
      as a yield proxy.
- [ ] **Section 5 — assertion semantics profile.** `assertion_semantics` kind × argument
      shape counts, `fail_context` breakdown, matcher families. Sizes assertNotNull/fail
      support work (observability plan Tier C).
- [ ] **Section 6 — baseline deltas.** Dual-DB machinery: same funnel/yield aggregates
      computed on baseline and snapshot, joined on `root_path`, capped projects excluded
      pairwise via the ledger. Sketch of the exclusion, the one load-bearing correctness
      risk:

      ```python
      capped = {row.root_path for row in ledger if row.exit_code == 124}
      # every delta query filters both sides:
      #   WHERE p.root_path NOT IN :capped
      ```

      Reports: assertions reaching SPF, output_spec_class distribution shifts,
      generalizations, included, final_usable — per project and corpus-wide.
- [ ] **Full-snapshot regeneration** (post-run, minutes): rerun the report against the
      completed snapshot, export, and hand the numbers to the rerun plan's post-run tasks.

## Non-goals

- R2 verdict (owned by `mut_resolution_funnel.py`, run post-run).
- Touching the paper notebooks or their classifiers.
- New DB writes of any kind: the report stays read-only.
