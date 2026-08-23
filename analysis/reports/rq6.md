# RQ6 - Causes of Unsuccessful Generalization (Real-World)

_Source database: `postgres_reporeapers_rq6_v7`._

## Project-level exclusions

Real-world exclusions separate project-level failures from filtering and downstream test, assertion, and generalization failures.

**Project-level exclusions by stage and cause for the Improved (200 tries) generalization strategy in RepoReapers projects. Internal causes are due to configured resource limits or current limitations of Teralizer. External causes are due to Teralizer's dependencies (i.e., JUnit, Spoon, JPF / SPF, JaCoCo, and PIT). Mixed causes are influenced by both internal and external factors.**

| # | Type | Cause of Project-level Exclusion | Count |
| --- | --- | --- | --- |
| Stage 1 + 2 - Project Analysis: 584 projects, 182 inclusions, 402 exclusions, 31.2% inclusion rate |  |  |  |
| 1 | External | JUnit execution error during test execution | 13 |
| 2 | External | Spoon execution error during test analysis | 8 |
| 3 | Internal | JUnit reports not found | 5 |
| 4 | Internal | timeout exceeded (300 seconds per Original test suite) | 26 |
| 5 | Mixed | all assertions excluded due to filter rejections | 151 |
| 6 | Mixed | all assertions excluded due to filter rejections and failures | 105 |
| 7 | Mixed | all tests excluded due to filter rejections and failures | 94 |
| Stage 3 - Spec. Extraction: 182 projects, 176 inclusions, 6 exclusions, 96.7% inclusion rate |  |  |  |
| 8 | External | Spoon execution error during test instrumentation | 5 |
| 9 | Mixed | all assertions excluded due to earlier filter rejections and new failures | 1 |
| Stage 4 - Gen. Test Creation: 176 projects, 98 inclusions, 78 exclusions, 55.7% inclusion rate |  |  |  |
| 10 | Internal | all generalizations excluded due to filter rejections and failures | 78 |
| Stage 5 - Test Suite Reduction: 98 projects, 85 inclusions, 13 exclusions, 86.7% inclusion rate |  |  |  |
| 11 | External | PIT execution error during mutation testing | 2 |
| 12 | Internal | JaCoCo outputs not found | 1 |
| 13 | Internal | timeout exceeded (3600 seconds during PIT mutation testing) | 4 |
| 14 | Mixed | unmutated test suite has failing tests | 6 |
| Overall: 584 projects, 85 inclusions, 499 exclusions, 14.6% inclusion rate |  |  |  |

_Eligible projects: 584. Stage 1 + 2: 584 entering, 182 included (31.2%), 402 excluded. Stage 3: 182 entering, 176 included (96.7%), 6 excluded. Stage 4: 176 entering, 98 included (55.7%), 78 excluded. Stage 5: 98 entering, 85 included (86.7%), 13 excluded. Overall: 85 of 584 included (14.6%)._

