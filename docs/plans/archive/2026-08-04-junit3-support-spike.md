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

## Verdict: three blockers, two of them now removed

Detection alone yields nothing, but the reason is not what the first run suggested.

**Run 1, `github_com_jfgiraud_temmental` (6 tests).** All 6 recognized, `TestType` accepts,
then `NoAssertions` rejects all 6 with zero assertions extracted. Its assertions sit in private
helpers of the same class, so intraprocedural detection sees none.

**Population check.** That project is not representative. Parsing the method bodies of all
12,090 rejected JUnit 3 tests: 8,268 (68.4%, 63 projects) contain a direct JUnit assertion,
708 (5.9%) are helper-only like temmental, 157 have no calls, and 2,957 could not be parsed,
mostly because the method is inherited from a base class in another file. Of the 9,133 parsed,
90.5% assert directly. 58 of the 63 direct-assertion projects hold no validated generalization
today.

**Run 2, `github_com_tomgibara_bits` (296 tests, 29 parsed as direct-assertion).** All 296
recognized and accepted by `TestType`, then 295 rejected by `NoAssertions` — despite bodies
containing `assertEquals(d, c)` and `fail()`. Cause: `TestAnalysis.isAssertion` accepted only
`org.junit.Assert`, `org.junit.jupiter.api.Assertions`, and `org.hamcrest.MatcherAssert`. A
JUnit 3 test calls the assertions it *inherits* from `TestCase`, whose declaring type is
`junit.framework.Assert`, so no JUnit 3 assertion was ever recognized as an assertion.

**Run 3, same project, after adding the JUnit 3 declaring types.** 287 of 296 tests included,
9 still rejected by `NoAssertions`, and **1,953 assertions extracted where 19 were before**.
All 1,953 are then rejected by `MissingValue`: no tested method could be identified for any of
them, which is the ordinary gate that rejects 38.7% of real-world assertions corpus-wide. This
project's assertions compare `BitVector` objects rather than method results.

So JUnit 3 tests are now first-class through the test and assertion levels, and their yield
depends on the same downstream gates as every other test. Yield on the two projects sampled is
still zero generalizations, for reasons that are no longer JUnit-3-specific.

**Run 4, a six-project sample** drawn across the size range of the 58 direct-assertion,
currently-inapplicable projects. Every project now admits its JUnit 3 tests and extracts
assertions in volume, and none produces a usable generalization:

| Project | JUnit 3 tests | Included tests | Assertions | Included assertions | Validated generalizations |
|---|---|---|---|---|---|
| `jeremypepper_snakeyaml` | 813 | 777 | 2,512 | 0 | 0 |
| `killme2008_gecko` | 7 | 151 | 1,004 | 1 | 0 |
| `mwanji_migrate4j-maven` | 80 | 80 | 431 | 0 | 0 |
| `laforge49_JID` | 56 | 35 | 243 | 0 | 0 |
| `const3_doctor` | 20 | 26 | 165 | 0 | 0 |
| `weswilliams_GivWenZen` | 4 | 9 | 24 | 0 | 0 |

4,379 assertions extracted, 1 included, 1 generalization created, **0 validated**. `MissingValue`
rejects 3,825 of 4,379, that is 87.3%, against 38.7% corpus-wide: the asserted value in a JUnit 3
suite is usually not traceable to a single resolvable method call. These suites are
integration-style, and recognition was never the barrier.

Cost also counts against it. `gecko` alone ran about 55 minutes, because admitting its JUnit 3
tests raised included tests from 85 to 151 and produced 1,004 assertions, each requiring symbolic
analysis. Extrapolated across 94 projects that is a material addition to a 29.5-hour collection.

**Verdict: JUnit 3 support does not move applicability.** Measured yield across 7 projects is zero
validated generalizations, at higher runtime, and keeping it would move 9,617 tests from
`TestType` rejections into the assertion-level gates, changing the funnel's attribution.

Recommended disposition: revert test-type detection and the `setUp` fixture, and keep the
assertion-recognition fix, which is a correctness fix independent of JUnit 3 admission. The
negative result is worth keeping in the thesis's future-work discussion: JUnit 3 suites are not
blocked by framework support but by tested-method identification.

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
  Result: the JUnit-3-specific blockers are detection, fixture handling, and assertion
  recognition. All three are now implemented. What remains is ordinary downstream attrition.

- [x] Sample 6 more projects across the size range and record how far each gets.
  Result: 0 of 6 reach a validated generalization; 4,379 assertions extracted, 87.3% rejected for
  a missing tested method.

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
