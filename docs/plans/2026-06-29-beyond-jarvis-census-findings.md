---
title: Beyond-JARVIS Census — Findings & Issue Backlog
type: note
status: active
created: 2026-06-29
parent: 2026-06-29-beyond-jarvis-generalization-census
---

# Beyond-JARVIS Census — Findings & Issue Backlog

First execution of the census (`2026-06-29-beyond-jarvis-census-implementation`) ran but
did **not** complete cleanly. It surfaced a strong partial result plus several concrete
issues to tackle one by one. This note records both. Run artifacts live in
`postgres_jarvis_census` / `data/jarvis-census` (gitignored); the prep script, configs,
run script, and `--census` report are committed.

## Run status: partial / blocked

- **commons-lang**: the `IMPROVED_100_TRIES` generalized build **failed to compile** (a
  malformed generated test — see I1), so lang produced **no** generalized data.
- **commons-math**: generalized *execution* succeeded for `IMPROVED_100_TRIES` (109 FULL
  generalizations), but `COLLECT_PIT_DATA_GENERALIZED` **timed out** (I3), after which the
  `NAIVE_100_TRIES` math tasks were dropped. `CENSUS_EXIT=0` was misleading — the pipeline
  swallows per-task failures.
- Net: no mutation-gain data, no NAIVE baseline, no lang generalized data. The
  sound-generalization **count** for commons-math `IMPROVED_100` is valid.

## Valid partial result (the breadth signal holds)

`uv run --directory analysis python -m teralizer.jarvis_scoreboard --census`, sound (FULL)
generalizations, commons-math `IMPROVED_100_TRIES`:

| upstream test class | FULL | executions |
|---|--:|--:|
| `ArithmeticUtilsTest` | 44 | 83 |
| `MathArraysTest` | 36 | 42 |
| `PolynomialFunctionTest` | 29 | 29 |
| `PrecisionTest` | 0 | 82 |
| **total** | **109** | 236 |

- **109 sound generalizations from one variant of one project**, vs JARVIS's ~7-8
  commons-math Table-2 successes — even this partial run is ~13x JARVIS's math reach. The
  broader-applicability thesis (`2026-06-29-beyond-jarvis-generalization-census`) holds.
- `PrecisionTest` 0 FULL / 82 executions is the raw-bits exclusion working as designed
  (the eps probe is unsound; the renderer/peer fail-loud excludes it).
- `FastMathTest` and `IntervalTest` produced **no** FULL rows — worth investigating
  (FastMath is dominated by transcendentals → `NonGeneralizableExpressionException`, and
  loop-style assertions → `DEFER` + downstream drop; Interval's upstream test structure
  needs a look). Tracked as I5.

## Funnel observations (filter tally, both projects)

- **Type ceiling is the dominant *reject*** (`ParameterTypeFilter` REJECT: 42 math /
  317 lang) — lang has many `String`/object-arg methods (`NumberUtils`), correctly rejected.
- **`MissingValueFilter` REJECT is large (314 math / 393 lang)** — strongly correlated with
  the "Unable to transform boolean/char/Integer value to Java" log errors. The value-handling
  gap (I1) is **suppressing generalizations broadly**, not only crashing the one lang build.
- `NonPassingTestFilter` REJECT (188 math) + `ExcludedTestFilter` (280 math) — many upstream
  seed tests don't pass in the instrumented/initial build, or cascade from an excluded test.
- `DEFER` (informational, never excludes): `AssertionInLoopFilter` (85 math / 33 lang),
  `TestedMethodInLoopFilter`, `AssertionInMethodFilter`, the null-column `ParameterType`/
  `ReturnType` DEFERs — the loop/structure cases the funnel records as downstream exits.

## Issue backlog (tackle one by one)

### I1 — Boxed-wrapper values render as object identity (build-breaker + value loss) — HIGH

Generated line (commons-lang `_BooleanUtilsTest_Generalized_test_toBooleanObject_Integer_*`):

```java
return new FirstValueArbitrary<TestParameters>(
    new TestParameters((java.lang.Integer) (java.lang.Integer@24c)), ...)
```

The seed/first value for a **boxed `java.lang.Integer`** parameter is rendered via object
identity (`java.lang.Integer@24c`) instead of its value → invalid Java. Teralizer's
value→Java-literal path handles primitives (`int`/`double`/`char`/`boolean`) but not boxed
wrappers (`Integer`/`Boolean`/`Character`/`Long`/`Double`/…).

