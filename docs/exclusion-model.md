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

### The question it asks

Input generation already preserves the extracted path predicate. Both generation algorithms render
the flattened predicate into the generated property's jqwik `.filter`, so every candidate input
that survives filtering follows the execution path the specification came from
(`ModelToJavaTransformer.transformPredicate`, `NaiveTestParametersSupplierFactory:90-93`,
`ImprovedTestParametersSupplierFactory:32-35,106-110`). Widening the **inputs** is therefore
already sound.

The open question is whether the **oracle** can follow. `WideningLicense.evaluate` answers only
that, and nothing else.

### What licenses a widening

The expected side must either co-vary with the generated inputs or be provably invariant across
the admitted path. Which of those applies is decided by the shape of the persisted output model.

| Output model | What it is | Verdict |
|---|---|---|
| `SYMBOLIC` | an expression over symbolic inputs, so the generated assertion recomputes the expected value for each input | always widen |
| `CONSTANT` | a model with no variables, so SPF proved the value is constant along this path | always widen |
| `EXCEPTION` | the captured output kind was `THROWN`, so the oracle is "this throws" | conditional |
| `NULL_CONCRETE` | the persisted output model is literally `null`, so there is no symbolic evidence for the expected value | conditional and rare |

`OutputSpecClass` has exactly four values and the first three all return before the
`!= NULL_CONCRETE` guard, so that guard was unreachable and has been removed.

**`EXCEPTION` is a reachability claim, not a value claim.** The oracle says control reaches the
throw. That holds under widening when every branch deciding the throw either leaves a
path-condition clause the generated inputs must satisfy, or does not depend on a widened input. So
an empty path condition means an unconditional throw and is safe. Otherwise every widened
parameter must be named by the path condition.

**`NULL_CONCRETE` has two siblings that look identical on disk.** One is recoverable: a *computed
boolean* whose result is decided entirely by which branch was taken. The bytecode branches on the
symbolic operands, so the path condition captures the whole relation even though no separate
return attribute survives. The other is an unlicensed concrete oracle whose expected value simply
cannot follow the inputs. Telling them apart needs all four of:

1. the oracle expression type is `boolean` or `java.lang.Boolean`
2. no concretization event occurred
3. neither parameter set is empty
4. every generated parameter is named by the path condition

| Output spec class | attempts | widened | refused |
|---|---|---|---|
| `SYMBOLIC` | 747 | 747 | 0 |
| `CONSTANT` | 3 | 3 | 0 |
| `EXCEPTION` | 130 | 114 | 16 |
| `NULL_CONCRETE` | 4,649 | 1,194 | 3,455 |

**A symbolic output model was extracted for 750 of 5,529 attempts (13.6%), and every one of them
was licensed to widen.** The gate is not the barrier. Output-specification extraction is.

`NULL_CONCRETE` describes the persisted artifact, not the semantics. A 20-case source audit
(`docs/plans/2026-08-10-null-concrete-sampling.md`) read the original test and the tested method
for each sampled assertion and found 18 whose output plainly varies with a generalizable input,
including six with no recorded concretization event. Report 13.6% as the yield this extractor
achieved, never as the share of real Java tests whose outputs are input-independent.

### Why each refusal happens

**The oracle expression is not boolean** (1,828). This is the only discriminator between the two
`NULL_CONCRETE` siblings. Without a boolean result there is no computed-boolean argument to make,
and a null output model leaves no evidence at all for the expected value, so asserting the
recorded concrete value against a different input would be a guess. Note that the check reads the
type of the **oracle expression the assertion observes**, not the tested method's return type.
Those usually coincide and sometimes do not, and using the wrong one mis-attributes roughly
fifteen cases.

**Concretization weakened the path condition** (1,329). A concretization event is counted when SPF
reaches an `EXECUTENATIVE` instruction whose caller frame still carries a symbolic attribute in the
native method's argument slots (`TestGeneralizationListener:143-161`). JPF boxes concrete arguments
for MJI, and a native peer preserves symbolic attributes only if it explicitly reads and reattaches
them, so each event marks a boundary where symbolic tracking may be dropped. Branches taken after
that boundary run on concrete values and leave no clause behind. The path condition stops being a
complete record of what decided the result, which is exactly the evidence the computed-boolean
argument rests on.

