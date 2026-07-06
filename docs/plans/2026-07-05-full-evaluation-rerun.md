---
title: Full Evaluation Rerun (RepoReapers, post-runway)
type: plan
status: active
created: 2026-07-05
parent: 2026-06-26-teralizer-overview
---

# Full Evaluation Rerun (RepoReapers, post-runway)

One measurement event: run all 1,161 RepoReapers projects through the current pipeline into a
fresh snapshot DB, alongside the protected July 1 baseline (`postgres_reporeapers_rerun`), so
every question below is answered by one pass plus baseline-vs-snapshot queries.

## What we execute

`scripts/run-reporeapers-rerun.sh` with the `reporeapers-rerun.conf` profile, unchanged in
substance: single `IMPROVED_100_TRIES` variant, PIT off, reference timeouts. Comparability with
the baseline requires this — the funnel is variant-independent, mutation scores belong to the
JARVIS scoreboard and primary corpora, and changed timeouts would confound every delta.

Target naming (env overrides, no code change):

```bash
REPOREAPERS_DB=postgres_reporeapers_rerun2 \
REPOREAPERS_DATA_DIR=data/reporeapers-rerun-2 \
caffeinate -is scripts/run-reporeapers-rerun.sh
```

- Fresh DB name: the July baseline stays untouched (it is on the protected list). The new
  snapshot gets a durable name and joins the protected list after the run.
- Fresh data dir: the runner resets `status.tsv`/`done/` on DB creation and overwrites
  per-project logs. A new dir preserves the baseline's attempt ledger and build logs, which
  the report's log-parsing sections consume.
- `caffeinate -is`: the run is ~24 h on this machine. The July run took 23.6 h
  (08:45 Jul 1 → 08:18 Jul 2) and the new code admits more work (inherited tests, parse/char
  predicates, exception widening), so budget 24–30 h.
- Resumable: done-markers mean an interrupted run continues with
  the same command. Schema applies itself on first pipeline start (runner creates the bare DB,
  `TestGeneralizationRunner` applies `create-tables.sql` when `project` is missing — verified to
  contain all nine telemetry tables including `actual_shape`/`receiver_provenance`).
- Pause and stop: `touch data/reporeapers-rerun-2/STOP` finishes the in-flight project and
  exits at the boundary with zero loss. INT/TERM kills the in-flight project's process group
  immediately (no done-marker, re-runs on resume). Never sleep the machine mid-run without
  stopping first: wall-clock stage timers fire spuriously after wake and the poisoned project
  completes under a done-marker.
- Per-project wall cap: 30 min (`REPOREAPERS_PROJECT_TIMEOUT`), recorded as exit 124 in
  `status.tsv` and done-marked. Grounded in the July distribution (median 20 s, p99 4.6 min):
  the three projects above 30 min consumed 12.7 h — over half the wall clock — and produced
  zero generalizations, while the deepest data-bearing project finished in 23 min. Capped
  projects have truncated funnels vs. their full July rows, so baseline delta queries exclude
  them pairwise via the exit code. Applied from project 533 onward. The first 532 ran
  uncapped, and every July >30-min project would have been capped to zero data loss anyway.

## What we collect (deliverable → source)

| Deliverable | Source | Resolves |
|---|---|---|
| Applicability funnel refresh | `filter_result` reason codes, `task`, typed `exclusion_info` | RQ-applicability on the broad corpus |
| R2 decision data | `mut_resolution_observation.actual_shape` × `receiver_provenance` counts | P1 gate: >5k local-ctor-rooted zero-arg inspectors → design R2, else out of scope |
| Census-lever conversions | baseline-vs-snapshot deltas on `assertion.output_spec_class`, exclusion funnel | char-predicates + exception-widening corpus effect |
| Parse-predicate conversions | same delta, `ParsePredicates` admissions | parse-predicate corpus effect |
| Inherited-tests flattenable share | screen reason codes, `INHERITED_METHOD_NOT_FLATTENABLE` | recall gained by flattening |
| End-to-end property yield | `generalization_lifecycle`, `jqwik_execution_run`/`jqwik_property_execution` | true usable-property count (not `is_included`) |
| SPF loss rollup | `jpf_extraction_summary`, `task_diagnostic` | ranks native-peer work (feeds P2) |
| Build failure causes | `task_diagnostic`, `build_environment_observation` | sizes the Java-8 generated-source blocker |
| Fresh timeout list | `task` timeouts | regenerated retry lane (post-run) |

