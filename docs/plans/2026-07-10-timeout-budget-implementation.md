---
title: Timeout Budget Policy Implementation
type: plan
status: draft
created: 2026-07-10
parent: 2026-07-10-timeout-budget-policy
---

# Timeout Budget Policy Implementation

**Goal:** Replace the three ad-hoc pipeline timeout shapes with one fixed-budget mechanism
(`TimeoutBudget.forStage`), a uniform `timeout` config schema, and data-driven defaults, so no profile
config carries a timeout override. Implements `docs/plans/2026-07-10-timeout-budget-policy.md`.

**Architecture:** A pure resolver `TimeoutBudget.forStage(ProcessingStage)` maps each ConsoleCommand
stage to a fixed budget read from `Configuration`; `TestExecutionTask` and `PitDataCollectionTask`
consume it. SPF keeps its per-assertion budget, rekeyed. `reference.conf` gains a `timeout` block per
tool and drops the old flat/scaled keys. Profile configs drop their (now-redundant) timeout overrides.

**Tech stack:** Java 8, Gradle, Typesafe Config (HOCON), jqwik `@Example` + `org.junit.Assert`.

**Execution note:** the config-schema rename does not compile in intermediate states (removing a
`Configuration` reader breaks its consumer), so Task 2 is one coordinated commit. Per the repo rule,
the Tester writes the failing tests in Task 1 before Task 2 implements.

---

## Values (from the spec)

| Config key | Value |
|---|---|
| `teralizer.jpf.timeout.per-assertion` | 10 |
| `teralizer.junit.timeout.original-initial` | 60 |
| `teralizer.junit.timeout.generalized` | 1800 |
| `teralizer.pitest.timeout.original-initial` | 1800 |
| `teralizer.pitest.timeout.generalized` | 1800 |

`TimeoutBudget.forStage` mapping: `EXECUTE_TESTS_ORIGINAL`/`EXECUTE_TESTS_INITIAL` ->
`junit.timeout.original-initial`; `EXECUTE_TESTS_GENERALIZED` -> `junit.timeout.generalized`;
`COLLECT_PIT_DATA_ORIGINAL`/`COLLECT_PIT_DATA_INITIAL` -> `pitest.timeout.original-initial`;
`COLLECT_PIT_DATA_GENERALIZED` -> `pitest.timeout.generalized`; anything else -> `IllegalArgumentException`.
JPF stays a direct `Configuration.getJpfTimeoutPerAssertion()` read (enforced in the listener, not a
ConsoleCommand), so it is not routed through `TimeoutBudget`.

---

## Task 1: Failing tests (Tester)

**Files:**
- Create: `src/test/java/teralizer/processing/task/TimeoutBudgetTest.java`
- Modify: `src/test/java/teralizer/processing/task/TestExecutionTaskTest.java`

- [ ] **Step 1: Write `TimeoutBudgetTest`** asserting the stage mapping against the reference defaults
  and the unmapped-stage error.

```java
package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;

public class TimeoutBudgetTest {

    @Example
    void executionStagesUseJunitBudgets() {
        Assert.assertEquals(60, TimeoutBudget.forStage(ProcessingStage.EXECUTE_TESTS_ORIGINAL));
        Assert.assertEquals(60, TimeoutBudget.forStage(ProcessingStage.EXECUTE_TESTS_INITIAL));
        Assert.assertEquals(1800, TimeoutBudget.forStage(ProcessingStage.EXECUTE_TESTS_GENERALIZED));
    }

    @Example
    void pitStagesUsePitestBudgets() {
        Assert.assertEquals(1800, TimeoutBudget.forStage(ProcessingStage.COLLECT_PIT_DATA_ORIGINAL));
        Assert.assertEquals(1800, TimeoutBudget.forStage(ProcessingStage.COLLECT_PIT_DATA_INITIAL));
        Assert.assertEquals(1800, TimeoutBudget.forStage(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED));
    }

    @Example
    void nonTimedStageHasNoBudget() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> TimeoutBudget.forStage(ProcessingStage.BUILD_PROJECT_ORIGINAL));
    }
}
```

- [ ] **Step 2: Delete the scaled-timeout tests** in `TestExecutionTaskTest.java` (the
  `scaledGeneralizedTimeoutSeconds` method it exercises is removed in Task 2). If that leaves the class
  with no `@Example`, delete the file. Do NOT invent replacement assertions.

- [ ] **Step 3: Run and confirm they fail** — `TimeoutBudget` does not yet exist.

Run: `./gradlew test --tests '*TimeoutBudgetTest*' --no-daemon`
Expected: compile failure / FAIL (symbol `TimeoutBudget` not found).

- [ ] **Step 4: Commit.**

```bash
git add src/test/java/teralizer/processing/task/TimeoutBudgetTest.java \
        src/test/java/teralizer/processing/task/TestExecutionTaskTest.java
# (helper commit) test(pipeline): pin timeout-budget stage mapping
```

---

## Task 2: Mechanism, config schema, and wiring (one coordinated commit)

