# Completion Notes

## Finalized evidence identity

| Field | Final value |
|---|---|
| Evidence-producing source revision | `d035a6555f1f266bb01de0013b859377052ce204` (clean) |
| Manifest-recording revision | `da0a4f6182575bf0457f0554127c6235c7dc0718` |
| Artifact manifest | `analysis/reports/provenance.json` |
| Artifact manifest SHA-256 | `5510d3caba990491e54cf0cf597a390a3bbce470fc468d73ff16e2bb4820511c` |
| Canonical `rq6` manifest-entry SHA-256 | `4e2cf7962eb7588cc444f06116188d1197b4a619fc65cfcbe8c06b8b9923c74b` |
| Semantic corpus id | `real-world` |
| Physical project-count check | 1,161 expected; 1,161 observed |
| Derived-view revision | `e23ce4b25aa45c733d4b79f0a2178d4e6454d75e6bd69bd8c458e33927c672c9` |
| Scratch consumer revision | `0d18d9228223e49985f62b5598bc6f5f314abb68` |

The source and manifest revisions have different roles. `d035a655` produced the evidence. `da0a4f61` committed the clean provenance manifest without changing the evidence.

A clean regeneration at `d035a655` produced an `rq6` manifest entry byte-for-byte equivalent after canonical JSON sorting to the committed entry. The full manifest file also contains other report entries and is identified by the file checksum above.

## RQ6 artifact manifest

The `rq6` report owns 18 manifest artifacts:

| Artifact id | Producer path |
|---|---|
| `csv/rq6_exclusion_mechanisms` | `build/rq6/rq6_exclusion_mechanisms.csv` |
| `csv/rq6_generalization_funnel` | `build/rq6/rq6_generalization_funnel.csv` |
| `csv/rq6_jpf_exception_causes` | `build/rq6/rq6_jpf_exception_causes.csv` |
| `csv/rq6_mut_choice_sensitivity` | `build/rq6/rq6_mut_choice_sensitivity.csv` |
| `csv/rq6_widening_refusals` | `build/rq6/rq6_widening_refusals.csv` |
| `csv/tab-exclusions-breakdown-extended` | `build/rq6/tab-exclusions-breakdown-extended.csv` |
| `csv/tab-exclusions-filtering-extended` | `build/rq6/tab-exclusions-filtering-extended.csv` |
| `csv/tab-processing-failures` | `build/rq6/tab-processing-failures.csv` |
| `latex/macros/rq6` | `build/macros/rq6.tex` |
| `latex/rq6_exclusion_mechanisms` | `build/rq6_exclusion_mechanisms.tex` |
| `latex/rq6_generalization_funnel` | `build/rq6_generalization_funnel.tex` |
| `latex/rq6_jpf_exception_causes` | `build/rq6_jpf_exception_causes.tex` |
| `latex/rq6_mut_choice_sensitivity` | `build/rq6_mut_choice_sensitivity.tex` |
| `latex/rq6_widening_refusals` | `build/rq6_widening_refusals.tex` |
| `latex/tab-exclusions-breakdown-extended` | `build/tab-exclusions-breakdown-extended.tex` |
| `latex/tab-exclusions-filtering-extended` | `build/tab-exclusions-filtering-extended.tex` |
| `latex/tab-processing-failures` | `build/tab-processing-failures.tex` |
| `md/rq6` | `reports/rq6.md` |

## Metric keys

The finalized report registers 57 metric keys:

- `realworld.applicability_pct`
- `realworld.applicability_projects`
- `realworld.assertions_included`
- `realworld.assertions_included_pct`
- `realworld.assertions_total`
- `realworld.assertions_without_resolution`
- `realworld.eligible_projects`
- `realworld.generalization_attempts`
- `realworld.generalization_unknown_attempt_state`
- `realworld.generalization_validated_pct`
- `realworld.generalizations_emitted`
- `realworld.generalizations_filter_adjudicated`
- `realworld.generalizations_filter_passed`
- `realworld.generalizations_final_usable`
- `realworld.generalizations_reduced`
- `realworld.generalizations_validated`
- `realworld.initial_gate_excluded_projects`
- `realworld.jpf_uncaught_exception_diagnostics`
- `realworld.jpf_uncaught_exception_reclassified`
- `realworld.jpf_uncaught_exception_reclassified_pct`
- `realworld.no_executed_test_excluded_projects`
- `realworld.parameter_type_choice_dependent_lower_bound`
- `realworld.parameter_type_choice_dependent_lower_bound_pct`
- `realworld.parameter_type_choice_observations`
- `realworld.reduction_excluded_baseline_side`
- `realworld.reduction_excluded_projects`
- `realworld.selected_projects`
- `realworld.stage4_projects`
- `realworld.stage_1_2.entering`
- `realworld.stage_1_2.excluded`
- `realworld.stage_1_2.included`
- `realworld.stage_1_2.included_pct`
- `realworld.stage_3.entering`
- `realworld.stage_3.excluded`
- `realworld.stage_3.included`
- `realworld.stage_3.included_pct`
- `realworld.stage_4.entering`
- `realworld.stage_4.excluded`
- `realworld.stage_4.included`
- `realworld.stage_4.included_pct`
- `realworld.stage_5.entering`
- `realworld.stage_5.excluded`
- `realworld.stage_5.included`
- `realworld.stage_5.included_pct`
- `realworld.widening_refusal_concretization`
- `realworld.widening_refusal_concretization_pct`
- `realworld.widening_refusal_exception_divergence`
- `realworld.widening_refusal_exception_divergence_pct`
- `realworld.widening_refusal_exception_path_coverage`
- `realworld.widening_refusal_exception_path_coverage_pct`
- `realworld.widening_refusal_output_not_literal`
- `realworld.widening_refusal_output_not_literal_pct`
- `realworld.widening_refusal_parameters_empty`
- `realworld.widening_refusal_parameters_empty_pct`
- `realworld.widening_refusal_path_coverage`
- `realworld.widening_refusal_path_coverage_pct`
- `realworld.widening_refusals`

## Table row keys

The finalized report registers 63 row keys across eight semantic tables:

### `rq6_exclusion_mechanisms`

- `test.included`
- `test.filter_rejection`
- `test.inline_capability`
- `test.task_exception`
- `assertion.included`
- `assertion.filter_rejection`
- `assertion.build_quarantine`
- `assertion.task_exception`
- `generalization.included`
- `generalization.filter_rejection`
- `generalization.generation_gate`
- `generalization.build_quarantine`
- `generalization.task_exception`

### `rq6_generalization_funnel`

- `attempted`
- `emitted`
- `filter_adjudicated`
- `filter_passed`
- `validated`
- `reduced`
- `final_usable`

### `rq6_jpf_exception_causes`

- `Application exception`
- `JPF native-peer gap`
- `JPF model/field gap`
- `Unparsed`

### `rq6_mut_choice_sensitivity`

- `Candidate detail unavailable`
- `Choice-invariant`
- `Choice-dependent`

### `rq6_widening_refusals`

- `NULL_CONCRETE_OUTPUT_NOT_LITERAL`
- `NULL_CONCRETE_CONCRETIZATION_EVENTS`
- `NULL_CONCRETE_PARAMETERS_EMPTY`
- `NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS`
- `EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK`
- `EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS`

### `tab-exclusions-breakdown-extended`

- `Test`
- `Assertion`
- `Generalization`

### `tab-exclusions-filtering-extended`

- `Test:NonPassingTest`
- `Test:TestType`
- `Test:NoAssertions`
- `Test:DisabledTest`
- `Test:InheritedTestCase`
- `Test:MockingFramework`
- `Assertion:AssertionType`
- `Assertion:ExcludedTest`
- `Assertion:MissingValue`
- `Assertion:ParameterType`
- `Assertion:ReturnType`
- `Assertion:StringOperation`
- `Generalization:NonPassingTest`

