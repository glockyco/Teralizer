package teralizer.transformer;

import gov.nasa.jpf.symbc.concolic.FunctionExpression;
import gov.nasa.jpf.symbc.mixednumstrg.SpecialIntegerExpression;
import gov.nasa.jpf.symbc.mixednumstrg.SpecialRealExpression;
import gov.nasa.jpf.symbc.numeric.BinaryLinearIntegerExpression;
import gov.nasa.jpf.symbc.numeric.Comparator;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.IntegerConstant;
import gov.nasa.jpf.symbc.numeric.LinearIntegerConstraint;
import gov.nasa.jpf.symbc.numeric.RawDoubleBitsExpression;
import gov.nasa.jpf.symbc.numeric.SymbolicInteger;
import gov.nasa.jpf.symbc.numeric.SymbolicReal;
import gov.nasa.jpf.symbc.string.StringConstant;
import gov.nasa.jpf.symbc.string.StringSymbolic;
import gov.nasa.jpf.symbc.string.SymbolicIndexOfCharInteger;
import gov.nasa.jpf.symbc.string.SymbolicLengthInteger;
import java.util.ArrayList;
import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

/**
 * Unsupported SPF term kinds (the concolic "special" expressions and the function
 * expression used for native-peer calls like {@code doubleToRawLongBits}) must raise
 * a typed, attributable {@link UnsupportedSpfTermException} rather than a bare
 * {@link UnsupportedOperationException}, so the listener can mark the spec incomplete
 * instead of crashing — and a future renderer can distinguish "unsupported term" from
 * a JPF bug.
 */
public class SpfToModelTransformerUnsupportedTermTest {

    private static SpfToModelTransformer transformer() {
        return new SpfToModelTransformer();
    }

    @Example
    void symbolicLengthIntegerBecomesReceiverLengthInvocation() {
        LinearIntegerConstraint constraint = new LinearIntegerConstraint(
            new SymbolicLengthInteger("Length_0_", 0, 100, new StringSymbolic("value_1_SYMSTRING")),
            Comparator.GE,
            new IntegerConstant(0));

        Assert.assertEquals(
            new Operation(
                new Invocation(new Variable("value", TypeDomain.STRING), null, "length", Collections.emptyList()),
                Operator.GE,
                new Constant((long) 0, TypeDomain.INTEGER)),
            transformer().transform(constraint));
    }

    @Example
    void stringDerivedIntegerOtherThanLengthRaisesTypedException() {
        SymbolicIndexOfCharInteger expr = new SymbolicIndexOfCharInteger(
            "IndexOf_0_",
            -1,
            100,
            new StringSymbolic("value_1_SYMSTRING"),
            new SymbolicInteger("needle_2_SYMINT"));
        LinearIntegerConstraint constraint = new LinearIntegerConstraint(expr, Comparator.GE, new IntegerConstant(0));

        try {
            transformer().transform(constraint);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (UnsupportedSpfTermException expected) {
            Assert.assertTrue(expected.getMessage().contains("SymbolicIndexOfCharInteger"));
        }
    }

    @Example
    void specialIntegerExpressionRaisesTypedException() {
        SpecialIntegerExpression expr = new SpecialIntegerExpression(new StringConstant("x"));
        try {
            transformer().transform(expr);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (UnsupportedSpfTermException expected) {
            Assert.assertTrue(expected.getMessage().contains("SpecialIntegerExpression"));
        }
    }

    @Example
    void specialRealExpressionRaisesTypedException() {
        SpecialRealExpression expr = new SpecialRealExpression(new StringConstant("x"));
        try {
            transformer().transform(expr);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (UnsupportedSpfTermException expected) {
            Assert.assertTrue(expected.getMessage().contains("SpecialRealExpression"));
        }
    }

    @Example
    void functionExpressionRaisesTypedException() {
        FunctionExpression expr = new FunctionExpression(
            "java.lang.Double", "doubleToRawLongBits",
            new Class<?>[0], new Expression[0], new ArrayList<>());
        try {
            transformer().transform(expr);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (UnsupportedSpfTermException expected) {
            Assert.assertTrue(expected.getMessage().contains("FunctionExpression"));
        }
    }

    @Example
    void rawDoubleBitsExpressionRaisesTypedException() {
        RawDoubleBitsExpression expression = new RawDoubleBitsExpression(new SymbolicReal("value_1_SYMREAL"));

        try {
            transformer().transform(expression);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (UnsupportedSpfTermException expected) {
            Assert.assertTrue(expected.getMessage().contains("RawDoubleBitsExpression"));
            Assert.assertTrue(expected.getMessage().contains("Double.doubleToRawLongBits"));
        }
    }

    @Example
    void nestedRawDoubleBitsExpressionRaisesTypedException() {
        RawDoubleBitsExpression rawBits = new RawDoubleBitsExpression(new SymbolicReal("value_1_SYMREAL"));
        BinaryLinearIntegerExpression expression = new BinaryLinearIntegerExpression(
            rawBits, gov.nasa.jpf.symbc.numeric.Operator.PLUS, new IntegerConstant(1));

        try {
            transformer().transform(expression);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (UnsupportedSpfTermException expected) {
            Assert.assertTrue(expected.getMessage().contains("RawDoubleBitsExpression"));
        }
    }

    @Example
    void typedExceptionIsNotAGenericUnsupportedOperationException() {
        // The signal must be a distinct type: callers that catch RuntimeException must
        // still be able to distinguish "unsupported SPF term" from unrelated failures.
        SpecialIntegerExpression expr = new SpecialIntegerExpression(new StringConstant("x"));
        try {
            transformer().transform(expr);
            Assert.fail("expected UnsupportedSpfTermException");
        } catch (RuntimeException e) {
            Assert.assertTrue("must be the typed subtype, not plain UnsupportedOperationException",
                e instanceof UnsupportedSpfTermException);
            Assert.assertFalse("must not be a plain UnsupportedOperationException",
                e.getClass().equals(UnsupportedOperationException.class));
        }
    }
}