**Files:**
- Modify: `src/main/resources/reference.conf:17-37`
- Modify: `src/main/java/teralizer/util/Configuration.java:293-332`
- Create: `src/main/java/teralizer/processing/task/TimeoutBudget.java`
- Modify: `src/main/java/teralizer/processing/task/TestExecutionTask.java` (ctor `:45`, GENERALIZED override `:77-82`, scaled methods `:197-216`)
- Modify: `src/main/java/teralizer/processing/task/PitDataCollectionTask.java:81`
- Modify: `src/main/java/teralizer/processing/task/JpfInstrumentationTask.java:284`

- [ ] **Step 1: Rewrite the `reference.conf` timeout blocks.** Replace lines 17-37 with:

```hocon
  jpf {
    timeout { per-assertion = 10 }
    max-path-condition-size = 100000
    max-search-depth = 100
  }

  junit {
    timeout {
      original-initial = 60
      generalized = 1800
    }
  }

  pitest {
    mutators = "DEFAULTS"
    timeout {
      original-initial = 1800
      generalized = 1800
    }
    # ORIGINAL-stage PIT mutates the full-suite coverage scope and has no analysis consumer.
    original.enabled = false
  }
```

- [ ] **Step 2: Replace the `Configuration` readers.** Swap `getJpfMaxExecutionTime` (293-295),
  `getJunitMaxExecutionTime`/`getJunitBaselineTriesBudget`/`getJunitMaxGeneralizedExecutionTime`
  (311-323), and `getPitestMaxExecutionTime` (330-332) with:

```java
    public static double getJpfTimeoutPerAssertion() {
        return CONFIG.getDouble(TOOL_NAME_LOWER + ".jpf.timeout.per-assertion");
    }
```
```java
    public static int getJunitTimeoutOriginalInitial() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".junit.timeout.original-initial");
    }

    public static int getJunitTimeoutGeneralized() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".junit.timeout.generalized");
    }
```
```java
    public static int getPitestTimeoutOriginalInitial() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".pitest.timeout.original-initial");
    }

    public static int getPitestTimeoutGeneralized() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".pitest.timeout.generalized");
    }
```
  Keep `getJpfMaxPathConditionSize`, `getJpfMaxSearchDepth`, `getPitestMutators`, `isPitestEnabled`,
  `isPitestOriginalEnabled` unchanged.

- [ ] **Step 3: Create `TimeoutBudget`.**

```java
package teralizer.processing.task;

import teralizer.processing.ProcessingStage;
import teralizer.util.Configuration;

/**
 * Fixed per-stage timeout budget (seconds) for the ConsoleCommand-driven stages. Original and
 * initial work gets the reference budget; the generalized variant gets its own larger budget. The
 * value is a config read, not an arithmetic of run inputs (no generalization count, tries, or mutant
 * count), so a stage that blows its fixed budget is an honest exclusion. JPF keeps its own
 * per-assertion budget in TestGeneralizationListener and does not route through here.
 */
public final class TimeoutBudget {

    private TimeoutBudget() {
    }

    static int forStage(ProcessingStage stage) {
        switch (stage) {
            case EXECUTE_TESTS_ORIGINAL:
            case EXECUTE_TESTS_INITIAL:
                return Configuration.getJunitTimeoutOriginalInitial();
            case EXECUTE_TESTS_GENERALIZED:
                return Configuration.getJunitTimeoutGeneralized();
            case COLLECT_PIT_DATA_ORIGINAL:
            case COLLECT_PIT_DATA_INITIAL:
                return Configuration.getPitestTimeoutOriginalInitial();
            case COLLECT_PIT_DATA_GENERALIZED:
                return Configuration.getPitestTimeoutGeneralized();
            default:
                throw new IllegalArgumentException("No timeout budget for stage: " + stage);
        }
    }
}
```

- [ ] **Step 4: Wire `TestExecutionTask`.** In the constructor (`:45`), replace
  `Configuration.getJunitMaxExecutionTime()` with `TimeoutBudget.forStage(stage)`. Delete the
  `EXECUTE_TESTS_GENERALIZED` `setTimeout` override block (`:77-82`) — `forStage` already returns 1800
  for that stage. Delete `generalizedExecutionTimeoutSeconds` and `scaledGeneralizedTimeoutSeconds`
  (`:197-216`). Remove any now-unused imports (`TimeUnit` stays; it is used by the ctor).

- [ ] **Step 5: Wire `PitDataCollectionTask`.** At `:81`, replace
  `Configuration.getPitestMaxExecutionTime()` with `TimeoutBudget.forStage(stage)`.

- [ ] **Step 6: Wire `JpfInstrumentationTask`.** At `:284`, replace
  `Configuration.getJpfMaxExecutionTime()` with `Configuration.getJpfTimeoutPerAssertion()`.

- [ ] **Step 7: Build and run the new + affected tests.**

Run: `./gradlew spotlessApply build -x test --no-daemon && ./gradlew test --tests '*TimeoutBudgetTest*' --tests '*TestExecutionTask*' --tests '*PipelinePhaseTest*' --no-daemon`
Expected: BUILD SUCCESSFUL; `TimeoutBudgetTest` passes; no reference to removed symbols.

