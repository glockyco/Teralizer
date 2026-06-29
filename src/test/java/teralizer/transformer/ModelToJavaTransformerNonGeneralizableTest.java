package teralizer.transformer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.ConstantString;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableReal;
import teralizer.domain.VariableString;

public class ModelToJavaTransformerNonGeneralizableTest {

    @Example
    void unsupportedStringOperatorThrowsTypedException() {
        // EQUALS is a string operator with no Java-render case; the renderer must signal a
        // typed non-generalizable outcome, not a bare RuntimeException, so callers can drop
        // the clause instead of crashing the whole generalization.
        Operation model = new Operation(new VariableString("s"), Operator.EQUALS, new ConstantString("x"));
        try {
            new ModelToJavaTransformer().transform(model);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("EQUALS"));
        }
    }

    @Example
    void typedExceptionIsNotAGenericRuntimeException() {
        // Callers distinguish "non-generalizable clause" from other runtime failures; the
        // signal must be a distinct type, not RuntimeException itself.
        Operation model = new Operation(new VariableString("s"), Operator.STARTSWITH, new ConstantString("p"));
        try {
            new ModelToJavaTransformer().transform(model);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (RuntimeException e) {
            Assert.assertTrue("must be the typed subtype, not plain RuntimeException",
                e instanceof NonGeneralizableExpressionException);
        }
    }

    @Example
    void bitwiseXorOnFloatingPointOperandsThrowsTypedException() {
        // Under z3bitvector, SPF emits (rawBits(x) ^ rawBits(y)) directly on the symbolic
        // doubles when the doubleToRawLongBits wrapper has no Model node. Rendering '^' on
        // double operands is invalid Java; the renderer must fail loud so the clause excludes
        // cleanly (honest non-generalizable outcome) instead of emitting uncompilable code.
        Operation model = new Operation(new VariableReal("x"), Operator.XOR, new VariableReal("y"));
        try {
            new ModelToJavaTransformer().transform(model);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("XOR"));
        }
    }

    @Example
    void bitwiseAndWithFloatingPointOperandThrowsTypedException() {
        // Mixed real/int bitwise (the `& SGN_MASK` step) is equally un-renderable.
        Operation model = new Operation(new VariableReal("x"), Operator.AND, new ConstantInteger(1));
        try {
            new ModelToJavaTransformer().transform(model);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("AND"));
        }
    }

    @Example
    void shiftOnFloatingPointOperandThrowsTypedException() {
        Operation model = new Operation(new VariableReal("x"), Operator.SHIFTL, new ConstantInteger(2));
        try {
            new ModelToJavaTransformer().transform(model);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("SHIFTL"));
        }
    }

    @Example
    void bitwiseXorOnIntegerOperandsStillRenders() {
        // Integer bitwise stays valid Java and must keep rendering after the guard lands.
        Operation model = new Operation(new VariableInteger("a"), Operator.XOR, new VariableInteger("b"));
        Assert.assertEquals("(_p_.a ^ _p_.b)", new ModelToJavaTransformer().transform(model));
    }

    @Example
    void predicateRethrowsWhenBitwiseClauseConstrainsGeneratedParameters() {
        // The scorecard-relevant path: when the un-renderable bitwise clause constrains
        // generated parameters (x, y), transformPredicate must rethrow so the generalization
        // is excluded — never silently drop it, which would weaken the path predicate.
        Operation model = new Operation(new VariableReal("x"), Operator.XOR, new VariableReal("y"));
        Set<String> generalizable = new HashSet<>(Arrays.asList("x", "y"));
        try {
            new ModelToJavaTransformer().transformPredicate(model, generalizable);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            // expected — bitwise-on-double excludes the generalization, not dropped.
        }
    }
}
