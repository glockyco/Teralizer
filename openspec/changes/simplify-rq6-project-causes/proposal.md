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
- Separate test, assertion, and generalization decisions with rules. Let evaluated-population ordering expose different test decision points without extra subgroup rules.
- Rename the filter-detail denominator from `Total` to `Evaluated` and order each level by evaluated population, rejection count, and filter name.
- Use `included` and `excluded` for filtering outcomes while preserving `Accept`, `Defer`, and `Reject` for individual filter verdicts.
- Publish provenance-backed test-flow counts that reconcile source screening, pre-filter failures, both filter rounds, overlaps, and intervening failures.
- Correct the reader-facing `ExcludedTest` mechanism without publishing a diagnostic cause breakdown in the thesis.
- Publish provenance-backed `TestType` declaration categories that separate unsupported execution models from declaration-resolution failures.
- Separate oracle recognition, tested-method identification, and assertion-to-MUT mapping in the `NoAssertions` interpretation.
- Present implicit `does not throw` generation as an extension that must preserve framework exception semantics.
- Keep widening conclusions within the population that reaches Stage 4, and keep filtering comparisons separate from attempt-to-validation yield.
- Center the summary-table `Excluded` spanner and give the long filter-detail table an explicit compact-density contract.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Project-level exclusions omit inferred ownership classes. Filtering comparisons use included and excluded outcomes with corpus-local denominators and provenance.
- `reporting/exclusion-accounting`: Filter evidence follows proactive exclusion behavior. Detail tables expose evaluated populations and decision-group boundaries without conflating per-filter verdicts with aggregate outcomes.

## Impact

The change affects the shared RQ5/RQ6 exclusion renderers, RQ5 and RQ6 report builders, filtering models and metric identities, report tests, accepted terminology contracts, and regenerated publication artifacts. The thesis consumes the revised tables, metrics, and concise mechanism corrections. No database schema or corpus input changes. Project-funnel totals remain stable. Test-flow counts and filter decisions must reconcile from persisted evidence before publication.