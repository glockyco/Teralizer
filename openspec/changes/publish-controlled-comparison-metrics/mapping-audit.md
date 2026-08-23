# Cross-Corpus Comparison Mapping Audit

## Operator decisions

- Preserve every project and generalized-test row in the published corpus denominators.
- Represent each corpus pipeline with the latest recorded producer revision. Treat within-corpus
  revision differences as a minor limitation rather than reconstructing every revision.
- Because both denominators contain rows from non-representative revisions, any retained comparison is
  qualified and descriptive even if its task-transition predicate otherwise maps directly.

## Inputs and report revisions

| Side | Semantic input | Physical snapshot | Projects | Configuration | Report run | Evidence-query revision |
|---|---|---|---:|---|---|---|
| Controlled RQ5 | `controlled` | `postgres_dev` | 13 | No external config or data directory | `d035a6555f1f266bb01de0013b859377052ce204` | RQ5 breakdown query `e6018372bcf463c65b1e2eb3c92e0c2d6078405c` |
| RepoReapers RQ6 | `real-world` | `postgres_reporeapers_rq6_v7` | 1,161 physical; 584 eligible | `project-configs/replication/extended`; `data/reporeapers-rerun-v7` | `d035a6555f1f266bb01de0013b859377052ce204` | Generalization funnel and mechanism partition `da8dfb508e363bd66c05e6739ddf0a5d3c63f6d2` |

The report run and evidence-query revisions are consumers of frozen databases. They are not the pipeline
revisions that produced those databases.

## Producer revisions and representative selection

The repository comparability command reports `NOT comparable`: the corpora have different tool-version
sets and different funnel schemas. This audit therefore supplies an explicit translation rather than a
figure-for-figure comparison.

### Controlled

`project.tool_git_version` records seven revisions across the 13 projects:

| Recorded revision | Projects | Project-id range |
|---|---:|---:|
| `3d62433bab620df6bcd94d0ee60a2d77ec3483be` | 2 | 11-13 |
| `416fac458abd58d587eb56e145fbeb6e4953ad7d` | 3 | 22-25 |
| `4a1cf22a8d4ce5633896a788eba7193bf203440c` | 1 | 27 |
| `86c6c6e5e401b53dc68fc6017498b02163ad6461` | 1 | 29 |
| `075b646949a7a98d82ea23b71877ef5c56fd186f` | 3 | 30-32 |
| `bac35de35c613d6522f86ebf36439c2ce5f76719` | 1 | 34 |
| `580ca7de779a25ca6e697f433386413bf983d915` | 2 | 35-36 |

Project insertion order makes `580ca7de779a25ca6e697f433386413bf983d915` the latest recorded
revision. Its Git object is absent from the local, GitHub, and producer-workstation object stores checked
by this audit. The latest recoverable recorded controlled revision is
`86c6c6e5e401b53dc68fc6017498b02163ad6461` (2025-04-08,
`Add baseline projects for data collection`). Its executable graph is the controlled representative and
must agree with persisted task/stage rows before it supports a mapping.

The retained controlled variant is `IMPROVED_200_TRIES`: 13,836 generalization rows, of which 11,597
carry `generalization.is_included = true`.

### RepoReapers

`project.tool_git_version` records three non-null revisions plus 329 null values across the 1,161
physical projects. The accepted eligibility predicate excludes every null-version project. The 584
eligible projects divide as follows:

| Recorded revision | Eligible projects |
|---|---:|
| `66ac3dea83cf9dc6cf13d49843f201a243df6b2d` | 286 |
| `c8a26a79ccd0a2b7b5eebd1c30e825449caf2e15` | 34 |
| `c58fcd3a3826b6945bb7d43117da663aabfbdbc8` | 264 |

Project insertion order makes `c58fcd3a3826b6945bb7d43117da663aabfbdbc8` the latest recorded
non-null revision. The object is recoverable (2026-08-11,
`docs(generalization): correct why a returned value carries no expression`) and is the RepoReapers
representative.

The sole real-world variant is `IMPROVED_200_TRIES`: 5,356 generalization attempts across eligible
projects. The accepted lifecycle reports 1,615 validated generalizations; the generic inclusion flag is
true for 1,583 and is not the RQ6 lifecycle authority.

## Eligibility and relevant schema evidence

The controlled RQ5 denominator joins `generalization` to `project` and requires only
`project.use_test_generalization`, then selects the retained variant. Its relevant persisted evidence is
`project`, `task`, `test`, `assertion`, `generalization`, `filter_result`, and `junit_test_report`.

