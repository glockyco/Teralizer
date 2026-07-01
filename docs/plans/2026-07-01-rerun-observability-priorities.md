---
title: Rerun Observability Priorities
type: audit
status: active
created: 2026-07-01
parent: 2026-06-26-teralizer-overview
---

# Rerun Observability Priorities

Purpose: decide which pipeline telemetry is worth adding because it turns rerun evidence into
better implementation choices. The goal is not complete tracing. Add structured facts only at
existing decision points where a later query changes what we build next.

## Decision

Do **not** implement every possible telemetry idea now. Implement the small subset that answers
near-term planning questions with concrete evidence:

1. **MUT-resolution provenance first** — required before static MUT-id work can separate true
   no-input APIs from getter/inspector mis-targeting.
2. **SPF/spec-extraction rollups second** — required to make `ANALYZE_JPF: all assertions were
   excluded` actionable.
3. **Build/report failure codes as stage-local cleanups** — useful when touching
   `BUILD_PROJECT_INSTRUMENTED`, `COLLECT_JUNIT_REPORTS_ORIGINAL`, or
   `BUILD_PROJECT_GENERALIZED`, but not a prerequisite for static MUT-id.
4. **Assertion semantic categories before assertion-support work** — especially for
   `assertNotNull` and `fail`; defer broad matcher parsing until counts justify it.
5. **Generated-property lifecycle flags when evaluating end-to-end yield** — useful because
   `generalization.is_included` is not the same as final usable generated property.

Avoid a generic observability/event framework. The pipeline already has durable entities
(`project`, `test`, `assertion`, `generalization`, `task`, `filter_result`); add stable reason
codes, provenance columns, or narrow companion tables to those entities.

## Evidence from the current rerun snapshot

The partial `postgres_reporeapers_rerun` snapshot shows why the telemetry matters:

| Signal | Current evidence | Why current data is insufficient |
|---|---:|---|
| `MissingValueFilter` rejects | 31,776 assertions | Does not say whether a producer call was visible, not extracted, ambiguous, a library target, or an inspector/getter mis-target. |
| `ParameterTypeFilter` rejects | 26,294 assertions | Reason is mostly no parameters / null metadata; cannot distinguish true no-input methods from incorrectly picked getter-like MUTs. |
| `ANALYZE_JPF` project failures | 208 projects | Project task says all assertions were excluded, but the lower-level SPF causes are only recoverable by joining task/assertion rows and parsing free text. |
| Assertion-level SPF/instrumentation exclusions | 199 assertions | Useful causes exist but are free-text (`_target_` type, native peer, model gap, depth limit, listener NPE). |
| `BUILD_PROJECT_GENERALIZED` failures | 10 projects / 161 generated properties | The concrete cause is Java-8 generated syntax under project `-source 1.5/1.6/1.7`, but that is only visible by reading command logs. |
| Generalized report collection failures | 37 generated properties | The DB does not distinguish missing report file, wrong report naming, and report found but testcase unmatched as stable codes. |
| Unsupported assertions | `assertNotNull` 2,836; `fail` 2,230 | Counts alone do not say whether these are nullness or exception-control-flow assertions that can be modeled safely. |

The rerun snapshot is pre-string-branch for most projects, so use these counts as barrier-shape
evidence, not as a measurement of the latest implementation.

## Tier A — add before or during static MUT-id

### A1. MUT-resolution provenance

**Question enabled:** Is a `ParameterType(no parameters)` reject a true no-input API, or did MUT-id
pick a getter/inspector instead of the producer/input-side call?

Record for each assertion after analysis:

- `mut_resolution_status`: `RESOLVED`, `ABSTAINED`, `UNRESOLVED_DECLARATION`,
  `LIBRARY_TARGET`, `AMBIGUOUS`.
- `mut_resolution_signal`: `DIRECT_ACTUAL_CALL`, `LOCAL_VARIABLE_PRODUCER`,
  `FIELD_PRODUCER`, `SUBEXPRESSION_PRODUCER`, `INSPECTOR_UNWRAP`,
  `ASSERT_THROWS_LAMBDA`, `FALLBACK_LCBA`, `NONE`.
- `mut_resolution_abstain_reason`: `NO_VISIBLE_CALL`, `MULTIPLE_PRODUCERS`,
  `CROSS_METHOD_SLICE`, `UNRESOLVED_RECEIVER_TYPE`, `NON_CUT_TARGET`,
  `LIBRARY_DECLARATION`, `UNSUPPORTED_ASSERTION_SHAPE`.
- Resolved/candidate call facts: source snippet, method name, declaring type, parameter types,
  return type, candidate count, inspector method name, receiver-producer method name, focal type,
  focal-type source.

