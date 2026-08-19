# RQ6 - Causes of Unsuccessful Generalization (Real-World)

_Source database: `postgres_reporeapers_rq6_v7`._

## Project-level exclusions

Real-world exclusions separate project-level failures from filtering and downstream test, assertion, and generalization failures.

**Project-level exclusions by stage and cause for the \VariantImprovedC{} generalization strategy in RepoReapers projects. Internal causes are due to configured resource limits or current limitations of \ToolTeralizer{}. External causes are due to \ToolTeralizer{}'s dependencies (i.e., JUnit, Spoon, \ToolJPF{} / \ToolSPF{}, \ToolJacoco{}, and \ToolPit{}). Mixed causes are influenced by both internal and external factors.**

| \# | Type | Cause of Project-level Exclusion | Count |
| --- | --- | --- | --- |
| 1 | External | JUnit execution error during test execution | 13 |
| 2 | External | Spoon execution error during test analysis | 8 |
| 3 | Internal | JUnit reports not found | 5 |
| 4 | Internal | timeout exceeded (300 seconds per \VariantOriginal{} test suite) | 26 |
| 5 | Mixed | all assertions excluded due to filter rejections | 151 |
| 6 | Mixed | all assertions excluded due to filter rejections and failures | 105 |
| 7 | Mixed | all tests excluded due to filter rejections and failures | 94 |
| 8 | External | Spoon execution error during test instrumentation | 5 |
| 9 | Mixed | all assertions excluded due to earlier filter rejections and new failures | 1 |
| 10 | Internal | all generalizations excluded due to filter rejections and failures | 78 |
| 11 | External | \ToolPit{} execution error during mutation testing | 2 |
| 12 | Internal | JaCoCo outputs not found | 1 |
| 13 | Internal | timeout exceeded (3600 seconds during PIT mutation testing) | 4 |
| 14 | Mixed | unmutated test suite has failing tests | 6 |

_Eligible projects: 584. Stage 1 + 2: 584 entering, 182 included (31.2%), 402 excluded. Stage 3: 182 entering, 176 included (96.7%), 6 excluded. Stage 4: 176 entering, 98 included (55.7%), 78 excluded. Stage 5: 98 entering, 85 included (86.7%), 13 excluded. Overall: 85 of 584 included (14.6%)._

