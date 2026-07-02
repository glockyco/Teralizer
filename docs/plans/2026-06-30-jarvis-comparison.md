---
title: JARVIS Comparison (RQ0)
type: spec
status: active
created: 2026-06-30
parent: 2026-06-26-teralizer-overview
---

# JARVIS Comparison (RQ0)

The single home for how Teralizer compares to JARVIS (VMCAI 2018, the closest prior
CUT→PBT generalizer) and all the evidence behind it — the paper's **RQ0**. Design and
measured results live here together so the whole comparison is graspable in one place.
When the analysis notebook lands it takes over the number-carrying and this doc points at
it; until then the figures are inline and current (scoreboard + census re-run on current
code, 2026-06-30).

JARVIS on its own terms (mechanism, assumptions, limitations) and the paper's claims about
it are writing-side references in the paper repo:
`~/Projects/test-generalization-paper/docs/notes/{jarvis-process,jarvis-claims}.md`. JARVIS
paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug). Table 2 is
encoded verbatim in `analysis/src/teralizer/jarvis_scoreboard.py` (`JARVIS_TABLE2`).

## Framing: a published-result benchmark, not a re-run

JARVIS's implementation is not public, so we cannot re-run it. We run a **published-result
benchmark comparison** against its Table 2 — IC and PVC for the original JUnit test (CUT) and
the JARVIS-generated ScalaCheck PBT, on 10 cases from Commons-Lang/Commons-Math, the exact
projects we pin. The claim is "we reproduce JARVIS's Table-2 setting and place Teralizer
beside its reported numbers, with reconstructed metrics and stated caveats," never "we re-ran
JARVIS." Positioned as **RQ0** (kept as RQ0 for now, ahead of the mutation-score RQ1): the
comparison earns the paper's metric choice before RQ1 asserts it.

## Unit of comparison

A **JARVIS Table-2 row is one reported generalization case**, listed per test method (or per
class when it spans several methods — hence `IntervalTest`/`PrecisionTest` carry no
`::method`). A row is not necessarily one generated property (JARVIS emits a positive and a
negative property per parameterized test; its PVC aggregation is under-specified), so the row
count is *JARVIS-reported cases*, not a literal PBT count. The 10 rows do **not** map 1:1 to
methods-under-test: the 3 `PolynomialFunctionTest` rows all test `PolynomialFunction.value`,
while `testMinMaxDouble` is one row over two MUTs (`FastMath.min`, `FastMath.max`; PDF line
632). This is **10 reported cases across 9 distinct MUTs in 6 test classes** — independent
tallies, not a nesting: cases exceed MUTs because several test methods share one MUT.

A **Teralizer "sound generalization" is one per-assertion property** (`jqwik_property_execution`
with `diagnostic_kind = 'FULL'`).

| unit (coarse → fine) | JARVIS Table 2 | Teralizer | comparable? |
|---|--:|--:|---|
| test classes with ≥1 success | 6 | 6 (census) | weak — different classes |
| **distinct production MUTs** | **9** | **8** (Table-2 fixture) / **26** (census breadth) | **yes — the fair unit** |
| reported Table-2 cases | 10 | (n/a) | JARVIS-native (scenario-depth) |
| sound assertion-properties | (n/a) | 250 (census) | Teralizer-native (throughput only) |

**The comparable unit is the distinct method-under-test (MUT)** — the production method whose
tested behaviour each tool turns into a property; the only unit both tools express, and a
property of the production code, not of test-suite structure. The row count is a valid
*scenario-depth* measure (distinct behavioural regions — e.g. `value` at constant/linear/
derivative); MUT count is *method-breadth*. The 250 assertion-properties are Teralizer
throughput, reported as such, never opposite JARVIS's 10 cases. MUT identity keys on
`tested_class` + `tested_method`; overloads (`min`/`max` double-overloads) collapse unless
keyed on the argument signature — footnote that for `min`/`max`/`value`/`equals`.

## Metrics

