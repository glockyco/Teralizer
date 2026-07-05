---
title: Partial Sound String Support
type: plan
status: active
created: 2026-06-30
parent: 2026-06-26-teralizer-overview
---

# Partial Sound String Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generalize tests over `String` parameters and returns for the subset of string operations SPF handles *soundly*, excluding the unsound/unsupported operations structurally — never by luck.

**Architecture:** SPF's `symbolic.strings` backend and Teralizer's model-level string plumbing already exist (`SpfToModelTransformer` handles `StringConstraint`/`DerivedStringExpression`/`StringSymbolic`; `TypeDomain.STRING` and `defaultRecipe`'s `Arbitraries.strings()` are present). The remaining work is five co-dependent seams that must land together: (1) capture the string path condition + string return in the listener; (2) render string operators to Java in `ModelToJavaTransformer.fold`; (3) a `StringDomainPlanner` that builds a *satisfying* bounded, non-null arbitrary from the captured clauses; (4) a structural pre-screen that enables `symbolic.strings` and excludes unsound/unsupported ops via a typed `ExtractionOutcome`; (5) jpf-symbc robustness so an unsupported op degrades to a clean per-assertion exclusion, not a process crash. Soundness comes from the structural screen + the full-predicate residual filter, **not** from the random-sampling execution gate.

**Tech stack:** Java 8, SPF/jpf-symbc (`symbolic.strings`, git submodule), jqwik `Arbitraries`, Spoon AST, Velocity template `jpf-config.vm`.

---

## Progress

The string-support seams are implemented and merged. String path-condition capture, string return capture, the structural string-op screen, conditional `symbolic.strings`, typed `UNSUPPORTED_TERM` exclusions, `isEmpty`, `equalsIgnoreCase`, and the unified-model string transforms (`trim`, `replace`, `toLowerCase`, `toUpperCase`) all route through the unified expression model.

Current representation:
- SPF string constraints and derived string expressions become typed `Invocation` / `Not` / `Variable` / `Constant` model nodes.
- `MethodCapabilities` is the source for supported string methods, renderability, return domains, and input-generation constraint kinds.
- `StringDomainPlanner` structurally satisfies positive `equals`, `isEmpty`, `startsWith`, `endsWith`, and `contains` clauses; output-only transforms stay residual-filter checked via the rendered predicate.
- Synthetic SPF temporaries are recovered by walking typed model variables, not by scraping serialized JSON for numeric symbol names.

**Remaining:**

- **Task 7** (corpus verification) is unblocked: MUT-id fusion v1 resolves best-effort picks with confidence tiers, so String-parameter MUTs now reach the string seams, and the `SymbolicLengthInteger` ingestion fix (`8303a4fd`) keeps length clauses tied to their receiver (pinned by the `string-sound-set` golden, length row FULL 100/100). Run the empirical checks on the sentinel subset (scratch DB, expected census in the config headers). The same run doubles as the first real-world exposure of the ingestion-totality change: derived string symbols (`indexOf`/`lastIndexOf` family) that previously flattened to free variables now surface as typed `UNSUPPORTED_TERM` exclusions, so the sentinel census may shift in both directions (length-bearing MUTs gained, indexOf-family MUTs excluded). Full-corpus funnel measurement batches into the next scheduled corpus evaluation event; when the observability telemetry lands, use its candidate type-eligibility fields to separate newly resolved String opportunities from receiver/stateful setup blockers.

## Soundness invariants (non-negotiable)

1. **Structural screen is the guarantee; execution is only a backstop.** `NonPassingTestFilter` is a bounded random sampler (N tries + the concrete original as `edgeCases=FIRST`, seed 0). SPF's known holes live on thin slices (`i == s.length()`, `s == null`) that random strings essentially never hit, so the gate cannot prove soundness for them. Only emit specs whose every string op is in the sound set and whose string domain the captured path condition actually constrained.
2. **Emit only over the constrained domain.** Generated strings are **non-null** in v1 (SPF's `IFNULL` falls through → the null path is never captured; restricting to non-null records that the spec's validity domain excludes null — a sound restriction, not a hack).
3. **Co-dependency.** Making `String` a *generated* parameter without rendering its operators forces exclusions (`ConstraintClauses.from` rethrows `NonGeneralizableExpressionException` for a clause that constrains a generated parameter). Seams (1)–(3) must ship together or not at all.
4. **No regression** of the ~250 numeric/char/boolean sound generalizations.

## Current implementation map

- `src/main/java/teralizer/jpf/TestGeneralizationListener.java` captures the numeric path condition, the string path condition, concrete arguments, and symbolic string/numeric returns.
- `src/main/java/teralizer/transformer/SpfToModelTransformer.java` maps SPF string constraints and derived string expressions to `Invocation`/`Not`, and refuses unsupported terms with `UnsupportedSpfTermException` instead of silently dropping operands.
- `src/main/java/teralizer/transformer/ModelToJavaTransformer.java` renders `Invocation` via `MethodCapabilities` and raises `NonGeneralizableExpressionException` for unsupported or type-inconsistent calls.
- `src/main/java/teralizer/jqwik/planning/MethodCapabilities.java` records supported method symbols, input-generatable constraints, output renderability, return domains, and static/instance shape.
- `src/main/java/teralizer/jqwik/planning/StringDomainPlanner.java` consumes the input-generatable string constraints and leaves output-only transforms to the residual predicate.
- `src/main/java/teralizer/processing/filter/StringOperationFilter.java` consults `MethodCapabilities` for the structural string-op screen.
- `src/main/java/teralizer/transformer/VariableDescriptorCollector.java` recovers typed SPF temporaries from model variables.
- `jpf-symbc/jpf-symbc/src/main/gov/nasa/jpf/symbc/bytecode/SymbolicStringHandler.java` handles the admitted string operations and reports unsupported ones as typed exclusions.

## Sound-set decision (v1)

- **Input-generatable:** `equals`, `isEmpty`, `startsWith`, `endsWith`, `contains` on literal string constraints.
- **Output-renderable / residual-filter checked:** `equals`, `equalsIgnoreCase`, `startsWith`, `endsWith`, `contains`, `isEmpty`, `concat`, `trim`, `replace`, `toLowerCase`, `toUpperCase`, `length`, and `String.valueOf`. `length` renders from the `SymbolicLengthInteger` parent tie as a `length()` invocation, so length clauses stay residual instead of flattening to a free integer (pinned by the `string-sound-set` golden since `8303a4fd`).
- **Screen-admitted, ingestion-refused (typed):** `indexOf`, `lastIndexOf`. The structural screen lets them reach SPF, but their derived symbols carry no renderable model mapping, so `SpfToModelTransformer` refuses them as `UNSUPPORTED_TERM` rather than flattening to free variables.
- **Excluded (typed):** `compareTo`, `charAt`, `substring`, regex/region matching, and regex replacement variants whose soundness or generation contract is not modeled.

## Tasks

### Task 1: Spike — capture `spc` and observe the model node shapes (throwaway)

Discover the exact `Model` node produced for each slice operator before writing renderers/interpreters. This de-risks Tasks 2–4.

**Files:** temporary edits + a scratch test in `src/test/java/teralizer/jpf/`.

- [x] **Step 1:** In a scratch harness test (pattern: `TestGeneralizationListenerSymbolicTest.java`), run a target whose MUT branches on `s.equals("foo")`, one on `s.length() > 3`, one on `s.startsWith("a")`, one on `s.indexOf('x')`, and one returning `a.concat(b)`, with `symbolic.strings=true` and a symbolic `String` parameter.
- [x] **Step 2:** Temporarily transform `pathCondition.spc` in `captureInvocation` and log the resulting `teralizer.domain.Expression` tree (node class + `Operator`) for each.
- [x] **Step 3:** Record the node shape per op after the unified model landed: string predicates are `Invocation`/`Not`, string leaves are typed `Variable`/`Constant`, and string-returning transforms are instance `Invocation`s. Acceptance: Tasks 3 and 4 reference concrete node shapes, not guesses. Revert the scratch edits.
- [x] **Step 4: Check ingestion totality.** In the same scratch run, confirm `a.concat(b)` is captured **whole** — `SpfToModelTransformer` has a TODO that silently drops `DerivedStringExpression.oprlist` (audit A-5); verify the captured model reflects the full concat, not a truncated term. Then round-trip the captured string spec through `Model→JSON→Model` (`ModelToJsonTransformer`/`JsonToModelTransformer`) and confirm string nodes survive (audit A-2 found a field-name mismatch in that path). Any gap becomes a fix step in Task 2 (capture) or a local `SpfToModelTransformer`/JSON fix — never a silent drop.

### Task 2: Listener — capture the string path condition + string return

**Files:** `TestGeneralizationListener.java`; `src/test/java/teralizer/jpf/TestGeneralizationListenerCaptureTest.java`; `JpfListenerHarness.java`.

- [x] **Step 1: Write failing tests.** A MUT branching on `s.equals("foo")` → the captured `modelInput` contains the string-equality clause (not just numeric). A MUT returning a computed `String` → the captured `modelOutput` is the string expression, not null.
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Capture `spc`.** In `captureInvocation`, after computing the numeric `modelInput`, transform `pathCondition.spc` via `SpfToModelTransformer.transform(StringPathCondition)`; if non-null, combine: `modelInput = numeric == null ? stringModel : new Operation(Operator.AND, numeric, stringModel)`. (Confirm the exact `Operation` constructor / model-conjunction idiom used elsewhere.)
- [x] **Step 4: Capture string return.** When the return type is `String`, also read the string return attribute (the `StringExpression`/`StringSymbolic` attr, not only `Expression.class`) and transform it to the `modelOutput`. Guard by concrete return type so numeric capture is unchanged.
- [x] **Step 5: Run, expect PASS**, and re-run `TestGeneralizationListenerSymbolicTest`/`...CaptureTest` to confirm numeric capture is unaffected.
- [x] **Step 6: Commit.** `feat: capture string path condition and string return in listener`

### Task 3: Render string operators to Java in `fold`

**Files:** `ModelToJavaTransformer.java`; `src/test/java/teralizer/transformer/ModelToJavaTransformerValueTest.java` (or a new `...StringOperatorTest.java`).

- [x] **Step 1: Write failing unit tests** asserting the rendered Java for each slice operator, matching Task 1's observed node shapes. Expected renderings:

```
EQUALS(l,r)            -> (l.equals(r))
NOTEQUALS(l,r)         -> (!l.equals(r))
EQUALSIGNORECASE(l,r)  -> (l.equalsIgnoreCase(r))
NOTEQUALSIGNORECASE    -> (!l.equalsIgnoreCase(r))
STARTSWITH(l,r)        -> (l.startsWith(r))
NOTSTARTSWITH          -> (!l.startsWith(r))
ENDSWITH(l,r)          -> (l.endsWith(r))
NOTENDSWITH            -> (!l.endsWith(r))
CONTAINS(l,r)          -> (l.contains(r))
NOTCONTAINS            -> (!l.contains(r))
```

- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Add the render paths** for admitted string invocations via `ModelToJavaTransformer.fold(Invocation)`, using `MethodCapabilities` for renderability and return-domain metadata. Numeric/math invocations render through the same `Invocation` path.
- [x] **Step 4: Run, expect PASS**, and run `ModelToJavaTransformerNonGeneralizableTest` to confirm the *excluded* operators (e.g. `MATCHES`) still raise `NonGeneralizableExpressionException`.
- [x] **Step 5: Commit.** `feat: render sound string operators to Java`

### Task 4: `StringDomainPlanner` — satisfying, bounded, non-null arbitrary

Build an arbitrary that *satisfies* the captured clauses (so generation is practical), with the full-predicate filter as the soundness backstop.

**Files:** create `StringDomainPlanner.java`, `StringClauseInterpretation.java`; modify `DomainPlanners.java`; tests `StringDomainPlannerTest.java`, `TypeCapabilityTest.java`.

- [x] **Step 1: Write failing tests.**
  - `TypeCapability.supportsGeneratedInput("java.lang.String")` and `supportsReturnValue("java.lang.String")` are `true`.
  - `InputGenerationPlanner.planParameter` for a `String` parameter uses `StringDomainPlanner` (not `defaultRecipe`).
  - Equality clause `s.equals("foo")` → recipe `return net.jqwik.api.Arbitraries.of("foo")` (not an unbounded filter).
  - `startsWith("ab")` → an arbitrary whose values all start with `"ab"`.
  - No string clause → bounded non-null ASCII: `Arbitraries.strings().ascii().ofMaxLength(N)` with a sensible `N`.
- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement `StringClauseInterpretation`** (mirror `NumericClauseInterpretation`): from the input model, extract per-parameter string constraints — equality target(s), required prefix/suffix/substring, and length bounds — plus consumed clause ids. Contradictory equalities ⇒ empty arbitrary.
- [x] **Step 4: Implement `StringDomainPlanner`** (`supports(TypeDomain.STRING)`; mirror `NumericDomainPlanner.plan`):
  - equality present → `Arbitraries.of(<value>)`;
  - else compose from `Arbitraries.strings().ascii()` with `.ofMinLength/.ofMaxLength` for length bounds and a prefix/suffix/embed builder (`.map`) for `startsWith`/`endsWith`/`contains`;
  - default → `Arbitraries.strings().ascii().ofMaxLength(N)`;
  - **never** `null`; inject the concrete original string as the first value (match the `edgeCases=FIRST` convention). Return the `ParameterGenerationPlan` with the consumed clause ids; the residual full-predicate filter enforces anything not captured by the base arbitrary.
- [x] **Step 5: Register** `new StringDomainPlanner()` in `DomainPlanners.REGISTERED`.
- [x] **Step 6: Run, expect PASS**, plus a test that a `String`-only-parameter MUT is no longer stripped by `TestGeneralizationTask`'s `removeIf` and no longer rejected by `ParameterTypeFilter`/`ReturnTypeFilter`.
- [x] **Step 7: Commit.** `feat: add StringDomainPlanner with clause-satisfying arbitraries`

### Task 4b: Recover symbolic temporaries via typed Model traversal

`TestGeneralizationTask` recovers SPF synthetic temporaries by walking the typed input/output `Model` trees and collecting `Variable(name, TypeDomain)` descriptors. This replaces the JSON regex scrape for numeric-only synthetic names and admits string temporaries when SPF emits them.

**Files:** `TestGeneralizationTask.java`; `src/main/java/teralizer/transformer/VariableDescriptorCollector.java`; `VariableDescriptorCollectorTest.java`; `TestGeneralizationTaskTest.java`.

- [x] **Step 1: Gate on the spike.** The unified model now represents string returns and string path constraints as typed variables/invocations, so a traversal is the correct source of temporary metadata.
- [x] **Step 2: Write a failing test** that a spec containing a string temporary yields that symbol in the recovered temporary parameters.
- [x] **Step 3: Replace the regex-scrape** with `VariableDescriptorCollector`, a `ModelVisitor` over typed `Variable` leaves. Walk `inputModel` + `outputModel`, subtract declared tested-method parameters, and build `MethodParameter`s using the collected `TypeDomain`.
- [x] **Step 4: Run, expect PASS**; numeric temporaries remain recovered and string temporaries are no longer missed.
- [x] **Step 5: Commit.** `refactor(task): recover temporaries from model variables`

### Task 5: Structural pre-screen + conditional `symbolic.strings`

Enable string symbolization only when needed and exclude unsound/unsupported ops before running SPF.

**Files:** `SpfSymbolicConfigSelector.java` (or new `StringSupportScreen.java`); `SpfSymbolicConfig.java`; `jpf-config.vm`; `ExtractionOutcome.java`; tests alongside `SpfSymbolicConfigSelector`.

- [x] **Step 1: Add `ExtractionOutcome.Kind.UNSUPPORTED_TERM`** with a factory + detail string; keep the `fromState` classifier unchanged for the existing kinds.
- [x] **Step 2: Write failing tests.**
  - A MUT whose body uses only sound string ops on a `String` param → screen result "string-symbolic enabled, admitted".
  - A MUT using `charAt`/`substring`/`compareTo` on a symbolic `String` → screen result "excluded" (maps to `UNSUPPORTED_TERM`).
  - A numeric-only MUT → unchanged (`symbolic.strings` off).
- [x] **Step 3: Implement the screen** by extending `SpfSymbolicConfigSelector`'s body inspection: detect any `String` operation → request `symbolic.strings=true` (add a flag to `SpfSymbolicConfig` threaded into `jpf-config.vm` line 34, replacing the commented `#symbolic.strings=true` with `symbolic.strings=${symbolicStrings}`); detect an unsound/unsupported op (`charAt`, `substring`, `compareTo` on a symbolic string) → exclude the assertion with `UNSUPPORTED_TERM` (recorded as an exclusion, matching how `TARGET_NOT_ENTERED` is handled — not a pipeline failure). Detection is direct-body-only and conservative (like the existing raw-bits detection); a transitively-reached unsupported op is the backstop's job (Task 6).
- [x] **Step 4: Run, expect PASS.**
- [x] **Step 5: Commit.** `feat: screen string MUTs and enable symbolic.strings conditionally`

### Task 6: jpf-symbc robustness — signal unsupported instead of crashing

Make an unsupported string op degrade to a clean per-assertion exclusion, never a whole-process (whole-variant) crash.

**Files (submodule):** `jpf-symbc/jpf-symbc/src/main/gov/nasa/jpf/symbc/bytecode/SymbolicStringHandler.java`; a jpf-symbc unit test mirroring the existing raw-bits test; Teralizer-side catch in the JPF execution boundary.

- [x] **Step 1: Write a failing jpf-symbc test** that a `compareTo` on a symbolic string produces a *typed unsupported signal*, not a bare `RuntimeException` that aborts the search.
- [x] **Step 2: Replace the `else`-throw** (`SymbolicStringHandler.java:309-311`) with a dedicated `UnsupportedSymbolicStringOpException` (a JPF-visible typed exception carrying the op name). **Do not `return null`** — a null next-instruction / silent fallthrough can create bogus symbolic states. The handler signals; it does not fabricate a state.
- [x] **Step 3: Catch at the Teralizer boundary** — in the JPF execution task/listener boundary, catch the typed unsupported signal for the current assertion and record `ExtractionOutcome.Kind.UNSUPPORTED_TERM` (exclude that assertion), leaving the rest of the run intact. Rebuild the submodule (`./gradlew build` builds jpf-symbc).
- [x] **Step 4: Run, expect PASS** (jpf-symbc test + a Teralizer harness test that an unsupported-op MUT is excluded, not crashed).
- [x] **Step 5: Commit.** `fix: signal unsupported symbolic string ops instead of crashing` (submodule commit + parent pointer bump).

### Task 7: Corpus verification (empirical, no new code)

**Files:** none (disposable DB).

- [ ] **Step 1: Build + unit tests.** `./gradlew build` fully green (string operator, planner, listener, screen, jpf-symbc tests).
- [ ] **Step 2: Run the sentinel subset** (`REPOREAPERS_DB=postgres_sentinel_verify REPOREAPERS_DATA_DIR=data/sentinel-verify REPOREAPERS_CONFIG_DIR=project-configs/sentinel scripts/run-reporeapers-rerun.sh --reset-db`); confirm sound string generalizations appear where string-parameter MUTs resolve and their generated tests pass on sampled strings **and** the concrete seed. Drop the scratch DB and data dir afterwards.
- [ ] **Step 3: Screen + ingestion check.** Confirm MUTs using `charAt`/`substring`/`compareTo` are recorded as `UNSUPPORTED_TERM` exclusions from the screen, and MUTs whose paths reach `indexOf`/`lastIndexOf` derived symbols are recorded as `UNSUPPORTED_TERM` exclusions from ingestion (not crashes, not silent free-variable generalizations).
- [ ] **Step 4: No-regression + totality.** Numeric/char/boolean generalization counts on the scoreboard census configs (`project-configs/jarvis-scoreboard/*-census.conf`, scratch DB) unchanged; measure the `ParameterType`/`ReturnType` first-reject drop. Confirm `concat`-bearing string specs are captured whole (no `oprlist` truncation) and string symbols survive the `Model→JSON→Model` round-trip. Acceptance: sound string generalizations added, unsound ops excluded, zero numeric regression, no process crashes, no silently-dropped string terms.

### Task 8 (optional): add `isEmpty` as a sound SPF op

- [x] **Done** — but modeled as the equality `s == ""` (not a dedicated `EMPTY` op): `SymbolicStringHandler.handleIsEmpty` treats `isEmpty()` fork-free as `receiver.equals("")`, reusing the `EQUALS` path end-to-end (symcrete select, `fold` `.equals("")`, planner `Arbitraries.of("")`), and removed it from the Task 5 screen's excluded set. Native `TestSymbolicStringIsEmpty` (both branches) + Teralizer `StringIsEmptyCaptureTest`. The pre-existing `EMPTY`/`NOTEMPTY` enum constants were not needed.

## Self-review

- **Spec coverage:** capture + ingestion totality = Tasks 1–2; rendering = Task 3; generation = Task 4; symbolic-temporary recovery = Task 4b; screen + config + typed exclusion = Task 5; jpf-symbc robustness = Task 6; verification = Task 7; optional slice growth = Task 8. Task 1 de-risks 2–4.
- **Co-dependency honored:** Tasks 2–4 form one shippable unit (do not enable capability without rendering + a satisfying planner). Task 5's screen gates what reaches SPF; Task 6 backstops transitive gaps.
- **Soundness:** unsound/unsupported ops are excluded structurally (Task 5) and cannot crash (Task 6); generated strings are non-null and clause-satisfying; the full-predicate filter enforces the rest. The execution gate is defense-in-depth for rendering bugs only.
- **Deferred / rejected:** `charAt`/`substring` with a provably-bounded index (needs a captured length guard); `matches`/regex, `regionMatches`; a multi-path capture redesign for a sound SIOOBE fork (a separate large spec). A wholesale `ModelVisitor`-hook-coverage refactor was **considered and rejected** after grounding: the render path is already strict (`ModelToJavaTransformer extends ModelFolder`, a missing node kind is a compile error) and the only `ModelVisitor` subclasses (`VariableNameCollector`, `ModelStatisticsExtractor`) are collectors that are partial by intent. The broader typed-ingestion hardening (SPF→Model / JSON totality) stays in the architecture audit (A-5/A-2).
