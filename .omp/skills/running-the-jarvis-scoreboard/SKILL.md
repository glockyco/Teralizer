---
name: running-the-jarvis-scoreboard
description: Use when running, refreshing, or debugging the Teralizer JARVIS Table-2 scorecard (comparing IMPROVED vs NAIVE PVC/IC, or validating generator changes before archiving a lane), or when a scorecard run hits DB password-authentication failures, a port-5432 wrong-container conflict, a template1 collation-version mismatch on CREATE DATABASE, stale generated-test compile errors, or a green "BUILD SUCCESSFUL" that actually hid a dropped pipeline task.
---

# Running the JARVIS scoreboard

## Overview

The JARVIS scoreboard runs Teralizer on pinned Commons-Math (`MATH_3_5`) and
Commons-Lang (`LANG_3_5`) fixtures and compares generated jqwik tests against the
JARVIS paper's Table-2 PVC/IC. It uses the dedicated scratch database and data root
selected by `scripts/run-jarvis-scoreboard.sh`.

**Core trap:** `./gradlew run` exits `0` (`BUILD SUCCESSFUL`) even when a *pipeline*
task fails — the pipeline catches the error, drops the downstream tasks, and the JVM
still exits cleanly. A green gradle build is **not** a clean scorecard. Always verify
the pipeline log, not the exit code.

The executable comparison contract and case map live in
`analysis/src/teralizer/eval/reports/rq0_jarvis.py`. Current evidence and source links live in
`analysis/reports/rq0.md` and `analysis/reports/provenance.json`. This skill covers **how to collect
the data**, not what the numbers are.

## When to use

- Refreshing the scorecard to validate generator/pipeline changes before archiving a lane.
- Comparing IMPROVED vs NAIVE PVC, or checking a Table-2 row for regressions.
- Debugging a scorecard run that failed or produced no data.

**Not for:** the primary RepoReapers evaluation (different DB/runner) or any run
against `postgres_dev` / `postgres_test` / `_replication` (read-only; never mutate).

## Runbook

1. **Preflight the DB container.** Runtime credentials come from environment variables,
   then `.env`, then the defaults in `Configuration.java`. Check `DB_USER` and
   `DB_PASSWORD` overrides before connecting so another shell's credentials do not
   select the wrong server. Confirm the project's own container is on the configured
   port, not another project's:
   ```bash
   docker ps --format "{{.Names}} | {{.Status}} | {{.Ports}}" | grep postgres
   # want: postgres-teralizer ... 0.0.0.0:5432->5432/tcp
   ```
   If a foreign container (e.g. `postgres-replication`, creds `teralizer`/`teralizer`,
   no `postgres` role) holds 5432, the run dies with `password authentication failed
   for user "postgres"`. Switch to `postgres-teralizer` (ask before stopping another
   project's container).

2. **Run** (long: SPF + PIT + JaCoCo on both fixtures — minutes; run in the background).
   The runner materializes fixtures, resets the scratch DB (including the template1
   collation-mismatch fallback), and sweeps stale generated tests itself:
   ```bash
   bash scripts/run-jarvis-scoreboard.sh --prepare-fixtures --reset-db
   ```
   `scripts/run-jarvis-scoreboard.sh` defines the scratch targets and their environment
   overrides. Use those overrides rather than `DB_NAME`, which does not select the
   runtime database. Interruption is safe: the runner kills the whole gradle process group.

3. **Verify the run actually succeeded** (not just the gradle exit — see Core trap):
   ```bash
   grep -E "ERROR t.processing.ProcessingPipeline|terminated with exit code|BUILD FAILURE" <run-log>
   ```
   On a pipeline build failure, read the per-project Maven logs:
   `data/jarvis-scoreboard/<fixture>/project-id-N/command-data/<step>.<variant>.*.{output,error}.txt`.

4. **Aggregate and compare.** Run the scorer from the repo root (it chdirs so the relative
   value-log paths resolve): `uv run --directory analysis python -m teralizer.jarvis_scoreboard`
   for the Table-2 head-to-head, `--sweep` for the tries sweep, `--census` for breadth. Interpret
   the output against `analysis/reports/rq0.md`; the raw-bits `Precision` row
   is expected absent (a soundness exclusion), not a run failure.

## Long detached runs (census, rerun) and the `--no-reduction` pass

These runs outlive a session, so launch them through the detached manager rather than
hand-rolling `nohup` (macOS has no `setsid`) or, worse, `kill -9` on the runner — a hard
kill bypasses the runner's cleanup traps and orphans the gradle-forked application JVM and
its PIT minions.

- **Generalization-only pass** — add `--no-reduction` to skip Stage 5 (mutation + coverage).
  Reduction is a separate later run over the same persisted workspace, so a fast Stage-4
  applicability pass never pays the PIT cost:
  ```bash
  scripts/detached-run.sh launch census --sweep-path data/jarvis-census -- \
    bash scripts/run-jarvis-census.sh --prepare-fixtures --reset-db --no-reduction
  ```
- **Check / stop / recover:**
  ```bash
  scripts/detached-run.sh status census   # liveness + last log lines
  scripts/detached-run.sh stop census     # TERM the runner (its traps tear down the gradle group), then a path-sweep backstop
  scripts/detached-run.sh sweep data/jarvis-census   # clear orphans left by a PRIOR hard-killed run
  ```
  `--sweep-path` records the run's data root so `stop` can backstop-sweep any fixture JVM
  that escaped the group teardown. State lives under `data/detached/<name>.{pid,meta,log}`.

## Gotchas

| Symptom | Cause | Fix |
|---|---|---|
| `password authentication failed for user "postgres"` | Foreign container on 5432 (different creds) | Start `postgres-teralizer`; verify with `docker ps` |
| Connection settings differ from `.env` | `build.gradle`'s `getEnv` prefers nonempty environment values. `Configuration.java` uses dotenv-java, which prefers environment values, including empty strings, over `.env`. The runtime database name instead comes from `teralizer.database.name`, not `DB_NAME`. | Check exported connection settings before changing `.env`. For the database target, inspect the runner's `-Dteralizer.database.name` argument, which overrides the run profile. |
| `CREATE DATABASE` → `template1 has a collation version mismatch` | glibc upgraded under the container | `ALTER DATABASE template1 REFRESH COLLATION VERSION;` then CREATE |
| Rebuild fails on `_*Generalized*_Test.java` from a prior run | Failed `BUILD_PROJECT_GENERALIZED` dropped the cleanup task | Delete stale generated tests before re-running (step 3) |
| `BUILD SUCCESSFUL` but no scorecard data | A pipeline task failed and dropped downstream tasks; gradle still exits 0 | Grep the pipeline log for `ERROR`; never trust the gradle exit code |
| `precisionEqualsMaxUlps` excluded | It is a raw-bits probe, **outside** Table-2 (documented concession) | Expected, not a regression. Raw-bits MUTs fail loud / exclude rather than silently concretize — see the reported-case comparison in `analysis/reports/rq0.md` |
| Orphaned `java`/`MutationTestMinion` processes (PPID 1, 0% CPU) from a killed run | The runner was `kill -9`'d, bypassing its cleanup traps, so the gradle-forked JVMs never got torn down | `scripts/detached-run.sh sweep <data-dir>` (path-based tree kill); in future, stop via `scripts/detached-run.sh stop <name>`, never `kill -9` the runner |

## Data boundaries

Use only the scratch targets selected by `scripts/run-jarvis-scoreboard.sh`.
Never read or mutate protected corpora during a scoreboard run. The shared
`scripts/lib/jarvis-run.sh` checks its selected database with the scratch guard
before starting the pipeline.