source: [`build_funnel`](https://github.com/glockyco/Teralizer/blob/57d235ce321541372494cfa9b2f52ab0ef054997/analysis/src/teralizer/eval/reports/_funnel.py#L378)

Generic JPF uncaught-exception diagnostics are reclassified from their retained detail into application exceptions and JPF environment gaps.

**Retrospective classification of generic JPF uncaught-exception diagnostics from retained detail.**

| Recovered cause | Diagnostics | Share |
| --- | --- | --- |
| Application exception | 1,378 | 76.8% |
| JPF native-peer gap | 255 | 14.2% |
| JPF model/field gap | 118 | 6.6% |
| Unparsed | 43 | 2.4% |

_This recovery changes cause attribution only; it does not change project eligibility or funnel outcomes._

source: [`fetch_jpf_exception_causes`](https://github.com/glockyco/Teralizer/blob/57d235ce321541372494cfa9b2f52ab0ef054997/analysis/src/teralizer/eval/reports/_diagnostics.py#L89)

ParameterType choice sensitivity is reported conservatively: only a rejection with an observed argument-taking alternative is choice-dependent.

**Choice sensitivity of ParameterType rejections classified from retained MUT candidate details.**

| Candidate evidence | Rejections | Share of all |
| --- | --- | --- |
| Candidate detail unavailable | 49,801 | 75.6% |
| Choice-invariant | 4,686 | 7.1% |
| Choice-dependent | 11,363 | 17.3% |

_Choice-dependent rows divided by all ParameterType rejections are a lower bound; rows without candidate detail remain unscored._

source: [`fetch_mut_choice_sensitivity`](https://github.com/glockyco/Teralizer/blob/57d235ce321541372494cfa9b2f52ab0ef054997/analysis/src/teralizer/eval/reports/_diagnostics.py#L169)

**Exclusion results for \VariantImprovedC{} in the RepoReapers projects.**

| Level | Total | Included | Filtering | Failures |
| --- | --- | --- | --- | --- |
| Test | 85,368 | 44,875 (52.6%) | 40,198 (47.1%) | 295 (0.3%) |
| Assertion | 180,548 | 6,905 (3.8%) | 166,602 (92.3%) | 7,041 (3.9%) |
| Generalization | 5,356 | 1,615 (30.2%) | 3,719 (69.4%) | 22 (0.4%) |

source: [`_fetch_breakdown`](https://github.com/glockyco/Teralizer/blob/57d235ce321541372494cfa9b2f52ab0ef054997/analysis/src/teralizer/eval/reports/rq6_causes.py#L292)

**Filtering results for \VariantImprovedC{} in the RepoReapers projects.**

| Level | Filter Name | Total | Accept | Defer | Reject |
| --- | --- | --- | --- | --- | --- |
| Test | NonPassingTest | 82,445 | 73,780 (89.5%) | — | 8,665 (10.5%) |
| Test | TestType | 82,445 | 82,246 (99.8%) | — | 199 (0.2%) |
| Test | NoAssertions | 73,662 | 49,396 (67.1%) | — | 24,266 (32.9%) |
| Test | DisabledTest | 73,662 | 73,657 (100.0%) | — | 5 (0.0%) |
| Test | InheritedTestCase | 73,662 | 72,609 (98.6%) | — | 1,053 (1.4%) |
| Test | MockingFramework | 73,662 | 66,689 (90.5%) | — | 6,973 (9.5%) |
| Assertion | AssertionType | 180,548 | 148,901 (82.5%) | — | 31,647 (17.5%) |
| Assertion | ExcludedTest | 180,548 | 141,764 (78.5%) | — | 38,784 (21.5%) |
| Assertion | MissingValue | 180,548 | 109,865 (60.9%) | — | 70,683 (39.1%) |
| Assertion | ParameterType | 180,548 | 48,992 (27.1%) | 65,706 (36.4%) | 65,850 (36.5%) |
| Assertion | ReturnType | 180,548 | 52,400 (29.0%) | 70,683 (39.1%) | 57,465 (31.8%) |
| Assertion | StringOperation | 180,548 | 175,345 (97.1%) | — | 5,203 (2.9%) |
| Generalization | NonPassingTest | 2,035 | 1,615 (79.4%) | — | 420 (20.6%) |

source: [`_fetch_filtering`](https://github.com/glockyco/Teralizer/blob/57d235ce321541372494cfa9b2f52ab0ef054997/analysis/src/teralizer/eval/reports/rq6_causes.py#L279)

Most of the generalization row's filtering column contains pre-emission soundness rejections rather than filter decisions.

**Causes for generalization attempts that produce no generalized test in the real-world dataset.**

| Refusal cause | Generalizations | Refusals | Attempts |
| --- | --- | --- | --- |
| Null output model, and the returned value is not a bytecode literal | 2,552 | 77.4% | 47.6% |
| Null output model, and a native call received a symbolic argument | 488 | 14.8% | 9.1% |
| Null output model, and no generated parameter reaches the path condition | 206 | 6.2% | 3.8% |
| Null output model, and the path condition does not pin every generated parameter | 38 | 1.2% | 0.7% |
| Exception oracle, concretized with a risk of divergence | 13 | 0.4% | 0.2% |
| Exception oracle, and the path condition does not pin every generated parameter | 1 | 0.0% | 0.0% |

_Refusals are decided before a generalized test is written, so they carry no filter decision and no lifecycle record._

source: [`fetch_widening_refusals`](https://github.com/glockyco/Teralizer/blob/57d235ce321541372494cfa9b2f52ab0ef054997/analysis/src/teralizer/eval/reports/_widening.py#L89)
