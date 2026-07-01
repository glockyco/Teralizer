package teralizer.transformer;

import gov.nasa.jpf.symbc.numeric.MathFunction;
import gov.nasa.jpf.symbc.numeric.MathRealExpression;
import gov.nasa.jpf.symbc.numeric.SymbolicReal;
import java.util.Arrays;
import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Invocation;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class SpfToModelTransformerMathInvocationTest {

    @Example
    void unaryMathRealExpressionBecomesStaticInvocation() {
        MathRealExpression expression = new MathRealExpression(
            MathFunction.SQRT,
            new SymbolicReal("x_1_SYMREAL"));

        Assert.assertEquals(
            new Invocation(null, "java.lang.Math", "sqrt", Collections.singletonList(new Variable("x", TypeDomain.REAL))),
            new SpfToModelTransformer().transform(expression));
    }

    @Example
    void binaryMathRealExpressionPreservesArgumentOrder() {
        MathRealExpression expression = new MathRealExpression(
            MathFunction.POW,
            new SymbolicReal("x_1_SYMREAL"),
            new SymbolicReal("y_2_SYMREAL"));

        Assert.assertEquals(
            new Invocation(null, "java.lang.Math", "pow", Arrays.asList(new Variable("x", TypeDomain.REAL), new Variable("y", TypeDomain.REAL))),
            new SpfToModelTransformer().transform(expression));
    }
}
