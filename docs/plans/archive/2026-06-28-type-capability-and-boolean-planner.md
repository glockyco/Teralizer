---
title: P-A2/C-2 — Type-Capability Single Source + Boolean Planner
type: plan
status: implemented
created: 2026-06-28
archived: 2026-06-28
parent: 2026-06-28-clause-driven-input-generation
---

# Type-Capability Single Source (A-2) + BooleanDomainPlanner (C-2)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) tracking.

## Implementation outcome (as built)

Shipped as designed, behavior-preserving. **C-2:** `NumericClauseInterpreter` gated to numeric domains (INTEGER/REAL/CHAR); `BooleanDomainPlanner` interprets the int-encoded boolean equality/inequality (`b==1`/`b!=0`→`just(true)`, `b==0`/`b!=1`→`just(false)`, conflict→`of()`, unconstrained→`of(true,false)`) and reports consumed ids; shared `DomainPlanners.REGISTERED`. **A-2:** registry-backed `TypeCapability` facade (`supportsGeneratedInput` vs distinct `supportsReturnValue`); all six `SUPPORTED_TYPES` consumers rerouted; `SUPPORTED_TYPES` + `ConfigurationSupportedTypesTest` removed. Supported input domains unchanged {INTEGER, REAL, CHAR, BOOLEAN}; zero `SUPPORTED_TYPES` references remain; full `./gradlew build` green.

Commits: `45e8e3da` numeric-domain gate · `d3f9a0e0` BooleanDomainPlanner + registry · `8d1428d0` TypeCapability facade · `238c61e7` `b != 1` test · `6eeb9638` reroute + retire.

**Goal:** Make "a type is generatable" derive from the `DomainPlanner` registry instead of a hand-maintained `Configuration.SUPPORTED_TYPES` string list (A-2), and add a `BooleanDomainPlanner` so booleans get real clause-driven generation instead of falling through to `defaultRecipe` (C-2). C-2 lands first because A-2's "supported ⇔ a registered planner claims the domain" is behavior-preserving only once a boolean planner exists.

**Why coupled:** `boolean`/`Boolean` are already in `SUPPORTED_TYPES`, so the gate already admits booleans — they just hit `defaultRecipe` → `Arbitraries.of(true,false)` (no planner claims `BOOLEAN`). If A-2 switched the gate to "has-a-planner" without C-2, boolean would silently drop from the supported set. Together, the supported domains stay exactly `{INTEGER, REAL, CHAR, BOOLEAN}` = today's `SUPPORTED_TYPES`.

**Tech Stack:** Java 8, jqwik, JUnit/jqwik `@Example`, `./gradlew test`/`build`.

## Grounded facts

- `TypeDomain.from(String)` maps both primitive and qualified-wrapper names; `SUPPORTED_TYPES` (Configuration.java:94-103) is exactly `{byte/Byte, short/Short, int/Integer, long/Long, float/Float, double/Double, char/Character, boolean/Boolean}` — i.e. the strings `TypeDomain.from` sends to `{INTEGER, REAL, CHAR, BOOLEAN}`. A facade `domainOf(s) ∈ supportedDomains` reproduces `SUPPORTED_TYPES.contains(s)` for every string the call sites pass (simple primitives, qualified wrappers; simple wrappers like "Integer" → `OBJECT` → unsupported, matching the list).
- Booleans are **int-encoded**: a boolean param `b` is a `VariableInteger`; `ModelToJavaTransformer.postVisit(VariableInteger)` renders it `(_p_.b ? 1 : 0)` when its declared type is `boolean`/`java.lang.Boolean`. So `b == true` appears as `Operation(VariableInteger("b"), EQ, ConstantInteger(1))`.
- `NumericClauseInterpreter` dispatches by **operand type**, so it currently builds a stray (unused) `IntegerConstraints` for boolean params. The clean fix: gate it to numeric **domains** (INTEGER/REAL/CHAR — keeps char, drops boolean).
- `SUPPORTED_TYPES` consumers (6): `ParameterTypeFilter:46` (input gate), `ReturnTypeFilter:26` (output/return gate — distinct concern), `JpfInstrumentationTask:440` (`getSimpleName()` → symbolic-marking), `TestGeneralizationTask:261,321` (`removeIf` drops unsupported params), `GeneralizableInput:60,93` (input derivation). Plus `ConfigurationSupportedTypesTest`.

