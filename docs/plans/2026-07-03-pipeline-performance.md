---
title: Pipeline Performance — Resolver Memoization & Setup Classpath
type: plan
status: active
created: 2026-07-03
parent: 2026-06-26-teralizer-overview
---

# Pipeline Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the two measured hot spots that dominate corpus runtime without touching any measured semantics: (1) `MethodUnderTestResolver` walks the whole Spoon model twice per assertion (`ANALYZE_TESTS`: 1,485s of ~4,400s on the 23-project spike, 0.13s/task vs 0.0017s pre-fusion — a ~77× regression); (2) `SETUP_PROJECT` parses a full `mvn dependency:build-classpath` log per project (25,400s ≈ 7.1h on the 1,165-project baseline corpus).

**Architecture:** Both fixes are read-path only. The resolver gains a per-model type index (one walk, reused) plus a per-test-class memo of the `Focal` result — resolution outputs must be bit-identical, verified by re-running the tier funnel and census against the spike DB after the Task 5 re-run of `2026-07-03-generalized-validation-repair`. The setup task writes the classpath to a file via `-Dmdep.outputFile` and reads it back instead of scraping ~200 lines of Maven log output.

**Tech stack:** Java 8, Spoon (`CtModel`, `CtType`), jqwik `@Example` + `org.junit.Assert`.

**Ground rules for every task:**
- Run from the repo root. Do NOT run the full test suite or project-wide formatters; verification commands are given explicitly. Run `./gradlew spotlessApply` before committing (the build gate enforces it).
- New tests use `net.jqwik.api.Example` + `org.junit.Assert`; resolver tests build Spoon models via `VirtualFile` + `Launcher` like `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java`.
- Never edit anything under `projects/`.
- Dense logic gets a javadoc explaining *why* (yardstick: `MethodUnderTestResolver`); no planning-doc references in code comments.
- Commit your own task's work: one commit, Conventional Commit subject + prose body, via `bun ~/.omp/agent/skills/commit/commit-helper.ts`. Never push; stage only your files.

---

## Binding decisions

