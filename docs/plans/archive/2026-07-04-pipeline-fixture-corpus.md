---
title: Pipeline Fixture Corpus — Fast Deterministic Verification
type: plan
status: implemented
created: 2026-07-04
parent: 2026-06-26-teralizer-overview
archived: 2026-07-04
---

# Pipeline Fixture Corpus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Teralizer-owned corpus of tiny synthetic Maven projects that exercises the FULL pipeline (setup → build → SPF extraction → generalization → jqwik validation → collection) in minutes, deterministically, with golden assertions on the DB outcomes — so code changes are verified without a ~60-minute spike re-run.

**Architecture:** Verification is tiered. Tier 0 = unit/model tests (exists). **Tier 1 = this corpus**: one fixture project per behavior family, run by a script (mirroring `scripts/run-reporeapers-rerun.sh`) into a scratch DB (`postgres_verification`), checked by a golden-assertion script against expected per-fixture outcomes (gen counts, `output_spec_class`, `exclusion_info` labels, `jqwik_property_execution.diagnostic_kind`). Tier 2 = a sentinel subset of five stable real spike projects (defined below; no new code). Tier 3 = full spike/corpus — evaluation events only, uniform-settings doctrine applies there and only there.

**Tech stack:** minimal Maven projects (JUnit 4.13.2, `-source/-target 1.8`, no external deps beyond JUnit), bash runner, SQL/Python golden checks (`uv`-run, matching `analysis/` conventions if Python).

**Ground rules for every task:**
- Fixtures live under `verification/fixtures/<name>/` (top level; NOT `projects/`, which is read-only submodules). Fixture `target/` build output and Teralizer's per-run artifacts under fixtures must be gitignored; generated `_*_Generalized_*` sources under fixtures are transient run output, never committed.
- Determinism is the point: jqwik seed is already pinned (`seed = "0"` in generated tests); fixture tests must avoid time/randomness/network/filesystem-order dependence; suites must run in ≪ the 60s ceiling.
- The scratch DB is `postgres_verification` — created/dropped by the runner; NEVER a core or spike DB.
- Dense logic javadoc/comments explain WHY; no runtime numbers/dates in comments. jqwik `@Example` + `org.junit.Assert` for any Teralizer-side test code.
- Commit per task via `bun ~/.omp/agent/skills/commit/commit-helper.ts` (prose body). Never push.

---

## Fixture families (initial set)

Each fixture = one Maven project with one small CUT + one JUnit-4 test class whose assertions land in exactly the target family. Expected outcomes are recorded in the golden file, derived from the *current verified behavior* (post-widening-license, post-boxed-capture).

| fixture | exercises | expected outcome sketch |
|---|---|---|
| `symbolic-int` | computed int return, `assertEquals(literal, f(x))` | SYMBOLIC spec; licensed; jqwik FULL |
| `boxed-returns` | `Integer.valueOf` computed return (attr survives) + `Long.valueOf` (attr lost in vendored fork) | one SYMBOLIC + licensed; one NULL_CONCRETE + `ORACLE_NOT_WIDENABLE` — pins the characterization |
| `thrown-oracle` | both arms of license rule 2: (a) MUT throwing on the concrete path with clean evidence (no concretization, PC empty or naming the params); (b) MUT whose throw sits behind a concretized branch (e.g. a String input passed through an unmodeled JDK call before the throw) | (a) EXCEPTION spec, licensed, jqwik FULL; (b) EXCEPTION spec, `ORACLE_NOT_WIDENABLE` — pins the corrected rule 2 (antiaction falsification). The String-input variant also pins the `SpoonUtils.getTypeReference` NPE fix |
| `boolean-in-pc` | computed boolean (`return a == b`) + primitive pass-through boolean + boxed pass-through boolean (`Boolean.valueOf`) | computed: NULL_CONCRETE licensed (clauses name params); primitive pass-through: SYMBOLIC licensed; boxed pass-through: NULL_CONCRETE + `ORACLE_NOT_WIDENABLE` — pins the license arms and the primitive/boxed distinction |
| `min-value-seeds` | `Long.MIN_VALUE`/`Integer.MIN_VALUE`/`Short` seeds through supplier rendering | builds + validates; pins the cast-operand and narrow-boxed-bridge fixes |
| `string-sound-set` | `equals(const)`/`length`/`isEmpty` string MUTs plus an unsupported `compareTo` assertion | `equals(const)` and `isEmpty` are NULL_CONCRETE, licensed, jqwik FULL 1/1; the length predicate records the current conservative `ORACLE_NOT_WIDENABLE` NULL_CONCRETE gap; `compareTo` is rejected by `StringOperationFilter` before golden generation |
| `old-surefire` | pom pins surefire 2.17 + `-source 1.7` | test-source floor + surefire floor in derived pom; display-name/FQN report matching; validates the whole validation-repair family |
| `all-refused` | a project whose only test targets boxed pass-through boolean (`Boolean.valueOf`) | gens recorded, 0 included, `EXECUTE_TESTS_GENERALIZED` SUCCEEDED with zero generalized classes — pins the fail-loud false-positive fix |
| `filter-degenerate` | a numeric MUT whose PC clause no planner encodes (falls to the residual filter and rejects nearly all generated inputs) | generalization validates with `FILTER_EXHAUSTED_SEED_ONLY`/`LIMITED_TOO_MANY_FILTER_MISSES` diagnostics — pins the filter-degeneracy telemetry the clause-driven spec's phasing consumes |

