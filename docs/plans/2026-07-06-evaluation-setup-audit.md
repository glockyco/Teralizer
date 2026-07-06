---
title: Evaluation Setup Audit
type: audit
status: active
created: 2026-07-06
parent: 2026-06-26-teralizer-overview
---

# Evaluation Setup Audit

One investigation: the run-script, config, analysis, and repo-hygiene state of the evaluation
setup after the supervision-library wave, with a ranked cleanup proposal. Evidence gathered by
four read-only exploration passes plus git verification. The live rerun constrains nothing
here except gradle-touching smokes.

## Verified findings

### Dead weight (zero references, evidence checked)

- `project-configs/examples/` (4 files), `test.conf`, `commons-utils-pit-defaults.conf` —
  all untracked, zero references. The top-level `example-*.conf` set supersedes `examples/`.
- `project-configs/evaluation/` — empty directory, zero references.
- Root scratch: `run_timeout_retry.sh` (drops the protected `postgres_timeout_retry`
  DIRECTLY, predating and violating the db-guard design), `generate_timeout_configs.sh`,
  `tekst.txt` (0 B), `data/teralizer.db` (0 B).
- `analysis/pyproject.toml` excludes `notebooks/legacy/` from ruff and AGENTS.md documents
  that exclusion, but the directory does not exist.

### Misplacement and duplication

- `scripts/` mixes operator tooling (`run-*`, `verify-*`, `check-*`, `regenerate-jooq`) with
  packager-only tooling nothing references (`prepare-zenodo-package.sh`,
  `setup-eval-environment.sh` — the latter destructive, doing docker rm/volume/rmi) and
  `collect-disk-metrics.sh` (header claims no hardcoded values, hardcodes two absolute
  workstation paths and image-version filters).
- Scratch-DB lifecycle (terminate + drop + create + template1 collation refresh) is
  copy-pasted in four places: reporeapers driver, verification driver, jarvis-run,
  regenerate-jooq.
- Failure-cause regex classification exists three times in analysis
  (`exclusions.py`, `rq4_limitations.py`, `reporeapers_rerun_report.py`) — all predating the
  `task_diagnostic`/`jpf_extraction_summary` stable codes.
- Five telemetry tables have zero analysis consumers yet: `task_diagnostic`,
  `jpf_extraction_summary`, `generalization_lifecycle`, `assertion_semantics`,
  `build_environment_observation`. The rerun plan already schedules the report extension
  that consumes them.

### Actively wrong docs

- `.omp/skills/running-the-jarvis-scoreboard/SKILL.md` instructs
  `DB_NAME=... DATA_DIR=... DATASET_VARIANT=jarvis` — none of which the current runners
  read (they pin `JARVIS_DB_NAME`/`JARVIS_DATA_DIR` internally and pass system properties).
- Root `README.md`/`INSTALL.md`/`REQUIREMENTS.md`/`STATUS.md` are frozen artifact-package
  docs (5 months old) sitting unlabeled in a development repo. Runtime estimates are wrong
  (measured 23.6 h vs documented ~15 h). Labeling is queued in the replication plan; the
  estimate corrections too.

### Layout debt (documented, not restructured)

- `project-configs/` mixes four species with no signpost: composable profiles
  (`reporeapers-rerun.conf`, `verification.conf`), self-contained lane configs
  (`jarvis-scoreboard/`), per-project corpora, and a generation chain that is easy to
  misread as dead: `extended/` (remote GitHub URLs) is the SOURCE corpus that
  `replication/scripts/generate-replication-configs.sh` transforms into
  `replication/extended/` (local roots), and `replication/scripts/run.sh` falls back to it.
  A physical restructure would break paths in archived plans, scripts, and operator muscle
  memory. A manifest README fixes discoverability at zero risk.
- `data/` mixes live run roots, old run roots, fixture caches, and ~839 loose
  `github_com_*` output dirs from pre-profile eras. Deletion is a provenance decision
  (old snapshots may reference them), so: document now, decide deletion after the paper.
