---
title: Unified Expression Model — Implementation Plan
type: plan
status: implemented
created: 2026-06-30
parent: 2026-06-30-unified-expression-model
archived: 2026-07-01
---

# Unified Expression Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Execution scope: Phases 1–3 are implemented. Phase 4 remains committed scope but MUST be expanded into its own step-level plan (with a fresh code-read) and checkpointed with the user before any code is written for it.**

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

- [x] **Step 1: Write the failing harness test only.** Add `GoldenRenderingTest` that builds a deterministic `LinkedHashMap<String, ModelCase>` and reads `/golden/rendering-baseline.json`. The first run intentionally fails because the resource is absent. Use these cases:

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

- [x] **Step 2: Add the baseline fixture.** Create `src/test/resources/golden/rendering-baseline.json` with exactly:

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

- [x] **Step 3: Commit.** Commit only the golden harness + fixture with subject `test(transformer): add rendering baseline goldens` and a body explaining that this is a review prompt for Phase 2, not a byte-parity compatibility guarantee.

### Task 8: math functions + numeric function nodes → `Invocation`

**Files:** Modify `src/main/java/teralizer/transformer/SpfToModelTransformer.java`, `src/main/java/teralizer/transformer/ModelToJavaTransformer.java`, `src/main/java/teralizer/transformer/ModelToJsonTransformer.java`, `src/main/java/teralizer/transformer/JsonToModelTransformer.java`, `src/main/java/teralizer/domain/ModelFolder.java`, `src/main/java/teralizer/domain/ModelVisitor.java`, `src/main/java/teralizer/domain/Operator.java`; Delete `src/main/java/teralizer/domain/SymbolicIntegerFunction.java`, `src/main/java/teralizer/domain/SymbolicRealFunction.java`; Test updates under `src/test/java/teralizer/{domain,transformer,jqwik/planning}`.

- [x] **Step 1: Write failing transformer and operator tests.** Add/extend tests before production edits:
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

- [x] **Step 2: Ingest `MathRealExpression` as static `Invocation`.** In `SpfToModelTransformer.postVisit(MathRealExpression)`, replace the `Operator.get(...)` / `new Operation(...)` path with a method-name conversion based on `expression.getOp().name().toLowerCase(Locale.ROOT)` and argument list construction that preserves `arg1`, then `arg2` when present:

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

- [x] **Step 3: Retire math entries from `Operator` and `fold(Operation)`.** Remove `POW`, `SQRT`, `EXP`, `LOG`, `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`, `ATAN`, and `ATAN2` from `Operator`; remove the matching `case` arms from `ModelToJavaTransformer.fold(Operation)`; update `GoldenRenderingTest` to build the three math cases as static `Invocation` instead of `Operation` while keeping the expected JSON unchanged. Extend `isFloatingPoint(Expression)` to return true for output-renderable static `java.lang.Math` invocations so the bitwise/shift guard still rejects real-valued math calls.

Run:

```bash
./gradlew test \
  --tests 'teralizer.domain.OperatorTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerNonGeneralizableTest' \
  --tests 'teralizer.transformer.GoldenRenderingTest'
```

Expected: PASS.

- [x] **Step 4: Delete `SymbolicIntegerFunction` and `SymbolicRealFunction`.** Remove the classes, their `ModelFolder` hooks, their `ModelVisitor` hooks, their JSON serializers/deserializers/registrations, and their direct renderer hooks. Update `ModelFolderTest.folderDeclaresOneHookPerConcreteNode` to remove both classes from the expected node set, and update `ModelToJavaTransformerFoldOrderTest` to pin static `Invocation` argument order instead of the deleted symbolic-function nodes. Run `./gradlew compileTestJava` first; any remaining reference to the deleted classes is a compile failure to fix at the source.

Expected: `./gradlew compileTestJava` PASS, and `grep` for `SymbolicIntegerFunction|SymbolicRealFunction` under `src/main` and `src/test` returns no matches.

- [x] **Step 5: Keep `String.valueOf` as Phase-1 static invocation.** Do not add an `Operator.VALUEOF` or numeric-function node replacement for it. Add a regression assertion either in `ModelToJavaTransformerInvocationTest` or `GoldenRenderingTest` that `new Invocation(null, "java.lang.String", "valueOf", Collections.singletonList(new VariableInteger("i")))` renders as `String.valueOf(_p_.i)`.

Run `./gradlew test --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' --tests 'teralizer.transformer.GoldenRenderingTest'` — expect PASS.

- [x] **Step 6: Run focused and full verification.** Run:

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

- [x] **Step 7: Run the numeric/char/boolean behavioral guardrail.** Use the JARVIS scratch scorecard because `docs/plans/2026-06-30-jarvis-comparison.md` records the current 250 sound assertion-properties and the char/boolean/numeric Table-2 probes. Follow `skill://running-the-jarvis-scoreboard`: prepare fixtures, reset `postgres_jarvis_scoreboard`, then run:

```bash
DB_NAME=postgres_jarvis_scoreboard DATA_DIR=data/jarvis-scoreboard DATASET_VARIANT=jarvis \
  bash scripts/run-jarvis-scoreboard.sh 2>&1 | tee /tmp/teralizer-jarvis-scoreboard.log
grep -E "scoreboard run complete: no pipeline breakage" /tmp/teralizer-jarvis-scoreboard.log
! grep -E "pipeline task\(s\) FAILED outside JPF analysis|gradle exited non-zero|terminated with exit code|BUILD FAILURE" /tmp/teralizer-jarvis-scoreboard.log
uv run --directory analysis python -m teralizer.jarvis_scoreboard --census
```

Acceptance: generated jqwik tests compile and execute, the run log contains `scoreboard run complete: no pipeline breakage`, the failure grep finds no non-JPF pipeline/build failures, expected raw-bits per-assertion JPF exclusions remain non-fatal, and the census still reports the current 250 sound numeric/char/boolean successes without a drop attributable to rendering or ingestion.

- [x] **Step 8: Run branch handoff build.** Run `./gradlew build` after the full suite and guardrail so Gradle build/check tasks and SPF submodule build tasks are covered before any merge/PR handoff.

- [x] **Step 9: Commit.** Commit the math migration, deleted nodes, updated tests, and Phase-2 checkboxes with subject `refactor(domain): migrate math functions to Invocation` and a body explaining why math functions are calls rather than operators, why `String.valueOf` stays as the static invocation shape, and how the guardrail was verified.

---

## Phase 3 — Leaf unification

### Task 9: typed `Variable`/`Constant`

Replace the six per-type leaf classes with `Variable(name, TypeDomain)` and `Constant(value, TypeDomain)`. Move `TypeDomain` into `teralizer.domain` first so the domain model does not depend on `teralizer.jqwik.planning`.

**Fresh code-read findings (2026-07-01):**
- `TypeDomain` currently lives in `teralizer.jqwik.planning`; typed leaves in `teralizer.domain` must not import planning, so Phase 3 moves the enum to `teralizer.domain` and updates planner imports.
- SPF integer variables still cover Java `int`, `long`, `char`, and `boolean`; the model leaf remains `TypeDomain.INTEGER` for symbolic integer terms. Char/boolean specialization stays in parameter metadata (`ModelToJavaTransformer.variableTypes`, `NumericDomainPlanner`, `BooleanDomainPlanner`) as today.
- Leaf-specific logic remains in `SpfToModelTransformer`, `ValueToModelTransformer`, `ModelToJavaTransformer`, `VariableNameCollector`, `NumericClauseInterpreter`, `StringDomainPlanner`, JSON adapters, and folder/visitor tests.
- `GoldenRenderingTest` is a construction-level snapshot: Phase 3 should update its model factories from old leaves to typed leaves while keeping expected rendered strings unchanged unless a real rendering change is intentional.

**Files:** Move `src/main/java/teralizer/jqwik/planning/TypeDomain.java` to `src/main/java/teralizer/domain/TypeDomain.java`; Create `src/main/java/teralizer/domain/Variable.java`, `Constant.java`; Modify `ModelFolder.java`, `ModelVisitor.java`, `SpfToModelTransformer.java`, `ValueToModelTransformer.java`, `VariableNameCollector.java`, `ModelToJavaTransformer.java`, `ModelToJsonTransformer.java`, `JsonToModelTransformer.java`, `NumericClauseInterpreter.java`, `BooleanDomainPlanner.java`, `StringDomainPlanner.java`, planner imports/tests, transformer tests, and `GoldenRenderingTest`; Delete `VariableInteger.java`, `VariableReal.java`, `VariableString.java`, `ConstantInteger.java`, `ConstantReal.java`, `ConstantString.java`.

- [x] **Step 1: Write failing typed-leaf tests.** Add `src/test/java/teralizer/domain/TypedLeafTest.java` before production edits:
  - `new Variable("x", TypeDomain.REAL)` visits `preVisit(Variable)` / `postVisit(Variable)`, folds through `fold(Variable)`, and equals another real variable with the same name but not an integer variable with the same name.
  - `new Constant(7L, TypeDomain.INTEGER)`, `new Constant(1.5d, TypeDomain.REAL)`, and `new Constant("s", TypeDomain.STRING)` fold through `fold(Constant)` and equality includes the domain.
  - `Variable.toString()` returns the name; `Constant.toString()` returns the raw value string for all three domains.

Run `./gradlew test --tests 'teralizer.domain.TypedLeafTest'` — expect compilation failure because `teralizer.domain.TypeDomain`, `Variable`, and `Constant` do not exist.

