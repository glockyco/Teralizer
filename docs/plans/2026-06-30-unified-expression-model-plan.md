---
title: Unified Expression Model — Implementation Plan
type: plan
status: active
created: 2026-06-30
parent: 2026-06-30-unified-expression-model
---

# Unified Expression Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Execution scope: this document authorizes step-level execution of Phase 1 only. Phases 2–4 are committed scope but MUST each be expanded into their own step-level plan (with a fresh code-read) and checkpointed with the user before any code is written for them.**

**Goal:** Replace the type-fragmented `teralizer.domain` expression zoo with a uniform model — one `Invocation` node for method/function calls of any arity, a `Not` wrapper, typed `Variable`/`Constant`, `Operation` for true operators only — gated by a single capability registry.

**Architecture:** Phased migration (string → functions → leaves → cleanup). Each phase is independently shippable and leaves the full suite + native SPF tests green. The `ModelFolder<T>` is compile-strict (one abstract hook per node kind), so adding/removing a node kind is compiler-guided: every renderer must handle it or the build fails.

**Tech stack:** Java 8, Spoon AST, SPF/jpf-symbc (`SpfToModelTransformer`), `ModelVisitor`/`ModelFolder` traversal, jqwik planners, Gson JSON adapters. Design: `2026-06-30-unified-expression-model`.

**Regression net (every phase):** `./gradlew test` green + native jpf-symbc string tests green (`TestSymbolicString{Symcrete,IsEmpty,EqualsIgnoreCase}`, run by temporarily neutralizing the `settings.gradle:15` test-disable toggle, then reverting). From Phase 2 on, the ~250 numeric/char/boolean generalizations must still **compile and pass** (behavioral guardrail; golden snapshots are review prompts, not byte-parity gates).

**Plan granularity (deliberate):** Phase 1 is specified at **step level** (TDD-ready) and is executable now. Phases 2–4 are specified at **milestone level** — goal, exact file map, ordered steps, and acceptance — but not line-by-line code, because each phase edits the structure the previous phase creates; writing exact Phase-3 edits now would be speculative against code that does not exist yet. **Each of Phases 2–4 is expanded to step-level detail (with a fresh code-read) immediately before it is executed**, following the pattern Phase 1 establishes. Treat Phases 2–4 as committed scope + acceptance, re-planned per phase.

---

## File map

**New:**
- `src/main/java/teralizer/domain/Invocation.java` — n-ary call node (`receiver?`, `qualifier?`, `method`, `args[]`).
- `src/main/java/teralizer/domain/Not.java` — negation wrapper (`operand`).
- `src/main/java/teralizer/jqwik/planning/MethodCapability.java` + `MethodCapabilities.java` — the capability registry (`spfCollectable`/`inputGeneratable`/`outputRenderable`/render descriptor), keyed by symbol.
- `src/main/java/teralizer/domain/Variable.java`, `Constant.java` (Phase 3) — typed leaves.
- Golden-snapshot test + fixtures (Phase 2).

**Modified:**
- `src/main/java/teralizer/domain/ModelFolder.java`, `ModelVisitor.java` — add `Invocation`/`Not` hooks; later remove `Symbolic*Function` and per-type leaf hooks.
- `src/main/java/teralizer/domain/Operator.java` — shrink to true operators.
- `src/main/java/teralizer/transformer/SpfToModelTransformer.java` — total ingestion → `Invocation`/`Not`.
- `src/main/java/teralizer/transformer/ModelToJavaTransformer.java` — `fold(Invocation)`/`fold(Not)`; drop string arms + guards.
- `src/main/java/teralizer/transformer/{ModelToJsonTransformer,JsonToModelTransformer,*JsonAdapter}.java` — (de)serialize new nodes.
- `src/main/java/teralizer/jqwik/planning/{StringDomainPlanner,NumericDomainPlanner,BooleanDomainPlanner}.java` — consult the registry.
- `src/main/java/teralizer/processing/filter/StringOperationFilter.java` — registry-driven screen.
- `jpf-symbc/.../SymbolicStringHandler.java` — `toLowerCase`/`toUpperCase` handlers (Phase 1).

**Deleted (by end):** `SymbolicIntegerFunction.java`, `SymbolicRealFunction.java`, `SymbolicStringFunction.java`, `Variable{Integer,Real,String}.java`, `Constant{Integer,Real,String}.java`, and the retired `Operator` entries.

