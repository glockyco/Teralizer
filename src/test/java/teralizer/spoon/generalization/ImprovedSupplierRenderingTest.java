package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.jqwik.RealConstraints;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;

public class ImprovedSupplierRenderingTest {

    @Example
    void omitsFilterWhenNoResidualInputPredicateExists() {
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(new MethodParameter("double", "x")),
            Collections.emptyMap(),
            Collections.emptyMap(),
            null
        );

        Assert.assertFalse(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFilterWhenInputPredicateExists() {
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(new MethodParameter("double", "x")),
            Collections.emptyMap(),
            Collections.emptyMap(),
            "_p_.x > 0.0"
        );

        Assert.assertTrue(supplierClass.toString().contains(".filter("));
    }

    @Example
    void doubleBoundsUseDynamicScaleInsteadOfGlobalMaximumScale() throws Exception {
        Method createDoubleArbitrary = ImprovedTestParametersSupplierFactory.class.getDeclaredMethod(
            "createDoubleArbitrary",
            MethodParameter.class,
            Optional.class,
            RealConstraints.class
        );
        createDoubleArbitrary.setAccessible(true);

        RealConstraints constraints = new RealConstraints();
        constraints.setVariableType("double");
        constraints.setVariableName("b");
        constraints.addVariableLowerBound("a", false);

        String body = (String) createDoubleArbitrary.invoke(
            null,
            new MethodParameter("double", "b"),
            Optional.of(new MethodArgument("double", "2.0")),
            constraints
        );

        Assert.assertFalse(body.contains("ofScale(325)"));
        Assert.assertTrue(body.contains("java.math.BigDecimal.valueOf"));
        Assert.assertTrue(body.contains("bScale"));
        Assert.assertTrue(body.contains(".ofScale(bScale).between"));
    }
}