- [x] **Step 2: Move `TypeDomain` and add typed leaves additively.** Move `TypeDomain` to package `teralizer.domain` and update imports in `teralizer.jqwik.planning` and tests. Add:

```java
public final class Variable implements Expression {
    public final String name;
    public final TypeDomain domain;
    public Variable(String name, TypeDomain domain) { this.name = name; this.domain = domain; }
    @Override public void accept(ModelVisitor visitor) { visitor.preVisit(this); visitor.postVisit(this); }
    @Override public <T> T fold(ModelFolder<T> folder) { return folder.fold(this); }
    @Override public String toString() { return this.name; }
    // equals/hashCode include name + domain
}
```

```java
public final class Constant implements Expression {
    public final Object value;
    public final TypeDomain domain;
    public Constant(Object value, TypeDomain domain) { this.value = value; this.domain = domain; }
    @Override public void accept(ModelVisitor visitor) { visitor.preVisit(this); visitor.postVisit(this); }
    @Override public <T> T fold(ModelFolder<T> folder) { return folder.fold(this); }
    @Override public String toString() { return String.valueOf(this.value); }
    // equals/hashCode include value + domain
}
```

Add `fold(Variable)` / `fold(Constant)` to `ModelFolder`, no-op visitor hooks to `ModelVisitor`, and temporary implementations in every concrete test/production folder while keeping the old leaf hooks. In `ModelToJavaTransformer`, render by `domain`: integer via `transform(((Number) value).longValue())`, real via `transform(((Number) value).doubleValue())`, string via `renderStringLiteral`, variable via the existing `_p_.name` path with boolean numeric rendering still driven by `variableTypes`.

Run `./gradlew test --tests 'teralizer.domain.TypedLeafTest'` — expect PASS.

- [x] **Step 3: Write failing cutover tests for producers and consumers.** Before changing producers, update or add tests so they expect typed leaves:
  - `SpfToModelTransformerSymbolNameTest`: `SymbolicInteger` → `Variable(name, TypeDomain.INTEGER)`, `SymbolicReal` → `Variable(name, TypeDomain.REAL)`, string symbols/constants → `TypeDomain.STRING` leaves.
  - `SpfToModelTransformerMathInvocationTest`, `SpfToModelTransformerStringInvocationTest`, and `StringDomainPlannerTest`: invocation receivers/args use typed leaves.
  - `ValueJsonAdapterTest` stays unchanged, but `InvocationJsonRoundTripTest` and a new/updated model JSON round-trip test assert `_type: "Variable"` / `_type: "Constant"` with `domain` and exact `value`.
  - `NumericDomainPlannerClauseTest` and `BooleanDomainPlannerTest`: numeric/boolean clauses built from typed leaves still consume the same clauses and emit the same recipes.

Run the updated focused tests — expect FAIL because production code still emits/consumes the six legacy leaf classes.

- [x] **Step 4: Cut producers and interpreters over to typed leaves.** Update:
  - `SpfToModelTransformer`: integer constants/symbols → `Constant(..., INTEGER)` / `Variable(..., INTEGER)`; real constants/symbols → `REAL`; string constants/symbols/builders → `STRING`.
  - `ValueToModelTransformer`: `Integer` → `Constant(value.longValue(), INTEGER)`, `Double` → `Constant(value, REAL)`, `String` → `Constant(value, STRING)`.
  - `VariableNameCollector`: collect `Variable.name` and remove old per-type hook dependence.
  - `StringDomainPlanner`: require `invocation.receiver instanceof Variable` with `domain == STRING`, and args `Constant` with `domain == STRING`.
  - `BooleanDomainPlanner`: read boolean path clauses from typed integer variables/constants.
  - `NumericClauseInterpreter` and its `AffineTerm`: read typed integer/real variables/constants; keep char/boolean modeled as `INTEGER` and continue using parameter declared types for domain-specific recipes.
  - `ModelToJsonTransformer` / `JsonToModelTransformer`: serialize typed leaves as `_type: "Variable"` with `name` + `domain`, and `_type: "Constant"` with `value` + `domain`; deserialize `Constant.value` by domain (`INTEGER` → `long`, `REAL` → `double`, `STRING` → `String`) rather than through generic `Object` so equality and planner casts keep exact value types.
  - `ModelToJavaTransformer.isStringExpression` / `isFloatingPoint`: use `Constant` / `Variable` domains plus existing invocation checks.

Run the focused tests from Step 3 — expect PASS.

