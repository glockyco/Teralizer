---
title: Unified Expression Model — Implementation Plan
type: plan
status: active
created: 2026-06-30
parent: 2026-06-30-unified-expression-model
---

# Unified Expression Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Execution scope: Phase 1 is implemented. Phase 2 is expanded below for a user checkpoint and MUST NOT be coded until that checkpoint is approved. Phases 3–4 remain committed scope but MUST each be expanded into their own step-level plan (with a fresh code-read) and checkpointed with the user before any code is written for them.**

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

### Task 3: render `Invocation`/`Not`; preserve legacy string arms until ingestion moves

**Files:** Modify `ModelToJavaTransformer.java`; Test `ModelToJavaTransformerInvocationTest.java`.

- [x] **Step 1: Write failing tests** for the render contract:
  - instance no-arg: `Invocation(Variable "s" STRING, null, "isEmpty", [])` → `(_p_.s.isEmpty())`
  - instance 1-arg: `Invocation(Variable "s", null, "equals", [Constant "foo"])` → `(_p_.s.equals("foo"))`
  - static 1-arg: `Invocation(null, "java.lang.Math", "sqrt", [Variable "x" REAL])` → `Math.sqrt(_p_.x)` (matches the current `SQRT` rendering)
  - negation: `Not(Invocation(... "equals" ...))` → `(!(_p_.s.equals("foo")))`
  - unsupported method (not in registry) → `NonGeneralizableExpressionException`
  - static/instance qualifier mismatches → `NonGeneralizableExpressionException`

- [x] **Step 2: Implement `fold(Invocation, receiver, args)`:**

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

`fold(Not, operand)` renders `"(!" + operand + ")"`, preserving the operand's existing parentheses without duplicating them.

- [x] **Step 3: Run, expect PASS.**

- [x] **Step 4: Preserve the string `Operation` render arms** (`EQUALS`…`NOTCONTAINS`, `CONCAT`) until `SpfToModelTransformer` and `StringDomainPlanner` stop producing/consuming them. Task 6 removes these arms after string ingestion/planning has moved to `Invocation`.

- [x] **Step 5: Commit.** `refactor(transformer): render calls via Invocation`

### Task 4: total string ingestion → `Invocation`/`Not`

**Files:** Modify `SpfToModelTransformer.java`; Test `SpfToModelTransformerStringTest.java` + the harness capture tests.

- [x] **Step 1: Write failing tests.** Feed the visitor a `StringConstraint` for `s.equals("foo")` and assert the produced model is `Invocation(VariableString "s", null, "equals", [ConstantString "foo"])`; the false branch is `Not(Invocation(... "equals" ...))`; canonicalize reversed equality so the symbolic expression is the receiver; unary `EMPTY`/`NOTEMPTY` become `isEmpty`/`Not(isEmpty)`; a `DerivedStringExpression(TRIM)` (unary, `right`=receiver) → `Invocation(receiver, null, "trim", [])`; a `DerivedStringExpression` with `oprlist` (`replace`) → `Invocation(receiver, null, "replace", [arg0, arg1])` (no drop); `Invocation`/`Not` serialize with `_type` and round-trip.

- [x] **Step 2: Rewrite `postVisit(StringConstraint)` and `postVisit(DerivedStringExpression)`** to emit `Invocation`/`Not`, reading operands from `left`/`right`/`oprlist` uniformly (pop visited children; transform `oprlist` operands directly because SPF does not visit them; map the SPF `StringOperator`/`StringComparator` symbol → Java method name via a small switch; wrap the false-branch comparator in `Not`). Null-guard SPF unary string constraints before visiting/popping `left`. An unmapped symbol throws a typed `UnsupportedTerm` (no silent drop). Delete the `Operator.get(...)`-based string path and the `oprlist` TODO.

- [x] **Step 3: Run, expect PASS**; re-run `StringCaptureTest`, `StringReturnCaptureTest`, `StringIsEmptyCaptureTest` (update their JSON assertions to the new node shapes).

- [x] **Step 4: Commit.** `refactor(transformer): total string ingestion into Invocation/Not`

### Task 5: `trim`, `toLowerCase`, `toUpperCase`, `replace`

