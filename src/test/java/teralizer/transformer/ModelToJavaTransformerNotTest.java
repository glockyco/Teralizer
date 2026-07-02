package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Not;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

/**
 * {@code Not} rendering around non-{@link teralizer.domain.Invocation} operands.
 * The {@code Not(Invocation)} case is covered by
 * {@link ModelToJavaTransformerInvocationTest#rendersNotAroundInvocation()};
 * these tests pin {@code Not(Variable)} and {@code Not(Operation)}.
 */
public class ModelToJavaTransformerNotTest {

    @Example
    void rendersNotAroundVariable() {
        Assert.assertEquals(
            "(!_p_.s)",
            new ModelToJavaTransformer().transform(new Not(new Variable("s", TypeDomain.STRING))));
    }

    @Example
    void rendersNotAroundOperation() {
        Operation eq = new Operation(
            new Variable("s", TypeDomain.STRING),
            Operator.EQ,
            new Constant("foo", TypeDomain.STRING));

        Assert.assertEquals(
            "(!(_p_.s == \"foo\"))",
            new ModelToJavaTransformer().transform(new Not(eq)));
    }

    @Example
    void rendersNestedNot() {
        Operation eq = new Operation(
            new Variable("s", TypeDomain.STRING),
            Operator.EQ,
            new Constant("foo", TypeDomain.STRING));

        Assert.assertEquals(
            "(!(!(_p_.s == \"foo\")))",
            new ModelToJavaTransformer().transform(new Not(new Not(eq))));
    }
}
