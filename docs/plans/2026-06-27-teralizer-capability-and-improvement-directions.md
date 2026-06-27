---
title: Teralizer Capability & Improvement Directions
type: audit
status: active
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
---

Point-in-time synthesis of Teralizer's current capabilities, remaining
limitations, and the most promising improvement directions — after the
Phase 1 JARVIS-capability work merged (`2026-06-26-beat-jarvis-phase1`,
implemented). Supersedes the evidence ledger in the archived
`2026-06-26-teralizer-improvement-roadmap`; the RQ6-specific real-world
funnel + barrier inventory lives separately in
`2026-06-26-applicability-barriers`.

## What works now

The pipeline extracts path-exact specifications (input partition + symbolic
output oracle) from JUnit tests via SPF in `symbolic.collect_constraints`
mode — the solver is never invoked; jqwik generates inputs from the collected
path condition, not SPF. Phase 1 lifted the capability surface:

| Capability | Status | Evidence |
|---|---|---|
| Scalar inputs: `int`, `long`, `short`, `byte` | ✅ full | long-standing |
| `char`, `boolean` inputs | ✅ full (Phase 1) | `SUPPORTED_TYPES` + generator + boolean-return assertion rewriting |
| `double`/`float` outputs (linear paths) | ✅ full | jqwik-side sampling; solver bounds irrelevant in collect-constraints mode (#10 retracted as moot) |
| `FastMath.abs/min/max/toIntExact` | ✅ modeled (Phase 1) | jpf-symbc `FastMath` JARVIS model — Abs BLOCKED→FULL |
| Object construction → field → return (inline, fixed-arity) | ✅ full (Phase 1) | `JpfInstrumentationTask` constructing-input generation; unblocks Interval, PolynomialFunction, UnivariateFunction |
| Exception-path capture | ✅ full (Phase 1) | `TestGeneralizationListener` records thrown-exception specs instead of aborting (#18) |
| `writeSpecificationFiles` NPE | ✅ fixed (Phase 1) | 89 dev + 7 real-world free recoveries (#19) |
| Regression oracle (expected ← SPF-derived `outputJava`) | ✅ working | correct for mutation detection; not independent of the implementation |

SPF's real type coverage is broader than Teralizer historically exposed:
String (equals/length/concat/substring/charAt/indexOf), arrays (+varargs),
boxed primitives, ArrayList, static fields, switch, bounded loops, recursion,
interprocedural calls — all supported by SPF per the first-hand type-support
study (`~/Projects/phd-thesis/projects/spf-eval/RESULTS.md`).

## What doesn't work (and why)

### Capability gaps (SPF or Teralizer front-end)

| # | Gap | Root cause | Class | Impact |
|---|---|---|---|---|
| 2 | String / array / object **inputs** unsupported | `SUPPORTED_TYPES` + `ParameterType`/`ReturnType` reject; spec-extraction scoping | E/R | ParameterType rejects 49.4% real-world; surfaces shadowed-loop issues |
| 4 | Assertions must be in the `@Test` body | `NoAssertionsFilter` (top-level only) | E | 41.3% RQ6 reject, **~86% false positives** (helpers/fluent APIs) |
| 5 | "Very basic" MUT identification | `TestAnalysis.findTestedMethodCall` shares plain-LCBA inspector flaw (picks `getState()` not `setState()`) | E | MissingValue 57.9% real-world — but 41% is really #6, 33% is classpath; ~15% true MUT-id share |
| 6 | Few assertion types | `AssertionType`/`UnsupportedAssertionFilter` (assertEquals/True/False/Throws only) | E/R | assertThat 8.3%, assertNotNull 6.1%, assertArrayEquals (arrays *do* work in SPF) |
| 7 | void-return MUTs rejected | `ReturnTypeFilter` | E/R | no output spec; field-effect/state oracles are modelable; SPF captures `void_return` |
| 8 | Pure-function assumption (no side effects) | spec-extraction design | E/R | PUTFIELD already tracked by SPF; unmodeled calls → uninterpreted functions |
| 9 | `@ParameterizedTest` / `@RepeatedTest` rejected | `TestType` filter | E | 180 controlled rejections; param tests are already parametric — ideal raw material |
| 12 | Reduction replaces only 1 original test | reduction stage | E | multiple same-partition tests could collapse to one PBT |
| 13 | PIT class-level exclusion amplification | PIT integration (green-suite, class-level only) | X/E | one failing test excludes its whole class — 10× amplification |
| 14 | Maven + standard structure only | project setup/detection | E | ~15% real-world project failures; no Gradle / non-standard layouts |
| 15 | Method-call sequences unsupported | generalization model | E/R | stateful APIs (build-then-use) |
| 16 | `toIntExact`: exception path + `(int)` cast oracle | jpf-symbc symcrete `LCMP` push-order + `L2I` (no truncation node) | E/R | spike PARTIAL; LCMP one-liner only bites in full-symbolic mode; L2I oracle harder |
| 17 | NaN / signed-zero unmodeled | jpf-symbc rational `SymbolicReal` / `ProblemZ3` | R | needs QF_FP threaded through ~40 solver methods |
| 20 | Symbolic array length | jpf-symbc `NEWARRAY`/`MULTIANEWARRAY` | E/R | 31 dev + 12 real-world `NEWARRAY: symbolic array length` |
| 21 | Missing JPF/JDK model classes + native peers | jpf-symbc model classpath / native peers | E/X | `class not found` ×592; real-world `UnsatisfiedLinkError` ×~109 — partly modelable, partly genuinely native |

### Concessions (deliberate, recorded in the win condition)

- **`Precision.equals` ulps fast-path** — `Double.doubleToRawLongBits` has no
  symbolic model over SPF's rational reals → concretizes, collapses to 1 path.
  Core `|x−y|≤eps` IS extractable; the ulps fast-path needs real IEEE-754 FP
  theory (research-grade). Conceded in the JARVIS win condition.
- **`Interval` paradigm limit** — MATH-1256 is a silent wrong-value bug
  (`new Interval(0.0,-1.0).getSize()` → −1.0), not an exception. JARVIS finds
  it by learning the oracle from example pairs *independent* of the
  implementation; Teralizer derives the oracle *from* the implementation
  (`getSize()==upper−lower`), so a negative-size input matches our oracle →
  the test passes → bug not surfaced. PVC win, bug-finding loss.
- **`doubleToRawLongBits` concretization** silently degrades specs (Abs/Precision-ulps)
  rather than crashing, so it never reaches `EXECUTE_JPF FAILED` — visible only as
  degenerate specs.

## Pipeline failure landscape (pre-analysis funnel)

Re-run with `get_pipeline_failure_funnel` + `compute_stage_failure_summary` from
`applicability_priorities.py`. The funnel (1161 projects, first-failure
stage):

| Stage | Step | Projects killed | % | Root cause |
|---|---|---|---|---|
| SETUP_PROJECT | 2 | 355 | 30.9% | Maven setup (dependency resolution, non-standard layouts) |
| BUILD_PROJECT_ORIGINAL | 4 | 189 | 16.5% | Compilation errors (`mvn compile test-compile` exit 1) |
| EXECUTE_TESTS_ORIGINAL | 8 | 61 | 5.3% | Test execution timeout (48 of 61) |
| COLLECT_JUNIT_REPORTS_ORIGINAL | 9 | 88 | 7.7% | Cannot identify test report path (non-standard report locations) |
| EXECUTE_TESTS_INITIAL | 22 | 116 | 10.1% | "All tests excluded" — test-level filters reject everything |
| COLLECT_PIT_DATA_GENERALIZED | 33 | 219 | 19.1% | Mutation testing on generalized tests fails |
| COLLECT_PIT_DATA_INITIAL | 25 | 40 | 3.5% | PIT timeout |
| EXECUTE_JPF | 18 | 29 | 2.5% | JPF execution failures |
| COLLECT_JACOCO_DATA_INITIAL | 24 | 34 | 3.0% | JaCoCo collection failures |

**47.4% of projects fail before analysis** (SETUP + BUILD + EXECUTE_TESTS +
COLLECT_JUNIT). The biggest killer is SETUP_PROJECT (30.9%) — Maven dependency
resolution and non-standard project layouts. Only 13 projects (1.1%) complete
the pipeline with no failures.

**10.1% reach EXECUTE_TESTS_INITIAL but fail with "all tests excluded"** —
test-level filters (NoAssertions, NonPassingTest, TestType) reject every test.
Fixing #4 (interprocedural assertions) and #9 (@ParameterizedTest) would
unblock projects here.

**These pre-analysis failures are infrastructure problems** that need separate
fixes (barrier #14: Maven/structure-only, project-setup detection). They are
orthogonal to the MUT-id spec but limit its reach: the mutation-based oracle
only helps projects that reach step 25 with PIT data.

## The applicability ceiling (RQ6 real-world)

The RepoReapers funnel: 1161 projects → 507 analyzed → 44 w/ ≥1 included
assertion → **11 complete (~1.7%)**. Tests 81,834 → 33,390 included.
**Assertions 122,166 → 711 included (0.58%)** — the collapse is at the
assertion level.

**Shadowing is the dominant effect.** `filter_result` short-circuits at the
first REJECT, so a fix's *true reach* = cases where it's the first reject AND
they pass the later filters. Of ~121k excluded assertions, **27% have 1
blocker, 62% have 2, 10% have 3** → ~73% need multiple coordinated fixes; any
single fix is marginal. Dominant blocker combos: `ParameterType+ReturnType`
31,679 · `MissingValue+UnsupportedAssertion` 25,113 · `MissingValue` 21,616.

The real ceiling is **object/string/state predicates**, not the scalar type set:
boolean methods are mostly predicates on objects/strings (`hasNext()`,
`matches(String,…)`) gated by object/string inputs or object state — the hard,
loop-shadowed territory. ParameterType rejects 60,289 real-world. char+boolean
support (Phase 1) removes a stated limitation but only ~12% of the boolean
headline survives shadowing. Inline ctor-param generalization (#3) unshadows
only ~2.7% of shadowed boolean predicates — 94% of boolean-return rejects have
a field/variable receiver (`@Before`/factory/builder), needing stateful-setup
handling (overlaps #15/#8).

**No cheap fix moves this > marginally** — substantial RepoReapers progress
requires string + object types (inputs, outputs, oracles) + stateful setup,
all of which hit the loop/summarization research wall.

## SPF-stage failure breakdown (collected data)

From `task` (stage `EXECUTE_JPF`, status `FAILED`): 2,995 dev / 368 real-world.

| Cause | dev | real-world | Kind |
|---|---|---|---|
| Uncaught `ArithmeticException` on explored path (#18) | 827 | 18 | ✅ **resolved** — exception-path capture (Phase 1) |
| PC size limit exceeded | 790 | — | resource (tunable) |
| `class not found` (`NoSuchMethodException`) (#21) | 534 | 50 | partly resolvable |
| depth limit exceeded | 524 | 24 | resource (tunable) |
| listener NPE `writeSpecificationFiles` (#19) | 89 | 7 | ✅ **resolved** — our bug (Phase 1) |
| AIOOBE / `NoSuchMethodError` / OOM / timeout | ~99 | ~85 | env / resource |
| symbolic array length (#20) | 31 | 12 | resolvable (capability) |
| native peer / `UnsatisfiedLinkError` (#21) | — | ~109 | partly resolvable |
| transcendental/sqrt not supported (#11) | 1 | — | resolvable (model class) |

**Key:** after Phase 1, the biggest *resolvable* SPF-stage buckets are
exception capture (done) and resource limits (tunable). The remaining resolvable
capability gap is symbolic array length (#20).

## Most promising improvement directions

Ranked by impact-realism, scored on the metric each moves.

### Tier 1 — the JARVIS evidence run (highest ROI, unblocked)

Not a capability fix — the *evidence* step. Phase 1 built the capability; it has
not yet produced head-to-head numbers. Run the pinned JARVIS-era scoreboard
(`jarvis-scoreboard-evidence-run` plan, 0/7): pin fixtures, reset scratch DB,
execute BASELINE/NAIVE/IMPROVED, collect real PVC/IC. This is the only clean
"better than SOTA" claim and it's already de-risked (8/11 SPF spikes FULL).
Everything else is gated on whether (1) is promising.

### Tier 2 — RQ6 applicability levers (the rejection reason)

Scored as applicability %, not mutation score (RQ1 barely moves: ~0.05pp on
strong suites). Ordered by **shadowing-aware net reach** — the count of
assertions where a filter is the *first* reject (the only ones a fix actually
unblocks). Re-run the analysis after any fix to reprioritize:

```python
from teralizer.config import db_config
from teralizer.applicability_priorities import generate_report, print_report
with db_config.get_test_engine().connect() as conn:
    print_report(generate_report(conn))
```

| # | Lever | Net reach | Why it's first |
|---|---|---|---|
| 5 | **MUT identification** (mutation-oracle + Ghafari fallback) | 58,122 | The dominant blocker — and it is NOT "missing values" but the MUT-id layer failing (`tested_class_path IS NULL` on chained/fluent calls like `wheels.size()`). Unshadows 25k MissingValue+UnsupportedAssertion and 11k MissingValue+ParameterType pairs. Spec: `2026-06-27-mutation-based-mut-identification`. |
| 6 | **More assertion types** (ReturnType is the #2 first-reject) | 33,978 | Object-returning getters on domain types (`JID.getDomain`, `LeapYear.checkTheYear`). Overlaps #7 (void/state oracles). |
| 4 | **Interprocedural assertion analysis** (ExcludedTest cascade) | 20,647 | Recovers ~86% false-positive NoAssertions rejects; prerequisite for helper-delegated MUT-id. |
| 9 | **@ParameterizedTest** | — | Param tests are already parametric; ideal raw material. |

**MissingValue taxonomy** — the 58,122 first-reject MissingValue assertions
break down by call-extraction state (re-run `compute_missingvalue_taxonomy`):

| Category | Count | % | Fixable? |
|---|---|---|---|
| `instance_call_in_source_not_extracted` (obj.method() in source, never extracted) | 22,261 | 38.3% | **yes** — MUT-id extraction gap |
| `instance_call_extracted` (var.method found, MUT not resolved) | 16,630 | 28.6% | **yes** — receiver type resolution |
| `no_call_visible` (field access / instanceof / bare var / fail()) | 16,583 | 28.5% | partly — needs #6/#7 (assertion types) |
| `static_call_extracted` (Class.method found, MUT not resolved) | 1,366 | 2.4% | **yes** — classpath resolution |
| `static_call_in_source_not_extracted` | 697 | 1.2% | **yes** — MUT-id extraction gap |
| `casted_call_extracted` / `other_call_extracted` | 585 | 1.0% | yes — chained-call resolution |

**Key:** ~70% of the dominant blocker (40k of 58k) is fixable via MUT-id
extraction + receiver-type resolution — the call is either already extracted
but the MUT wasn't resolved, or visible in the source but never extracted.
Only 28% (`no_call_visible`) needs assertion-type expansion (#6) rather than
MUT-id.

**Shadowing insight that reorders the old priority:** ParameterType looks like
the #2 blocker by raw rejects (60,289) but has only 8,326 net reach — 86%
shadowed behind MissingValue/ReturnType. UnsupportedAssertion has 29,170 total
rejects but **zero** net reach (entirely shadowed behind MissingValue).
72.4% of blocked assertions hit >=2 filters, so most fixes are marginal in
isolation. Fix MUT-id (#5) first to unshadow the masses.

**Projects closest to completion** (top candidates for targeted investigation —
they have included assertions, so the pipeline partially works; re-run the
analysis after a fix to see blocked assertions move toward inclusion):

| Project | Included/Total | % |
|---|---|---|
| `byteseek` | 124/934 | 13.3% |
| `JadConfig` | 72/1734 | 4.2% |
| `fsola` | 53/672 | 7.9% |
| `HdrHistogram` | 53/296 | 17.9% |
| `combinatoricslib` | 39/757 | 5.2% |
| `vrockai_beast` | 21/68 | 30.9% |

### Tier 3 — breadth / capability expansion (deferred)

String/array/object inputs (#2) → symbolic array length (#20) → void returns
(#7) → `toIntExact` cast-oracle (#16) → side-effects/UIF + call sequences
(#8/#15) → multi-test reduction (#12) → PIT isolation (#13) → Gradle/structure
(#14). These hit the loop/summarization research wall; net gain is low until
stateful setup lands.

### Tier 4 — research / deep (gated)

- **#17 QF_FP/NaN** — needs QF_FP threaded through ~40 solver methods. HIGH
  effort; only if a concrete case demands it (shared gap with JARVIS).
- **Differential oracle** (optional strategic direction) — carries the original
  test's asserted constant as an independent oracle alongside the SPF-derived
  one. The principled way to close the `Interval` class (reproduce JARVIS's
  semantic-bug finding where the implementation is itself wrong). Distinct
  contribution; do not fold into Phase 1.
- **Constraint-encoding depth** (compound terms, e.g. `a == b+1`) — constraint
  utilization is 11–69%; the largest *effectiveness* lever when generalization
  applies; path to a mutation-score story on weak-baseline datasets.

## What NOT to chase

- **Mutation score as the headline** — RQ1 was not the primary rejection reason
  and barely moves with applicability fixes.
- **The full 1.7% via cheap fixes** — no single fix moves RQ6 > marginally;
  chasing it distracts from the JARVIS comparison + characterizing the barrier.
- **#10 double/float bounds bug** — retracted; moot in collect-constraints mode.
- **Bug-finding parity** — JARVIS's bug results are cherry-picked, need manual
  triage, and single-version "bugs" are over-approximation FPs. Concede
  Interval + Precision; match JARVIS only on the exception/precondition class
  that the SPF-derived oracle can express.

## Pointers

- Shipped Phase 1 tasks: `docs/plans/archive/2026-06-26-beat-jarvis-phase1.md`.
- Re-runnable shadowing-aware prioritization analysis: `analysis/src/teralizer/applicability_priorities.py` (run `generate_report` against `postgres_test`; tests in `analysis/tests/test_applicability_priorities.py`).
- RQ6 real-world barrier inventory + funnel: `2026-06-26-applicability-barriers`.
- JARVIS per-case SPF spike verdicts + provenance: `2026-06-26-jarvis-case-coverage`.
- Evidence run (next): `2026-06-27-jarvis-scoreboard-evidence-run`.
- SPF type-support study: `~/Projects/phd-thesis/projects/spf-eval/RESULTS.md`.
- Focal-method literature (source-verified, `history://FocalMethodLit`): adopt
  Ghafari mutator/inspector classification (SCAM'15 / JSS'17
  `arXiv:2208.00264`, OpenAlex `W2096985255`) as the highest-leverage feasible
  MUT-id fix; then sub-scenario decomposition for multi-assert, an NCC name
  tiebreaker, and a unique-call fallback (Methods2Test `arXiv:2203.12776`).
  He et al. (FSE'24) confirms MUT-id quality drives downstream accuracy.
- Code touchpoints: type gate `src/main/java/teralizer/util/Configuration.java`
  (`SUPPORTED_TYPES`); MUT id `src/main/java/teralizer/spoon/analysis/TestAnalysis.java`
  (`findTestedMethodCall`); filters `src/main/java/teralizer/processing/filter/`;
  instrumentation `src/main/java/teralizer/processing/task/JpfInstrumentationTask.java`;
  jpf-symbc model pattern `src/classes/java/lang/Math.java`.
