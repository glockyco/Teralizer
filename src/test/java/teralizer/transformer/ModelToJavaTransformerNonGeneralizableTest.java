package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantString;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
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
}
