---
title: Filter-Exhaustion LIMITED Outcome
type: spec
status: implemented
created: 2026-06-29
parent: 2026-06-26-teralizer-overview
archived: 2026-06-29
---

# Filter-Exhaustion LIMITED Outcome

## Problem

Generated jqwik properties can use `Arbitrary.filter(...)` for residual path
predicates that the generator cannot construct by design. When the predicate is sparse,
jqwik throws `TooManyFilterMissesException` after the arbitrary's miss budget
(default `10000`) while trying to produce the next accepted value. The property can
therefore fail even after it has already executed the oracle on the seed input and one
or more genuinely new tuples.

Treating that exhaustion as an ordinary failed PBT loses sound work. Treating it as an
ordinary full pass hides a generation-quality limitation. Teralizer needs a third
outcome:

- **FULL** -- the property completed normally;
- **LIMITED** -- every accepted tuple passed the oracle, but generation exhausted before
  the configured tries budget;
- **EXCLUDED** -- the property never validated a useful tuple beyond the seed, or failed
  for a real oracle/runtime reason.

The outcome must be physically green for downstream tools such as PIT. PIT requires a
green suite before mutation analysis; a permanently failing generated property would
otherwise appear to kill every mutant or abort PIT before scoring.

## Ground rules

Grounded in jqwik 1.8.5 and PIT 1.17.0:

- `Arbitrary.filter(predicate)` delegates to `filter(10000, predicate)` and throws
  `TooManyFilterMissesException` from parameter generation when it cannot find the next
  accepted value. A `try/catch` inside the property body cannot catch it, because the
  body is called only after parameters are resolved.
- `@Property(maxDiscardRatio)` governs discarded property tries from assumptions, not
  `Arbitrary.filter(...)`'s per-value `maxMisses` budget. It is not the design lever.
- Increasing `filter(maxMisses, predicate)` is stochastic and expensive for sparse
  predicates. For `ch < 32` over all Java chars, acceptance is about `1 / 2048`; 1000
  accepted checks require roughly two million candidate draws before PIT repeats the
  property across coverage and mutants.
- PIT's partial failing-suite modes are not the semantic fix. A failing test must not be
  allowed to kill every mutant merely because generation exhausted.
- IMPROVED should construct domains by design whenever possible. `ch < 32` is a
  NAIVE-only sparse-filter example; IMPROVED should emit `chars().range(0, 31)` for the
  same partition.
- The seed wrapper must preserve jqwik exhaustive generation by delegating
  `exhaustive(long)` to the wrapped arbitrary. LIMITED is only for residual/filter
  paths that still exhaust after construction and exhaustive generation have done what
  they can.

## Outcome gate

LIMITED is accepted only when the current property execution recorded at least one
distinct serialized tuple beyond the original concrete seed tuple. The gate is a
**full-tuple count**, not PVC:

- serialize each accepted `TestParameters` row the same way the value log serializes it;
- treat the first serialized row as the seed tuple unless explicit seed metadata exists;
- deduplicate full rows;
- count distinct rows that are not equal to the seed row;
- accept as LIMITED iff that count is `>= 1`.

PVC remains telemetry for how much input diversity a kept generalization explored. PVC
must not decide whether a filter-exhausted run is useful.

## Generated-test behavior

Generated jqwik tests should be physically green when all accepted tuples satisfy the
oracle, even if generation later exhausts. Teralizer should implement this with a jqwik
`AroundPropertyHook`, not with filter-stage post-processing and not with a per-property
body wrapper.

The hook is applied to generated jqwik properties in all execution contexts. It executes
the property normally and remaps only this exact result to success:

1. final jqwik status is `FAILED`;
2. the final throwable is `net.jqwik.api.TooManyFilterMissesException`;
3. the in-memory recorder for the current property execution reports
   `distinctNewTupleCount() > 0`.

Every other result is returned unchanged:

- assertion failures remain failed and can kill PIT mutants;
- target-code runtime errors remain failed;
- seed-only filter exhaustion remains failed;
- missing recorder evidence remains failed;
- normal completions remain successful.

The hook must compute both the raw jqwik result and the mapped result in every
execution context. When durable diagnostics are enabled, it records both results. A
LIMITED run is therefore physically green, and normal generated-test executions remain
queryable as filter-exhausted.

## Shared generated support

Do not generate hook classes in every generalized test. Teralizer should inject one
shared support source file per generated source root, for example:

```text
src/test/java/teralizer/generated/TeralizerJqwikSupport.java
```

The shared support owns:

- `LimitedFilterMissesHook`, the jqwik `AroundPropertyHook`;
- `ValueRecorder`, the reusable in-memory tuple counter and optional value-log buffer;
- outcome sidecar writing when durable diagnostics are enabled;
- a small interface or registry that lets the hook find the current generated test's
  recorder.

Each generated `@Property` method should add only small references to the shared support.
Register the hook on the property method itself; do not rely on a class-level
`@AddLifecycleHook` for generated tests.

```java
@net.jqwik.api.lifecycle.AddLifecycleHook(
    teralizer.generated.TeralizerJqwikSupport.LimitedFilterMissesHook.class
)
@net.jqwik.api.Property(...)
public void property(...) { ... }
```

and a recorder field/call, for example:

```java
private static final teralizer.generated.TeralizerJqwikSupport.ValueRecorder<TestParameters>
    VALUE_RECORDER = ...;

VALUE_RECORDER.record(_p_);
```

The support class may use an explicit interface implemented by generated tests or a
controlled reflection convention. Prefer the interface if the generated boilerplate is
small; prefer reflection only if generated source size is measured to matter.

A ServiceLoader/global hook is not the first choice. It risks affecting non-generated
jqwik tests in the target project unless gated very tightly, and it makes generated-test
behavior less visible.

## Execution identity and sidecars

Diagnostics have two layers:

1. **Runtime evidence** is always in memory. The recorder tracks the seed tuple and
   whether any later accepted full tuple differs from it, exposing that gate to the hook.
   Persisted mode may keep the fuller distinct-count state needed for diagnostics. The
   minimal gate is sufficient for PIT: the hook can map useful filter exhaustion to
   success without writing jqwik diagnostic files during thousands of coverage and
   mutant invocations.
2. **Durable diagnostics** are enabled for ordinary generated-test execution stages.
   The recorder buffers accepted rows and the final outcome in memory, then flushes the
   value log and outcome sidecar once at the end of the property invocation. Collection
   tasks import those sidecars into `jqwik_property_execution`.

PIT defaults to `IN_MEMORY_ONLY` diagnostics. It still uses the same hook and recorder
for green-suite semantics, but it does not write jqwik value logs, jqwik outcome files,
or jqwik diagnostic DB rows. If a PIT-specific failure needs investigation, rerun the
generated tests outside PIT with persisted diagnostics and the same jqwik seed/config
before adding any PIT persistence feature.

Durable diagnostics are per **pipeline/test-JVM execution**, not merely per
generalization and not necessarily per JUnit report. Every pipeline task that persists
jqwik diagnostics must create a unique execution id and pass it into the test JVM, for
example:

```text
-Dteralizer.jqwik.executionId=<uuid-or-task-id>
-Dteralizer.jqwik.executionKind=JUNIT
-Dteralizer.jqwik.diagnosticsMode=PERSISTED
```

or equivalent environment variables. The execution id is a run id for one pipeline
command/minion context, not every jqwik property invocation.

When durable diagnostics are enabled, live artifacts are written under the execution id:

```text
jqwik-data/executions/<execution-id>/<generalization-id>.<variant>.outcome.json
jqwik-data/executions/<execution-id>/<generalization-id>.<variant>.values.tsv
```

For normal generated-test execution, one outcome and one value log per property per
execution id is enough because the property is invoked once. PIT has no durable jqwik
diagnostic artifacts in the first implementation.

The persisted outcome repeats the identity so import can validate it:

```json
{
  "executionId": "<execution-id>",
  "projectId": 1,
  "generalizationId": 14,
  "variant": "NAIVE_1000_TRIES",
  "testCaseName": "isAsciiPrintable",
  "executionKind": "JUNIT",
  "diagnosticsMode": "PERSISTED",
  "rawStatus": "FAILED",
  "finalStatus": "SUCCESSFUL",
  "diagnosticKind": "LIMITED_TOO_MANY_FILTER_MISSES",
  "throwableType": "net.jqwik.api.TooManyFilterMissesException",
  "tries": 179,
  "checks": 178,
  "distinctTuples": 32,
  "distinctNewTuples": 31,
  "seed": "0",
  "valueLogPath": "jqwik-data/executions/<execution-id>/14.NAIVE_1000_TRIES.values.tsv"
}
```

`@BeforeProperty` reset must clear the in-memory recorder state for the current
invocation. In persisted JUnit mode it may also clear the current execution id's live
value log and outcome sidecar before writing the fresh result. A stale event from a
prior run must never be importable as the current run's result because artifact
collectors import only files under the current execution id and reject payloads whose
`executionId`, `generalizationId`, `variant`, or `testCaseName` do not match the
execution being collected. Reusing an execution id is invalid.

