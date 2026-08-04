---
title: Open Questions to Close Before Re-Collection
type: plan
status: active
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Open Questions to Close Before Re-Collection

Three characterizations whose answers could change what the next collection should fix or
report. Each is a query and log-reading exercise against `postgres_reporeapers_rq6`, not a
build, and each ends by recording its finding. Run them alongside
`2026-08-04-reduction-path-fixes`; they gate the launch in `2026-08-04-rq6-recollection`.

## Tasks

### Task 1: Characterize the largest extraction failure class

`UNCAUGHT_EXCEPTION_PATH` accounts for 1,783 assertion-level `EXECUTE_JPF` failures, more
than any other cause, and nothing currently says whether it is a genuine limit of
single-path analysis or an artifact of how the harness drives SPF.

**Files:**
- Modify: `docs/plans/2026-08-04-reduction-failure-anatomy.md` (record the finding) or add a companion audit if the answer is large.

- [ ] Sample at least 20 affected assertions across at least 10 projects. For each, read the
      stored diagnostic and the assertion's tested method, and determine whether the exception
      arises in the tested method on the concrete path, in test setup, or inside SPF itself.
  Verification: the sample, its per-cause counts, and the query used are recorded.
  Expected: a stated split between genuine exceptional paths, harness-driven failures, and SPF
  faults, with the dominant one named.

- [ ] If harness-driven failures dominate, open a fix task in
      `2026-08-04-reduction-path-fixes` rather than widening this plan.
  Verification: `omp-plans check`
  Expected: the fix lands in the fixes plan, and this plan holds only the finding.

- [ ] Commit.
  Message: `docs(plans): characterize uncaught-exception extraction failures`

### Task 2: Explain the residual absent JaCoCo reports

The argLine fix cut absent coverage reports from 40 projects to 8. Whether those 8 share a
cause decides if one more mechanical fix is available before re-collection.

- [ ] For each of the 8 projects, read the coverage command output and classify the cause, for
      example an un-instrumentable build, an aggregator module, or a non-default report path.
  Verification: per-project causes recorded with project ids.
  Expected: either a shared pattern worth a fix task, or a statement that they are genuinely
  un-instrumentable and remain legitimate exclusions.

- [ ] Commit.
  Message: `docs(plans): classify residual absent coverage reports`

### Task 3: Re-ground the unsupported test-type claim

`TestType` rejects 9,617 tests under the single reason code `UNSUPPORTED_TEST_TYPE`. The
published claim that these are JUnit 3 methods was verified on the retired corpus only, and
the thesis will restate it.

- [ ] Split the rejections by what actually made them unsupported — JUnit 3 naming convention,
      `@ParameterizedTest`, `@RepeatedTest`, or another annotation — over the current corpus,
      and record counts and distinct projects per group.
  Verification: the query and its result table are recorded.
  Expected: a defensible statement of the composition, replacing the retired claim.

- [ ] If one group dominates and is mechanically detectable, note it as a candidate feature in
      `2026-06-26-teralizer-overview`'s improvement ordering. Do not implement it here.
  Verification: `omp-plans check`
  Expected: candidate recorded, no code change in this plan.

- [ ] Commit.
  Message: `docs(plans): re-ground the unsupported test-type composition`
