---
title: Phase-Decoupled Pipeline — Implementation Plan
type: plan
status: active
created: 2026-07-06
parent: 2026-07-06-phase-decoupled-pipeline
---

# Phase-Decoupled Pipeline Implementation Plan

> **For agentic workers:** the design authority is the spec
> `2026-07-06-phase-decoupled-pipeline`. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Make generation, generalization, and reduction independently
requestable phases that execute sequentially and resume across invocations
against a persisted on-disk workspace.

**Architecture:** First-class `PipelinePhase` objects plus a sequential
`PipelinePlanner` (clear then precondition then drain then next phase). Sequential
draining structurally isolates a reduction failure from generalization results.
Tasks, the priority queue, and the comparator are reused. Reduction (Stage 5
measurement) is renumbered to sort after the full generalization loop.

**Tech Stack:** Java 8, jOOQ (nu.studer.jooq 5.2.2, generated sources tracked
under `build/generated-src/jooq/`), Typesafe Config (HOCON), PostgreSQL DDL in
`src/main/resources/db/create-tables.sql`, JUnit 5 + jqwik, Python analysis
(uv/ruff/pytest), Docker Postgres.

## Execution protocol (operator constraints)

- **Sequential subagents, no parallelism.** The coordinator dispatches exactly
  one implementer subagent per task, waits for it, reviews inline (reads the
  diff, runs the task's named check), then dispatches the next. No reviewer
  subagents and no mandatory re-review loops. Never two implementers at once.
- **No worktrees.** All work happens inline in this workspace on the current
  branch.
- **Atomic commits, no big bang.** Every task ends by committing its own change
  through `skill://commit` (`bun ~/.omp/agent/skills/commit/commit-helper.ts`
  with `COMMIT_ACTION`/`COMMIT_SUBJECT`/`COMMIT_BODY`). A task with ordered
  commit points commits at each. Every commit has a real body explaining the
  why, not a bare subject. Implementers choose the exact wording; suggested
  subjects are given per task.
- **No prose semicolons** in commit bodies, comments, or docs (house style).
- **Per-task hygiene:** run only the tests a task adds or touches. Prefer small,
  fast spikes over heavyweight verification. Do NOT run the full 21-fixture
  `scripts/verify-pipeline.sh` after every change. For a behavior-preserving
  change, spike ONE small fixture (or a focused unit check) to confirm the seam
  works; reserve the full corpus gate for the final verification task. Do not
  run project-wide formatters or the full suite unless a step requires it.
- **Green at every commit.** Each commit compiles and passes the tests the task
  names.

## File map

New (Java, `src/main/java/teralizer/processing/`):
- `PipelinePhase.java` — the three phases, each owning stages, preconditions,
  `clear()`, and a success predicate.
- `PipelinePlanner.java` — sequential per-phase orchestration.
- `ProjectIdentity.java` — resolve-or-create `ProjectRecord` by `root_path` with
  a config-hash guard.

Modified (Java):
- `ProcessingStage.java` — renumber reduction stages to sort last.
- `ProcessingPipeline.java` — variant-aware cascade-drop.
- `task/ProjectSetupTask.java` — scheduling moves out into phases; setup keeps
  paths, build file, classpath, framework detection.
- `task/TestExecutionTask.java` — preserve per-stage `jacoco.exec`.
- `task/JacocoDataCollectionTask.java` — report from the preserved `.exec`.
- `task/CleanupTask.java` — destructive `CLEANUP_PROJECT` gated to fresh start.
- `TestGeneralizationRunner.java` — drive the planner.
- `util/Configuration.java` — `getProjectUseTestReduction()`, identity-hash render.

Modified (schema, config, analysis):
- `src/main/resources/db/create-tables.sql` — `use_test_reduction` column.
- `src/main/resources/db/create-views.sql` — success-view split, mirrored
  `stage_order()` renumber.
- `src/main/resources/reference.conf` and `src/test/resources/reference.conf` —
  `use-test-reduction` default.
- tracked jOOQ sources under `build/generated-src/jooq/main/org/jooq/generated/**`.
- `analysis/src/teralizer/` — success-view consumers unaffected (alias), new
  applicability read where relevant.

New fixtures/tests:
- `src/test/java/teralizer/processing/PipelinePhaseTest.java`
- `src/test/java/teralizer/processing/PipelinePlannerTest.java`
- `src/test/java/teralizer/processing/ProjectIdentityTest.java`
- `src/test/java/teralizer/processing/ProcessingPipelineCascadeTest.java`
- a reduction-resume fixture under `verification/fixtures/`.

---

## Phase 1 — Foundations (additive, each commit independently green)

### Task 1: `use_test_reduction` toggle, DDL column, jOOQ regen, Configuration accessor

Additive schema plus config. Nothing reads the toggle yet, so behavior is
unchanged and the build stays green.

**Files:**
- Modify: `src/main/resources/db/create-tables.sql`
- Modify: `src/main/resources/reference.conf`
- Modify: `src/test/resources/reference.conf`
- Modify: `src/main/java/teralizer/util/Configuration.java`
- Modify: tracked `build/generated-src/jooq/main/org/jooq/generated/tables/Project.java` and `.../records/ProjectRecord.java`
- Test: `src/test/java/teralizer/util/ConfigurationTest.java`

- [ ] **Step 1: Add the DDL column.** In `create-tables.sql`, the `project`
  table has `use_test_generalization BOOLEAN NOT NULL` (around line 45). Add
  directly after it:

```sql
    use_test_reduction      BOOLEAN NOT NULL,
```

- [ ] **Step 2: Add the reference defaults.** In `src/main/resources/reference.conf`,
  the `project { }` block (lines 4-7) currently reads:

```hocon
  project {
    use-test-generation = false
    use-test-generalization = true
  }
```

Add the reduction default (on, to preserve today's end-to-end behavior):

```hocon
  project {
    use-test-generation = false
    use-test-generalization = true
    use-test-reduction = true
  }
```

Apply the identical `use-test-reduction = true` addition to the `project { }`
block in `src/test/resources/reference.conf`.

- [ ] **Step 3: Add the Configuration accessor.** In `Configuration.java`, after
  `getProjectUseTestGeneralization()` (lines 254-256), add:

```java
    public static boolean getProjectUseTestReduction() {
        return CONFIG.getBoolean(TOOL_NAME_LOWER + ".project.use-test-reduction");
    }
```

- [ ] **Step 4: Write the failing Configuration test.** In `ConfigurationTest.java`,
  add a test asserting the reduction default resolves true from the reference
  config. Mirror the existing pattern used for the other project toggles in that
  file (read one first to match the harness). Assertion:

```java
    assertThat(Configuration.getProjectUseTestReduction()).isTrue();
```

- [ ] **Step 5: Run it to confirm it fails to compile then passes.**
  Run: `./gradlew test --tests 'teralizer.util.ConfigurationTest'`
  Expected: compiles once Step 3 is in, passes once Step 2 is in.

- [ ] **Step 6: Regenerate jOOQ.** Read the header of
  `scripts/regenerate-jooq.sh` first, then run it. It builds a throwaway
  `teralizer_codegen` from `create-tables.sql`, runs `generateJooq`, and drops
  the DB.
  Run: `scripts/regenerate-jooq.sh && git diff --stat build/generated-src`
  Expected: `Project.java` and `ProjectRecord.java` gain `USE_TEST_REDUCTION` /
  `getUseTestReduction` / `setUseTestReduction`. If any unrelated generated
  table changes semantically, stop — the codegen DB did not match the DDL.

- [ ] **Step 7: Set the toggle at project creation.** In
  `TestGeneralizationRunner.run()`, after
  `projectRecord.setUseTestGeneralization(...)` (line 61), add:

```java
        projectRecord.setUseTestReduction(Configuration.getProjectUseTestReduction());
```

Note: `use_test_reduction` is `NOT NULL`, so this set is required for the insert
to succeed. This keeps the full run working end to end (all three phases on by
default) even before the planner lands.

- [ ] **Step 8: Build to confirm green.**
  Run: `./gradlew compileJava compileTestJava test --tests 'teralizer.util.ConfigurationTest'`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit.** Stage `create-tables.sql`, both `reference.conf`,
  `Configuration.java`, `ConfigurationTest.java`, `TestGeneralizationRunner.java`,
  and the two regenerated jOOQ files. Commit via the commit skill.
  Suggested subject: `feat(config): add use-test-reduction project toggle`
  Body must explain: reduction becomes an independent phase toggle, defaults on
  to preserve current end-to-end behavior, column is NOT NULL so the runner sets
  it at project creation.

---

### Task 2: Variant-aware cascade-drop

Today `ProcessingPipeline`'s failure drop ignores `variant`, so one variant's
failure over-drops sibling variants. Make the drop variant-aware. This is a
standalone correctness fix, valuable on its own and required before phase
draining relies on it.

**Files:**
- Modify: `src/main/java/teralizer/processing/ProcessingPipeline.java:126-139`
- Test: `src/test/java/teralizer/processing/ProcessingPipelineCascadeTest.java` (create)

- [ ] **Step 1: Write the failing test.** Create `ProcessingPipelineCascadeTest.java`.
  Extract the drop predicate into a testable static helper first (Step 2 defines
  it), then assert:
  - a failed task with `variant = "A"` drops a queued task with `variant = "A"`
    and the same project id;
  - it does NOT drop a queued task with `variant = "B"`, same project id;
  - a failed task with `variant = null` (shared) drops queued tasks of ANY
    variant with the same project id (shared stages are legitimate dependencies).

```java
    @Example
    void variant_scoped_failure_drops_only_same_variant() {
        assertThat(ProcessingPipeline.shouldDrop(failedA, queuedA)).isTrue();
        assertThat(ProcessingPipeline.shouldDrop(failedA, queuedB)).isFalse();
    }

    @Example
    void shared_failure_drops_all_variants() {
        assertThat(ProcessingPipeline.shouldDrop(failedShared, queuedA)).isTrue();
        assertThat(ProcessingPipeline.shouldDrop(failedShared, queuedB)).isTrue();
    }
```

Build minimal `Task` doubles or reuse an existing test double in the package
(check `TaskPriorityComparator` tests for an existing pattern before inventing
one).

- [ ] **Step 2: Run it to verify it fails.**
  Run: `./gradlew test --tests 'teralizer.processing.ProcessingPipelineCascadeTest'`
  Expected: FAIL (method `shouldDrop` does not exist).

- [ ] **Step 3: Extract and extend the predicate.** In `ProcessingPipeline`,
  replace the inline `removeIf` lambda body (lines 126-139) with a call to a new
  package-visible static method, and add the variant rule:

```java
    static boolean shouldDrop(Task failed, Task queued) {
        boolean sameProject = failed.getProjectId() == null || failed.getProjectId().equals(queued.getProjectId());
        boolean sameTest = failed.getTestId() == null || failed.getTestId().equals(queued.getTestId());
        boolean sameAssertion = failed.getAssertionId() == null || failed.getAssertionId().equals(queued.getAssertionId());
        boolean sameGeneralization = failed.getGeneralizationId() == null || failed.getGeneralizationId().equals(queued.getGeneralizationId());
        // A shared (variant-null) failure cascades to every variant, since per-variant work
        // depends on shared stages. A variant-scoped failure drops only that same variant.
        boolean sameVariant = failed.getVariant() == null || failed.getVariant().equals(queued.getVariant());
        return sameProject && sameTest && sameAssertion && sameGeneralization && sameVariant;
    }
```

Then the `removeIf` becomes:

```java
            this.queuedTasks.removeIf(queuedTask -> {
                if (ProcessingPipeline.shouldDrop(currentTask, queuedTask)) {
                    LOGGER.atDebug().log("Task {} dropped from queue.", queuedTask);
                    return true;
                }
                return false;
            });
```

- [ ] **Step 4: Run the test to verify it passes.**
  Run: `./gradlew test --tests 'teralizer.processing.ProcessingPipelineCascadeTest'`
  Expected: PASS.

- [ ] **Step 5: Commit.** Stage `ProcessingPipeline.java` and the new test.
  Suggested subject: `fix(pipeline): scope cascade-drop to the failing variant`
  Body must explain: a variant-scoped failure previously dropped sibling
  variants because the drop predicate ignored variant. Shared (variant-null)
  failures still cascade to every variant since per-variant work depends on
  shared stages. This matters for the multi-variant PVC sweep.

---

### Task 3: Per-stage JaCoCo `.exec` preservation

`BUILD_PROJECT_GENERALIZED` runs `mvn clean`, deleting `target/jacoco.exec`
before a deferred reduction report could read it. Preserve each stage's `.exec`
at test-execution time and have the report read the preserved copy. Behavior is
unchanged today (report still finds the right data), but this is the mechanism
deferral requires.

**Files:**
- Modify: `src/main/java/teralizer/processing/task/TestExecutionTask.java`
- Modify: `src/main/java/teralizer/processing/task/JacocoDataCollectionTask.java`

- [ ] **Step 1: Add a preserved-exec path helper.** Decide one canonical
  location. Use the existing data directory convention seen in
  `JacocoDataCollectionTask.collectCoverageData`
  (`data/.../project-id-<id>/jacoco-data`). Add a static helper (place it in
  `JacocoDataCollectionTask` so both tasks share it):

```java
    static Path preservedExecPath(ProjectRecord projectRecord, long projectId, ProcessingStage stage, String variant) {
        String variantPart = variant == null ? "" : ("." + variant);
        return projectRecord.getDataPath()
            .resolve("project-id-" + projectId)
            .resolve("jacoco-data")
            .resolve(stage.name() + variantPart + ".exec");
    }
```

- [ ] **Step 2: Copy `target/jacoco.exec` after each test execution.** In
  `TestExecutionTask.executeInternal`, after the test command runs (after the
  try/catch that ends near line 144, before the generalized-only block), copy
  the produced exec to the preserved path. The default JaCoCo dest is
  `<root>/target/jacoco.exec` for Maven and `<root>/build/jacoco/test.exec` for
  Gradle. Resolve by project type:

```java
        Path producedExec = this.projectRecord.getType() == ProjectType.GRADLE
            ? this.projectRecord.getRootPath().resolve("build/jacoco/test.exec")
            : this.projectRecord.getRootPath().resolve("target/jacoco.exec");
        if (Files.exists(producedExec)) {
            Path preserved = JacocoDataCollectionTask.preservedExecPath(
                this.projectRecord, this.getProjectId(),
                JacocoDataCollectionTask.jacocoStageFor(this.stage), this.getVariant());
            Files.createDirectories(preserved.getParent());
            Files.copy(producedExec, preserved, StandardCopyOption.REPLACE_EXISTING);
        }
```

Add imports for `Files`, `Path`, `StandardCopyOption`, `ProjectType` if missing.

The executor stage names differ from the collector stage names that read the
exec (`EXECUTE_TESTS_INITIAL` produces what `COLLECT_JACOCO_DATA_INITIAL`
consumes). Both ends MUST key the preserved filename on the same canonical
collector stage, or the writer and reader compute different paths and never
match. Define a small `static ProcessingStage jacocoStageFor(ProcessingStage stage)`
in `JacocoDataCollectionTask` that maps an executor stage to its collector stage
(`EXECUTE_TESTS_ORIGINAL`→`COLLECT_JACOCO_DATA_ORIGINAL`, INITIAL→INITIAL,
GENERALIZED→GENERALIZED) and returns a collector stage unchanged (idempotent).
The writer (Step 2) passes `jacocoStageFor(this.stage)`; the reader (Step 3)
passes `this.stage`, which is already a collector stage. `preservedExecPath`
itself does no mapping, so both ends resolve the identical path.

- [ ] **Step 3: Report from the preserved exec.** In
  `JacocoDataCollectionTask.buildMavenCommand` / `buildGradleCommand`, point the
  report at the preserved file. Maven `jacoco:report` accepts
  `-Djacoco.dataFile=<path>`; Gradle's `jacocoTestReport` reads
  `executionData`. Add the preserved path:

```java
    private List<String> buildMavenCommand() {
        Path preserved = preservedExecPath(this.projectRecord, this.getProjectId(), this.stage, this.getVariant());
        return new ArrayList<>(Arrays.asList("mvn", "--file", Configuration.MAVEN_CUSTOM_BUILD_FILE,
            "-Djacoco.skip=false", "-Djacoco.dataFile=" + preserved.toAbsolutePath(), "jacoco:report"));
    }
```

For Gradle, set `-Djacoco.destFile` is not honored by the report task; instead
pass the preserved file through a project property the jacoco config reads, or
add `executionData` wiring in `jacoco-config-gradle.txt`. Read
`jacoco-config-gradle.txt` and extend `jacocoTestReport { executionData(...) }`
to accept a `-PjacocoExec=<path>` property, defaulting to the existing behavior
when unset. Keep the default path working so nothing regresses.

- [ ] **Step 4: Verify on the fixture batch gate.** This is a behavior-preserving
  change, so goldens must not move.
  Run: `bash scripts/verify-pipeline.sh`
  Expected: `Verification golden check passed`, all fixtures attempted, zero
  gradle-nonzero.

- [ ] **Step 5: Commit.** Stage the two task files and any jacoco config template
  touched. Suggested subject: `feat(pipeline): preserve per-stage jacoco exec`
  Body must explain: the generalized build's `mvn clean` deletes the shared
  `target/jacoco.exec`, so deferring coverage reporting to the reduction phase
  requires copying each stage's exec to the data directory at execution time and
  reporting from the preserved copy. Behavior is unchanged today, verified by the
  fixture goldens.

---

### Task 4: Success-view split

Split `v_projects_successes` into two milestones without disturbing the roughly
eighteen downstream consumers. Analysis-only and jOOQ-independent.

**Files:**
- Modify: `src/main/resources/db/create-views.sql`
- Test: a psql assertion (manual, in-step) plus analysis smoke

- [ ] **Step 1: Add the two views and keep the alias.** In `create-views.sql`,
  the current definition (lines 207-222) ends with the reduction-requiring
  predicate. Replace the single `v_projects_successes` with:

```sql
DROP VIEW IF EXISTS v_projects_successes;
DROP VIEW IF EXISTS v_projects_reduced;
DROP VIEW IF EXISTS v_projects_generalized;

CREATE VIEW v_projects_generalized AS
SELECT
    project_name(t.project_id),
    t.project_id
FROM task t
GROUP BY t.project_id
HAVING
    BOOL_AND(t.status = 'SUCCEEDED')
    AND SUM(CASE WHEN t.stage = 'FILTER_GENERALIZATIONS' THEN 1 ELSE 0 END) > 0;

CREATE VIEW v_projects_reduced AS
SELECT
    project_name(t.project_id),
    t.project_id
FROM task t
GROUP BY t.project_id
HAVING
    BOOL_AND(t.status = 'SUCCEEDED')
    AND SUM(CASE WHEN t.stage = 'COLLECT_PIT_DATA_GENERALIZED' THEN 1 ELSE 0 END) > 0;

CREATE VIEW v_projects_successes AS
SELECT * FROM v_projects_reduced;
```

Update the drop block at the top of the file (lines 38-40) to drop the two new
views before their dependents, matching existing reverse-dependency order.

- [ ] **Step 2: Verify the views build on a scratch DB.**
  Run:
```bash
docker exec -i postgres-teralizer psql -U postgres -c "DROP DATABASE IF EXISTS views_scratch;"
docker exec -i postgres-teralizer psql -U postgres -c "CREATE DATABASE views_scratch;"
docker exec -i postgres-teralizer psql -U postgres -d views_scratch < src/main/resources/db/create-tables.sql
docker exec -i postgres-teralizer psql -U postgres -d views_scratch < src/main/resources/db/create-views.sql
docker exec -i postgres-teralizer psql -U postgres -c "DROP DATABASE views_scratch;"
```
  Expected: no ERROR; all three views created.

- [ ] **Step 3: Confirm consumers are unaffected.** Grep confirms all analysis
  usages read `v_projects_successes`, which is now an alias of
  `v_projects_reduced` (identical predicate to before). No analysis edit is
  required for parity. If an applicability reader wants the Stage-4 milestone,
  it reads `v_projects_generalized` — note this in the spec's acceptance, but add
  no speculative consumer now (YAGNI).

- [ ] **Step 4: Commit.** Stage `create-views.sql`.
  Suggested subject: `feat(db): split success views into generalized and reduced`
  Body must explain: `v_projects_successes` hard-required a
  `COLLECT_PIT_DATA_GENERALIZED` row, which breaks once reduction is optional.
  Split into `v_projects_generalized` (passed Stage 4) and `v_projects_reduced`
  (has Stage-5 metrics); `v_projects_successes` stays an alias of the reduced
  view so existing analyses are unchanged.

---

## Phase 2 — Phase model (new types, unit-tested, not yet wired)

### Task 5: `PipelinePhase`

The three phases as first-class objects. Each owns its stage list, a precondition
check, a `clear()` that tears down its own prior outputs, and a success
predicate. Nothing calls it yet, so the build stays green.

**Files:**
- Create: `src/main/java/teralizer/processing/PipelinePhase.java`
- Test: `src/test/java/teralizer/processing/PipelinePhaseTest.java`

- [ ] **Step 1: Write the failing test.** Assert phase membership and precondition
  behavior against a small in-memory or fixture project. Minimum assertions:
  - `PipelinePhase.GENERATION.stages()` contains `GENERATE_EVOSUITE_TESTS`,
    `POSTPROCESS_EVOSUITE_TESTS` and nothing else.
  - `PipelinePhase.REDUCTION.stages()` equals the Stage-5 set
    (`COLLECT_PIT_DATA_ORIGINAL`, `COLLECT_JACOCO_DATA_INITIAL`,
    `COLLECT_PIT_DATA_INITIAL`, `COLLECT_JACOCO_DATA_GENERALIZED`,
    `COLLECT_PIT_DATA_GENERALIZED`).
  - `REDUCTION.checkPreconditions(project)` throws a `PhasePreconditionException`
    naming the missing artifact when no generalized test sources exist on disk.

```java
    @Example
    void reduction_precondition_fails_loud_without_generalized_tests() {
        PhasePreconditionException ex = catchThrowableOfType(
            () -> PipelinePhase.REDUCTION.checkPreconditions(projectWithoutGeneralizedTests),
            PhasePreconditionException.class);
        assertThat(ex).hasMessageContaining("generalized test");
    }
```

- [ ] **Step 2: Run it to verify it fails.**
  Run: `./gradlew test --tests 'teralizer.processing.PipelinePhaseTest'`
  Expected: FAIL (type does not exist).

- [ ] **Step 3: Implement `PipelinePhase`.** Define the enum with the three
  constants and the contract. Sketch:

```java
public enum PipelinePhase {
    GENERATION {
        @Override public Set<ProcessingStage> stages() {
            return EnumSet.of(ProcessingStage.GENERATE_EVOSUITE_TESTS, ProcessingStage.POSTPROCESS_EVOSUITE_TESTS);
        }
        @Override public boolean isRequested(ProjectRecord p) { return p.getUseTestGeneration(); }
        @Override public void checkPreconditions(ProjectRecord p) { /* project on disk, builds */ }
        @Override public void schedule(ProjectRecord p, Consumer<Task> schedule) { /* generation entry tasks */ }
        @Override public void clear(DSLContext create, ProjectRecord p) { /* remove evosuite rows + ESTest sources + phase task rows */ }
    },
    GENERALIZATION { /* Stages 1-4: SPOON_MODEL .. FILTER_GENERALIZATIONS incl. INITIAL build+execute */ },
    REDUCTION {
        @Override public Set<ProcessingStage> stages() {
            return EnumSet.of(
                ProcessingStage.COLLECT_PIT_DATA_ORIGINAL,
                ProcessingStage.COLLECT_JACOCO_DATA_INITIAL, ProcessingStage.COLLECT_PIT_DATA_INITIAL,
                ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED, ProcessingStage.COLLECT_PIT_DATA_GENERALIZED);
        }
        @Override public boolean isRequested(ProjectRecord p) { return p.getUseTestReduction(); }
        @Override public void checkPreconditions(ProjectRecord p) {
            // generalized test sources present on disk AND initial build artifacts present, else throw
        }
        @Override public void schedule(ProjectRecord p, Consumer<Task> schedule) {
            // recompile generalized tests against pom.teralizer.xml, then the Stage-5 collectors per variant
        }
        @Override public void clear(DSLContext create, ProjectRecord p) {
            // delete pit_mutation_report, pit_coverage_report, jacoco_coverage_report for ORIGINAL/INITIAL/GENERALIZED
            // and this phase's task rows; leave preserved .exec untouched
        }
    };

    public abstract Set<ProcessingStage> stages();
    public abstract boolean isRequested(ProjectRecord project);
    public abstract void checkPreconditions(ProjectRecord project);
    public abstract void schedule(ProjectRecord project, Consumer<Task> schedule);
    public abstract void clear(DSLContext create, ProjectRecord project);
}
```

Add `PhasePreconditionException extends RuntimeException` (same package). The
`schedule` bodies here are placeholders until Task 8 moves the real scheduling
out of `ProjectSetupTask`. For this task, `schedule` may be a `TODO`-free stub
that throws `UnsupportedOperationException("wired in Task 8")` — but note that is
a temporary internal seam, NOT a delivered stub: Task 8 replaces it in the same
plan, and no production path calls `schedule` until Task 11. Preconditions,
`stages()`, `isRequested`, and `clear()` are fully implemented now and unit
tested.

- [ ] **Step 4: Run the test to verify it passes.**
  Run: `./gradlew test --tests 'teralizer.processing.PipelinePhaseTest'`
  Expected: PASS.

- [ ] **Step 5: Commit.** Stage `PipelinePhase.java`, `PhasePreconditionException.java`,
  and the test. Suggested subject: `feat(pipeline): add first-class pipeline phases`
  Body must explain: the three phases become objects owning their stages,
  preconditions, teardown, and success predicate. Preconditions fail loud with
  the missing artifact named. Scheduling is wired in a later task; this commit
  lands the model, its membership, and its teardown under unit test.

---

### Task 6: `ProjectIdentity`

Resolve-or-create the `ProjectRecord` by `root_path`, with a config-hash guard so
a materially different config on the same path fails loud rather than silently
attaching.

**Files:**
- Create: `src/main/java/teralizer/processing/ProjectIdentity.java`
- Modify: `src/main/java/teralizer/util/Configuration.java` (identity-hash render)
- Test: `src/test/java/teralizer/processing/ProjectIdentityTest.java`

- [ ] **Step 1: Add an identity-config render to Configuration.** The hash must
  exclude the three run-scoped phase toggles. Add:

```java
    /** Rendered config for identity comparison: the teralizer subtree minus the run-scoped
     * phase toggles, so a resume with different phases requested still matches the same project. */
    public static String renderIdentity() {
        return CONFIG.getConfig(TOOL_NAME_LOWER)
            .withoutPath("project.use-test-generation")
            .withoutPath("project.use-test-generalization")
            .withoutPath("project.use-test-reduction")
            .root().render(ConfigRenderOptions.concise());
    }
```

- [ ] **Step 2: Write the failing test.** Assert:
  - resolving when no record exists at the path creates one;
  - resolving when a record exists with a matching identity hash returns that
    record (same id);
  - resolving when a record exists with a different identity hash throws.

```java
    @Example
    void mismatched_config_on_same_path_fails_loud() {
        ProjectIdentity.resolveOrCreate(create, rootPath, hashA); // seeds
        assertThatThrownBy(() -> ProjectIdentity.resolveOrCreate(create, rootPath, hashB))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining(rootPath.toString());
    }
```

Use an in-memory or test Postgres DSLContext consistent with existing repository
tests (check how other `teralizer.repository` or task tests obtain a DSLContext;
reuse that harness).

- [ ] **Step 3: Run it to verify it fails.**
  Run: `./gradlew test --tests 'teralizer.processing.ProjectIdentityTest'`
  Expected: FAIL.

- [ ] **Step 4: Implement `ProjectIdentity`.**

```java
public final class ProjectIdentity {
    public static ProjectRecord resolveOrCreate(DSLContext create, Path rootPath, String identityHash) {
        ProjectRecord existing = create.selectFrom(Tables.PROJECT)
            .where(Tables.PROJECT.ROOT_PATH.eq(rootPath))
            .orderBy(Tables.PROJECT.ID.desc())
            .limit(1)
            .fetchOne();
        if (existing == null) {
            return null; // caller creates + stores a fresh record (fresh-start path)
        }
        String storedHash = ConfigIdentity.hash(existing.getConfiguration());
        if (!storedHash.equals(identityHash)) {
            throw new RuntimeException("Refusing to attach to project at " + rootPath
                + ": stored configuration differs from the current run. Use a fresh workspace or reconcile the config.");
        }
        return existing;
    }
}
```

Add a `ConfigIdentity.hash(String rendered)` helper (stable hash, e.g. SHA-256
hex of the identity render). The stored `configuration` column holds the full
render; hash the same identity-excluded projection on both sides. Keep the
projection logic in one place so stored and current hashes are computed
identically.

- [ ] **Step 5: Run the test to verify it passes.**
  Run: `./gradlew test --tests 'teralizer.processing.ProjectIdentityTest'`
  Expected: PASS.

- [ ] **Step 6: Commit.** Stage `ProjectIdentity.java`, `ConfigIdentity.java`,
  `Configuration.java`, and the test. Suggested subject:
  `feat(pipeline): resolve project identity with a config-hash guard`
  Body must explain: a resume attaches to the existing project at a root path;
  the phase toggles are excluded from the identity hash since they are run-scoped,
  and a materially different config on the same path fails loud instead of
  silently attaching to a different project.

---

### Task 7: `PipelinePlanner`

Sequential per-phase orchestration: for each requested phase in canonical order,
clear then check preconditions then schedule then drain, then advance.

**Files:**
- Create: `src/main/java/teralizer/processing/PipelinePlanner.java`
- Test: `src/test/java/teralizer/processing/PipelinePlannerTest.java`

- [ ] **Step 1: Write the failing test.** With phase doubles or a controllable
  `PipelinePhase` seam, assert:
  - only requested phases run;
  - phases run in canonical order generation, generalization, reduction;
  - each requested phase is cleared before it is scheduled;
  - the queue is fully drained between phases (a phase's tasks all complete
    before the next phase schedules) — assert by recording call order;
  - a precondition failure aborts before scheduling that phase's stages.

- [ ] **Step 2: Run it to verify it fails.**
  Run: `./gradlew test --tests 'teralizer.processing.PipelinePlannerTest'`
  Expected: FAIL.

- [ ] **Step 3: Implement `PipelinePlanner`.**

```java
public class PipelinePlanner {
    private final ProcessingPipeline pipeline;
    private final DSLContext create;

    public void run(ProjectRecord project) {
        for (PipelinePhase phase : PipelinePhase.values()) { // declared in canonical order
            if (!phase.isRequested(project)) {
                continue;
            }
            phase.clear(create, project);
            phase.checkPreconditions(project); // throws PhasePreconditionException, fail loud
            phase.schedule(project, pipeline::addTask);
            while (pipeline.hasNext()) {
                pipeline.executeNext();
            }
        }
    }
}
```

`PipelinePhase.values()` is declared in canonical order (generation,
generalization, reduction), so iteration order is the phase order. The
drain-between-phases loop is what isolates a reduction failure from
generalization results.

- [ ] **Step 4: Run the test to verify it passes.**
  Run: `./gradlew test --tests 'teralizer.processing.PipelinePlannerTest'`
  Expected: PASS.

- [ ] **Step 5: Commit.** Stage `PipelinePlanner.java` and the test.
  Suggested subject: `feat(pipeline): add sequential phase planner`
  Body must explain: the planner runs each requested phase to completion before
  the next begins, draining the queue between phases. That draining is what makes
  a reduction-phase failure unable to drop generalization work, since reduction
  tasks are not queued until generalization has fully drained.

---

## Phase 3 — Cutover (behavior change, atomic commits, fixture-gated)

### Task 8: Move scheduling into phases, with per-variant source archive and isolated restore

Moves the per-phase stage scheduling out of `ProjectSetupTask` into the
`PipelinePhase.schedule` bodies, and adds the per-variant generalized-source
archive (writer) plus a reduction-owned `RESTORE_GENERALIZED_BUILD` stage
(reader) so reduction rebuilds each variant in isolation. Behavior-preserving for
a full single-variant run; the archive addition makes multi-variant reduction
correct. Landed as three ordered commits so the build stays green at each.

**Why the archive:** the per-variant `CLEANUP_GENERALIZATION` deletes every
`_*_Generalized_*` source at the start of each variant, so only one variant's
sources ever sit in the workspace, and `mvn pitest:mutationCoverage` mutates
whatever is already compiled in `target/`. Deferring all generalized PIT to a
later phase would run every variant against the last variant's classes. The fix
archives each variant's sources during generalization and restores + rebuilds
per variant during reduction, preserving one-variant-at-a-time isolation.

**Files:**
- Modify: `src/main/java/teralizer/processing/ProcessingStage.java` (add `RESTORE_GENERALIZED_BUILD` in the reduction band; Task 9 does the coherent renumber)
- Create: `src/main/java/teralizer/processing/task/GeneralizedSourceRestoreTask.java`
- Modify: `src/main/java/teralizer/processing/task/TestExecutionTask.java` (archive writer at `EXECUTE_TESTS_GENERALIZED`)
- Modify: `src/main/java/teralizer/processing/PipelinePhase.java` (fill `schedule` bodies; adjust `REDUCTION.checkPreconditions` to check the archive)
- Modify: `src/main/java/teralizer/processing/task/ProjectSetupTask.java` (remove the per-phase scheduling block; add the temporary driver)
- Test: `src/test/java/teralizer/processing/task/GeneralizedSourceRestoreTaskTest.java`

- [ ] **Commit A — archive writer + restore stage + restore task (infra, nothing scheduled yet).**
  - Add a `RESTORE_GENERALIZED_BUILD` constant to `ProcessingStage` (temporary
    step number in the reduction band, e.g. just below `COLLECT_JACOCO_DATA_GENERALIZED`;
    Task 9 renumbers the whole band coherently). No jOOQ regen: `task.stage` uses a
    runtime `EnumConverter` keyed on the class, so a new constant is picked up
    automatically (verified).
  - In `TestExecutionTask`, at `EXECUTE_TESTS_GENERALIZED` (after the existing
    `.exec` copy from Task 3), copy this variant's `_*_Generalized_*` sources
    from the test source tree to `data/project-id-N/generalized-sources/<variant>/`,
    mirroring the tree structure so restore is a straight copy back. Reuse the
    `_*_Generalized_*` predicate convention (`startsWith("_") && contains("_Generalized_")`).
  - Create `GeneralizedSourceRestoreTask` (stage `RESTORE_GENERALIZED_BUILD`,
    variant-scoped): delete any `_*_Generalized_*` from the workspace test tree,
    restore this variant's archived sources into it, then rebuild via the same
    `ProjectBuildTask` build helper used for `BUILD_PROJECT_GENERALIZED` (custom
    `pom.teralizer.xml`). It is NOT scheduled anywhere in this commit.
  - Unit test the restore task's path derivation (archive dir per variant, source
    round-trips) with a temp-dir fixture. Do not run the full gate.
  - Compile green. Commit. Suggested subject:
    `feat(pipeline): archive generalized sources per variant for isolated restore`
    Body: explains the interleave-isolation problem and the archive+restore fix.

- [ ] **Commit B — move scheduling into the phase bodies.**
  - `GENERATION.schedule`: the `if (getUseTestGeneration())` block (old
    `ProjectSetupTask` lines 79-82), same task order.
  - `GENERALIZATION.schedule`: SPOON_MODEL through `FILTER_GENERALIZATIONS`,
    including the INITIAL build+execute and the per-variant interleaved
    `CLEANUP_GENERALIZATION → GENERALIZE → BUILD → EXECUTE → JUNIT → FILTER`
    exactly as ordered today (the archive copy already happens inside EXECUTE).
  - `REDUCTION.schedule`: `COLLECT_PIT_DATA_ORIGINAL`, `COLLECT_JACOCO_DATA_INITIAL`,
    `COLLECT_PIT_DATA_INITIAL` (variant-null, against the original build), then
    per variant `RESTORE_GENERALIZED_BUILD → COLLECT_JACOCO_DATA_GENERALIZED →
    COLLECT_PIT_DATA_GENERALIZED`.
  - Adjust `REDUCTION.checkPreconditions` to require the per-variant
    generalized-source ARCHIVE under the data dir (not the live workspace, which
    holds only the last variant after generalization). Update the committed
    `PipelinePhaseTest` precondition case accordingly.
  - Temporary driver: until Task 11 flips the runner, `ProjectSetupTask` calls the
    three phases' `schedule` in order when their toggles are set, reproducing the
    single-pass behavior. Remove the old inline scheduling block.
  - Spike ONE small fixture end to end (not the 21-fixture gate): confirm it
    reaches reduction, produces generalizations and PIT rows, goldens for that
    fixture unmoved. Commit. Suggested subject:
    `refactor(pipeline): move stage scheduling into the phases`
    Body: scheduling moves into the phase objects; reduction restores and rebuilds
    each variant before its collectors; single-variant behavior preserved.

---

### Task 9: Renumber reduction stages to sort last

The comparator orders queued tasks by `stage.step`. Renumber the reduction stages
above the whole generalization loop so, within a single drain, measurement never
precedes generalization. Mirror the change in `stage_order()`.

**Files:**
- Modify: `src/main/java/teralizer/processing/ProcessingStage.java`
- Modify: `src/main/resources/db/create-views.sql` (the `stage_order()` function)
- Modify: `analysis/src/teralizer/stages.py` (add the new stage to the Stage-5 set + the SQL CASE)

- [ ] **Step 1: Renumber.** Give the reduction band contiguous step values ABOVE
  the whole generalization loop, in this within-phase order:
  `COLLECT_PIT_DATA_ORIGINAL`, `COLLECT_JACOCO_DATA_INITIAL`, `COLLECT_PIT_DATA_INITIAL`,
  then per variant `RESTORE_GENERALIZED_BUILD`, `COLLECT_JACOCO_DATA_GENERALIZED`,
  `COLLECT_PIT_DATA_GENERALIZED`. All must sort after `FILTER_GENERALIZATIONS` and
  the per-variant generalized build/execute stages. `RESTORE_GENERALIZED_BUILD`
  (added in Task 8 with a temporary number) gets its final number here, just
  before the generalized collectors it precedes. Preserve relative order within
  each phase.

- [ ] **Step 2: Mirror `stage_order()` and `stages.py`.** Update the SQL
  `stage_order()` CASE in `create-views.sql` (lines ~160-197) so every stage's
  number matches the enum, and add `RESTORE_GENERALIZED_BUILD` to the Stage-5 set
  and the `get_stage_group_sql_case()` Stage-5 branch in
  `analysis/src/teralizer/stages.py`. Enum, SQL, and stages.py must stay in
  lockstep; a mismatch corrupts failure-stage and paper-stage reporting.

- [ ] **Step 3: Verify the views build.** Re-run the scratch-DB view check from
  Task 4 Step 2.
  Expected: no ERROR.

- [ ] **Step 4: Fixture gate.**
  Run: `bash scripts/verify-pipeline.sh`
  Expected: goldens unmoved (reordering measurement after generalization does not
  change per-stage outputs, only their execution order within a full run).

- [ ] **Step 5: Commit.** Stage `ProcessingStage.java` and `create-views.sql`.
  Suggested subject: `refactor(pipeline): order reduction stages after generalization`
  Body must explain: reduction stages carried lower step numbers and, being
  variant-null, sorted ahead of per-variant generalization in the shared queue.
  Renumbering them above the generalization loop makes Stage 5 run after Stage 4
  within a full run, matching the paper order, and the `stage_order()` SQL mirror
  is updated in lockstep.

---

### Task 10: Gate destructive `CLEANUP_PROJECT` to fresh start

`CLEANUP_PROJECT` deletes tool-generated sources at every run start. Under
persist-on-disk resume that would wipe the generalized tests a reduction-only run
needs. Gate the destructive deletion to fresh-start only.

**Files:**
- Modify: `src/main/java/teralizer/processing/task/CleanupTask.java`
- Test: extend an existing CleanupTask test or add one

- [ ] **Step 1: Write the failing test.** Assert that a `CLEANUP_PROJECT` cleanup
  invoked in attach mode does not delete `_*_Generalized_*` sources, while a
  fresh-start cleanup still does. Model the fresh-vs-attach signal explicitly
  (Step 2).

- [ ] **Step 2: Add the fresh-start signal.** The planner knows whether the
  project was just created (`ProjectIdentity` returned null then a fresh record
  was stored) or attached. Thread a boolean `freshStart` to the cleanup decision.
  Simplest: only schedule the destructive `CLEANUP_PROJECT` on fresh start;
  on attach, skip it. Prefer skipping the schedule over branching deep in the
  visitor, so the task stays single-purpose. Implement by having the planner
  schedule the cleanup only when `freshStart`.

- [ ] **Step 3: Run tests.**
  Run: `./gradlew test --tests 'teralizer.processing.task.CleanupTaskTest'`
  Expected: PASS (adjust the test class name to the actual one; create if none).

- [ ] **Step 4: Commit.** Stage `CleanupTask.java` (and/or planner) and the test.
  Suggested subject: `fix(pipeline): only wipe generated sources on a fresh start`
  Body must explain: destructive `CLEANUP_PROJECT` deletion ran at every start,
  which would erase the generalized sources a reduction-only resume mutates. It
  is now gated to fresh-start; an attach preserves the workspace. DB and data-dir
  preservation are unchanged.

---

### Task 11: Wire the planner into the runner (the cutover)

Flip `TestGeneralizationRunner` to drive `PipelinePlanner`, remove the temporary
driver from `ProjectSetupTask`, and route project creation through
`ProjectIdentity`.

**Files:**
- Modify: `src/main/java/teralizer/TestGeneralizationRunner.java`
- Modify: `src/main/java/teralizer/processing/task/ProjectSetupTask.java`

- [ ] **Step 1: Route identity.** In `TestGeneralizationRunner.run()`, before
  scheduling download, resolve identity:

```java
        String identityHash = ConfigIdentity.hash(Configuration.renderIdentity());
        ProjectRecord projectRecord = ProjectIdentity.resolveOrCreate(create, Configuration.getProjectRootPath(), identityHash);
        boolean freshStart = projectRecord == null;
        if (freshStart) {
            projectRecord = create.newRecord(Tables.PROJECT);
            // ... existing field sets (type, paths, toggles incl. setUseTestReduction, configuration) ...
            projectRecord.store();
        }
```

- [ ] **Step 2: Run bootstrap then the planner.** Keep the download and setup
  cascade for the bootstrap stages (download, setup, add-deps, build-original),
  then hand off to the planner for the phases. Bootstrap tasks are idempotent
  (download skips when present, add-deps checks for existing plugins, build runs
  clean). Drive them, then:

```java
        PipelinePlanner planner = new PipelinePlanner(pipeline, create);
        planner.run(projectRecord);
```

The cleanest structure: bootstrap is scheduled and drained first (or folded into
the planner as a pre-phase), then `planner.run` iterates the three phases. Choose
one and keep it explicit; document it in the runner.

- [ ] **Step 3: Remove the temporary driver.** Delete the Task-8 temporary
  phase-driving from `ProjectSetupTask`. Setup now schedules only the bootstrap
  continuation it owns (cleanup on fresh start, add-deps, build-original), never
  the phase stages.

- [ ] **Step 4: Full fixture gate.**
  Run: `bash scripts/verify-pipeline.sh`
  Expected: goldens unmoved. A full run (all three toggles on) reproduces today's
  end-to-end behavior through the planner.

- [ ] **Step 5: Commit.** Stage both files. Suggested subject:
  `feat(pipeline): drive phases through the sequential planner`
  Body must explain: the runner resolves project identity, runs bootstrap, then
  the planner executes the requested phases in order. The temporary driver in the
  setup task is removed. A full run is behavior-identical, verified by fixture
  goldens; reduction failures can no longer drop generalization because phases
  drain sequentially.

---

## Phase 4 — Verification

### Task 12: Reduction-resume fixture

Prove the headline capability: run generalization in one invocation, then
reduction in a separate invocation against the persisted workspace.

**Files:**
- Create: a fixture under `verification/fixtures/` (follow an existing fixture's
  layout, e.g. `verification/fixtures/symbolic-int`)
- Modify: fixture config under `project-configs/verification/` if the corpus
  driver needs an entry
- Test: the fixture's golden entry

- [ ] **Step 1: Pick the smallest viable fixture.** Reuse an existing small MUT
  fixture shape. The fixture must reach `FILTER_GENERALIZATIONS` with at least one
  included generalization so reduction has something to mutate.

- [ ] **Step 2: Add a two-invocation check.** Extend the corpus driver (or add a
  dedicated script step) to run the fixture twice: first with
  `use-test-reduction = false` (generalization only), asserting generalized
  sources on disk and a `v_projects_generalized` row but no PIT rows; then with
  only `use-test-reduction = true`, asserting PIT/JaCoCo rows appear without
  re-running generalization (no new `GENERALIZE_TESTS` task rows).

Read `scripts/verify-pipeline.sh` and the corpus driver to match the existing
golden mechanism before adding this. Keep the assertion data-driven off the DB,
consistent with existing golden checks.
- [ ] **Step 2b: Add a multi-variant reduction isolation check.** Single-variant
  fixtures cannot catch a sweep regression. Add a check (a fixture config with two
  variants, e.g. `IMPROVED_100_TRIES` + `IMPROVED_200_TRIES`, run through
  generalization then reduction) asserting each variant produces its OWN PIT and
  JaCoCo rows keyed to its own generalized classes, and that the per-variant
  source archives exist under `data/.../generalized-sources/<variant>/`. The
  assertion that proves isolation: each variant's `pit_mutation_report` /
  `jacoco_coverage_report` rows reference that variant's generalization classes,
  never a sibling's. Keep it small (a fixture with one generalizable MUT per
  variant suffices).

