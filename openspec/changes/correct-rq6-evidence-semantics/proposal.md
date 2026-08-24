## Why

The finalized RepoReapers reports still expose a review-stage filter term, a generic inherited-test mechanism, two misclassified PIT import failures, and filter rows that contradict retained MUT evidence. The thesis cannot publish precise mechanism claims until the producer corrects these identities and evidence inconsistencies without rerunning the first-run corpus.

## What Changes

- **BREAKING**: Replace the filter-result boundary identifier throughout report code, metric keys, row keys, generated labels, aggregate macros, provenance, tests, and accepted specifications. Remove the old identifier without a compatibility alias.
- **BREAKING**: Replace the generic test-level unsupported-capability mechanism key and label with the exact inherited-test inlining limit, then migrate every consumer.
- Attribute the two retained PIT coverage-import failures to the failed report-import operation rather than PIT execution.
- Repair five resolved MUT picks whose required tested-method fields were not persisted.
- Resolve seven `ParameterType` rejection rows whose retained resolver evidence contains a supported input domain by correcting persistence or report interpretation. Omit any component claim that corrected evidence cannot support.
- Regenerate affected reports, macros, provenance, and manifests from the preserved first-run database. Do not start or rerun the corpus.
- Keep purely diagnostic `DEFER` filters out of reader-facing exclusion mechanisms and thesis-facing outputs.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Require stable filter-result vocabulary, exact mechanism attribution, internally consistent filter evidence, correct operation-level failure attribution, and regenerated traceable outputs.
- `pipeline/cross-stage-contracts`: Require resolved MUT observations and persisted tested-method fields to remain consistent before filter decisions are recorded.

## Impact

The change affects exclusion-evidence queries and report builders, MUT-resolution persistence and filtering, generated metric and row identities, report provenance and manifests, accepted reporting and pipeline contracts, and focused tests. It deliberately breaks consumers of the retired identifiers. It does not change the protected corpus identity, mutate the preserved first-run database, or authorize a corpus, sentinel, hotspot, or JARVIS run.
