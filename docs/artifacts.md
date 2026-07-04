# Generated artifacts — a worked example

Teralizer's output is code that generates code. This file shows one real artifact family so a
reader never has to run the pipeline just to see what it produces. Every listing below is a
verbatim pipeline output for the `expression-slice` verification fixture (assertion
`assertTrue(ExpressionSliceCut.intCompare(4, 1) > 0)`, generalization id 7), except where a
`[... trimmed ...]` marker says otherwise. Regenerate the family with
`scripts/run-verification-corpus.sh --only expression-slice`. The artifacts land under
`data/verification/expression-slice/project-id-*/`.

The family covers four stages: the instrumented wrapper SPF analyzes, the driver that runs it,
the JPF config that binds them, and the generalized jqwik test the pipeline emits at the end.

## 1. Instrumented class (`jpf-data/test/**/_*_Instrumented_*_Test.java`)

`JpfInstrumentationTask` clones the test class, deletes every other test method, and adds a
wrapper method whose parameters are the recipe's input sites. For an expression recipe the
wrapper body IS the asserted expression with sites lifted, and the original assertion is
rewritten to call the wrapper.

```java
public class _ExpressionSliceCutTest_Instrumented_operatorCompositeOverCallsIsAdmitted_7_Test {
    @Test
    public void operatorCompositeOverCallsIsAdmitted() {
        assertTrue(this.intCompare_7(4, 1));
    }

    public boolean intCompare_7(int site0, int site1) {
        return ExpressionSliceCut.intCompare(site0, site1) > 0;
    }
}
```

For contrast, a T0 invocation recipe (`assertEquals(7, add(3, 4))`) wraps just the call, and the
wrapper's return type comes from the assertion context (`assertEquals(long, long)` here):

```java
    @Test
    public void directInvocationStillGeneralizes() {
        assertEquals(7, this.add_3(3, 4));
    }

    public long add_3(int a, int b) {
        return ExpressionSliceCut.add(a, b);
    }
```

Instance-method recipes additionally receive the receiver as a `_target_` parameter, and any
test-method locals the cloned expression still references are passed as concrete `_local_*`
parameters (see `JpfInstrumentationTask.collectLiftableLocals`).

## 2. Driver class (`jpf-data/test/**/_*_Driver_*.java`)

The Velocity-generated entry point JPF executes: construct the instrumented class, run
`@Before` methods when the test class has them, invoke the test method. The concrete path the
test takes through the wrapper is the path SPF collects constraints along.

```java
public class _ExpressionSliceCutTest_Driver_intCompare_7 {
    public static void main(String[] args) throws Throwable {
        _ExpressionSliceCutTest_Instrumented_operatorCompositeOverCallsIsAdmitted_7_Test instance =
            new _ExpressionSliceCutTest_Instrumented_operatorCompositeOverCallsIsAdmitted_7_Test();
        instance.operatorCompositeOverCallsIsAdmitted();
    }
}
```

## 3. JPF config (`jpf-data/specs/<test>.<assertionId>.jpf`)

Velocity-generated from `jpf-config.vm`. The load-bearing lines: `symbolic.method` marks the
WRAPPER's parameters symbolic (`sym`; `_target_`/`_local_*` stay `con`);
`symbolic.collect_constraints=true` is constraint-collection mode (follow the concrete path,
record the PC — never explore). The `test_generalization.*` keys parameterize the listener,
including `expression_recipe` (wrapper-exit capture) and the four spec-output paths.

```properties
target=teralizer.verification.expressionslice._ExpressionSliceCutTest_Driver_intCompare_7
symbolic.method=teralizer.verification.expressionslice._ExpressionSliceCutTest_Instrumented_operatorCompositeOverCallsIsAdmitted_7_Test.intCompare_7(sym#sym)
symbolic.collect_constraints=true
symbolic.strings=false
symbolic.dp=z3
test_generalization.expression_recipe=true
test_generalization.tested_method=teralizer.verification.expressionslice.ExpressionSliceCut.intCompare
test_generalization.instrumented_method=teralizer.verification.expressionslice._ExpressionSliceCutTest_Instrumented_operatorCompositeOverCallsIsAdmitted_7_Test.intCompare_7
test_generalization.input_specification_path=data/verification/expression-slice/project-id-1/jpf-data/specs/....symbolic.input.json
[... trimmed: classpath, limits, report wiring ...]
```

