## Why

The thesis retains one controlled-versus-RepoReapers comparison, but the controlled numerator,
denominator, and rate exist only inside a rendered RQ5 table row. Without stable metric identities, the
consumer cannot cite or publish that comparison under the accepted evidence contract.

## What Changes

- Emit controlled `Improved (200 tries)` generalization-inclusion numerator, denominator, and share as
  registered RQ5 metrics with stable semantic keys.
- Attach the `Generalization` population, controlled corpus identity, denominator key, and query
  provenance to those metrics.
- Derive the existing RQ5 breakdown table row and the new metrics from the same fetched result; do not
  add a second query or duplicate the calculation.
- Publish the metrics through the existing aggregate macro artifact so downstream consumers can use
  generated values rather than table-position lookup or prose literals.
- Verify that the registered report, provenance manifest, generated macros, and existing table remain
  numerically consistent.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reporting/exclusion-evidence`: Requires the retained controlled comparison to expose stable,
  denominator-explicit metric identities backed by the same evidence as the RQ5 table.

## Impact

- `analysis/src/teralizer/eval/reports/rq5_causes.py` and its focused tests.
- Registered RQ5 metric inventory, provenance output, and generated aggregate LaTeX macros.
- The existing controlled exclusion table remains a presentation of the same source data.
- No database schema, corpus contents, query semantics, report scheduling, or real-world RQ6 values
  change.