- **Impact:** (a) one malformed generated file fails the *entire* variant build (see I2);
  (b) the same gap likely drives many `MissingValueFilter` REJECTs (boolean/char/Integer
  values that can't be transformed) — so fixing it may *unlock* generalizations, not just
  unblock the build.
- **Root cause (verified):** `TestGeneralizationListener.captureConcreteArguments()`
  (L235-243) builds each argument as `new MethodArgument(type, String.valueOf(concreteValues[i]))`
  (L240), but for a boxed wrapper `concreteValues[i]` is a JPF `ElementInfo`/`DynamicElementInfo`
  whose `toString()` is the identity (`...@24c`). The boxed *return* value has the same flaw in
  the output-capture branches (~L174-190, `outputValueForArgument.toString()`). The bad string
  then flows through `NumericDomainPlanner` (`(type)(value)`) and
  `ImprovedTestParametersSupplierFactory.buildOriginalTuple()` into the seed literal.
- **Fix:** add a typed `extractConcreteValue(javaType, raw)` helper that reads the wrapper's
  primitive field via JPF `ElementInfo` getters — `ei.getIntField("value")`, `getLongField`,
  `getDoubleField`, `getFloatField`, `getBooleanField`, `getCharField` (as int), `getByteField`,
  `getShortField` — modeled on spf-eval's `HeapCapture.readFieldConcreteValue()`. Use it in
  both `captureConcreteArguments()` and the boxed-return branch of `writeSpecificationFiles()`.
  `ModelToJavaTransformer.transform(MethodArgument)` already renders the resulting numeric
  strings for all boxed types (incl. the `(char)` int case), so no downstream change is needed.
  Optionally reject pre-generation if a value still can't render (defense-in-depth, see I2).

### I2 — One malformed generated test fails the whole variant build — MEDIUM

`BUILD_PROJECT_GENERALIZED` compiles the entire generalized suite at once, so a single
bad generated file (I1) sinks *all* generalizations for that variant (and the downstream
execution/JaCoCo/PIT). Fragile.

- **Fix direction:** isolate generated tests so one bad file can't drop the batch — e.g.
  pre-generation validation (parse/compile-check each generated test, exclude failures), or
  per-test/per-batch compilation with failure quarantine. Pre-gen validation is cheaper and
  also yields a clean per-test exclusion reason.

### I3 — PIT mutation testing times out at census scale — MEDIUM

`COLLECT_PIT_DATA_GENERALIZED` for commons-math exceeded the 300s `pitest.max-execution-time`
(`Command execution timeout exceeded`, `PitDataCollectionTask:185`). The census suite covers
far more commons-math classes than the JARVIS-10 scorecard, so PIT mutates many more classes.
This blocks the mutation-gain metric at census scale.

- **Fix directions (pick/stack):** (a) raise `pitest.max-execution-time` substantially for
  census configs; (b) scope PIT's mutated classes to the MUTs actually under test rather than
  all JaCoCo-covered classes; (c) trim the allowlist's biggest coverage drivers
  (`FastMathTest`, `MathArraysTest`) for a tractable first census. Note the cost tradeoff:
  full-coverage PIT over commons-math may be inherently long.

### I4 — `reference.conf` generalization variants leak into census runs — LOW

`getGeneralizationVariants()` reads the **merged** `teralizer.generalizations` keyset, so the
user's local `reference.conf` (which defines `IMPROVED_200_TRIES`) merges into the census's
2-variant config (HOCON deep-merge; a `null` override would crash `getGeneralizationAlgorithm`).
The census ran 3 variants instead of 2.

- **Mitigation in place:** the `--census` report scopes to `CENSUS_VARIANTS`
  (`NAIVE_100`/`IMPROVED_100`), so output is correct regardless of leakage.
- **Fix direction (optional):** a config mechanism to *replace* (not merge) the generalization
  set per project, or move the default variant set out of `reference.conf`.

### I5 — Port spf-eval's refined specification extraction into Teralizer's listener — HIGH (investigation)

spf-eval's listener is more refined than Teralizer's. Scout findings
(`agent://SpfEvalPortScout`), prioritized:

- **P1 — ObjectList unwrap on the symbolic return attr (HIGH, XS).** SPF may wrap a slot's
  attributes in an `ObjectList`; Teralizer's direct `(Expression) returnInstruction.getReturnAttr(...)`
  in `writeSpecificationFiles()` (~L160) fails/nulls when wrapped. Mirror spf-eval
  `ExtractionListener.extractSymbolicAttr`: `ObjectList.isList(raw) ? ObjectList.getFirst(raw) : raw`
  before the cast (`import gov.nasa.jpf.util.ObjectList`).
- **P2 — `exceptionHandled` hook (MEDIUM, XS).** Teralizer clears `pendingThrownException` only on
  `methodExited`; if the method catches internally and returns, the output spec is mis-classified as
  an exception path. Add `@Override exceptionHandled(...) { pendingThrownException = null; }`
  (spf-eval `ExtractionListener.exceptionHandled`).
- **P3 — bit-exact float/double capture (LOW-MED, S; defer).** `String.valueOf(d)` loses NaN
  payloads/subnormals; spf-eval `ValueHelpers` stores decimal + `0x` raw bits and emits
  `Double.longBitsToDouble(0x…)`. Only needed if NaN-payload tests fail.
- **P4 — capture the heap PC (MEDIUM, M).** Teralizer reads only the numeric `PathCondition.getPC`;
  spf-eval also captures `HeapChoiceGenerator.getCurrentPCheap()` (null/non-null lazy-init forks for
  object/boxed params). Capturing is easy; transforming heap terms into the input predicate (extending
  `SpfToModelTransformer` for symbolic reference tokens) is the hard part — capture+log first, integrate later.
- **P5 — `header.toString()` vs `stringPC()` (LOW, awareness).** `NonLinearIntegerConstraint` drops its
  `%NonLinInteger%` marker under `stringPC()`. Teralizer transforms the PC via the visitor (node, not
  string), so this only matters if a raw PC string is ever logged — use `header.toString()` then.

**Already equivalent (do not double-port):** numeric PC→Model (`SpfToModelTransformer` — richer than
spf-eval's raw string), string-PC transform, NaN/Infinity rendering, the `ExceptionModel` output spec,
array expressions, `long` suffixing, method-boundary detection, and PC-size/timeout guards.

## How to proceed

Suggested order: **I1** (the boxed-value capture fix — unblocks lang and likely unlocks more
generalizations via fewer `MissingValueFilter` rejects) plus the **quick listener-correctness
fixes P1 (ObjectList unwrap) + P2 (`exceptionHandled`)** first — all small, and shipping them
before the next run avoids knowingly re-running with silent attr-loss / stale-exception risk.
Then **I2** (build robustness so one bad file can't sink a variant) → **I3** (so the
mutation-gain metric collects at census scale) → re-run the census for a complete result. The
larger spec-extraction ports **P3/P4/P5** and the optional **I4** (variant hygiene) follow. The
census spec and implementation plan remain active until a clean complete run lands.
