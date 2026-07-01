---
title: Beyond-JARVIS Census Implementation Plan
type: plan
status: implemented
created: 2026-06-29
parent: 2026-06-29-beyond-jarvis-generalization-census
archived: 2026-06-30
---

# Beyond-JARVIS Census Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `subagent-driven-development` (recommended)
> or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Measure how many sound property generalizations Teralizer produces from
commons-lang/commons-math's own numeric/char tests beyond JARVIS's reported Table-2 set,
within the same two projects, with a per-class funnel and a mutation-score fault-detection gain.

**Architecture:** Promote a compile-gated allowlist of upstream `*Test.java` from the pinned
`source-cache` into dedicated census fixtures; run the existing pipeline (NAIVE_100 +
IMPROVED_100) against a dedicated census DB/data dir; add an analysis-only census report to
`jarvis_scoreboard.py` that reads the existing diagnostic tables and the INITIAL-vs-GENERALIZED
mutant-key set difference. No pipeline/`ProjectSetupTask` changes.

**Tech Stack:** Bash (prep + run scripts), HOCON configs, Java pipeline (unchanged), PostgreSQL,
Python 3 / pandas (`uv`, `ruff`, `ty`, `pytest`) for the report.

**Design invariants (from the spec):**
- The canonical JARVIS-10 fixture, config, DB (`postgres_jarvis_scoreboard`), and
  `compare_to_jarvis` output stay **unchanged**.
- Census runs in a dedicated DB + data dir: `postgres_jarvis_census`, `data/jarvis-census`.
- Only `REJECT` excludes (loop/nested/static-init filters only `DEFER`); the funnel records
  the actual exit stage for `DEFER`-annotated cases.
- Mutation gain = killed mutant-**key** set difference `GENERALIZED \ INITIAL` (same mutated
  classes; GENERALIZED = seed + properties), not a count delta.

---

## File structure

- Modify: `scripts/prepare-jarvis-scoreboard-fixtures.sh` — add census-fixture assembly
  (allowlist copy + dep-extended POM) behind a `--census` flag; canonical path untouched.
- Create: `project-configs/jarvis-scoreboard/commons-lang-3.5-census.conf`
- Create: `project-configs/jarvis-scoreboard/commons-math-3.5-census.conf`
- Create: `scripts/run-jarvis-census.sh` — mirrors `run-jarvis-scoreboard.sh`, guards on the
  census DB/data dir.
- Modify: `analysis/src/teralizer/jarvis_scoreboard.py` — add `get_mutation_scores(..., stage=)`
  param, `get_mutation_gain(...)` (key-set diff), `get_census(...)` funnel, and a `--census` CLI mode.
- Create: `analysis/tests/test_jarvis_census.py` — unit tests for the census queries.
- Create (at the end, from real output): `docs/plans/2026-06-29-beyond-jarvis-census-results.md`
  — the audit doc with the funnel + mutation gain + headline.

---

## Task 1: Census fixtures + compile-gated allowlist

**Files:**
- Modify: `scripts/prepare-jarvis-scoreboard-fixtures.sh`

The script already pins the repos in `source-cache`, copies `src/main`, and writes the
hand-authored scorecard. Add a parallel census-fixture path that copies a curated allowlist of
upstream test classes and an extended POM, leaving the canonical fixtures untouched.

- [ ] **Step 1: Re-read the script before editing.**

Run: `read scripts/prepare-jarvis-scoreboard-fixtures.sh` (confirm `write_pom`, `copy_path`,
`prepare_math_fixture`, `prepare_lang_fixture`, and the `MATH_SHA`/`LANG_SHA` pins are current).

- [ ] **Step 2: Add a census POM writer** (JUnit 4.12 + the deps numeric/char tests commonly need).

Add after `write_pom`:

```bash
write_census_pom() {
  local artifact_id="$1"; local dst="$2"
  cat > "$dst/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>teralizer.jarvis</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.12</version><scope>test</scope></dependency>
  </dependencies>
</project>
POM
}
```

- [ ] **Step 3: Add the allowlist + census-fixture assembly.**

```bash
# Space-separated repo-relative test source paths (resolved from source-cache).
MATH_CENSUS_TESTS="src/test/java/org/apache/commons/math3/util/FastMathTest.java \
src/test/java/org/apache/commons/math3/util/PrecisionTest.java \
src/test/java/org/apache/commons/math3/util/ArithmeticUtilsTest.java \
src/test/java/org/apache/commons/math3/util/MathArraysTest.java \
src/test/java/org/apache/commons/math3/geometry/euclidean/oned/IntervalTest.java \
src/test/java/org/apache/commons/math3/analysis/polynomials/PolynomialFunctionTest.java"
LANG_CENSUS_TESTS="src/test/java/org/apache/commons/lang3/CharUtilsTest.java \
src/test/java/org/apache/commons/lang3/BooleanUtilsTest.java \
src/test/java/org/apache/commons/lang3/math/NumberUtilsTest.java"

prepare_census_fixture() {
  local repo_dir="$1"; local dst="$2"; local artifact="$3"; shift 3
  rm -rf "$dst"; mkdir -p "$dst/src"
  write_census_pom "$artifact" "$dst"
  cp -R "$repo_dir/src/main" "$dst/src/"
  for t in "$@"; do
    if [[ -f "$repo_dir/$t" ]]; then copy_path "$repo_dir" "$t" "$dst"; else echo "DROP(missing): $t"; fi
  done
}
```

