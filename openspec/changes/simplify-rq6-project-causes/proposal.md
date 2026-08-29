## Why

The RQ6 project-exclusion table classifies each row as internal, external, or mixed. Those labels describe an inferred implementation locus, not a stable causal property. Several rows cross that boundary, so the taxonomy encourages unsupported responsibility and actionability claims.

## What Changes

- Remove the internal/external/mixed `Type` column from the RQ6 project-exclusion report and generated thesis table.
- Preserve each recorded stage, concrete cause description, and count.
- Keep `Cause of Project-level Exclusion` as the reader-facing cause column heading.
- Update report validation, tests, generated metrics, and publication artifacts so the removed taxonomy has no remaining consumer.
- Correct project-cause rows that combine distinct observed mechanisms or assign a mechanism without entity evidence.
- Preserve useful task-diagnostic distinctions for JUnit report collection failures.
- Keep project, test, assertion, and generalization populations distinct.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Project-level exclusion evidence reports the observed stage, cause description, and count without assigning an internal/external/mixed ownership class.

## Impact

The change affects `analysis/src/teralizer/eval/reports/rq6.py`, the project-exclusion table contract and tests, and regenerated RQ6 report and thesis artifacts. The thesis change `restore-rq6-narrative` consumes the revised table and removes the same taxonomy from prose. No database schema or corpus input changes. Project-funnel totals remain stable, but cause rows and stage attribution can change when concrete evidence contradicts the legacy fallback.