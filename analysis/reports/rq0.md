# RQ0 - JARVIS Comparison

_Source database: `postgres_jarvis_scoreboard`._

## Reported-case comparison

The JARVIS publication reports CUT and PBT PVC for ten cases from commons-lang and commons-math (its Table 2), with PBT PVC collected from the synthesized properties alone at the ScalaCheck default of 100 samples. Cases aggregate all properties JARVIS synthesized for one scenario while Teralizer creates one generalized test per assertion, so the comparison aligns on distinct MUTs. 7 of 10 reported cases have a matching generalized test, covering 7 of 9 distinct MUTs.

JARVIS operates on test code alone and abstracts positive and negative examples through a predefined template library with a fixed ranking. The abstractions deliberately overapproximate, so their precision depends on multiple related tests per scenario, and the values JARVIS reports reflect this mechanism: the isPrintable and toIntExact CUT suites loop over large ranges that exceed 100 property samples, and the IntervalTest property exposed MATH-1256 and failed on its second sample. The JARVIS implementation and template library are unavailable, so JARVIS is not rerun and its Table-2 rows serve as the comparison reference.

Teralizer derives a path-exact specification from each concrete execution through single-path symbolic analysis. Generalized tests exercise the original inputs as their first samples by design, so coverage after generalization never falls below the original tests' values. The Teralizer column reports the measured value coverage after generalization, joining the captured original-suite values with the generalized tests' value logs.

**CUT and PBT PVC reported by JARVIS beside post-generalization suite PVC on the reconstructed fixtures.**

| Reported case | JARVIS CUT PVC | JARVIS PBT PVC | Teralizer suite PVC |
| --- | --- | --- | --- |
| CharUtilsTest::isAscii | 6 | 59 | 230 |
| CharUtilsTest::isPrintable | 195 | 45 | 197 |
| FastMathTest::testMinMaxDouble | 9 | 400 | 368 |
| FastMathTest::toIntExact | 2,001 | 65 | 2,074 |
| IntervalTest | 2 | 2 | 172 |
| PolynomialFunctionTest::testConstants | 5 | 105 | — |
| PolynomialFunctionTest::testfirstDerivativeComparison | 7 | 264 | 105 |
| PolynomialFunctionTest::testLinear | 5 | 160 | 104 |
| PrecisionTest | 8 | 102 | — |
| UnivariateFunctionTest::testAbs | 5 | 506 | — |

_JARVIS CUT and PBT PVC are the published values, with PBT PVC measuring the synthesized properties alone. Teralizer suite PVC unions the reconstructed original tests' values with the generalized tests' values. A dash marks a case Teralizer excludes from generalization._

source: [`compare_to_jarvis`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/jarvis_scoreboard.py#L831)

## Applicability breadth

RQ0 uses a separate, pinned fixture set reproducing the twelve Apache Commons project versions of the JARVIS evaluation. RQ1--RQ5 use the constructed commons-utils dataset. The JARVIS columns aggregate the reported cases by project, and a zero states that the publication reports no case for that project. Teralizer PVC deduplicates values per MUT and parameter across generalized tests, so a value exercised by several tests counts once.

**Project-level PVC and MUT breadth for the RQ0 benchmark fixtures.**

| JARVIS benchmark fixture | JARVIS successful PBT PVC | JARVIS successful MUTs | Teralizer aggregate PVC | Teralizer generalized MUTs |
| --- | --- | --- | --- | --- |
| commons-math-2017-02-01 | 1,604 | 7 | 12,471 | 90 |
| commons-lang-2017-02-01 | 104 | 2 | 26,079 | 208 |
| commons-cli-2017-02-01 | 0 | 0 | 217 | 2 |
| commons-codec-2017-02-01 | 0 | 0 | 983 | 6 |
| commons-collections-2017-02-01 | 0 | 0 | 102 | 2 |
| commons-configuration-2017-02-01 | 0 | 0 | 1,403 | 9 |
| commons-csv-2017-02-01 | 0 | 0 | 4 | 1 |
| commons-email-2017-02-01 | 0 | 0 | — | — |
| commons-io-2017-02-01 | 0 | 0 | 774 | 7 |
| commons-jexl-2017-02-01 | 0 | 0 | — | — |
| commons-pool-2017-02-01 | 0 | 0 | — | — |
| commons-text-2017-02-01 | 0 | 0 | 1,144 | 12 |
| all 12 projects | 1,708 | 9 | 43,177 | 337 |

_JARVIS columns use zero for projects without a reported case. Teralizer aggregate PVC counts distinct values exercised by generalized tests for each MUT and parameter. Generalized MUTs have at least one generalized test._

source: [`get_census_project_pvc`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/jarvis_scoreboard.py#L710)

Census status partial. Intended projects 12, persisted PVC rows 9, complete projects 7, failed projects 3. Completion marker present.

## PVC and mutation score

PVC rewards every additional distinct input value, so it grows with the sampling budget by construction. Killed mutants, covered mutants, and covered mutation score stay flat across the sweep, so mutation score remains the effectiveness measure for the later research questions.

**PVC rises with the tries budget while covered mutation score stays flat.**

| Variant | Probes | Total PVC | Killed mutants | Covered mutants | Covered mutation score |
| --- | --- | --- | --- | --- | --- |
| Improved, 100 tries | 10 | 1,135 | 51 | 80 | 63.7% |
| Improved, 200 tries | 10 | 2,152 | 51 | 80 | 63.7% |
| Improved, 1,000 tries | 10 | 10,055 | 51 | 80 | 63.7% |

_PVC is a generation-volume diagnostic. Rows with persisted PIT results carry kills and mutation scores. Missing PIT results appear as unavailable cells._

source: [`summarize_variants`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/jarvis_scoreboard.py#L993)
