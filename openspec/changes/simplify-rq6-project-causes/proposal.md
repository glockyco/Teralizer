## Why

The RQ6 project-exclusion table classifies each row as internal, external, or mixed. Those labels describe an inferred implementation locus, not a stable causal property. Several rows cross that boundary, so the taxonomy encourages unsupported responsibility and actionability claims.

## What Changes

- Remove the internal/external/mixed `Type` column from the RQ6 project-exclusion report and generated thesis table.
- Preserve each recorded stage, concrete cause description, and count.
- Keep `Cause of Project-level Exclusion` as the reader-facing cause column heading.
- Update report validation, tests, generated metrics, and publication artifacts so the removed taxonomy has no remaining consumer.
- Correct project-cause rows that combine distinct observed mechanisms or assign a mechanism without entity evidence.
- Reconstruct stage entry from observed transitions instead of final entity status, so later failures remain in the stage where they occur.
- Aggregate project rows into reader-facing causes when internal mechanism combinations do not change the interpretation.
- Preserve detailed diagnostics in generated evidence while keeping the project table focused on material distinctions.
- Keep project, test, assertion, and generalization populations distinct; use selected concrete examples in the thesis prose and leave complete subtype distributions in the replication package.
- Classify proactive exclusions as filters by behavior, independent of their pipeline stage, producer class, or persistence shape.
- Add `InheritedTestMethod`, `SeedSpecConsistency`, and `WideningLicense` to the filter-detail table with evidence-derived verdict populations.
- Separate the two test-filter rounds with a rule and order each decision subgroup by descending rejection count.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Project-level exclusion evidence reports the observed stage, cause description, and count without assigning an internal/external/mixed ownership class.
- `reporting/exclusion-accounting`: Filter evidence follows proactive exclusion behavior instead of implementation-stage or storage-shape boundaries.

## Impact

The change affects the RQ6 cause and exclusion-evidence report builders, the project-exclusion and filter-detail contracts and tests, and regenerated RQ6 report and thesis artifacts. The thesis change `restore-rq6-narrative` consumes the revised table and removes the same taxonomy from prose. No database schema or corpus input changes. The eligible-project total, final inclusion count, and total exclusions remain stable. Intermediate stage bands, cause rows, and stage attribution can change when transition evidence contradicts the legacy use of final entity status.