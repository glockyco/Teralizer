---
title: JARVIS Head-to-Head Evidence
type: audit
status: active
created: 2026-06-26
parent: 2026-06-26-teralizer-overview
---

Point-in-time JARVIS head-to-head evidence: per-case SPF spike verdicts, corpus provenance, and scorecard run outputs.

Design spec: `2026-06-27-jarvis-scoreboard-evaluation-lane`. Implementation plan: `2026-06-27-jarvis-scoreboard-evidence-run`.

## Case scorecard (JARVIS paper Table 2)

Lang: `CharUtilsTest::isAscii` (char), `CharUtilsTest::isPrintable` (char).
Math: `FastMathTest::testMinMaxDouble` (double²), `FastMathTest::toIntExact` (int),
`IntervalTest` (double²), `PolynomialFunctionTest::{testConstants,testfirstDerivativeComparison,testLinear}` (double),
`PrecisionTest` (double³), `UnivariateFunctionTest::testAbs` (double).

JARVIS ran on the **2018 monolithic** Apache Commons (commons-math `math3`,
commons-lang). Assertions in that era were inline in the `@Test` body.

## Key findings

1. **Version/era mismatch.** Our `commons-utils` dataset is the **modern modular**
   Apache Commons (`commons-numbers`, `commons-geometry`, `math4`, `lang3`,
   `rng`, `io`, `codec`, …). Class locations and test code differ from JARVIS's.

2. **Only 1 of 10 Table-2 rows is represented in the current modern corpus.** `PrecisionTest` →
   `org.apache.commons.numbers.core.PrecisionTest` (Precision moved math3.util →
   commons-numbers-core). The other JARVIS rows are not bundled in the current corpus.

3. **Exclusion rationale (paper, verbatim).** `sections/04-evaluation-02-framework.tex`
   L94–105: *"we identified public static methods with numeric or boolean
   parameters and return values — the types currently supported by Teralizer."*
   Yielded 247 classes from 17 Apache Commons projects. Most JARVIS Table-2 rows were
   **excluded by design at dataset-selection time**, not by a runtime failure:
   - `Interval`, `PolynomialFunction`, `UnivariateFunction`: object construction +
     instance methods → not public *static*.
   - `CharUtils.isAscii/isPrintable`: `char` param → outside the numeric/boolean
     selection set.
   - `FastMath`: class simply not in the extracted set.

4. **The one in-corpus case is rejected downstream.** `PrecisionTest`'s
   JARVIS-relevant methods (`testEqualsWithAllowedDelta`, `…Ulps`,
   `testEqualsIncludingNaN…`) are REJECTED by `NoAssertionsFilter`
   ("No assertions found"). Modern commons-numbers refactored the inline asserts
   into a private static helper that takes the method-under-test as a functional
   interface:
   ```java
   @Test void testEqualsWithAllowedDelta() { assertEqualsWithAllowedDelta(Precision::equals, false); }
   private static void assertEqualsWithAllowedDelta(EqualsWithDelta fun, boolean nan) {
       Assertions.assertTrue(fun.equals(153.0000, 153.0000, .0625));  // assertion lives here; calls fun.equals, not Precision.equals
   }
   ```
   Two gaps: (a) assertions in a called helper, not the `@Test` body; (b) MUT
   invoked indirectly via a functional interface (`fun.equals`, not `Precision.equals`).
   DB evidence: `filter_result` for test_id 17140 — `AssertionInMethodFilter` DEFER
   ("assertion fixture"), `NoAssertionsFilter` REJECT.

5. **SPF supports far more types than the paper's "numeric or boolean" claim.**
   First-hand eval at `~/Projects/phd-thesis/projects/spf-eval/` (RESULTS.md,
   run 2026-02-19, jpf-symbc gradle-build rev fc70d27, Java 8, Z3). Highlights:
   - **Full** (input PC + output oracle): int, long, **short, byte, char**,
     boolean; String (equality/length/concat/substring/charAt/indexOf); arrays
     (int[], varargs); objects (lazy init, field read/write, **constructed
     return**, instance fields); boxed primitives + autoboxing; ArrayList;
     static fields; switch; bounded/symbolic-bound loops; recursion;
     interprocedural calls.
   - **Partial**: double/float (a **fixable bounds bug** — lower bound set to
     `Double.MIN_VALUE` instead of `-Double.MAX_VALUE` in the solver wrapper's
     `makeRealVar`); nonlinear real; D2I cast (z3bitvector only); 2D arrays;
     enum/optional/generic/interface/lambda params (often null-only lazy init).
   - **Hard crash**: transcendentals (`Math.sin/cos/sqrt`); bitwise/shift under
     plain z3 (use `z3bitvector`); `String.compareTo/isEmpty`; null string/array
     params; symbolic float compared to ±Infinity.