- [ ] **Step 4: Gate census assembly behind a flag** so the canonical run is untouched.

At the bottom, wrap the census calls:

```bash
if [[ "${1:-}" == "--census" ]]; then
  CENSUS_FIXTURE_DIR="$ROOT_DIR/data/jarvis-census/fixtures"
  mkdir -p "$CENSUS_FIXTURE_DIR"
  prepare_census_fixture "$CACHE_DIR/commons-math" "$CENSUS_FIXTURE_DIR/commons-math-3.5-census" "commons-math-3.5-census" $MATH_CENSUS_TESTS
  prepare_census_fixture "$CACHE_DIR/commons-lang" "$CENSUS_FIXTURE_DIR/commons-lang-3.5-census" "commons-lang-3.5-census" $LANG_CENSUS_TESTS
  echo "Prepared census fixtures under $CENSUS_FIXTURE_DIR"; exit 0
fi
```

- [ ] **Step 5: Run prep and the compile-gate loop.**

Run: `bash scripts/prepare-jarvis-scoreboard-fixtures.sh --census`
Then for each fixture: `cd data/jarvis-census/fixtures/commons-math-3.5-census && mvn -q test-compile` (and likewise the `commons-lang-3.5-census` fixture); return to repo root after.
Expected: either green, or a missing-symbol error. For each failing class: if it needs a
support base class/resource or a non-junit dependency, **remove it from the allowlist** and
record `DROP(<class>): <reason>`; if it needs only a common dep (e.g. `hamcrest-core` already
transitively present via junit 4.12), it should already compile. Re-run until `test-compile`
is green for the final allowlist.

- [ ] **Step 6: Commit.**

```bash
git add scripts/prepare-jarvis-scoreboard-fixtures.sh
bun skill://commit/commit-helper.ts  # feat(census): assemble compile-gated census fixtures
```

**Acceptance:** `bash scripts/prepare-jarvis-scoreboard-fixtures.sh --census` is idempotent;
`mvn test-compile` is green for both census fixtures; dropped classes are echoed with reasons;
the canonical fixtures and `--census`-less invocation are unchanged.

---

## Task 2: Census configs + run script

**Files:**
- Create: `project-configs/jarvis-scoreboard/commons-{lang,math}-3.5-census.conf`
- Create: `scripts/run-jarvis-census.sh`

- [ ] **Step 1: Write the math census config** (copy of the canonical config; new root-path;
  two variants only).

`project-configs/jarvis-scoreboard/commons-math-3.5-census.conf`:

```hocon
teralizer {
  project {
    root-path = "data/jarvis-census/fixtures/commons-math-3.5-census"
    use-test-generation = false
    use-test-generalization = true
  }
  jpf { max-execution-time = 30, max-path-condition-size = 100000 }
  junit { max-execution-time = 120 }
  pitest { mutators = "DEFAULTS", max-execution-time = 300 }
  generalizations {
    NAIVE_100_TRIES    { algorithm = "NAIVE",    jqwik { tries = 100 } }
    IMPROVED_100_TRIES { algorithm = "IMPROVED", jqwik { tries = 100 } }
  }
}
```

- [ ] **Step 2: Write the lang census config** — identical but
  `root-path = "data/jarvis-census/fixtures/commons-lang-3.5-census"`.