- [x] **Step 5: Delete the six legacy leaf classes and hooks.** Remove `VariableInteger`, `VariableReal`, `VariableString`, `ConstantInteger`, `ConstantReal`, `ConstantString`; delete their `ModelFolder` and `ModelVisitor` hooks; remove JSON adapters/registrations; update `ModelFolderTest.folderDeclaresOneHookPerConcreteNode` to include only `Variable` and `Constant` for leaves; update every recording folder in tests to implement `fold(Variable)` and `fold(Constant)` only. Run `./gradlew compileTestJava` first; any remaining reference is a compile-guided fix.

Expected: `./gradlew compileTestJava` PASS, and `grep` for `VariableInteger|VariableReal|VariableString|ConstantInteger|ConstantReal|ConstantString` under `src/main` and `src/test` returns no matches.

- [x] **Step 6: Update goldens and remaining direct model construction.** Update `GoldenRenderingTest` to construct typed leaves. Expected strings in `src/test/resources/golden/rendering-baseline.json` should remain unchanged for Phase 3 unless rendering intentionally changed; if a string changes, run a generated-test compile/pass check before accepting it.

Run `./gradlew test --tests 'teralizer.transformer.GoldenRenderingTest' --tests 'teralizer.transformer.ModelToJavaTransformerFoldOrderTest' --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest'` — expect PASS.

- [x] **Step 7: Run focused and full verification.** Run:

```bash
./gradlew test \
  --tests 'teralizer.domain.TypedLeafTest' \
  --tests 'teralizer.domain.ModelFolderTest' \
  --tests 'teralizer.transformer.SpfToModelTransformerSymbolNameTest' \
  --tests 'teralizer.transformer.SpfToModelTransformerMathInvocationTest' \
  --tests 'teralizer.transformer.SpfToModelTransformerStringInvocationTest' \
  --tests 'teralizer.transformer.InvocationJsonRoundTripTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerFoldOrderTest' \
  --tests 'teralizer.jqwik.planning.NumericDomainPlannerClauseTest' \
  --tests 'teralizer.jqwik.planning.BooleanDomainPlannerTest' \
  --tests 'teralizer.jqwik.planning.StringDomainPlannerTest'
./gradlew test
```

Run the native SPF string tests with the same cleanup wrapper/trap used in Phase 2. Expected: focused tests, full tests, and native tests PASS; `git status --short settings.gradle` is clean.

- [x] **Step 8: Run the numeric/char/boolean behavioral guardrail.** Re-run the JARVIS scratch scorecard and census as in Phase 2. Acceptance: the run log contains `scoreboard run complete: no pipeline breakage`, hard-failure grep finds no non-JPF pipeline/build failures, expected raw-bits per-assertion JPF exclusions remain non-fatal, and `uv run --directory analysis python -m teralizer.jarvis_scoreboard --census` still reports 250 sound numeric/char/boolean successes.

- [x] **Step 9: Run branch handoff build.** Run `./gradlew build` after the full suite and guardrail.

- [x] **Step 10: Commit.** Commit the typed-leaf migration, deleted legacy leaves, updated tests, and Phase-3 checkboxes with subject `refactor(domain): unify typed expression leaves` and a body explaining why `TypeDomain` moved to `teralizer.domain`, why char/boolean still enter the model as integer leaves, and how the JARVIS guardrail was verified.


---

## Phase 4 — Delete legacy + finalize

Phase 4 is a cleanup/finalization pass over the unified model after Phases 1–3. It does not add new string capabilities beyond the Phase-1 additions; it makes the remaining contracts explicit, deletes the last legacy shims, and syncs the dependent planning docs.

**Fresh code-read findings (2026-07-01):**
- `Operator` now contains true operators only (`==`, comparisons, arithmetic, bitwise, shifts). No enum entry is scheduled for removal; the remaining legacy shape is `Operation` itself still accepting a `null` operand, with a test pinning that obsolete unary-call shape.
- `MethodCapability` still has only `method`, `staticQualifier`, `inputGeneratable`, and `outputRenderable`. As a result, `ModelToJavaTransformer` hardcodes string-returning invocations (`concat`, `trim`, `replace`, `toLowerCase`, `toUpperCase`) and math-return detection, while `StringDomainPlanner` hardcodes the input-generation methods in a `switch`.
- `StringOperationFilter` already consults `MethodCapabilities.isSupported`, but `SpfToModelTransformer` still maps some unsupported SPF string operators (`replaceFirst`, `replaceAll`, `substring`) to `Invocation` nodes that the renderer later rejects. Ingestion admission should refuse those via the registry instead of constructing an unrenderable model node.
- `TestGeneralizationTask` still regex-scrapes JSON for temporary `"INT_"`/`"REAL_"` symbols. That is the remaining pre-typed-leaf escape hatch and still misses string temporaries. It should walk the typed `Model` and collect `Variable(name, TypeDomain)` instead.
- `2026-06-30-partial-sound-string-support` still describes `SymbolicStringFunction`, old per-type leaves, and `trim`/`replace` as deferred/unrenderable. The overview still says Task 4b follows this refactor. Phase 4 must update those docs to the current model.

