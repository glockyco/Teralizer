## Why

The RQ3 reduction view counts only single-assertion source tests as replaceable, although the final-suite contract permits removal whenever retained generalized tests represent every source assertion. The executable view and thesis must use one rule before the reduction mechanism is described as established.

## What Changes

- Derive source-assertion coverage from the generalized tests retained for newly detected mutants.
- Count an original test as replaceable only when every recorded assertion identity is represented by a retained generalized test.
- Preserve original tests when representation is partial or absent.
- Add focused database-view verification for single-assertion, fully represented multi-assertion, and partially represented multi-assertion source tests.
- Confirm that the finalized controlled database produces the same RQ3 rows under the aligned rule.

## Capabilities

### New Capabilities

- `reporting/test-suite-reduction`: Defines retained generalized tests, complete source-assertion representation, and original-test replacement in reduction reports.

### Modified Capabilities

None.

## Impact

- `src/main/resources/db/create-views.sql`: the `mv_generalization_effects` source-test replacement relation.
- Focused database-view tests or verification fixtures that own `mv_generalization_effects` behavior.
- RQ3 consumers retain their schema and reported values.
- No pipeline stage scheduling, corpus execution, mutation collection, or generated test source changes.
