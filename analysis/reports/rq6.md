# RQ6 - Causes of Unsuccessful Generalization (Real-World)

_Source database: `postgres_reporeapers_rq6_v6`._

## Project-level exclusions

Real-world exclusions separate project-level failures from filtering and downstream test, assertion, and generalization failures.

**Project-level processing failures by funnel stage and cause.**

| Stage | Type | Cause | Count |
| --- | --- | --- | --- |
| 1 + 2 | External | JUnit execution error during test execution | 13 |
| 1 + 2 | External | Spoon execution error during test analysis | 8 |
| 1 + 2 | Internal | JUnit reports not found | 32 |
| 1 + 2 | Internal | timeout exceeded (300 seconds per original test suite) | 26 |
| 1 + 2 | Mixed | all assertions excluded due to filter rejections | 152 |
| 1 + 2 | Mixed | all assertions excluded due to filter rejections and failures | 102 |
| 1 + 2 | Mixed | all tests excluded due to filter rejections and failures | 93 |
| 3 | External | Spoon execution error during test instrumentation | 5 |
| 3 | Mixed | all assertions excluded due to earlier filter rejections and new failures | 1 |
| 4 | Internal | all generalizations excluded due to filter rejections and failures | 78 |
| 5 | External | PIT coverage minion exited abnormally | 1 |
| 5 | External | PIT execution error during mutation testing | 2 |
| 5 | Internal | JaCoCo outputs not found | 1 |
| 5 | Internal | timeout exceeded (3600 seconds during PIT mutation testing) | 5 |
| 5 | Mixed | unmutated test suite has failing tests | 7 |

_Eligible projects: 611. Stage 1 + 2: 611 entering, 185 included, 426 excluded (30.3%). Stage 3: 185 entering, 179 included, 6 excluded (96.8%). Stage 4: 179 entering, 101 included, 78 excluded (56.4%). Stage 5: 101 entering, 85 included, 16 excluded (84.2%). Overall: 85 of 611 included (13.9%)._

source: [`build_funnel`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/_funnel.py#L300)

Generic JPF uncaught-exception diagnostics are reclassified from their retained detail into application exceptions and JPF environment gaps.

**Retrospective classification of generic JPF uncaught-exception diagnostics from retained detail.**

| Recovered cause | Diagnostics | Share |
| --- | --- | --- |
| Application exception | 1,359 | 75.9% |
| JPF native-peer gap | 255 | 14.2% |
| JPF model/field gap | 141 | 7.9% |
| Unparsed | 35 | 2.0% |

_This recovery changes cause attribution only; it does not change project eligibility or funnel outcomes._

source: [`fetch_jpf_exception_causes`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/_diagnostics.py#L89)

ParameterType choice sensitivity is reported conservatively: only a rejection with an observed argument-taking alternative is choice-dependent.

**Choice sensitivity of ParameterType rejections classified from retained MUT candidate details.**

| Candidate evidence | Rejections | Share of all |
| --- | --- | --- |
| Candidate detail unavailable | 50,061 | 75.5% |
| Choice-invariant | 4,735 | 7.1% |
| Choice-dependent | 11,479 | 17.3% |

_Choice-dependent rows divided by all ParameterType rejections are a lower bound; rows without candidate detail remain unscored._

source: [`fetch_mut_choice_sensitivity`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/_diagnostics.py#L169)

**Eligible test, assertion, and generalization outcomes by filtering versus failures for the real-world dataset.**

| Level | Total | Included | Incl. % | Filtering | Filt. % | Failures | Fail. % |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Test | 85,595 | 44,989 | 52.6% | 40,311 | 47.1% | 295 | 0.3% |
| Assertion | 181,585 | 7,096 | 3.9% | 167,559 | 92.3% | 6,930 | 3.8% |
| Generalization | 5,529 | 1,614 | 29.2% | 3,893 | 70.4% | 22 | 0.4% |

source: [`_fetch_breakdown`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/rq6_causes.py#L292)

**Distinct eligible entities receiving each filter decision, by level and filter.**

| Level | Filter Name | Total | Accept | Defer | Reject |
| --- | --- | --- | --- | --- | --- |
| Test | NonPassingTest | 82,603 | 89.4% | 0.0% | 10.6% |
| Test | TestType | 82,603 | 99.8% | 0.0% | 0.2% |
| Test | NoAssertions | 73,764 | 67.1% | 0.0% | 32.9% |
| Test | DisabledTest | 73,764 | 100.0% | 0.0% | 0.0% |
| Test | InheritedTestCase | 73,764 | 98.6% | 0.0% | 1.4% |
| Test | MockingFramework | 73,764 | 90.5% | 0.0% | 9.5% |
| Assertion | AssertionType | 181,585 | 82.5% | 0.0% | 17.5% |
| Assertion | ExcludedTest | 181,585 | 78.3% | 0.0% | 21.7% |
| Assertion | MissingValue | 181,585 | 60.9% | 0.0% | 39.1% |
| Assertion | ParameterType | 181,585 | 27.2% | 36.3% | 36.5% |
| Assertion | ReturnType | 181,585 | 29.0% | 39.1% | 31.9% |
| Assertion | StringOperation | 181,585 | 97.1% | 0.0% | 2.9% |
| Generalization | NonPassingTest | 2,035 | 79.3% | 0.0% | 20.7% |

source: [`_fetch_filtering`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/rq6_causes.py#L279)

Most of the generalization row's filtering column is the widening license rather than any filter.

**Why the widening license refused to emit a generalized test, by cause, for the real-world dataset.**

| Refusal cause | Generalizations | Refusals | Attempts |
| --- | --- | --- | --- |
| Null output model, oracle expression is not boolean | 1,828 | 52.7% | 33.1% |
| Null output model, concretization weakened the path condition | 1,329 | 38.3% | 24.0% |
| Path condition does not pin every generated parameter | 299 | 8.6% | 5.4% |
| Exception oracle concretized with divergence risk | 15 | 0.4% | 0.3% |

_Refusals are decided before a generalized test is written, so they carry no filter decision and no lifecycle record._

source: [`fetch_widening_refusals`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/_widening.py#L78)