## Design

1. **`BooleanDomainPlanner implements DomainPlanner`** (`supports(BOOLEAN)`), registered in the planner registry. For its parameter it scans `context.getClauses()` for the int-encoded boolean equality shapes and renders:
   - `b == 1` / `b != 0` → `return net.jqwik.api.Arbitraries.just(true)`, clause consumed.
   - `b == 0` / `b != 1` → `return net.jqwik.api.Arbitraries.just(false)`, clause consumed.
   - first-value present, no constraint → `new FirstValueArbitrary<Boolean>((boolean) (<first>), Arbitraries.of(true, false))`.
   - otherwise → `return net.jqwik.api.Arbitraries.of(true, false)` (byte-identical to today's `defaultRecipe` for the unconstrained case).
   Consumed ids are reported on the `ParameterGenerationPlan` exactly as the numeric planner does.
2. **Gate `NumericClauseInterpreter`** to numeric-domain parameters (`TypeDomain.from(param.type) ∈ {INTEGER, REAL, CHAR}`), so it no longer builds stray constraints for boolean params. (Behavior-preserving: that interpretation was unused.)
3. **`TypeCapability` facade** — the single capability source, with name canonicalization (accepts simple + qualified):
   - `supportsGeneratedInput(String type)` = `registeredInputDomains.contains(TypeDomain.from(type))`, where `registeredInputDomains` is derived from the `DomainPlanner` registry (`TypeDomain.values()` filtered by `anyPlanner.supports(d)`). This is the principled A-2 source for the **input** consumers.
   - `supportsReturnValue(String type)` = a separate query (the return type usable as the symbolic output oracle), initially the same domain set, kept distinct so input vs output capability can diverge later without re-tangling.
   - A single registry constant (e.g. `DomainPlanners.REGISTERED`) shared by `InputGenerationPlanner` and `TypeCapability`.
4. **Route consumers:** input gates (`ParameterTypeFilter`, `TestGeneralizationTask` ×2 `removeIf`, `GeneralizableInput` ×2, `JpfInstrumentationTask` symbolic-marking) → `supportsGeneratedInput`; `ReturnTypeFilter` → `supportsReturnValue`. Remove `Configuration.SUPPORTED_TYPES`; convert `ConfigurationSupportedTypesTest` into a `TypeCapability` test.

**Behavior preservation:** supported input domains stay `{INTEGER, REAL, CHAR, BOOLEAN}`; the facade reproduces `SUPPORTED_TYPES.contains(...)` for all call-site strings; unconstrained boolean recipe stays `of(true,false)`. The full existing filter/generation/config suites are the oracle.

## File structure

- Create: `src/main/java/teralizer/jqwik/planning/BooleanDomainPlanner.java`, `src/main/java/teralizer/jqwik/planning/DomainPlanners.java` (shared registry), `src/main/java/teralizer/util/TypeCapability.java`.
- Modify: `NumericClauseInterpreter.java` (domain gate), `InputGenerationPlanner.java` (use shared registry), `ParameterTypeFilter.java`, `ReturnTypeFilter.java`, `JpfInstrumentationTask.java`, `TestGeneralizationTask.java`, `GeneralizableInput.java`, `Configuration.java` (remove `SUPPORTED_TYPES`).
- Tests: `BooleanDomainPlannerTest` (new), `TypeCapabilityTest` (replaces `ConfigurationSupportedTypesTest`), plus the existing filter/generation suites as the regression oracle.

---

## Phase 1 — BooleanDomainPlanner (C-2)

### Task 1: Gate the numeric interpreter to numeric domains
- [ ] **Step 1 (red):** Add a `NumericDomainPlannerClauseTest` case asserting a `boolean` parameter with a `b == 1`-shaped clause yields an empty numeric interpretation (no `IntegerConstraints`, no consumed ids) — fails today (operand-type dispatch builds one).
- [ ] **Step 2 (green):** In `NumericClauseInterpreter.interpret`, only build/record for a parameter whose `TypeDomain.from(type) ∈ {INTEGER, REAL, CHAR}`. Keep operand-type dispatch within those.
- [ ] **Step 3:** Run `teralizer.jqwik.planning.*` + `teralizer.spoon.generalization.*` + `TestGeneralizationTaskTest` — all green (numeric/char unchanged). Commit.

### Task 2: BooleanDomainPlanner + registry
- [ ] **Step 1 (red):** `BooleanDomainPlannerTest`: `b == 1` → `just(true)` + consumed {0}; `b == 0` → `just(false)` + consumed {0}; `b != 0` → `just(true)`; unconstrained → `of(true, false)` + empty consumed.
- [ ] **Step 2 (green):** Implement `BooleanDomainPlanner` (`supports(BOOLEAN)`; clause scan for the int-encoded equality/inequality shapes against `ConstantInteger` 0/1; render as above; report consumed ids). Add `DomainPlanners.REGISTERED = [new NumericDomainPlanner(), new BooleanDomainPlanner()]`; point `InputGenerationPlanner` at it.
- [ ] **Step 3:** Run the new test + `InputGenerationPlannerTest` (a boolean param still renders `of(true,false)` when unconstrained — behavior-preserving) + `TestGeneralizationTaskTest`. Commit.

## Phase 2 — Type-capability single source (A-2)

### Task 3: TypeCapability facade
- [ ] **Step 1 (red):** `TypeCapabilityTest`: `supportsGeneratedInput` true for each of `{byte, java.lang.Byte, …, char, java.lang.Character, boolean, java.lang.Boolean}` and false for `String`/`int[]`/arbitrary objects/simple wrapper `"Integer"`; equals the legacy `SUPPORTED_TYPES` membership for every such string; `supportsReturnValue` matches the same set initially.
- [ ] **Step 2 (green):** Implement `TypeCapability` (canonicalization + `supportsGeneratedInput` registry-backed via `DomainPlanners.REGISTERED` + `TypeDomain`; `supportsReturnValue` separate).
- [ ] **Step 3:** Run `TypeCapabilityTest`. Commit.

### Task 4: Route consumers, retire SUPPORTED_TYPES
- [ ] **Step 1:** Replace the 6 consumers with the facade (input gates → `supportsGeneratedInput`; `ReturnTypeFilter` → `supportsReturnValue`; preserve `JpfInstrumentationTask`'s `getSimpleName()` argument). Remove `Configuration.SUPPORTED_TYPES`. Delete/replace `ConfigurationSupportedTypesTest` with `TypeCapabilityTest`.
- [ ] **Step 2:** `./gradlew build` — all filter/generation/config suites green (the gate admits/rejects/symbolizes exactly as before). Commit.

## Acceptance criteria
- Booleans generate via `BooleanDomainPlanner`: `just(true/false)` for `b==true/false` constraints (consumed ids reported), `of(true,false)` unconstrained (byte-identical to the old default).
- `NumericClauseInterpreter` no longer builds constraints for non-numeric-domain params; all numeric/char recipes byte-identical.
- `Configuration.SUPPORTED_TYPES` removed; every gate routes through `TypeCapability`; input vs return capability are distinct queries; full `./gradlew build` green.
- Supported input domains remain `{INTEGER, REAL, CHAR, BOOLEAN}` — no test newly accepted/rejected/symbolized differently.

## Follow-ups (not this plan)
- A-3 fail-loud visitor seam; A-4 retire `VariableConstraintExtractor` (unify its metrics counting onto the interpreter); P2 SPF characterization → P4 strings → P5 arrays/objects; C-3 residual-only filtering.