### Task 10: registry metadata drives rendering and string planning

**Files:** Modify `src/main/java/teralizer/jqwik/planning/MethodCapability.java`, `MethodCapabilities.java`, `StringDomainPlanner.java`, `src/main/java/teralizer/transformer/ModelToJavaTransformer.java`; tests in `MethodCapabilitiesTest.java`, `StringDomainPlannerTest.java`, `ModelToJavaTransformerInvocationTest.java`, and `ModelToJavaTransformerNonGeneralizableTest.java`.

- [x] **Step 1: Write failing registry-metadata tests.** Extend `MethodCapabilitiesTest` before production edits:
  - `equals`, `equalsIgnoreCase`, `startsWith`, `endsWith`, `contains`, and `isEmpty` have `returnDomain == TypeDomain.BOOLEAN`.
  - `trim`, `replace`, `toLowerCase`, `toUpperCase`, `concat`, and `String.valueOf` have `returnDomain == TypeDomain.STRING`.
  - `sqrt` and every `java.lang.Math` capability have `returnDomain == TypeDomain.REAL`.
  - `length`, `indexOf`, and `lastIndexOf` are supported for filtering, have `returnDomain == TypeDomain.INTEGER`, and stay `outputRenderable == false`.
  - input-constraint kinds are registry data: `equals -> EQUALITY`, `startsWith -> PREFIX`, `endsWith -> SUFFIX`, `contains -> CONTAINS`, `isEmpty -> EMPTY`, output-only transforms (`trim`, `replace`, `toLowerCase`, `toUpperCase`, `concat`) -> `NONE`.

Run:

```bash
./gradlew test --tests 'teralizer.jqwik.planning.MethodCapabilitiesTest'
```

Expected: FAIL because `MethodCapability` has no return-domain or input-constraint metadata yet.

- [x] **Step 2: Add capability metadata.** Extend `MethodCapability` with the metadata the consumers currently hardcode:

```java
public final class MethodCapability {
    public enum InputConstraintKind {
        NONE,
        EQUALITY,
        PREFIX,
        SUFFIX,
        CONTAINS,
        EMPTY
    }

    public final String method;
    public final String staticQualifier;
    public final TypeDomain receiverDomain; // null for static calls
    public final TypeDomain returnDomain;
    public final boolean inputGeneratable;
    public final boolean outputRenderable;
    public final InputConstraintKind inputConstraintKind;

    MethodCapability(
        String method,
        String staticQualifier,
        TypeDomain receiverDomain,
        TypeDomain returnDomain,
        boolean inputGeneratable,
        boolean outputRenderable,
        InputConstraintKind inputConstraintKind) {
        this.method = method;
        this.staticQualifier = staticQualifier;
        this.receiverDomain = receiverDomain;
        this.returnDomain = returnDomain;
        this.inputGeneratable = inputGeneratable;
        this.outputRenderable = outputRenderable;
        this.inputConstraintKind = inputConstraintKind;
    }
}
```

Update `MethodCapabilities` helper methods so string predicate calls set `receiverDomain = TypeDomain.STRING` and `returnDomain = TypeDomain.BOOLEAN`, string transform calls set `receiverDomain = TypeDomain.STRING` and `returnDomain = TypeDomain.STRING`, math static calls set `returnDomain = TypeDomain.REAL`, and `String.valueOf` sets `returnDomain = TypeDomain.STRING`. Keep the existing public lookup methods.

Run the MethodCapabilities test from Step 1 — expect PASS.

- [x] **Step 3: Route renderer type checks through capability metadata.** In `ModelToJavaTransformer`, replace `isStringReturningInvocation` and the hardcoded Math check in `isFloatingPoint` with a shared domain helper:

```java
private static TypeDomain expressionDomain(Expression expression) {
    if (expression instanceof Variable) {
        return ((Variable) expression).domain;
    }
    if (expression instanceof Constant) {
        return ((Constant) expression).domain;
    }
    if (expression instanceof Invocation) {
        MethodCapability capability = MethodCapabilities.get(((Invocation) expression).method);
        return capability == null || !capability.outputRenderable ? null : capability.returnDomain;
    }
    return null;
}
```

`isStringExpression` becomes `expressionDomain(expression) == TypeDomain.STRING`; the static-Math branch in `isFloatingPoint` becomes `expressionDomain(expression) == TypeDomain.REAL`. Keep the existing static qualifier validation in `fold(Invocation)`.

Run:

```bash
./gradlew test \
  --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerNonGeneralizableTest'
```

Expected: PASS; the rendered Java strings should not change.