---

## Phase 1 — Foundations + string migration

Introduce the new nodes + registry, migrate string ops onto them, and unlock `trim`/`toLowerCase`/`toUpperCase`/`replace`.

### Task 1: `Invocation` and `Not` nodes (additive)

**Files:** Create `src/main/java/teralizer/domain/Invocation.java`, `Not.java`; Modify `ModelFolder.java`, `ModelVisitor.java`; Test `src/test/java/teralizer/domain/InvocationTest.java`.

- [x] **Step 1: Add folder + visitor hooks.** In `ModelFolder`, add:

```java
public abstract T fold(Invocation invocation, T receiver, java.util.List<T> args);
public abstract T fold(Not not, T operand);
```

In `ModelVisitor`, add no-op `preVisit`/`postVisit` for `Invocation` and `Not` (mirror the existing pairs).

- [x] **Step 2: Write `Invocation`.**

```java
package teralizer.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A method/function call: instance {@code receiver.method(args)} (receiver set) or static
 *  {@code qualifier.method(args)} (qualifier set, e.g. "java.lang.Math"). Exactly one of
 *  receiver/qualifier is non-null. Any arity via {@code args}. */
public class Invocation implements Expression {
    public final Expression receiver;   // null ⇒ static
    public final String qualifier;      // null ⇒ instance
    public final String method;
    public final List<Expression> args;

    public Invocation(Expression receiver, String qualifier, String method, List<Expression> args) {
        this.receiver = receiver;
        this.qualifier = qualifier;
        this.method = method;
        this.args = args;
    }

    @Override public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        if (this.receiver != null) this.receiver.accept(visitor);
        for (Expression arg : this.args) arg.accept(visitor);
        visitor.postVisit(this);
    }

    @Override public <T> T fold(ModelFolder<T> folder) {
        T r = this.receiver == null ? null : this.receiver.fold(folder);
        List<T> a = new ArrayList<>(this.args.size());
        for (Expression arg : this.args) a.add(arg.fold(folder));
        return folder.fold(this, r, a);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Invocation that = (Invocation) o;
        return Objects.equals(receiver, that.receiver) && Objects.equals(qualifier, that.qualifier)
            && Objects.equals(method, that.method) && Objects.equals(args, that.args);
    }
    @Override public int hashCode() { return Objects.hash(receiver, qualifier, method, args); }
    @Override public String toString() {
        String base = receiver != null ? receiver.toString() : qualifier;
        return base + "." + method + args;
    }
}
```

- [x] **Step 3: Write `Not`** — one field `Expression operand`; `accept` visits operand; `fold` returns `folder.fold(this, operand.fold(folder))`; standard `equals`/`hashCode`/`toString` (`"!(" + operand + ")"`).

- [x] **Step 4: Compile.** Run `./gradlew compileJava` — expect **failure**: `ModelToJavaTransformer` (and any other `ModelFolder`) now miss the two new abstract hooks.

- [x] **Step 5: Add temporary throwing hooks** to every `ModelFolder` subclass (`ModelToJavaTransformer` + the Model→JSON folder if present) so the tree compiles:

```java
@Override public String fold(Invocation invocation, String receiver, java.util.List<String> args) {
    throw new NonGeneralizableExpressionException("Invocation rendering not wired yet: " + invocation.method);
}
@Override public String fold(Not not, String operand) { return "(!(" + operand + "))"; }
```

(`Not` is trivial and final; `Invocation` gets its real body in Task 3.)

- [x] **Step 6: Test node mechanics.** In `InvocationTest`, assert `accept` visits receiver+args in order (use a counting `ModelVisitor`) and `equals`/`hashCode` for instance vs static. Run `./gradlew test --tests 'teralizer.domain.InvocationTest'` — expect PASS.

- [x] **Step 7: Commit.** `feat(domain): add Invocation and Not expression nodes`

### Task 2: capability registry

**Files:** Create `src/main/java/teralizer/jqwik/planning/MethodCapability.java`, `MethodCapabilities.java`; Test `MethodCapabilitiesTest.java`.

- [x] **Step 1: Write `MethodCapability`** — immutable record-like class:

```java
public final class MethodCapability {
    public final String method;          // "equals", "trim", "sqrt", ...
    public final String staticQualifier; // null ⇒ instance call; else e.g. "java.lang.Math"
    public final boolean inputGeneratable;
    public final boolean outputRenderable;
    // constructor + getters
}
```

(`spfCollectable` is implied by presence for ops SPF handles; ops absent from the registry are unsupported.)

- [x] **Step 2: Write `MethodCapabilities`** — a static `Map<String, MethodCapability>` seeded with the current sound set: `equals`, `startsWith`, `endsWith`, `contains` (instance, output-renderable + input-generatable per `StringDomainPlanner`), `equalsIgnoreCase` and `concat` (instance, output-renderable only), `isEmpty` (instance, models as `equals("")`), and current static renderable functions (`java.lang.Math.*`, `java.lang.String.valueOf`) so `Invocation` rendering does not regress numeric output. Include lookups `get(String)` / `isSupported(String)` / `isOutputRenderable(String)` / `isInputGeneratable(String)`.

- [x] **Step 3: Test** lookups: `isSupported("equals")` true, `isSupported("compareTo")` false, `get("isEmpty").staticQualifier == null`, `equalsIgnoreCase` output-only, and `sqrt` static-renderable. Run the test — expect PASS.

- [x] **Step 4: Commit.** `feat(planning): add method capability registry`

### Task 3: render `Invocation`/`Not`; retire string arms + guards

**Files:** Modify `ModelToJavaTransformer.java`; Test `ModelToJavaTransformerInvocationTest.java`.

- [ ] **Step 1: Write failing tests** for the render contract:
  - instance no-arg: `Invocation(Variable "s" STRING, null, "trim", [])` → `(_p_.s.trim())`
  - instance 1-arg: `Invocation(Variable "s", null, "equals", [Constant "foo"])` → `(_p_.s.equals("foo"))`
  - static 1-arg: `Invocation(null, "java.lang.Math", "sqrt", [Variable "x" REAL])` → `Math.sqrt(_p_.x)` (matches the current `SQRT` rendering)
  - negation: `Not(Invocation(... "equals" ...))` → `(!(_p_.s.equals("foo")))`
  - unsupported method (not in registry) → `NonGeneralizableExpressionException`

- [ ] **Step 2: Implement `fold(Invocation, receiver, args)`:**

```java
@Override public String fold(Invocation inv, String receiver, java.util.List<String> args) {
    if (!MethodCapabilities.isOutputRenderable(inv.method)) {
        throw new NonGeneralizableExpressionException("Cannot render call '" + inv.method + "' as Java.");
    }
    String argList = String.join(", ", args);
    if (inv.receiver != null) {           // instance
        return "(" + receiver + "." + inv.method + "(" + argList + "))";
    }
    return inv.qualifier + "." + inv.method + "(" + argList + ")";   // static (e.g. Math.sqrt)
}
```

`fold(Not, operand)` stays `"(!(" + operand + "))"`.

- [ ] **Step 3: Run, expect PASS.**

- [ ] **Step 4: Remove the string operator arms** (`EQUALS`…`NOTCONTAINS`, `CONCAT`) from `fold(Operation)` and delete `isStringOperator`/`isStringExpression`. Run `ModelToJavaTransformerNonGeneralizableTest` + the string operator test (now superseded by `...InvocationTest`; delete `ModelToJavaTransformerStringOperatorTest`).

- [ ] **Step 5: Commit.** `refactor(transformer): render string calls via Invocation, not Operation arms`

### Task 4: total string ingestion → `Invocation`/`Not`

**Files:** Modify `SpfToModelTransformer.java`; Test `SpfToModelTransformerStringTest.java` + the harness capture tests.

- [ ] **Step 1: Write failing tests.** Feed the visitor a `StringConstraint` for `s.equals("foo")` and assert the produced model is `Invocation(VariableString "s", null, "equals", [ConstantString "foo"])`; the false branch is `Not(Invocation(... "equals" ...))`; a `DerivedStringExpression(TRIM)` (unary, `right`=receiver) → `Invocation(receiver, null, "trim", [])`; a `DerivedStringExpression` with `oprlist` (`replace`) → `Invocation(receiver, null, "replace", [arg0, arg1])` (no drop).