The listener writes four JSON files next to the config: concrete input tuple, concrete output,
symbolic input spec (the path condition as a Model tree), symbolic output spec (the return
expression, or `null` when no symbolic attr survived — see `docs/architecture.md`
§Cross-stage contracts, extraction telemetry).

## 4. Generalized test (`teralizer-data/tests/<VARIANT>/**/_*_Generalized_*_Test.java`)

`TestGeneralizationTask` clones the test class again and turns the one assertion into a jqwik
property. The property method — the part that matters — for generalization 7:

```java
@net.jqwik.api.Property(edgeCases = net.jqwik.api.EdgeCasesMode.FIRST, seed = "0",
    shrinking = net.jqwik.api.ShrinkingMode.OFF, tries = 100)
@net.jqwik.api.lifecycle.AddLifecycleHook(JqwikValueRecorder.LimitedFilterMissesHook.class)
public void operatorCompositeOverCallsIsAdmitted(
    @net.jqwik.api.ForAll(supplier = TestParametersSupplier.class) TestParameters _p_) {
    JqwikValueRecorder.record(_p_);
    assertTrue(ExpressionSliceCut.intCompare(_p_.site0, _p_.site1) > 0);
}
```

The asserted expression survives verbatim with sites replaced by `_p_.<site>` reads. The
supplier encodes the path condition by construction where a planner recipe exists and filters
the rest (the residual predicate). Here SPF captured `site0 >= site1 && site0 > site1`, the
planner turned the var/var comparison into a dependent bound on `site1`, and the residual
filter re-checks the full predicate:

```java
public static class TestParametersSupplier implements net.jqwik.api.ArbitrarySupplier<TestParameters> {
    public net.jqwik.api.Arbitrary<TestParameters> get() {
        return new FirstValueArbitrary<TestParameters>(new TestParameters((int) (4), (int) (1)),
            get_site0().flatMap(...)
            .filter(_p_ -> (_p_.site0 >= _p_.site1) && (_p_.site0 > _p_.site1)));
    }
    private net.jqwik.api.Arbitrary<Integer> get_site0() {
        return net.jqwik.api.Arbitraries.integers();
    }
    private net.jqwik.api.Arbitrary<Integer> get_site1(final int site0) {
        [... trimmed: bound assembly ...]
        java.util.List<Integer> site1UpperBounds = java.util.Arrays.asList(site1DefaultMax, (int) (site0), (int) (site0-1));
        return net.jqwik.api.Arbitraries.integers().between(site1Min, site1Max);
    }
}
```

Three nested support classes complete the file:
- `TestParameters` — one public field per generated site. This is `_p_` in the property.
- `FirstValueArbitrary` — emits the captured concrete tuple as the first edge case, then
  dedups random draws. It overrides both `generator` overloads because the engine's two-arg
  default would re-inject edge cases past the dedup — a leak found by fixture, not review.
- `JqwikValueRecorder` — the ~200-line telemetry harness (value rows, outcome sidecar,
  filter-exhaustion remap hook). It is inlined into every generated file today. The
  harness-support-artifact spec extracts it into a precompiled jar.

## Reading the family against the DB

The `generalization` row for this family: `is_included=true`, `output_spec_class=NULL_CONCRETE`
(boolean-in-PC, licensed by `WideningLicense`), `total/used_constraint_count=2/2` (both clauses
consumed by the dependent bound), and its `jqwik_property_execution` row: `diagnostic_kind=FULL`,
`tries=100`, `distinct_tuples=100`.
