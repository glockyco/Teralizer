---
title: Filter-Exhaustion LIMITED Implementation
type: plan
status: active
created: 2026-06-29
parent: 2026-06-29-filter-exhaustion-limited-outcome
---

# Filter-Exhaustion LIMITED Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `skill://subagent-driven-development` (recommended) or `skill://executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep generated tests that hit jqwik `TooManyFilterMissesException` after validating at least one distinct new tuple, while preserving high-level scorecards that count those rows as passing.

**Architecture:** `NonPassingTestFilter` remains the generalization validation gate. It classifies `TooManyFilterMissesException` rows by reading the persisted jqwik value-log snapshot, records stable filter-result reasons plus `distinct_new_tuples`, and leaves `generalization.is_included = true` for useful limited runs. Python scorecards treat limited rows as high-level `passed` while exposing diagnostic metadata.

**Tech Stack:** Java 8, jOOQ generated records, PostgreSQL schema DDL in `src/main/resources/db/create-tables.sql`, jqwik/JUnit tests, Python analysis with `uv`/pytest.

---

## Files and responsibilities

- Modify `src/main/resources/db/create-tables.sql`
  - Add nullable `filter_result.distinct_new_tuples INTEGER`.
- Regenerate tracked jOOQ files under `build/generated-src/jooq/main/org/jooq/generated/**`
  - Existing repo exception: these generated sources live under `build/` but are tracked (`git ls-files build/generated-src/jooq` confirms this). Update only the tracked generated files changed by schema generation; do not stage unrelated build outputs.
- Modify `src/main/java/teralizer/processing/filter/FilterResult.java`
  - Carry optional `distinctNewTuples` from filters to DB records.
- Modify `src/main/java/teralizer/processing/task/TestFilteringTask.java`
  - Persist `FilterResult.distinctNewTuples` to `FilterResultRecord.setDistinctNewTuples(...)`.
  - Pass `projectRecord.getDataPath()` to the generalization `NonPassingTestFilter` constructor.
- Modify `src/main/java/teralizer/processing/filter/NonPassingTestFilter.java`
  - For generated-test filtering only, classify `TooManyFilterMissesException` using value-log evidence.
  - Emit stable reasons: `LIMITED_TOO_MANY_FILTER_MISSES`, `FILTER_EXHAUSTED_SEED_ONLY`, `FILTER_EXHAUSTED_VALUE_LOG_MISSING`.
- Create `src/main/java/teralizer/processing/filter/JqwikValueLogEvidence.java`
  - Read `.junit.tsv` rows and count distinct full-tuple rows beyond the seed.
  - Represent missing/unreadable logs separately from readable seed-only logs.
- Test `src/test/java/teralizer/processing/filter/JqwikValueLogEvidenceTest.java`
  - Unit tests for seed-only, duplicate seed, one new tuple, multiple new tuples, empty, and missing logs.
- Test `src/test/java/teralizer/processing/filter/NonPassingTestFilterLimitedTest.java`
  - DB-backed tests for pass, limited, seed-only reject, missing-log reject, and assertion-failure reject.
- Modify `analysis/src/teralizer/jarvis_scoreboard.py`
  - Join LIMITED filter metadata.
  - Count LIMITED as high-level `passed`.
  - Expose generation diagnostic status separately.
- Modify `analysis/tests/test_jarvis_scoreboard.py`
  - Verify LIMITED maps to high-level `passed` and keeps diagnostic metadata.

---

## Task 1: Schema and generated jOOQ

**Files:**
- Modify: `src/main/resources/db/create-tables.sql`
- Modify: tracked generated files under `build/generated-src/jooq/main/org/jooq/generated/**`

- [ ] **Step 1: Add the schema column**

In `src/main/resources/db/create-tables.sql`, change the `filter_result` table from:

```sql
    filter_name       TEXT   NOT NULL,
    decision          TEXT   NOT NULL,
    reason            TEXT   NOT NULL,
```

to:

```sql
    filter_name         TEXT   NOT NULL,
    decision            TEXT   NOT NULL,
    reason              TEXT   NOT NULL,
    distinct_new_tuples INTEGER,
```