- [x] **Step 4: Route `StringDomainPlanner` through capability metadata.** In `deriveConstraints`, look up `MethodCapabilities.get(invocation.method)` and ignore the clause unless the capability is non-null and `inputGeneratable`. Replace the string-method `switch` with `capability.inputConstraintKind`. Add `EMPTY` handling for `isEmpty()` with zero arguments so `s.isEmpty()` structurally consumes the clause and emits `Arbitraries.of("")` rather than relying only on the residual filter.

Add/extend `StringDomainPlannerTest` first:

```java
@Example
void isEmptyClauseCollapsesToEmptyStringAndConsumesClause() {
    Model model = new Invocation(new Variable("s", TypeDomain.STRING), null, "isEmpty", Collections.emptyList());
    List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("s", "java.lang.String"));
    ParameterGenerationPlan plan = new StringDomainPlanner().plan(
        new MethodParameter("java.lang.String", "s"),
        new PlanningContext(Collections.singletonList(new MethodParameter("java.lang.String", "s")), clauses));

    Assert.assertEquals("return net.jqwik.api.Arbitraries.of(\"\")", plan.getRecipe().emit());
    Assert.assertTrue(plan.getConsumedClauseIds().contains(0));
}
```

Run:

```bash
./gradlew test --tests 'teralizer.jqwik.planning.StringDomainPlannerTest'
```

Expected: FAIL before the planner change, PASS after it.

### Task 11: registry-gated ingestion and binary-only `Operation`

**Files:** Modify `src/main/java/teralizer/transformer/SpfToModelTransformer.java`, `src/main/java/teralizer/domain/Operation.java`; tests in `SpfToModelTransformerStringInvocationTest.java`, `ModelFolderTest.java`, and `OperatorTest.java`.

- [x] **Step 1: Write failing ingestion-admission tests.** Extend `SpfToModelTransformerStringInvocationTest` before production edits:

```java
@Example
void unsupportedOprlistStringOperatorIsRejectedAtIngestion() {
    StringExpression expression = new StringSymbolic("value_1_SYMSTRING")._subString(1);

    try {
        new SpfToModelTransformer().transform(expression);
        Assert.fail("expected unsupported substring to be refused before a Model node is built");
    } catch (UnsupportedSpfTermException expected) {
        Assert.assertTrue(expected.getMessage().contains("substring"));
    }
}
```

Run:

```bash
./gradlew test --tests 'teralizer.transformer.SpfToModelTransformerStringInvocationTest'
```

Expected: FAIL because `SUBSTRING` currently becomes `Invocation(..., "substring", ...)`.

- [x] **Step 2: Gate every string invocation constructed by SPF ingestion through `MethodCapabilities`.** Add helpers inside `ConstraintExpressionFactoryVisitor`:

```java
private static Invocation instanceInvocation(Expression receiver, String method, List<Expression> args) {
    MethodCapability capability = MethodCapabilities.get(method);
    if (capability == null || capability.staticQualifier != null || !capability.outputRenderable) {
        throw new UnsupportedSpfTermException("String method '" + method + "' is not admitted by MethodCapabilities.");
    }
    return new Invocation(receiver, null, method, args);
}

private static Invocation staticInvocation(String qualifier, String method, List<Expression> args) {
    MethodCapability capability = MethodCapabilities.get(method);
    if (capability == null || !qualifier.equals(capability.staticQualifier) || !capability.outputRenderable) {
        throw new UnsupportedSpfTermException("Static method '" + qualifier + "." + method + "' is not admitted by MethodCapabilities.");
    }
    return new Invocation(null, qualifier, method, args);
}
```

Use these helpers in `invocationForComparator` and both `invocationForOperator` overloads. Remove the `REPLACEFIRST`, `REPLACEALL`, and `SUBSTRING` construction arms; unsupported operators now fall through to a typed `UnsupportedSpfTermException` or fail the helper check. Keep `replace`, `trim`, `toLowerCase`, `toUpperCase`, `concat`, and `String.valueOf` admitted.

Run the test from Step 1 — expect PASS.

- [x] **Step 3: Make `Operation` binary-only.** Replace `ModelFolderTest.operationWithNullOperandFoldsWithoutExploding` with a failing invariant test:

```java
@Example
void operationRequiresTwoOperands() {
    try {
        new Operation(new Constant(4L, TypeDomain.INTEGER), Operator.PLUS, null);
        Assert.fail("operation must be binary after calls and negation moved to Invocation/Not");
    } catch (IllegalArgumentException expected) {
        Assert.assertTrue(expected.getMessage().contains("binary"));
    }
}
```

Then change `Operation`'s constructor to reject `left == null || right == null` with `IllegalArgumentException("Operation is binary; use Invocation or Not for unary/call expressions.")`, and simplify `toString()` to the binary branch. `SpfToModelTransformer`, `JsonToModelTransformer`, and all tests already construct binary operations in normal code.

