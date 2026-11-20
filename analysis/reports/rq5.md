# RQ5 - Causes of Unsuccessful Generalization (Controlled)

_Source database: `postgres_dev`._

## Exclusion breakdown

Controlled-dataset exclusions separate successful inclusions from proactive filter rejections and task failures.

**Exclusion results for tests, assertions, and generalizations in the Commons and EqBench-ES projects.**

| Strategy | Level | Total | Included | Filtering | Failures |
| --- | --- | --- | --- | --- | --- |
| All | Test | 23,246 | 19,306 (83.1%) | 3,933 (16.9%) | 7 (0.0%) |
| All | Assertion | 28,923 | 13,836 (47.8%) | 12,092 (41.8%) | 2,995 (10.4%) |
| Baseline | Generalization | 13,836 | 13,814 (99.8%) | 22 (0.2%) | 0 (0.0%) |
| Naive (10 tries) | Generalization | 13,836 | 10,743 (77.6%) | 3,061 (22.1%) | 32 (0.2%) |
| Naive (50 tries) | Generalization | 13,836 | 9,964 (72.0%) | 3,840 (27.8%) | 32 (0.2%) |
| Naive (200 tries) | Generalization | 13,836 | 9,881 (71.4%) | 3,923 (28.4%) | 32 (0.2%) |
| Improved (10 tries) | Generalization | 13,836 | 11,788 (85.2%) | 2,016 (14.6%) | 32 (0.2%) |
| Improved (50 tries) | Generalization | 13,836 | 11,660 (84.3%) | 2,144 (15.5%) | 32 (0.2%) |
| Improved (200 tries) | Generalization | 13,836 | 11,597 (83.8%) | 2,207 (16.0%) | 32 (0.2%) |

source: [`_fetch_breakdown`](https://github.com/glockyco/Teralizer/blob/a421ce6ab66c4d6666716bc8acb3c5a9ab0d7764/analysis/src/teralizer/eval/reports/rq5_causes.py#L404)

**Filtering results for tests and assertions in the Commons and EqBench-ES projects.**

| Level | Filter Name | Evaluated | Accept | Defer | Reject |
| --- | --- | --- | --- | --- | --- |
| Test | NonPassingTest | 23,246 | 21,719 (93.4%) | — | 1,527 (6.6%) |
| Test | TestType | 23,246 | 23,066 (99.2%) | — | 180 (0.8%) |
| Test | NoAssertions | 21,532 | 19,306 (89.7%) | — | 2,226 (10.3%) |
| Assertion | MissingValue | 28,923 | 21,766 (75.3%) | — | 7,157 (24.7%) |
| Assertion | ParameterType | 28,923 | 17,835 (61.7%) | 6,630 (22.9%) | 4,458 (15.4%) |
| Assertion | ExcludedTest | 28,923 | 27,326 (94.5%) | — | 1,597 (5.5%) |
| Assertion | AssertionType | 28,923 | 28,180 (97.4%) | — | 743 (2.6%) |
| Assertion | VoidReturnType | 28,923 | 21,763 (75.2%) | 7,157 (24.7%) | 3 (0.0%) |

source: [`_fetch_filtering`](https://github.com/glockyco/Teralizer/blob/a421ce6ab66c4d6666716bc8acb3c5a9ab0d7764/analysis/src/teralizer/eval/reports/rq5_causes.py#L394)

**Causes of assertion-level failures during specification extraction in the Commons and EqBench-ES projects, with shares of all failures and of all assertions.**

| Group | Cause | Assertions | Share of assertions |
| --- | --- | --- | --- |
| SPF exception | ArithmeticException (division by zero) | 827 (27.6%) | 2.9% |
| SPF exception | NoSuchMethodException | 534 (17.8%) | 1.8% |
| SPF exception | Other SPF/JPF exceptions | 179 (6.0%) | 0.6% |
| Exceeded analysis limit | Path condition size limit | 790 (26.4%) | 2.7% |
| Exceeded analysis limit | Search depth limit | 524 (17.5%) | 1.8% |
| Exceeded analysis limit | Execution timeout | 28 (0.9%) | 0.1% |
| Exceeded analysis limit | Out of memory | 16 (0.5%) | 0.1% |
| Teralizer error | NullPointerException while writing the specification | 89 (3.0%) | 0.3% |
| Teralizer error | Specification not collected, no recorded reason | 8 (0.3%) | 0.0% |

source: [`_fetch_assertion_failures`](https://github.com/glockyco/Teralizer/blob/a421ce6ab66c4d6666716bc8acb3c5a9ab0d7764/analysis/src/teralizer/eval/reports/rq5_causes.py#L276)

**Generalized tests that fail on the original program, by failure type and strategy, in the Commons and EqBench-ES projects, with shares of all generalized tests of the strategy.**

| Strategy | Generalized | TooManyFilterMisses | AssertionFailedError | ArithmeticException | NumberFormatException |
| --- | --- | --- | --- | --- | --- |
| Baseline | 13,836 | 0 (0.0%) | 22 (0.2%) | 0 (0.0%) | 0 (0.0%) |
| Naive (10 tries) | 13,836 | 2,233 (16.1%) | 729 (5.3%) | 99 (0.7%) | 0 (0.0%) |
| Naive (50 tries) | 13,836 | 2,938 (21.2%) | 803 (5.8%) | 99 (0.7%) | 0 (0.0%) |
| Naive (200 tries) | 13,836 | 3,005 (21.7%) | 819 (5.9%) | 99 (0.7%) | 0 (0.0%) |
| Improved (10 tries) | 13,836 | 1,189 (8.6%) | 752 (5.4%) | 57 (0.4%) | 18 (0.1%) |
| Improved (50 tries) | 13,836 | 1,223 (8.8%) | 845 (6.1%) | 58 (0.4%) | 18 (0.1%) |
| Improved (200 tries) | 13,836 | 1,265 (9.1%) | 866 (6.3%) | 58 (0.4%) | 18 (0.1%) |

source: [`_fetch_generalization_failures`](https://github.com/glockyco/Teralizer/blob/a421ce6ab66c4d6666716bc8acb3c5a9ab0d7764/analysis/src/teralizer/eval/reports/rq5_causes.py#L301)
