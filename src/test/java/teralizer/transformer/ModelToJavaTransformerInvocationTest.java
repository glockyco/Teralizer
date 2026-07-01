package teralizer.transformer;

import java.util.Arrays;
import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Not;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class ModelToJavaTransformerInvocationTest {

    @Example
    void rendersNoArgInstanceInvocation() {
        Invocation invocation = new Invocation(new Variable("s", TypeDomain.STRING), null, "isEmpty", Collections.emptyList());

        Assert.assertEquals("(_p_.s.isEmpty())", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersOneArgInstanceInvocation() {
        Invocation invocation = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING)));

        Assert.assertEquals("(_p_.s.equals(\"foo\"))", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersStringPredicateAndTransformInvocations() {
        Assert.assertEquals(
            "(_p_.s.equalsIgnoreCase(\"foo\"))",
            new ModelToJavaTransformer().transform(new Invocation(
                new Variable("s", TypeDomain.STRING), null, "equalsIgnoreCase", Collections.singletonList(new Constant("foo", TypeDomain.STRING)))));
        Assert.assertEquals(
            "(_p_.s.startsWith(\"f\"))",
            new ModelToJavaTransformer().transform(new Invocation(
                new Variable("s", TypeDomain.STRING), null, "startsWith", Collections.singletonList(new Constant("f", TypeDomain.STRING)))));
        Assert.assertEquals(
            "(_p_.s.concat(\"!\"))",
            new ModelToJavaTransformer().transform(new Invocation(
                new Variable("s", TypeDomain.STRING), null, "concat", Collections.singletonList(new Constant("!", TypeDomain.STRING)))));
        Assert.assertEquals(
            "((_p_.s.trim()).toLowerCase())",
            new ModelToJavaTransformer().transform(new Invocation(
                new Invocation(new Variable("s", TypeDomain.STRING), null, "trim", Collections.emptyList()),
                null,
                "toLowerCase",
                Collections.emptyList())));
    }

    @Example
    void stringInvocationOnNonStringReceiverThrowsTypedException() {
        Invocation invocation = new Invocation(
            new Variable("a", TypeDomain.INTEGER),
            null,
            "equals",
            Collections.singletonList(new Constant((long) 0, TypeDomain.INTEGER)));

        try {
            new ModelToJavaTransformer().transform(invocation);
            Assert.fail("String invocation must not render on an integer receiver");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("equals"));
        }
    }

    @Example
    void rendersStaticInvocationUsingSimpleJavaLangClassName() {
        Invocation invocation = new Invocation(
            null,
            "java.lang.Math",
            "sqrt",
            Collections.singletonList(new Variable("x", TypeDomain.REAL)));

        Assert.assertEquals("Math.sqrt(_p_.x)", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersStaticStringValueOfInvocation() {
        Invocation invocation = new Invocation(
            null,
            "java.lang.String",
            "valueOf",
            Collections.singletonList(new Variable("i", TypeDomain.INTEGER)));

        Assert.assertEquals("String.valueOf(_p_.i)", new ModelToJavaTransformer().transform(invocation));
    }

    @Example
    void rendersNotAroundInvocation() {
        Invocation invocation = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING)));

        Assert.assertEquals("(!(_p_.s.equals(\"foo\")))", new ModelToJavaTransformer().transform(new Not(invocation)));
    }

    @Example
    void unsupportedInvocationThrowsTypedException() {
        Invocation invocation = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "substring",
            Arrays.asList(new Constant((long) 0, TypeDomain.INTEGER), new Constant((long) 1, TypeDomain.INTEGER)));

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
            new Variable("x", TypeDomain.REAL),
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
            Collections.singletonList(new Variable("x", TypeDomain.REAL)));

        try {
            new ModelToJavaTransformer().transform(invocation);
            Assert.fail("sqrt must render only for its registered static qualifier");
        } catch (NonGeneralizableExpressionException expected) {
            Assert.assertTrue(expected.getMessage().contains("foo.Bar"));
        }
    }
}
