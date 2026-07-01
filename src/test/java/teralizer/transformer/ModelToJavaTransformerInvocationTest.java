package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.ConstantString;
import teralizer.domain.Invocation;
import teralizer.domain.Not;
import teralizer.domain.VariableReal;
import teralizer.domain.VariableString;

import java.util.Arrays;
import java.util.Collections;

public class ModelToJavaTransformerInvocationTest {

    @Example
    void rendersNoArgInstanceInvocation() {
        Invocation invocation = new Invocation(new VariableString("s"), null, "isEmpty", Collections.emptyList());

        Assert.assertEquals("(_p_.s.isEmpty())", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersOneArgInstanceInvocation() {
        Invocation invocation = new Invocation(
            new VariableString("s"),
            null,
            "equals",
            Collections.singletonList(new ConstantString("foo")));

        Assert.assertEquals("(_p_.s.equals(\"foo\"))", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersStaticInvocationUsingSimpleJavaLangClassName() {
        Invocation invocation = new Invocation(
            null,
            "java.lang.Math",
            "sqrt",
            Collections.singletonList(new VariableReal("x")));

        Assert.assertEquals("Math.sqrt(_p_.x)", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersNotAroundInvocation() {
        Invocation invocation = new Invocation(
            new VariableString("s"),
            null,
            "equals",
            Collections.singletonList(new ConstantString("foo")));

        Assert.assertEquals("(!(_p_.s.equals(\"foo\")))", new ModelToJavaTransformer().transform(new Not(invocation)));
    }

    @Example
    void unsupportedInvocationThrowsTypedException() {
        Invocation invocation = new Invocation(
            new VariableString("s"),
            null,
            "substring",
            Arrays.asList(new ConstantInteger(0), new ConstantInteger(1)));

        try {
            new ModelToJavaTransformer().transform(invocation);
            Assert.fail("substring should not render before it is capability-registered");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("substring"));
        }
    }

    @Example
    void instanceCallUsingStaticCapabilityThrowsTypedException() {
        Invocation invocation = new Invocation(
            new VariableReal("x"),
            null,
            "sqrt",
            Collections.emptyList());

        try {
            new ModelToJavaTransformer().transform(invocation);
            Assert.fail("sqrt is registered as static and must not render as an instance call");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("sqrt"));
        }
    }

    @Example
    void staticCallWithWrongQualifierThrowsTypedException() {
        Invocation invocation = new Invocation(
            null,
            "foo.Bar",
            "sqrt",
            Collections.singletonList(new VariableReal("x")));

        try {
            new ModelToJavaTransformer().transform(invocation);
            Assert.fail("sqrt must render only for its registered static qualifier");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("foo.Bar"));
        }
    }
}