**Files:** Modify `MethodCapabilities.java`, `StringOperationFilter.java`, `StringDomainPlanner.java`, `jpf-symbc/.../SymbolicStringHandler.java`, `StringExpression.java`; Tests + native `TestSymbolicStringCaseChange.java`.

- [x] **Step 1: Register** `trim`, `replace` (SPF already captures them), and `toLowerCase`/`toUpperCase` in `MethodCapabilities` — all instance, `outputRenderable=true`; `inputGeneratable=false` (return-oracle-first: input use falls to the filter backstop). Keep SPF-collected numeric string methods (`length`, `indexOf`, `lastIndexOf`) registered for filtering without output rendering. Point `StringOperationFilter` at `MethodCapabilities.isSupported(...)` instead of its hardcoded `UNSUPPORTED_STRING_OPS` list.

- [x] **Step 2: Add SPF handlers for `toLowerCase`/`toUpperCase`.** In `StringExpression`, add `_toLowerCase()`/`_toUpperCase()` mirroring `_trim()` (`new DerivedStringExpression(StringOperator.TOLOWERCASE, this)`). In `SymbolicStringHandler`, add dispatch arms + handlers mirroring `handleTrim`, preserving the concrete transformed value for symcrete branch selection. Rebuild the submodule.

- [x] **Step 3: Native test** `TestSymbolicStringCaseChange` (mirror `TestSymbolicStringIsEmpty`): branches on `s.toLowerCase().equals("foo")` and `s.toUpperCase().equals("FOO")` follow the concrete seed path under `collect_constraints`. Run via the toggle; revert `settings.gradle`.

- [x] **Step 4: Teralizer test** — `toLowerCase`, `toUpperCase`, `trim`, and char-overload `replace('o', 'a')` returns render as `Invocation` Java expressions. Run, expect PASS.

- [x] **Step 5: Commit.** `feat: sound trim/toLowerCase/toUpperCase/replace via unified model` (submodule + parent pointer bump).

### Task 6: delete `SymbolicStringFunction` + retire string `Operator` entries

- [x] **Step 1:** Remove `SymbolicStringFunction` usages; delete the class + its `ModelFolder`/`ModelVisitor` hooks (compile-guided). Remove `EQUALS`…`NOTCONTAINS`, `EQUALSIGNORECASE`/`NOT…`, `CONTAINS`, `CONCAT`, `EMPTY`/`NOTEMPTY`, `MATCHES`/`REGIONMATCHES`, string transform entries from `Operator`. Update the JSON adapters for the removed nodes.
- [x] **Step 2:** `./gradlew test` + native tests green.
- [x] **Step 3: Commit.** `refactor(domain): delete SymbolicStringFunction and string Operator entries`

---

## Phase 2 — Function migration (numeric)

Move math functions to `Invocation`; keep `String.valueOf` as the static `Invocation` shape that Phase 1 already introduced; delete the remaining numeric function nodes and the math entries from `Operator`. The ~250 numeric/char/boolean JARVIS generalizations are the behavioral guardrail.

**Fresh code-read findings (2026-07-01):**
- `MethodCapabilities` already registers `sqrt`/`pow`/`exp`/`log`/`sin`/`cos`/`tan`/`asin`/`acos`/`atan`/`atan2` as static `java.lang.Math` output-renderable methods, and `valueOf` as static `java.lang.String` output-renderable.
- `SpfToModelTransformer.postVisit(MathRealExpression)` still emits `Operation(left, Operator.SQRT…ATAN2, right)`; `DerivedStringExpression(VALUEOF)` already emits `Invocation(null, "java.lang.String", "valueOf", operands)`.
- `SymbolicIntegerFunction` and `SymbolicRealFunction` remain only as model nodes, visitor/folder hooks, JSON adapters, renderer hooks, and tests; SPF does not need to keep producing them.
- `ModelToJavaTransformer.isFloatingPoint` must learn that static `java.lang.Math` invocations are real-valued before math operators leave `Operator`, otherwise bitwise/shift guards can miss `Math.*` operands.

### Task 7: golden-snapshot harness

**Files:** Create `src/test/java/teralizer/transformer/GoldenRenderingTest.java`; Create `src/test/resources/golden/rendering-baseline.json`.

