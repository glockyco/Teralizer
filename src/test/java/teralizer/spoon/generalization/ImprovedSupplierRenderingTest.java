package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.jqwik.RealConstraints;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;
import teralizer.jqwik.planning.ParameterGenerationPlan;
import teralizer.jqwik.planning.RawJavaRecipe;
import teralizer.jqwik.planning.TypeDomain;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
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
    void keepsFullFilterWhenPlannerReportsResidualClauses() {
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(new MethodParameter("double", "x")),
            Collections.emptyMap(),
            Collections.emptyMap(),
            "_p_.x > 0.0",
            new InputGenerationPlan(
                Collections.singletonList(new ParameterGenerationPlan(
                    new MethodParameter("double", "x"),
                    TypeDomain.REAL,
                    new RawJavaRecipe(""),
                    Collections.emptySet()
                )),
                teralizer.jqwik.planning.ConstraintClauses.from(
                    new teralizer.domain.Operation(
                        new teralizer.domain.VariableReal("x"),
                        teralizer.domain.Operator.GT,
                        new teralizer.domain.ConstantReal(0.0)
                    ),
                    Collections.singletonMap("x", "double")
                ),
                Collections.emptySet()
            )
        );

        Assert.assertTrue(supplierClass.toString().contains(".filter("));
        Assert.assertTrue(supplierClass.toString().contains("return (_p_.x > 0.0);"));
    }

    @Example
    void omitsFilterWhenPlannerHasNoResidualClauses() {
        MethodParameter parameter = new MethodParameter("double", "x");
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(parameter),
            Collections.emptyMap(),
            Collections.emptyMap(),
            "_p_.x > 0.0",
            new InputGenerationPlan(
                Collections.singletonList(new ParameterGenerationPlan(
                    parameter,
                    TypeDomain.REAL,
                    new RawJavaRecipe(""),
                    new HashSet<>(Collections.singletonList(0))
                )),
                teralizer.jqwik.planning.ConstraintClauses.from(
                    new teralizer.domain.Operation(
                        new teralizer.domain.VariableReal("x"),
                        teralizer.domain.Operator.GT,
                        new teralizer.domain.ConstantReal(0.0)
                    ),
                    Collections.singletonMap("x", "double")
                ),
                new HashSet<>(Collections.singletonList(0))
            )
        );

        Assert.assertFalse(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFullFilterForPlannerGeneratedAffineBounds() {
        MethodParameter a = new MethodParameter("int", "a");
        MethodParameter b = new MethodParameter("int", "b");
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            java.util.Arrays.asList(a, b),
            new Operation(
                new Operation(new VariableInteger("a"), Operator.PLUS, new VariableInteger("b")),
                Operator.LT,
                new teralizer.domain.ConstantInteger(10)
            )
        );

        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            java.util.Arrays.asList(a, b),
            Collections.emptyMap(),
            Collections.emptyMap(),
            "((_p_.a + _p_.b) < 10)",
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains("bUpperBounds = java.util.Arrays.asList(bDefaultMax, (int) (9 - a))"));
        Assert.assertTrue(supplierClass.toString().contains(".filter("));
        Assert.assertTrue(supplierClass.toString().contains("return ((_p_.a + _p_.b) < 10);"));
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