`post_concretization_divergence_risk` is `true` when at least one event occurred and either a later
application conditional branched with no symbolic operand, or the captured throw did not originate
in application code. It is `false` when telemetry ruled both out, and `null` when unknown. The
exception branch accepts an explicit `false` and refuses `null`, because unknown is not evidence.
The null-concrete branch refuses on any event at all, because that inference needs the path
condition to be complete rather than merely non-divergent.

**The path condition does not pin every generated parameter** (299). The generated set is the
tested method's parameters plus *temporary parameters*, which are model variables appearing in the
input or output model without being declared parameters, such as constructor argument sites,
restricted to types the generator supports. The path-condition set is every `Variable` name in any
flattened top-level conjunct of the input predicate. A generated parameter missing from that set
is untested by the rendered filter, so jqwik may supply a value that leaves the captured execution
region, and an oracle justified only for that region no longer applies.

Three source codes report here, and they mean different things.

- The generated set is empty. Nothing is being generalized, so there is no widening to license.
- The path-condition set is empty. This is the **pass-through** case. A method can load and return
  a stored symbolic flag without ever branching, so the result varies with the input while the path
  condition stays empty. **An empty path condition means an unconditional throw for an exception
  oracle and is safe, and means no evidence at all for a null-concrete oracle and is never safe.**
  That asymmetry is the sharpest rule in the gate.
- Both sets are non-empty but some generated parameter is absent.

**An exception oracle was concretized with divergence risk** (15). The throw's reachability may
have been decided at the native boundary rather than by a clause the generated inputs must satisfy.

### Refusal causes measured

The report emits both a table and macros. The table is supporting analysis, alongside
`rq6_jpf_exception_causes` and `rq6_mut_choice_sensitivity`, and like those two it is not meant
for the chapter. The chapter includes three RQ6 tables, the stage funnel, the per-level breakdown,
and the per-filter decision matrix, which already ties RQ0 and RQ3 for the largest table budget in
the evaluation.

Chapter prose cites the macros instead: `\TzRealworldWideningRefusals`, and per cause
`\TzRealworldWideningRefusal<Cause>` with a matching `Pct` for the share of refusals.

The verdict is recorded as a single constant, so the deciding branch is **not persisted**
(defect **EM-8**). It can be reconstructed from `assertion.output_spec_class`,
`generalization_recipe ->> 'oracleExpressionType'`, `assertion.concretization_events` and
`assertion.post_concretization_divergence_risk`, in the branch order of `evaluate`:

The report emits this as `tab:widening-refusals`, merging the two parameter-coverage branches
because they differ only in output class.

| Deciding branch | refused | of 3,471 | of 5,529 attempts |
|---|---|---|---|
| `NULL_CONCRETE`, oracle expression not boolean | 1,828 | 52.7% | 33.1% |
| `NULL_CONCRETE`, concretization events present | 1,329 | 38.3% | 24.0% |
| Generated parameters not covered by the path condition | 299 | 8.6% | 5.4% |
| `EXCEPTION`, concretized with post-event divergence risk | 15 | 0.4% | 0.3% |

The reconstruction reproduces the implementation exactly on the three deterministic branches:
750 always-widen cases produced 0 refusals, 15 exception-risk cases produced 15 refusals, 1,329
concretized cases produced 1,329 refusals, and 1,829 non-boolean cases produced 1,828 refusals
plus one row pre-empted by the seed gate. The three coverage codes compare two `Set<String>` values
that are never persisted, so 299 refusals (8.6%) cannot be split further without EM-8.

### The residual risk the gate accepts

A boolean method whose path condition names a widened parameter for some branch unrelated to the
returned value, while the return itself is pass-through, is licensed and should not be. The source
states this deliberately. The gate is a small generation-time policy that rejects claims without
oracle evidence, and this case is left to the validation net downstream rather than handled by
weakening the license.

## Per-level composition in v6

