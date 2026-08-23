## Context

See proposal.md — Why. `mv_generalization_effects` already derives retained generalized tests from mutations that change from undetected in the selected baseline to killed in a generalized variant. Its `test_data` relation then treats a source test as replaceable only when the source assertion count is one. This shortcut duplicates source-test cost if extended by merely relaxing the predicate because one source test can contribute several retained generalizations.

The view is a reporting contract. It does not physically delete test source or schedule a pipeline stage. The finalized controlled database is protected evidence and can be queried read-only to check whether the corrected rule changes published rows.

## Goals / Non-Goals

**Goals:**

- Derive replacement eligibility from retained generalized-test identities.
- Support complete multi-assertion replacement without double-counting source-test cost.
- Preserve the columns and row identities consumed by RQ3 reports.
- Prove the single, complete-multi, and partial-multi cases with a focused database-view contract.

**Non-Goals:**

- Minimize the combined suite globally by runtime, source lines, or mutation subsumption.
- Change how PIT attributes newly detected mutants to generalized tests.
- Change generation, filtering, stage scheduling, or physical test source.
- Rerun a corpus or rewrite an unchanged published value.

## Decisions

### 1. Select generalized tests before determining replaceable originals

The existing newly-killed mutation relation remains the authority for the retained generalized set. The relation that carries retained generalization identity will also carry its source assertion identity. A generalized test discarded by mutation-based selection cannot contribute assertion coverage.

**Alternative:** Use `generalization.is_included`. Rejected because that flag records pipeline viability before mutation-based reduction and would remove originals whose generalized replacements are absent from the final suite.

### 2. Build one row per completely represented source test

A new relation will group retained source-assertion identities by project, baseline, variant, and source test. A source test is replaceable when its nonzero total assertion count equals the count of distinct represented assertion identities. The relation will then join the source test and baseline test-extension data once.

This row shape is the central invariant:

```text
one retained generalization  → one added generalized-test cost
one replaceable source test  → one removed original-test cost
```

It supports multi-assertion replacement without subtracting the original once per retained generalized test.

**Alternative:** Relax the current `assertions = 1` filter inside `test_data`. Rejected because the current input has one row per retained generalization and would double-count multi-assertion source tests.

### 3. Keep report consumers unchanged

`mv_generalization_effects` will continue to expose the same columns and project/baseline/variant row identities. Only `removed_tests`, removed source lines and runtime, and their derived after/delta columns may change when a dataset contains a fully represented multi-assertion test.

### 4. Verify behavior synthetically before checking finalized evidence

A focused database-view test will cover:

- one retained generalization replacing a single-assertion source test;
- all retained generalizations replacing a multi-assertion source test exactly once;
- partial retained coverage preserving a multi-assertion source test;
- a viable but mutation-redundant generalization not contributing coverage.

After the synthetic contract passes, a read-only query against the finalized controlled database will compare old and corrected removal identities and RQ3 measures. No corpus run is required.

## Risks / Trade-offs

- **A source test has unrecorded assertions.** The reporting rule is bounded by recorded assertion identity, matching the rest of the pipeline's analysis model. Tests outside that model remain governed by the existing filters.
- **Several generalized tests kill the same new mutant.** PIT attribution may retain only the reported killer. This change does not broaden mutation evidence or infer unrecorded killers.
- **A future dataset changes RQ3 values.** That is intended when it contains a fully represented multi-assertion source test. Existing report schemas remain stable.
- **The finalized controlled database masks the semantic change.** Synthetic fixtures prove both positive and negative multi-assertion cases before the unchanged empirical result is accepted.