**IC — preserved by construction (a contrast, not a headline).** IC preservation is one of
JARVIS's own two coverage questions (PDF line 567), i.e. an explicit goal, but it achieves it
only empirically — "at least as great … in every case but one" (line 612), "in most cases"
(line 634). The one loss is `testMinMaxDouble`: JARVIS's generators cannot sample `Double.NaN`,
so a NaN path goes uncovered (line 632); the flip side is uncontrolled *gain* —
`IntervalTest` IC jumps 38 → 3869 (line 626) as the over-broad generator wanders into the bug
path. Teralizer **guarantees** IC by construction: every generated input replays the exact
symbolic path the seed exercised, so it cannot fall short of or wander out of it. Empirical
non-regression check (census, current code, IMPROVED_100): commons-lang 3610 = 3610 covered
(identical), commons-math 44332 → 44344 (+0.03%). Project-level sanity check, consistent with
the guarantee (not a per-seed path-identity proof).

**PVC — not construct-equivalent; report as a ratio, then critique.** Teralizer PVC = distinct
jqwik values per MUT parameter at N tries, summed over a row's probes, from the generated
property's value log. JARVIS PVC = distinct parameter values at ScalaCheck's 100-test default
(PDF line 610, citing Sampath 2008); its reported values exceed 100 on several rows
(`testAbs` 506, `testMinMaxDouble` 400), so it is not a simple 100-draw count and its exact
aggregation is unspecified. Consequence: raw absolute PVC is not construct-equivalent across
tools. Report (1) the **PVC-multiplier over the same CUT** (PBT-PVC / CUT-PVC — JARVIS's own
"~26×" headline; the ratio cancels most of the instrumentation/aggregation difference), and
(2) raw absolute PVC only as a caveated secondary. And then dismantle PVC as an effectiveness
metric (Axis 2).

**Mutation score — the honest effectiveness metric.** Covered mutation score (killed / mutants
the tests actually reach) is the fault-detection metric PVC fails to be, and the one the rest
of the evaluation uses. RQ0 earns it.

## Axis 1 — Table-2 head-to-head (@100 tries)

Source: scoreboard fixture `JARVIS_TABLE2` / `postgres_jarvis_scoreboard` (it hand-targets all
10 rows; the census does not promote `FastMath`/`Interval`/`Univariate`). `IMPROVED_100_TRIES`
Teralizer PVC vs JARVIS's published Scala-PBT PVC:

| Table row | params | Teralizer PVC | JARVIS PBT PVC | verdict |
|---|---|---:|---:|:--|
| `CharUtilsTest::isAscii` | char | 148 | 59 | win |
| `CharUtilsTest::isPrintable` | char | 127 | 45 | win |
| `FastMathTest::testMinMaxDouble` | double² | 304 | 400 | trail |
| `FastMathTest::toIntExact` | int | 90 | 65 | win |
| `IntervalTest` | double² | 88 | 2 | win |
| `PolynomialFunctionTest::testConstants` | double | 90 | 105 | trail |
| `PolynomialFunctionTest::testfirstDerivativeComparison` | double | 89 | 264 | trail |
| `PolynomialFunctionTest::testLinear` | double | 90 | 160 | trail |
| `PrecisionTest` (eps) | double³ | — | 102 | absent (sound-excluded) |
| `UnivariateFunctionTest::testAbs` | double | 94 | 506 | trail |

**Capability — 8 of JARVIS's 9 MUTs are soundly generalized** (`CharUtils.isAscii`/
`isAsciiPrintable`, `FastMath.min`/`max`/`toIntExact`, `Interval.getSize`,
`PolynomialFunction.value`, `Abs.value`). The 10 Table-2 rows map to **11 tracked probes**
(`min`/`max` split `testMinMaxDouble` into two). The scoreboard fixture also includes a
12th diagnostic test, `precisionEqualsMaxUlps` (`Precision.equals(double, double, int maxUlps)`),
which is not a Table-2 case — the Table-2 `PrecisionTest` row is the `eps` overload
(`equals(double, double, double)`, parameter space `double³`), not the `maxUlps` overload
(`double² + int`). The maxUlps test is an extra fixture for the raw-bits/ULP investigation; it is
not tracked in `JARVIS_TABLE2` and cannot offset the missing eps overload — it is a
different method signature, not a substitute. Capability against the JARVIS target set
remains 8/9.

