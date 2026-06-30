---
title: SPF Specification-Extraction Hardening
type: spec
status: draft
created: 2026-06-30
parent: 2026-06-29-beyond-jarvis-census-findings
---

# SPF Specification-Extraction Hardening

## Problem

`teralizer.jpf.TestGeneralizationListener` extracts a path-exact specification by running
SPF (constraint-collection mode, single concrete path) over an instrumented wrapper that
calls one tested method, capturing the concrete + symbolic input/output at the tested
method's return. It is the foundation the whole generator builds on, and it is the source
of the foundational fixes the beyond-JARVIS census surfaced.

Its structure is why those bugs recur. The listener is one mutable, cross-callback state
machine (`recursionDepth`, `isInInstrumentedMethod`, `pendingThrownException`,
`instrumentedInputArguments`) that fuses five concerns — target detection, concrete
capture, symbolic capture, model transformation, and file I/O — in a single
`writeSpecificationFiles`, and collapses every non-success into one untyped
`"Failed to collect … for unknown reason"`. It has no reachability gate and no
`stateBacktracked` handling. Each census bug is a symptom of that fusion + the untyped
outcome, not an independent defect:

| census symptom | underlying flaw |
|---|---|
| `Integer@24c` rendered as a literal | stringly-typed capture (`String.valueOf` on an `ElementInfo`) |
| crash rendering a null boxed `Boolean`/`Character` seed | value round-tripped as the string `"null"` |
| Postgres insert fails on `0x00` in a throwable message | untyped text shipped straight to the DB |
| unreachable assertion (dead `else`) → `"unknown reason"` | no reachability signal; binary "files exist?" outcome |

Three of these have interim point-fixes (typed boxed-wrapper capture; reference-typed
`null` rendering; NUL stripping at the DB boundary). They stop the bleeding but leave the
structure — and the next stringly/untyped edge — intact. This spec defines the target
architecture that makes the whole class structurally impossible, reached incrementally
from today's green baseline.

## Target architecture

```mermaid
flowchart LR
  A[original JUnit run + coverage] -->|P1 reachability gate| B[reachable candidates]
  B --> C[SPF run: observer-only listener]
  C -->|P3 raw Invocation| D[pure SpecificationExtractor]
  D -->|P5 typed Value| E[Model -> spec JSON]
  C -->|P2 total ExtractionOutcome| F[diagnostics row]
```

Five principles:

- **P2 — one total, typed `ExtractionOutcome` per candidate.** A closed set:
  `EXTRACTED | TARGET_NOT_ENTERED | TARGET_NOT_EXITED | UNSUPPORTED_TERM | PC_TOO_LARGE |
  TIMEOUT | ORACLE_THREW | NATIVE_MODEL_GAP`. Every run maps to exactly one; "unknown" is
  unrepresentable. This is the census diagnostic taxonomy produced at the source instead
  of grepped from stack traces.
- **P5 — typed `Value`s, not strings.** `Primitive | Reference(nullable) | StringValue |
  SymbolicExpr`. The Java renderer pattern-matches on the variant; no identity-hash
  strings, no `"null"`, no raw NUL reaching a downstream parser or the DB.
- **P3 — the listener only observes.** It records a raw `Invocation { concreteIn, pcIn,
  concreteOut | thrown, symbolicOut }` and an observable state snapshot. Transformation
  (SpfToModel → JSON) and file I/O run *after* `jpf.run()` in a pure `SpecificationExtractor`
  — unit-testable, no JPF coupling in the part that holds the bugs.
- **P4 — identify the target by frame identity, not a depth counter.** Capture the
  wrapper's call frame into the tested method; the return that matters is that frame's
  return. Deletes the `recursionDepth`/`isInInstrumentedMethod`/`pendingThrownException`
  mutable dance and is correct under recursion and backtracking.
- **P1 — gate on reachability before SPF.** Drop assertions the original suite never
  executes (the dead-`else` class) so SPF never runs on dead code.

Contract sketches (Java 8 — no records/sealed; tagged classes + enums):

```java
final class ExtractionOutcome {
    enum Kind { EXTRACTED, TARGET_NOT_ENTERED, TARGET_NOT_EXITED, UNSUPPORTED_TERM,
                PC_TOO_LARGE, TIMEOUT, ORACLE_THREW, NATIVE_MODEL_GAP }
    final Kind kind;
    final String detail;          // human-readable, never null
    final Invocation invocation;  // non-null iff kind == EXTRACTED
}

abstract class Value { /* PrimitiveValue | ReferenceValue(nullable) | StringValue | SymbolicValue */ }

final class Invocation {          // raw capture; no transformation, no I/O
    final List<Value> concreteIn;
    final Constraint  pcIn;       // SPF path-condition header (symbolic input)
    final Value       concreteOut;   // or…
    final CapturedException thrown;  // …exactly one of these
    final Expression  symbolicOut;
}

interface SpecificationExtractor {                 // pure; no JPF, no VM
    ExtractedSpec toSpec(Invocation invocation);   // Model + JSON, fully unit-testable
}

// Observable listener state, read by JpfExecutionTask to classify (no "unknown reason"):
final class ListenerState { boolean wrapperEntered, targetEntered, targetExited, specCaptured; }
```

## Incremental sequence

Each phase is independently shippable and verified (harness unit tests + a clean PIT-free
census re-run) before the next starts. Order minimizes risk: typed outcomes first (immediate
diagnostic correctness, no behavior change for the success path), then the observer boundary
that makes the rest unit-testable, then value typing and frame identity on that testable base.

### Phase 1 — typed outcome + observer boundary (P2 + P3)

