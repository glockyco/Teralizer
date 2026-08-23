## Why

RQ6 needs to distinguish losses during generalized test creation from losses during filtering. The existing reports expose the necessary counts separately, but they do not publish one denominator-explicit comparison that shows filtering retains a similar proportion of controlled and RepoReapers generalized tests that reach this step.

## What Changes

- Publish controlled and RepoReapers counts for generalized tests that reach filtering, remain retained, or are excluded by filtering.
- Publish the retained share for each corpus from those generated counts, with separate denominators and provenance.
- Validate the controlled result against explicit filtering and failure evidence instead of treating `generalization.is_included` as sufficient semantic evidence.
- Present the result as conditional RQ6 mechanism evidence: filtering retains 84.0% of controlled generalized tests and 79.4% of RepoReapers generalized tests that reach filtering.
- Record the interpretation boundary: filtering is not the main source of the real-world applicability loss; the larger loss occurs during generalized test creation.
- Exclude the comparison from the thesis-wide headline key set, overall generalization success, project applicability, paired-project effects, and causal attribution.
- Reuse the thesis terms **filtering**, **filter results**, **retained**, and **generalized tests**. Do not introduce another reader-facing term for this step.
- Require a later thesis semantic review to refine the final sentence and placement without changing the metric identities or denominators.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Defines a conditional, denominator-explicit filtering comparison across controlled and RepoReapers generalized tests.
- `reporting/headline-evidence`: Keeps the conditional filtering comparison outside the thesis-wide headline evidence set while permitting it as bounded RQ6 mechanism evidence.

## Impact

The change affects controlled and real-world report queries, typed metrics, aggregate macros, provenance, focused report tests, generated analysis artifacts, and the thesis reconciliation handoff. It does not change a corpus, database schema, pipeline stage, filter, generated test, project applicability metric, or thesis prose.