The one gap is `Precision.equals` (eps) — a deliberate soundness abstention (the raw-bits/
ULP case; SPF cannot capture the 1-ULP disjunct in rational-real mode, so the abs-branch-only
generalization is unsound and fails loud rather than emit it). Principled, but a real coverage
tradeoff: JARVIS generalizes it by over-approximating, Teralizer does not. That the rejected
paper handled 9/10 of these rows only because its static-method/numeric selection excluded
them is the headline: `char`, instance-method/object-construction, and FastMath all now enter
and pass.

**PVC — win 4, trail 5, absent 1.** Wins: both `char` rows, `toIntExact`, `IntervalTest`. The
5 trails are single-`double` rows: JARVIS pairs multiple ScalaCheck generators per scenario,
so its per-`double` PVC (264, 506…) exceeds one jqwik arbitrary's ~90 at 100 checks — a
sampling-strategy difference, not a soundness or capability gap. `isPrintable`/`toIntExact`
being JARVIS "regressions" vs its own CUT is JARVIS's 100-iteration cap losing to original
tests that loop over thousands of values (PDF lines 612, 628).

**SPF spike (first-hand, pinned stack; full report `spf-eval/jarvis-spike/jarvis-spf-RESULTS.md`):
8 FULL · 1 PARTIAL · 2 BLOCKED.** FULL: `isAscii`/`isAsciiPrintable` (char→SYMINT), `min`/`max`
(NaN/−0.0 arms unmodeled over rationals), `Interval.getSize`, `PolynomialFunction.value`
(const/linear/deriv — symbolic flow through constructor→fields→Horner→a second constructed
object), `Abs.value`. PARTIAL: `toIntExact` (5-region partition correct; overflow path missing,
`(int)` cast unmodeled — symcrete `LCMP`). BLOCKED: both `Precision.equals` overloads in
rational-real mode (no raw-bits model; the eps path's `equals(x,y,1)` 1-ULP disjunct is
silently concretized). Headline: SPF spec quality is gated by which JVM primitives the
implementation reaches, not mathematical difficulty — object construction + instance-method +
symbolic-array propagation work robustly, so the barrier that kept 9/10 rows out of the dataset
was a Teralizer selection criterion, not an SPF limit.

## Axis 2 — Budget elasticity (PVC is a knob; kills are not)

Source: `postgres_jarvis_scoreboard`, full 6-variant + PIT sweep, current code. Reproduce:
`run-jarvis-scoreboard.sh --reset-db` then
`uv run --directory analysis python -m teralizer.jarvis_scoreboard --sweep`.

| variant | tests | total PVC | killed | covered | covered score |
|---|--:|--:|--:|--:|--:|
| NAIVE_100_TRIES | 12 | 1073 | 51 | 78 | 0.654 |
| NAIVE_200_TRIES | 12 | 2245 | 51 | 78 | 0.654 |
| NAIVE_1000_TRIES | 12 | 11092 | 51 | 78 | 0.654 |
| IMPROVED_100_TRIES | 12 | 1120 | 51 | 78 | 0.654 |
| IMPROVED_200_TRIES | 12 | 2257 | 51 | 78 | 0.654 |
| IMPROVED_1000_TRIES | 12 | 11095 | 51 | 78 | 0.654 |

**PVC inflates ~10× with the tries budget; kills and the covered mutation score are dead flat**
(51 killed / 78 covered / 65.4% for every variant, both generators). The covered denominator
is 78 of the project's 2953 mutants — the rest are `NO_COVERAGE`, code the tests never touch
(scoring against 2953 gives a meaningless 1.7%). Extra tries buy input diversity, not fault
detection; the covered score does not even discriminate NAIVE from IMPROVED (both kill 51/78).

Per-test, the same, with a telling exception (`IMPROVED`, distinct values):

| test | 100 | 200 | 1000 | note |
|---|--:|--:|--:|---|
| `intervalGetSize` | 88 | 260 | 1771 | ~20× (unbounded double) |
| `minDouble` / `maxDouble` | 152 | 322 | 1837 | ~12× |
| `polynomialConstant` / `Linear` | 90 | 185 | 920 | ~10× |
| `absValue` | 94 | 185 | 915 | ~10× |
| `toIntExact` | 90 | 176 | 811 | ~9× (long) |
| `isAscii` | 148 | 311 | 1038 | ~7× (char) |
| `isAsciiPrintable` | 127 | 127 | 127 | **flat — bounded printable-char domain** |