- **No semantic drift.** Resolution results (pick, tier, focal source, candidate order) are bit-identical before/after. Determinism contract 4 of `2026-07-02-static-mut-id-fusion` stays binding: identical input source ⇒ identical resolution, including order.
- **First-match order is part of the contract.** The current linear scans return the *first* model-iteration-order match (`findTypeBySimpleName` prefers `preferredPackage`, else first name match; `findTypeByPath` returns first path match). The index must preserve exactly that.
- **Rejected for now** (recorded so they aren't re-litigated): Maven daemon / `mvnd` (changes the measured execution environment mid-study for ~15% gain); cross-project parallelism (distorts `task.runtime`, violates the measurement rule); JUnit-report parse cache (640s corpus-wide, easy to get subtly wrong); `-o` offline mode for setup (deferred until wanted).

---

### Task 1: Resolver type index + focal memo

**Files:**
- Modify: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java`
- Test: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java` (extend)

The only whole-model walks are `findTypeBySimpleName` (line 1160) and `findTypeByPath` (line 1176), both called from `resolveFocalType` (line 1131), which `resolveInternal` invokes **per assertion** (line 99) although its result depends only on `testMethod.getDeclaringType()`.

- [x] **Step 1: Write the failing behavioral tests** (they pass before AND after — they pin the contract the optimization must preserve; plus one identity test that fails before memoization exists is impractical here, so contract tests + timing sanity are the guard):
  - duplicate simple names across two packages: focal resolution from a test in package `a` picks the `a`-package type (preferred package), from an unrelated package picks the first-in-model-order type;
  - repeated `resolve` calls on two assertions of the same test method return equal focal (`testedClassQualifiedName`, `FocalSource`) — same for a second test class in the same model;
  - a model with a name-derived AND path-derived focal still reports `PATH_AND_NAME`/`NAME_ONLY`/`PATH_ONLY` exactly as today (cover all three via three small virtual models — mirror the existing test's `VirtualFile` setup; path-derived cases need real files on disk if `SourcePosition.getFile()` must be a real file — check how existing tests handle `realMirrorPath` returning null for virtual files, and scope the path-index tests accordingly: if virtual models never exercise the path arm, say so in the test javadoc and rely on the census re-verification for that arm).
- [x] **Step 2: Run** `./gradlew test --tests 'teralizer.spoon.analysis.MethodUnderTestResolverTest'` → green (baseline).
- [x] **Step 3: Implement the index.** Private static final `Map<CtModel, TypeIndex>` behind a `WeakHashMap` wrapped in `Collections.synchronizedMap` (models die with the per-project pipeline; weak keys prevent cross-project leaks). `TypeIndex` holds, built in ONE `model.getElements(CtType.class::isInstance)` walk in model iteration order:
  - `Map<String, List<CtType<?>>> bySimpleName` (`LinkedHashMap`, lists in encounter order),
  - `Map<String, CtType<?>> byNormalizedPath` (first-wins; keys via the existing `normalizePath`, skipping types whose `sourcePath(...)` is null).
  Rewrite `findTypeBySimpleName`/`findTypeByPath` to query the index — `findTypeBySimpleName` scans the (short) per-name list for `preferredPackage`, else returns the list head; `findTypeByPath` is a map lookup with `normalizePath(mirroredPath)`. Javadoc on `TypeIndex`: why it exists (the linear scans were O(model) per assertion — quadratic over a project's assertion count) and the order-preservation contract.
- [x] **Step 4: Memoize the focal.** Private static final synchronized `WeakHashMap<CtType<?>, Focal>`; `resolveFocalType` computes once per declaring type. `Focal` must be immutable (verify; it is a small value holder today).
- [x] **Step 5: Run the test class** → green. Also run the two neighbor suites touching resolution: `./gradlew test --tests 'teralizer.processing.task.TestAnalysisTaskTest' --tests 'teralizer.processing.task.MutResolutionObservationMapperTest'` → green.
- [x] **Step 6: Timing sanity (informal, not committed):** time `./gradlew test --tests 'teralizer.spoon.analysis.MethodUnderTestResolverTest'` before/after locally; report the delta in the task summary (no assertion on wall-clock in tests — flaky).
- [x] **Step 7:** `./gradlew spotlessApply`, tick these boxes, commit.

**Commit subject:** `perf(mut-id): index type lookups and memoize focal resolution`

---

### Task 2: Setup classpath via output file

**Files:**
- Modify: `src/main/java/teralizer/processing/task/ProjectSetupTask.java` (`setupMavenProjectClasspath`, lines 286-335)
- Test: `src/test/java/teralizer/processing/task/ProjectSetupClasspathTest.java` (create)

`setupMavenProjectClasspath` runs `mvn dependency:build-classpath` and scrapes the line after `Dependencies classpath:` from the full log. `maven-dependency-plugin` supports `-Dmdep.outputFile=<file>` — the classpath lands in a file, the log becomes irrelevant, and `-q` cuts Maven's output work.

- [x] **Step 1: Extract the pure assembly.** Package-private static `assembleClasspath(String rawClasspath, Path mainCompiled, Path testCompiled, Path workingDir)` returning the joined string — exact current semantics (main + test compiled paths first, then each dependency path relativized against `workingDir`; empty raw ⇒ just the two compiled paths).
- [x] **Step 2: Write the failing test** for `assembleClasspath`: (a) two deps → relativized, order kept; (b) empty raw string → only the two compiled paths; (c) separator is `File.pathSeparator`. Run `./gradlew test --tests 'teralizer.processing.task.ProjectSetupClasspathTest'` → RED (method missing) → implement → GREEN.
- [x] **Step 3: Swap the subprocess.** Command becomes `mvn -q dependency:build-classpath -Dmdep.outputFile=<absolute tmp file>` (create via `Files.createTempFile("teralizer-classpath", ".txt")`, delete in a `finally`). Read the file (`UTF-8`, trimmed) instead of scraping stdout. Keep: `exitCode != 0` ⇒ RuntimeException with captured output; drop the `error.toString().isEmpty()` requirement in the success check ONLY if `-q` still routes warnings to stderr in a way that breaks currently-passing projects — verify with the smoke test below and keep behavior conservative (treat non-empty stderr + exit 0 as success but log it, since `-q` makes stderr noise more likely; javadoc why).
- [x] **Step 4: Smoke test against a real project** (writes only to `/tmp` and the project's `target/`, allowed): run the goal manually, e.g. `cd projects/github_com_hampelratte_svdrp4j && mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/teralizer-cp-smoke.txt && head -c 200 /tmp/teralizer-cp-smoke.txt` — confirm the file holds the colon-separated classpath and stdout is near-empty. Compare against the classpath currently stored for that project in the spike DB (`SELECT classpath FROM project WHERE root_path LIKE '%svdrp4j%'` on `postgres_fusion_spike`, via `docker exec -i postgres-teralizer psql -U postgres -d postgres_fusion_spike -c "..."`) — same dependency set after relativization.
- [x] **Step 5:** `./gradlew test --tests 'teralizer.processing.task.ProjectSetupClasspathTest'` green; `./gradlew compileJava compileTestJava` green; `./gradlew spotlessApply`; tick boxes; commit.

**Commit subject:** `perf(pipeline): read setup classpath from file instead of log scrape`

---

### Task 3: Corpus-scale verification (rides the validation-repair re-run)

No dedicated run. The Task 5 re-run of `2026-07-03-generalized-validation-repair` executes with both changes in place; its census-stability acceptance (≈2003 gens, per-project counts unchanged) plus a funnel re-run (`uv run --directory analysis python -m teralizer.mut_resolution_funnel` with `DB_NAME_TEST=postgres_fusion_spike`) IS the bit-identical evidence for Task 1, and `SETUP_PROJECT` avg runtime from the fresh `task` table quantifies Task 2.

- [ ] **Step 1:** After that re-run: funnel tier counts identical to the recorded pre-optimization numbers (T1 19,251 / T2 1,748 / T3 2,770 / T4 908 / T5 7,883 of 32,560); census per-project counts identical.
- [ ] **Step 2:** Record `ANALYZE_TESTS` and `SETUP_PROJECT` avg/total deltas in `2026-06-28-mut-id-targeting-and-coverage` next to the existing cost-delta table.

---

## Self-review

- **Coverage:** both measured hot spots have a task; corpus verification is explicit and free-riding on an already-required run.
- **Determinism:** first-match/preferred-package order pinned by Task 1 Step 1 tests + funnel/census identity in Task 3.
- **No placeholders:** every step names files, lines, commands, and expected outcomes.
