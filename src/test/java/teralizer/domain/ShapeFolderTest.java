package teralizer.domain;

import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;

public class ShapeFolderTest {

    @Example
    void variableShapesByDomain() {
        Assert.assertEquals("Variable:INTEGER", new Variable("x", TypeDomain.INTEGER).fold(new ShapeFolder()));
        Assert.assertEquals("Variable:STRING", new Variable("s", TypeDomain.STRING).fold(new ShapeFolder()));
    }

    @Example
    void constantShapesByDomainNotValue() {
        Assert.assertEquals("Constant:INTEGER", new Constant(42L, TypeDomain.INTEGER).fold(new ShapeFolder()));
        Assert.assertEquals("Constant:STRING", new Constant("foo", TypeDomain.STRING).fold(new ShapeFolder()));
        Assert.assertEquals("Constant:REAL", new Constant(3.14, TypeDomain.REAL).fold(new ShapeFolder()));
    }

    @Example
    void arrayShapesByElementType() {
        Assert.assertEquals("Array:int", new ArrayExpression("values", "int").fold(new ShapeFolder()));
    }

    @Example
    void arrayElementShapesByElementTypeAndSelector() {
        ArrayElementExpression expression = new ArrayElementExpression(
            "values",
            "int",
            new Variable("index", TypeDomain.INTEGER)
        );

        Assert.assertEquals("ArrayElement:int[Variable:INTEGER]", expression.fold(new ShapeFolder()));
    }

    @Example
    void operationShapesByOperatorAndOperands() {
        Operation op = new Operation(
            new Variable("a", TypeDomain.INTEGER),
            Operator.MOD,
            new Constant(2L, TypeDomain.INTEGER)
        );

        Assert.assertEquals("MOD(Variable:INTEGER,Constant:INTEGER)", op.fold(new ShapeFolder()));
    }

    @Example
    void invocationShapesByMethodAndArgs() {
        Invocation inv = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "startsWith",
            Collections.singletonList(new Constant("x", TypeDomain.STRING))
        );

        Assert.assertEquals("startsWith(Variable:STRING,Constant:STRING)", inv.fold(new ShapeFolder()));
    }

    @Example
    void staticInvocationShapesWithQualifier() {
        Invocation inv = new Invocation(
            null,
            "java.lang.Math",
            "sqrt",
            Collections.singletonList(new Variable("x", TypeDomain.REAL))
        );

        Assert.assertEquals("sqrt(Variable:REAL)", inv.fold(new ShapeFolder()));
    }

    @Example
    void notShapesOperand() {
        Not not = new Not(new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING))
        ));

        Assert.assertEquals("!(equals(Variable:STRING,Constant:STRING))", not.fold(new ShapeFolder()));
    }

    @Example
    void nestedOperationInsideInvocation() {
        Invocation inv = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING))
        );
        Operation op = new Operation(inv, Operator.EQ, new Constant(true, TypeDomain.BOOLEAN));

        Assert.assertEquals(
            "EQ(equals(Variable:STRING,Constant:STRING),Constant:BOOLEAN)",
            op.fold(new ShapeFolder())
        );
    }

    @Example
    void operatorShapesByName() {
        Assert.assertEquals("EQ", Operator.EQ.fold(new ShapeFolder()));
    }

    @Example
    void errorShapesUseNodeKind() {
        Assert.assertEquals("Error", new Error("java.lang.Error", "boom").fold(new ShapeFolder()));
    }

    @Example
    void exceptionShapesByName() {
        Assert.assertEquals(
            "Exception:java.lang.IllegalArgumentException",
            new ExceptionModel("java.lang.IllegalArgumentException", "bad input").fold(new ShapeFolder())
        );
    }
}