Run:

```bash
./gradlew test --tests 'teralizer.domain.ModelFolderTest' --tests 'teralizer.domain.OperatorTest'
```

Expected: PASS.

### Task 12: typed model traversal for symbolic temporaries

**Files:** Create `src/main/java/teralizer/transformer/VariableDescriptorCollector.java`; modify `src/main/java/teralizer/processing/task/TestGeneralizationTask.java`; tests in `src/test/java/teralizer/transformer/VariableDescriptorCollectorTest.java` and `src/test/java/teralizer/processing/task/TestGeneralizationTaskTest.java`.

- [x] **Step 1: Write failing collector tests.** Add `VariableDescriptorCollectorTest`:

```java
@Example
void collectsVariableNamesWithDomainsAcrossNestedModels() {
    Model input = new Operation(
        new Variable("INT_1", TypeDomain.INTEGER),
        Operator.GT,
        new Constant(0L, TypeDomain.INTEGER));
    Model output = new Invocation(
        new Variable("STR_2", TypeDomain.STRING),
        null,
        "trim",
        Collections.emptyList());

    Map<String, TypeDomain> variables = VariableDescriptorCollector.collect(input, output);

    Assert.assertEquals(TypeDomain.INTEGER, variables.get("INT_1"));
    Assert.assertEquals(TypeDomain.STRING, variables.get("STR_2"));
}
```

Run:

```bash
./gradlew test --tests 'teralizer.transformer.VariableDescriptorCollectorTest'
```

Expected: FAIL because the collector does not exist.

- [x] **Step 2: Implement `VariableDescriptorCollector`.** Implement a small `ModelVisitor` over typed leaves:

```java
package teralizer.transformer;

import java.util.LinkedHashMap;
import java.util.Map;
import teralizer.domain.Model;
import teralizer.domain.ModelVisitor;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public final class VariableDescriptorCollector extends ModelVisitor {
    private final Map<String, TypeDomain> variables = new LinkedHashMap<>();

    public static Map<String, TypeDomain> collect(Model... models) {
        VariableDescriptorCollector collector = new VariableDescriptorCollector();
        for (Model model : models) {
            if (model != null) {
                model.accept(collector);
            }
        }
        return new LinkedHashMap<>(collector.variables);
    }

    @Override
    public void preVisit(Variable variable) {
        this.variables.putIfAbsent(variable.name, variable.domain);
    }
}
```

Run the collector test — expect PASS.

- [x] **Step 3: Write failing temporary-parameter test.** Add to `TestGeneralizationTaskTest` a package-private helper test:

```java
@Example
void recoversTypedTemporaryParametersFromInputAndOutputModels() {
    List<MethodParameter> declared = Arrays.asList(new MethodParameter("int", "x"));
    Model input = new Operation(new Variable("INT_1", TypeDomain.INTEGER), Operator.GT, new Constant(0L, TypeDomain.INTEGER));
    Model output = new Invocation(new Variable("STR_2", TypeDomain.STRING), null, "trim", Collections.emptyList());

    List<MethodParameter> recovered = TestGeneralizationTask.collectTemporaryParameters(input, output, declared);

    Assert.assertTrue(recovered.stream().anyMatch(p -> p.getName().equals("INT_1") && p.getType().equals("int")));
    Assert.assertTrue(recovered.stream().anyMatch(p -> p.getName().equals("STR_2") && p.getType().equals("java.lang.String")));
    Assert.assertFalse(recovered.stream().anyMatch(p -> p.getName().equals("x")));
}
```

Run:

```bash
./gradlew test --tests 'teralizer.processing.task.TestGeneralizationTaskTest'
```

Expected: FAIL because `collectTemporaryParameters` does not exist and the production path still uses the JSON regex.

- [x] **Step 4: Replace the regex scrape in `TestGeneralizationTask`.** Add a package-private static helper on `TestGeneralizationTask`:

```java
static List<MethodParameter> collectTemporaryParameters(
    Model inputModel,
    Model outputModel,
    List<MethodParameter> testedMethodParameters) {
    Set<String> declared = testedMethodParameters.stream()
        .map(MethodParameter::getName)
        .collect(Collectors.toSet());
    return VariableDescriptorCollector.collect(inputModel, outputModel).entrySet().stream()
        .filter(entry -> !declared.contains(entry.getKey()))
        .map(entry -> new MethodParameter(javaTypeForTemporary(entry.getValue()), entry.getKey()))
        .collect(Collectors.toList());
}

private static String javaTypeForTemporary(TypeDomain domain) {
    switch (domain) {
        case INTEGER:
            return "int";
        case REAL:
            return "double";
        case STRING:
            return "java.lang.String";
        default:
            throw new IllegalArgumentException("Unsupported temporary domain " + domain);
    }
}
```

