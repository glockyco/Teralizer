---
title: Residual-Aware Generator Scorecard Rerun
type: audit
status: active
created: 2026-06-28
parent: 2026-06-27-residual-aware-input-generation
---

Point-in-time evidence from the first JARVIS scorecard rerun on the typed
`InputGenerationPlanner` generator. Supersedes the generator-probe PVC numbers in
`2026-06-26-jarvis-case-coverage` (§ "Scoreboard run output (2026-06-27)"), which
predate the planner redesign and the two robustness fixes recorded here.

## Generator under test

`IMPROVED` now compiles its input model through `src/main/java/teralizer/jqwik/planning/`:
clauses are flattened (`ConstraintClauses`), each parameter gets a typed recipe
(`NumericDomainPlanner`), and atomic plus simple affine two-variable bounds
(`a + b < n`, `b == a + 1`, `eps >= y - x`) become jqwik `between(...)` / `just(...)`.
The full `filter(inputJava)` guard is always retained, so generation stays
path-exact; the planner narrows ranges by construction but never drops the predicate.

## Two robustness bugs found and fixed

The first rerun on this generator excluded three generated tests. Two were generator
bugs (both fixed; see `2026-06-27-improved-generator-redesign` Task 8):

- **Non-finite real scale.** The affine bound `eps >= y - x` overflows to Infinity
  for extreme operands, and `BigDecimal.valueOf(Infinity).scale()` threw
  `NumberFormatException` before the empty-range guard ran, aborting the whole
  property. Fixed by computing the decimal scale only for finite bounds. Surfaced as
  an excluded `precisionEquals` assertTrue generalization.
- **Surrogate value-log write.** A `ch >= 128` model now generates
  `chars().range(128, 65535)`, which spans lone surrogates (`0xD800`–`0xDFFF`).
  Writing one to the UTF-8 value log threw `MalformedInputException`. Fixed by
  escaping surrogates like control characters. Surfaced as an excluded `isAscii`
  assertFalse generalization; the fix also hardens NAIVE, which can hit surrogates too.

## Clean rerun result

Scratch DB `postgres_jarvis_scoreboard`, fixtures `MATH_3_5` and `LANG_3_5`.
**All 30 generated NAIVE + IMPROVED tests pass; zero Table-2 exclusions.**

Per-property PVC (distinct generated values per parameter; 100 checks unless an
edge-case set bounds the run lower):

| Project | Fixture | Assertion | NAIVE PVC | IMPROVED PVC |
|---|---|---|---:|---:|
| lang | isAscii | assertFalse | 92 | 93 |
| lang | isAscii | assertTrue | 68 | 68 |
| lang | isAsciiPrintable | assertFalse | 32 | 32 |
| lang | isAsciiPrintable | assertTrue | 60 | 95 |
| math | toIntExact | assertEquals | 91 | 97 |
| math | intervalGetSize | assertEquals | 72 | 87 |
| math | maxDouble | assertEquals | 131 | 138 |
| math | minDouble | assertEquals | 131 | 138 |
| math | absValue | assertEquals | 91 | 95 |
| math | polynomialConstant | assertEquals | 91 | 93 |
| math | polynomialLinear | assertEquals | 91 | 93 |
| math | polynomialDerivative | assertEquals | 90 | 93 |
| math | precisionEquals (eps) | assertTrue | 246 | 169 |
| math | precisionEquals (eps) | assertFalse | 176 | 39 |
| math | precisionEqualsMaxUlps | assertFalse | 111 | 15 |

## Findings

1. **IMPROVED ≥ NAIVE on 11 of 14 passing probes.** The planner's by-construction
   bounds raise value diversity for char-range, min/max, interval, polynomial, abs,
   and toIntExact probes (e.g. `isAsciiPrintable` assertTrue 95 vs 60).

2. **The affine `eps >= y - x` encoding lowers PVC on the three `Precision.equals`
   probes** (IMPROVED 169/39/15 vs NAIVE 246/176/111). Constraining `eps` into
   `between(y - x, MAX)` by construction trades value diversity for guaranteed
   precondition satisfaction. This is a sound generator/metric-shape tradeoff, not a
   failure: every value still satisfies the path predicate, and IMPROVED still
   exceeds the JARVIS Table-2 `PrecisionTest` PVC of 102 on the eps aggregate
   (169 + 39 = 208). Residual-only filtering would not change this; the narrowing
   comes from the `between` range, not the filter.

3. **maxUlps assertTrue is unsound for both variants (excluded).** The
   `precisionEqualsMaxUlps` input model is only `0 < maxUlps`: SPF's raw-bits spike
   never captured the x/y ulp relation, so no filter can reject `x=0, y=1,
   maxUlps=2`, and the assertTrue generalization fails its assertion and is excluded
   for NAIVE and IMPROVED alike. The `assertFalse` direction still passes because
   most random pairs are genuinely not within `maxUlps`. This is a raw-bits spike
   gap (`2026-06-27-spf-ulps-raw-bits-spike`, archived), not a generator or Table-2
   result; the maxUlps probe stays outside Table-2 claims.

## Reproduction

- Reset scratch DB, then `scripts/run-jarvis-scoreboard.sh` with
  `DB_NAME=postgres_jarvis_scoreboard`, `DATA_DIR=data/jarvis-scoreboard`,
  `DATASET_VARIANT=jarvis`.
- Score query: `analysis/src/teralizer/jarvis_scoreboard.py::get_pvc_scores`
  / `get_scoreboard(conn, variants=["NAIVE", "IMPROVED"])`, run with CWD at the
  worktree root so relative `data/jarvis-scoreboard` value-log paths resolve.
- No `postgres_dev`, `postgres_test`, or `_replication` database was read or mutated.