**Tangible evidence:**

- Count newly visible single-producer assertions before implementing each static resolver branch.
- Quantify getter-like / inspector-like picks that explain `ParameterType(no parameters)`.
- Measure abstention causes instead of treating all `MissingValue` as one bucket.
- Spot-check newly resolved assertions from a provenance query rather than manual grepping.

**Implementation shape:** narrow companion table, e.g. `mut_resolution_observation(assertion_id,
status, signal, abstain_reason, candidate_count, resolved_call_source, resolved_method_name,
resolved_declaring_type, resolved_parameter_types, resolved_return_type, inspector_unwrapped,
inspector_method_name, receiver_producer_method_name, focal_type, focal_type_source)`. A JSON
candidate-list column is acceptable for diagnostics; avoid making the pipeline depend on parsing it.

### A2. Independent type eligibility for resolved candidates

**Question enabled:** If MUT-id were fixed, which `MissingValue` assertions would pass type filters
next?

Record candidate type classes independently of filter execution:

- `candidate_param_count`.
- parameter classes: `SUPPORTED_SCALAR`, `SUPPORTED_STRING`, `UNSUPPORTED_OBJECT`,
  `UNSUPPORTED_ARRAY`, `NO_PARAMETERS`, `UNKNOWN`.
- return class: `SUPPORTED_SCALAR`, `SUPPORTED_STRING`, `UNSUPPORTED_OBJECT`,
  `UNSUPPORTED_ARRAY`, `VOID`, `UNKNOWN`.

**Tangible evidence:**

- Net static-MUT reach after type shadowing.
- Size of the receiver/stateful setup wall versus string/object type support.
- Whether string support reduced the right `ReturnType`/`ParameterType` buckets after rerun.

**Implementation shape:** columns on the MUT-resolution observation, not another filter pass.

## Tier B — add while touching SPF/spec extraction or generated build stages

### B1. SPF/spec-extraction rollup for `ANALYZE_JPF`

**Question enabled:** Why did a project report `all assertions were excluded during SPF/spec
extraction`?

At project/test rollup time, record:

- assertions scheduled for SPF;
- assertions instrumented;
- assertions whose JPF execution succeeded/failed;
- assertions with input/output specs written;
- failure counts by stable cause.

Stable cause examples: `SEARCH_DEPTH_LIMIT`, `PC_SIZE_LIMIT`, `UNCAUGHT_EXCEPTION_PATH`,
`MISSING_NATIVE_PEER`, `MISSING_JPF_MODEL_METHOD`, `MISSING_JPF_MODEL_CLASS`,
`UNSUPPORTED_BYTECODE`, `LISTENER_BUG`, `NO_INPUT_SPEC`, `NO_OUTPUT_SPEC`.

**Tangible evidence:**

- Rank native-peer/model work against depth/PC/resource tuning.
- Verify whether listener bugs are still present after fixes.
- Turn project-level `ANALYZE_JPF` failures into assertion-level work items.

**Implementation shape:** add stable reason codes to assertion-level failed JPF tasks, then derive the
project rollup. Do not put only a summarized free-text string in the project task.

### B2. Structured build diagnostics for instrumented and generalized builds

**Question enabled:** Did the build fail because of our rewrite/generation, a project dependency, a
compiler source-level mismatch, or Maven/Gradle infrastructure?

For `BUILD_PROJECT_INSTRUMENTED` and `BUILD_PROJECT_GENERALIZED`, record:

- build tool, phase/goal, exit code;
- compiler `source`, `target`, `release` when discoverable;
- first compiler diagnostic file/line/message;
- stable failure code: `INSTRUMENTED_SOURCE_COMPILE_ERROR`, `GENERATED_SOURCE_LEVEL_TOO_NEW`,
  `MISSING_DEPENDENCY`, `MAVEN_PLUGIN_FAILURE`, `TEST_COMPILE_OUTPUT_MISSING`,
  `OTHER_COMPILE_FAILURE`.

For generalized builds, also record generated-source feature flags:

- `generated_source_required_level`;
- `generated_uses_lambdas`;
- `generated_uses_method_references`;
- `generated_uses_diamond`.

**Tangible evidence:**

- Directly sizes the Java-8 jqwik-source compatibility blocker.
- Separates fixable generator compatibility from external dependency rot.
- Lets a rerun report `BUILD_PROJECT_GENERALIZED` causes without reading command logs.

**Implementation shape:** parse the command output once when the task fails; store the stable code and
the first diagnostic in `task.info` JSON or a companion `task_diagnostic` table.

### B3. JUnit report collection diagnostics