`isAsciiPrintable` caps at 127 because IMPROVED enumerates the whole printable-char partition
by construction; more tries cannot add values. A metric that swings 7–20× on the same test
purely from the budget (or saturates a finite domain) measures the input space, not
fault-finding power — the per-test kill count is unchanged across the budget.

**Why the covered gap is structural, not a generator bug.** The 27 covered-but-unkilled mutants
(IMPROVED_100) break down as **10 boundary/comparison flips** (`ConditionalsBoundaryMutator` on
`min`/`max`/`toIntExact`/`Precision.equals`/`isAsciiPrintable`), **10 removed conditionals**
(`RemoveConditional` guards in `PolynomialFunction.{<init>,differentiate,evaluate}`,
`MathUtils.checkNotNull`, `FastMath.{min,max,toIntExact}`), **4 arithmetic** (`MathMutator` on
`abs`, `Precision.equals`, `differentiate`), **3 void-call removals** (`VoidMethodCallMutator`
on `PolynomialFunction.{differentiate,evaluate,<init>}`). None is reachable by path-exact
generalization. `toIntExact` is illustrative: the generated filter `n > MIN && n < MAX` is
strict, not inclusive `[MIN, MAX]`, which looks like a dropped-equality bug but is correct —
`lcmp` is tri-state and `jpf-symbc`'s `LCMP` handler records the *concrete* outcome as its own
symbolic path (for `n = 7`, `n vs MIN → GT`, `n vs MAX → LT`, giving the strict path
condition). The equality endpoints where the boundary mutant flips are the `EQ` choice — a
*different* symbolic path the test never executed. The killing input is off the generalized
path, so raising the covered score needs new *original* tests on the boundary/invalid-input
paths, or stronger assertions — both outside "generalize the existing test." The gap is a
property of path-exact generalization and oracle strength, not a defect.

## Axis 3 — Breadth beyond JARVIS (the census)

Beyond the 10 Table-2 cases, run Commons-Lang/Math's **own** numeric/char test classes through
the pipeline and let the filters + SPF decide feasibility. Source: `postgres_jarvis_census`,
IMPROVED_100, current code; `uv run --directory analysis python -m teralizer.jarvis_scoreboard
--census`. Honesty bound: claim "beyond JARVIS's *reported* set," never "JARVIS failed on test
X" (the paper publishes only successes). JARVIS structurally needs ≥2 repetitive traces per
scenario and numeric/char-primitive templates; Teralizer generalizes from a single test via
symbolic paths.

**26 distinct MUTs soundly generalized across 6 real upstream classes** (250 sound
assertion-properties):

| class | distinct MUTs | sound properties |
|---|--:|--:|
| `CharUtilsTest` | 10 | 82 |
| `ArithmeticUtilsTest` | 6 | 44 |
| `BooleanUtilsTest` | 4 | 18 |
| `MathArraysTest` | 3 | 36 |
| `NumberUtilsTest` | 2 | 41 |
| `PolynomialFunctionTest` | 1 | 29 |
| `PrecisionTest` | 0 | 0 (raw-bits sound-excluded) |