## Versions & provenance

**The Sourcegraph query (verbatim regex)** — recovered from
`~/Projects/test-generalization/commons-{math,lang}-candidates.txt`:
```
public static (?:byte|short|int|long|float|double|boolean)\s+\w+\s*\((?:final\s+)?(?:byte|short|int|long|float|double|boolean)\s+\w+\s*(?:,\s*(?:final\s+)?(?:byte|short|int|long|float|double|boolean)\s+\w+\s*)*\)
```
Matches: `public static` + return ∈ {byte,short,int,long,float,double,boolean} +
≥1 param all from that same set. Note what's excluded by construction: **char,
String, void, objects, arrays**, and any non-static / instance method. This is
the regex form of the paper's "numeric or boolean" criterion. `char` (CharUtils)
never had a chance; neither did Interval/PolynomialFunction/UnivariateFunction
(object construction + instance methods).

**Crawl mechanism.** `~/Projects/test-generalization/projects/apache-commons-utils/`
holds `crawler.py` + `export.csv` (`path;url` pairs). URLs are
`https://sourcegraph.com/github.com/apache/commons-*/-/raw/src/main/.../File.java`
— **unpinned (default branch / HEAD at crawl time, ~Feb 2025)**, downloading each
matched main file and its `*Test.java` sibling. So our dataset = apache/commons-*
**HEAD as of early 2025**, i.e. the modern modular line.

**Versions — what we actually used vs. JARVIS:**

| | Library line | Concrete version | Precision class |
|---|---|---|---|
| **Our final dataset** (`commons-utils`, dev DB project 22) | modular | commons-numbers ~1.1+ (has `DD`,`ExtendedPrecision`,`Norm`,`Sum`), commons-math4 ~4.0-beta+ (`core/jdkmath`+`legacy` split), geometry/rng/lang3/codec/collections4/jcs3/… from HEAD ~Feb 2025 | `org.apache.commons.numbers.core.Precision` |
| **Our early experiment** (still on disk, non-dev repo) | monolithic | `projects/commons-math-3.x` = **3.6.2-SNAPSHOT**; candidate search ran over `commons-math3` + `commons-lang3` | `org.apache.commons.math3.util.Precision` |
| **JARVIS** (VMCAI 2018) | monolithic | commons-math **3.5** (Interval "from release 3.5"; MATH-1256 fixed in 3.6); commons-lang3 | `org.apache.commons.math3.util.Precision` |

**Key artifact — the modern refactor is why Precision looks "unhandled".**
The **math3** `PrecisionTest.testEqualsWithAllowedDelta` (in our
`commons-math-3.x` checkout) has the JARVIS Fig.1 form, assertions **inline**:
```java
public void testEqualsWithAllowedDelta() {
    Assert.assertTrue(Precision.equals(153.0000, 153.0000, .0625));   // direct, in @Test body
    Assert.assertTrue(Precision.equals(153.0000, 153.0625, .0625));
}
```
The **modern commons-numbers** version refactored this into a helper taking the
MUT as a functional interface (`Precision::equals`) — which is exactly what trips
`NoAssertionsFilter`. So a chunk of the "Precision failure" is an **artifact of
using HEAD instead of the JARVIS-era version**, not a fundamental Teralizer gap.

**Implication for corpus choice:** the scorecard run must use a pinned JARVIS-era
fixture or checksummed source artifacts as execution inputs. The modern corpus remains
supporting evidence for why the existing evaluation database cannot prove the claim.

**SPF spike versions (pinned — no snapshots).**
- commons-math: tag `MATH_3_5` → `b3c5dae8f253fcb4484e5cd3cc5662587803efc2`.
- commons-lang: tag `LANG_3_5` → `36f98d87b24c2f542b02abbf6ec1ee742f1b158b` for CharUtils scorecard inputs.
- jpf-symbc / jpf-core: the spf-eval submodule commits listed below.

**Spike artifact provenance.**
- No SNAPSHOT / HEAD / unpinned sources anywhere. Pin exact **tag + resolved
  commit SHA**, never a branch.
- Every vendored source file gets a header: upstream repo URL, release tag,
  commit SHA, original path, license.
- `spf-eval/jarvis-spike/PROVENANCE.md` records, per library: tag + resolved SHA,
  the exact git/fetch command used, the JARVIS test row(s) it serves; plus
  jpf-symbc & jpf-core submodule SHAs, JDK version, and Z3 version.
- `jarvis-spf-RESULTS.md` cites the exact version/SHA each verdict was produced against.

