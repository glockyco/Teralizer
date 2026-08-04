---
title: JUnit 3 Support Spike
type: plan
status: implemented
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived: 2026-08-04
---

# JUnit 3 Support Spike

Decide whether generalizing JUnit 3 tests works end to end, on one project, before the scope
question is settled and before `2026-08-04-rq6-recollection` launches.

## Verdict: detection is necessary and not sufficient

Run on `github_com_jfgiraud_temmental` (project 792, 6 JUnit 3 tests) into
`postgres_junit3_spike`. Detection works: the 6 tests are recorded under the `TestCase`
marker and `TestType` accepts all 234 tests in the project. They are then **rejected at
`FILTER_TESTS` with zero assertions extracted**.

The cause is mechanical and visible in the source. `TestI18n.test_en` reads

```java
public void test_en() throws IOException, TemplateException {
    assertFoundAndEquals("hello fr_FR", locale_en, "test");
    assertNotFound(locale_en, "test2");
```

where `assertFoundAndEquals` and `assertNotFound` are private helpers declared in the same
class that hold the real assertions. Intraprocedural assertion detection sees none, so
`NoAssertions` rejects the test. This is the false-positive family RQ5 measured at 86.3% on
developer-written tests.

So JUnit 3 support cannot be valued independently: **interprocedural assertion detection is
the prerequisite, not JUnit 3 recognition**, and the standalone yield on this project is zero.

Two measurement errors in the estimate that motivated this spike are corrected here. The
claim that 32 projects have a wholly JUnit 3 suite was wrong: the criterion used was zero
*included* tests, and project 792 shows that is compatible with 228 annotated tests excluded
for other reasons. And no JUnit 3 test in the corpus carries a `NoAssertions` decision at all,
because filters stop at the first rejection, so the survival rate past `TestType` was never
derivable from the existing data.

## What motivated it

`TestType` rejects 9,617 tests, all of which carry no annotation and 99.1% of which use the
JUnit 3 naming convention, across 94 eligible projects of which 84 hold no validated
generalization. Nothing else identified moves the applicability figure.

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

- [x] Run the full pipeline for one candidate into a scratch database, with the RQ6 profile.
  Verification: `REPOREAPERS_DB=postgres_junit3_spike REPOREAPERS_CONFIG_DIR=<temp dir with one project conf> REPOREAPERS_PROFILE=project-configs/reporeapers-rq6.conf bash scripts/run-reporeapers-rerun.sh --reset-db`
  Expected: tests are recorded with `test_annotation_name = 'TestCase'` and are no longer
  rejected by `TestType`.

- [x] Read how far the project gets, stage by stage, and record it.
  Verification: query `task`, `test`, `assertion`, `generalization` for the project in the scratch database
  Expected: a definitive answer to each of — are assertions extracted; does specification
  extraction run with the fixture applied; is a generalized test created; does the generalized
  suite compile; does it execute with JUnit 3 tests and jqwik properties in one surefire run.

- [x] Record the verdict and the blocking stage, then decide scope.
  Result: blocked at `NoAssertions`. Production JUnit 3 support is not worth planning until
  interprocedural assertion detection exists, and the two must then be valued together.

- [x] Note the consequence of keeping the implementation. It recovers nothing today, but it does
      move 9,617 tests from `TestType` rejections to whatever rejects them next, which changes
      the filter attribution RQ6 reports. Keeping it is the more honest attribution, since it
      shows the real blocker; reverting preserves continuity with the published table. The
      decision belongs to the chapter, not to this spike.

### Task 2: Drop the scratch database

- [x] Remove `postgres_junit3_spike` once the verdict is recorded, keeping every measurement
      corpus untouched.
  Verification: database list no longer contains it
  Expected: no corpus database is affected.