The real-world denominator requires `project.use_test_generalization`, excludes the declared projects
with no executed baseline tests, and excludes projects with failed project-scoped
`SETUP_PROJECT`, `ADD_DEPENDENCIES`, or `BUILD_PROJECT_ORIGINAL` tasks. Its relevant persisted evidence
adds `generalization_lifecycle`, `task_diagnostic`, and typed filter-result details. The real-world schema
also contains `project.use_test_reduction`; the controlled schema does not.

The population difference is intrinsic: these are disjoint corpora with different eligibility and
instrumentation. No result from this change may be described as paired or causal.

## Controlled representative pipeline

The recoverable controlled representative predates `PipelinePlanner`. `ProjectSetupTask` schedules one
priority queue ordered by the numeric `ProcessingStage.step`; `ProcessingPipeline` executes it and drops
queued descendants by matching project, test, assertion, and generalization identities after a failure.
A project-scoped failure therefore drops all later project work.

The persisted task table contains every stage declared by the representative `ProcessingStage`, with the
same step order from `CLEANUP_PROJECT` (0) through `COLLECT_PIT_DATA_GENERALIZED` (33). The retained
outcome follows this transition chain:

1. `GENERALIZE_TESTS` (27) schedules one assertion-scoped task per included assertion. It inserts a
   `generalization` row with `is_included = true` before writing the generated source. An exception then
   clears that row through `AbstractTask`.
2. `BUILD_PROJECT_GENERALIZED` (28) compiles all generated tests in one project-and-variant-scoped task.
   It does not carry a `generalization_id`; failure drops later tasks but does not clear any surviving
   generalization flag.
3. `EXECUTE_TESTS_GENERALIZED` (29) executes the included original and generated classes in one
   project-and-variant-scoped task. Assertion and jqwik filtering failures are tolerated so report
   collection can classify individual tests. A structural execution failure drops later tasks without
   clearing generalization flags.
4. `COLLECT_JUNIT_REPORTS_GENERALIZED` (30) expands into one task per included generalization. Missing or
   invalid per-generalization reports fail with a `generalization_id` and clear `is_included`.
5. `FILTER_GENERALIZATIONS` (31) expands over the remaining included generalizations. A
   `NonPassingTestFilter` rejection stores a `filter_result` row and clears `is_included`.
6. JaCoCo and PIT collection (32-33) are project-and-variant-scoped and occur after the inclusion verdict.
   Their failures do not clear generalization rows.

Persisted counts corroborate the representative graph: `GENERALIZE_TESTS` has 96,852
`generalization_id`-scoped executions; generated-report collection and filtering each have 96,660 such
executions, accounting for the 192 generation-task failures. Build, execution, JaCoCo, and PIT have 49
project-and-variant tasks and no generalization identity.

Consequently, controlled `generalization.is_included` proves successful source generation plus successful
per-generalization report collection and no generated-test filter rejection only when the project-scoped
build and execution tasks reached those later stages. By itself, the flag does not prove compilation or
execution: a project-scoped failure can strand earlier rows as included while dropping all later tasks.

## RepoReapers representative pipeline

The RepoReapers representative uses `PipelinePlanner` to run independently toggled generation,
generalization, and reduction phases. Each phase clears owned outputs, checks preconditions, schedules its
stages, drains the queue, and then fails fast on uncoded structural project-level failures. Generalization
contains stages 7-27; reduction is a separate later phase at stages 28-35.

The generalized-test transition chain is:

1. `GENERALIZE_TESTS` (23) creates an attempt row for every candidate. A generation-time refusal may
   leave the attempt without source output. Successful emission creates the lifecycle row and sets
   `generated_source_created`.
2. Project-scoped `BUILD_PROJECT_GENERALIZED` (24) fans success or failure out to every source-created
   lifecycle row for the project and variant, setting `generated_project_compiled`.
3. Project-scoped `EXECUTE_TESTS_GENERALIZED` (25) similarly sets `generated_tests_executed` only for
   compiled rows.
4. `COLLECT_JUNIT_REPORTS_GENERALIZED` (26) sets `generated_report_collected` for executed rows. Both
   project- and generalization-scoped results are normalized by `GeneralizationLifecycleWriter`.
5. `FILTER_GENERALIZATIONS` (27) records an accepted or rejected typed verdict and sets
   `generated_filter_passed` only after all previous flags are true. This is the pre-reduction validated
   boundary used by the RQ6 generalization funnel.
6. The separate reduction phase restores archived builds and collects original, initial, and generalized
   JaCoCo/PIT data. Generalized PIT success sets `generated_pit_collected`; the lifecycle rollup calls
   this `final_usable`.

