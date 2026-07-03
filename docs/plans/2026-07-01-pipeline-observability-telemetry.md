---
title: Pipeline Observability Telemetry
type: spec
status: draft
created: 2026-07-01
parent: 2026-06-26-teralizer-overview
---

# Pipeline Observability Telemetry

Design a small set of structured telemetry records that make rerun analysis evidence-driven. The
telemetry answers which barrier to work on next, whether an implementation moved that barrier, and
whether later stages failed for tool reasons or corpus reasons. It is additive: existing pipeline
entities and free-text diagnostics remain valid, but future runs also emit stable reason codes and
provenance that analysis scripts can query directly.

Evidence and prioritization live in `2026-07-01-rerun-observability-priorities`; this spec defines
what to build.

## Relationship to active work

- `2026-07-02-static-mut-id-fusion` implements `mut_resolution_observation` (Task 1/9) and uses
  candidate type eligibility for its ranking and spike verification.
- `2026-06-30-partial-sound-string-support` should use candidate type eligibility during corpus
  verification to separate newly resolved String opportunities from receiver/stateful setup blockers.
- `2026-06-28-mut-id-targeting-and-coverage` supplies the MUT-id targeting evidence this spec makes
  directly queryable.
- `2026-06-26-applicability-barriers` names the broader RQ6 barrier classes; this spec supplies the
  structured observations needed when revisiting those barriers.
- `2026-06-28-pipeline-architecture-review` identifies the SPF/build/report silent-failure surfaces;
  this spec defines the stage-local diagnostics for them.
- `2026-06-28-generation-coverage-telemetry` remains the generator-internal coverage contract; this
  spec covers cross-stage lifecycle and failure attribution.

## Goals

- Make static MUT-id work measurable: distinguish true unidentified MUTs, visible-but-not-extracted
  producers, getter/inspector mis-targets, library targets, ambiguous producers, and no-visible-call
  assertions.
- Make type-filter shadowing measurable before and after MUT-id changes: record candidate parameter
  and return type eligibility even when filters defer.
- Make `ANALYZE_JPF: all assertions were excluded during SPF/spec extraction` actionable by rolling
  assertion-level SPF/spec failures into stable project/test summaries.
- Make highlighted infrastructure failures queryable without log parsing:
  `BUILD_PROJECT_INSTRUMENTED`, `COLLECT_JUNIT_REPORTS_ORIGINAL`, and
  `BUILD_PROJECT_GENERALIZED`.
- Make assertion-support prioritization evidence-driven for `assertNotNull`, `fail`, and later
  matcher assertions.
- Make generated-property yield unambiguous: separate source generation, compilation, execution,
  report collection, filtering, PIT collection, and final usability.

## Non-goals

- No generic event framework.
- No attempt to store every log line in the database.
- No broad matcher implementation in this spec.
- No PIT/oracle provenance until PIT_ORIGINAL / ensemble MUT-id work starts.
- No change to the current soundness policy; telemetry observes decisions, it does not make unsafe
  decisions acceptable.

## Design principles

1. **Stable code first, free text second.** Every analysis-relevant outcome gets an enum-like code;
   human-readable detail remains supplemental.
2. **Write telemetry where the fact is known.** Do not reconstruct compiler source level, report
   lookup status, or MUT candidate state later if the producing task already has that information.
3. **Keep records additive and nullable.** Old databases stay readable. New columns/tables are empty
   for historical runs.
4. **Prefer narrow companion tables over overloaded JSON.** Use JSON for diagnostic details that do
   not drive joins; use columns for fields used by ranking and acceptance checks.
5. **Make shadowing explicit.** A deferred filter should say what it depends on; candidate type
   eligibility should be available even if the final filter decision is not.

## Data model

### `mut_resolution_observation`

One row per assertion after MUT analysis. This table is the foundation for static MUT-id
diagnostics. **Schema authority: `2026-07-02-mut-id-confidence-fusion` §Data model** — the fusion
spec defines the columns and enums; the summary here names only the design decisions the rest of
this spec depends on:

- `status` (pipeline consequence: `RESOLVED`, `CHARACTERIZATION_ONLY`, `NONE`) and
  `confidence_tier` (`T1_PROVEN` … `T5_NONE`) are **orthogonal** columns — a library-target pick
  can be T1-proven yet characterization-only. The former `ABSTAINED`/`AMBIGUOUS` terminal states
  demote to ranked guesses with `candidate_details` populated.
- The single `signal` column splits into `deciding_signal` (who decided) and
  `corroborating_signals` (who agreed — the T2 promotion evidence).
- `focal_type_source` uses the fusion vocabulary (`PATH_AND_NAME`, `NAME_ONLY`, `PATH_ONLY`,
  `NONE`); receiver dominance is dropped.
- `oracle_agreement` (`AGREED`, `REFUTED`, `ABSENT`; nullable) is reserved so the runtime tier
  (`2026-06-27-ensemble-mut-identification`) has a home before PIT_ORIGINAL is enabled.

Analysis enabled:

- `MissingValue` split by resolver failure mode and confidence tier.
- Getter-like / inspector-like mis-target rate (`shallow_inspector_pick`).
- Tier funnel: "X% proven (T1), Y% corroborated (T2), Z% single-weak (T3), W% guessed (T4)" —
  the per-assertion threat-to-validity story.
- Manual spot-check sample selection stratified by tier.

### Candidate type eligibility

Store on `mut_resolution_observation` or a one-to-one companion table.

| Column | Type | Meaning |
|---|---|---|
| `candidate_param_count` | integer nullable | Resolved/candidate MUT parameter count. |
| `candidate_param_type_classes` | text[] / jsonb | Type classes for parameters. |
| `candidate_return_type_class` | text enum nullable | Return type class. |
| `has_receiver_input` | boolean nullable | Whether an instance receiver could be an input source. |
| `receiver_type_class` | text enum nullable | Receiver class support category. |

Type classes:

- `SUPPORTED_SCALAR`
- `SUPPORTED_STRING`
- `UNSUPPORTED_OBJECT`
- `UNSUPPORTED_ARRAY`
- `NO_PARAMETERS`
- `VOID`
- `UNKNOWN`

Analysis enabled:

- Static MUT-id reach after type shadowing.
- True no-input APIs versus mis-targeted no-arg inspectors/getters.
- Residual object/receiver/stateful setup wall.
- String-support before/after effect on `ReturnType` and `ParameterType`.

### `filter_result` additions

Add stable metadata to existing filter decisions.

| Column | Type | Meaning |
|---|---|---|
| `reason_code` | text nullable | Stable code for the decision. |
| `depends_on` | text nullable | Cause of `DEFER`, e.g. `MISSING_MUT`, `EXCLUDED_TEST`, `UNSUPPORTED_ASSERTION`. |
| `detail_json` | jsonb nullable | Small structured details for analysis/debugging. |

Example `reason_code` values:

- `MISSING_TESTED_FILE`
- `MISSING_TESTED_CLASS`
- `MISSING_TESTED_METHOD`
- `MISSING_TESTED_PARAMS`
- `NO_GENERALIZABLE_PARAMETERS`
- `UNSUPPORTED_PARAMETER_TYPE`
- `UNSUPPORTED_RETURN_TYPE`
- `UNSUPPORTED_ASSERTION_ASSERT_NOT_NULL`
- `UNSUPPORTED_ASSERTION_FAIL`
- `UNSUPPORTED_ASSERTION_ASSERT_THAT`
- `EXCLUDED_PARENT_TEST`

Analysis enabled:

- Stable blocker grouping without parsing free-text `reason`.
- Clear DEFER dependencies.
- First-reject and shadowed-blocker queries that survive wording changes.

### `assertion_semantics`

One row per assertion discovered by Spoon analysis.

| Column | Type | Meaning |
|---|---|---|
| `assertion_id` | FK | Assertion. |
| `semantic_kind` | text enum | Assertion meaning. |
| `argument_shape` | text enum / jsonb | Coarse shape of relevant arguments. |
| `fail_context` | text enum nullable | Context for `fail`. |
| `matcher_family` | text nullable | `HAMCREST`, `ASSERTJ`, or specific family when known. |
| `matcher_name` | text nullable | Matcher method/type name when known. |

