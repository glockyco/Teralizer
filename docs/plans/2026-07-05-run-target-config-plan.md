---
title: Run-Target Config — Implementation Plan
type: plan
status: active
created: 2026-07-05
parent: 2026-07-05-run-target-config
---

# Run-Target Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each run name its database and data directory explicitly through HOCON profile keys, shrink `.env` to workstation facts, enforce one protected-corpus policy in a single file read by both Java and shell, and decouple jOOQ codegen from live corpora.

**Architecture:** `Configuration` sources `DB_NAME`/`DATA_DIR` from the merged HOCON `CONFIG` instead of dotenv. A missing database name or a protected name without an explicit opt-in throws at class-load, before any connection. One policy file (`src/main/resources/db/protected-databases.txt`) is read by a Java guard and a shell guard library. Runner scripts pass targets as `-Dteralizer.*` system properties, which already outrank profile files. Codegen runs against a dedicated throwaway `teralizer_codegen` database built from the checked-in DDL.

**Tech Stack:** Java 8, Typesafe Config (HOCON), jOOQ (nu.studer.jooq 5.2.2), JUnit 5 + jqwik, Bash, Docker Postgres, Python (uv/ruff).

---

## File Structure

- **Create** `src/main/resources/db/protected-databases.txt` — canonical protected-corpus name/glob list, one per line, `#` comments allowed.
- **Create** `scripts/lib/db-guard.sh` — sourced library exposing `require_scratch_db`, reading the policy file.
- **Create** `scripts/regenerate-jooq.sh` — create `teralizer_codegen`, apply DDL, `generateJooq`, drop.
- **Modify** `src/main/java/teralizer/util/Configuration.java` — policy-file constant, guard methods, `DB_NAME`/`DATA_DIR` from `CONFIG`, startup guard.
- **Modify** `src/test/java/teralizer/util/ConfigurationTest.java` — guard + policy-load unit tests.
- **Modify** `src/main/resources/reference.conf` — `teralizer.data-dir = data` default (no database name default).
- **Modify** `src/test/resources/reference.conf` — `teralizer.database.name` scratch name for class-load under test.
- **Modify** `build.gradle` — jooq jdbc points at fixed `teralizer_codegen`; drop now-unused `dbName`.
- **Modify** profiles: `project-configs/verification.conf`, `eqbench.conf`, `timeout-retry.conf`, `reporeapers-rerun.conf`, `example-maven-junit4.conf`, `example-maven-junit5.conf`, `example-gradle-junit4.conf`, `example-gradle-junit5.conf`.
- **Modify** scripts: `scripts/run-verification-corpus.sh`, `scripts/check-verification-corpus.sh`, `scripts/run-reporeapers-rerun.sh`, `scripts/lib/jarvis-run.sh`.
- **Modify** `analysis/validate.py` — drop `DB_NAME` requirement and `override=True`.
- **Modify** `docker-compose.yml` — `POSTGRES_DB`/`ADMINER_DEFAULT_DB` literals.
- **Modify** `.env` (local, gitignored — not committed) — shrink to workstation facts.
- **Modify** `AGENTS.md` — protected list + run-target-in-profile note.

---

## Task 1: Protected-DB policy file and Java guard logic (pure, TDD)

No behavior wiring yet — `DB_NAME` still comes from dotenv, so the build stays green. This task adds the policy file and the pure, unit-tested guard helpers.

**Files:**
- Create: `src/main/resources/db/protected-databases.txt`
- Modify: `src/main/java/teralizer/util/Configuration.java`
- Test: `src/test/java/teralizer/util/ConfigurationTest.java`

- [ ] **Step 1: Write the policy file**

Create `src/main/resources/db/protected-databases.txt`:

```
# Protected corpora: never a scratch/experiment target. One name or glob per line.
# Read by teralizer.util.Configuration (Java startup guard) and scripts/lib/db-guard.sh.
# A run may target one of these only with an explicit opt-in
# (teralizer.database.allow-protected = true in the profile, or TERALIZER_ALLOW_PROTECTED=1 for scripts).
postgres
postgres_dev
postgres_test
postgres_timeout_retry
postgres_reporeapers_rerun
postgres_fusion_spike
*_replication
```