**Captured pins for verified spf-eval build/run.**
- commons-math `MATH_3_5` → `b3c5dae8f253fcb4484e5cd3cc5662587803efc2`.
- jpf-symbc `7949438f88224ab073b01cc418555174b35dcd04` (gradle-build) — same SHA as Teralizer's submodule.
- jpf-core (nested) `201f658be0b8bf23bbb29b69081f5c5304dd34d0` (JPF-8.0-126).
- JDK openjdk 1.8.0_462. Z3 4.11.2 (z3-turnkey, bundled in jpf-symbc).
- commons-lang `LANG_3_5` → `36f98d87b24c2f542b02abbf6ec1ee742f1b158b`.
- Full spike report: `spf-eval/jarvis-spike/jarvis-spf-RESULTS.md`, with exact version/SHA cited for each verdict.

## Implications

- For most JARVIS cases the bottleneck is **Teralizer's own layers**, not SPF:
  - MUT-identification / dataset criteria (public static + numeric/boolean) are
    **narrower than SPF's real capability** (e.g. `char` is fully supported;
    object construction + instance methods are supported).
  - Test-analysis filters reject the in-corpus Precision tests (helper-delegated
    assertions, MUT-via-lambda).
- So "improving JARVIS-case handling" splits into two tracks:
  1. **Analysis robustness** (in-corpus, smaller): assertions in helper methods;
     MUT invoked via functional interface. Unblocks `Precision`.
  2. **Capability expansion** (lets the excluded 9 Table-2 rows enter the pipeline):
     support `char` params; include FastMath fixture rows; support instance methods /
     object construction (Interval, PolynomialFunction, UnivariateFunction). Plus the
     double/float bounds-bug fix in the SPF wrapper, which Precision-style double comparisons need anyway.

## Scoreboard run output

Current per-probe NAIVE/IMPROVED PVC, the zero-exclusion result on the typed `InputGenerationPlanner` generator, the two robustness fixes, and the IC delta live in `2026-06-28-residual-aware-generator-rerun`. Metric definitions (probe, PVC, IC) and the run contract live in `2026-06-27-jarvis-scoreboard-evaluation-lane`.

The JARVIS Table-2 reference targets (durable paper facts) are:

| Table row | Teralizer probes | JARVIS PBT PVC |
|---|---:|---:|
| `CharUtilsTest::isAscii` | 2 | 59 |
| `CharUtilsTest::isPrintable` | 2 | 45 |
| `FastMathTest::testMinMaxDouble` | 2 | 400 |
| `FastMathTest::toIntExact` | 1 | 65 |
| `IntervalTest` | 1 | 2 |
| `PolynomialFunctionTest::testConstants` | 1 | 105 |
| `PolynomialFunctionTest::testLinear` | 1 | 264 |
| `PolynomialFunctionTest::testfirstDerivativeComparison` | 1 | 160 |
| `PrecisionTest` (`eps`) | 2 | 102 |
| `UnivariateFunctionTest::testAbs` | 1 | 506 |

All 10 rows enter as 14 assertion-level probes (plus a separate non-Table-2 `Precision.equals(double,double,int maxUlps)` spike probe), and all generated tests pass. JARVIS Table 2 used ScalaCheck's 100-tests-per-PBT default with multiple generators per scenario, so some row PVCs (e.g. `UnivariateFunctionTest::testAbs` at 506) exceed a single one-parameter 100-check run — the honest comparison reports probe count and PVC together rather than collapsing to an unqualified win/loss. IC is a project-level sanity check, not the headline, until per-probe JaCoCo is added.

## Spike results

First-hand by an unbiased SPF investigator against the pinned stack; full report:
`spf-eval/jarvis-spike/jarvis-spf-RESULTS.md`. Verdicts spot-verified against the
result XMLs. **Tally: 8 FULL · 1 PARTIAL · 2 BLOCKED.**

| Case | Verdict | Spec (oracle) |
|---|---|---|
| CharUtils.isAscii / isAsciiPrintable | ✅ FULL | char→SYMINT; complete boolean partition |
| FastMath.min/max(double,double) | ✅ FULL | symbolic `a`/`b` (caveat: NaN / −0.0 arms not modeled over rational reals) |
| Interval.getSize() | ✅ FULL | `(upper − lower)` |
| PolynomialFunction.value() — const / linear / deriv | ✅ FULL | `c0` / `(x*c1+c0)` / `(2*c2*x+c1)` |
| FastMath.toIntExact(long) | ⚠️ PARTIAL | correct 5-region partition; overflow exception path missing, `(int)` cast unmodeled (symcrete LCMP) |
| Precision.equals(double,double,double) | ✅ FULL | Table-2 eps path is extractable through the `FastMath.abs(y - x) <= eps` branch |
| Precision.equals(double,double,int maxUlps) | ✅ SPIKE | Separate raw-bits probe passes with `symbolic.fp=true` + Z3 `fp.to_ieee_bv`; keep outside the Table-2 eps row |
| Abs.value() | ✅ FULL | `FastMath.abs` model is reached once the generated JPF config prepends `${jpf-symbc}/build/classes` |