- [ ] **Step 2: Rewrite `postVisit(StringConstraint)` and `postVisit(DerivedStringExpression)`** to emit `Invocation`/`Not`, reading operands from `left`/`right`/`oprlist` uniformly (pop the visited children; map the SPF `StringOperator`/`StringComparator` symbol → Java method name via a small switch; wrap the false-branch comparator in `Not`). An unmapped symbol throws a typed `UnsupportedTerm` (no silent drop). Delete the `Operator.get(...)`-based string path and the `oprlist` TODO.

- [ ] **Step 3: Run, expect PASS**; re-run `StringCaptureTest`, `StringReturnCaptureTest`, `StringIsEmptyCaptureTest` (update their JSON assertions to the new node shapes).

- [ ] **Step 4: Commit.** `refactor(transformer): total string ingestion into Invocation/Not`

### Task 5: `trim`, `toLowerCase`, `toUpperCase`, `replace`

**Files:** Modify `MethodCapabilities.java`, `StringOperationFilter.java`, `StringDomainPlanner.java`, `jpf-symbc/.../SymbolicStringHandler.java`, `StringExpression.java`; Tests + native `TestSymbolicStringCaseChange.java`.

- [ ] **Step 1: Register** `trim`, `replace` (SPF already captures them), and `toLowerCase`/`toUpperCase` in `MethodCapabilities` — all instance, `outputRenderable=true`; `inputGeneratable=false` (return-oracle-first: input use falls to the filter backstop). Point `StringOperationFilter` at `MethodCapabilities.isSupported(...)` instead of its hardcoded `UNSUPPORTED_STRING_OPS` list.

- [ ] **Step 2: Add SPF handlers for `toLowerCase`/`toUpperCase`.** In `StringExpression`, add `_toLowerCase()`/`_toUpperCase()` mirroring `_trim()` (`new DerivedStringExpression(StringOperator.TOLOWERCASE, this)`). In `SymbolicStringHandler`, add dispatch arms + handlers mirroring `handleTrim`. Rebuild the submodule.

- [ ] **Step 3: Native test** `TestSymbolicStringCaseChange` (mirror `TestSymbolicStringIsEmpty`): a MUT returning `s.toLowerCase()` captures a `DerivedStringExpression(TOLOWERCASE)`. Run via the toggle; revert `settings.gradle`.

- [ ] **Step 4: Teralizer test** — a `toLowerCase` return renders `(_p_.s.toLowerCase())`; `trim` return renders `(_p_.s.trim())`; a `replace` renders `(_p_.s.replace('a', 'b'))`. Run, expect PASS.

- [ ] **Step 5: Commit.** `feat: sound trim/toLowerCase/toUpperCase/replace via unified model` (submodule + parent pointer bump).

### Task 6: delete `SymbolicStringFunction` + retire string `Operator` entries

- [ ] **Step 1:** Remove `SymbolicStringFunction` usages; delete the class + its `ModelFolder`/`ModelVisitor` hooks (compile-guided). Remove `EQUALS`…`NOTCONTAINS`, `EQUALSIGNORECASE`/`NOT…`, `CONTAINS`, `CONCAT`, `EMPTY`/`NOTEMPTY`, `MATCHES`/`REGIONMATCHES`, string transform entries from `Operator`. Update the JSON adapters for the removed nodes.
- [ ] **Step 2:** `./gradlew test` + native tests green.
- [ ] **Step 3: Commit.** `refactor(domain): delete SymbolicStringFunction and string Operator entries`

---

## Phase 2 — Function migration (numeric)

Move math functions + `valueOf` to `Invocation`; the ~250 numeric generalizations are the guardrail.

### Task 7: golden-snapshot harness

**Files:** Create `src/test/java/teralizer/transformer/GoldenRenderingTest.java` + `src/test/resources/golden/*.json`.

- [ ] **Step 1:** Capture rendered Java for a deterministic representative set of numeric/char/boolean/string specs (build the `Model` directly, fold with `ModelToJavaTransformer`) and assert against committed golden strings. Commit the goldens as the pre-migration baseline. Run, expect PASS.
- [ ] **Step 2: Commit.** `test(transformer): golden rendering snapshots for the numeric baseline`

### Task 8: math functions + `valueOf` → `Invocation`

