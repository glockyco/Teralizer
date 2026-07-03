package teralizer.spoon.generalization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import teralizer.domain.Constant;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;

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
        Assert.assertFalse("Long remains an unbridged boxed cast",
            code.contains("(java.lang.Long) (long) (-9223372036854775808L)"));
        Assert.assertFalse("must not render the javac-rejected reference-cast shape",
            code.contains("(java.lang.Long) -9223372036854775808L"));
    }

    @Example
    void bridgesBoxedShortCastThroughPrimitiveNarrowingCast() {
        String code = renderSupplierFor(
            new MethodParameter("java.lang.Short", "value"),
            new PrimitiveValue("java.lang.Short", Short.valueOf((short) -129)));

        Assert.assertTrue("boxed Short cast must narrow the int literal before boxing",
            code.contains("(java.lang.Short) (short) (-129)"));
        Assert.assertFalse("must not rely on an int-to-Short boxing conversion",
            code.contains("(java.lang.Short) (-129)"));
    }

    @Example
    void bridgesBoxedByteCastThroughPrimitiveNarrowingCast() {
        String code = renderSupplierFor(
            new MethodParameter("java.lang.Byte", "value"),
            new PrimitiveValue("java.lang.Byte", Byte.valueOf((byte) 3)));

        Assert.assertTrue("boxed Byte cast must narrow the int literal before boxing",
            code.contains("(java.lang.Byte) (byte) (3)"));
        Assert.assertFalse("must not rely on an int-to-Byte boxing conversion",
            code.contains("(java.lang.Byte) (3)"));
    }

    @Example
    void bridgesBoxedCharacterCastThroughPrimitiveNarrowingCast() {
        String code = renderSupplierFor(
            new MethodParameter("java.lang.Character", "value"),
            new PrimitiveValue("java.lang.Character", Character.valueOf('A')));

        Assert.assertTrue("boxed Character cast must pass through a char cast before boxing",
            code.contains("(java.lang.Character) (char) ((char) 65)"));
        Assert.assertFalse("must not rely on a direct boxed Character cast",
            code.contains("(java.lang.Character) ((char) 65)"));
    }

    @Example
    void parenthesizesBoxedIntegerMinValueAfterReferenceCast() {
        String code = renderSupplierFor(
            new MethodParameter("java.lang.Integer", "value"),
            new PrimitiveValue("java.lang.Integer", Integer.MIN_VALUE));

        Assert.assertTrue("cast operand must open with parentheses", code.contains(") (-"));
        Assert.assertTrue("Integer.MIN_VALUE must stay attached to unary minus",
            code.contains("(java.lang.Integer) (-2147483648)"));
        Assert.assertFalse("Integer remains an unbridged boxed cast",
            code.contains("(java.lang.Integer) (int) (-2147483648)"));
        Assert.assertFalse("must not render the javac-rejected reference-cast shape",
            code.contains("(java.lang.Integer) -2147483648"));
    }

    @Example
    void bridgesNaiveTupleSeedBoxedShortCastThroughPrimitiveNarrowingCast() {
        String code = renderNaiveSupplierFor(
            new MethodParameter("java.lang.Short", "value"),
            new PrimitiveValue("java.lang.Short", Short.valueOf((short) -129)));

        Assert.assertTrue("naive tuple seed must narrow before boxing",
            code.contains("(java.lang.Short) (short) (-129)"));
        Assert.assertFalse("naive tuple seed must not rely on int-to-Short boxing",
            code.contains("(java.lang.Short) (-129)"));
    }

    @Example
    void bridgesNumericPlannerBoxedShortSeedsThroughPrimitiveNarrowingCast() {
        MethodParameter parameter = new MethodParameter("java.lang.Short", "value");
        Map<String, Value> arguments = new HashMap<>();
        arguments.put(parameter.getName(), new PrimitiveValue("java.lang.Short", Short.valueOf((short) -129)));
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(parameter),
            arguments,
            impossibleShortBounds(parameter.getName()));

        Assert.assertEquals("(java.lang.Short) (short) (-129)",
            plan.getParameterPlans().get(0).getOriginalValue());
        String recipe = plan.getParameterPlans().get(0).getRecipe().emit();
        Assert.assertTrue("numeric fallback seed must narrow before boxing",
            recipe.contains("Arbitraries.just((java.lang.Short) (short) (-129))"));
        Assert.assertFalse("numeric fallback seed must not rely on int-to-Short boxing",
            recipe.contains("Arbitraries.just((java.lang.Short) (-129))"));
    }

    private static String renderSupplierFor(MethodParameter parameter, Value argument) {
        Map<String, Value> arguments = new HashMap<>();
        arguments.put(parameter.getName(), argument);

        CtClass<?> supplierClass = BaselineTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(), Collections.singletonList(parameter), arguments);
        return supplierClass.toString();
    }

    private static String renderNaiveSupplierFor(MethodParameter parameter, Value argument) {
        Map<String, Value> arguments = new HashMap<>();
        arguments.put(parameter.getName(), argument);

        CtClass<?> supplierClass = NaiveTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(), Collections.singletonList(parameter), arguments, "true");
        return supplierClass.toString();
    }

    private static Operation impossibleShortBounds(String parameterName) {
        return new Operation(
            new Operation(
                new Variable(parameterName, TypeDomain.INTEGER),
                Operator.GT,
                new Constant((long) 100, TypeDomain.INTEGER)),
            Operator.AND,
            new Operation(
                new Variable(parameterName, TypeDomain.INTEGER),
                Operator.LT,
                new Constant((long) 0, TypeDomain.INTEGER)));
    }
}