The persisted task table contains the representative stage order. The eligible `IMPROVED_200_TRIES`
lifecycle conserves the transitions as follows: 5,356 attempted; 2,057 source-created; 2,057 compiled;
2,042 executed; 2,035 report-collected; 1,615 filter-passed; and 1,435 PIT-collected/final-usable. The
generic `generalization.is_included` flag has 1,583 true rows and therefore disagrees with every accepted
lifecycle boundary. It is not comparison evidence on the real-world side.

## Representative graph differences

| Boundary | Controlled representative | RepoReapers representative | Mapping consequence |
|---|---|---|---|
| Scheduling | `ProjectSetupTask` places the complete run in one step-priority queue | `PipelinePlanner` clears, checks, schedules, and drains three separate phases | Stage ordinal and queue completion are not common identities |
| Attempt | `GENERALIZE_TESTS` inserts a true inclusion row before source generation | Generalization records include pre-emission refusals; lifecycle starts only when source exists | `generalization` row is the common attempt entity; source creation is not the denominator |
| Generated build | One project-and-variant task; failures drop descendants without clearing existing flags | One project-and-variant task; lifecycle writer fans success/failure out to source-created rows | Controlled flag alone cannot prove this boundary |
| Generated execution | One project-and-variant task; tolerated assertion failures proceed to reports | One project-and-variant task with typed execution diagnostics and lifecycle fanout | Task success is structurally similar, persistence is stronger in RQ6 |
| Report collection | Per-generalization failures clear the generic inclusion flag | Per-generalization and project outcomes set `generated_report_collected` | Both provide per-entity evidence after successful collection |
| Generated filtering | Rejection clears `is_included` and stores an untyped filter result | Typed verdict sets `generated_filter_passed`; rejection is a lifecycle mechanism | This is the strongest shared pre-reduction outcome |
| Coverage and mutation | Original coverage precedes analysis; generalized JaCoCo/PIT immediately follows filtering in the same queue | All coverage/mutation work moved to a separate reduction phase after generalization | Post-filter success cannot be compared through the RQ5 inclusion flag |
| Final usability | No typed rollup; inclusion ignores project-scoped JaCoCo/PIT failure | Requires generalized PIT success and records `final_usable` | Final usability is unmappable to RQ5 |

The stable task sequence is source creation -> generated build -> generated execution -> generated report
collection -> generated filtering. RQ6 adds explicit lifecycle persistence at each transition and moves
mutation/coverage into a separate phase. Therefore the only candidate compatible with the retained
numerators is **attempts that pass generated-test filtering before reduction**. Compilation, execution,
and final usability are not encoded reliably by the controlled inclusion flag without additional task
joins.

## Controlled outcome audit

For `IMPROVED_200_TRIES`, the controlled denominator partitions exactly:

| Outcome evidence | Generalizations |
|---|---:|
| Generated-filter passage | 11,597 |
| Generated-filter rejection | 2,207 |
| Generation-task failure before source completion | 32 |
| Total attempts | 13,836 |

All 32 non-filter failures are failed `GENERALIZE_TESTS` tasks. The corpus contains no included row with
a rejected filter decision and no included row lacking any of these successful transitions:

- generalization-scoped `GENERALIZE_TESTS`;
- project-and-variant-scoped `BUILD_PROJECT_GENERALIZED`;
- project-and-variant-scoped `EXECUTE_TESTS_GENERALIZED`;
- generalization-scoped `COLLECT_JUNIT_REPORTS_GENERALIZED`; and
- generalization-scoped `FILTER_GENERALIZATIONS`.

Thus, for this frozen corpus, `generalization.is_included = true` is extensionally equal to explicit
**generated-filter passage** evidence. The mapping must implement and validate the explicit task/filter
predicate; the flag may be checked for agreement but must not define the lifecycle meaning. This guards
against the historical failure-propagation weakness even though no retained `IMPROVED_200_TRIES` row
exhibits it.

## RepoReapers lifecycle boundary audit

The accepted RQ6 predicate `generalization_lifecycle.generated_filter_passed` yields 1,615 rows. Every
one also has source-created, compiled, executed, and report-collected flags; a successful
`FILTER_GENERALIZATIONS` task; and no rejected `filter_result`. No `final_usable` row lacks filter
passage. The 180 filter-passed rows that are not final-usable are reduction attrition, which is correctly
outside the candidate pre-reduction numerator.

The 5,356-attempt denominator includes 3,299 pre-emission outcomes: 3,298 carry a typed widening-refusal
code and one carries the generation-gate code `INPUT_SPEC_NOT_SATISFIED_BY_SEED`. This is intentional:
all eligible candidate generalizations enter the denominator, while only those completing generated
filtering enter the numerator.

The corresponding real-world source mapping is therefore exact for the normalized boundary:

- **denominator:** every eligible `IMPROVED_200_TRIES` `generalization` row;
- **numerator:** the subset with `generalization_lifecycle.generated_filter_passed = true`;
- **proved transition:** generated source compiled and executed, its report was collected, and generated
  filtering accepted it, before reduction; and
- **excluded later state:** `final_usable` and `generated_pit_collected` are not part of this measure.

## Classified mapping matrix

**Normalized measure:** generated-filter passage among eligible `IMPROVED_200_TRIES` generalization
attempts, measured before test-suite reduction.

| Field | Controlled mapping | RepoReapers mapping |
|---|---|---|
| Corpus/input | `controlled` / `postgres_dev` | `real-world` / `postgres_reporeapers_rq6_v7` |
| Revision evidence | Seven recorded revisions; latest object missing; `86c6c6e…` is latest recoverable representative | Three eligible revisions; latest `c58fcd3a…` is representative |
| Eligibility | `project.use_test_generalization` | Accepted RQ6 eligibility CTE: requested generalization, baseline setup/build gate, and executed-test exclusions |
| Variant | `IMPROVED_200_TRIES` | `IMPROVED_200_TRIES` |
| Entity | One `generalization` row per attempted candidate | One `generalization` row per attempted candidate |
| Denominator predicate | All retained variant rows in controlled projects | All retained variant rows in eligible projects |
| Numerator predicate | Successful generation, generated build, generated execution, per-generalization report collection and filtering tasks, with no rejected generated filter | `generalization_lifecycle.generated_filter_passed` |
| Persisted verdict | Legacy flag plus task/filter evidence | Typed monotone lifecycle plus filter/task evidence |
| Numerator / denominator | 11,597 / 13,836 | 1,615 / 5,356 |
| Share | 83.8% | 30.2% |
| Source contradictions | Zero included-with-reject or included-without-required-transition rows | Zero passed-with-reject or passed-without-prior-transition rows |
| Source classification | **Qualified**: exact for the frozen rows under the explicit predicate; representative source does not cover every producer revision and legacy persistence is weaker | **Exact** for the declared pre-reduction lifecycle boundary; mixed revisions still qualify corpus-level interpretation |

**Combined classification: qualified descriptive comparison.** Both sides measure the same entity-level
transition and retain their full attempt denominators. The comparison is not paired or causal because
project populations, eligibility gates, producer revisions, and pre-emission instrumentation differ.

Thesis-safe interpretation:

> Among candidate `Improved (200 tries)` generalizations attempted in each independently selected corpus,
> 83.8% in the controlled corpus and 30.2% in RepoReapers reached accepted generated-test filtering before
> reduction. This descriptive difference spans different projects, producer revisions, eligibility, and
> instrumentation; it does not estimate a paired project effect or attribute the gap to one mechanism.

Rejected or unmappable alternatives:

- **Generic flag to generic flag:** rejected. Real-world `is_included` has 1,583 rows and is not an
  accepted lifecycle boundary.
- **Controlled flag to source creation:** rejected. The controlled flag includes later filtering while
  RQ6 source creation has 2,057 rows.
- **Controlled flag to compilation or execution:** rejected for the retained comparison. Those are
  intermediate transitions and do not equal the controlled RQ5 table row.
- **Controlled flag to final usability:** unmappable. RQ6 final usability requires generalized PIT in the
  later reduction phase; RQ5 inclusion does not record that requirement.
- **Stage ordinal alignment:** rejected. The same task transitions moved from stages 27-31 to 23-27 and
  coverage/mutation moved into a separate phase.

## Metric and provenance support audit

The current API already preserves:

- stable semantic metric keys and exact values;
- typed count/share kinds;
- an entity level and declared input role through `MetricPopulation`;
- numerator and denominator metric keys with arithmetic and population validation;
- the producing function, SQL text, file, line, source commit, package version, and dirty state through
  `Provenance`; and
- stable aggregate macro names derived from metric keys.

It does **not** preserve the normalized mapping identity or its exact/qualified/unmappable classification.
`Metric` has no mapping field, and `_metric_entry` serializes only value kind, population, and operand
keys beyond ordinary source provenance. Prose is not sufficient because generated macros and manifest
consumers cannot recover the qualification from it.

Implementation therefore needs one small typed metadata extension shared by all six metrics. At minimum
it must carry a stable mapping key and classification; the manifest must serialize both. The report-level
prose can state the detailed qualification, while controlled and real-world metric provenance remains
bound to each adapter's actual query. Existing `MetricPopulation`, operand relations, `Provenance`, and
macro naming require no replacement.

## Operator decision

Approved on 2026-08-22: implement the qualified pre-reduction comparison and the minimal typed mapping
identity/classification extension exactly as audited above. Do not publish generic-flag or final-usability
comparisons.
