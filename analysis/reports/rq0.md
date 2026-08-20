# RQ0 - JARVIS Comparison

_Source database: `postgres_jarvis_scoreboard`._

## Reported-case comparison

The JARVIS publication reports CUT and PBT PVC for ten cases from commons-lang and commons-math (its Table 2), with PBT PVC collected from the synthesized properties alone at the ScalaCheck default of 100 samples. Cases aggregate all properties JARVIS synthesized for one scenario while Teralizer creates one generalized test per assertion, so the comparison aligns on distinct MUTs. 7 of 10 reported cases have a matching generalized test, covering 7 of 9 distinct MUTs.

The JARVIS implementation and template library are unavailable, so JARVIS is not rerun and its Table-2 rows serve as the comparison reference. A reported PBT PVC counts the values that the synthesized properties sampled. IntervalTest reports 2 because its property stopped on the second sample, so that cell counts a run that ended rather than the values a passing property covered.

Teralizer extracts its specification from a single execution. Generalized tests exercise the original inputs as their first samples by design, so coverage after generalization never falls below the original tests' values. The Teralizer column reports the measured value coverage after generalization, joining the captured original-suite values with the generalized tests' value logs.

**PVC before generalization, after generalization with JARVIS, and after generalization with \ToolTeralizer{} for each of the 10 scenarios reported by JARVIS.**

| \# | JARVIS scenario | CUT PVC | PBT PVC | PBT PVC |
| --- | --- | --- | --- | --- |
| 1 | \texttt{isAscii} | 6 | 59 | 230 |
| 2 | \texttt{isPrintable} | 195 | 45 | 197 |
| 3 | \texttt{testMinMaxDouble} | 9 | 400 | 368 |
| 4 | \texttt{toIntExact} | 2,001 | 65 | 2,074 |
| 5 | \texttt{IntervalTest} | 2 | 2 | 172 |
| 6 | \texttt{testConstants} | 5 | 105 | — |
| 7 | \texttt{testfirstDerivativeComparison} | 7 | 264 | 105 |
| 8 | \texttt{testLinear} | 5 | 160 | 104 |
| 9 | \texttt{PrecisionTest} | 8 | 102 | — |
| 10 | \texttt{testAbs} | 5 | 506 | — |

_JARVIS CUT and PBT PVC are the published values, with PBT PVC measuring the synthesized properties alone. For Teralizer, PVC includes the reconstructed original tests' values and the generalized tests' values. A dash marks a scenario Teralizer excludes from generalization._

source: [`compare_to_jarvis`](https://github.com/glockyco/Teralizer/blob/b5112480c27aad999d9308a996ebc3ee9ef7fe5c/analysis/src/teralizer/jarvis_scoreboard.py#L838)

## Applicability breadth

RQ0 uses a separate, pinned fixture set reproducing the twelve Apache Commons project versions of the JARVIS evaluation. RQ1--RQ5 use the constructed commons-utils dataset. The JARVIS columns aggregate the reported cases by project. Teralizer PVC deduplicates values per MUT and parameter across generalized tests, so a value exercised by several tests counts once.

**MUTs with a generalized test and the PVC of those tests, per project.**

| Benchmark project | PBT PVC | MUTs | PBT PVC | MUTs |
| --- | --- | --- | --- | --- |
| commons-math | 1,604 | 7 | 12,471 | 90 |
| commons-lang | 104 | 2 | 26,079 | 208 |
| commons-cli | — | — | 217 | 2 |
| commons-codec | — | — | 983 | 6 |
| commons-collections | — | — | 102 | 2 |
| commons-configuration | — | — | 1,403 | 9 |
| commons-csv | — | — | 4 | 1 |
| commons-email | — | — | — | — |
| commons-io | — | — | 774 | 7 |
| commons-jexl | — | — | — | — |
| commons-pool | — | — | — | — |
| commons-text | — | — | 1,144 | 12 |
| all 12 projects | 1,708 | 9 | 43,177 | 337 |

_A dash in the JARVIS columns marks a project that the publication reports no case for. A dash in the Teralizer columns marks a project for which the pipeline produced no generalized test. Teralizer aggregate PVC counts distinct values exercised by generalized tests for each MUT and parameter. Generalized MUTs have at least one generalized test._

source: [`census_project_frame`](https://github.com/glockyco/Teralizer/blob/0ef9a91bee8787db60d33691db27ebe9493cc501/analysis/src/teralizer/eval/evidence/jarvis_values.py#L223)

Census status partial. The census intended 12 projects: 7 completed, 3 failed, and the run did not reach 2. 9 projects carry persisted PVC rows. Completion marker present.

## PVC and mutation score

A larger sampling budget can raise PVC by exercising more distinct values. In this sweep, the same 10 tests kill 51 of 80 covered mutants at every budget. Their covered mutation score therefore stays at 63.7%. More sampling effort does not change detection of the selected mutants in this sweep.

**PVC and mutation testing results for the same 10 generalized tests at 3 sampling budgets. Only the sampling budget changes between the rows. PVC sums the distinct input values that the tests exercise, and Score is the killed mutants divided by the covered mutants.**

| Sampling Budget | Tests | PVC | Killed | Covered | Score |
| --- | --- | --- | --- | --- | --- |
| 100 tries | 10 | 1,135 | 51 | 80 | 63.7% |
| 200 tries | 10 | 2,152 | 51 | 80 | 63.7% |
| 1,000 tries | 10 | 10,055 | 51 | 80 | 63.7% |

_The same mutant sets are covered and killed at every budget._

source: [`_build_budget_table`](https://github.com/glockyco/Teralizer/blob/b3fd3d5d1bfde88b63e71abad1b762c3c181ec17/analysis/src/teralizer/eval/reports/rq0_jarvis.py#L255)