- [ ] **Step 1: Write the failing harness test only.** Add `GoldenRenderingTest` that builds a deterministic `LinkedHashMap<String, ModelCase>` and reads `/golden/rendering-baseline.json`. The first run intentionally fails because the resource is absent. Use these cases:

```java
package teralizer.transformer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.*;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GoldenRenderingTest {
    @Example
    void currentRepresentativeModelsRenderAsTheBaseline() throws Exception {
        Map<String, String> expected = readBaseline();
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map.Entry<String, ModelCase> entry : cases().entrySet()) {
            actual.put(entry.getKey(), entry.getValue().transformer.transform(entry.getValue().model));
        }
        Assert.assertEquals(expected, actual);
    }

    private static Map<String, ModelCase> cases() {
        Map<String, ModelCase> cases = new LinkedHashMap<>();
        cases.put("math.sqrt.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Operation(new VariableReal("x"), Operator.SQRT, null)));
        cases.put("math.pow.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Operation(new VariableReal("x"), Operator.POW, new ConstantReal(2.0))));
        cases.put("math.atan2.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Operation(new VariableReal("y"), Operator.ATAN2, new VariableReal("x"))));
        cases.put("boolean.path.predicate", new ModelCase(
            new ModelToJavaTransformer(Collections.singletonMap("b", "boolean")),
            new Operation(new VariableInteger("b"), Operator.NE, new ConstantInteger(0))));
        cases.put("char.bound.predicate", new ModelCase(
            new ModelToJavaTransformer(Collections.singletonMap("c", "char")),
            new Operation(new VariableInteger("c"), Operator.GT, new ConstantInteger(64))));
        cases.put("string.transform.invocation", new ModelCase(
            new ModelToJavaTransformer(),
            new Invocation(new VariableString("s"), null, "trim", Collections.emptyList())));
        return cases;
    }

    private static Map<String, String> readBaseline() throws Exception {
        try (Reader reader = new InputStreamReader(
            GoldenRenderingTest.class.getResourceAsStream("/golden/rendering-baseline.json"),
            StandardCharsets.UTF_8)) {
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            return new Gson().fromJson(reader, type);
        }
    }

    private static final class ModelCase {
        final ModelToJavaTransformer transformer;
        final Model model;

        ModelCase(ModelToJavaTransformer transformer, Model model) {
            this.transformer = transformer;
            this.model = model;
        }
    }
}
```

Run: `./gradlew test --tests 'teralizer.transformer.GoldenRenderingTest'`

Expected: FAIL with a missing/null resource failure before any production code changes.

- [ ] **Step 2: Add the baseline fixture.** Create `src/test/resources/golden/rendering-baseline.json` with exactly:

```json
{
  "math.sqrt.operation": "Math.sqrt(_p_.x)",
  "math.pow.operation": "Math.pow(_p_.x, 2.0)",
  "math.atan2.operation": "Math.atan2(_p_.y, _p_.x)",
  "boolean.path.predicate": "((_p_.b ? 1 : 0) != 0)",
  "char.bound.predicate": "(_p_.c > 64)",
  "string.transform.invocation": "(_p_.s.trim())"
}
```

Run: `./gradlew test --tests 'teralizer.transformer.GoldenRenderingTest'`

Expected: PASS.

- [ ] **Step 3: Commit.** Commit only the golden harness + fixture with subject `test(transformer): add rendering baseline goldens` and a body explaining that this is a review prompt for Phase 2, not a byte-parity compatibility guarantee.

### Task 8: math functions + numeric function nodes → `Invocation`

**Files:** Modify `src/main/java/teralizer/transformer/SpfToModelTransformer.java`, `src/main/java/teralizer/transformer/ModelToJavaTransformer.java`, `src/main/java/teralizer/transformer/ModelToJsonTransformer.java`, `src/main/java/teralizer/transformer/JsonToModelTransformer.java`, `src/main/java/teralizer/domain/ModelFolder.java`, `src/main/java/teralizer/domain/ModelVisitor.java`, `src/main/java/teralizer/domain/Operator.java`; Delete `src/main/java/teralizer/domain/SymbolicIntegerFunction.java`, `src/main/java/teralizer/domain/SymbolicRealFunction.java`; Test updates under `src/test/java/teralizer/{domain,transformer,jqwik/planning}`.