`NULL` means the filter result is not backed by readable filter-exhaustion tuple evidence, or the row predates this schema.

- [ ] **Step 2: Regenerate jOOQ against a fresh scratch schema**

Run from repo root:

```bash
./gradlew startPostgres
```

Create and load a scratch DB for generation:

```bash
docker exec -i postgres-teralizer psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS postgres_limited_jooq;"
docker exec -i postgres-teralizer psql -U postgres -d postgres -c "CREATE DATABASE postgres_limited_jooq;"
cat src/main/resources/db/create-tables.sql | docker exec -i postgres-teralizer psql -U postgres -d postgres_limited_jooq -v ON_ERROR_STOP=1
DB_NAME=postgres_limited_jooq ./gradlew generateJooq
```

Expected: `generateJooq` succeeds and updates tracked generated files such as:

```text
build/generated-src/jooq/main/org/jooq/generated/tables/FilterResult.java
build/generated-src/jooq/main/org/jooq/generated/tables/records/FilterResultRecord.java
```

- [ ] **Step 3: Verify generated API contains the setter**

Confirm generated files contain:

```java
public final TableField<FilterResultRecord, Integer> DISTINCT_NEW_TUPLES
```

and:

```java
public void setDistinctNewTuples(Integer value)
```

Use `read` or editor inspection; do not rely on grep output alone when reviewing.

- [ ] **Step 4: Commit schema and generated jOOQ only**

Stage exactly:

```bash
git add src/main/resources/db/create-tables.sql \
  build/generated-src/jooq/main/org/jooq/generated/tables/FilterResult.java \
  build/generated-src/jooq/main/org/jooq/generated/tables/records/FilterResultRecord.java
```

If `generateJooq` changes other tracked generated metadata files (for example `Tables.java`), inspect each change and stage only the files whose diff is caused by the new `filter_result.distinct_new_tuples` column.

Commit:

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(db): record filter-exhaustion tuple evidence" \
COMMIT_BODY="Add nullable tuple-evidence metadata to filter_result so generated tests that exhaust jqwik filters can be classified without parsing prose reasons." \
bun skill://commit/commit-helper.ts
```

---

## Task 2: Tuple evidence reader

**Files:**
- Create: `src/main/java/teralizer/processing/filter/JqwikValueLogEvidence.java`
- Test: `src/test/java/teralizer/processing/filter/JqwikValueLogEvidenceTest.java`

- [ ] **Step 1: Write failing tests for value-log evidence**

Create `src/test/java/teralizer/processing/filter/JqwikValueLogEvidenceTest.java`:

```java
package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class JqwikValueLogEvidenceTest {

    @Example
    void missingLogHasNoEvidence() throws Exception {
        Path path = Files.createTempDirectory("jqwik-evidence").resolve("missing.tsv");

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertFalse(evidence.isReadable());
        Assert.assertNull(evidence.getDistinctNewTuples());
    }

    @Example
    void emptyLogHasNoEvidence() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertFalse(evidence.isReadable());
        Assert.assertNull(evidence.getDistinctNewTuples());
    }

    @Example
    void seedOnlyCountsZeroNewTuples() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList("ch=\\u0000"), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(0), evidence.getDistinctNewTuples());
    }

    @Example
    void duplicateSeedStillCountsZeroNewTuples() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList("ch=\\u0000", "ch=\\u0000", "ch=\\u0000"), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(0), evidence.getDistinctNewTuples());
    }

    @Example
    void oneDistinctTupleBeyondSeedCountsOne() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList("ch=\\u0000", "ch=\\u0001", "ch=\\u0001"), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(1), evidence.getDistinctNewTuples());
    }

    @Example
    void multiParameterRowsUseFullTupleIdentity() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList(
            "a=1\tb=2",
            "a=1\tb=3",
            "a=1\tb=3",
            "a=2\tb=2"
        ), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(2), evidence.getDistinctNewTuples());
    }
}
```

- [ ] **Step 2: Verify the tests fail**

Run:

```bash
./gradlew test --tests teralizer.processing.filter.JqwikValueLogEvidenceTest
```

Expected: compilation fails because `JqwikValueLogEvidence` does not exist.

- [ ] **Step 3: Implement the evidence reader**

Create `src/main/java/teralizer/processing/filter/JqwikValueLogEvidence.java`:

```java
package teralizer.processing.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JqwikValueLogEvidence {
    private final boolean readable;
    private final Integer distinctNewTuples;

    private JqwikValueLogEvidence(boolean readable, Integer distinctNewTuples) {
        this.readable = readable;
        this.distinctNewTuples = distinctNewTuples;
    }

    public static JqwikValueLogEvidence read(Path path) {
        List<String> rows;
        try {
            if (!Files.exists(path)) {
                return new JqwikValueLogEvidence(false, null);
            }
            rows = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new JqwikValueLogEvidence(false, null);
        }

        if (rows.isEmpty()) {
            return new JqwikValueLogEvidence(false, null);
        }

        String seed = rows.get(0);
        Set<String> distinctNewRows = new LinkedHashSet<>();
        for (String row : rows) {
            if (!row.equals(seed)) {
                distinctNewRows.add(row);
            }
        }
        return new JqwikValueLogEvidence(true, distinctNewRows.size());
    }

    public boolean isReadable() {
        return readable;
    }

    public Integer getDistinctNewTuples() {
        return distinctNewTuples;
    }
}
```

- [ ] **Step 4: Verify the tests pass**

Run:

```bash
./gradlew test --tests teralizer.processing.filter.JqwikValueLogEvidenceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/teralizer/processing/filter/JqwikValueLogEvidence.java \
  src/test/java/teralizer/processing/filter/JqwikValueLogEvidenceTest.java