- Local heavyweights on a 95%-full disk: `database/db.sqlite` (613 MB, SQLite predates the
  Postgres migration, nothing references it), `data-dev.zip` (231 MB, 10 months old).
  Deleting frees ~0.85 GB. Operator call, unrecoverable.

## Proposal (ranked, atomic commits each)

### Tier 1 — do now, no gradle contention with the live run

- [x] Delete untracked config litter: `examples/`, `test.conf`,
      `commons-utils-pit-defaults.conf`, `evaluation/`.
- [x] Delete `run_timeout_retry.sh` + `generate_timeout_configs.sh` (superseded by guarded
      drivers; the `timeout-retry-*.conf` set stays until the post-run regeneration task
      replaces it) and the 0-byte litter (`tekst.txt`, `data/teralizer.db`).
- [x] Move packager tooling to `scripts/packaging/` (`prepare-zenodo-package.sh`,
      `setup-eval-environment.sh`, `collect-disk-metrics.sh`) with a README line each.
- [x] Extract `scripts/lib/db-lifecycle.sh` (terminate/drop/create/collation-refresh) and
      use it from the four duplication sites. Verification: bash -n + shellcheck now, the
      already-queued fixture smoke exercises it at the next gate.
- [x] Fix the JARVIS skill's dead env-var instructions.
- [x] Drop the `notebooks/legacy/` exclusion from `analysis/pyproject.toml` and the AGENTS.md
      sentence documenting it.
- [x] Add `project-configs/README.md` (the config species, composition rules, which driver
      consumes which directory, and each lane's retirement trigger). Must state the
      `extended/` → `replication/extended/` generation chain.
- [x] Normalize the JARVIS run-target env style inside the db-lifecycle commit: expose
      `JARVIS_DB`/`JARVIS_DATA_DIR` overrides the way `REPOREAPERS_DB`/`VERIFICATION_DB`
      work, defaults unchanged.
- [ ] Tighten `.gitignore`: `.idea/`, `*.iml`, `project-configs/timeout-retry-*.conf`.
- [x] Add a data-layout note (`data/` ownership per driver, retention boundaries) to
      `docs/database.md` or a short `data/README.md`.

### Tier 2 — sequencing decided

- Replication-package work (supervisor adoption in `replication/scripts/run.sh`, Docker-mode
  kill semantics, root-doc labeling, estimate corrections): pull forward to NOW. Edit-only
  against the frozen-elsewhere artifact, no gradle contention. The local-mode smoke waits
  for the corpus run to finish, so implementation lands now and its E2E check runs tonight.
- Report extension + telemetry consumers + classifier-regex reconciliation: stays post-run.
  The queries only mean something against the fresh snapshot, and the three regex
  classifiers collapse into telemetry queries in the same pass.
- `input_topology.py` retirement: stays gated on the R2 verdict, which consumes its
  successor telemetry from the running rerun.

### Tier 3 — operator decisions (resolved)

- `database/db.sqlite` and `data-dev.zip`: KEEP for now (operator call, 2026-07-06).
- The ~839 loose `data/github_com_*` dirs: KEEP; the data-layout note documents them.

## Explicitly recommended against

- Physically restructuring `project-configs/` into `profiles/`/`lanes/`/`corpora/`. Breaks
  archived-plan references, driver defaults, and documented commands for a discoverability
  win the manifest README already provides.
- Deleting `extended/`, `fusion-spike/`, `spikes/`, `primary/`, `hotspot/`. None are dead:
  `extended/` is the replication corpus generation source and `run.sh` fallback. `primary/`
  is consumed by `replication/scripts/run.sh --dataset primary`. `fusion-spike/` is the
  verification skill's corpus-claims tier. `hotspot/` backs the queued antiaction NPE trace.
  `spikes/` retires with the pending R1/R2 verdict. The manifest states each role and
  retirement trigger.
