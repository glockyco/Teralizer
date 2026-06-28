package teralizer.transformer;

import gov.nasa.jpf.symbc.concolic.FunctionExpression;
import gov.nasa.jpf.symbc.mixednumstrg.SpecialIntegerExpression;
import gov.nasa.jpf.symbc.mixednumstrg.SpecialRealExpression;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.string.StringConstant;
import net.jqwik.api.Example;
import org.junit.Assert;

import java.util.ArrayList;

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