Growth rule: every future pipeline defect gets a fixture reproducing it before/with its fix (the JadConfig-literal pattern, retroactively encoded by `min-value-seeds`).

Candidate, deliberately deferred: a deterministic mis-pick fixture (T3 ranked guess whose
property seed-kills, pinning the coherence backstop end-to-end). Valuable, but constructing a
*stable* wrong pick couples the fixture to resolver internals; revisit once the corpus is
established. Fragile-assertion shapes, local lifting, and generic receivers stay unit-level by
design — model tests already pin them and pipeline fixtures would add runtime without evidence.

## Sentinel subset (Tier 2 — definition only, no code)

`TDD-Katas`, `JadConfig`, `svdrp4j`, `unicrypt`, `MarkupTagScanner` — bit-identical census across all four 2026-07-03/04 spike runs, jointly covering: large stable suite, boxed converters + MIN_VALUE seeds, display-name reports + boolean-in-PC, all-refused NULL_CONCRETE, old-surefire floor. Run via `REPOREAPERS_CONFIG_DIR` pointing at a five-config subset directory into a scratch DB (~15 min). The flaky five (kouchat, gedcom4j, xenqtt, uaicriteria, sparkey) are NEVER part of verification subsets — they jitter at the 60s ceiling or carry native flakes; they remain evaluation-corpus members only.

---

### Task 1: Runner + scratch-DB plumbing + first fixture end-to-end

**Files:**
- Create: `verification/fixtures/symbolic-int/` (pom + CUT + test)
- Create: `scripts/run-verification-corpus.sh` (mirror `scripts/run-reporeapers-rerun.sh`: per-fixture Teralizer invocation, `--reset-db` semantics, status ledger; drop done-markers — the corpus is small enough to always run whole)
- Create: `project-configs/verification.conf` (profile: `IMPROVED_100_TRIES` only, PIT disabled, standard ceilings) + `project-configs/verification/fixture-<name>.conf` per fixture
- Modify: `.gitignore` (fixture build output)

- [x] **Step 1:** Write the `symbolic-int` fixture: CUT with `int increment(int x) { if (x > 0) return x + 1; return x - 1; }`-class method; test `assertEquals(3, new Cut().increment(2))`. Pom: minimal, JUnit 4.13.2, source/target 1.8.
- [x] **Step 2:** Runner script: creates `postgres_verification` (drop-if-exists; `ALTER DATABASE template1 REFRESH COLLATION VERSION` guard like the jarvis skill documents), runs each fixture config via `./gradlew run -Dteralizer.config=...` with `DB_NAME=postgres_verification`, records exit codes to a ledger, deletes stale `_*_Generalized_*` files under `verification/fixtures/` first.
- [x] **Step 3:** Run it; inspect the DB by hand; record the observed outcome for `symbolic-int` as the first golden entry.
- [x] **Step 4:** Golden-check script (`scripts/check-verification-corpus.sh` or a small Python module under `analysis/` — follow `analysis/` conventions if Python): per fixture, assert gen count, per-gen `is_included`/`exclusion_info`, assertion `output_spec_class`, and `jqwik_property_execution.diagnostic_kind`. Non-zero exit on any mismatch, with a readable diff.
- [x] **Step 5:** Wire a top-level entry point (`scripts/verify-pipeline.sh` = run + check) and document it in `AGENTS.md`'s command table.
- [x] **Step 6:** Commit.

### Task 2: Remaining fixture families

**Files:** `verification/fixtures/<name>/` + `project-configs/verification/fixture-<name>.conf` per family from the table; golden entries per fixture.

- [x] **Step 1:** `boxed-returns`, `boolean-in-pc`, `min-value-seeds` (pure-Java families; golden entries pin the license arms and codegen fixes).
- [x] **Step 2:** `thrown-oracle` — BOTH arms per the family table: (a) clean-evidence throw → licensed, validated; (b) throw behind a concretized branch → `ORACLE_NOT_WIDENABLE` (pins the corrected license rule 2).
- [x] **Step 3:** `string-sound-set` (needs `symbolic.strings` handling — check how string-parameter MUTs are configured in the string-support plan's shipped tasks; scope to the sound set).
- [x] **Step 4:** `old-surefire` (pom pins surefire 2.17, `-source 1.7`; golden entry asserts the derived generalized pom + successful collection).
- [x] **Step 4b:** `all-refused` (pass-through boolean only) and `filter-degenerate` (unencodable PC clause → residual-filter exhaustion diagnostics) per the family table.
- [x] **Step 5:** Full corpus run end-to-end; record total wall time in the task summary (target: single-digit minutes); commit.

### Task 3: Sentinel subset definition

**Files:** `project-configs/sentinel/` (five configs copied from `project-configs/fusion-spike/`), a short README-style note inside the config dir header comments; `AGENTS.md` command-table row.

- [x] **Step 1:** Create the config subset; verify with one run into a scratch DB that all five complete and match their recorded census values.
- [x] **Step 2:** Commit.

---

## Self-review

- **Coverage:** every defect family found in the 2026-07-03 sessions maps to a fixture; the tiers give every future change a verification home cheaper than a spike run.
- **Determinism:** fixtures avoid ceiling jitter by construction; flaky five excluded from all verification tiers.
- **No placeholders:** fixture behavior sketches are concrete; golden values are recorded from observed runs, not invented.