### `tab-processing-failures`

- `1 + 2:JUnit execution error during test execution`
- `1 + 2:Spoon execution error during test analysis`
- `1 + 2:JUnit reports not found`
- `1 + 2:timeout exceeded (300 seconds per original test suite)`
- `1 + 2:all assertions excluded due to filter rejections`
- `1 + 2:all assertions excluded due to filter rejections and failures`
- `1 + 2:all tests excluded due to filter rejections and failures`
- `3:Spoon execution error during test instrumentation`
- `3:all assertions excluded due to earlier filter rejections and new failures`
- `4:all generalizations excluded due to filter rejections and failures`
- `5:PIT execution error during mutation testing`
- `5:JaCoCo outputs not found`
- `5:timeout exceeded (3600 seconds during PIT mutation testing)`
- `5:unmutated test suite has failing tests`

## Denominator definitions

Every share metric resolves to one of these typed count metrics in the same `real-world` input role:

| Denominator key | Entity level | Count | Rate metrics |
|---|---:|---:|---|
| `realworld.assertions_total` | Assertion | 180,548 | `realworld.assertions_included_pct` |
| `realworld.eligible_projects` | Project | 584 | `realworld.applicability_pct` |
| `realworld.generalization_attempts` | Generalization | 5,356 | `realworld.generalization_validated_pct` |
| `realworld.jpf_uncaught_exception_diagnostics` | Diagnostic | 1,794 | `realworld.jpf_uncaught_exception_reclassified_pct` |
| `realworld.parameter_type_choice_observations` | Assertion | 65,850 | `realworld.parameter_type_choice_dependent_lower_bound_pct` |
| `realworld.stage_1_2.entering` | Project | 584 | `realworld.stage_1_2.included_pct` |
| `realworld.stage_3.entering` | Project | 182 | `realworld.stage_3.included_pct` |
| `realworld.stage_4.entering` | Project | 176 | `realworld.stage_4.included_pct` |
| `realworld.stage_5.entering` | Project | 98 | `realworld.stage_5.included_pct` |
| `realworld.widening_refusals` | Generalization | 3,298 | `realworld.widening_refusal_concretization_pct`, `realworld.widening_refusal_exception_divergence_pct`, `realworld.widening_refusal_exception_path_coverage_pct`, `realworld.widening_refusal_output_not_literal_pct`, `realworld.widening_refusal_parameters_empty_pct`, `realworld.widening_refusal_path_coverage_pct` |

## Known attempt-state limitation

The generalization funnel observes 5,356 attempted, 2,057 emitted, 2,035 filter-adjudicated, 1,615 filter-passed, 1,615 validated, 1,435 reduced, and 1,435 final-usable generalizations. Of the 180 exclusions between validated and reduced, 32 have an independent matching task record and 148 have unknown attempt state. A later lifecycle failure label does not prove that its stage ran. The final-usable count remains publishable because the report states this limitation and keeps the 148 unknown cases separate.

## Publication compatibility proof

Declaration-driven publication used the committed thesis declaration at `chapters/05-teralizer/publish.toml` from `0d18d922`. It delivered all 24 declared artifacts and no undeclared artifact into a clean scratch checkout. The producer emitted 74 manifest artifacts plus the composed macro artifact; undeclared evidence remained producer-side.

`./scripts/thesis-build` completed with `latexmk -Werror`. The build produced 232 pages and no undefined-reference, undefined-citation, or multiply-defined-label diagnostic. Visual inspection covered affected PDF pages 153, 154, 157, 161, and 162; the generated exclusion tables remained readable without clipped cells or collisions. The real thesis checkout remained unchanged.

## Conditional audit disposition

Persisted codes and focused fixtures close every retained causal explanation. No qualitative audit input, schema, or report artifact is required.