Use it where the `Pattern`/`Matcher` block currently builds `temporaryParameters`; delete the regex, `Pattern`, and `Matcher` imports. Run the TestGeneralizationTask test — expect PASS.

### Task 13: docs, verification, and final commit

**Files:** Modify `docs/plans/2026-06-30-partial-sound-string-support.md`, `docs/plans/2026-06-26-teralizer-overview.md`, `docs/plans/2026-06-30-unified-expression-model-plan.md`, and regenerated `docs/plans/INDEX.md`.

- [x] **Step 1: Update dependent docs to current truth.** In `2026-06-30-partial-sound-string-support.md`, mark Task 4b implemented after the typed traversal lands; remove stale references that describe `SymbolicStringFunction`, `VariableString`/`ConstantString`, and `trim`/`replace` as unrenderable/deferred. Keep corpus verification (Task 7) active and gated on MUT-id. In the overview, move the current focus past the unified-expression refactor once Phase 4 verifies, leaving static MUT-id as the next implementation item and string corpus verification still gated on MUT-id.

- [x] **Step 2: Run focused tests.** Run:

```bash
./gradlew test \
  --tests 'teralizer.jqwik.planning.MethodCapabilitiesTest' \
  --tests 'teralizer.jqwik.planning.StringDomainPlannerTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerInvocationTest' \
  --tests 'teralizer.transformer.ModelToJavaTransformerNonGeneralizableTest' \
  --tests 'teralizer.transformer.SpfToModelTransformerStringInvocationTest' \
  --tests 'teralizer.transformer.VariableDescriptorCollectorTest' \
  --tests 'teralizer.processing.task.TestGeneralizationTaskTest' \
  --tests 'teralizer.domain.ModelFolderTest' \
  --tests 'teralizer.domain.OperatorTest'
```

Expected: PASS.

- [x] **Step 3: Run full unit verification.** Run:

```bash
./gradlew test
```

Expected: PASS.

- [x] **Step 4: Run native SPF string tests with cleanup.** Use the cleanup wrapper/trap pattern from Phases 2–3 so `settings.gradle` is restored even on failure. Run the native tests for `TestSymbolicStringCaseChange`, `TestSymbolicStringSymcrete`, `TestSymbolicStringIsEmpty`, and `TestSymbolicStringEqualsIgnoreCase`. Expected: PASS and `git status --short settings.gradle` has no output.

- [x] **Step 5: Run the JARVIS behavioral guardrail with PIT disabled.** Because the branch now has the PIT-disable flag available, run the scorecard with PIT stages disabled:

```bash
GRADLE_OPTS=-Dteralizer.pitest.enabled=false bash scripts/run-jarvis-scoreboard.sh --reset-db --prepare-fixtures 2>&1 | tee /tmp/teralizer-jarvis-scoreboard-phase4.log
```

Then check the log for `scoreboard run complete: no pipeline breakage`, check that no hard build/pipeline failures appear, and run:

```bash
uv run --directory analysis python -m teralizer.jarvis_scoreboard --census
```

Acceptance: no non-JPF pipeline/build failures, PIT tasks recorded as disabled skips, and the census still reports 250 sound numeric/char/boolean successes.

- [x] **Step 6: Run branch handoff build.** Run:

```bash
./gradlew build
```

Expected: PASS.

- [x] **Step 7: Validate planning docs.** Run:

```bash
omp-plans index && omp-plans check
```

Expected: PASS.

- [x] **Step 8: Commit Phase 4.** Commit the doc sync, Phase-4 verification checkboxes, and regenerated index with subject `docs(plans): finalize unified expression model` and a body explaining that registry metadata now drives rendering/planning/admission, temporary recovery now walks typed model variables instead of JSON, and the no-PIT JARVIS guardrail was used.

---

## Self-review

- **Spec coverage:** node model = Tasks 1, 9 (+ `Operator` shrink across 3, 6, 8); `Invocation`/`Not` = 1, 3, 4; capability registry = 2 (+ wired in 5, 6, 8, 10); total ingestion = 4, 8; layer contracts = 3 (render), 4/8 (ingestion), 5 (planner/screen); migration phases 1–4 map 1:1; testing = golden harness (7) + per-phase gates + native tests + behavioral guardrail (8).
- **Sequencing:** string-first (Phase 1) is lowest-risk and unlocks the leftover ops; numeric (Phase 2) is guardrailed by goldens + the 250 generalizations; leaves (Phase 3) last; delete (Phase 4).
- **Deferred (unchanged):** Tier-3 string ops (`compareTo`, regex, SIOOBE `substring`/`charAt`), arrays, and MUT-id all remain out of scope.
