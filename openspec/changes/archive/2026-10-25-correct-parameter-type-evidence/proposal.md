## Why

ParameterType evidence can misclassify selected-MUT cases when it treats an empty derived list as proof of unsupported parameters. This obscures dependency semantics and conflates unsupported parameters, zero-parameter MUTs, missing declarations, and derivation edge cases in registered RQ6 reports.

## What Changes

- Preserve honest dependency semantics during source filtering; a derived input list must not stand in for the selected MUT signature.
- Keep `ParameterType` as the stable filter name.
- Treat `tested_method_parameters` as recipe-derived generalizable inputs, not the complete original MUT signature. Treat `tested_method_call_arguments` as recipe-derived only when derivation ran, and as raw call-site arguments otherwise.
- Classify registered report evidence from selected-MUT telemetry, using the complete declaration tuple of candidate parameter count, candidate support, and resolved parameter types as the classification authority.
- Retain resolution status and no-pick reason as provenance only; they must not override declaration-tuple completeness. A selected method with null declaration telemetry is missing declaration evidence, even when resolution records a successful pick.
- Distinguish unsupported selected-MUT parameters, zero-parameter MUTs, missing declarations, and derivation edge cases. Do not label missing declaration evidence as an unsupported-parameter cause.
- Keep historical databases immutable. No corpus rerun is required.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-accounting`: Source filtering preserves dependency semantics, and registered ParameterType reports classify evidence from selected-MUT telemetry rather than derived-list emptiness.

## Impact

The change affects the evidence semantics for source filtering and the classification of registered RQ6 exclusion reports. Historical database contents and corpus measurements remain unchanged, and no corpus rerun is required.
