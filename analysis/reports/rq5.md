# RQ5 - Causes of Unsuccessful Generalization (Controlled)

_Source database: `postgres_dev`._

## Exclusion breakdown

Controlled-dataset exclusions separate successful inclusions from proactive filter rejections and task failures.

**Test, assertion, and generalization exclusions by filtering versus failures for the controlled dataset.**

| Strategy | Level | Total | Included | Incl. % | Filtering | Filt. % | Failures | Fail. % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| All | Test | 23,246 | 19,306 | 83.1% | 3,933 | 16.9% | 7 | 0.0% |
| All | Assertion | 28,923 | 13,836 | 47.8% | 12,092 | 41.8% | 2,995 | 10.4% |
| BASELINE | Generalization | 13,836 | 13,814 | 99.8% | 22 | 0.2% | 0 | 0.0% |
| NAIVE_10_TRIES | Generalization | 13,836 | 10,743 | 77.6% | 3,061 | 22.1% | 32 | 0.2% |
| NAIVE_50_TRIES | Generalization | 13,836 | 9,964 | 72.0% | 3,840 | 27.8% | 32 | 0.2% |
| NAIVE_200_TRIES | Generalization | 13,836 | 9,881 | 71.4% | 3,923 | 28.4% | 32 | 0.2% |
| IMPROVED_10_TRIES | Generalization | 13,836 | 11,788 | 85.2% | 2,016 | 14.6% | 32 | 0.2% |
| IMPROVED_50_TRIES | Generalization | 13,836 | 11,660 | 84.3% | 2,144 | 15.5% | 32 | 0.2% |
| IMPROVED_200_TRIES | Generalization | 13,836 | 11,597 | 83.8% | 2,207 | 16.0% | 32 | 0.2% |

source: [`_fetch_breakdown`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/rq5_causes.py#L215)

**Filter rejection rates by level and filter for the controlled dataset.**

| Level | Filter Name | Total | Accept | Defer | Reject |
| --- | --- | --- | --- | --- | --- |
| Test | NonPassingTest | 23,246 | 93.4% | 0.0% | 6.6% |
| Test | TestType | 23,246 | 99.2% | 0.0% | 0.8% |
| Test | NoAssertions | 21,532 | 89.7% | 0.0% | 10.3% |
| Assertion | AssertionType | 28,923 | 97.4% | 0.0% | 2.6% |
| Assertion | ExcludedTest | 28,923 | 94.5% | 0.0% | 5.5% |
| Assertion | MissingValue | 28,923 | 75.3% | 0.0% | 24.7% |
| Assertion | ParameterType | 28,923 | 61.7% | 22.9% | 15.4% |
| Assertion | VoidReturnType | 28,923 | 75.2% | 24.7% | 0.0% |

source: [`_fetch_filtering`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/eval/reports/rq5_causes.py#L205)
