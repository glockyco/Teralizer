---
title: Run-Target Config — Explicit DB Targets, Workstation-Only .env
type: spec
status: active
created: 2026-07-05
parent: 2026-06-26-teralizer-overview
---

# Run-Target Config — Explicit DB Targets, Workstation-Only .env

**One concern:** `.env` conflates workstation facts (DB credentials, tool paths) with
per-run targets (`DB_NAME`, `DATA_DIR`), and the ambient default is a protected corpus.
Every safe workflow already fights this: all four runner scripts override `DB_NAME` and
carry hand-copied deny-lists that have drifted apart, and the jOOQ codegen read its schema
from whatever live DB the ambient env named. Make run targets explicit per run, shrink
`.env` to workstation facts, enforce one protected-corpus policy in one place, and decouple
codegen from live databases.

## Why now

- The jarvis guard (`scripts/lib/jarvis-run.sh`) accepts `postgres_timeout_retry` and
  `postgres_reporeapers_rerun` today — two protected corpora missing from its deny-list.
- A bare `run-reporeapers-rerun.sh` appends to the protected `postgres_reporeapers_rerun`
  baseline by default.
- The jOOQ codegen silently read the schema of the `.env`-pinned protected corpus instead
  of the target scratch DB, because build.gradle inverted env precedence relative to the
  runtime, docker-compose, and Python readers. The inversion is fixed, but codegen still
  reads whatever live DB the env names.
- `validate.py` requires a `DB_NAME` that no analysis code reads, and flips precedence
  with `override=True` — a third precedence rule in the same repo.

## Design

### 1. Run-target channel

Two HOCON keys become the only way a run names its targets:

```hocon
teralizer.database.name   # per profile; NO reference.conf default — absent means startup failure
teralizer.data-dir        # reference.conf default: data
```

`Configuration` sources `DB_NAME` (hence `DB_CONNECTION_STRING`) and `DATA_DIR` from
`CONFIG` instead of `DOTENV`; the statics keep their shape. A missing `database.name`
throws in static init, before any DB touch. Dotenv keeps only `DB_HOST`, `DB_PORT`,
`DB_USER`, `DB_PASSWORD` (plus the non-DB workstation keys). Runner scripts replace
`DB_NAME=x DATA_DIR=y gradlew run` with `-Dteralizer.database.name=x -Dteralizer.data-dir=y`;
system properties already outrank profile files in `Configuration.buildConfig`, and
build.gradle already forwards every `-Dteralizer.*` property to the application JVM. Scripts
keep their own shell-side `$DATA_DIR` variable for the paths they compute directly (status
ledgers, run-logs, done-markers); only the value handed to the JVM moves to the system
property.

### 2. One protected-corpus policy

`src/main/resources/db/protected-databases.txt` — one name or glob per line:
`postgres`, `postgres_dev`, `postgres_test`, `postgres_timeout_retry`,
`postgres_reporeapers_rerun`, `postgres_fusion_spike`, `*_replication`. Shared by both
layers the way `create-tables.sql` already is. (`postgres_fusion_spike` is in the
verification-script deny-lists but absent from AGENTS.md's four-name canon and the other
runners; drift resolution takes the most-protective union, and AGENTS.md's list is updated
to match.)

- **Java:** a startup guard in `Configuration`. Targeting a protected name requires
  `teralizer.database.allow-protected = true` in the profile; the failure message names
  the DB and the opt-in key. Only real-corpus profiles carry the key.
- **Shell:** `scripts/lib/db-guard.sh` exposing `require_scratch_db <name>`, replacing
  the five drifted inline deny-lists (`run-verification-corpus.sh`,
  `check-verification-corpus.sh`, `run-reporeapers-rerun.sh`, `scripts/lib/jarvis-run.sh`,
  and the name-shape check). `run-reporeapers-rerun.sh` loses its protected default:
  writing to the rerun baseline becomes an explicit `REPOREAPERS_DB` choice.

### 3. Self-contained jOOQ codegen

The `jooq` block in build.gradle hardcodes database `teralizer_codegen`; host, port, and
credentials stay env-sourced workstation facts. New `scripts/regenerate-jooq.sh`:
create `teralizer_codegen` (with the template1 collation-refresh fallback) → apply
`create-tables.sql` → `./gradlew generateJooq` → drop the DB. Generated sources always
match the checked-in DDL; a bare `generateJooq` fails on the missing DB instead of
silently reading a live corpus schema.

### 4. Consistency sweep

- `analysis/validate.py`: drop `DB_NAME` from required vars (no analysis reader exists);
  drop `override=True` so process env wins everywhere.
- `scripts/lib/jarvis-run.sh`: delete the dead `DATASET_VARIANT=jarvis` from the gradle
  invocation (no Java reader; the Python analysis sets its own).
- `docker-compose.yml`: `POSTGRES_DB=postgres` and `ADMINER_DEFAULT_DB=postgres` literals
  (what the `${DB_NAME:-postgres}` fallbacks always resolved to at initdb time).
- `.env` (and any `.env.example`): one credentials block plus tokens and paths; the
  dataset-selection blocks disappear.
- `src/test/resources/reference.conf`: gains `teralizer.database.name` with a scratch
  name so `Configuration` class-load never throws under test.
- `AGENTS.md`: the protected-DB list gains `postgres_fusion_spike` to match the canonical
  policy file; the Database section notes that a run names its DB in its profile.

### 5. Profile migration

Every profile that starts a run names its DB: `verification.conf` →
`postgres_verification`; `eqbench.conf` → `postgres_dev` + `allow-protected`;
`reporeapers-rerun.conf` → a scratch default; example configs → an obviously-scratch
name demonstrating the key. Per-project configs (`sentinel/project-N.conf`,
`verification/fixture-*.conf`, `replication/extended/project-N.conf`) stay DB-less and
compose with a profile that carries it.

## Error handling

- Missing `teralizer.database.name` → startup failure naming the key.
- Protected DB without `allow-protected` → startup failure naming the DB and the key.
- Both fire in `Configuration` static init, before any connection attempt.
- `require_scratch_db` exits non-zero naming the offending DB and the policy file.

## Acceptance

- Bare `./gradlew run -Dteralizer.config=<profile>` with a DB-less profile fails fast
  with the named key; the same run with `-Dteralizer.database.name=<scratch>` proceeds.
- `-Dteralizer.database.name=postgres_timeout_retry` without the opt-in key fails fast;
  `eqbench.conf` (carrying the key) still runs.
- Unit tests: guard behavior (protected exact name, glob, scratch pass, opt-in pass) and
  run-target key precedence through `buildConfig`.
- `scripts/lib/jarvis-run.sh` refuses `postgres_timeout_retry` via the shared guard.
- `scripts/regenerate-jooq.sh` round-trips: fresh clone of the DDL → generate → drop,
  producing zero diff against the tracked generated sources.
- One `verify-pipeline.sh` green (scripts changed → pipeline gate) and one sentinel-subset
  invocation green, proving the override path end to end.
- `.env` contains no `DB_NAME`/`DATA_DIR`; `validate.py` passes without them.

## Non-goals

- Typed config facade / un-freezing `Configuration` statics (documented pitfall, no
  current pain).
- Java/Python config key unification; `project-configs/` restructure.
- The untracked `timeout-retry-*` workspace litter.
