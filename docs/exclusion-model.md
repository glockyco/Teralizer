# Exclusion model

Why an entity leaves the pipeline, where that decision is recorded, and which column may be
trusted to report it.

Every count below was measured on `postgres_reporeapers_rq6_v6` after the run completed
(1,161/1,161 done markers, single variant `IMPROVED_200_TRIES`). Re-derive them with the queries
in [Re-deriving these numbers](#re-deriving-these-numbers) rather than trusting the figures here
after the corpus changes. The invariants are enforced by
`analysis/tests/eval/test_rq6_invariants.py`.

Read this before changing `analysis/src/teralizer/eval/reports/`, before adding a way to exclude
an entity, and before quoting an exclusion figure in a paper or thesis.

## The short version

The pipeline has **five** ways to exclude an entity. The analysis reports **two** buckets,
`filtering` and `failures`. Three mechanisms therefore land in a bucket that does not describe
them, and where they land is an accident of which SQL predicate matches first.

| # | Mechanism | Decides | Recorded in | Reported as | Correct |
|---|---|---|---|---|---|
| 1 | Filter rejection | candidate is out of scope | `filter_result` + a `Filter` implementation | filtering | yes |
| 2 | Build quarantine | generated code does not compile | `filter_result`, `filter_name='GeneratedTestValidator'` | filtering | **no**, a compile failure |
| 3 | Generation-time gate | oracle cannot be soundly widened | `generalization.exclusion_info` only | filtering | **no**, not a filter |
| 4 | Inline capability exclusion | shape unsupported by the collector | `test.exclusion_info` only | failures | **no**, nothing failed |
| 5 | Task exception | something threw | `exclusion_info` + a `FAILED` task row | failures | yes |

Mechanisms 2, 3 and 4 have no slot of their own. See [Known defects](#known-defects).

## Mechanism reference

### 1. Filter rejection

`TestFilteringTask.checkFilters` runs every filter for the stage with no short-circuit, writes one
`filter_result` row per filter, then clears `is_included` if any decision was `REJECT`
(`TestFilteringTask.java:196-246`). Decisions are `ACCEPT`, `DEFER`, `REJECT`
(`FilterDecision`). Exactly one of `test_id` / `assertion_id` / `generalization_id` is set,
preferring the most specific.

Because there is no short-circuit, every filter in a stage adjudicates the same entity set, so
per-filter totals are equal within a stage and differ between stages. In v6 the test-level stages
show this plainly: `FILTER_TESTS_ORIGINAL` (`NonPassingTest`, `TestType`) saw 82,603 tests and
`FILTER_TESTS` (eight filters) saw 73,764, because rejects from the earlier stage never reach the
later one.

Filter rows overlap. An assertion rejected by three filters appears in three rows. Never sum a
filter column and call it an exclusion count.

The exclusion message is `String.format("Excluded by %s.", this)` with **no** stack trace. That
matters: mechanism 5 writes the same prefix *with* a stack trace, and the presence of a newline is
the only way to tell them apart in `exclusion_info`.

### 2. Build quarantine

`ProjectBuildTask.recordQuarantineExclusion` compiles generated sources, and when javac reports
errors it removes the offending unit from the build and records the removal as a `filter_result`
row with the literal name `GeneratedTestValidator` and decision `REJECT`
(`ProjectBuildTask.java:200-211`). `GeneratedTestValidator` is not a `Filter`. It has no `check()`
and only shells out to javac.

Two reason codes, at two stages:

| Reason code | Entity | v6 |
|---|---|---|
| `UNCOMPILABLE_INSTRUMENTED_WRAPPER` | assertion | 187 |
| `UNCOMPILABLE_GENERALIZED_TEST` | generalization | 5 |

The project build then **succeeds**, because the uncompilable unit is gone. All three projects
holding the 5 generalization-level quarantines report `BUILD_PROJECT_GENERALIZED = SUCCEEDED`.

This mechanism is the reason `filter_result` cannot be read as "the filter log".

### 3. Generation-time gate

`TestGeneralizationTask.createBuilderPlan` refuses to emit a generalized test in two cases, writes
a bare code into `generalization.exclusion_info`, and returns `null`
(`TestGeneralizationTask.java:209-213` and `:259-263`). No generalized source is written, so
`GeneralizationLifecycleWriter.recordGeneratedSourceCreated` never fires and **no lifecycle row is
created**. No filter runs, so there is no `filter_result` row either.

| Code | Gate | Order | v6 |
|---|---|---|---|
| `INPUT_SPEC_NOT_SATISFIED_BY_SEED` | `SeedSpecConsistency` | first | 1 |
| `ORACLE_NOT_WIDENABLE` | `WideningLicense` | second | 3,471 |

Order matters for attribution. A generalization that would fail both is recorded under the seed
gate.

This mechanism is post-manuscript. It has no counterpart in the published Teralizer paper, and at
62.8% of all generalization attempts it is the single largest outcome of the v6 run. See
[The widening gate](#the-widening-gate).

### 4. Inline capability exclusion

`JunitDataCollectionTask.updateTestRecord` drops inherited test methods whose declaring class
cannot be flattened (`JunitDataCollectionTask.java:475-480`). It writes a typed code to
`test.exclusion_info` and no `filter_result` row.

| Code | v6 |
|---|---|
| `INHERITED_METHOD_NOT_FLATTENABLE:TYPE_VARIABLE` | 2,028 |
| `INHERITED_METHOD_NOT_FLATTENABLE:PRIVATE_MEMBER` | 876 |

This overlaps conceptually with `InheritedTestCaseFilter`, which rejected 1,064 tests through
mechanism 1. Two mechanisms decide about inherited tests. One appears in the filter table and one
does not.

`EvoSuitePostprocessingTask.java:216-232` is a second instance of this mechanism, inactive on
RepoReapers corpora.

### 5. Task exception

`AbstractTask.execute:52-74` catches `Exception`, writes
`String.format("Excluded by %s.%n%n%s", this, stackTrace)` into the most specific attached record,
and rethrows. `ProcessingPipeline` catches `Throwable`, marks the task `FAILED`, records a
`task_diagnostic` reason code, and calls `GeneralizationLifecycleWriter.recordStageFailed`.

Two scoping rules apply here and they do not agree, which is defect **EM-5**:

- `AbstractTask` clears the record **attached to the failing task**. A project-scoped task has no
  attached test, assertion, or generalization, so nothing is cleared.
- `recordStageFailed` fans a project-level failure out to **every** generalization of that
  project and variant, via `fetchByProjectVariant`.

`AbstractTask` catches `Exception`, not `Throwable`, which is defect **EM-6**.

## The generalization funnel

```
5,529  attempts                       one row per (assertion, variant)
  -3,472  gated before emission       mechanism 3, no lifecycle row
= 2,057  emitted                      == COUNT(*) FROM generalization_lifecycle
    -17  died before filtering        15 LISTENER_BUG, 2 UNSUPPORTED_REPORT_LAYOUT
= 2,040  adjudicated                  == COUNT(DISTINCT generalization_id) FROM filter_result
     -5  quarantined                  mechanism 2
= 2,035  reached NonPassingTestFilter
   -421  rejected TEST_NOT_PASSING    mechanism 1
= 1,614  generated_filter_passed      the generalization success count
   -159  lost at PIT collection       98 never ran, 54 timeout, 6 not green, 1 minion died
= 1,455  final_usable
```

Three denominators, three different questions:

| Denominator | n | Answers |
|---|---|---|
| attempts | 5,529 | how often is generalization attempted |
| emitted | 2,057 | of tests actually produced, how many hold |
| adjudicated | 2,040 | of tests the filter judged, how many passed |

`1,614 / 5,529 = 29.2%`, `1,614 / 2,057 = 78.5%`, `1,614 / 2,040 = 79.1%`. All three are correct.
Any figure quoting one of them must name it.

## Column semantics

### `is_included` — never use as a success signal

It means "never explicitly excluded", not "validated". Rows are created `true`
(`JunitDataCollectionTask.java:448`, `TestAnalysisTask.java:232`,
`TestGeneralizationTask.java:123`) and only cleared on the specific paths listed above. Any
failure that misses those paths leaves it `true`.

Confirmed stale-true rows in v6:

| Entity | n | Cause |
|---|---|---|
| generalization | 15 | project-scoped `EXECUTE_TESTS_GENERALIZED` failure, nothing attached to clear (EM-5) |
| assertion | 2 | `StackOverflowError` and `OutOfMemoryError`, which are not `Exception` (EM-6) |

`docs/database.md` already states that `generalization_lifecycle` is the yield authority. That
holds. At test and assertion level there is no lifecycle table, so the breakdown still keys on
`is_included` and inherits the defect.

### `exclusion_info`

Free-form and overloaded. Three shapes:

| Shape | Written by | Example |
|---|---|---|
| typed code | mechanisms 2, 3, 4 | `ORACLE_NOT_WIDENABLE` |
| `Excluded by <task>.` | mechanism 1 | `Excluded by TestFilteringTask{...}.` |
| `Excluded by <task>.` + blank line + stack trace | mechanism 5 | same prefix, then a trace |

The last two are distinguishable only by the newline. In v6 exactly one test carries the
crash shape from `TestFilteringTask` against 37,830 carrying the rejection shape.

### `filter_result`

Not only filters. Mechanism 2 writes here too. The honest test for "is this a filter" is the one
`FILTERING_SQL` already uses:

```sql
fr.filter_name ~ 'filter\.\w+Filter$'
```

`BREAKDOWN_SQL` does not apply it, which is defect **EM-2**.

### `generalization_lifecycle`

One row per emitted generalized test. Flags are set in pipeline order and
`GeneralizationLifecycleWriter.deriveRollup` reports the **first unset flag** as the failure stage.

`generated_filter_passed` is the generalization success signal.
`final_usable` additionally requires `generated_pit_collected`, so it imports mutation-testing
infrastructure failures into what looks like a generalization result. Use `final_usable` only for
claims about end-to-end yield after Stage 5.

`deriveRollup` cannot distinguish "stage failed" from "stage never ran", which is defect
**EM-7**. In v6, 98 of the 159 PIT-stage failures belong to 10 projects with no
`COLLECT_PIT_DATA_GENERALIZED` task at all. Those projects died at the **baseline** stages
`COLLECT_PIT_DATA_INITIAL` or `COLLECT_JACOCO_DATA_INITIAL`, for which the lifecycle has no flag.

## The widening gate

`WideningLicense.evaluate` (`src/main/java/teralizer/generalization/WideningLicense.java`) decides
whether a generated property may widen its inputs while keeping the extracted oracle coherent.

`OutputSpecClass` has exactly four values, and `SYMBOLIC`, `CONSTANT` and `EXCEPTION` all return
before the `!= NULL_CONCRETE` guard, so **that guard is unreachable**. Only `NULL_CONCRETE` and
`EXCEPTION` can be refused.

| Output spec class | attempts | widened | refused |
|---|---|---|---|
| `SYMBOLIC` | 747 | 747 | 0 |
| `CONSTANT` | 3 | 3 | 0 |
| `EXCEPTION` | 130 | 114 | 16 |
| `NULL_CONCRETE` | 4,649 | 1,194 | 3,455 |

**A symbolic output model was extracted for 750 of 5,529 attempts (13.6%), and every one of them
was licensed to widen.** The gate is not the barrier. Output-specification extraction is.

### Refusal causes

The verdict is recorded as a single constant, so the deciding branch is **not persisted**
(defect **EM-8**). It can be reconstructed from `assertion.output_spec_class`,
`generalization_recipe ->> 'oracleExpressionType'`, `assertion.concretization_events` and
`assertion.post_concretization_divergence_risk`, in the branch order of `evaluate`:

| Deciding branch | refused | of 3,471 |
|---|---|---|
| `NULL_CONCRETE`, oracle expression not boolean | 1,828 | 52.7% |
| `NULL_CONCRETE`, concretization events present | 1,329 | 38.3% |
| `NULL_CONCRETE`, generated parameters not covered by the path condition | 298 | 8.6% |
| `EXCEPTION`, concretized with post-event divergence risk | 15 | 0.4% |
| `EXCEPTION`, generated parameters not covered by the path condition | 1 | 0.0% |

The reconstruction reproduces the implementation exactly on the three deterministic branches:
750 always-widen cases produced 0 refusals, 15 exception-risk cases produced 15 refusals, 1,329
concretized cases produced 1,329 refusals, and 1,829 non-boolean cases produced 1,828 refusals
plus one row pre-empted by the seed gate. The two parameter-coverage branches compare two
`Set<String>` values that are never persisted, so 299 refusals (8.6%) cannot be split further
without EM-8.

## Per-level composition in v6

`analysis/build/rq6/rq6_breakdown.csv`, decomposed by mechanism.

| Level | total | included | filtering | failures |
|---|---|---|---|---|
| Test | 85,595 | 44,989 | 37,407 | 3,199 |
| Assertion | 181,585 | 7,094 | 167,743 | 6,748 |
| Generalization | 5,529 | 1,614 | 3,898 | 17 |

What those buckets actually contain:

| Level | `filtering` contains | `failures` contains |
|---|---|---|
| Test | 37,407 filter rejections, clean | **2,904 inline capability exclusions**, 294 collector exceptions, 1 filter-task crash |
| Assertion | 167,559 filter rejections + **184 javac quarantines** | 6,744 JPF exceptions + 4 stale-included |
| Generalization | 421 filter rejections + **5 javac quarantines** + **3,472 gate refusals** | 15 + 2 |

Only 421 of the 3,898 generalizations reported as filtered were filtered. Only 295 of the 3,199
tests reported as failed failed.

## Known defects

| ID | Defect | Layer | Blast radius | Fixable without a re-run |
|---|---|---|---|---|
| EM-1 | Gate codes hardcoded into the `filtering` branch (`rq6_causes.py:198-202`) | analysis | 3,472 generalizations | yes |
| EM-2 | `BREAKDOWN_SQL` counts any `filter_result` REJECT as filtering, including javac quarantine | analysis | 184 assertions, 5 generalizations | yes |
| EM-3 | `ELSE 'failures'` absorbs inline capability exclusions | analysis | 2,904 tests | yes |
| EM-4 | `assertion_counts` failed-task probe lacks the `generalization_id IS NULL` guard that `test_counts` has | analysis | 2 assertions | yes |
| EM-5 | Project-scoped failures clear no `is_included`, but the lifecycle fans them out | implementation | 15 generalizations | mitigate in analysis |
| EM-6 | `AbstractTask` catches `Exception`, not `Throwable` | implementation | 2 assertions | mitigate in analysis |
| EM-7 | `deriveRollup` reports "never ran" as "failed at this stage" | implementation | 98 generalizations | mitigate in analysis |
| EM-8 | Widening refusal sub-reason not persisted | implementation | 299 refusals unsplittable | **no** |

The root cause behind EM-1 through EM-3 is structural: a two-bucket model over a five-mechanism
pipeline, with a silent `ELSE` catch-all. Adding buckets without removing the catch-all will let
the sixth mechanism disappear the same way.

## Invariants

Enforced by `analysis/tests/eval/test_rq6_invariants.py`. Each is named after the assertion that
checks it.

1. `generalization` rows partition into gated and emitted, and emitted equals the
   `generalization_lifecycle` row count.
2. No gated generalization has a lifecycle row or a `filter_result` row.
3. Every `filter_result.filter_name` either matches the filter regex or is a known non-filter.
   A new unknown name fails the suite rather than silently joining the filtering bucket.
4. Every `exclusion_info` value matches a known mechanism. A new exclusion code fails the suite.
5. The three breakdown buckets sum to the level total.
6. Every generalization with `generated_filter_passed` has no `REJECT` filter result.
7. A lifecycle failure stage was actually attempted. Currently `xfail` under EM-7.

## Re-deriving these numbers

Run the report, do not hand-query, for anything that will be cited:

```bash
scripts/run-rq6-analysis.sh
```

For auditing the model itself, these are the queries behind this document.

Funnel:

```sql
SELECT (SELECT count(*) FROM generalization)                            AS attempts,
       (SELECT count(*) FROM generalization
         WHERE exclusion_info IN ('ORACLE_NOT_WIDENABLE',
                                  'INPUT_SPEC_NOT_SATISFIED_BY_SEED'))  AS gated,
       (SELECT count(*) FROM generalization_lifecycle)                  AS emitted,
       (SELECT count(DISTINCT generalization_id) FROM filter_result
         WHERE generalization_id IS NOT NULL)                           AS adjudicated,
       (SELECT count(*) FROM generalization_lifecycle
         WHERE generated_filter_passed)                                 AS passed,
       (SELECT count(*) FROM generalization_lifecycle
         WHERE final_usable)                                            AS usable;
```

Mechanism census:

```sql
SELECT CASE WHEN exclusion_info IS NULL              THEN '(included)'
            WHEN exclusion_info LIKE 'Excluded by%'
                 AND position(chr(10) in exclusion_info) > 0 THEN 'exception'
            WHEN exclusion_info LIKE 'Excluded by%'  THEN 'filter rejection'
            ELSE split_part(exclusion_info, ':', 1) END AS mechanism,
       count(*)
FROM generalization GROUP BY 1 ORDER BY 2 DESC;
```

Substitute `test` or `assertion` for `generalization` for the other levels.

Widening refusal taxonomy:

```sql
SELECT CASE
         WHEN a.output_spec_class IN ('SYMBOLIC', 'CONSTANT')       THEN 'widened'
         WHEN a.output_spec_class = 'EXCEPTION'
              AND coalesce(a.concretization_events, 0) > 0
              AND a.post_concretization_divergence_risk IS DISTINCT FROM false
                                                                    THEN 'exception: divergence risk'
         WHEN a.output_spec_class = 'EXCEPTION'                     THEN 'exception: parameter coverage'
         WHEN coalesce(a.generalization_recipe::jsonb ->> 'oracleExpressionType', '')
              NOT IN ('boolean', 'java.lang.Boolean')               THEN 'null model: oracle not boolean'
         WHEN coalesce(a.concretization_events, 0) > 0              THEN 'null model: concretized'
         ELSE                                                            'null model: parameter coverage'
       END AS branch,
       count(*) FILTER (WHERE g.exclusion_info = 'ORACLE_NOT_WIDENABLE') AS refused,
       count(*)                                                          AS total
FROM generalization g JOIN assertion a ON a.id = g.assertion_id
GROUP BY 1 ORDER BY 2 DESC;
```

## See also

- `docs/database.md` — table and column inventory.
- `docs/rq6-analysis.md` — how to produce citable RQ6 figures.
- `docs/architecture.md` — stage list and phase model.
- `.omp/rules/db.md`, `.omp/rules/pipeline.md` — the rules that point here.
