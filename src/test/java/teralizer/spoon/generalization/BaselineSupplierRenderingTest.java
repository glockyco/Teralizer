package teralizer.spoon.generalization;

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

public class BaselineSupplierRenderingTest {

    @Example
    void parenthesizesBoxedLongMinValueAfterReferenceCast() {
        String code = renderSupplierFor(
            new MethodParameter("java.lang.Long", "value"),
            new PrimitiveValue("java.lang.Long", Long.MIN_VALUE));

        // JLS 15.16 reference-type casts take UnaryExpressionNotPlusMinus; without these
        // operand parentheses, the '-' re-parses as binary minus and detaches the only literal
        // magnitude that Java permits solely as the operand of unary minus.
        Assert.assertTrue("cast operand must open with parentheses", code.contains(") (-"));
        Assert.assertTrue("Long.MIN_VALUE must stay attached to unary minus",
            code.contains("(java.lang.Long) (-9223372036854775808L)"));
        Assert.assertFalse("must not render the javac-rejected reference-cast shape",
            code.contains("(java.lang.Long) -9223372036854775808L"));
    }

    @Example
    void parenthesizesBoxedIntegerMinValueAfterReferenceCast() {
        String code = renderSupplierFor(
            new MethodParameter("java.lang.Integer", "value"),
            new PrimitiveValue("java.lang.Integer", Integer.MIN_VALUE));

        Assert.assertTrue("cast operand must open with parentheses", code.contains(") (-"));
        Assert.assertTrue("Integer.MIN_VALUE must stay attached to unary minus",
            code.contains("(java.lang.Integer) (-2147483648)"));
        Assert.assertFalse("must not render the javac-rejected reference-cast shape",
            code.contains("(java.lang.Integer) -2147483648"));
    }

    private static String renderSupplierFor(MethodParameter parameter, Value argument) {
        Map<String, Value> arguments = new HashMap<>();
        arguments.put(parameter.getName(), argument);

        CtClass<?> supplierClass = BaselineTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(), Collections.singletonList(parameter), arguments);
        return supplierClass.toString();
    }
}
