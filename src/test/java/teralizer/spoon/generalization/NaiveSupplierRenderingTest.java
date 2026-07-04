package teralizer.spoon.generalization;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import teralizer.domain.MethodParameter;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.Value;

public class NaiveSupplierRenderingTest {

    @Example
    void wrapsCombinedTupleNotIndividualParametersForMultipleParameters() {
        MethodParameter a = new MethodParameter("int", "a");
        MethodParameter b = new MethodParameter("int", "b");
        Map<String, Value> arguments = new HashMap<>();
        arguments.put("a", new PrimitiveValue("int", 7));
        arguments.put("b", new PrimitiveValue("int", 9));

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
    void wrapsFilteredArbitrarySoDedupSeesOnlyExecutableTuples() {
        MethodParameter x = new MethodParameter("int", "x");
        Map<String, Value> arguments = new HashMap<>();
        arguments.put("x", new PrimitiveValue("int", 2));

        CtClass<?> supplierClass = NaiveTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            arguments,
            "(_p_.x > 0)"
        );
        String code = supplierClass.toString();

        Assert.assertTrue("seed wrapper must contain the residual filter, not sit below it",
            firstValueWrapperArgumentContainsFilter(code));
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
    private static boolean firstValueWrapperArgumentContainsFilter(String code) {
        int start = code.indexOf("new FirstValueArbitrary<TestParameters>");
        if (start < 0) {
            return false;
        }
        int open = code.indexOf('(', start);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return code.substring(open, i).contains(".filter(");
                }
            }
        }
        return false;
    }

}