Do not add PIT jqwik outcome files in the first implementation. The PIT spike proved
that a single repeated-invocation path can overwrite earlier outcomes, and storing
per-invocation PIT telemetry is not needed for the current scorecards. Default PIT
avoids that cost and risk by using in-memory-only diagnostics. If future work requires
per-mutant jqwik diagnostics, write a separate spec for an append-only or
invocation-scoped design.

## Database model

Diagnostics belong in their own execution tables. They are not filter decisions, and
they are not stable generated-artifact metadata.

Use one table for persisted diagnostic runs and one table for per-property summaries.
The run table gives later collection tasks a reliable way to find the sidecar directory
created by the earlier execution task.

```sql
CREATE TABLE jqwik_execution_run
(
    id             BIGSERIAL PRIMARY KEY,

    execution_id   TEXT    NOT NULL UNIQUE,
    project_id     BIGINT  NOT NULL,
    task_id        BIGINT,

    step           INTEGER NOT NULL,
    stage          TEXT    NOT NULL,
    variant        TEXT,
    execution_kind TEXT    NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);

CREATE INDEX idx_jqwik_execution_run_project_id
    ON jqwik_execution_run (project_id);
CREATE INDEX idx_jqwik_execution_run_task_id
    ON jqwik_execution_run (task_id);
CREATE INDEX idx_jqwik_execution_run_stage_variant
    ON jqwik_execution_run (stage, variant);
```

Use `jqwik_property_execution` for one imported summary row per generated property per
execution run. The row points at the raw diagnostic sidecar and, when relevant, the
selected value log used to compute the summary:

```sql
CREATE TABLE jqwik_property_execution
(
    id                       BIGSERIAL PRIMARY KEY,

    jqwik_execution_run_id   BIGINT NOT NULL,
    project_id               BIGINT NOT NULL,
    generalization_id        BIGINT NOT NULL,

    junit_test_report_id     BIGINT,

    test_case_name           TEXT    NOT NULL,
    diagnostic_kind          TEXT    NOT NULL,

    raw_status               TEXT    NOT NULL,
    final_status             TEXT    NOT NULL,
    throwable_type           TEXT,
    throwable_message        TEXT,

    tries                    INTEGER,
    checks                   INTEGER,
    distinct_tuples          INTEGER,
    distinct_new_tuples      INTEGER,
    seed                     TEXT,

    selected_value_log_path  TEXT,
    diagnostic_sidecar_path  TEXT,

    FOREIGN KEY (jqwik_execution_run_id) REFERENCES jqwik_execution_run (id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE,
    FOREIGN KEY (junit_test_report_id) REFERENCES junit_test_report (id) ON DELETE CASCADE,

    UNIQUE (jqwik_execution_run_id, generalization_id, test_case_name)
);
```

Recommended indexes:

```sql
CREATE INDEX idx_jqwik_property_execution_run_id
    ON jqwik_property_execution (jqwik_execution_run_id);
CREATE INDEX idx_jqwik_property_execution_project_id
    ON jqwik_property_execution (project_id);
CREATE INDEX idx_jqwik_property_execution_generalization_id
    ON jqwik_property_execution (generalization_id);
CREATE INDEX idx_jqwik_property_execution_junit_test_report_id
    ON jqwik_property_execution (junit_test_report_id);
CREATE INDEX idx_jqwik_property_execution_diagnostic_kind
    ON jqwik_property_execution (diagnostic_kind);
```

Every generated jqwik property execution should get a row, including FULL runs. Absence
of a row means missing/import-failed diagnostics, not FULL.

Suggested `diagnostic_kind` values:

- `FULL`;
- `LIMITED_TOO_MANY_FILTER_MISSES`;
- `FILTER_EXHAUSTED_SEED_ONLY`;
- `ASSERTION_FAILED`;
- `EXECUTION_ERROR`;
- `DIAGNOSTIC_MISSING` when an importer needs an explicit failure row.

`junit_test_report_id` is nullable because not every durable diagnostic run necessarily
has an imported JUnit XML row at insertion time. For JUnit collection rows, link it when
available. Since current report import uses `batchInsert`, implementation can either
insert generated report rows with `returning()` before inserting diagnostics, or rely on
the execution-run key and backfill/link the report id after insert. Prefer a real
`junit_test_report_id` link unless measured import overhead says otherwise.

`filter_result.distinct_new_tuples` should not be the long-term source of this data.
`filter_result` remains a filter ledger; jqwik execution diagnostics live in
`jqwik_property_execution`.