- [ ] **Step 1: Write failing transformer and operator tests.** Add/extend tests before production edits:
  - `SpfToModelTransformerMathInvocationTest.unaryMathRealExpressionBecomesStaticInvocation`: `new MathRealExpression(MathFunction.SQRT, new SymbolicReal("x_1_SYMREAL"))` transforms to `new Invocation(null, "java.lang.Math", "sqrt", Collections.singletonList(new VariableReal("x")))`.
  - `SpfToModelTransformerMathInvocationTest.binaryMathRealExpressionPreservesArgumentOrder`: `new MathRealExpression(MathFunction.POW, new SymbolicReal("x_1_SYMREAL"), new SymbolicReal("y_2_SYMREAL"))` transforms to `new Invocation(null, "java.lang.Math", "pow", Arrays.asList(new VariableReal("x"), new VariableReal("y")))`.
  - `OperatorTest.mathFunctionSymbolsAreNotOperators`: `sqrt`, `pow`, `exp`, `log`, `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2` must all throw `IllegalArgumentException` from `Operator.get`.
  - `ModelToJavaTransformerNonGeneralizableTest.bitwiseOnMathInvocationThrowsTypedException`: `new Operation(new Invocation(null, "java.lang.Math", "sqrt", singletonList(new VariableReal("x"))), Operator.AND, new ConstantInteger(1))` throws `NonGeneralizableExpressionException`.
  - `InvocationJsonRoundTripTest.staticMathInvocationRoundTrips`: static `Math.pow(x, 2.0)` keeps `receiver == null`, `qualifier == "java.lang.Math"`, method, and arg order after JSON round trip.

Run:

```bash
./gradlew test \
  --tests 'teralizer.transformer.SpfToModelTransformerMathInvocationTest' \
  --tests 'teralizer.domain.OperatorTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerNonGeneralizableTest' \
  --tests 'teralizer.transformer.InvocationJsonRoundTripTest'
```

Expected: FAIL for math ingestion/operator/bitwise guard; JSON static invocation may already pass.

- [ ] **Step 2: Ingest `MathRealExpression` as static `Invocation`.** In `SpfToModelTransformer.postVisit(MathRealExpression)`, replace the `Operator.get(...)` / `new Operation(...)` path with a method-name conversion based on `expression.getOp().name().toLowerCase(Locale.ROOT)` and argument list construction that preserves `arg1`, then `arg2` when present:

```java
Expression right = expression.getArg2() == null ? null : this.stack.pop();
Expression left = expression.getArg1() == null ? null : this.stack.pop();
List<Expression> args = new ArrayList<>(2);
args.add(left);
if (right != null) {
    args.add(right);
}
this.stack.push(new Invocation(
    null,
    "java.lang.Math",
    expression.getOp().name().toLowerCase(Locale.ROOT),
    args));
```

Run `./gradlew test --tests 'teralizer.transformer.SpfToModelTransformerMathInvocationTest'` — expect PASS.

- [ ] **Step 3: Retire math entries from `Operator` and `fold(Operation)`.** Remove `POW`, `SQRT`, `EXP`, `LOG`, `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`, `ATAN`, and `ATAN2` from `Operator`; remove the matching `case` arms from `ModelToJavaTransformer.fold(Operation)`; update `GoldenRenderingTest` to build the three math cases as static `Invocation` instead of `Operation` while keeping the expected JSON unchanged. Extend `isFloatingPoint(Expression)` to return true for output-renderable static `java.lang.Math` invocations so the bitwise/shift guard still rejects real-valued math calls.

Run:

```bash
./gradlew test \
  --tests 'teralizer.domain.OperatorTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerNonGeneralizableTest' \
  --tests 'teralizer.transformer.GoldenRenderingTest'
```

Expected: PASS.

