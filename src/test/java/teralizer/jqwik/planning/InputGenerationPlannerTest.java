package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;

import java.util.Arrays;
import java.util.List;

public class InputGenerationPlannerTest {
    @Example
    void normalizesSupportedTypeDomains() {
        Assert.assertEquals(TypeDomain.INTEGER, TypeDomain.from("int"));
        Assert.assertEquals(TypeDomain.INTEGER, TypeDomain.from("java.lang.Long"));
        Assert.assertEquals(TypeDomain.REAL, TypeDomain.from("double"));
        Assert.assertEquals(TypeDomain.REAL, TypeDomain.from("java.lang.Float"));
        Assert.assertEquals(TypeDomain.CHAR, TypeDomain.from("char"));
        Assert.assertEquals(TypeDomain.BOOLEAN, TypeDomain.from("java.lang.Boolean"));
        Assert.assertEquals(TypeDomain.STRING, TypeDomain.from("java.lang.String"));
        Assert.assertEquals(TypeDomain.ARRAY, TypeDomain.from("int[]"));
        Assert.assertEquals(TypeDomain.OBJECT, TypeDomain.from("org.example.Interval"));
    }

    @Example
    void createsResidualOnlyPlanBeforeDomainPlannersConsumeClauses() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("int", "x"));
        Model inputModel = new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, inputModel);

        Assert.assertEquals(1, plan.getTotalConstraintCount());
        Assert.assertEquals(0, plan.getUsedConstraintCount());
        Assert.assertEquals(1, plan.getResidualClauses().size());
        Assert.assertEquals("(_p_.x > 0)", plan.getResidualPredicate());
        Assert.assertFalse(plan.hasConsumedClauses());
        Assert.assertTrue(plan.hasResidualClauses());
        Assert.assertEquals(1, plan.getParameterPlans().size());
        Assert.assertEquals(TypeDomain.INTEGER, plan.getParameterPlans().get(0).getDomain());
        Assert.assertEquals("x", plan.getParameterPlans().get(0).getParameter().getName());
    }

    @Example
    void createsEmptyPlanForMissingInputModel() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("double", "x"));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, null);

        Assert.assertEquals(0, plan.getTotalConstraintCount());
        Assert.assertEquals(0, plan.getUsedConstraintCount());
        Assert.assertFalse(plan.hasResidualClauses());
        Assert.assertEquals("true", plan.getResidualPredicate());
        Assert.assertEquals(TypeDomain.REAL, plan.getParameterPlans().get(0).getDomain());
    }
}