- [ ] **Step 8: Commit.**

```bash
git add src/main/resources/reference.conf src/main/java/teralizer/util/Configuration.java \
        src/main/java/teralizer/processing/task/TimeoutBudget.java \
        src/main/java/teralizer/processing/task/TestExecutionTask.java \
        src/main/java/teralizer/processing/task/PitDataCollectionTask.java \
        src/main/java/teralizer/processing/task/JpfInstrumentationTask.java
# feat(pipeline): unify stage timeouts behind fixed per-stage budgets
```

---

## Task 3: Strip profile-config timeout overrides

**Files (each sets `jpf`/`junit`/`pitest` timeout keys that now duplicate — or would silently shadow —
the reference defaults):**
- `project-configs/jarvis-scoreboard/commons-*-census.conf` (the with-PIT census configs: cli, codec,
  collections, configuration, csv, email, io, jexl, lang-3.5, math-3.5, pool, text)

- [ ] **Step 1: Remove the timeout overrides** from every `*-census.conf`: delete
  `jpf.max-execution-time`, the whole `junit { ... }` block (it holds only `max-execution-time`), and
  `pitest.max-execution-time`. Keep `jpf.max-path-condition-size`, `pitest.mutators`, generalization
  variants, and everything else. These configs then inherit the reference timeout defaults (1800s PIT
  covers the census max of 1467s; the two >3600s outliers are excluded as before).

- [ ] **Step 2: Verify no old timeout keys remain in any config or source.**

Run (built-in grep tool, pattern): `max-execution-time|baseline-tries-budget|max-generalized-execution-time`
across `project-configs/`, `src/main/resources/`, `src/main/java/`, `src/test/`.
Expected: only `jpf`/`search`-unrelated matches are gone; the only remaining hits are the RQ5
methodology configs handled in Step 3 and any prose in `docs/`.

- [ ] **Step 3: Migrate the RQ5 methodology configs** `project-configs/jarvis-scoreboard/commons-lang-3.5.conf`
  and `commons-math-3.5.conf` to the new key names, preserving their old *effective* caps (do NOT
  strip — these are a deliberate replication, per the spec's exception). The old configs set `junit`
  base 120 and inherited `max-generalized-execution-time = 3600`, so the generalized ceiling was 3600,
  not 120; `pitest` was a flat 300 across every variant:

```hocon
  jpf {
    timeout { per-assertion = 30 }
    max-path-condition-size = 100000
  }

  junit {
    # original-initial keeps the paper's 120s base; generalized keeps the old inherited 3600s ceiling.
    timeout { original-initial = 120, generalized = 3600 }
  }

  pitest {
    mutators = "DEFAULTS"
    timeout { original-initial = 300, generalized = 300 }
  }
```

  Whether RQ5 later keeps these or adopts the unified defaults is decided with the RQ5 work; this
  migration only re-expresses the current effective behavior under the new key names.

- [ ] **Step 4: Delete the transient calibration profile** `project-configs/reporeapers-pit-calibrate.conf`
  (uncommitted; the calibration is done and its findings live in the spec).

Run: `rm project-configs/reporeapers-pit-calibrate.conf`

- [ ] **Step 5: Commit.**

```bash
git add project-configs/jarvis-scoreboard/
# refactor(configs): drop per-config timeout overrides for unified defaults
```

---

## Task 4: Verify

- [ ] **Step 1: Full build.** Run: `./gradlew build --no-daemon`. Expected: BUILD SUCCESSFUL, exit 0.
- [ ] **Step 2: Pipeline gate.** Run: `scripts/verify-pipeline.sh`. Expected: goldens unchanged
  (fixtures set `pitest.enabled=false` and small suites, so the timeout values do not alter generated
  output); `gradle-nonzero=0`.
- [ ] **Step 3:** If a golden changes, investigate — do not edit the golden to match.

---

## Acceptance criteria (from the spec)

- `TimeoutBudget.forStage` is a pure resolver, unit-tested per stage incl. the unmapped-stage error.
- `TestExecutionTask` and `PitDataCollectionTask` derive their ConsoleCommand wall from `TimeoutBudget`;
  no inline timeout arithmetic; no dependence on generalization count / tries / mutant count.
- `reference.conf` carries the unified `timeout` schema; no legacy timeout keys remain anywhere.
- No profile config sets a timeout override except the two RQ5 methodology configs (migrated,
  preserving their paper caps).
- `./gradlew build` green and one `scripts/verify-pipeline.sh` golden pass.

## Deferred (not this plan)

- Setting `REPOREAPERS_PROJECT_TIMEOUT = 14400s` lands in the Phase 2 `reporeapers-rq6.conf` (the RQ6
  Stage-5 collection plan), not here.
- Whether the RQ5 configs adopt the unified defaults is decided with the RQ5 work.
- The optional ~20-project spike to pin the 1800s PIT cap by distribution.