- [ ] **Step 3: Run the fixture.**
  Run: `bash scripts/verify-pipeline.sh` (or the fixture-scoped invocation the
  driver exposes)
  Expected: the new fixture passes both invocations; all existing goldens
  unmoved.

- [ ] **Step 4: Commit.** Stage the fixture, config, and golden.
  Suggested subject: `test(pipeline): add reduction-resume fixture`
  Body must explain: the fixture proves generalization then reduction run in
  separate invocations against the persisted workspace, that reduction-only adds
  metrics without re-running generalization, and that the Stage-4 and Stage-5
  milestones are separately observable.

---

### Task 13: Final review and plan completion

- [ ] **Step 1: Full targeted test sweep.** Run the unit tests this plan added
  plus the fixture gate:
  `./gradlew test --tests 'teralizer.processing.*' --tests 'teralizer.util.ConfigurationTest'`
  then `bash scripts/verify-pipeline.sh`.
  Expected: all green, goldens unmoved.

- [ ] **Step 2: Dispatch a final code reviewer** over the whole change set
  (all commits from Task 1 onward) via `skill://requesting-code-review`.

- [ ] **Step 3: Address any review findings** with a focused follow-up commit
  each (no big bang).

- [ ] **Step 4: Complete the plan.** Run `omp-plans complete
  2026-07-06-phase-decoupled-pipeline-implementation`, and set the spec
  `2026-07-06-phase-decoupled-pipeline` to `implemented` (or complete it too if
  fully realized). Regenerate INDEX and run `omp-plans check`.

---

## Notes for the coordinator

- Dispatch order is Task 1 → 13, strictly sequential. Phase 1 tasks are
  independent and each green on its own. Phase 2 adds unit-tested types nothing
  calls yet. Phase 3 is the behavioral cutover, fixture-gated at every commit.
  Phase 4 proves and closes.
- The one internal seam is `PipelinePhase.schedule` stubbed in Task 5 and filled
  in Task 8. It is never on a production path before Task 11 wires the planner.
  This is a within-plan seam, not a delivered stub.
- Out of scope, do not start: the PIT-at-scale timeout/cap decision and the
  quarantine/exclusion telemetry follow-up. Both are tracked separately.
