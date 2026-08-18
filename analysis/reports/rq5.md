# RQ5 - Causes of Unsuccessful Generalization (Controlled)

_Source database: `postgres_dev`._

## Exclusion breakdown

Controlled-dataset exclusions separate successful inclusions from proactive filter rejections and task failures.

**Exclusion results for tests, assertions, and generalizations in the \DatasetsCommons{} and \DatasetsEqBenchEs{} projects.**

| Strategy | Level | Total | Included | Filtering | Failures |
| --- | --- | --- | --- | --- | --- |
| \VariantAll{} | Test | 23,246 | 19,306 (83.1%) | 3,933 (16.9%) | 7 (0.0%) |
| \VariantAll{} | Assertion | 28,923 | 13,836 (47.8%) | 12,092 (41.8%) | 2,995 (10.4%) |
| \VariantBaseline{} | Generalization | 13,836 | 13,814 (99.8%) | 22 (0.2%) | 0 (0.0%) |
| \VariantNaiveA{} | Generalization | 13,836 | 10,743 (77.6%) | 3,061 (22.1%) | 32 (0.2%) |
| \VariantNaiveB{} | Generalization | 13,836 | 9,964 (72.0%) | 3,840 (27.8%) | 32 (0.2%) |
| \VariantNaiveC{} | Generalization | 13,836 | 9,881 (71.4%) | 3,923 (28.4%) | 32 (0.2%) |
| \VariantImprovedA{} | Generalization | 13,836 | 11,788 (85.2%) | 2,016 (14.6%) | 32 (0.2%) |
| \VariantImprovedB{} | Generalization | 13,836 | 11,660 (84.3%) | 2,144 (15.5%) | 32 (0.2%) |
| \VariantImprovedC{} | Generalization | 13,836 | 11,597 (83.8%) | 2,207 (16.0%) | 32 (0.2%) |

source: [`_fetch_breakdown`](https://github.com/glockyco/Teralizer/blob/c48a96bbf9414e93853b9a61f540f05d08271c7d/analysis/src/teralizer/eval/reports/rq5_causes.py#L215)

**Filtering results for tests and assertions in the \DatasetsCommons{} and \DatasetsEqBenchEs{} projects.**

| Level | Filter Name | Total | Accept | Defer | Reject |
| --- | --- | --- | --- | --- | --- |
| Test | NonPassingTest | 23,246 | 21,719 (93.4%) | - | 1,527 (6.6%) |
| Test | TestType | 23,246 | 23,066 (99.2%) | - | 180 (0.8%) |
| Test | NoAssertions | 21,532 | 19,306 (89.7%) | - | 2,226 (10.3%) |
| Assertion | AssertionType | 28,923 | 28,180 (97.4%) | - | 743 (2.6%) |
| Assertion | ExcludedTest | 28,923 | 27,326 (94.5%) | - | 1,597 (5.5%) |
| Assertion | MissingValue | 28,923 | 21,766 (75.3%) | - | 7,157 (24.7%) |
| Assertion | ParameterType | 28,923 | 17,835 (61.7%) | 6,630 (22.9%) | 4,458 (15.4%) |
| Assertion | VoidReturnType | 28,923 | 21,763 (75.2%) | 7,157 (24.7%) | 3 (0.0%) |

source: [`_fetch_filtering`](https://github.com/glockyco/Teralizer/blob/c48a96bbf9414e93853b9a61f540f05d08271c7d/analysis/src/teralizer/eval/reports/rq5_causes.py#L205)
