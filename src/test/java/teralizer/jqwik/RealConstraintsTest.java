package teralizer.jqwik;

import net.jqwik.api.Example;
import org.junit.Assert;

public class RealConstraintsTest {

    // Pre-fix, Double.isNaN was missing; valueOf fell through to String.valueOf(NaN) = "NaN",
    // producing "(double) (NaN)" which does not compile as a Java literal.

    @Example
    void nanLowerBoundRendersAsDoubleNan() {
        RealConstraints constraints = new RealConstraints();
        constraints.setVariableType("double");
        constraints.setVariableName("x");

        constraints.addConstantLowerBound(Double.NaN, true);

        String rendered = constraints.getLowerBounds().get(0).getValue();
        Assert.assertEquals("(double) (Double.NaN)", rendered);
        Assert.assertFalse("bare NaN token must not appear", rendered.equals("(double) (NaN)"));
    }

    @Example
    void nanUpperBoundRendersAsDoubleNan() {
        RealConstraints constraints = new RealConstraints();
        constraints.setVariableType("double");
        constraints.setVariableName("x");

        constraints.addConstantUpperBound(Double.NaN, false);

        String rendered = constraints.getUpperBounds().get(0).getValue();
        Assert.assertEquals("(double) (Double.NaN)", rendered);
    }

    @Example
    void finiteBoundRendersAsDecimalLiteral() {
        RealConstraints constraints = new RealConstraints();
        constraints.setVariableType("double");
        constraints.setVariableName("x");

        constraints.addConstantLowerBound(3.14, true);

        Assert.assertEquals("(double) (3.14)", constraints.getLowerBounds().get(0).getValue());
    }

    @Example
    void positiveInfinityBoundRendersAsDoublePositiveInfinity() {
        RealConstraints constraints = new RealConstraints();
        constraints.setVariableType("double");
        constraints.setVariableName("x");

        constraints.addConstantUpperBound(Double.POSITIVE_INFINITY, false);

        Assert.assertEquals("(double) (Double.POSITIVE_INFINITY)", constraints.getUpperBounds().get(0).getValue());
    }

    @Example
    void negativeInfinityBoundRendersAsDoubleNegativeInfinity() {
        RealConstraints constraints = new RealConstraints();
        constraints.setVariableType("double");
        constraints.setVariableName("x");

        constraints.addConstantLowerBound(Double.NEGATIVE_INFINITY, true);

        Assert.assertEquals("(double) (Double.NEGATIVE_INFINITY)", constraints.getLowerBounds().get(0).getValue());
    }
}