## Pipeline placement

Generated-test execution tasks create and pass execution ids when durable diagnostics
are enabled. Artifact collection tasks import durable diagnostics. PIT's default path
passes `teralizer.jqwik.diagnosticsMode=IN_MEMORY_ONLY` and does not create jqwik
diagnostic sidecars or DB rows.

`task.id` is a good execution-id candidate only after one plumbing change: today
`ProcessingPipeline` creates the `TaskRecord` outside `Task.execute`, and task
implementations do not receive that record or its id. An implementation must either
expose the current `TaskRecord.id` through `TaskContext` before invoking the task, or
generate a UUID inside the execution task and persist a small execution-run mapping so
later collection tasks know which execution id to import. Do not infer the execution id
from `(project_id, stage, variant, generalization_id)`: retries and reruns can share all
of those values.

- `TestExecutionTask` for `EXECUTE_TESTS_GENERALIZED` creates a JUnit execution id,
  inserts a `jqwik_execution_run` row, passes the id to Maven/Gradle test JVMs, and
  leaves sidecar parsing to collection.
- `JunitDataCollectionTask` for `COLLECT_JUNIT_REPORTS_GENERALIZED` finds the matching
  `jqwik_execution_run`, imports JUnit XML, snapshots/imports sidecars under that
  execution id, inserts `jqwik_property_execution`, and links `junit_test_report_id`
  where possible.
- `PitDataCollectionTask` for `COLLECT_PIT_DATA_GENERALIZED` leaves jqwik diagnostics in
  `IN_MEMORY_ONLY` mode. It still passes the mode through PIT minion JVM args or an
  inherited environment variable so minion JVMs classify useful filter exhaustion
  correctly, but it does not import jqwik diagnostics. Passing the value only through
  Surefire or the outer Maven process is insufficient; the PIT-launched test JVM must
  see it.

For PIT, the support hook still maps useful `TooManyFilterMissesException` to success,
so PIT receives a green property. Mutants are killed only by real assertion failures or
runtime failures, not by generator exhaustion after all accepted tuples passed.

## Scorecard and analysis semantics

High-level scorecards use `final_status` and collapse LIMITED into passed:

```text
final_status = SUCCESSFUL
```

Generation-diagnostic analyses use `diagnostic_kind`:

```text
FULL
LIMITED_TOO_MANY_FILTER_MISSES
FILTER_EXHAUSTED_SEED_ONLY
ASSERTION_FAILED
EXECUTION_ERROR
```

For JARVIS Table-2 style scorecards, LIMITED counts as passing by default. Diagnostic
tables can split LIMITED rows out without changing the high-level pass/fail result.

## Non-goals

- Do not tune jqwik `maxMisses` as the fix.
- Do not make NAIVE construct domains that define IMPROVED's advantage.
- Do not exclude LIMITED generated tests from PIT target tests.
- Do not use PIT's partial failing-suite mode as the semantic fix.
- Do not add PIT jqwik sidecars, a per-mutant jqwik diagnostics DB table, or per-mutant
  jqwik scorecard analysis in the first implementation.
- Do not retroactively re-score archived runs unless a paper-specific audit explicitly
  asks for a regenerated run.

## Acceptance criteria

- In a persisted generated-test execution, a property that exhausts
  `Arbitrary.filter(...)` after validating at least one distinct new tuple is physically
  green and records `LIMITED_TOO_MANY_FILTER_MISSES` in `jqwik_property_execution`.
- In a persisted generated-test execution, a property that exhausts before validating a
  distinct new tuple remains non-green and records seed-only or missing-diagnostic
  evidence.
- Assertion failures remain non-green and can kill PIT mutants.
- PIT runs LIMITED generated tests with in-memory-only jqwik diagnostics: useful
  generator exhaustion does not kill mutants or abort PIT, and PIT does not write jqwik
  value logs, jqwik outcome sidecars, or jqwik diagnostic DB rows. This requires explicit
  propagation of `teralizer.jqwik.diagnosticsMode=IN_MEMORY_ONLY` into the PIT minion
  JVM, not just the outer Maven/Surefire process.
- Every generated jqwik property execution imported by the pipeline has a diagnostic row;
  FULL is explicit, not represented by row absence.
- Sidecar imports are keyed by a collector-owned execution id and reject stale or
  mismatched payloads.
- Generated support is centralized so the hook/recorder implementation is not duplicated
  in every generated test class.
- High-level scorecards count LIMITED as passed while generation-specific analyses can
  query LIMITED and tuple counts directly from the DB.
