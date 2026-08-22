## Why

The full verification corpus runs after every push to `master`, including documentation-only and report-only changes that cannot affect the Java pipeline. Its historical successful runtime is about 20 minutes, and one run remained active for more than six hours because the workflow has no explicit timeout.

## What Changes

- Inventory every tracked input whose behavior or bytes can affect `scripts/verify-pipeline.sh`, including its transitive scripts, Java and JPF sources, build inputs, project configuration, synthetic fixtures, goldens, database schema, and the workflow itself.
- Restrict push-triggered corpus runs to that declared owner-path set while preserving weekly drift detection and explicit manual dispatch.
- Add a 35-minute job timeout. This exceeds the observed successful range while bounding stuck pipeline, Gradle, container, and runner failures.
- Keep newer-push cancellation because a newer revision supersedes an in-flight corpus result for the same branch.
- Add a deterministic trigger-contract check that proves representative pipeline-owner changes schedule the workflow and representative documentation or report-renderer changes do not.
- Record the resulting scheduling and timeout contract in the accepted CI validation capability.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository/ci-validation`: Define ownership-scoped push scheduling, retained scheduled and manual execution, cancellation semantics, and a bounded corpus runtime.

## Impact

This change affects `.github/workflows/verification-corpus.yml`, repository validation for workflow trigger coverage, and the accepted `repository/ci-validation` specification. It changes when the existing synthetic verification corpus runs; it does not change fixture behavior, pipeline semantics, production corpora, reports, release acceptance, or database lifecycle rules.