`semantic_kind` values:

- `EQUALITY`
- `BOOLEAN_TRUE`
- `BOOLEAN_FALSE`
- `NULLNESS_NOT_NULL`
- `NULLNESS_NULL`
- `INEQUALITY`
- `SAMENESS`
- `ARRAY_EQUALITY`
- `HAMCREST_MATCHER`
- `ASSERTJ_MATCHER`
- `FAIL_SENTINEL`
- `UNKNOWN`

`fail_context` values:

- `TRY_BLOCK_EXPECTING_EXCEPTION`
- `CATCH_BLOCK_SHOULD_NOT_REACH`
- `GUARD_BRANCH`
- `UNKNOWN`

Analysis enabled:

- Size `assertNotNull` as nullness over method result versus object-state assertions.
- Split `fail` into expected-exception control-flow versus unreachable guard failures.
- Count matcher families before implementing broad `assertThat` support.

### `task_diagnostic`

One row per failed task when the task can provide a stable cause. This complements, not replaces,
`task.info`.

| Column | Type | Meaning |
|---|---|---|
| `task_id` | FK | Failed task. |
| `project_id` | FK nullable | Denormalized owner. |
| `test_id` | FK nullable | Denormalized owner. |
| `assertion_id` | FK nullable | Denormalized owner. |
| `generalization_id` | FK nullable | Denormalized owner. |
| `stage` | text | Task stage. |
| `reason_code` | text enum | Stable cause. |
| `detail_json` | jsonb nullable | Stage-specific details. |
| `first_error_file` | text nullable | File path for compiler/report diagnostics. |
| `first_error_line` | integer nullable | Line for compiler diagnostics. |
| `first_error_message` | text nullable | First diagnostic message. |

SPF/JPF `reason_code` examples:

- `SEARCH_DEPTH_LIMIT`
- `PC_SIZE_LIMIT`
- `UNCAUGHT_EXCEPTION_PATH`
- `MISSING_NATIVE_PEER`
- `MISSING_JPF_MODEL_METHOD`
- `MISSING_JPF_MODEL_CLASS`
- `UNSUPPORTED_BYTECODE`
- `LISTENER_BUG`
- `NO_INPUT_SPEC`
- `NO_OUTPUT_SPEC`

Build `reason_code` examples:

- `INSTRUMENTED_SOURCE_COMPILE_ERROR`
- `GENERATED_SOURCE_LEVEL_TOO_NEW`
- `MISSING_DEPENDENCY`
- `MAVEN_PLUGIN_FAILURE`
- `TEST_COMPILE_OUTPUT_MISSING`
- `OTHER_COMPILE_FAILURE`

Report collection `reason_code` examples:

- `FOUND_EXACT`
- `FOUND_ALTERNATIVE`
- `MISSING_REPORT_FILE`
- `FOUND_REPORT_NO_MATCHING_TESTCASE`
- `MULTIPLE_MATCHING_TESTCASES`
- `UNSUPPORTED_REPORT_LAYOUT`

Analysis enabled:

- `BUILD_PROJECT_INSTRUMENTED` and `BUILD_PROJECT_GENERALIZED` failure breakdowns by stable cause.
- Generalized Java-source-level mismatch counts without reading command logs.
- `COLLECT_JUNIT_REPORTS_ORIGINAL` and generalized report-discovery failure causes.
- Assertion-level JPF cause rollups feeding project-level `ANALYZE_JPF` summaries.

### `jpf_extraction_summary`

One row per project/test JPF analysis aggregation.

| Column | Type | Meaning |
|---|---|---|
| `project_id` | FK | Project. |
| `test_id` | FK nullable | Null for project-level rollup, set for test-level rollup. |
| `assertions_scheduled` | integer | Assertions entering SPF. |
| `assertions_instrumented` | integer | Assertions with instrumentation generated. |
| `assertions_jpf_succeeded` | integer | Assertions whose JPF task succeeded. |
| `assertions_jpf_failed` | integer | Assertions whose JPF task failed. |
| `assertions_with_input_spec` | integer | Assertions with input spec. |
| `assertions_with_output_spec` | integer | Assertions with output spec. |
| `assertions_with_complete_spec` | integer | Assertions with both specs. |
| `failure_counts` | jsonb | Stable SPF/JPF cause counts. |