**Files:** Modify `SpfToModelTransformer.java`, `ModelToJavaTransformer.java`, `MethodCapabilities.java`; delete `SymbolicIntegerFunction`/`SymbolicRealFunction`.

- [ ] **Step 1:** Register `sqrt`/`pow`/`exp`/`log`/`sin`/`cos`/`tan`/`asin`/`acos`/`atan`/`atan2` (static `java.lang.Math`) and `valueOf` (static `java.lang.String`) in `MethodCapabilities`, with render matching the current `Math.x(...)` output.
- [ ] **Step 2:** Ingest `MathRealExpression`/`SymbolicRealFunction`/`SymbolicIntegerFunction` + `DerivedStringExpression(VALUEOF)` → `Invocation`. Remove `SQRT`…`ATAN2` from `Operator` and their `fold(Operation)` arms; delete the two numeric function nodes + hooks.
- [ ] **Step 3:** Update the `GoldenRenderingTest` goldens where rendering intentionally changed (parens/spacing); each changed golden's generated test must still compile+pass. Run `./gradlew test` — expect PASS.
- [ ] **Step 4: Verify the behavioral guardrail** — run the numeric/char/boolean generalizations (existing generation tests or a disposable-DB spike) and confirm they compile + pass.
- [ ] **Step 5: Commit.** `refactor: migrate math functions and valueOf to Invocation`

---

## Phase 3 — Leaf unification

### Task 9: typed `Variable`/`Constant`

**Files:** Create `Variable.java`, `Constant.java`; Modify `ModelFolder.java`, `ModelVisitor.java`, `SpfToModelTransformer.java`, `ModelToJavaTransformer.java`, all planners, JSON adapters; delete `Variable{Integer,Real,String}`, `Constant{Integer,Real,String}`.

- [ ] **Step 1: Write failing tests** — `Variable("x", TypeDomain.REAL)` folds/renders exactly as `VariableReal("x")` did; same for `Constant` per type; char/boolean map to `TypeDomain.INTEGER` as today.
- [ ] **Step 2:** Add `Variable(name, TypeDomain)` + `Constant(value, TypeDomain)` with `accept`/`fold`; add `fold(Variable)`/`fold(Constant)` to `ModelFolder` (render by `TypeDomain`, reproducing the current per-type output — string literals quoted, reals as-is, etc.).
- [ ] **Step 3:** Repoint `SpfToModelTransformer`, planners, and JSON adapters to the unified leaves. Delete the six per-type classes + their hooks (compile-guided).
- [ ] **Step 4:** `./gradlew test` + native tests + numeric guardrail green; goldens updated only where intentionally changed.
- [ ] **Step 5: Commit.** `refactor(domain): unify Variable/Constant into typed leaves`

---

## Phase 4 — Delete legacy + finalize

### Task 10: cleanup + doc sync

- [ ] **Step 1:** Remove any remaining dead code, unused `Operator` entries, and the last hardcoded soundness lists (everything registry-driven). Confirm `MethodCapabilities` is the single source consulted by screen + fold + planners + ingestion admission.
- [ ] **Step 2:** `./gradlew test` + native tests + numeric guardrail green.
- [ ] **Step 3:** Update `2026-06-30-partial-sound-string-support` (remaining ops now landed) and the overview; `omp-plans index` + `check`.
- [ ] **Step 4: Commit.** `refactor(domain): finalize unified expression model; registry as single source`

---

## Self-review

- **Spec coverage:** node model = Tasks 1, 9 (+ `Operator` shrink across 3, 6, 8); `Invocation`/`Not` = 1, 3, 4; capability registry = 2 (+ wired in 5, 6, 8, 10); total ingestion = 4, 8; layer contracts = 3 (render), 4/8 (ingestion), 5 (planner/screen); migration phases 1–4 map 1:1; testing = golden harness (7) + per-phase gates + native tests + behavioral guardrail (8).
- **Sequencing:** string-first (Phase 1) is lowest-risk and unlocks the leftover ops; numeric (Phase 2) is guardrailed by goldens + the 250 generalizations; leaves (Phase 3) last; delete (Phase 4).
- **Deferred (unchanged):** Tier-3 string ops (`compareTo`, regex, SIOOBE `substring`/`charAt`), arrays, and MUT-id all remain out of scope.