- [ ] **Step 4: Delete `SymbolicIntegerFunction` and `SymbolicRealFunction`.** Remove the classes, their `ModelFolder` hooks, their `ModelVisitor` hooks, their JSON serializers/deserializers/registrations, and their direct renderer hooks. Update `ModelFolderTest.folderDeclaresOneHookPerConcreteNode` to remove both classes from the expected node set, and update `ModelToJavaTransformerFoldOrderTest` to pin static `Invocation` argument order instead of the deleted symbolic-function nodes. Run `./gradlew compileTestJava` first; any remaining reference to the deleted classes is a compile failure to fix at the source.

Expected: `./gradlew compileTestJava` PASS, and `grep` for `SymbolicIntegerFunction|SymbolicRealFunction` under `src/main` and `src/test` returns no matches.

- [ ] **Step 5: Keep `String.valueOf` as Phase-1 static invocation.** Do not add an `Operator.VALUEOF` or numeric-function node replacement for it. Add a regression assertion either in `ModelToJavaTransformerInvocationTest` or `GoldenRenderingTest` that `new Invocation(null, "java.lang.String", "valueOf", Collections.singletonList(new VariableInteger("i")))` renders as `String.valueOf(_p_.i)`.

Run `./gradlew test --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' --tests 'teralizer.transformer.GoldenRenderingTest'` — expect PASS.

- [ ] **Step 6: Run focused and full verification.** Run:

```bash
./gradlew test \
  --tests 'teralizer.transformer.GoldenRenderingTest' \
  --tests 'teralizer.transformer.SpfToModelTransformerMathInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerNonGeneralizableTest' \
  --tests 'teralizer.transformer.InvocationJsonRoundTripTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerFoldOrderTest' \
  --tests 'teralizer.domain.ModelFolderTest' \
  --tests 'teralizer.domain.OperatorTest' \
  --tests 'teralizer.jqwik.planning.MethodCapabilitiesTest'
./gradlew test
```

Run the native SPF string tests with a cleanup wrapper/trap so `settings.gradle` is restored even on failure:

```bash
trap 'git checkout -- settings.gradle' EXIT
perl -0pi -e 's/task\.enabled = false/\/\/ task.enabled = false/' settings.gradle
./gradlew :jpf-symbc:test \
  --tests 'gov.nasa.jpf.symbc.TestSymbolicStringCaseChange' \
  --tests 'gov.nasa.jpf.symbc.TestSymbolicStringSymcrete' \
  --tests 'gov.nasa.jpf.symbc.TestSymbolicStringIsEmpty' \
  --tests 'gov.nasa.jpf.symbc.TestSymbolicStringEqualsIgnoreCase'
```

Expected: both Gradle commands PASS and `git status --short settings.gradle` is clean.

- [ ] **Step 7: Run the numeric/char/boolean behavioral guardrail.** Use the JARVIS scratch scorecard because `docs/plans/2026-06-30-jarvis-comparison.md` records the current 250 sound assertion-properties and the char/boolean/numeric Table-2 probes. Follow `skill://running-the-jarvis-scoreboard`: prepare fixtures, reset `postgres_jarvis_scoreboard`, then run:

```bash
DB_NAME=postgres_jarvis_scoreboard DATA_DIR=data/jarvis-scoreboard DATASET_VARIANT=jarvis \
  bash scripts/run-jarvis-scoreboard.sh 2>&1 | tee /tmp/teralizer-jarvis-scoreboard.log
! grep -E "ERROR t.processing.ProcessingPipeline|terminated with exit code|BUILD FAILURE" /tmp/teralizer-jarvis-scoreboard.log
uv run --directory analysis python -m teralizer.jarvis_scoreboard --census
```

Acceptance: generated jqwik tests compile and execute, `! grep` succeeds because the run log has zero matching pipeline/build failures, and the census still reports the current sound numeric/char/boolean successes without a drop attributable to rendering or ingestion.

- [ ] **Step 8: Run branch handoff build.** Run `./gradlew build` after the full suite and guardrail so Gradle build/check tasks and SPF submodule build tasks are covered before any merge/PR handoff.

- [ ] **Step 9: Commit.** Commit the math migration, deleted nodes, updated tests, and Phase-2 checkboxes with subject `refactor(domain): migrate math functions to Invocation` and a body explaining why math functions are calls rather than operators, why `String.valueOf` stays as the static invocation shape, and how the guardrail was verified.

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