- [ ] **Step 3: Write `scripts/run-jarvis-census.sh`** (clone of `run-jarvis-scoreboard.sh`
  with census guards):

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME_VALUE=${DB_NAME:-postgres_jarvis_census}
DATA_DIR_VALUE=${DATA_DIR:-data/jarvis-census}
if [[ "$DB_NAME_VALUE" != "postgres_jarvis_census" ]]; then echo "Expected DB_NAME=postgres_jarvis_census, got $DB_NAME_VALUE" >&2; exit 1; fi
if [[ "$DATA_DIR_VALUE" != "data/jarvis-census" ]]; then echo "Expected DATA_DIR=data/jarvis-census, got $DATA_DIR_VALUE" >&2; exit 1; fi
configs=("$@")
if [[ ${#configs[@]} -eq 0 ]]; then
  configs=(project-configs/jarvis-scoreboard/commons-lang-3.5-census.conf project-configs/jarvis-scoreboard/commons-math-3.5-census.conf)
fi
for config in "${configs[@]}"; do
  [[ -f "$ROOT_DIR/$config" ]] || { echo "Configuration file not found: $config" >&2; exit 1; }
  DB_NAME="$DB_NAME_VALUE" DATA_DIR="$DATA_DIR_VALUE" DATASET_VARIANT="jarvis" "$ROOT_DIR/gradlew" run -Dteralizer.config="$config" --no-daemon
done
```

- [ ] **Step 4: `chmod +x scripts/run-jarvis-census.sh`** and commit.

```bash
git add project-configs/jarvis-scoreboard/commons-lang-3.5-census.conf project-configs/jarvis-scoreboard/commons-math-3.5-census.conf scripts/run-jarvis-census.sh
bun skill://commit/commit-helper.ts  # feat(census): census configs + isolated run script
```

**Acceptance:** the run script refuses any DB name other than `postgres_jarvis_census`; configs
point at the census fixtures and request only `NAIVE_100_TRIES` + `IMPROVED_100_TRIES`.

---

## Task 3: Census report in `jarvis_scoreboard.py` (TDD)

**Files:**
- Modify: `analysis/src/teralizer/jarvis_scoreboard.py`
- Test: `analysis/tests/test_jarvis_census.py`

Read `jarvis_scoreboard.py:413-470` (`get_mutation_scores`) and `:270-336`
(`get_generated_test_runs`) first to match the existing query conventions
(`mutant_key`, the `is_detected`/`status` columns, project/variant filtering).

- [ ] **Step 1: Write the failing test for the mutation key-set diff.**

```python
# analysis/tests/test_jarvis_census.py
import sqlite3
from teralizer.jarvis_scoreboard import mutation_gain_keys  # to be added

def test_mutation_gain_is_generalized_minus_initial():
    # killed keys: INITIAL {k1}, GENERALIZED {k1,k2,k3}; gain = {k2,k3}
    initial = {"k1"}
    generalized = {"k1", "k2", "k3"}
    assert mutation_gain_keys(generalized, initial) == {"k2", "k3"}
```

- [ ] **Step 2: Run it — expect ImportError/fail.**

Run: `uv run --directory analysis pytest tests/test_jarvis_census.py -q` → FAIL.

- [ ] **Step 3: Add the `mutation_gain_keys` helper** (pure; unit-tested directly).

`get_mutation_scores` already returns the augmented GENERALIZED-suite score (its default stage,
`variant IS NOT NULL`), so it stays unchanged. Add to `jarvis_scoreboard.py`:

```python
def mutation_gain_keys(generalized_keys: set[str], initial_keys: set[str]) -> set[str]:
    """Mutant keys killed by the GENERALIZED (seed+properties) suite but not by INITIAL (seed)."""
    return generalized_keys - initial_keys
```

- [ ] **Step 4: Add `get_mutation_gain(conn, ...)`** — mind the **variant asymmetry**:
  - INITIAL rows (`stage = 'COLLECT_PIT_DATA_INITIAL'`) have **`variant IS NULL`** (the seed
    baseline runs once per project, not per variant); `get_mutation_scores` filters
    `variant IS NOT NULL`, so it **cannot** be reused for INITIAL — write a dedicated query.
  - INITIAL killed mutant-keys grouped per **`(project_id, mutated_class)`** (no variant),
    using the same `mutant_key` expression + `is_detected` filter as `get_mutation_scores`.
  - GENERALIZED killed mutant-keys grouped per **`(project_id, variant, mutated_class)`**.
  - Gain per `(project_id, variant, mutated_class)` =
    `mutation_gain_keys(generalized, initial[(project_id, mutated_class)])` — every generalized
    variant diffed against the single shared INITIAL baseline for its project+class.

- [ ] **Step 5: Add `get_census(conn, ...)`** building the per-class funnel from `assertion`
  (`is_included`, `exclusion_info`), `jqwik_property_execution` (`diagnostic_kind`), and the
  mutation gain. Columns: `class, test_methods, probes, passed_filters, generalized, full_sound,
  killing, mutation_score, mutation_gain` + a by-reason rejection tally derived from
  `exclusion_info` and the `filter_result`/`DEFER` annotations.

- [ ] **Step 6: Add a `--census` CLI mode** to `main()` that connects to
  `postgres_jarvis_census` and prints the funnel + gain. Keep the default and `--sweep` modes
  reading `postgres_jarvis_scoreboard` unchanged.

- [ ] **Step 7: Run tests + lint/type.**

Run: `uv run --directory analysis pytest tests/test_jarvis_census.py -q && uv run --directory analysis ruff check analysis/src/teralizer/jarvis_scoreboard.py && uv run --directory analysis ty check analysis/src/teralizer/jarvis_scoreboard.py`
Expected: PASS / clean.

- [ ] **Step 8: Commit.**

```bash
git add analysis/src/teralizer/jarvis_scoreboard.py analysis/tests/test_jarvis_census.py
bun skill://commit/commit-helper.ts  # feat(census): census funnel + INITIAL-baseline mutation gain
```

**Acceptance:** `mutation_gain_keys` unit-tested; `get_census`/`get_mutation_gain` query the
existing tables (no schema change); the default + `--sweep` scoreboard modes are unchanged;
ruff/ty/pytest green.

---

## Task 4: Run the full census

**Files:** none (run + DB).

- [ ] **Step 1: Create + reset the census DB.**

Run: `docker exec -i postgres-teralizer psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS postgres_jarvis_census;"`
then `... -c "CREATE DATABASE postgres_jarvis_census TEMPLATE template0;"` (template0 avoids the
collation-version pitfall, per `skill://running-the-jarvis-scoreboard`).

- [ ] **Step 2: Run both census configs** (background; ~minutes per the JARVIS-10 baseline ×
  the larger allowlist).

Run: `DB_NAME=postgres_jarvis_census DATA_DIR=data/jarvis-census DATASET_VARIANT=jarvis bash scripts/run-jarvis-census.sh`
Expected: two `BUILD SUCCESSFUL`. Then grep the log for `ERROR t.processing.ProcessingPipeline`
(never trust the gradle exit code alone — per the scoreboard skill, a dropped pipeline task can
still exit 0). Investigate any error that is not the documented `precisionEquals`/maxUlps
raw-bits `NonGeneralizableExpressionException`.

- [ ] **Step 3: Sanity-check the DB.**

Run: `docker exec -i postgres-teralizer psql -U postgres -d postgres_jarvis_census -tAF'|' -c "SELECT diagnostic_kind, count(*) FROM jqwik_property_execution GROUP BY 1;"` — expect FULL rows
(+ possibly LIMITED), no unexpected emptiness; and that `jqwik_execution_run` has rows.

**Acceptance:** both fixtures complete; no undocumented pipeline error; `jqwik_property_execution`
is populated for both projects.

---

## Task 5: Census audit doc

**Files:**
- Create: `docs/plans/2026-06-29-beyond-jarvis-census-results.md` (type: audit)

- [ ] **Step 1: Generate the report.**

Run: `uv run --directory analysis python -m teralizer.jarvis_scoreboard --census`
Capture the per-class funnel, by-reason tally, mutation score + gain, and the headline counts.

- [ ] **Step 2: Write the audit doc** with front-matter (`type: audit`, `created: 2026-06-29`,
  `parent: 2026-06-29-beyond-jarvis-generalization-census`), embedding: the allowlist + dropped
  classes (from `PROVENANCE.md`), the per-class funnel, the by-reason rejection tally, the
  mutation score + INITIAL-baseline gain, and the two headlines (N sound generalizations beyond
  JARVIS's Table-2 set; per-class superset within JARVIS's own Table-2 source classes). State
  the run command + DB so it reproduces.

- [ ] **Step 3: Index + validate + commit.**

```bash
omp-plans index && omp-plans check
git add docs/plans/2026-06-29-beyond-jarvis-census-results.md docs/plans/INDEX.md
bun skill://commit/commit-helper.ts  # docs(plans): beyond-JARVIS census results
```

- [ ] **Step 4: Complete the spec.**

Run: `omp-plans complete 2026-06-29-beyond-jarvis-generalization-census` (and this plan) once
the results doc lands, then `omp-plans index` + `check`.

**Acceptance:** the audit doc states the funnel, mutation gain, and headlines from the real run;
`omp-plans check` green; spec + plan marked implemented/archived.

---

## Verification (whole plan)

- Canonical JARVIS-10 untouched: `postgres_jarvis_scoreboard`, the canonical configs/fixtures,
  and `uv run --directory analysis python -m teralizer.jarvis_scoreboard` output are unchanged.
- `mvn test-compile` green for the final allowlist; dropped classes recorded.
- `pytest` + `ruff` + `ty` green for the analysis changes.
- The census report reproduces from `postgres_jarvis_census` with the documented command.
- No undocumented pipeline error in the run log (raw-bits `precisionEquals`/maxUlps exclusions
  are expected).

## Out of scope (from the spec)

Object/string/array tests (type ceiling); other projects / full RepoReapers re-run; any change
to the canonical JARVIS-10 comparison; per-test "JARVIS failed on X" claims; authoring scorecard
tests; any `ProjectSetupTask`/extra-PIT change (the INITIAL baseline already runs).