Analysis enabled:

- Direct explanation of `ANALYZE_JPF: all assertions were excluded`.
- Project-level ranking by missing native peers, model gaps, depth/resource limits, listener bugs.
- Before/after checks for SPF/model-class improvements.

### Output-spec degeneracy (`assertion.output_spec_class`)

One nullable column on `assertion`, written where the spec files are written
(`SpecificationExtractor` call in `JpfExecutionTask`, or `JpfAnalysisTask` alongside
`output_model_statistics`): `SYMBOLIC` (output model contains ≥1 variable), `CONSTANT` (lone
constant), `NULL_CONCRETE` (no symbolic return attr — value concretized through unmodeled
library/native code), `EXCEPTION` (captured throw). Trivially derivable from the output model;
`output_model_statistics` cannot distinguish these (`operationCount = 0` for null, lone-constant,
and lone-variable alike).

Analysis enabled:

- Direct measurement of silent concretization (`2026-06-28-pipeline-architecture-review` D-1).
- Realized-vs-attempted value of expression-slice recipes (R1 gate,
  `2026-07-02-recipe-seam-review`): a chain whose tail concretizes yields `NULL_CONCRETE`.
- Wrong-pick quality signal for fusion tiers: incoherent/shallow picks skew to
  `CONSTANT`/`NULL_CONCRETE`.

### `build_environment_observation`

One row per project build stage where compiler configuration is discoverable.

| Column | Type | Meaning |
|---|---|---|
| `project_id` | FK | Project. |
| `stage` | text | Build stage. |
| `build_tool` | text enum | `MAVEN`, `GRADLE`, `UNKNOWN`. |
| `compiler_source` | text nullable | Maven/Gradle source level. |
| `compiler_target` | text nullable | Maven/Gradle target level. |
| `compiler_release` | text nullable | Java release flag. |
| `generated_source_required_level` | text nullable | Minimum source level required by generated tests. |
| `generated_uses_lambdas` | boolean nullable | Generated source feature. |
| `generated_uses_method_references` | boolean nullable | Generated source feature. |
| `generated_uses_diamond` | boolean nullable | Generated source feature. |

Analysis enabled:

- Detect projects where jqwik generation will fail before building.
- Decide whether to emit source-compatible tests or classify pre-Java-8 test-source projects as unsupported.
- Explain `BUILD_PROJECT_GENERALIZED` failures with DB data.

### `generalization_lifecycle`

One row per generated property.

| Column | Type | Meaning |
|---|---|---|
| `generalization_id` | FK | Generated property. |
| `generated_source_created` | boolean | Source file written. |
| `generated_project_compiled` | boolean | Generalized project build passed. |
| `generated_tests_executed` | boolean | Generated test execution ran. |
| `generated_report_collected` | boolean | JUnit report collected and mapped. |
| `generated_filter_passed` | boolean | Generalization filters accepted it. |
| `generated_pit_collected` | boolean | PIT collection completed, when enabled. |
| `final_usable` | boolean | End-to-end usable generated property. |
| `final_failure_stage` | text nullable | First lifecycle stage that failed. |
| `final_failure_code` | text nullable | Stable cause from `task_diagnostic` / filters. |

Analysis enabled:

- Final generated-property yield without reconstructing task state.
- Distinguish generated-but-never-built from executed-and-failing properties.
- Before/after comparison of generator changes.

## Pipeline integration points

### Test analysis

`TestAnalysisTask` writes `mut_resolution_observation` and `assertion_semantics` after Spoon analysis.
The resolver produces structured provenance regardless of whether it resolves a MUT. Filter code then
uses the same observation instead of re-deriving why values are missing.

### Filtering

Filters continue writing `filter_result`, but add `reason_code`, `depends_on`, and `detail_json`.
Free-text `reason` remains for humans.

### JPF execution and analysis