- [ ] **Step 2: Write the failing tests**

Add to `ConfigurationTest.java` (imports: `java.util.Arrays`, `java.util.List`, `static org.junit.jupiter.api.Assertions.assertTrue`, `assertEquals` already present):

```java
    @Test
    void isProtectedMatchesExactAndGlob() {
        List<String> patterns = Arrays.asList("postgres_dev", "*_replication");
        assertTrue(Configuration.isProtectedDatabase("postgres_dev", patterns));
        assertTrue(Configuration.isProtectedDatabase("postgres_dev_replication", patterns));
        assertFalse(Configuration.isProtectedDatabase("postgres_verification", patterns));
    }

    @Test
    void loadsProtectedPatternsSkippingCommentsAndBlanks(@TempDir Path dir) throws IOException {
        Path file = writeConf(dir, "protected.txt", "# comment\n\npostgres_dev\n*_replication\n");
        assertEquals(Arrays.asList("postgres_dev", "*_replication"),
            Configuration.loadProtectedDatabasePatterns(file));
    }

    @Test
    void policyFileListsCanonicalProtectedNames() {
        List<String> patterns = Configuration.loadProtectedDatabasePatterns(Configuration.PROTECTED_DB_PATH);
        assertTrue(patterns.contains("postgres_dev"));
        assertTrue(patterns.contains("postgres_reporeapers_rerun"));
        assertTrue(patterns.contains("postgres_fusion_spike"));
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'teralizer.util.ConfigurationTest'`
Expected: FAIL — `isProtectedDatabase`, `loadProtectedDatabasePatterns`, `PROTECTED_DB_PATH` do not exist (compile error).

- [ ] **Step 4: Implement the guard helpers**

In `Configuration.java`, add imports `java.util.ArrayList`, `java.util.List`, `java.util.regex.Pattern` (keep existing). In the Database section, ABOVE the `DB_NAME` declaration, add the policy-path constant:

```java
    public static final Path PROTECTED_DB_PATH = Paths.get("src/main/resources/db/protected-databases.txt");
```

Add these package-private static methods (anywhere in the class body, e.g. after `buildConfig`):