Explicitly not collected: mutation scores (PIT off), JARVIS scoreboard numbers (separate
measurement event), primary-corpora RQ numbers (`postgres_dev`).

## Stale tooling to fix

- [x] `docs/database.md` missed seven telemetry tables (`assertion_semantics`,
      `build_environment_observation`, `jpf_extraction_summary`, `task_diagnostic`,
      `generalization_lifecycle`, `generation_clause`, `generation_parameter`). One-paragraph
      entries each, same style as the existing `jqwik_*` entries.
- [ ] `analysis/src/teralizer/reporeapers_rerun_report.py` predates the telemetry tables: it
      parses build logs and free-text where `task_diagnostic`/`jpf_extraction_summary` now hold
      stable codes, and has no funnel sections for lifecycle/jqwik/generation tables.
      Extension is planned and sequenced in `2026-07-06-rerun-report-extension`: development
      NOW against the midpoint snapshot, final numbers regenerated post-run. The R2 decision
      query stays with `mut_resolution_funnel.py`.
- [x] `project-configs/timeout-retry-*.conf` (untracked, now gitignored) were one-offs bound to
      July baseline project ids. Deleted after the rerun produced an empty fresh timeout list
      (see the post-run task below). No retry lane regenerated.

## Tasks

### Pre-flight

- [x] Fix `docs/database.md` (table entries above). Commit.
- [x] Run the fixture corpus once as the pre-measurement smoke:
      `scripts/run-verification-corpus.sh && scripts/check-verification-corpus.sh`.
      Expected: 16/16 fixtures match goldens. This is the wave's single
      `verify-pipeline` event, not a per-change gate. The refactoring batch shipped on a
      build-only gate, and a 24 h event warrants one fixture pass in front of it.
- [x] Disk check: ≥50 GiB free required (July run's net growth was ~2–3 GiB — data dir 911 MB,
      DB 701 MB, build artifacts mostly pre-existing in `projects/`). Currently 52 GiB. Run
      `scripts/packaging/collect-disk-metrics.sh` for the before-snapshot.
- [x] Confirm no concurrent pipeline users: no JARVIS/sentinel/hotspot runs during the event
      (shared `projects/` clones and the port-5432 container).
- [x] Postgres container healthy (`docker compose ps`). The runner handles startup and the
      template1 collation workaround itself.

### Launch and monitor

- [x] Launch with the command above (operator sign-off = running this task).
- [x] Monitor cheaply, no per-project attention: `tail data/reporeapers-rerun-2/status.tsv`,
      row counts on `project`/`task`, disk headroom. Interruption is safe, relaunch resumes.

### Post-run

- [ ] Add `postgres_reporeapers_rerun2` to `src/main/resources/db/protected-databases.txt`.
      Commit.
- [x] Regenerated the extended report against the completed snapshot
      (`python -m teralizer.reporeapers_rerun_report --db postgres_reporeapers_rerun2`): 1,161
      projects, 105,897 assertions, 753 included / 514 final_usable. One integrity fix landed
      first — the harness-killed first HdrHistogram attempt left a duplicate project row and a
      stale `EXECUTE_JPF IN_PROGRESS` task, both reconciled.
- [x] Ran the R2 decision query against the full snapshot. Verdict: OUT OF SCOPE. The sound
      `LOCAL_CTOR` sub-family is 3,621 assertions, below the 5k gate. Recorded in
      `2026-07-02-input-topology-spike`.
- [x] Extracted the fresh timeout list: empty. No task carried a timeout reason code, the
      slowest project ran 7.6 min against the 30 min cap, and the sole capped project is a JPF
      model-method gap that more wall time cannot resolve. The retry-lane design question is
      moot at count zero, so the stale `timeout-retry-*.conf` set was deleted with no
      replacement.
- [ ] Update the overview: rerun gate consumed, queue re-ranked by the new evidence
      (P2 native-peer ranking gets its corpus numbers, JARVIS refresh scheduling per standing
      gate).
