---
title: JUnit 3 Support Spike
type: plan
status: active
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# JUnit 3 Support Spike

Decide whether generalizing JUnit 3 tests works end to end, on one project, before the scope
question is settled and before `2026-08-04-rq6-recollection` launches.

Why it is worth asking: `TestType` rejects 9,617 tests, all of which carry no annotation and
99.1% of which use the JUnit 3 naming convention. Those tests belong to 94 eligible projects,
**84 of which are currently not applicable at all**, and 32 of which have no included test
whatsoever, so their whole suite is invisible today. Nothing else identified moves the
applicability figure; every reduction-path fix leaves it unchanged.

Cost of being wrong: this is a capability change, not a defect fix. It alters §5.3.2's
supported-test-type description in the thesis, and RQ1--RQ5 stay frozen on an implementation
without it, so controlled and real-world results would differ in capability. The spike exists
to price that before committing.

## What is implemented

Detection and fixture handling only, both minimal:

- `Configuration.TEST_MARKER_JUNIT3` records an annotation-less JUnit 3 test under a synthetic
  marker, and `SUPPORTED_TEST_ANNOTATIONS` accepts it, so `TestTypeFilter` needed no change.
- `JunitDataCollectionTask.isJunit3TestMethod` requires the `test` name prefix, no parameters,
  and a `junit.framework.TestCase` ancestor found through superclass *references*, since
  TestCase is not in the Spoon model.
- `JpfInstrumentationTask.beforeMethodsFor` adds a no-argument `setUp` per class in the
  hierarchy when the class is a TestCase subclass. The hierarchy is already walked parent
  first, so an inherited fixture runs before its override. `setUp` is `protected`, which the
  generated driver can call because it is emitted into the same package.

Nothing else is adapted. In particular the generated property test remains a jqwik class, and
no attempt is made to reconcile JUnit 3 execution with the jqwik engine in one surefire run.
That is the risk the spike measures.

## Tasks

### Task 1: Run one project end to end

Candidates, all eligible with zero included tests today: `github_com_jfgiraud_temmental` (6
JUnit 3 tests, real `setUp` chain), `github_com_alibaba_tamper` (7),
`github_com_karlwettin_json-simple-kalle` (8).

- [ ] Run the full pipeline for one candidate into a scratch database, with the RQ6 profile.
  Verification: `REPOREAPERS_DB=postgres_junit3_spike REPOREAPERS_CONFIG_DIR=<temp dir with one project conf> REPOREAPERS_PROFILE=project-configs/reporeapers-rq6.conf bash scripts/run-reporeapers-rerun.sh --reset-db`
  Expected: tests are recorded with `test_annotation_name = 'TestCase'` and are no longer
  rejected by `TestType`.

- [ ] Read how far the project gets, stage by stage, and record it.
  Verification: query `task`, `test`, `assertion`, `generalization` for the project in the scratch database
  Expected: a definitive answer to each of — are assertions extracted; does specification
  extraction run with the fixture applied; is a generalized test created; does the generalized
  suite compile; does it execute with JUnit 3 tests and jqwik properties in one surefire run.

- [ ] Record the verdict and the blocking stage if any, then decide scope.
  Verification: `omp-plans check`
  Expected: either a follow-up plan for production JUnit 3 support with the remaining work
  named, or this plan abandoned with the blocking mechanism recorded and the implementation
  reverted.

### Task 2: Drop the scratch database

- [ ] Remove `postgres_junit3_spike` once the verdict is recorded, keeping every measurement
      corpus untouched.
  Verification: database list no longer contains it
  Expected: no corpus database is affected.