**Headline:** SPF spec quality is gated by *which JVM primitives the implementation
reaches*, not by mathematical difficulty. Crucially, **object construction +
instance-method + symbolic-array propagation works robustly** — symbolic values
flow through constructor → fields → coefficient array → Horner → even a second
constructed object (`polynomialDerivative()`) into the return oracle. So the
barrier that kept 9/10 JARVIS Table-2 rows out of our dataset (static-method-only
selection plus type/corpus limits) is **a Teralizer criterion, not an SPF limit.**

Genuine SPF gaps are narrow and specific:
- `Double.doubleToRawLongBits` has symbolic support in the `symbolic.fp=true` + `z3bitvector` spike path, so pure ulps-style checks such as `Precision.equals(double,double,int maxUlps)` are feasible as a separate raw-bits probe. The default rational-real mode still has no raw-bits model.
- `FastMath.abs` no longer blocks the scorecard once the generated JPF classpath prepends `${jpf-symbc}/build/classes`; this reuses jpf-symbc's existing model-class mechanism rather than modeling raw bits.
- `toIntExact`'s missing overflow exception is a symcrete `LCMP` control-flow
  decoupling in jpf-symbc (solver-independent; deep fix). `z3bitvector` never
  helped; `z3` is best for the current scorecard rows.

## Implications for Teralizer extension

The spec exists for most JARVIS cases, so ingesting them is largely a
"wire-it-through" task, not research. Candidate scope by value/effort:
- **High value, in reach:** extend MUT identification to **instance methods /
  object construction** (unblocks Interval + 3× PolynomialFunction + Abs) and
  **`char` parameters** (CharUtils). SPF already delivers the specs.
- **Completed in this lane:** prepend the jpf-symbc model classes to generated JPF configs so `FastMath.abs(double)` is reachable.
- **Focused spike:** pure `Precision.equals(double,double,int maxUlps)` raw-bits support via `symbolic.fp=true` + Z3 `fp.to_ieee_bv`; see `2026-06-27-spf-ulps-raw-bits-spike`.
- **Deep fix:** `toIntExact` exception path (LCMP semantics).

## Pointers

- JARVIS paper: `~/Downloads/vmcai2018-jarvis-extended.pdf` (Tables 1–2; §9 Interval bug case study).
- Paper exclusion wording: `~/Projects/test-generalization-paper/sections/04-evaluation-02-framework.tex` L94–105.
- SPF type eval: `~/Projects/phd-thesis/projects/spf-eval/` (RESULTS.md summary table + Key Conclusions; STATUS.md for harness design ideas — it's a clean DB-free SPF harness we can learn from).
- Teralizer filters: `src/main/java/teralizer/processing/filter/` (esp. `NoAssertionsFilter`, `AssertionInMethodFilter`, `AssertionInLoopFilter`, `ParameterTypeFilter`, `ReturnTypeFilter`).
- Pipeline tasks: `src/main/java/teralizer/processing/task/` (`TestAnalysisTask`, `TestFilteringTask`, `TestGeneralizationTask`).
- Generalization: `src/main/java/teralizer/spoon/{analysis,generalization}/`, `transformer/`, `jqwik/`.
- In-corpus subject (modern): `projects/commons-utils/src/test/java/org/apache/commons/numbers/core/PrecisionTest.java` (helper-refactored).
- JARVIS-era subjects: **check out tag `MATH_3_5`** from `~/Projects/test-generalization/projects/commons-math-3.x` (released 3.5). NOTE: that working tree currently sits at `MATH_3_6_1-9-g…` (unreleased snapshot) — **do not use the snapshot**, check out the tag. commons-lang3 source: `~/Projects/test-generalization/projects/commons-lang3-3.17.0`.
- Sourcegraph query + candidates: `~/Projects/test-generalization/commons-{math,lang}-candidates.txt`; crawler+CSV: `~/Projects/test-generalization/projects/apache-commons-utils/{crawler.py,export.csv}`.
- DB (read-only): container `postgres-replication` :5432, user `teralizer`, db `postgres_dev`; project_id 22 = commons-utils. Do NOT mutate.
