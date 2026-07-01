---
name: running-the-jarvis-scoreboard
description: Use when running, refreshing, or debugging the Teralizer JARVIS Table-2 scorecard (comparing IMPROVED vs NAIVE PVC/IC, or validating generator changes before archiving a lane), or when a scorecard run hits DB password-authentication failures, a port-5432 wrong-container conflict, a template1 collation-version mismatch on CREATE DATABASE, stale generated-test compile errors, or a green "BUILD SUCCESSFUL" that actually hid a dropped pipeline task.
---

# Running the JARVIS scoreboard

## Overview

The JARVIS scoreboard runs Teralizer on pinned Commons-Math (`MATH_3_5`) and
Commons-Lang (`LANG_3_5`) fixtures and compares generated jqwik tests against the
JARVIS paper's Table-2 PVC/IC. It runs on a **dedicated scratch DB**
(`postgres_jarvis_scoreboard`) and scratch data root (`data/jarvis-scoreboard/`).

**Core trap:** `./gradlew run` exits `0` (`BUILD SUCCESSFUL`) even when a *pipeline*
task fails — the pipeline catches the error, drops the downstream tasks, and the JVM
still exits cleanly. A green gradle build is **not** a clean scorecard. Always verify
the pipeline log, not the exit code.

The authoritative contract — definitions, data boundaries, the case map, the rows→probes
distinction — plus all comparison evidence and current numbers live in
`docs/plans/2026-06-30-jarvis-comparison.md`. This skill covers **how to collect the data**, not
what the numbers are.

## When to use

- Refreshing the scorecard to validate generator/pipeline changes before archiving a lane.
- Comparing IMPROVED vs NAIVE PVC, or checking a Table-2 row for regressions.
- Debugging a scorecard run that failed or produced no data.

**Not for:** the primary RepoReapers evaluation (different DB/runner) or any run
against `postgres_dev` / `postgres_test` / `_replication` (read-only; never mutate).

## Runbook

1. **Preflight the DB container.** The run reads `.env` creds (`DB_USER`/`DB_PASSWORD`,
   default `postgres`/`postgres`) through dotenv. Confirm the project's own container is
   on 5432 — not another project's:
   ```bash
   docker ps --format "{{.Names}} | {{.Status}} | {{.Ports}}" | grep postgres
   # want: postgres-teralizer ... 0.0.0.0:5432->5432/tcp
   ```
   If a foreign container (e.g. `postgres-replication`, creds `teralizer`/`teralizer`,
   no `postgres` role) holds 5432, the run dies with `password authentication failed
   for user "postgres"`. Switch to `postgres-teralizer` (ask before stopping another
   project's container).

2. **Materialize fixtures** (gitignored, not cached — re-clone is fast):
   ```bash
   bash scripts/prepare-jarvis-scoreboard-fixtures.sh
   ```

3. **Reset the scratch DB** (clean run = clean DB; never append onto a prior run):
   ```bash
   docker exec postgres-teralizer psql -U postgres -d postgres \
     -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='postgres_jarvis_scoreboard' AND pid<>pg_backend_pid();"
   docker exec postgres-teralizer psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS postgres_jarvis_scoreboard;"
   docker exec postgres-teralizer psql -U postgres -d postgres -c "CREATE DATABASE postgres_jarvis_scoreboard;"
   ```
   If CREATE fails with `template database "template1" has a collation version
   mismatch` (after a glibc upgrade), refresh once then retry CREATE:
   ```bash
   docker exec postgres-teralizer psql -U postgres -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;"
   ```
   **If the previous run failed**, also delete stale generated tests — a failed
   `BUILD_PROJECT_GENERALIZED` drops the cleanup task, leaving broken files that break
   the rebuild:
   ```bash
   rm -f data/jarvis-scoreboard/fixtures/*/src/test/java/org/apache/commons/*/jarvis/_*Generalized*_Test.java
   ```

4. **Run** (long: SPF + PIT + JaCoCo on both fixtures — minutes; run in the background).
   The script defaults the three env vars; pass them explicitly to be safe:
   ```bash
   DB_NAME=postgres_jarvis_scoreboard DATA_DIR=data/jarvis-scoreboard DATASET_VARIANT=jarvis \
     bash scripts/run-jarvis-scoreboard.sh
   ```

5. **Verify the run actually succeeded** (not just the gradle exit — see Core trap):
   ```bash
   grep -E "ERROR t.processing.ProcessingPipeline|terminated with exit code|BUILD FAILURE" <run-log>
   ```
   On a pipeline build failure, read the per-project Maven logs:
   `data/jarvis-scoreboard/<fixture>/project-id-N/command-data/<step>.<variant>.*.{output,error}.txt`.

6. **Aggregate and compare.** Run the scorer from the repo root (it chdirs so the relative
   value-log paths resolve): `uv run --directory analysis python -m teralizer.jarvis_scoreboard`
   for the Table-2 head-to-head, `--sweep` for the tries sweep, `--census` for breadth. Interpret
   the output against `docs/plans/2026-06-30-jarvis-comparison.md`; the raw-bits `Precision` row
   is expected absent (a soundness exclusion), not a run failure.

## Gotchas

| Symptom | Cause | Fix |
|---|---|---|
| `password authentication failed for user "postgres"` | Foreign container on 5432 (different creds) | Start `postgres-teralizer`; verify with `docker ps` |
| Run targets the wrong DB despite the script | `build.gradle`'s `getEnv` is `.env`-first (jOOQ, build-time) — but the **app** (`Configuration.java`) uses dotenv-java, which is **env-first**, so the script's `DB_NAME` export wins at runtime | None needed; do **not** edit the user's `.env` (it may pin `DB_NAME=postgres_timeout_retry` for other work) |
| `CREATE DATABASE` → `template1 has a collation version mismatch` | glibc upgraded under the container | `ALTER DATABASE template1 REFRESH COLLATION VERSION;` then CREATE |
| Rebuild fails on `_*Generalized*_Test.java` from a prior run | Failed `BUILD_PROJECT_GENERALIZED` dropped the cleanup task | Delete stale generated tests before re-running (step 3) |
| `BUILD SUCCESSFUL` but no scorecard data | A pipeline task failed and dropped downstream tasks; gradle still exits 0 | Grep the pipeline log for `ERROR`; never trust the gradle exit code |
| `precisionEqualsMaxUlps` excluded | It is a raw-bits probe, **outside** Table-2 (documented concession) | Expected, not a regression. Raw-bits MUTs fail loud / exclude rather than silently concretize — see the soundness axis of `docs/plans/2026-06-30-jarvis-comparison.md` |

## Data boundaries

Scratch only: `postgres_jarvis_scoreboard` + `data/jarvis-scoreboard/`. Never read or
mutate `postgres_dev`, `postgres_test`, or any `_replication` database. The run script
preflights `DB_NAME` and refuses those targets.
