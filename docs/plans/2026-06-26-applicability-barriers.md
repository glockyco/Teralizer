---
title: Applicability Barrier Evidence
type: audit
status: active
created: 2026-06-26
parent: 2026-06-26-teralizer-overview
---

# Real-world (RQ6) applicability-barrier evidence for Teralizer's test generalization — deferred reference for strategy step (2), not active work

## Barrier inventory

Companion telemetry spec: `2026-07-01-pipeline-observability-telemetry` defines the structured
reason codes and provenance needed to revisit these barriers without parsing free-text task logs.
Use it when implementing MUT-id, SPF/spec-extraction, build/report diagnostics, assertion support,
or generated-property yield tracking.

Class: **E** = engineering (self-inflicted, lift with effort) · **E/R** =
engineering now, research for completeness · **R** = genuine research · **X** =
external dependency. Impact figures are from RQ5 (controlled) / RQ6 (real-world).

| # | Barrier | Enforced at | Class | Evidence / impact | Effort |
|---|---|---|---|---|---|
| 1 | `boolean` + `char` excluded | `Configuration.SUPPORTED_TYPES` (numeric-only) | **E** | code omits boolean despite paper's "numeric *or boolean*"; gen layer + SPF already support both | LOW |
| 2 | String / array / object **inputs** unsupported | `SUPPORTED_TYPES` + `ParameterType`/`ReturnType` + spec-extraction scoping | **E/R** | spf-eval shows SPF handles all three; ParameterType rejects 49.4% real-world (objects/arrays/no-param) | MED–HIGH |
| 3 | Static-only / receiver inputs ignored | implicit — no receiver symbolization; `ParameterType` rejects no-param MUTs; `JpfInstrumentationTask` wraps receiver as concrete `_target_` | **E** | spike: construction→field→return propagation works; unblocks Interval, PolynomialFunction, UnivariateFunction | MED/HIGH |
| 4 | Assertions must be in the `@Test` body | `NoAssertionsFilter` | **E** | 41.3% real-world reject, **~86% false positives** (helpers/fluent APIs); also the Precision case | MED (interprocedural) |
| 5 | "Very basic" MUT identification — shares plain-LCBA's inspector flaw (picks `getState()` not `setState()`) | `TestAnalysis.findTestedMethodCall` | **E** | `MissingValue` 57.9% real-world; 24.7% controlled; misses field side-effects, non-call actuals, reversed args, library/inheritance resolution | MED (adopt Ghafari mutator/inspector classification, static/Spoon — not from-scratch) |
| 6 | Few assertion types | `AssertionType`/`UnsupportedAssertionFilter` (only assertEquals/True/False/Throws) | **E/R** | assertThat 8.3%, assertNotNull 6.1%, assertNull, fail, assertArrayEquals (arrays *do* work in SPF), assertInstanceOf | MED |
| 7 | void-return MUTs rejected | `ReturnTypeFilter` | **E/R** | no output spec; but field-effect / state oracles modelable; SPF captures void_return | MED |
| 8 | Pure-function assumption (no side effects) | spec-extraction design | **E/R** | `03-approach-03` L124-125; field writes (PUTFIELD) already tracked by SPF; unmodeled calls → uninterpreted functions | HIGH |
| 9 | `@ParameterizedTest` / `@RepeatedTest` rejected | `TestType` filter | **E** (param) | 180 controlled rejections; param tests are *already* parametric — ideal raw material | MED |
| 10 | double/float negative paths UNSAT | jpf-symbc real lower-bound defaulted to `Double.MIN_VALUE` | **E** (bug) — ✅ fixed (B-2) | `MinMax.minDouble` → `-Double.MAX_VALUE`; symbolic reals now cover negatives/zero. spf-eval double_linear surfaced it | LOW |
| 11 | Native math / model classpath gaps | jpf-symbc model classes + selected native peers | **E** | `FastMath.abs` is fixed in the scorecard by prepending `${jpf-symbc}/build/classes`; pattern remains reusable for unsupported native math and model classes | LOW–MED |
| 12 | Reduction replaces only 1 original test | reduction stage | **E** | `05-discussion` L37; multiple same-partition tests could collapse to one PBT | MED |
| 13 | PIT class-level exclusion amplification | PIT integration (green-suite, class-level only) | **X/E** | one failing test excludes its whole class — 10× amplification | MED |
| 14 | Maven + standard structure only | project setup/detection | **E** | ~15% real-world project failures; no Gradle / non-standard layouts | MED |
| 15 | Method-call sequences unsupported | generalization model | **E/R** | `05-discussion` L56; stateful APIs (build-then-use) | HIGH |
| 16 | toIntExact: exception path + `(int)` cast oracle | jpf-symbc `LCMP` long-width + `L2I` (no truncation node) | **E/R** — ◐ partly addressed (B-5) | LCMP no longer truncates `long` operands to `int` in its GT branch; the `L2I` cast oracle and the overflow exception path remain open | MED |
| 17 | NaN / signed-zero unmodeled | jpf-symbc rational `SymbolicReal` / `ProblemZ3` | **R** | needs QF_FP threaded through ~40 solver methods | HIGH |
| 18 | SPF exploration **exception paths abort the whole analysis** (no capture) | `TestGeneralizationListener` / `JpfExecutionTask` aborts on uncaught exception | **E** | **827 dev `ArithmeticException`** = largest resolvable SPF-stage bucket; spf-eval captures `thrownException` cleanly; completes toIntExact's exception path + a *legitimate* bug-finding axis (throwing implicit preconditions). **NB: does NOT address Interval** (silent wrong-value, not a throw) | MED |
| 19 | Listener NPE in `writeSpecificationFiles` | `TestGeneralizationListener:147` | **E** (bug) | 89 dev + 7 real-world EXECUTE_JPF failures — free recoveries | LOW |
| 20 | Symbolic array length unsupported | jpf-symbc `NEWARRAY`/`MULTIANEWARRAY` | **E/R** | 31 dev + 12 real-world `NEWARRAY: symbolic array length` | MED |
| 21 | Missing JPF/JDK model classes + native peers | jpf-symbc model classpath / native peers | **E/X** | `class not found: NoSuchMethodException` ×592; real-world `UnsatisfiedLinkError`/peers ×~109 (URLDecoder, etc.) — partly modelable, partly genuinely native | MED (partial) |