```java
    /** Reads the protected-corpus policy file, skipping blank lines and {@code #} comments. */
    static List<String> loadProtectedDatabasePatterns(Path path) {
        try {
            List<String> patterns = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                patterns.add(trimmed);
            }
            return patterns;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Cannot read protected-database policy at " + path, e);
        }
    }

    /** True when the database name matches any policy pattern. A pattern may use {@code *} as a wildcard. */
    static boolean isProtectedDatabase(String name, List<String> patterns) {
        for (String pattern : patterns) {
            if (globMatches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String pattern, String value) {
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(value);
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(regex.toString());
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'teralizer.util.ConfigurationTest'`
Expected: PASS (all, including the four pre-existing precedence tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/protected-databases.txt src/main/java/teralizer/util/Configuration.java src/test/java/teralizer/util/ConfigurationTest.java
COMMIT_ACTION=commit COMMIT_SUBJECT="feat(db): add canonical protected-corpus policy and guard" \
  COMMIT_BODY="Add one policy file listing the protected corpora, read by a pure, unit-tested Java guard. This is the single source the shell guard and the startup check will both consume, replacing the deny-lists that had drifted apart across the runner scripts." \
  bun ~/.omp/agent/skills/commit/commit-helper.ts
```

---

## Task 2: Wire the run-target channel into Configuration

Flip `DB_NAME`/`DATA_DIR` to read from `CONFIG`, add the startup guard, give the test config a scratch name, and migrate the entry-point profiles. All in one commit so the build and a bare profile run stay coherent.

**Files:**
- Modify: `src/main/java/teralizer/util/Configuration.java`
- Modify: `src/main/resources/reference.conf`
- Modify: `src/test/resources/reference.conf`
- Modify: the eight profile configs listed in File Structure

- [ ] **Step 1: Add the data-dir default to the main reference (no database-name default)**

In `src/main/resources/reference.conf`, inside the top-level `teralizer { ... }` block, add:

```hocon
  data-dir = "data"
```

Do NOT add `database.name` here — its absence is the fail-fast trigger for production runs.

- [ ] **Step 2: Add a scratch database name to the test reference**

In `src/test/resources/reference.conf`, inside `teralizer { ... }`, add:

```hocon
  database {
    name = "postgres_unit_test"
  }
  data-dir = "data"
```

`postgres_unit_test` is not in the policy file, so the startup guard passes under test. No test connects to it — `Configuration` only builds the connection string.

- [ ] **Step 3: Replace the dotenv reads with CONFIG reads and add the guard**

In `Configuration.java`, replace the `DB_NAME` line:

```java
    public static final String DB_NAME = DOTENV.get("DB_NAME", "postgres");
```

with:

```java
    public static final String DB_NAME = resolveDatabaseName();
```

Replace the `DATA_DIR` line:

```java
    public static final Path DATA_DIR = Paths.get(DOTENV.get("DATA_DIR", "data"));
```

with:

```java
    public static final Path DATA_DIR = Paths.get(CONFIG.getString("teralizer.data-dir"));
```

Add the resolver method (uses the Task 1 helpers and `PROTECTED_DB_PATH`):

```java
    private static String resolveDatabaseName() {
        if (!CONFIG.hasPath("teralizer.database.name")) {
            throw new RuntimeException("teralizer.database.name is not set. Name the target database in the "
                + "run profile (for example teralizer.database.name = postgres_verification), or pass "
                + "-Dteralizer.database.name=<db> on the command line.");
        }
        String name = CONFIG.getString("teralizer.database.name");
        boolean allowProtected = CONFIG.hasPath("teralizer.database.allow-protected")
            && CONFIG.getBoolean("teralizer.database.allow-protected");
        if (!allowProtected && isProtectedDatabase(name, loadProtectedDatabasePatterns(PROTECTED_DB_PATH))) {
            throw new RuntimeException("Refusing to target protected database '" + name + "'. Set "
                + "teralizer.database.allow-protected = true in the profile to run against a real corpus.");
        }
        return name;
    }
```

`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` stay on `DOTENV` (workstation facts). Confirm `PROTECTED_DB_PATH` is declared textually before `DB_NAME` (Task 1 placed it there) so the field initializer sees it.

- [ ] **Step 4: Migrate the entry-point profiles**

Add a `database` block inside `teralizer { ... }` in each profile.

`project-configs/verification.conf`:
```hocon
  database { name = "postgres_verification" }
```
`project-configs/eqbench.conf` (real corpus — opt in):
```hocon
  database { name = "postgres_dev", allow-protected = true }
```
`project-configs/timeout-retry.conf` (real corpus — opt in):
```hocon
  database { name = "postgres_timeout_retry", allow-protected = true }
```
`project-configs/reporeapers-rerun.conf` (scratch default; the script overrides per run):
```hocon
  database { name = "postgres_reporeapers_scratch" }
```
Each of `example-maven-junit4.conf`, `example-maven-junit5.conf`, `example-gradle-junit4.conf`, `example-gradle-junit5.conf`:
```hocon
  database { name = "postgres_example" }
```

- [ ] **Step 5: Run the build**

Run: `./gradlew build`
Expected: PASS. `ConfigurationTest` still green (test reference supplies the name); no runtime touches a protected DB.

- [ ] **Step 6: Smoke-test fail-fast and opt-in behavior**

Run: `./gradlew run -Dteralizer.config=project-configs/example-maven-junit4.conf -Dteralizer.database.name=postgres_dev 2>&1 | grep -i "protected"`
Expected: a line refusing protected database `postgres_dev` (sysprop overrides the profile's scratch name, guard fires).

Run: `./gradlew run -Dteralizer.config=project-configs/verification.conf -Dteralizer.database.name=postgres_dev -Dteralizer.database.allow-protected=true 2>&1 | grep -i "protected" || echo "no refusal (opt-in honored)"`
Expected: `no refusal (opt-in honored)` (it then fails later for lack of a project root-path — that is fine, the guard is what we are checking).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/teralizer/util/Configuration.java src/main/resources/reference.conf src/test/resources/reference.conf project-configs/verification.conf project-configs/eqbench.conf project-configs/timeout-retry.conf project-configs/reporeapers-rerun.conf project-configs/example-*.conf
COMMIT_ACTION=commit COMMIT_SUBJECT="feat(config): source DB target from profile, guard protected corpora" \
  COMMIT_BODY="Read the target database and data directory from the merged HOCON config instead of the ambient .env, so each run names its own target and a missing name fails fast at startup. Targeting a protected corpus now requires an explicit allow-protected opt-in in the profile. Migrate the entry-point profiles to carry their database name." \
  bun ~/.omp/agent/skills/commit/commit-helper.ts
```

---

## Task 3: Shell guard library and runner-script migration

**Files:**
- Create: `scripts/lib/db-guard.sh`
- Modify: `scripts/run-verification-corpus.sh`, `scripts/check-verification-corpus.sh`, `scripts/run-reporeapers-rerun.sh`, `scripts/lib/jarvis-run.sh`

- [ ] **Step 1: Write the guard library**

Create `scripts/lib/db-guard.sh`:

```bash
#!/usr/bin/env bash
# Shared database-target guard. Source this, then call require_scratch_db "<name>".
# Reads the canonical protected-corpus policy (src/main/resources/db/protected-databases.txt),
# the same file the Java startup guard consumes. A protected target is refused unless
# TERALIZER_ALLOW_PROTECTED=1 is set, mirroring the profile's allow-protected opt-in.
#
# Requires DB_GUARD_ROOT to point at the repo root before sourcing, or falls back to the
# directory two levels above this file.

_db_guard_root() {
  if [[ -n "${DB_GUARD_ROOT:-}" ]]; then
    printf '%s' "$DB_GUARD_ROOT"
  else
    cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P
  fi
}

require_scratch_db() {
  local name="$1"
  local policy
  policy="$(_db_guard_root)/src/main/resources/db/protected-databases.txt"
  if [[ ! -f "$policy" ]]; then
    echo "db-guard: policy file not found at $policy" >&2
    exit 1
  fi
  if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "db-guard: refusing invalid database name '$name'" >&2
    exit 1
  fi
  local line
  while IFS= read -r line; do
    line="${line%%#*}"
    line="${line// /}"
    [[ -z "$line" ]] && continue
    # shellcheck disable=SC2053  # intentional glob match against the pattern
    if [[ "$name" == $line ]]; then
      if [[ "${TERALIZER_ALLOW_PROTECTED:-}" == "1" ]]; then
        return 0
      fi
      echo "db-guard: refusing protected database '$name' (matches '$line' in $policy)." >&2
      echo "          Set TERALIZER_ALLOW_PROTECTED=1 to override deliberately." >&2
      exit 1
    fi
  done < "$policy"
}
```

- [ ] **Step 2: Wire it into `run-verification-corpus.sh`**

Replace the inline guard (the `case "$DB_NAME" in ... esac` block plus the following `if [[ ! "$DB_NAME" =~ ... ]]` block, lines ~21-27) with:

```bash
source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"
```

Change the gradle invocation (currently `DB_NAME="$DB_NAME" DATA_DIR="$DATA_DIR" "$ROOT_DIR/gradlew" run -Dteralizer.config="$PROFILE,$config" --no-daemon`) to pass targets as system properties:

```bash
    "$ROOT_DIR/gradlew" run \
      -Dteralizer.config="$PROFILE,$config" \
      -Dteralizer.database.name="$DB_NAME" \
      -Dteralizer.data-dir="$DATA_DIR" \
      --no-daemon \
      > "$log_abs" 2>&1
```

- [ ] **Step 3: Wire it into `check-verification-corpus.sh`**

Replace its inline `case ... esac` + name-shape `if` (lines ~13-19) with:

```bash
source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"
```

This script only reads the DB, so no gradle invocation changes.

- [ ] **Step 4: Wire it into `run-reporeapers-rerun.sh`**

Change the default target so it is no longer the protected baseline. Replace:

```bash
DB_NAME="${REPOREAPERS_DB:-postgres_reporeapers_rerun}"
```
with:
```bash
DB_NAME="${REPOREAPERS_DB:-postgres_reporeapers_scratch}"
```

Replace the inline `case "$DB_NAME" in ... esac` guard (lines ~33-37) with:

```bash
source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"
```

Change the gradle invocation to pass targets as system properties:

```bash
    "$ROOT_DIR/gradlew" run \
      -Dteralizer.config="$PROFILE,$config" \
      -Dteralizer.database.name="$DB_NAME" \
      -Dteralizer.data-dir="$DATA_DIR" \
      --no-daemon \
      > "$log_abs" 2>&1
```

- [ ] **Step 5: Wire it into `scripts/lib/jarvis-run.sh`**

Replace the inline `case "$JARVIS_DB_NAME" in ... esac` guard (lines ~35-39) with:

```bash
  source "$ROOT_DIR/scripts/lib/db-guard.sh"
  DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$JARVIS_DB_NAME"
```

Change the gradle invocation (currently `DB_NAME="$JARVIS_DB_NAME" DATA_DIR="$JARVIS_DATA_DIR" DATASET_VARIANT="jarvis" "$ROOT_DIR/gradlew" run -Dteralizer.config="$config" --no-daemon`) to drop the dead `DATASET_VARIANT` and pass system properties:

```bash
    if ! "$ROOT_DIR/gradlew" run \
         -Dteralizer.config="$config" \
         -Dteralizer.database.name="$JARVIS_DB_NAME" \
         -Dteralizer.data-dir="$JARVIS_DATA_DIR" \
         --no-daemon; then
```

Update the echo on the preceding line to drop the `DATASET_VARIANT` mention if present; keep the DB/data-dir echo.

- [ ] **Step 6: Verify the jarvis guard now refuses a protected name**

Run: `bash -c 'set -e; ROOT_DIR=$(pwd); DB_GUARD_ROOT=$ROOT_DIR; source scripts/lib/db-guard.sh; require_scratch_db postgres_timeout_retry'; echo "exit=$?"`
Expected: a refusal line for `postgres_timeout_retry` and a non-zero exit (the closing `echo "exit=$?"` will not print because the guard `exit 1`s the subshell; seeing the refusal line is the pass condition).

Run: `bash -c 'ROOT_DIR=$(pwd); DB_GUARD_ROOT=$ROOT_DIR; source scripts/lib/db-guard.sh; require_scratch_db postgres_verification; echo OK'`
Expected: `OK`.

- [ ] **Step 7: Commit**

```bash
git add scripts/lib/db-guard.sh scripts/run-verification-corpus.sh scripts/check-verification-corpus.sh scripts/run-reporeapers-rerun.sh scripts/lib/jarvis-run.sh
COMMIT_ACTION=commit COMMIT_SUBJECT="refactor(scripts): one shared DB guard, targets via sysprops" \
  COMMIT_BODY="Replace the four drifted inline deny-lists with one guard library that reads the canonical policy file, closing the hole where the jarvis runner accepted protected corpora. Runner scripts now pass the database and data directory as teralizer system properties instead of ambient environment variables, and the reporeapers runner no longer defaults to the protected baseline. Drop the dead DATASET_VARIANT that no Java code reads." \
  bun ~/.omp/agent/skills/commit/commit-helper.ts
```

---

## Task 4: Decouple jOOQ codegen from live corpora

**Files:**
- Modify: `build.gradle`
- Create: `scripts/regenerate-jooq.sh`

- [ ] **Step 1: Point the jooq jdbc URL at a fixed codegen database**

In `build.gradle`, add a codegen DB name beside the other db defs (after the `def dbPassword = ...` line, ~line 34):

```groovy
def codegenDbName = 'teralizer_codegen'
```

In the `jooq { ... }` block, change the jdbc `url`:

```groovy
                    url = "jdbc:postgresql://${dbHost}:${dbPort}/${codegenDbName}"
```

Remove the now-unused `def dbName = getEnv(envProps, 'DB_NAME', 'postgres')` line — grep confirms `dbName` is used only in the jooq url. Keep `getEnv` and its process-env-first precedence (host/port/user/password still use it).

- [ ] **Step 2: Write the regeneration script**

Create `scripts/regenerate-jooq.sh` (mark executable):

```bash
#!/usr/bin/env bash
# Regenerate the tracked jOOQ sources against a throwaway database built from the checked-in DDL,
# so generated code always matches src/main/resources/db/create-tables.sql and never a live corpus.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
CODEGEN_DB=teralizer_codegen
DDL="$ROOT_DIR/src/main/resources/db/create-tables.sql"

_psql() { docker exec -i postgres-teralizer psql -U postgres "$@"; }

_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 || { echo "Postgres (postgres-teralizer) not reachable" >&2; exit 1; }

cleanup() {
  _psql -d postgres -c "DROP DATABASE IF EXISTS $CODEGEN_DB;" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Creating $CODEGEN_DB"
_psql -d postgres -c "DROP DATABASE IF EXISTS $CODEGEN_DB;" >/dev/null
if ! _psql -d postgres -c "CREATE DATABASE $CODEGEN_DB;" 2>/dev/null; then
  _psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
  _psql -d postgres -c "CREATE DATABASE $CODEGEN_DB;" || { echo "CREATE DATABASE failed" >&2; exit 1; }
fi

echo "==> Applying DDL"
_psql -d "$CODEGEN_DB" < "$DDL" >/dev/null

echo "==> Generating jOOQ sources"
"$ROOT_DIR/gradlew" generateJooq --no-daemon || exit $?

echo "==> Done. Review: git diff --stat build/generated-src"
```

- [ ] **Step 3: Run the regeneration and confirm zero diff**

Run: `chmod +x scripts/regenerate-jooq.sh && scripts/regenerate-jooq.sh && git diff --stat build/generated-src`
Expected: script succeeds; `git diff --stat` prints nothing (generated sources already match the DDL from Task earlier in the session; the codegen source is now the DDL, not a live DB).

- [ ] **Step 4: Confirm build still green**

Run: `./gradlew build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle scripts/regenerate-jooq.sh
COMMIT_ACTION=commit COMMIT_SUBJECT="build(jooq): generate from the checked-in DDL, not a live corpus" \
  COMMIT_BODY="Point the jOOQ generator at a throwaway teralizer_codegen database that a dedicated script builds from create-tables.sql, then drops. Generated sources can no longer diverge from the checked-in schema by silently reading whatever corpus the ambient environment named, which was the failure this session hit." \
  bun ~/.omp/agent/skills/commit/commit-helper.ts
```

---

## Task 5: Consistency sweep

**Files:**
- Modify: `analysis/validate.py`
- Modify: `docker-compose.yml`
- Modify: `.env` (local, gitignored — edit but do NOT commit)
- Modify: `AGENTS.md`

- [ ] **Step 1: Fix `validate.py`**

In `analysis/validate.py`, `test_environment()`: change

```python
    load_dotenv(env_path, override=True)

    required_vars = ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"]
```
to
```python
    load_dotenv(env_path)

    required_vars = ["DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD"]
```

Dropping `override=True` makes process env win (matching every other reader); dropping `DB_NAME` removes a requirement no analysis code reads (analysis uses `DB_NAME_DEV`/`DB_NAME_TEST`).

- [ ] **Step 2: De-parameterize docker-compose bootstrap DB**

In `docker-compose.yml`, change `POSTGRES_DB=${DB_NAME:-postgres}` to `POSTGRES_DB=postgres` and `ADMINER_DEFAULT_DB=${DB_NAME:-postgres}` to `ADMINER_DEFAULT_DB=postgres`. Leave `DB_USER`, `DB_PASSWORD`, `DB_PORT` parameterized (workstation facts).

- [ ] **Step 3: Shrink the local `.env` (do not commit)**

Edit `.env` to a single credentials block plus non-DB workstation keys. Remove every `DB_NAME=` and `DATA_DIR=` line and the dataset-selection comment blocks. Target shape:

```
DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=postgres

GITHUB_TOKEN=<existing value, unchanged>
PAPER_REPO_PATH=<existing value, unchanged>
PROJECTS_PATH=<existing value, unchanged>
TERALIZER_DB_DSN=<existing value, unchanged>
```

`.env` is gitignored — this is a workstation edit, not part of any commit.

- [ ] **Step 4: Update AGENTS.md**

In the Database section of `AGENTS.md`: (a) add `postgres_fusion_spike` to the protected-DB list so it matches the policy file; (b) add one sentence: "A run names its target database in its profile (`teralizer.database.name`), or on the command line with `-Dteralizer.database.name`; targeting a protected corpus requires `teralizer.database.allow-protected = true`. The canonical protected list lives in `src/main/resources/db/protected-databases.txt`." Keep the prose plain (no semicolon-chained clauses).

- [ ] **Step 5: Validate analysis and commit the tracked files**

Run: `uv run --directory analysis python validate.py --changed`
Expected: PASS (or the pre-existing baseline; the env check no longer requires `DB_NAME`).

```bash
git add analysis/validate.py docker-compose.yml AGENTS.md
COMMIT_ACTION=commit COMMIT_SUBJECT="chore(config): unify precedence, drop dead DB_NAME requirement" \
  COMMIT_BODY="Make validate.py let process env win like every other reader and stop requiring a DB_NAME the analysis never reads. Fix docker-compose to a literal bootstrap database. Document the run-target-in-profile rule and align the AGENTS.md protected list with the policy file." \
  bun ~/.omp/agent/skills/commit/commit-helper.ts
```

---

## Task 6: Full verification

- [ ] **Step 1: Clean build and unit tests**

Run: `./gradlew build`
Expected: PASS.

- [ ] **Step 2: Pipeline gate (scripts + config changed)**

Run: `scripts/verify-pipeline.sh`
Expected: green, 12 fixtures, golden check passes. This exercises the new sysprop target path end to end (the verification runner now passes `-Dteralizer.database.name`).

- [ ] **Step 3: Sentinel-subset override path**

Run: `REPOREAPERS_DB=postgres_sentinel_verify REPOREAPERS_DATA_DIR=data/sentinel-verify REPOREAPERS_CONFIG_DIR=project-configs/sentinel scripts/run-reporeapers-rerun.sh --reset-db`
Expected: runs against the scratch `postgres_sentinel_verify` (guard passes), five projects complete. Drop the scratch DB and data dir afterward.

- [ ] **Step 4: Mark the plan and spec complete**

```bash
omp-plans complete 2026-07-05-run-target-config-plan
omp-plans complete 2026-07-05-run-target-config
```

---

## Self-review notes

- **Spec coverage:** §1 run-target channel → Task 2; §2 protected policy (Java + shell) → Tasks 1, 3; §3 jOOQ decoupling → Task 4; §4 consistency sweep → Task 5; §5 profile migration → Task 2 step 4; acceptance → Task 6. All covered.
- **Ordering keeps the build green:** Task 1 adds pure helpers only (dotenv still feeds `DB_NAME`); Task 2 flips the source and supplies the test key in the same commit; scripts (Task 3) change only after profiles carry names.
- **Type/name consistency:** `require_scratch_db`, `isProtectedDatabase`, `loadProtectedDatabasePatterns`, `PROTECTED_DB_PATH`, `teralizer.database.name`, `teralizer.database.allow-protected`, `teralizer.data-dir`, `teralizer_codegen`, `TERALIZER_ALLOW_PROTECTED` used identically throughout.
- **Out of scope:** `primary/`, `jarvis-scoreboard/*.conf`, and other per-project configs stay DB-less; they run only through scripts (which supply the sysprop) or fail fast if run bare. The census fixture-count doc drift is tracked separately in the census cleanup.