source: [`build_funnel`](https://github.com/glockyco/Teralizer/blob/e6018372bcf463c65b1e2eb3c92e0c2d6078405c/analysis/src/teralizer/eval/reports/_funnel.py#L378)

Filtering results use generalized tests that reach filtering as each dataset's denominator. They do not measure overall success or project applicability.

**Filtering results for controlled and RepoReapers generalized tests.**

| Dataset | Total | Retained | Excluded | Retained share |
| --- | --- | --- | --- | --- |
| Controlled | 13,804 | 11,597 | 2,207 | 84.0% |
| RepoReapers | 2,035 | 1,615 | 420 | 79.4% |

_Each share uses the generalized tests with a filtering result in that dataset as its denominator._

source: [`build_filtering_comparison_table`](https://github.com/glockyco/Teralizer/blob/600c4814b9f7aef1af68743fdb294d58f61d8809/analysis/src/teralizer/eval/reports/_filtering_comparison.py#L227)

Generalization attempts are reported separately from emitted, filter-adjudicated, validated, reduced, and final-usable tests. A missing independent task record remains unknown.

**Observed generalized-test populations for Improved (200 tries). Unknown means that no independent task record proves the reported failure stage ran.**

| Population | Count | Entering | Excluded | Attempt known | Attempt unknown |
| --- | --- | --- | --- | --- | --- |
| Attempted | 5,356 | 5,356 | 0 | 0 | 0 |
| Emitted | 2,057 | 5,356 | 3,299 | 3,299 | 0 |
| Filter adjudicated | 2,035 | 2,057 | 22 | 22 | 0 |
| Filter passed | 1,615 | 2,035 | 420 | 420 | 0 |
| Validated | 1,615 | 1,615 | 0 | 0 | 0 |
| Reduced | 1,435 | 1,615 | 180 | 32 | 148 |
| Final usable | 1,435 | 1,435 | 0 | 0 | 0 |

_A lifecycle failure stage without a matching task record remains unknown; later failure labels are not treated as attempt evidence._

source: [`build_generalization_funnel`](https://github.com/glockyco/Teralizer/blob/d3f3690dc17c9ef6cf6ffea3f15b5bddb7936ea4/analysis/src/teralizer/eval/reports/_generalization_funnel.py#L276)

Generic JPF uncaught-exception diagnostics are reclassified from their retained detail into application exceptions and JPF environment gaps.

**Retrospective classification of generic JPF uncaught-exception diagnostics from retained detail.**

| Recovered cause | Diagnostics | Share |
| --- | --- | --- |
| Application exception | 1,378 | 76.8% |
| JPF native-peer gap | 255 | 14.2% |
| JPF model/field gap | 118 | 6.6% |
| Unparsed | 43 | 2.4% |

_This recovery changes cause attribution only; it does not change project eligibility or funnel outcomes._

source: [`fetch_jpf_exception_causes`](https://github.com/glockyco/Teralizer/blob/2fc87b1642f4912e01d8c9dd2823e9ab1fdcea5a/analysis/src/teralizer/eval/reports/_diagnostics.py#L95)

ParameterType choice sensitivity is reported conservatively: only a rejection with an observed argument-taking alternative is choice-dependent.

**Choice sensitivity of ParameterType rejections classified from retained MUT candidate details.**

| Candidate evidence | Rejections | Share of all |
| --- | --- | --- |
| Candidate detail unavailable | 49,801 | 75.6% |
| Choice-invariant | 4,686 | 7.1% |
| Choice-dependent | 11,363 | 17.3% |

_Choice-dependent rows divided by all ParameterType rejections are a lower bound; rows without candidate detail remain unscored._

source: [`fetch_mut_choice_sensitivity`](https://github.com/glockyco/Teralizer/blob/2fc87b1642f4912e01d8c9dd2823e9ab1fdcea5a/analysis/src/teralizer/eval/reports/_diagnostics.py#L180)

**Included entities and exclusion mechanisms for Improved (200 tries), with shares of each entity-level population.**

| Level | Mechanism | Outcome | Entities | Level total |
| --- | --- | --- | --- | --- |
| Test | Included | included | 44,875 (52.6%) | 85,368 |
| Test | Filter rejection | filtering | 37,363 (43.8%) | 85,368 |
| Test | Unsupported capability | filtering | 2,835 (3.3%) | 85,368 |
| Test | Task exception | failures | 295 (0.3%) | 85,368 |
| Assertion | Included | included | 6,905 (3.8%) | 180,548 |
| Assertion | Filter rejection | filtering | 166,602 (92.3%) | 180,548 |
| Assertion | Build quarantine | failures | 182 (0.1%) | 180,548 |
| Assertion | Task exception | failures | 6,859 (3.8%) | 180,548 |
| Generalization | Included | included | 1,615 (30.2%) | 5,356 |
| Generalization | Filter rejection | filtering | 420 (7.8%) | 5,356 |
| Generalization | Generation-time gate | filtering | 3,299 (61.6%) | 5,356 |
| Generalization | Build quarantine | failures | 5 (0.1%) | 5,356 |
| Generalization | Task exception | failures | 17 (0.3%) | 5,356 |

source: [`fetch_mechanism_partition`](https://github.com/glockyco/Teralizer/blob/da8dfb508e363bd66c05e6739ddf0a5d3c63f6d2/analysis/src/teralizer/eval/reports/_exclusion_evidence.py#L536)

**Exclusion results for Improved (200 tries) in the RepoReapers projects.**

| Level | Total | Included | Filtering | Failures |
| --- | --- | --- | --- | --- |
| Test | 85,368 | 44,875 (52.6%) | 40,198 (47.1%) | 295 (0.3%) |
| Assertion | 180,548 | 6,905 (3.8%) | 166,602 (92.3%) | 7,041 (3.9%) |
| Generalization | 5,356 | 1,615 (30.2%) | 3,719 (69.4%) | 22 (0.4%) |

source: [`fetch_mechanism_partition`](https://github.com/glockyco/Teralizer/blob/da8dfb508e363bd66c05e6739ddf0a5d3c63f6d2/analysis/src/teralizer/eval/reports/_exclusion_evidence.py#L536)

**Filtering results for Improved (200 tries) in the RepoReapers projects.**

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

source: [`fetch_filter_decisions`](https://github.com/glockyco/Teralizer/blob/da8dfb508e363bd66c05e6739ddf0a5d3c63f6d2/analysis/src/teralizer/eval/reports/_exclusion_evidence.py#L526)

The reader-facing filtering column combines filter decisions, generation-gate refusals, and unsupported-capability declinations. The preceding table preserves the exact mechanisms.

**Causes for generalization attempts that produce no generalized test in the real-world dataset.**

| Refusal cause | Generalizations | All refusals | Refusals | All attempts | Attempts |
| --- | --- | --- | --- | --- | --- |
| Null output model, and the returned value is not a bytecode literal | 2,552 | 3,298 | 77.4% | 5,356 | 47.6% |
| Null output model, and a native call received a symbolic argument | 488 | 3,298 | 14.8% | 5,356 | 9.1% |
| Null output model, and no generated parameter reaches the path condition | 206 | 3,298 | 6.2% | 5,356 | 3.8% |
| Null output model, and the path condition does not pin every generated parameter | 38 | 3,298 | 1.2% | 5,356 | 0.7% |
| Exception oracle, concretized with a risk of divergence | 13 | 3,298 | 0.4% | 5,356 | 0.2% |
| Exception oracle, and the path condition does not pin every generated parameter | 1 | 3,298 | 0.0% | 5,356 | 0.0% |

_Refusals are decided before a generalized test is written, so they carry no filter decision and no lifecycle record._

source: [`fetch_widening_refusals`](https://github.com/glockyco/Teralizer/blob/d3f3690dc17c9ef6cf6ffea3f15b5bddb7936ea4/analysis/src/teralizer/eval/reports/_widening.py#L98)
