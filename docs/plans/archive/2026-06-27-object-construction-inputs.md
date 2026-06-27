---
title: Object Construction Inputs
type: spec
status: implemented
created: 2026-06-27
parent: 2026-06-26-beat-jarvis-phase1
archived: 2026-06-27
---

# Object Construction Inputs

## Goal

Allow Teralizer to generalize fixed-arity inline constructor arguments when the constructor arguments are already supported scalar inputs.

Example target:

```java
assertTrue(Subject.contains(new Interval(1, 10), 5));
```

Teralizer should treat `new Interval(1, 10)` as two symbolic scalar inputs for JPF and generated jqwik tests, then reconstruct the object where the tested method expects its declared object parameter.

## Scope

Supported:

- Inline `CtConstructorCall<?>` expressions used directly as tested method arguments.
- Fixed-arity constructors whose actual arguments are all in `Configuration.SUPPORTED_TYPES`.
- Constructor arguments that are already concrete expressions Teralizer can render through `MethodArgument` / `ModelToJavaTransformer`.
- Mixing direct scalar tested-method arguments with flattened constructor arguments in the same call.

Out of scope:

- Receiver-object generalization.
- Nested constructor graphs.
- Arrays, collections, varargs, builders, factories, setters, and mutable post-construction state.
- Constructors whose arguments include unsupported types or unresolved types.
- Persisting new database columns for this Phase-1 path.

## Design

Introduce one shared helper for deriving the generalizable input surface from a tested method declaration plus the concrete tested method call.

Conceptual type:

```java
final class GeneralizableInput {
    enum Kind { DIRECT_ARGUMENT, CONSTRUCTOR_ARGUMENT }

    private final Kind kind;
    private final int methodArgumentIndex;
    private final int constructorArgumentIndex;
    private final MethodParameter parameter;
    private final MethodArgument concreteArgument;
}
```

Naming rule for constructor inputs:

```text
_ctor_<methodParameterName>_<constructorArgumentIndex>_<constructorParameterNameOrArgN>
```

Examples:

- `Subject.contains(new Interval(1, 10), 5)` with method parameter `interval` becomes `_ctor_interval_0_lower`, `_ctor_interval_1_upper`, and direct `value`.
- If constructor parameter names are unavailable, use `_ctor_interval_0_arg0`, `_ctor_interval_1_arg1`.

The helper must be used by all consumers that need the input surface:

- `ParameterTypeFilter`: accept an assertion if the helper returns at least one supported direct or constructor-derived input.
- `JpfInstrumentationTask`: create instrumented method parameters from helper inputs, flatten constructor calls in the generated invocation, and reconstruct constructor calls inside the instrumented method body.
- `TestGeneralizationListener`: capture concrete input values from the instrumented method frame, not the tested method frame, so flattened constructor inputs remain aligned with JPF symbolic variable names.
- `TestGeneralizationTask`: create `TestParameters` fields from helper inputs and replace generated test call arguments with `_p_.<syntheticName>` inside constructor calls.
- `VariableConstraintExtractor`: no schema change; it already keys constraints by JPF variable name, so the synthetic names flow through unchanged.

The scouts identified a persisted `constructor_specs` JSON column as an alternative. Phase 1 does not add it: every required reconstruction fact is derivable from the persisted source paths and Spoon model, while schema churn would require jOOQ regeneration and migration work that is not needed for the JARVIS Table-2 inline-construction cases.

## Instrumentation behavior

For direct scalar arguments, keep the current behavior.

For constructor arguments:

1. Do not add the original object parameter to the instrumented method signature.
2. Add one instrumented method parameter per supported constructor argument.
3. In the original test method rewrite, call the instrumented method with the constructor argument expressions, not the constructed object.
4. In the instrumented method body, reconstruct a local object from the synthetic parameters and pass that local variable to the tested method call.
5. Leave non-generalizable tested method arguments unchanged only when they are required concrete context; reject cases where no generalizable input remains.

## Listener behavior

`TestGeneralizationListener` currently writes concrete input values from the tested method's JVM frame. That frame sees the reconstructed object, not the flattened constructor inputs. The listener must instead cache the instrumented method's argument names, types, and values when `methodEntered` matches `instrumentedMethodSpec`, then write that cached list when the tested method exits. Direct-scalar cases keep the same observable JSON because the instrumented and tested method signatures match there.

## Generation behavior

For generated jqwik tests:

1. Build `TestParameters` from the same helper-derived input list.
2. Build BASELINE/NAIVE/IMPROVED arbitraries from the flattened concrete inputs and SPF constraints.
3. Rewrite direct scalar call arguments to `_p_.<name>` as today.
4. Rewrite inline constructor-call arguments by replacing each constructor argument with `_p_.<syntheticName>`.
5. Keep the constructor expression itself in the generated test so the tested method still receives its declared object type.

## Required tests

Add unit tests around the helper and generated rewrites before production changes:

- Direct scalar arguments still derive the current `MethodParameter` names and concrete `MethodArgument` values.
- `new Interval(1, 10)` derives two synthetic inputs with stable names, scalar types, and concrete values.
- Mixed constructor + scalar arguments preserve input order for JPF variable ordering.
- Unsupported constructor arguments make the constructor argument non-generalizable rather than silently symbolic.
- Generated source for `assertTrue(Subject.contains(new Interval(1, 10), 5))` contains `new Interval(_p_._ctor_interval_0_lower, _p_._ctor_interval_1_upper)` and direct scalar `_p_.value`.

Add one scratch end-to-end smoke project before claiming the plan row complete:

```java
public final class Interval {
    private final int lower;
    private final int upper;

    public Interval(int lower, int upper) {
        this.lower = lower;
        this.upper = upper;
    }

    public boolean contains(int value) {
        return lower <= value && value <= upper;
    }
}

public final class Subject {
    public static boolean contains(Interval interval, int value) {
        return interval.contains(value);
    }
}

@Test
public void valueInsideInterval() {
    assertTrue(Subject.contains(new Interval(1, 10), 5));
}
```

Expected scratch result: all `EXECUTE_JPF` tasks succeed, generated tests compile and pass, and the symbolic input JSON contains constraints over `_ctor_*` variables.
