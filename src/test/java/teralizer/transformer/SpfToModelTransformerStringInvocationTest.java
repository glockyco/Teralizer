package teralizer.transformer;

import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.string.StringComparator;
import gov.nasa.jpf.symbc.string.StringExpression;
import gov.nasa.jpf.symbc.string.StringPathCondition;
import gov.nasa.jpf.symbc.string.StringSymbolic;
import java.util.Arrays;
import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Not;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class SpfToModelTransformerStringInvocationTest {

    @Example
    void stringEqualsConstraintBecomesInvocation() {
        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(
            StringComparator.EQUALS,
            "foo",
            new StringSymbolic("value_1_SYMSTRING"));

        Assert.assertEquals(
            new Invocation(new Variable("value", TypeDomain.STRING), null, "equals", Collections.singletonList(new Constant("foo", TypeDomain.STRING))),
            new SpfToModelTransformer().transform(pathCondition));
    }

    @Example
    void reversedStringEqualsConstraintStillUsesSymbolicReceiver() {
        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(
            StringComparator.EQUALS,
            new StringSymbolic("value_1_SYMSTRING"),
            new gov.nasa.jpf.symbc.string.StringConstant("foo"));

        Assert.assertEquals(
            new Invocation(new Variable("value", TypeDomain.STRING), null, "equals", Collections.singletonList(new Constant("foo", TypeDomain.STRING))),
            new SpfToModelTransformer().transform(pathCondition));
    }

    @Example
    void negatedStringConstraintBecomesNotInvocation() {
        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(
            StringComparator.NOTEQUALS,
            "foo",
            new StringSymbolic("value_1_SYMSTRING"));

        Assert.assertEquals(
            new Not(new Invocation(new Variable("value", TypeDomain.STRING), null, "equals", Collections.singletonList(new Constant("foo", TypeDomain.STRING)))),
            new SpfToModelTransformer().transform(pathCondition));
    }

    @Example
    void emptyComparatorBecomesIsEmptyInvocation() {
        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(StringComparator.EMPTY, new StringSymbolic("value_1_SYMSTRING"));

        Assert.assertEquals(
            new Invocation(new Variable("value", TypeDomain.STRING), null, "isEmpty", Collections.emptyList()),
            new SpfToModelTransformer().transform(pathCondition));
    }

    @Example
    void notEmptyComparatorBecomesNotIsEmptyInvocation() {
        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(StringComparator.NOTEMPTY, new StringSymbolic("value_1_SYMSTRING"));

        Assert.assertEquals(
            new Not(new Invocation(new Variable("value", TypeDomain.STRING), null, "isEmpty", Collections.emptyList())),
            new SpfToModelTransformer().transform(pathCondition));
    }

    @Example
    void unaryDerivedStringExpressionBecomesNoArgInvocation() {
        StringExpression expression = new StringSymbolic("value_1_SYMSTRING")._trim();

        Assert.assertEquals(
            new Invocation(new Variable("value", TypeDomain.STRING), null, "trim", Collections.emptyList()),
            new SpfToModelTransformer().transform(expression));
    }

    @Example
    void oprlistDerivedStringExpressionBecomesInvocationWithAllArguments() {
        StringExpression expression = new StringSymbolic("value_1_SYMSTRING")._replace("a", "b");

        Assert.assertEquals(
            new Invocation(
                new Variable("value", TypeDomain.STRING),
                null,
                "replace",
                Arrays.asList(new Constant("a", TypeDomain.STRING), new Constant("b", TypeDomain.STRING))),
            new SpfToModelTransformer().transform(expression));
    }
}
