## Context

`ParameterTypeFilter` currently decides from `assertion.tested_method_call_arguments` and `assertion.tested_method_parameters`. `tested_method_parameters` carries recipe-derived inputs when derivation has a complete source-model path; `tested_method_call_arguments` carries those derived inputs in that path and raw call-site arguments as a fallback otherwise. Neither column describes the complete formal signature of the selected method under test.

`GeneralizableInput.derive` intentionally drops unsupported arguments, skips varargs, and can add inputs from receiver constructors, argument constructors, nested expressions, or lifted locals. `TestAnalysisTask` serializes derived inputs into the recipe-capability fields only when that derivation path is available. Therefore, an empty or non-empty JSON list can mean several different things. It can mean a zero-parameter method, no supported recipe site, a constructor or receiver topology, a skipped vararg, raw call-site arguments, or missing declaration evidence. The list cannot prove the selected method signature.

`MutResolutionObservationMapper` already records declaration evidence when the resolver has a picked `CtMethod`. For that picked declaration, it records `candidate_param_count`, `candidate_param_supported`, and `resolved_parameter_types`. It leaves these declaration-dependent fields null when Spoon has no method declaration. The null state is useful evidence: it means that the declaration needed to classify the selected MUT is absent. It does not mean that the recipe has no supported input or that the method has unsupported parameter types.

The current registered report classifies `ParameterType` rejections from the JSON list in `tested_method_call_arguments`. It treats `[]` as `no_arguments` and any non-empty list as `unsupported_types`. This reproduces the source-filter defect and cannot distinguish a zero-parameter selected MUT from receiver or constructor lifting, or from another recipe derivation edge case. The report must instead join each rejection to the persisted resolver observation for the same assertion and classify it from selected-MUT declaration telemetry, using recipe telemetry only to identify the historical derivation result. The corpus database remains an immutable measurement record.

## Goals / Non-Goals

**Goals:**

- Make future `ParameterType` decisions use declaration evidence to establish that the selected MUT is classifiable, then use explicit recipe-capability evidence for the support decision.
- Preserve dependency semantics by returning `DEFER` when the observation or declaration evidence is absent.
- Reject only when a selected declaration exists and the recipe has no supported generalizable input.
- Allow receiver and constructor lifts to support a recipe even when the selected method has zero formal parameters or only unsupported formal parameters.
- Distinguish zero-parameter selected methods, unsupported selected-MUT parameters, missing declarations, and recipe derivation edge cases in historical reports.
- Keep `ParameterType` as the stable report-facing filter name and preserve stable reason-code identities.
- Keep method signatures separate from constructor inputs, receiver inputs, expression-site inputs, and lifted locals.
- Make the registered report reconstruct historical categories from one canonical observation per rejected assertion.
- Validate the category partition exactly before producing metrics or tables.
- Read historical databases without updating, repairing, or rerunning them.
- Provide an atomic migration and a code-only rollback path.

**Non-Goals:**

- Do not turn `ParameterType` into a formal-signature support gate.
- Do not use `candidate_param_supported` alone to accept or reject a future recipe. It describes the selected declaration and is historical classification evidence, not recipe-capability evidence.
- Do not treat `tested_method_call_arguments` or `tested_method_parameters` as a selected-MUT signature after this change.
- Do not infer selected-MUT parameter support from filter messages, JSON-list emptiness, or serialized assertion text. A recipe list may show recipe capability, but it cannot show the formal signature.
- Do not change resolver ranking, candidate selection, type-capability rules, constructor lifting, receiver lifting, or recipe generation in this change.
- Do not rewrite historical `assertion`, `filter_result`, or `mut_resolution_observation` rows.
- Do not rerun a project, a corpus, or a failed pipeline task.
- Do not create synthetic observations or synthetic filter results for historical reporting.
- Do not make `candidate_count` stand for a method parameter count. It remains the resolver candidate count.

## Decisions

### 1. Define two explicit evidence views

The selected-MUT signature is the formal parameter list of the method declaration represented by the resolver pick. The mapper remains the owner of this declaration evidence. For a picked `CtMethod`, it records the declaration parameter count and ordered qualified parameter types, and it records whether at least one declared parameter supports generated input. `candidate_param_count` and `resolved_parameter_types` must agree on the same declaration. `candidate_param_supported` is false for a declaration with no supported formal parameter and is not interpreted without the declaration count.

The recipe-capability view is the persisted `tested_method_parameters` list when recipe derivation ran. It identifies the inputs that the generator can actually expose. Its entries are not formal selected-MUT parameters. The future filter may inspect the entry types with `TypeCapability.supportsGeneratedInput`, but it must not use the list as signature evidence. A null value means that recipe-capability evidence is unavailable. `tested_method_call_arguments` is not needed for this decision and must not be used as the empty-list branch, because it can contain raw fallback arguments.

