---
title: Funnel Improvement Levers
type: spec
status: active
created: 2026-07-06
parent: 2026-06-26-teralizer-overview
---

# Funnel Improvement Levers

Design and acceptance for the four levers the midpoint rerun telemetry ranks as the best
effectiveness/applicability improvements. Grounded in the 60%-corpus snapshot
(`postgres_reporeapers_rerun2`, ~64k assertions). Full-run numbers replace the midpoint ones
when the run lands, and no verdict below is expected to flip.

## Investigation findings (root causes, not symptoms)

**MISSING_TESTED_FILE (28.7k) decomposes via `mut_resolution_observation.no_pick_reason`:**

| Root cause | Size | Verdict |
|---|---|---|
| `UNSUPPORTED_ASSERTION_SHAPE` (14.6k) | assertThat 5.8k, assertNotNull 2.8k, fail 2.7k, assertNull 1.9k, rest 1.5k | Not a resolver problem at all. The resolver never sees these assertion kinds, so no producer is ever looked for. The same population as Lever 1: supporting the assertion kind fixes the "missing file" too. |
| `LIBRARY_DECLARATION` (8.5k) | Picked call is JDK/library-declared: `toks.get(0)`, `list.get(0)`, `e.getMessage()` | Half-recoverable. The receiver (`toks`, `list`) is routinely produced by CUT code one step earlier. The resolver already unwraps project-declared inspectors; it stops at library accessors. Lever 4 extends the unwrap through a small JDK-accessor allowlist (`List.get`, `Map.get`, `Iterator.next`, array-ish accessors). `e.getMessage()` cases fold into Lever 1's exception-oracle work. |
| `UNRESOLVED_SOURCE_DECLARATION` (3.6k) | `mapper.readValue(...)` (Jackson), `org.xbill.DNS`, elasticsearch | Correct refusals. These tests exercise third-party libraries, not the CUT. Fixing means changing what "tested method" means. Out of scope, stated in the paper. |
| `NO_VISIBLE_CALL` (2.2k) | `assertEquals(5, count)`, field state, literals | Correct refusals. The producer is fixture state or control flow, not a call. Research-grade slicing (R2's grave). Out of scope. |

**SPF `UNCAUGHT_EXCEPTION_PATH` (2.8k) is not one cause.** Exception-type classification of the
task diagnostics shows ~35% are JPF environment gaps misfiled under a generic code:
`UnsatisfiedLinkError` 304 (missing native peers — should be `MISSING_NATIVE_PEER`),
`ClassNotFoundException` 137, `NoSuchMethodError` 46. The rest split between application
exceptions on the concrete path (NPE 263, UnsupportedOperation 197, IllegalArgument 125...)
and JPF-vs-JVM divergence smells (`AssertionError` 150, `ComparisonFailure` 52 — the test
passed on the JVM but its assertion failed inside JPF). Consequence: native-peer work is
UNDER-counted by the stable codes, and the reclassification must land before the queue
re-ranks SPF work.

**The Java floor exists and has exactly one hole.** `MavenDependencyManager.applyTestCompilerFloor`
floors `testSource`/`testTarget` to 1.8, but compares the RAW config text: a project with
`<source>${java.version}</source>` and `<java.version>1.5</java.version>` in properties is not
floored because `${java.version}` is not in the literal-level switch. 5 of the 10 failed
generalized builds at midpoint are exactly this shape.

**The 138 execution-stage losses are timeouts, not flaky tests.** Two projects, one cause:
`junit.max-execution-time = 60` s applies to the WHOLE suite, original and generalized alike.
A generalized suite (131 properties × 100 tries in gwt-commons-codec) cannot fit the budget
the original suite got. No isolation machinery is needed — the timeout budget for
`EXECUTE_TESTS_GENERALIZED` must scale with generated-property count.

## Lever 1 — assertion-kind support (recall, ~2× funnel entry)

Two sub-families, both riding existing machinery. The admission gate is
`Configuration.GENERALIZABLE_ASSERTS` consumed through `TestAnalysis.isGeneralizable`,
which both `UnsupportedAssertionFilter` and `findGeneralizableAsserts` share — a supported
kind must pass BOTH the gate and recipe derivation, or it enters the funnel and dies
later with a worse diagnostic. Gate and recipe change together, per sub-family:

- **Equality-isomorphic Hamcrest.** `assertThat(x, is(y))` and `assertThat(x, equalTo(y))`
  (3.8k of 5.3k assertThat uses) desugar to the assertEquals recipe: actual = `x`,
  expected = `y`. Implementation is recipe-derivation-side (`TestAnalysisTask` /
  GeneralizationRecipe): recognize the two matcher forms structurally (matcher factory
  method from `org.hamcrest.*Matchers`, single argument) and emit the same recipe an
  assertEquals would. Everything downstream (filters, SPF, generation) is unchanged.
  Non-goal: every other matcher family (`contains`, `hasSize`, `not`...) — sized small,
  each needs its own oracle semantics.
- **try/fail/catch exception tests.** `fail()` with `fail_context = TRY_BLOCK_EXPECTING_EXCEPTION`
  (1.5k of 2.7k fail uses) is the pre-assertThrows idiom for an exception oracle. The
  EXCEPTION oracle machinery (thrown-oracle capture, widening license, risk-gated message
  widening) already exists for assertThrows. The recipe front-end gains the idiom: MUT = the
  last CUT call in the try block before `fail()`, expected exception = the caught type.
  `e.getMessage()` assertions in the catch block map to the existing message-widening path.
  Non-goal: `CATCH_BLOCK_SHOULD_NOT_REACH` and `GUARD_BRANCH` fail() uses (no exception
  expected — different semantics).

Acceptance: one verification fixture per sub-family (golden: SYMBOLIC FULL properties from
a Hamcrest-equality test and a try/fail/catch test), existing fixture goldens unchanged,
midpoint-DB query shows the `UNSUPPORTED_ASSERTION_ASSERT_THAT`/`_FAIL` reject counts drop
proportionally on a sentinel re-run.

## Lever 2 — yield-gap engineering (+~50% final_usable)

- [x] **Floor property resolution.** `applyTestCompilerFloor` resolves single-level
  `${property}` references against the pom's `<properties>` before the level comparison.
  Unresolvable references stay untouched (the build-environment telemetry already labels
  them). Same resolution for the properties-based path.
- [x] **Scale the generalized execution budget.** `EXECUTE_TESTS_GENERALIZED` gets its own
  timeout derived from the generated-property count. Mechanism constraint from the code:
  `TestExecutionTask` builds its `ConsoleCommand` with the flat
  `junit.max-execution-time` in the CONSTRUCTOR, before the included-property count is
  fetched in `run()`. The timeout determination must move to run time (or the command
  must accept a late timeout). Formula shape
  `junit.max-execution-time × max(1, properties × tries / baseline-tries-budget)`, exact
  constants decided at implementation with the fixture corpus as evidence. The
  original-suite stages keep the flat 60 s. Config stays in `reference.conf` beside the
  existing knob.

Acceptance: the l10n-maven-plugin pom shape floors correctly (unit test on the manager),
a fixture with a property-resolved source level passes the generalized build, and the
gwt-commons-codec-shaped timeout ceases at a sentinel re-run. No fixture golden changes
expected (fixtures are small and fast).

## Lever 3 — SPF loss reclassification, then census (decision gate)

- [x] **Reclassify before deciding.** Single choke point, verified:
  `TaskDiagnosticClassifier.classify` routes every "Identified N error(s) during JPF
  execution" failure to `UNCAUGHT_EXCEPTION_PATH`; the underlying exception type is the
  first line after `NoUncaughtExceptionsProperty` in the message detail. New routing on
  that type: `UnsatisfiedLinkError` → `MISSING_NATIVE_PEER`, `ClassNotFoundException` →
  `MISSING_JPF_MODEL_CLASS` (code exists, unused), `NoSuchMethodError` →
  `MISSING_JPF_MODEL_METHOD` (same), JUnit `AssertionError`/`ComparisonFailure` → new
  `JPF_DIVERGENT_ASSERTION`, rest stays `UNCAUGHT_EXCEPTION_PATH`.
  `jpf_extraction_summary.failure_counts` aggregates task_diagnostic reason codes
  (`JpfAnalysisTask`), so the rollup inherits the reclassification with no second
  classifier to keep in sync.
- **Then the census.** With honest codes, the ranked stable-cause table (already in the
  report) IS the census. The decision — invest in native peers/models vs accept the
  ceiling — goes to the operator with true counts. `JPF_DIVERGENT_ASSERTION` cases get a
  sampled deep-dive first: divergence between JVM and JPF execution is a soundness smell,
  not just a loss.

Acceptance: reclassification covered by unit tests on the writer, the report's
stable-cause table shows the new codes on a sentinel re-run, divergent-assertion sample
documented in the census audit doc.

## Lever 4 — library-accessor unwrap (MUT-id recall)

Extend the resolver's inspector-unwrap to a fixed JDK-accessor allowlist: when the picked
call is `List.get`/`Map.get`/`Iterator.next`/`Optional.get` on a receiver produced by a
CUT call in the same method, retarget to the producer (the existing receiver-producer
machinery — same mechanism, one more admission rule). The observation row records the
unwrap in the existing `inspector_unwrapped`/provenance columns.

Sizing query: [x] implemented in `teralizer.mut_resolution_funnel` as the
`Lever 4 library-accessor unwrap sizing` section. Operationalization: count
`LIBRARY_DECLARATION` rows, bucket the picked call by the fixed allowlist
(`List.get`, `Map.get`, `Iterator.next`, `Optional.get`, `other`), and estimate a
row as recoverable when the accessor receiver is an inline call, a `LOCAL_OTHER`
receiver, or appears as a same-method candidate in `candidate_details`. On the
live `postgres_reporeapers_rerun2` snapshot this reports 9,206 total
abstentions and 4,221 estimated recoverable rows: `List.get` 4,047 total / 3,794
recoverable, `Map.get` 568 / 427, `Iterator.next` 0 / 0, `Optional.get` 0 / 0,
and `other` 4,591 / 0.

Acceptance: resolver unit tests for the allowlist, `LIBRARY_DECLARATION` abstentions drop
on a sentinel re-run, no new T4 guesses (the unwrap must stay tier-preserving).

## Sequencing

Lever 2 first (highest certainty, no theory), then Lever 1 (fixture-driven recipe work),
Lever 3 reclassification alongside (small writer change), census after the NEXT corpus
event picks up the honest codes. Lever 4 after its sizing query, likely with Lever 1's
resolver context loaded. Full-run verification for everything batches into the next
scheduled corpus run per the measurement policy.

## Explicitly out of scope

- `UNRESOLVED_SOURCE_DECLARATION` (third-party targets) and `NO_VISIBLE_CALL`
  (fixture-state slicing) — correct refusals, paper states the ceiling.
- Broad Hamcrest matcher support beyond is/equalTo.
- Receiver-state widening, collections/heap, void/state oracles — parked research.
