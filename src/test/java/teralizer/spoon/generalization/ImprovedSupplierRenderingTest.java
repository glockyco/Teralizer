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
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;

import java.util.Collections;

public class ImprovedSupplierRenderingTest {

    @Example
    void omitsFilterWhenNoResidualInputPredicateExists() {
        MethodParameter x = new MethodParameter("double", "x");
        // No model → no clauses → hasResidualClauses() == false → no filter emitted
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            Collections.emptyMap(),
            null
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertFalse(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFilterWhenInputPredicateExists() {
        MethodParameter x = new MethodParameter("double", "x");
        // x > 0.0 produces a residual clause → hasResidualClauses() == true → filter emitted
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            new Operation(
                new teralizer.domain.VariableReal("x"),
                Operator.GT,
                new teralizer.domain.ConstantReal(0.0)
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFullFilterWhenPlannerReportsResidualClauses() {
        MethodParameter x = new MethodParameter("double", "x");
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            new Operation(
                new teralizer.domain.VariableReal("x"),
                Operator.GT,
                new teralizer.domain.ConstantReal(0.0)
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains(".filter("));
        Assert.assertTrue(supplierClass.toString().contains("return (_p_.x > 0.0);"));
    }

    @Example
    void omitsFilterWhenPlannerHasNoResidualClauses() {
        MethodParameter parameter = new MethodParameter("double", "x");
        // No model → no clauses → hasResidualClauses() == false → filter omitted
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(parameter),
            Collections.emptyMap(),
            null
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(parameter),
            null,
            plan
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
            null,
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains("bUpperBounds = java.util.Arrays.asList(bDefaultMax, (int) (9 - a))"));
        Assert.assertTrue(supplierClass.toString().contains(".filter("));
        Assert.assertTrue(supplierClass.toString().contains("return ((_p_.a + _p_.b) < 10);"));
    }

    @Example
    void doubleBoundsUseDynamicScaleInsteadOfGlobalMaximumScale() {
        MethodParameter a = new MethodParameter("double", "a");
        MethodParameter b = new MethodParameter("double", "b");
        // b > a: NumericDomainPlanner sees a variable lower bound on b → emits dynamic-scale recipe
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            java.util.Arrays.asList(a, b),
            Collections.singletonMap("b", new MethodArgument("double", "2.0")),
            new Operation(
                new teralizer.domain.VariableReal("b"),
                Operator.GT,
                new teralizer.domain.VariableReal("a")
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            java.util.Arrays.asList(a, b),
            null,
            plan
        );

        String body = supplierClass.toString();
        Assert.assertFalse(body.contains("ofScale(325)"));
        Assert.assertTrue(body.contains("java.math.BigDecimal.valueOf"));
        Assert.assertTrue(body.contains("bScale"));
        Assert.assertTrue(body.contains(".ofScale(bScale).between"));
    }
}