The listener stops transforming/writing inside `methodExited`; it records the raw
`Invocation` + `ListenerState`. `JpfExecutionTask` runs the extractor post-run and maps the
result to an `ExtractionOutcome`: a captured invocation → `EXTRACTED`; no `Invocation` with
`targetEntered == false` → `TARGET_NOT_ENTERED` (the isAscii dead-`else` case); `targetEntered
&& !targetExited` → `TARGET_NOT_EXITED`; the existing throw-classifying hooks map to
`PC_TOO_LARGE` / `TIMEOUT` / `NATIVE_MODEL_GAP`. The `"unknown reason"` string is deleted.

- Files: `src/main/java/teralizer/jpf/TestGeneralizationListener.java`,
  `src/main/java/teralizer/processing/task/JpfExecutionTask.java`,
  new `src/main/java/teralizer/jpf/{Invocation,ExtractionOutcome,SpecificationExtractor}.java`.
- Tests: extend `src/test/java/teralizer/jpf/JpfListenerHarness.java` to return the
  `ExtractionOutcome`; add a `targets/UnreachableTargetWrapper` whose wrapper never calls the
  tested method → asserts `TARGET_NOT_ENTERED`; keep existing capture/outcome/symbolic tests
  green (now asserting `EXTRACTED`).
- Acceptance: every EXECUTE_JPF result carries a typed kind; the census run's isAscii row is
  `TARGET_NOT_ENTERED`, not a failure; no `"unknown reason"` anywhere.

### Phase 2 — typed values (P5)

Introduce the `Value` model. The listener's capture produces `Value`s (primitives boxed to
host wrappers; null references as `Reference(null)`; strings via `ElementInfo.asString()`;
symbolic terms as `SymbolicExpr`). `ModelToJavaTransformer` and the diagnostic writer consume
`Value`s, not re-parsed strings. The interim point-fixes (boxed capture, `"null"` rendering,
NUL stripping) become properties of the typed model and their string special-cases are removed.

- Files: new `src/main/java/teralizer/jpf/Value*.java`; `TestGeneralizationListener` capture
  helpers; `src/main/java/teralizer/transformer/ModelToJavaTransformer.java`;
  `src/main/java/teralizer/processing/task/JunitDataCollectionTask.java` (diagnostic text).
- Tests: harness asserts a typed null boxed `Boolean`/`Character` and a `char`-0 string render
  to valid Java; the existing `ModelToJavaTransformerTypeSupportTest` null/primitive cases hold.
- Acceptance: no `String.valueOf`/`toString`-on-`ElementInfo` in capture; no `"null"`/identity
  strings; a NUL-bearing value never reaches a parser or the DB.

### Phase 3 — frame-identity target detection (P4)

Replace `recursionDepth` + `isInInstrumentedMethod` + the entry/exit matching with capture of
the tested-method frame entered from the wrapper frame; the write trigger is that exact frame's
return. Add `stateBacktracked` handling (or rely on the single constraint-collection path,
documented). Removes the mutable counters.

- Files: `TestGeneralizationListener`.
- Tests: harness target with a recursive tested method and one with a sibling method sharing a
  name prefix; assert the correct frame is captured.
- Acceptance: capture is correct for recursive/nested targets; no behavior change for the ~210
  currently-handled assertions.

### Phase 4 — reachability gate (P1)

Exclude assertions the original suite never executes before instrumentation/SPF, using the
coverage already collected (`COLLECT_JACOCO_DATA_ORIGINAL`). Requires confirming the coverage
granularity distinguishes a dead branch; if it does not, this phase stays a no-op and P2's
runtime `TARGET_NOT_ENTERED` remains the safety net.

- Files: the assertion-filtering stage (`FILTER_ASSERTIONS` path) + its repository queries.
- Acceptance: the isAscii dead-`else` assertion is excluded before SPF; SPF runs only on
  reachable assertions.

## Key decisions (for review)

1. **Phasing** — incremental as above (recommended), vs. a clean-room rewrite. Incremental
   keeps the ~210 working assertions green throughout.
2. **Reachability signal (Phase 4)** — JaCoCo line/branch coverage (already collected) vs. a
   lighter "instrumented call line executed" probe. Recommend JaCoCo if granularity suffices;
   else rely on P2's runtime classification and drop Phase 4.
3. **Path contract** — keep the concrete-path-exact, single-path semantics
   (`symbolic.collect_constraints=true`); confirmed intended, not "first of many."
4. **`ExtractionOutcome` recording** — `TARGET_NOT_ENTERED` / `UNSUPPORTED_TERM` etc. are
   recorded as **exclusions** (not pipeline failures), matching how the census scorecard and
   the run-script failure-check already treat per-assertion JPF gaps.

## Acceptance criteria

- No `"unknown reason"` (or any untyped catch-all) reachable; every EXECUTE_JPF result is one
  `ExtractionOutcome.Kind`.
- The three value-bug classes are structurally impossible: capture and rendering operate on
  typed `Value`s; no stringly round-trip; no NUL reaches a parser/DB.
- The transformation + serialization path is pure and unit-tested via `JpfListenerHarness`
  for each outcome kind and each value kind.
- A PIT-free census re-run reports typed per-assertion exclusions and **zero** breakage
  (build/collect/generalize/execute), with no regression in the count of `EXTRACTED`
  assertions vs. today's baseline.

## Out of scope

- Closing the model gaps themselves (the 154 `NoSuchMethodException` CNFEs / native peers) —
  `2026-06-28-native-peer-model-coverage`.
- PIT-at-scale and generated-build robustness — `2026-06-30-census-build-robustness-and-pit-scale`.
- The generator/planner downstream of the spec — `2026-06-28-clause-driven-input-generation`.