## Collected-data SPF-stage failure breakdown

From `task` (stage `EXECUTE_JPF`, status `FAILED`): **2,995 dev** (commons-utils +
EqBench-ES) / **368 real-world**. Root causes decoded from nested stack traces
(`Caused by:` / `NoUncaughtExceptionsProperty <Exception>`); `assertion.exclusion_info`
mirrors these per-assertion.

| Cause | dev | real-world | Kind |
|---|---|---|---|
| Uncaught `ArithmeticException` on explored path (#18) | 827 | 18 | **resolvable** — capture exception path |
| PC size limit exceeded | 790 | — | resource (tunable) |
| `class not found` (`NoSuchMethodException`) (#21) | 534 | 50 | partly resolvable |
| depth limit exceeded | 524 | 24 | resource (tunable) |
| listener NPE `writeSpecificationFiles` (#19) | 89 | 7 | **resolvable** — our bug |
| AIOOBE / `NoSuchMethodError` / OOM / timeout | ~99 | ~85 | env / resource |
| symbolic array length (#20) | 31 | 12 | **resolvable** (capability) |
| native peer / `UnsatisfiedLinkError` (#21) | — | ~109 | partly resolvable |
| transcendental/sqrt not supported (#11) | 1 | — | resolvable (model class) |

**Key:** the biggest *resolvable* SPF-stage bucket is exception-path capture
(#18), not a solver-model gap. Resource limits (PC/depth/OOM/timeout) are tunable,
not missing models. Raw-bits concretization remains relevant for pure ulps-style checks such as `Precision.equals(double,double,int maxUlps)`, but the current Table-2 eps scorecard fixture reaches the arithmetic `FastMath.abs(y - x) <= eps` branch and passes after the model-classpath fix.

## RepoReapers applicability funnel

Projects 1161 → 507 analyzed → 44 w/ ≥1 included assertion → **11 complete**
(~1.7% of eligible). Tests 81,834 → 33,390 included. **Assertions 122,166 → 711
included (0.58%)** — the collapse is at the *assertion* level. Of ~121k excluded
assertions, **27% have 1 blocker, 62% have 2, 10% have 3** → ~73% need
*multiple* coordinated fixes; any single fix is marginal. Dominant blocker combos:
`ParameterType+ReturnType` 31,679 · `MissingValue+UnsupportedAssertion` 25,113 ·
`MissingValue` 21,616 · `MissingValue+ParameterType` 11,393 · `ParameterType`
8,326 · `ReturnType` 2,299 (+ ~20k `ExcludedTest` cascades from test-level cuts).
Type filters **DEFER** when the MUT is unresolved, so the MissingValue masses are
partly type-shadowed too. **Reading:** >½ of excluded assertions hit the *type
ceiling* (object/string params and/or returns); the rest are MUT-unresolved/
unsupported-assertion (which, once fixed, mostly convert to type rejects). **No
cheap fix moves this > marginally** — substantial RepoReapers progress requires
string + object types (inputs, outputs, oracles) + stateful setup, all of which hit
the loop/summarization research wall.

## Evidence ledger (DB-grounded)

First-hand read-only queries on `postgres_dev`/`postgres_test`. Decisions cite
these, not headlines. **Method rule:** `filter_result` short-circuits at the first
REJECT, so a fix's *true* reach = cases where it is the first reject AND they pass
the *later* filters (esp. ParameterType, AssertionInLoop). Always net out shadowing.

- **char/boolean reach is mostly shadowed.** Real-world boolean-return rejects =
  12,332, but only ~1,500 have a generalizable param (1,279 numeric / 83 char / 130
  boolean); **3,789 have NO params** and **7,051 only object/String params** →
  shadowed by ParameterType. char-return rejects = 201. In curated `commons-utils`,
  char/boolean rejects ≈ 0 (corpus pre-filtered to numeric). **Verdict:** add
  char+boolean to remove an embarrassing *stated limitation*, but it is NOT an
  applicability mover (~12% of the boolean headline survives shadowing).
- **The real-world applicability ceiling is object/string/state predicates**, not
  the scalar type set. boolean methods are mostly predicates on objects/strings
  (`hasNext()`, `supports(Class)`, `matches(String,…)`) gated by object/string
  inputs or object state (the hard, loop-shadowed territory). ParameterType rejects
  60,289 real-world. Honest RQ6 ceiling — cheap fixes won't move it much.
- **Constructor-param generalization (the #3 mechanism) unshadows only ~2.7% of the
  shadowed boolean predicates.** Of the 12,332 boolean-return rejects, inline
  construction with a scalar literal arg = ~337 (90 receiver `new T(…n…)` + 247 arg
  `new T(…n…)`); 172 are no-arg ctors, 281 non-scalar ctor args, and **11,542 (94%)
  have a field/variable receiver** (object set up via `@Before`/factory/builder/an
  earlier statement). So inline ctor-param gen (#3) is justified by the **JARVIS
  object cases** + a *small* real-world bonus; the real mass (field/var receivers)
  needs **stateful-setup / interprocedural-state** handling (overlaps #15/#8), and
  even the 337 often have the scalar incidental (`new TestMessage(MessageType.X, 0)`
  → behavior driven by the enum). Evidence via `tested_method_call_source_code`.
- **RQ6 measured-failure landscape** (`v_project_failures`): dependency-resolution
  (329) + compile (171) are **PRE-EXCLUDED** (not measured). Measured drivers: "all
  tests excluded" (129 → test-level filters; NoAssertions dominant per RQ6) and "all
  generalized excluded" (269, downstream), plus timeouts (~88), Spoon (6), structure.
- **Project-structure failures are concrete + fixable.** "Main/Test compiled path
  `target/classes|test-classes` does not exist" (vote-no-filme, time2lib, UFMGame,
  lovelmslms2_cartier, …) → Teralizer hardcodes Maven default output paths.
  "sources/tests not found" (ShackServer, zoeey, mapzen-android-demo, lajolla,
  lbm-webapp) → non-standard/android/web layouts. **General fix:** detect compiled +
  source/test paths from the POM (`outputDirectory`/`testOutputDirectory`) or by
  search, with an optional per-project path config (ship configs for affected
  dataset projects) — flexible, not per-project hand-tuning. Modest count; removes a
  hardcoded-assumption limitation.