COMMIT_ACTION=commit \
COMMIT_SUBJECT="test(filter): cover jqwik tuple evidence counting" \
COMMIT_BODY="Add the value-log evidence reader that distinguishes missing logs, seed-only logs, and distinct tuples beyond the seed for filter-exhaustion classification." \
bun skill://commit/commit-helper.ts
```

---

## Task 3: Persist optional tuple evidence on filter results

**Files:**
- Modify: `src/main/java/teralizer/processing/filter/FilterResult.java`
- Modify: `src/main/java/teralizer/processing/task/TestFilteringTask.java`
- Test: existing focused Java tests from Task 2 plus compilation

- [ ] **Step 1: Extend `FilterResult`**

Change `FilterResult.java` to add an optional count while preserving existing constructors:

```java
package teralizer.processing.filter;

public class FilterResult {

    private final String filter;
    private final FilterDecision decision;
    private final String reason;
    private final Integer distinctNewTuples;

    public FilterResult(String filter, FilterDecision decision) {
        this(filter, decision, "", null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason) {
        this(filter, decision, reason, null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason, Integer distinctNewTuples) {
        this.filter = filter;
        this.decision = decision;
        this.reason = reason;
        this.distinctNewTuples = distinctNewTuples;
    }

    public String getFilter() {
        return this.filter;
    }

    public FilterDecision getDecision() {
        return this.decision;
    }

    public String getReason() {
        return this.reason;
    }

    public Integer getDistinctNewTuples() {
        return this.distinctNewTuples;
    }
}
```

- [ ] **Step 2: Persist the count in `TestFilteringTask`**

In `TestFilteringTask.checkFilters`, after:

```java
record.setReason(filterResult.getReason());
```

add:

```java
record.setDistinctNewTuples(filterResult.getDistinctNewTuples());
```

This requires Task 1's regenerated `FilterResultRecord`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew test --tests teralizer.processing.filter.JqwikValueLogEvidenceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/teralizer/processing/filter/FilterResult.java \
  src/main/java/teralizer/processing/task/TestFilteringTask.java
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(filter): persist tuple evidence on filter results" \
COMMIT_BODY="Carry optional distinct-new-tuple counts through filter results so filter-exhaustion decisions can be queried without parsing reason text." \
bun skill://commit/commit-helper.ts
```

---

## Task 4: Classify TooManyFilterMisses in `NonPassingTestFilter`

**Files:**
- Modify: `src/main/java/teralizer/processing/filter/NonPassingTestFilter.java`
- Modify: `src/main/java/teralizer/processing/task/TestFilteringTask.java`
- Test: `src/test/java/teralizer/processing/filter/NonPassingTestFilterLimitedTest.java`

- [ ] **Step 1: Write failing DB-backed tests**

Create `src/test/java/teralizer/processing/filter/NonPassingTestFilterLimitedTest.java` with helper methods that create a temporary value-log path and an in-memory or test DSL context following existing test patterns. If the project test harness does not already provide a PostgreSQL-backed DSLContext, use jOOQ's `DSL.using("jdbc:h2:mem:limited", "sa", "")` only if the existing generated SQL works there; otherwise use the repo's configured PostgreSQL test DB. The test must insert into `junit_test_report` with the relevant `failure_type` and assert the returned `FilterResult` directly.

Required test cases:

```java
@Example
void tooManyFilterMissesWithNewTupleIsAcceptedAsLimited()
```

Setup:

- one non-passed generated JUnit row;
- `failure_type = "net.jqwik.api.TooManyFilterMissesException"`;
- value log rows: `ch=\\u0000`, `ch=\\u0001`.

Expected:

```java
Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
Assert.assertEquals("LIMITED_TOO_MANY_FILTER_MISSES", result.getReason());
Assert.assertEquals(Integer.valueOf(1), result.getDistinctNewTuples());
```

Also add:

```java
@Example
void tooManyFilterMissesWithSeedOnlyIsRejected()
```

Expected reason/count:

```java
FILTER_EXHAUSTED_SEED_ONLY
0
```

Add:

```java
@Example
void tooManyFilterMissesWithMissingLogIsRejected()
```

Expected reason/count:

```java
FILTER_EXHAUSTED_VALUE_LOG_MISSING
null
```

Add:

```java
@Example
void assertionFailureRemainsRejected()
```

Expected: existing reject behavior, no distinct tuple count.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew test --tests teralizer.processing.filter.NonPassingTestFilterLimitedTest
```

Expected: fail because `NonPassingTestFilter` does not yet inspect failure type or value logs.

- [ ] **Step 3: Pass data path into generalization filter**

In `NonPassingTestFilter`, add constants and a constructor that receives the data path:

```java
public static final String TOO_MANY_FILTER_MISSES = "net.jqwik.api.TooManyFilterMissesException";
public static final String LIMITED_TOO_MANY_FILTER_MISSES = "LIMITED_TOO_MANY_FILTER_MISSES";
public static final String FILTER_EXHAUSTED_SEED_ONLY = "FILTER_EXHAUSTED_SEED_ONLY";
public static final String FILTER_EXHAUSTED_VALUE_LOG_MISSING = "FILTER_EXHAUSTED_VALUE_LOG_MISSING";

private final java.nio.file.Path dataPath;
```

Keep the test-level constructor setting `dataPath = null`.

Add a generalization constructor:

```java
public NonPassingTestFilter(
    DSLContext create,
    TestRecord testRecord,
    GeneralizationRecord generalizationRecord,
    java.nio.file.Path dataPath
) {
    this.create = create;
    this.testRecord = testRecord;
    this.generalizationRecord = generalizationRecord;
    this.dataPath = dataPath;
}
```

In `TestFilteringTask.filterGeneralization`, change:

```java
new NonPassingTestFilter(create, this.testRecord, this.generalizationRecord)
```

to:

```java
new NonPassingTestFilter(create, this.testRecord, this.generalizationRecord, this.projectRecord.getDataPath())
```

- [ ] **Step 4: Query failing generated JUnit reports with failure type**

Replace the generated-test branch's `fetchInto(String.class)` with records containing method name and failure type:

```java
Result<Record2<String, String>> failingReports = this.create
    .select(Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME, Tables.JUNIT_TEST_REPORT.FAILURE_TYPE)
    .from(Tables.JUNIT_TEST_REPORT)
    .where(Tables.JUNIT_TEST_REPORT.PROJECT_ID.eq(this.generalizationRecord.getProjectId()))
    .and(Tables.JUNIT_TEST_REPORT.TEST_PACKAGE_NAME.eq(this.generalizationRecord.getPackageName()))
    .and(Tables.JUNIT_TEST_REPORT.TEST_CLASS_NAME.eq(this.generalizationRecord.getClassName()))
    .and(Tables.JUNIT_TEST_REPORT.VARIANT.eq(this.generalizationRecord.getVariant()))
    .and(Tables.JUNIT_TEST_REPORT.RESULT.ne(TestResult.PASSED))
    .fetch();
```

If `failingReports` is empty, return `ACCEPT`.

If any row has `failure_type` not equal to `TOO_MANY_FILTER_MISSES`, preserve existing reject behavior.

- [ ] **Step 5: Classify filter exhaustion from value-log evidence**

Build the snapshot path using the same convention as `JunitDataCollectionTask.getJunitJqwikValueLogPath`:

```java
private java.nio.file.Path getJunitJqwikValueLogPath() {
    return this.dataPath.resolve("project-id-" + this.generalizationRecord.getProjectId())
        .resolve("jqwik-data")
        .resolve(this.generalizationRecord.getId() + "." + this.generalizationRecord.getVariant() + ".junit.tsv");
}
```

Then:

```java
JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(getJunitJqwikValueLogPath());
if (!evidence.isReadable()) {
    return new FilterResult(this.getName(), FilterDecision.REJECT, FILTER_EXHAUSTED_VALUE_LOG_MISSING, null);
}
Integer distinctNewTuples = evidence.getDistinctNewTuples();
if (distinctNewTuples != null && distinctNewTuples > 0) {
    return new FilterResult(this.getName(), FilterDecision.ACCEPT, LIMITED_TOO_MANY_FILTER_MISSES, distinctNewTuples);
}
return new FilterResult(this.getName(), FilterDecision.REJECT, FILTER_EXHAUSTED_SEED_ONLY, 0);
```

- [ ] **Step 6: Verify Java tests pass**

Run:

```bash
./gradlew test --tests teralizer.processing.filter.JqwikValueLogEvidenceTest --tests teralizer.processing.filter.NonPassingTestFilterLimitedTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/teralizer/processing/filter/NonPassingTestFilter.java \
  src/main/java/teralizer/processing/task/TestFilteringTask.java \
  src/test/java/teralizer/processing/filter/NonPassingTestFilterLimitedTest.java
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(filter): keep useful filter-exhausted generalizations" \
COMMIT_BODY="Classify TooManyFilterMissesException rows using persisted jqwik tuple logs so useful filter-exhausted generalizations stay included with diagnostic evidence." \
bun skill://commit/commit-helper.ts
```

---

## Task 5: Analysis high-level pass plus diagnostics

**Files:**
- Modify: `analysis/src/teralizer/jarvis_scoreboard.py`
- Modify: `analysis/tests/test_jarvis_scoreboard.py`

- [ ] **Step 1: Write failing analysis tests**

In `analysis/tests/test_jarvis_scoreboard.py`, add a fixture row where:

- `generalization.is_included = TRUE`;
- `junit_test_report.result = 'ERROR'`;
- `junit_test_report.failure_type = 'net.jqwik.api.TooManyFilterMissesException'`;
- matching `filter_result.decision = 'ACCEPT'`;
- `filter_result.reason = 'LIMITED_TOO_MANY_FILTER_MISSES'`;
- `filter_result.distinct_new_tuples = 1`.

Assert:

```python
assert row["outcome"] == "passed"
assert row["generation_diagnostic"] == "limited_filter_exhausted"
assert row["distinct_new_tuples"] == 1
```

Also add seed-only and missing-log rows that remain excluded from the high-level generated-run query because `generalization.is_included = FALSE`.

- [ ] **Step 2: Run failing analysis tests**

Run:

```bash
uv run --directory analysis pytest tests/test_jarvis_scoreboard.py
```

Expected: fail because the query does not select filter-result LIMITED metadata and classification still maps `TooManyFilterMissesException` to `precondition_rejected`.

- [ ] **Step 3: Join LIMITED filter metadata**

In the generated-run SQL in `jarvis_scoreboard.py`, left join the `NonPassingTestFilter` row:

```sql
LEFT JOIN filter_result fr_limited
  ON fr_limited.generalization_id = g.id
 AND fr_limited.filter_name = 'NonPassingTestFilter'
 AND fr_limited.reason = 'LIMITED_TOO_MANY_FILTER_MISSES'
```

Select:

```sql
fr_limited.reason AS filter_result_reason,
fr_limited.distinct_new_tuples AS distinct_new_tuples
```

- [ ] **Step 4: Split high-level outcome from diagnostic outcome**

Update Python classification so LIMITED rows are high-level passed:

```python
LIMITED_FILTER_REASON = "LIMITED_TOO_MANY_FILTER_MISSES"


def classify_generated_test_outcome(result: str, failure_type: str | None, filter_result_reason: str | None = None) -> str:
    if filter_result_reason == LIMITED_FILTER_REASON:
        return "passed"
    normalized_result = result.upper()
    if normalized_result == "PASSED":
        return "passed"
    if failure_type in PRECONDITION_REJECTION_FAILURE_TYPES:
        return "precondition_rejected"
    if normalized_result == "FAILED":
        return "assertion_failed"
    return "error"
```

Add diagnostic classification:

```python
def classify_generation_diagnostic(filter_result_reason: str | None) -> str:
    if filter_result_reason == LIMITED_FILTER_REASON:
        return "limited_filter_exhausted"
    return "full"
```

Use the diagnostic only for included generated runs; rejected seed-only/missing rows are not in the included-run query.

- [ ] **Step 5: Run analysis tests**

Run:

```bash
uv run --directory analysis pytest tests/test_jarvis_scoreboard.py
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add analysis/src/teralizer/jarvis_scoreboard.py analysis/tests/test_jarvis_scoreboard.py
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(analysis): count LIMITED rows as scorecard passes" \
COMMIT_BODY="Keep filter-exhausted useful generalizations in high-level scorecards while retaining generation diagnostics for input-generation analysis." \
bun skill://commit/commit-helper.ts
```

---

## Task 6: End-to-end verification and plan bookkeeping

**Files:**
- Modify: `docs/plans/2026-06-29-limited-outcome-implementation.md` checkboxes as tasks complete.
- Possibly modify: `docs/plans/INDEX.md` via `omp-plans index`.

- [ ] **Step 1: Run focused Java verification**

Run:

```bash
./gradlew test --tests teralizer.processing.filter.JqwikValueLogEvidenceTest --tests teralizer.processing.filter.NonPassingTestFilterLimitedTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run focused Python verification**

Run:

```bash
uv run --directory analysis pytest tests/test_jarvis_scoreboard.py
```

Expected: all tests pass.

- [ ] **Step 3: Run broader verification**

Run:

```bash
./gradlew test
./gradlew build
uv run --directory analysis python validate.py --changed
```

Expected:

- Gradle test/build succeed.
- Analysis changed-file validation succeeds.

- [ ] **Step 4: Validate planning docs**

Run:

```bash
omp-plans index
omp-plans check
```

Expected: `ok`.

- [ ] **Step 5: Commit plan checkbox updates only**

If task checkboxes changed in this plan, stage exactly:

```bash
git add docs/plans/2026-06-29-limited-outcome-implementation.md docs/plans/INDEX.md
```

Commit:

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="docs(plans): track LIMITED implementation progress" \
COMMIT_BODY="Record completed implementation steps for filter-exhaustion LIMITED handling." \
bun skill://commit/commit-helper.ts
```

---

## Self-review

- **Spec coverage:** The plan covers the approved spec: filter-result metadata, distinct tuple counting, seed-only vs missing evidence, high-level pass semantics, generation diagnostics, and forward-only migration.
- **No generated test hook:** No task adds an `AroundPropertyHook`; classification remains post-processing.
- **No maxMisses tuning:** No task changes jqwik `maxMisses`.
- **JOOQ/tooling:** The plan updates tracked generated JOOQ files because this repo already tracks `build/generated-src/jooq`; it explicitly stages only schema-related generated diffs.
- **TDD:** Each implementation task starts with failing tests before production changes.