`analysis/build/rq6/rq6_breakdown.csv` keeps the reader-facing three-way split. Mechanisms are
tracked internally to make the assignment correct and testable, then collapsed by
`MECHANISM_COLLAPSE` in `_causes_common.py`.

| Level | total | included | filtering | failures |
|---|---|---|---|---|
| Test | 85,595 | 44,989 | 40,311 | 295 |
| Assertion | 181,585 | 7,096 | 167,559 | 6,930 |
| Generalization | 5,529 | 1,614 | 3,893 | 22 |

Filtering is the tool declining an unsuitable candidate, so it holds filter rejections, gate
refusals, and inline capability exclusions. Failures are breakage, so they hold task exceptions
and the javac quarantine.

| Level | filtering breaks down as | failures break down as |
|---|---|---|
| Test | 37,407 filter rejections + 2,904 capability exclusions | 294 collector exceptions + 1 filter-task crash |
| Assertion | 167,559 filter rejections | 6,746 JPF exceptions + 184 javac quarantines |
| Generalization | 421 filter rejections + 3,472 gate refusals | 17 stage failures + 5 javac quarantines |

The two-column shape was never the problem. Two mechanisms were on the wrong side of it: the
javac quarantine counted as filtering because it borrows `filter_result`, and inline capability
exclusions counted as failures because they fell through an `ELSE`.

The generalization row's filtering column is 89.2% widening gate, which is not a filter, so its
causes are reported separately in `tab:widening-refusals`.

## Known defects

| ID | Defect | Layer | Blast radius | Status |
|---|---|---|---|---|
| EM-1 | Gate codes hardcoded into the `filtering` branch | analysis | 3,472 generalizations | fixed, `refused` column |
| EM-2 | `BREAKDOWN_SQL` counted any `filter_result` REJECT as filtering, including javac quarantine | analysis | 184 assertions, 5 generalizations | fixed, filter-name test |
| EM-3 | `ELSE 'failures'` absorbed inline capability exclusions | analysis | 2,904 tests | fixed, `unsupported` column |
| EM-4 | `assertion_counts` failed-task probe lacked the `generalization_id IS NULL` guard | analysis | 2 assertions | fixed |
| EM-5 | Project-scoped failures cleared no `is_included` while the lifecycle fanned them out | implementation | 15 generalizations | fixed in code, needs a re-collection to show in data |
| EM-6 | `AbstractTask` caught `Exception`, not `Throwable` | implementation | 2 assertions | fixed in code, needs a re-collection |
| EM-7 | `deriveRollup` reports "never ran" as "failed at this stage" | implementation | 98 generalizations | **open**, guarded by an `xfail` invariant |
| EM-8 | Widening refusal sub-reason not persisted | implementation | 299 refusals unsplittable | fixed in code via `generalization.widening_refusal_code`, needs a re-collection |

The root cause behind EM-1 through EM-3 was structural: a two-bucket model over a five-mechanism
pipeline with a silent `ELSE` catch-all. The classification is now total, and `_fetch_breakdown`
raises rather than publishing a report containing an entity it cannot name. Adding a sixth
mechanism without giving it a bucket now fails the run instead of inflating an existing column.

EM-5, EM-6 and EM-8 do not change any v6 figure. EM-5 and EM-6 only mislead code that reads
`is_included` on its own, which the breakdown does not, and EM-8 costs the split of 299 refusals
that no query over v6 can recover.

## Invariants

Enforced by `analysis/tests/eval/test_rq6_invariants.py`. Each is named after the assertion that
checks it.

1. `generalization` rows partition into gated and emitted, and emitted equals the
   `generalization_lifecycle` row count.
2. No gated generalization has a lifecycle row or a `filter_result` row.
3. Every `filter_result.filter_name` either matches the filter regex or is a known non-filter.
   A new unknown name fails the suite rather than silently joining the filtering bucket.
4. Every `exclusion_info` value matches a known mechanism. A new exclusion code fails the suite.
5. The mechanism columns sum to the level total. `_fetch_breakdown` additionally raises rather
   than publishing a report containing an entity it cannot classify, so the report itself refuses
   to hide drift.
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
