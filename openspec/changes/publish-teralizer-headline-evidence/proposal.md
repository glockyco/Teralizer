## Why

The thesis needs a small, reusable results spine for abstract and summary passages, but the registered reports currently expose some headline evidence only as table cells or internal lifecycle metrics. This encourages hardcoded ranges, stale values, and conditional success rates whose denominators are too specialized for contribution-level claims.

## What Changes

- Define four evidence dimensions for thesis-level Teralizer claims: effectiveness, applicability, demonstrated real-world output, and mechanism-level applicability insight.
- Treat effectiveness and applicability as the two primary headline dimensions. Keep real-world output and mechanism insight as supporting dimensions whose reader-facing wording is decided later in the thesis repository.
- Publish stable, typed metrics and aggregate LaTeX macros for the existing RQ1 mutation-improvement ranges that summary prose currently derives from table cells.
- Reuse the existing RQ0 breadth and RQ6 project-applicability metrics as applicability evidence; do not add a composite applicability score.
- Publish the existing final-usable generalization count and its project population as demonstrated real-world output.
- Publish denominator-explicit RQ6 assertion-survival and widening-refusal metrics needed to support a mechanism-level insight without reconstructing values in prose.
- Preserve metric population, operand, corpus-input, producer-revision, and query provenance in the generated manifest.
- Do not publish a direct controlled-versus-RepoReapers generalization success comparison. The controlled producer predates the widening license, so 83.8%/30.2% and 84.0%/78.5% are diagnostic rates rather than headline evidence.
- Require a separate thesis planning and wording-review session before abstract or cross-chapter prose adopts the four dimensions. Producer metric names and lifecycle terms are evidence identities, not approved reader-facing language.

## Capabilities

### New Capabilities
- `reporting/headline-evidence`: Defines the four headline evidence dimensions, their typed metric contracts, publication requirements, and boundary between producer evidence and later thesis wording.

### Modified Capabilities
None.

## Impact

The change affects registered RQ1 and RQ6 metrics, aggregate macro selection, provenance output, focused report tests, and generated analysis artifacts. Existing RQ0, RQ1, RQ5, and RQ6 tables and query semantics remain unchanged. No corpus, database schema, pipeline behavior, or thesis prose changes in this producer change.