Two head-to-head reads: **within** a JARVIS class Teralizer covers a superset (`CharUtils`
2 → 10 MUTs, including both of JARVIS's plus `isAsciiControl/Alpha/Alphanumeric/Numeric/
AlphaUpper/AlphaLower`, `toIntValue`, `toChar`); and it adds whole classes JARVIS never
reported (`ArithmeticUtils`, `MathArrays`, `BooleanUtils`, `NumberUtils`). vs JARVIS's ~8
Math + 2 Lang reported cases, this is a large applicability gain.

**Fault-detection gain** (killed-mutant-key set difference, GENERALIZED \ INITIAL, same covered
classes): **+1 (commons-lang), +7 (commons-math)** over the seed tests — augmented GENERALIZED
score 634/771 (lang), 910/1409 (math). High input diversity, modest extra kills — the same
PVC-vs-kills disconnect as Axis 2.

**By-reason rejection tally** (the other half of the result; `REJECT` excludes, `DEFER` is
informational). Dominant rejects: `MissingValueFilter` (393 lang / 314 math),
`ParameterTypeFilter` — the type ceiling (317 lang / 42 math), `ExcludedTestFilter` (280 math),
`NonPassingTestFilter` (59 lang / 188 math), `ReturnTypeFilter` (112 lang / 24 math),
`UnsupportedAssertionFilter` (22 lang / 81 math), `NoAssertionsFilter` (68 lang / 10 math).
Loop/structure cases are `DEFER` (`AssertionInLoopFilter`, `TestedMethodInLoopFilter`, …) —
JARVIS's target, Teralizer's non-target; the funnel records both directions.

## Axis 4 — Soundness / automation tradeoff

JARVIS over-approximates: its abstractions can admit inputs that fail the reused oracle, and
its own §5.1 ("Handling Impreciseness") expects raw output to need manual triage; its bug
findings (MATH-1256, MATH-785) were same-version over-approximation failures, one of which
needed manual generator refinement. Teralizer extracts path-exact specifications and fails
loud / excludes unsound cases (the raw-bits `Precision.equals`), trading some coverage for
soundness. This is the qualitative axis; the exclusion ledger (Axis 3 rejection tally) and the
`Precision` abstention (Axis 1) are its quantitative face.

## Threats to validity

- **Construct:** PVC is not construct-equivalent across tools (different instrumentation;
  JARVIS's aggregation under-specified). Mitigated by leading with IC-preservation and
  PVC-multiplier-over-CUT, and by reporting mutation score as the effectiveness metric.
- **Internal:** our fixtures replicate JARVIS's Table-2 setting but are not JARVIS's exact
  harness; we compare to its *published* numbers, not a re-run. Scoreboard/census numbers are
  point-in-time (jqwik sampling varies run to run); the qualitative results are robust.
- **External:** the head-to-head is 10 cases across 2 projects (JARVIS's own reported set); the
  breadth census is the same 2 projects.

## Provenance & reproduction

- Fixtures pinned: commons-math `MATH_3_5` = `b3c5dae8f253fcb4484e5cd3cc5662587803efc2`,
  commons-lang `LANG_3_5` = `36f98d87b24c2f542b02abbf6ec1ee742f1b158b`; jpf-symbc
  `gradle-build` + jpf-core `java-8` (submodule SHAs in the main repo), JDK 8, Z3 4.11.2.
- Scoreboard (Table-2 + elasticity): `postgres_jarvis_scoreboard`, `data/jarvis-scoreboard/`;
  `bash scripts/run-jarvis-scoreboard.sh --reset-db`, then
  `uv run --directory analysis python -m teralizer.jarvis_scoreboard [--sweep]`.
- Census (breadth): `postgres_jarvis_census`, `data/jarvis-census/`;
  `bash scripts/run-jarvis-census.sh --reset-db`, then `… jarvis_scoreboard --census`.
- Corpus rationale (why pinned fixtures, not the main dataset): JARVIS ran on the **2018
  monolithic** Apache Commons (`commons-math3`, `commons-lang3`); our main `commons-utils`
  dataset is the **modern modular** line (commons-numbers, math4, lang3 at HEAD ~Feb 2025),
  crawled by a `public static` + numeric/boolean-parameter Sourcegraph query. That criterion
  excluded most Table-2 rows **at dataset-selection time**, not by runtime failure — `char`
  (CharUtils), object-construction + instance methods (Interval, PolynomialFunction,
  UnivariateFunction), and FastMath (not in the extracted set); the one in-corpus row
  (`PrecisionTest`, moved to commons-numbers) is `NoAssertionsFilter`-rejected because modern
  commons-numbers delegates the assertion to a helper taking the MUT as a functional interface.
  So the scoreboard/census run against **pinned JARVIS-era fixtures** (`MATH_3_5`/`LANG_3_5`) as
  execution inputs; the modern corpus is supporting evidence only.
- Granular tables reproduce from the stable analysis, not any scratch script: per-test PVC is
  `jarvis_scoreboard.get_pvc_scores(conn, variants=[v])` grouped by `generated_method_name`;
  the survivor breakdown is distinct covered-and-unkilled mutants from `pit_mutation_report`
  (GENERALIZED, IMPROVED_100), same distinct mutant-key as `get_mutation_scores`.
- Runbook + traps: `skill://running-the-jarvis-scoreboard`. Full SPF spike report (live
  artifact): `spf-eval/jarvis-spike/jarvis-spf-RESULTS.md`.