**Question enabled:** Is `COLLECT_JUNIT_REPORTS_ORIGINAL` / generalized report collection failing
because no report exists, naming differs, or the testcase mapping is wrong?

Record:

- `report_collection_status`: `FOUND_EXACT`, `FOUND_ALTERNATIVE`, `MISSING_REPORT_FILE`,
  `FOUND_REPORT_NO_MATCHING_TESTCASE`, `MULTIPLE_MATCHING_TESTCASES`,
  `UNSUPPORTED_REPORT_LAYOUT`;
- expected report path;
- alternative report paths tried;
- number of discovered report files;
- expected testcase name;
- sample testcase names seen;
- provider: `SUREFIRE`, `FAILSAFE`, `GRADLE_TEST`, `CUSTOM`, `UNKNOWN`.

**Tangible evidence:**

- Separates report-layout engineering from real test execution failure.
- Gives concrete projects for report-name matching fixes.
- Explains generalized report failures such as missing generated Surefire paths versus unmatched
  testcase names.

**Implementation shape:** stable code + compact detail JSON at report collection failure.

## Tier C — add before assertion-support work

### C1. Assertion semantic categories

**Question enabled:** Which unsupported assertions are cheap and sound to support next?

Record at assertion discovery time:

- semantic kind: `EQUALITY`, `BOOLEAN_TRUE`, `BOOLEAN_FALSE`, `NULLNESS_NOT_NULL`,
  `NULLNESS_NULL`, `INEQUALITY`, `SAMENESS`, `ARRAY_EQUALITY`, `HAMCREST_MATCHER`,
  `ASSERTJ_MATCHER`, `FAIL_SENTINEL`, `UNKNOWN`;
- argument shape: literal/null/method-call/field/read/composite;
- for `fail`, context: `TRY_BLOCK_EXPECTING_EXCEPTION`, `CATCH_BLOCK_SHOULD_NOT_REACH`,
  `GUARD_BRANCH`, `UNKNOWN`.

**Tangible evidence:**

- `assertNotNull` can be sized as nullness-over-method-result versus object-state cases.
- `fail` can be separated into exception-test patterns versus unreachable guards.
- `assertThat` can be split by matcher family before implementing a broad matcher parser.

**Implementation shape:** assertion-analysis metadata, then filter reasons reference the semantic kind.

## Tier D — add when evaluating final end-to-end yield

### D1. Generated-property lifecycle flags

**Question enabled:** How many generated properties are actually usable end to end?

Record per generalization:

- `generated_source_created`;
- `generated_project_compiled`;
- `generated_tests_executed`;
- `generated_report_collected`;
- `generated_filter_passed`;
- `generated_pit_collected`;
- `final_usable`;
- `final_failure_stage`;
- `final_failure_code`.

**Tangible evidence:**

- Avoids treating `generalization.is_included` as final success when the project later fails
  `BUILD_PROJECT_GENERALIZED`.
- Produces a stable end-to-end yield table without reconstructing lifecycle from tasks.
- Makes before/after comparisons of generator changes cleaner.

**Implementation shape:** either columns on `generalization` or a `generalization_lifecycle` companion
table populated by the existing pipeline stages.

## Deferred or not worth doing now

| Idea | Decision | Reason |
|---|---|---|
| Generic event/observability framework | Defer | Too much machinery; existing entities already define useful boundaries. |
| Full candidate-list JSON for every assertion by default | Partial | Useful for diagnostics, but store compact counts/signals first; enable full JSON only if storage/runtime stays acceptable. |
| Fine-grained solver/path metrics everywhere | Defer | Useful when tuning SPF resources, not needed for choosing static MUT-id versus filter/assertion work. |
| PIT/oracle provenance telemetry | Defer until PIT_ORIGINAL/ensemble work | Valuable for ensemble MUT-id, but not required for the static no-PIT plan. |
| Broad matcher AST model for all `assertThat` variants | Defer | First collect matcher-family counts; implement narrow families only when evidence is clear. |

## Recommended order of work

1. Add MUT-resolution observation table/fields and update the static MUT-id Task 1 diagnostic to use it.
2. Add independent candidate type eligibility alongside MUT provenance.
3. Add SPF/spec-extraction stable failure codes and project rollups.
4. Add build/report failure codes opportunistically when working on the highlighted stages.
5. Add assertion semantic categories before implementing `assertNotNull` / `fail` support.
6. Add generated-property lifecycle flags before making end-to-end yield claims from future reruns.

This keeps observability tied to decisions: each field either ranks a concrete improvement, sizes its
shadowing, or verifies that a change moved the intended barrier.