`JpfInstrumentationTask`, `JpfExecutionTask`, and `JpfAnalysisTask` write assertion-level
`task_diagnostic` rows. `JpfAnalysisTask` also writes `jpf_extraction_summary` rollups after assertion
SPF tasks finish.

### Build tasks

Build tasks parse first compiler diagnostics and source/target/release settings where practical.
Generalized builds also record generated source feature flags. Failure details go into
`task_diagnostic`; environment facts go into `build_environment_observation`.

### JUnit report collection

`JunitDataCollectionTask` records report lookup status, expected paths, attempted alternatives,
provider, and testcase matching details in `task_diagnostic.detail_json`.

### Test generalization

`TestGeneralizationTask` creates `generalization_lifecycle` rows when files are generated. Later
build, execution, report, filtering, and PIT stages update the lifecycle row. A failed project-level
stage updates every affected generated property with the first failing stage/code.

## Analysis outputs

The rerun snapshot report should be able to answer these queries without reading command logs:

- MissingValue by MUT-resolution status/signal/abstain reason.
- ParameterType no-parameters split by true no-input API versus getter/inspector mis-target.
- Static MUT-id expected reach after candidate type eligibility.
- `ANALYZE_JPF` failures by stable assertion-level cause.
- `BUILD_PROJECT_INSTRUMENTED` failures by compiler/project/tool cause.
- `COLLECT_JUNIT_REPORTS_ORIGINAL` failures by report discovery status.
- `BUILD_PROJECT_GENERALIZED` failures by source-level compatibility and first compiler diagnostic.
- `assertNotNull` / `fail` unsupported assertions by semantic shape.
- Generated properties by lifecycle category and final usable count.

## Acceptance criteria

A telemetry implementation satisfies this spec when:

- Schema migrations are additive and old databases remain readable by existing analysis scripts.
- New enum values are centralized in Java code and documented near the writer.
- A disposable rerun can populate MUT-resolution observations for every analyzed assertion.
- Static MUT-id diagnostics can report `MissingValue` buckets and candidate type eligibility from DB
  queries only.
- `ANALYZE_JPF` project failures include a rollup of assertion-level SPF/spec causes.
- Generalized build failures caused by Java-8 syntax under pre-Java-8 source levels are visible via a
  DB query without log parsing.
- Original and generalized JUnit report collection failures have stable report-discovery status codes.
- Unsupported `assertNotNull` and `fail` assertions have semantic categories suitable for sizing
  assertion-support work.
- Generated-property yield can be computed from `generalization_lifecycle` without interpreting
  `generalization.is_included` as final success.
- `teralizer.reporeapers_rerun_report` can use the telemetry tables when present and degrade cleanly
  for older databases.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Telemetry becomes a second pipeline with its own bugs. | Keep writers local to the task that already owns the fact; add unit tests for code mapping, not end-to-end observability tests for every stage. |
| JSON details become unqueryable. | Put fields used for ranking in columns; use JSON only for low-frequency diagnostics. |
| Enum drift breaks analysis. | Centralize reason-code constants and use tests that assert expected codes for representative failures. |
| Storage grows too much. | Store compact source snippets and counts by default; full candidate lists can be nullable/diagnostic. |
| Old reruns lack telemetry. | Analysis scripts check table/column existence and fall back to current reconstruction logic. |
| Telemetry masks real fixes. | Every field must answer a ranking, shadowing, or acceptance question; reject fields without a query consumer. |

## Open questions

- Should `mut_resolution_observation` be one row per assertion or one row per assertion per candidate?
  Default: one row per assertion, with compact candidate JSON only when needed.
- Should `task_diagnostic.detail_json` use JSONB in Postgres only, or a text field containing JSON for
  easier Java serialization? Default: JSONB if the migration helper and JDBC stack handle it cleanly;
  otherwise text with validated JSON.
- Should source-level incompatibility be handled by generator fallback or by a support-policy
  exclusion? The telemetry should answer how often each choice matters before implementation.
- Should lifecycle flags live on `generalization` directly? Default: companion table to avoid widening
  the core table until the lifecycle model stabilizes.
