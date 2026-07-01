---
title: Partial Sound String Support
type: plan
status: draft
created: 2026-06-30
parent: 2026-06-26-teralizer-overview
---

# Partial Sound String Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generalize tests over `String` parameters and returns for the subset of string operations SPF handles *soundly*, excluding the unsound/unsupported operations structurally — never by luck.

**Architecture:** SPF's `symbolic.strings` backend and Teralizer's model-level string plumbing already exist (`SpfToModelTransformer` handles `StringConstraint`/`DerivedStringExpression`/`StringSymbolic`; `TypeDomain.STRING` and `defaultRecipe`'s `Arbitraries.strings()` are present). The remaining work is five co-dependent seams that must land together: (1) capture the string path condition + string return in the listener; (2) render string operators to Java in `ModelToJavaTransformer.fold`; (3) a `StringDomainPlanner` that builds a *satisfying* bounded, non-null arbitrary from the captured clauses; (4) a structural pre-screen that enables `symbolic.strings` and excludes unsound/unsupported ops via a typed `ExtractionOutcome`; (5) jpf-symbc robustness so an unsupported op degrades to a clean per-assertion exclusion, not a process crash. Soundness comes from the structural screen + the full-predicate residual filter, **not** from the random-sampling execution gate.

**Tech stack:** Java 8, SPF/jpf-symbc (`symbolic.strings`, git submodule), jqwik `Arbitraries`, Spoon AST, Velocity template `jpf-config.vm`.

---

## Soundness invariants (non-negotiable)

1. **Structural screen is the guarantee; execution is only a backstop.** `NonPassingTestFilter` is a bounded random sampler (N tries + the concrete original as `edgeCases=FIRST`, seed 0). SPF's known holes live on thin slices (`i == s.length()`, `s == null`) that random strings essentially never hit, so the gate cannot prove soundness for them. Only emit specs whose every string op is in the sound set and whose string domain the captured path condition actually constrained.
2. **Emit only over the constrained domain.** Generated strings are **non-null** in v1 (SPF's `IFNULL` falls through → the null path is never captured; restricting to non-null records that the spec's validity domain excludes null — a sound restriction, not a hack).
3. **Co-dependency.** Making `String` a *generated* parameter without rendering its operators forces exclusions (`ConstraintClauses.from` rethrows `NonGeneralizableExpressionException` for a clause that constrains a generated parameter). Seams (1)–(3) must ship together or not at all.
4. **No regression** of the ~250 numeric/char/boolean sound generalizations.

## Background (verified at source)

- `src/main/java/teralizer/jpf/TestGeneralizationListener.java:147` — `captureInvocation` reads only `pathCondition.header` (numeric `Constraint`) and **drops `pathCondition.spc`** (the `StringPathCondition`). `:171` — `spfOutput = returnInstruction.getReturnAttr(..., Expression.class)` where `Expression` is `gov.nasa.jpf.symbc.numeric.Expression`, so a string return attribute is not captured. `:238-241` — concrete `String` reference capture already yields a `StringValue`.
- `src/main/java/teralizer/transformer/SpfToModelTransformer.java:19-45` — `transform(StringPathCondition)` and `transform(StringConstraint)` overloads already exist and produce `Expression` model nodes.
- `src/main/java/teralizer/transformer/ModelToJavaTransformer.java:226-228` — `fold(SymbolicStringFunction)` emits **free-function** syntax `name(args)` (wrong for instance methods like `s.length()`). `:231-298` — `fold(Operation)`: all string operators fall to the `default` (`:295-297`) → `NonGeneralizableExpressionException`. `fold(VariableString)` (`:198-203`) and `fold(ConstantString)` (`:176-179`) already render correctly.
- `src/main/java/teralizer/domain/Operator.java:42-67` — every string operator already exists in the enum.
- `src/main/java/teralizer/jqwik/planning/DomainPlanners.java:8-9` — `REGISTERED` = `NumericDomainPlanner`, `BooleanDomainPlanner`. `TypeCapability` (`src/main/java/teralizer/util/TypeCapability.java:34-45`) derives input/return support from `REGISTERED.supports(domain)`, and `TypeDomain.from` (`TypeDomain.java:37-39`) already maps `String`/`java.lang.String` → `STRING`. **Registering a `StringDomainPlanner` is the single structural unlock** for the `ParameterTypeFilter`, `ReturnTypeFilter`, the two `TestGeneralizationTask` `removeIf` strips, and the planner.
- `src/main/java/teralizer/jqwik/planning/InputGenerationPlanner.java:36-44,56-67` — the factory applies the **full** predicate (`ConstraintClauses.from` renders every conjunct) as the residual filter regardless of what a planner consumed. So string clauses are enforced by the filter **iff** `fold` can render them.
- `jpf-symbc/jpf-symbc/src/main/gov/nasa/jpf/symbc/bytecode/SymbolicStringHandler.java:142-313` — SPF already handles `concat`, `equals`, `equalsIgnoreCase`, `startsWith`, `endsWith`, `contains`, `length`, `indexOf`, `lastIndexOf`, `charAt`, `substring`, `trim`, `replace`, `valueOf`, `parse*`, `toString`. The `else` at `:309-311` **throws `RuntimeException`** (crashes the whole JPF process) for anything else — including `compareTo` and `isEmpty`. `handleCharAt` (`:320-355`, `sf.push(0,false)` at `:350`) has **no `PCChoiceGenerator` / no length constraint / no SIOOBE fork** → the missing-bounds unsoundness.
- `src/main/java/teralizer/jpf/ExtractionOutcome.java:10-17` — `Kind` currently has only `EXTRACTED`, `TARGET_NOT_ENTERED`, `TARGET_NOT_EXITED`. A new kind is needed for the string screen's typed exclusion.
- `src/main/resources/templates/jpf-config.vm:34` — `#symbolic.strings=true` is commented out; `src/main/java/teralizer/spoon/analysis/SpfSymbolicConfigSelector.java` already inspects the MUT body (direct-body-only) to select a `SpfSymbolicConfig` profile — the natural home for the string screen + the `symbolic.strings` toggle.

## Sound-set decision (v1)

- **Include:** `equals`, `equalsIgnoreCase`, `startsWith`, `endsWith`, `contains`, `length`, `indexOf`, `concat`, and computed `String` returns (`DerivedStringExpression`) — all handled by SPF and total along the concrete path.
- **Exclude (typed):** `compareTo`, `isEmpty` (SPF crash); `charAt`, `substring` (SPF omits the SIOOBE fork → unsound) — excluded unconditionally in v1; the "index provably bounded by the captured PC" refinement is deferred.
- **Deferred:** `matches`/regex, `regionMatches`, `replace`/`trim` as generalized ops, and `isEmpty` as a sound addition (Task 8, optional).

## File map

- Modify: `src/main/java/teralizer/jpf/TestGeneralizationListener.java` — capture `spc` + string return attr.
- Modify: `src/main/java/teralizer/transformer/ModelToJavaTransformer.java` — string-operator `fold` arms + fix `SymbolicStringFunction` instance-call rendering.
- Create: `src/main/java/teralizer/jqwik/planning/StringDomainPlanner.java` and `StringClauseInterpretation.java`; modify `DomainPlanners.java`.
- Modify: `src/main/java/teralizer/spoon/analysis/SpfSymbolicConfigSelector.java` (or a new `StringSupportScreen`); `src/main/java/teralizer/util/SpfSymbolicConfig.java`; `src/main/resources/templates/jpf-config.vm`; `src/main/java/teralizer/jpf/ExtractionOutcome.java`.
- Modify (submodule): `jpf-symbc/jpf-symbc/src/main/gov/nasa/jpf/symbc/bytecode/SymbolicStringHandler.java`.
- Tests: `src/test/java/teralizer/transformer/ModelToJavaTransformer*Test.java`, `src/test/java/teralizer/jpf/TestGeneralizationListenerCaptureTest.java` + `JpfListenerHarness.java`, new `src/test/java/teralizer/jqwik/planning/StringDomainPlannerTest.java`, `src/test/java/teralizer/util/TypeCapabilityTest.java`.

## Tasks

### Task 1: Spike — capture `spc` and observe the model node shapes (throwaway)

Discover the exact `Model` node produced for each slice operator before writing renderers/interpreters. This de-risks Tasks 2–4.

**Files:** temporary edits + a scratch test in `src/test/java/teralizer/jpf/`.

- [ ] **Step 1:** In a scratch harness test (pattern: `TestGeneralizationListenerSymbolicTest.java`), run a target whose MUT branches on `s.equals("foo")`, one on `s.length() > 3`, one on `s.startsWith("a")`, one on `s.indexOf('x')`, and one returning `a.concat(b)`, with `symbolic.strings=true` and a symbolic `String` parameter.
- [ ] **Step 2:** Temporarily transform `pathCondition.spc` in `captureInvocation` and log the resulting `teralizer.domain.Expression` tree (node class + `Operator`) for each.
- [ ] **Step 3:** Record, in this task's notes, the node shape per op (e.g. is `s.equals("foo")` an `Operation(EQUALS, VariableString, ConstantString)`? is `s.length()` a `SymbolicStringFunction("length", [VariableString])` or a derived integer expression?). Acceptance: Tasks 3 and 4 reference concrete node shapes, not guesses. Revert the scratch edits.
- [ ] **Step 4: Check ingestion totality.** In the same scratch run, confirm `a.concat(b)` is captured **whole** — `SpfToModelTransformer` has a TODO that silently drops `DerivedStringExpression.oprlist` (audit A-5); verify the captured model reflects the full concat, not a truncated term. Then round-trip the captured string spec through `Model→JSON→Model` (`ModelToJsonTransformer`/`JsonToModelTransformer`) and confirm string nodes survive (audit A-2 found a field-name mismatch in that path). Any gap becomes a fix step in Task 2 (capture) or a local `SpfToModelTransformer`/JSON fix — never a silent drop.

### Task 2: Listener — capture the string path condition + string return

**Files:** `TestGeneralizationListener.java`; `src/test/java/teralizer/jpf/TestGeneralizationListenerCaptureTest.java`; `JpfListenerHarness.java`.

- [ ] **Step 1: Write failing tests.** A MUT branching on `s.equals("foo")` → the captured `modelInput` contains the string-equality clause (not just numeric). A MUT returning a computed `String` → the captured `modelOutput` is the string expression, not null.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Capture `spc`.** In `captureInvocation`, after computing the numeric `modelInput`, transform `pathCondition.spc` via `SpfToModelTransformer.transform(StringPathCondition)`; if non-null, combine: `modelInput = numeric == null ? stringModel : new Operation(Operator.AND, numeric, stringModel)`. (Confirm the exact `Operation` constructor / model-conjunction idiom used elsewhere.)
- [ ] **Step 4: Capture string return.** When the return type is `String`, also read the string return attribute (the `StringExpression`/`StringSymbolic` attr, not only `Expression.class`) and transform it to the `modelOutput`. Guard by concrete return type so numeric capture is unchanged.
- [ ] **Step 5: Run, expect PASS**, and re-run `TestGeneralizationListenerSymbolicTest`/`...CaptureTest` to confirm numeric capture is unaffected.
- [ ] **Step 6: Commit.** `feat: capture string path condition and string return in listener`

### Task 3: Render string operators to Java in `fold`

**Files:** `ModelToJavaTransformer.java`; `src/test/java/teralizer/transformer/ModelToJavaTransformerValueTest.java` (or a new `...StringOperatorTest.java`).

- [ ] **Step 1: Write failing unit tests** asserting the rendered Java for each slice operator, matching Task 1's observed node shapes. Expected renderings:

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

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Add the case arms** to `fold(Operation, left, right)` before the `default` branch (`ModelToJavaTransformer.java:295`). If Task 1 shows `length`/`indexOf`/`concat` arrive as `SymbolicStringFunction`, fix `fold(SymbolicStringFunction)` (`:226-228`) to emit **instance-call** Java (`args.get(0) + "." + method + "(" + rest + ")"`), mapping the SPF function name to the Java method; keep numeric `SymbolicIntegerFunction`/`SymbolicRealFunction` unchanged.
- [ ] **Step 4: Run, expect PASS**, and run `ModelToJavaTransformerNonGeneralizableTest` to confirm the *excluded* operators (e.g. `MATCHES`) still raise `NonGeneralizableExpressionException`.
- [ ] **Step 5: Commit.** `feat: render sound string operators to Java`

### Task 4: `StringDomainPlanner` — satisfying, bounded, non-null arbitrary

Build an arbitrary that *satisfies* the captured clauses (so generation is practical), with the full-predicate filter as the soundness backstop.

**Files:** create `StringDomainPlanner.java`, `StringClauseInterpretation.java`; modify `DomainPlanners.java`; tests `StringDomainPlannerTest.java`, `TypeCapabilityTest.java`.

- [ ] **Step 1: Write failing tests.**
  - `TypeCapability.supportsGeneratedInput("java.lang.String")` and `supportsReturnValue("java.lang.String")` are `true`.
  - `InputGenerationPlanner.planParameter` for a `String` parameter uses `StringDomainPlanner` (not `defaultRecipe`).
  - Equality clause `s.equals("foo")` → recipe `return net.jqwik.api.Arbitraries.of("foo")` (not an unbounded filter).
  - `startsWith("ab")` → an arbitrary whose values all start with `"ab"`.
  - No string clause → bounded non-null ASCII: `Arbitraries.strings().ascii().ofMaxLength(N)` with a sensible `N`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement `StringClauseInterpretation`** (mirror `NumericClauseInterpretation`): from the input model, extract per-parameter string constraints — equality target(s), required prefix/suffix/substring, and length bounds — plus consumed clause ids. Contradictory equalities ⇒ empty arbitrary.
- [ ] **Step 4: Implement `StringDomainPlanner`** (`supports(TypeDomain.STRING)`; mirror `NumericDomainPlanner.plan`):
  - equality present → `Arbitraries.of(<value>)`;
  - else compose from `Arbitraries.strings().ascii()` with `.ofMinLength/.ofMaxLength` for length bounds and a prefix/suffix/embed builder (`.map`) for `startsWith`/`endsWith`/`contains`;
  - default → `Arbitraries.strings().ascii().ofMaxLength(N)`;
  - **never** `null`; inject the concrete original string as the first value (match the `edgeCases=FIRST` convention). Return the `ParameterGenerationPlan` with the consumed clause ids; the residual full-predicate filter enforces anything not captured by the base arbitrary.
- [ ] **Step 5: Register** `new StringDomainPlanner()` in `DomainPlanners.REGISTERED`.
- [ ] **Step 6: Run, expect PASS**, plus a test that a `String`-only-parameter MUT is no longer stripped by `TestGeneralizationTask`'s `removeIf` and no longer rejected by `ParameterTypeFilter`/`ReturnTypeFilter`.
- [ ] **Step 7: Commit.** `feat: add StringDomainPlanner with clause-satisfying arbitraries`

### Task 4b: Recover symbolic temporaries via Model traversal (replace the regex-scrape)

`TestGeneralizationTask.java:283-300` recovers SPF's symbolic temporaries by regex-scraping the serialized JSON spec for `INT_`/`REAL_` names — string symbols are missed, so their generated inputs would never be planned. Replace the regex with a typed `Model` traversal (the render path is already `ModelFolder`-strict, so this is the one real string-plumbing cleanup, not a visitor-hierarchy change).

**Files:** `TestGeneralizationTask.java`; new `src/main/java/teralizer/transformer/VariableDescriptorCollector.java`; a test alongside the generation tests.

- [ ] **Step 1: Gate on the spike.** Only needed if Task 1 shows string PC/return introduce symbolic temporaries not already in `testedMethodParameters`. If strings only ever appear as named formal parameters, record that and skip (no regex change needed).
- [ ] **Step 2: Write a failing test** that a spec containing a string symbol yields that symbol in `allParameters` (today the `INT_`/`REAL_` regex omits it).
- [ ] **Step 3: Replace the regex-scrape** (`:283-300`) with a typed `Model` walk. `VariableNameCollector` returns only names, so add a small `VariableDescriptorCollector` (a `ModelVisitor` overriding the three `Variable*` `preVisit` hooks) that records name → `TypeDomain` (`VariableInteger`→INTEGER, `VariableReal`→REAL, `VariableString`→STRING). Walk `inputModel` + `outputModel`, take the collected symbols minus the formal `testedMethodParameters`, and build each `temporaryParameter` with the domain-derived type. Delete the `Pattern`/`Matcher` code.
- [ ] **Step 4: Run, expect PASS**; confirm numeric temporaries are still recovered (no regression to existing generalizations).
- [ ] **Step 5: Commit.** `refactor: recover symbolic temporaries via Model traversal, not JSON regex`

### Task 5: Structural pre-screen + conditional `symbolic.strings`

Enable string symbolization only when needed and exclude unsound/unsupported ops before running SPF.

**Files:** `SpfSymbolicConfigSelector.java` (or new `StringSupportScreen.java`); `SpfSymbolicConfig.java`; `jpf-config.vm`; `ExtractionOutcome.java`; tests alongside `SpfSymbolicConfigSelector`.

- [ ] **Step 1: Add `ExtractionOutcome.Kind.UNSUPPORTED_TERM`** with a factory + detail string; keep the `fromState` classifier unchanged for the existing kinds.
- [ ] **Step 2: Write failing tests.**
  - A MUT whose body uses only sound string ops on a `String` param → screen result "string-symbolic enabled, admitted".
  - A MUT using `charAt`/`substring`/`compareTo`/`isEmpty` on a symbolic `String` → screen result "excluded" (maps to `UNSUPPORTED_TERM`).
  - A numeric-only MUT → unchanged (`symbolic.strings` off).
- [ ] **Step 3: Implement the screen** by extending `SpfSymbolicConfigSelector`'s body inspection: detect any `String` operation → request `symbolic.strings=true` (add a flag to `SpfSymbolicConfig` threaded into `jpf-config.vm` line 34, replacing the commented `#symbolic.strings=true` with `symbolic.strings=${symbolicStrings}`); detect an unsound/unsupported op (`charAt`, `substring`, `compareTo`, `isEmpty` on a symbolic string) → exclude the assertion with `UNSUPPORTED_TERM` (recorded as an exclusion, matching how `TARGET_NOT_ENTERED` is handled — not a pipeline failure). Detection is direct-body-only and conservative (like the existing raw-bits detection); a transitively-reached unsupported op is the backstop's job (Task 6).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit.** `feat: screen string MUTs and enable symbolic.strings conditionally`

### Task 6: jpf-symbc robustness — signal unsupported instead of crashing

Make an unsupported string op degrade to a clean per-assertion exclusion, never a whole-process (whole-variant) crash.

**Files (submodule):** `jpf-symbc/jpf-symbc/src/main/gov/nasa/jpf/symbc/bytecode/SymbolicStringHandler.java`; a jpf-symbc unit test mirroring the existing raw-bits test; Teralizer-side catch in the JPF execution boundary.

- [ ] **Step 1: Write a failing jpf-symbc test** that a `compareTo` on a symbolic string produces a *typed unsupported signal*, not a bare `RuntimeException` that aborts the search.
- [ ] **Step 2: Replace the `else`-throw** (`SymbolicStringHandler.java:309-311`) with a dedicated `UnsupportedSymbolicStringOpException` (a JPF-visible typed exception carrying the op name). **Do not `return null`** — a null next-instruction / silent fallthrough can create bogus symbolic states. The handler signals; it does not fabricate a state.
- [ ] **Step 3: Catch at the Teralizer boundary** — in the JPF execution task/listener boundary, catch the typed unsupported signal for the current assertion and record `ExtractionOutcome.Kind.UNSUPPORTED_TERM` (exclude that assertion), leaving the rest of the run intact. Rebuild the submodule (`./gradlew build` builds jpf-symbc).
- [ ] **Step 4: Run, expect PASS** (jpf-symbc test + a Teralizer harness test that an unsupported-op MUT is excluded, not crashed).
- [ ] **Step 5: Commit.** `fix: signal unsupported symbolic string ops instead of crashing` (submodule commit + parent pointer bump).

### Task 7: Corpus verification (empirical, no new code)

**Files:** none (disposable DB).

- [ ] **Step 1: Build + unit tests.** `./gradlew build` fully green (string operator, planner, listener, screen, jpf-symbc tests).
- [ ] **Step 2: Enable on the disposable spike DB** (`postgres_reporeapers_rerun` or fresh disposable) for projects with string-parameter MUTs; confirm new sound string generalizations appear and their generated tests pass on sampled strings **and** the concrete seed.
- [ ] **Step 3: Screen check.** Confirm MUTs using `charAt`/`substring`/`compareTo`/`isEmpty` are recorded as `UNSUPPORTED_TERM` exclusions (not crashes, not silent generalizations).
- [ ] **Step 4: No-regression + totality.** Numeric/char/boolean generalization counts on `postgres_jarvis_census` unchanged; measure the `ParameterType`/`ReturnType` first-reject drop. Confirm `concat`-bearing string specs are captured whole (no `oprlist` truncation) and string symbols survive the `Model→JSON→Model` round-trip. Acceptance: sound string generalizations added, unsound ops excluded, zero numeric regression, no process crashes, no silently-dropped string terms.

### Task 8 (optional): add `isEmpty` as a sound SPF op

- [ ] Add an `isEmpty` handler to `SymbolicStringHandler` modeled as `length() == 0` (fork-free, sound); add `EMPTY`/`NOTEMPTY` arms to `fold(Operation)` (`(l.isEmpty())` / `(!l.isEmpty())`); move `isEmpty` from the excluded set to the sound set in the Task 5 screen; add tests. Commit `feat: support isEmpty as a sound symbolic string op`.

## Self-review

- **Spec coverage:** capture + ingestion totality = Tasks 1–2; rendering = Task 3; generation = Task 4; symbolic-temporary recovery = Task 4b; screen + config + typed exclusion = Task 5; jpf-symbc robustness = Task 6; verification = Task 7; optional slice growth = Task 8. Task 1 de-risks 2–4.
- **Co-dependency honored:** Tasks 2–4 form one shippable unit (do not enable capability without rendering + a satisfying planner). Task 5's screen gates what reaches SPF; Task 6 backstops transitive gaps.
- **Soundness:** unsound/unsupported ops are excluded structurally (Task 5) and cannot crash (Task 6); generated strings are non-null and clause-satisfying; the full-predicate filter enforces the rest. The execution gate is defense-in-depth for rendering bugs only.
- **Deferred / rejected:** `charAt`/`substring` with a provably-bounded index (needs a captured length guard); `matches`/regex, `regionMatches`; a multi-path capture redesign for a sound SIOOBE fork (a separate large spec). A wholesale `ModelVisitor`-hook-coverage refactor was **considered and rejected** after grounding: the render path is already strict (`ModelToJavaTransformer extends ModelFolder`, a missing node kind is a compile error) and the only `ModelVisitor` subclasses (`VariableNameCollector`, `ModelStatisticsExtractor`) are collectors that are partial by intent. The broader typed-ingestion hardening (SPF→Model / JSON totality) stays in the architecture audit (A-5/A-2).
