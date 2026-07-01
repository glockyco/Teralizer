package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantString;
import teralizer.domain.ConstantInteger;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableString;
import teralizer.domain.VariableInteger;

/**
 * Renders the sound Boolean String operators to Java. SPF's StringConstraint places the receiver on
 * the left and the argument on the right, so each renders as {@code receiver.op(arg)}; the {@code
 * NOT*} comparators are the negation SPF records for the false branch, rendered as {@code !}.
 */
public class ModelToJavaTransformerStringOperatorTest {

    private static String render(Operator op) {
        return new ModelToJavaTransformer()
            .transform(new Operation(new VariableString("s"), op, new ConstantString("foo")));
    }

    @Example
    void rendersEquals() {
        Assert.assertEquals("(_p_.s.equals(\"foo\"))", render(Operator.EQUALS));
    }

    @Example
    void rendersNotEquals() {
        Assert.assertEquals("(!_p_.s.equals(\"foo\"))", render(Operator.NOTEQUALS));
    }

    @Example
    void rendersStartsWith() {
        Assert.assertEquals("(_p_.s.startsWith(\"foo\"))", render(Operator.STARTSWITH));
    }

    @Example
    void rendersNotStartsWith() {
        Assert.assertEquals("(!_p_.s.startsWith(\"foo\"))", render(Operator.NOTSTARTSWITH));
    }

    @Example
    void rendersEndsWith() {
        Assert.assertEquals("(_p_.s.endsWith(\"foo\"))", render(Operator.ENDSWITH));
    }

    @Example
    void rendersNotEndsWith() {
        Assert.assertEquals("(!_p_.s.endsWith(\"foo\"))", render(Operator.NOTENDSWITH));
    }

    @Example
    void rendersContains() {
        Assert.assertEquals("(_p_.s.contains(\"foo\"))", render(Operator.CONTAINS));
    }

    @Example
    void rendersNotContains() {
        Assert.assertEquals("(!_p_.s.contains(\"foo\"))", render(Operator.NOTCONTAINS));
    }

    @Example
    void stringOperatorOnNonStringOperandsThrows() {
        // EQUALS is a String comparator; on non-string operands it must stay non-generalizable
        // rather than render an invalid `.equals` on a primitive.
        Operation model = new Operation(new VariableInteger("a"), Operator.EQUALS, new ConstantInteger(0));
        try {
            new ModelToJavaTransformer().transform(model);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("EQUALS"));
        }
    }
}
