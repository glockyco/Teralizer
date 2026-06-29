package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NaiveSupplierRenderingTest {

    @Example
    void wrapsCombinedTupleNotIndividualParametersForMultipleParameters() {
        MethodParameter a = new MethodParameter("int", "a");
        MethodParameter b = new MethodParameter("int", "b");
        Map<String, MethodArgument> arguments = new HashMap<>();
        arguments.put("a", new MethodArgument("int", "7"));
        arguments.put("b", new MethodArgument("int", "9"));

        CtClass<?> supplierClass = NaiveTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(), Arrays.asList(a, b), arguments, null);
        String code = supplierClass.toString();

        // Tuple-level injection: the exact original combination is the seed, wrapped once.
        Assert.assertTrue("must inject the original tuple at the tuple level",
            code.contains("FirstValueArbitrary<TestParameters>(new TestParameters("));
        // The flatMap-collapse bug: per-parameter FirstValueArbitrary pins inner params to their firstValue.
        Assert.assertFalse("must not wrap individual parameters",
            code.contains("FirstValueArbitrary<Integer"));
    }

    @Example
    void omitsSeedInjectionWhenNoOriginalArguments() {
        MethodParameter a = new MethodParameter("int", "a");
        MethodParameter b = new MethodParameter("int", "b");

        CtClass<?> supplierClass = NaiveTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(), Arrays.asList(a, b), Collections.emptyMap(), null);
        String code = supplierClass.toString();

        Assert.assertFalse("no original values -> no seed injection", code.contains("FirstValueArbitrary"));
    }
}