A resolver candidate count is not a selected-MUT signature. `candidate_count` can be greater than one before a pick, and it remains resolver-ranking telemetry. `resolved_parameter_types` is the selected declaration evidence. The observation status and no-pick reason remain resolver outcome evidence, not substitutes for a declaration signature.

A null declaration-dependent field is positive missing-declaration evidence. In particular, a null `candidate_param_count`, null `candidate_param_supported`, or null `resolved_parameter_types` means that the declaration required for this classification is unavailable. A row with an incomplete declaration tuple is not treated as an unsupported-parameter result. The report validator rejects contradictory partial combinations instead of silently repairing them.

### 2. Make the future filter defer on missing declaration evidence

Change the future filter construction so `ParameterTypeFilter` receives the assertion's canonical `MutResolutionObservationRecord` and the recipe-derived assertion evidence. The filtering task obtains the same observation by assertion identity. It must not use an unrestricted `fetchAny` when more than one row exists; duplicate observations are a persistence error.

The filter checks declaration availability before it inspects whether the recipe list is empty:

| Evidence | Decision | Stable reason and dependency |
|---|---|---|
| No observation exists | `DEFER` | `MUT_RESOLUTION_NOT_RECORDED`, depends on `MISSING_MUT` |
| Observation exists, but no complete picked `CtMethod` declaration is available | `DEFER` | `MISSING_TESTED_PARAMS`, depends on `MISSING_MUT` |
| Complete declaration exists, but the recipe-derived parameter evidence is null | `DEFER` | `MISSING_TESTED_PARAMS`, depends on `MISSING_MUT` |
| Complete declaration exists and the recipe contains at least one supported derived input | `ACCEPT` | no rejection reason |
| Complete declaration exists and the recipe contains no supported derived input | `REJECT` | `NO_GENERALIZABLE_PARAMETERS` |

The final branch includes an empty recipe list and a non-empty list whose entries are all unsupported. It does not inspect `candidate_param_count` or `candidate_param_supported` to override recipe capability. Thus a zero-formal-parameter selected method can be accepted when a receiver-constructor input is supported, and a method with unsupported formal parameters can be accepted when a supported constructor or receiver lift is in the recipe. The filter rejects only after declaration evidence exists and no supported recipe input remains.

The filter does not parse a resolver message, infer a signature from `no_pick_reason`, or use call-argument-list emptiness. Resolver reason codes remain available to `MissingValueFilter` and report diagnostics. The `ParameterType` producer identity remains `teralizer.processing.filter.ParameterTypeFilter`; report normalization continues to expose the stable name `ParameterType` by removing only the terminal implementation suffix. No alias or renamed filter is introduced.

The existing `NO_GENERALIZABLE_PARAMETERS` identity remains the source-filter reason for the recipe-without-supported-input rejection. `MISSING_TESTED_PARAMS`, `MUT_RESOLUTION_NOT_RECORDED`, and `MISSING_MUT` retain their dependency meanings. Historical detail categories supply finer evidence without changing these producer identities.

### 3. Keep formal parameters separate from lifted recipe inputs

`candidate_param_count` and `resolved_parameter_types` describe only the selected method declaration. They exclude the receiver because an instance receiver is not a formal method parameter. They also exclude constructor arguments used to create a receiver or an object argument, expression-site literals, and locals lifted from the test scope.

`receiver_provenance` and `actual_shape` remain useful topology evidence. They do not change the selected-MUT signature. A constructor receiver can add supported recipe inputs while the selected method has zero formal parameters. An unsupported formal object parameter can produce supported constructor inputs. Conversely, a method with supported formal parameters can yield no supported recipe input when derivation skips varargs or exposes no supported site. The future filter uses the declaration only to decide whether classification may proceed and uses the recipe-capability view for the accept/reject decision.

### 4. Reconstruct historical categories from one canonical observation

The report adds a parameter-rejection relation that starts with the exact `ParameterType` rejection set for the eligible report population. It joins each assertion by `assertion_id` to exactly one persisted resolver observation and checks the observation's project and test identities. The report does not select an arbitrary row. Zero observations produce an explicit missing-observation evidence condition, and duplicate observations fail report validation before publication.

The historical detail partition evaluates these four categories in this order:

1. `missing_declaration`: the observation exists, but no complete picked `CtMethod` declaration is available. This includes null declaration telemetry and resolver outcomes that have no source declaration. The category never means unsupported parameters.
2. `zero_parameters`: the canonical observation has a complete selected declaration with `candidate_param_count = 0`.
3. `unsupported_types`: the canonical observation has a complete selected declaration with `candidate_param_count > 0` and `candidate_param_supported = false`.
4. `derivation_gap`: the canonical observation has a complete selected declaration with `candidate_param_count > 0` and `candidate_param_supported = true`, so the rejection is not explained by the selected declaration. Persisted recipe evidence, when present, is recorded as provenance for the gap. The report does not claim that a constructor or receiver lift was present unless the persisted recipe proves that fact.

The declaration telemetry is the authority for formal-parameter categories. A separate recipe-capability relation validates any available derived-input evidence for `derivation_gap`; it does not populate a formal parameter count or create another category. Absent historical `tested_method_parameters` evidence is recorded as unavailable and does not prevent classification. Malformed recipe evidence fails materialization. `tested_method_call_arguments = []`, `tested_method_parameters = []`, and a non-empty derived list are not signature evidence. An empty list can support the recipe-capability fact “no derived input,” but it cannot support the claim “the selected MUT has no parameters.” A persisted supported lift does not change `zero_parameters` or `unsupported_types` into another category, and the report does not claim such a lift unless the recipe proves it.

A missing observation, duplicate observation, malformed declaration tuple, or contradictory declaration telemetry is an evidence error. Report materialization fails for that assertion instead of publishing an `unknown` or other catch-all category.

The report may retain resolver status, no-pick reason, topology fields, and recipe-derived columns as diagnostic columns in an audit query. Those fields do not override the category rules. A no-pick reason can explain missing declaration evidence, but report classification uses the persisted reason code field and status, not message text.

### 5. Enforce exact partition and declaration invariants

Before materializing the report detail table, the registered report validates all of these invariants:

- The rejection relation uses the canonical `ParameterType` producer identity and has one row per rejected assertion.
- Every rejected assertion maps to exactly one canonical observation. A missing observation is an evidence error, not a report category.
- Every observation used for a semantic category has a non-negative declaration count, an ordered JSON type array whose cardinality equals that count, and a non-null `candidate_param_supported` value.
- A declaration tuple is all-or-nothing. A null count, null support flag, or null resolved type array cannot enter `zero_parameters`, `unsupported_types`, or `derivation_gap`.
- Recipe-capability validation is separate from declaration classification. It supplies provenance for `derivation_gap` when the derived-input evidence is present; it never populates a formal parameter count. An absent historical recipe value is recorded as unavailable, while malformed present evidence fails materialization.
- `zero_parameters`, `unsupported_types`, `missing_declaration`, and `derivation_gap` have mutually exclusive predicates, with missing-declaration evidence handled before complete declarations.
- The category rows contain every declared category exactly once, including zero-count rows.
- The sum of category counts equals the number of distinct `ParameterType` rejections and the `reject` count in the canonical filter-detail row.
- A malformed JSON type array, contradictory null combination, duplicate or missing observation, contradictory declaration telemetry, or unclassifiable status fails report generation with an evidence error. It is not placed in an existing category to make totals balance.

The report replaces the invalid `no_arguments` interpretation with `zero_parameters` and retains provenance for the read-only query. The filter-detail `reject` reconciliation remains against persisted `filter_result` totals. Category counts may change, but the rejection total and the immutable corpus measurements do not.

### 6. Separate future correction from historical reconstruction

The future pipeline correction changes only the producer-side evidence boundary and its callers. New observations continue to be stored immediately after the assertion row, before later assertion analysis can fail. New filter results then record the recipe-capability decision and its stable reason/dependency fields. The pipeline does not backfill old assertion columns and does not create an observation for an old row.

The historical report correction changes only registered report SQL, category validation, metric registration, and table labels. It reads the immutable observation, assertion, and filter-result rows already in the selected database. It does not rerun Spoon, resolve methods again, infer missing declarations from source text, or alter corpus measurements. New and historical paths therefore use the same declaration evidence contract while respecting their different authorities: the pipeline records recipe capability at execution time, and the report reconstructs the old result from persisted declaration and recipe telemetry.

### 7. Migrate atomically and preserve a narrow rollback

Apply the migration as one source-and-report cutover:

1. Update the filter API and its filtering-task caller to pass one assertion-scoped observation and the recipe-capability evidence.
2. Keep mapper declaration fields all-or-nothing for a picked `CtMethod`: count, support flag, and ordered resolved types describe the same declaration; leave them null when no declaration exists.
3. Remove the call-argument empty-list branch. Check declaration availability and recipe evidence in that order.
4. Update focused filter and mapper coverage for zero formal parameters with receiver or constructor lifts, unsupported formal parameters with supported lifts, supported formal parameters with no derived recipe input, missing observations, and incomplete declarations.
5. Replace historical list-based SQL with the canonical observation join and the four-category partition.
6. Update report metrics, table labels, and provenance to use the new categories. Add exact observation-cardinality and declaration-consistency checks.
7. Deploy the pipeline and report changes as one revision. Do not modify historical database contents and do not rerun the corpus.

