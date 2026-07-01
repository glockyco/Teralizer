package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ArrayElementExpression;
import teralizer.domain.ArrayExpression;
import teralizer.domain.ConstantInteger;
import teralizer.domain.ConstantReal;
import teralizer.domain.ConstantString;
import teralizer.domain.Invocation;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableReal;
import teralizer.domain.VariableString;

import java.util.Arrays;
import java.util.Collections;

/**
 * Regression guard for the {@link ModelFolder} migration: the old visitor used an
 * explicit stack and popped operands right-then-left, so a fold-order bug in a
 * composite node would silently swap operands. These composites are not covered by
 * the string/boolean/binary tests.
 */
public class ModelToJavaTransformerFoldOrderTest {

    @Example
    void arrayElementExpressionRendersSelectorInOrder() {
        // _p_.a[_p_.i] — the element selector must be the folded child, not swapped.
        ArrayElementExpression expr = new ArrayElementExpression("a", "int", new VariableInteger("i"));
        Assert.assertEquals("_p_.a[_p_.i]", new ModelToJavaTransformer().transform(expr));
    }

    @Example
    void arrayElementExpressionWithCompositeSelector() {
        // _p_.a[(1 + _p_.i)] — the selector is itself an Operation; fold order matters.
        Operation selector = new Operation(
            new ConstantInteger(1), Operator.PLUS, new VariableInteger("i"));
        ArrayElementExpression expr = new ArrayElementExpression("a", "int", selector);
        Assert.assertEquals("_p_.a[(1 + _p_.i)]", new ModelToJavaTransformer().transform(expr));
    }

    @Example
    void staticInvocationPreservesArgumentOrder() {
        Invocation invocation = new Invocation(
            null,
            "java.lang.Math",
            "pow",
            Arrays.asList(new ConstantReal(1.0), new VariableReal("y")));

        Assert.assertEquals("Math.pow(1.0, _p_.y)", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void instanceInvocationPreservesReceiverAndArgumentOrder() {
        Invocation invocation = new Invocation(
            new VariableString("s"),
            null,
            "replace",
            Arrays.asList(new ConstantString("a"), new ConstantString("b")));

        Assert.assertEquals("(_p_.s.replace(\"a\", \"b\"))", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void nestedOperationKeepsLeftAndRightDistinct() {
        // (1 - _p_.x) != (_p_.y + 2) — a swap of left/right would change the rendered predicate.
        Operation left = new Operation(new ConstantInteger(1), Operator.MINUS, new VariableInteger("x"));
        Operation right = new Operation(new VariableReal("y"), Operator.PLUS, new ConstantInteger(2));
        Operation model = new Operation(left, Operator.NE, right);
        Assert.assertEquals("((1 - _p_.x) != (_p_.y + 2))", new ModelToJavaTransformer(Collections.emptyMap()).transform(model));
    }
}