Rollback reverts the complete source-and-report revision before processing new measurements. It does not delete observation rows, alter historical filter results, or rerun the corpus. A report evidence error fails closed; operators can restore the last published report revision while the corrected read-only query is repaired against the same immutable database. Reverting only the filter or only the report is prohibited because it would pair different evidence contracts.

### 8. Do not rerun the historical corpus

The accepted historical result is a reconstruction from persisted telemetry. Report generation runs against a consistent read-only snapshot, validates the observation and filter-result joins, and records query and input provenance. It emits corrected category counts without rewriting the database or changing any original corpus denominator. A missing declaration remains a missing-declaration or incomplete-evidence result; it never triggers a project rerun or a replacement value from another corpus.

## Risks / Trade-offs

- **Legacy observations lack declaration telemetry.** The report preserves them as `missing_declaration` when an observation exists. A missing observation fails materialization instead of becoming an unsupported-parameter count.
- **A historical rejection has a supported selected declaration but no supported derived recipe input.** The report calls this `derivation_gap` rather than hiding it in `unsupported_types`. This exposes old recipe behavior without changing the rejection total.
- **Duplicate observations make a report unavailable.** Failing on duplicate identity is stricter than choosing the first row, but arbitrary selection would make a citable result depend on row order.
- **The new filter defers when declaration evidence is absent.** `DEFER` can reduce downstream evaluation, but it preserves dependency causality and prevents a false unsupported-parameter rejection.
- **Formal support and recipe support can differ.** The filter intentionally accepts a supported receiver or constructor lift even when formal parameters are unsupported, and it rejects a recipe with no supported input even when formal parameters are supported. This matches what the generator can expose, but it makes a signature-only report misleading.
- **Constructor and receiver topology can confuse consumers.** The design keeps topology fields for diagnostics while defining the formal signature from the picked declaration only. Consumers must not sum recipe inputs as method parameters.
- **Category names and metric keys change.** Existing consumers must migrate from `no_arguments` to `zero_parameters`, and must add `missing_declaration` and `derivation_gap`. No compatibility alias is kept because the old category semantics are wrong.
- **A filter reason remains broad.** Keeping `NO_GENERALIZABLE_PARAMETERS` preserves stable producer identity, while declaration and recipe telemetry supply the finer report partition. Adding message-derived reason variants would create unstable identities and is rejected.
- **A report can pass a numeric total while using weak evidence.** Exact observation cardinality, declaration-field consistency, and category predicates are validated before output, so balanced totals alone cannot mask missing or contradictory evidence.
- **Rollback can restore an older interpretation.** Rollback is limited to the complete prior code revision and does not claim that the old list-based classification is corrected. The immutable database remains available for a subsequent corrected read-only report run.

## Migration Plan

1. Implement the observation-backed `ParameterTypeFilter` contract and update the filtering task to use one assertion-scoped observation plus the recipe-capability evidence.
2. Preserve `ParameterType`, `NO_GENERALIZABLE_PARAMETERS`, `MISSING_TESTED_PARAMS`, `MUT_RESOLUTION_NOT_RECORDED`, and `MISSING_MUT` identities. Remove list deserialization of call arguments from this decision path, while retaining recipe-derived parameter evidence for capability evaluation.
3. Keep mapper declaration fields all-or-nothing for a picked `CtMethod`: count, support flag, and ordered resolved types describe the same declaration; leave them null when no declaration exists.
4. Add focused contract coverage for the decision matrix and for receiver-constructor, argument-constructor, zero-formal-parameter, unsupported-formal-parameter, and missing-declaration cases.
5. Replace the report's list-based `PARAMETER_TYPE_REJECTION_SQL` with the canonical observation join and the four-category partition. Add validation for canonical observation cardinality, declaration consistency, and exact category totals.
6. Update report metrics, table labels, and provenance to use the new categories. Keep the filter-detail `reject` reconciliation against persisted `filter_result` totals.
7. Deploy the pipeline and report changes as one revision. Do not modify historical database contents and do not rerun the corpus.
8. If deployment must be reversed, revert the complete revision before new measurements. Leave all rows and generated evidence untouched, and restore the prior report only as an operational rollback, not as the corrected classification.
9. Re-run the registered report read-only on the immutable historical database after any repair. Publish only when the exact partition and provenance checks pass.
